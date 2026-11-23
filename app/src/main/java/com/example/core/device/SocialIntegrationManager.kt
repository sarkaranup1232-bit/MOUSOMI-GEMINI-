package com.example.core.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.repository.AddOnRepository
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class SocialPlatform(val displayName: String, val packageName: String) {
  WHATSAPP("WhatsApp", "com.whatsapp"),
  INSTAGRAM("Instagram", "com.instagram.android"),
  FACEBOOK("Facebook", "com.facebook.katana"),
}

data class PendingSocialAction(
  val id: String = "soc_${UUID.randomUUID()}",
  val platform: SocialPlatform,
  val recipientOrHandle: String = "",
  val captionOrMessage: String,
  val mediaFilePath: String? = null,
  val summary: String,
)

/**
 * Verified Social Integration Manager for WhatsApp, Instagram, and Facebook.
 * - Never auto-sends messages or silently posts content.
 * - Always produces a [PendingSocialAction] for explicit user confirmation first,
 *   then opens the official composer / App Link / Android Sharesheet with clean fallback.
 */
@Singleton
class SocialIntegrationManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val visibilityManager: PackageVisibilityManager,
  private val verifier: DeviceActionVerifier,
  private val addOnRepository: AddOnRepository? = null,
) {

  fun isPlatformInstalled(platform: SocialPlatform): Boolean =
    visibilityManager.isPackageInstalled(platform.packageName) ||
      (platform == SocialPlatform.WHATSAPP && visibilityManager.isPackageInstalled("com.whatsapp.w4b"))

  fun prepareWhatsAppMessage(
    phoneOrName: String,
    messageText: String,
  ): PendingSocialAction {
    val cleanTarget = phoneOrName.trim()
    val cleanMsg = messageText.trim()
    return PendingSocialAction(
      platform = SocialPlatform.WHATSAPP,
      recipientOrHandle = cleanTarget,
      captionOrMessage = cleanMsg,
      summary =
        buildString {
          append("Open WhatsApp composer")
          if (cleanTarget.isNotBlank()) append(" for $cleanTarget")
          if (cleanMsg.isNotBlank()) append(" with message: \"${cleanMsg.take(80)}\"")
          append(". Nothing is sent until you tap Send in WhatsApp.")
        },
    )
  }

  fun prepareInstagramPost(
    captionText: String,
    mediaFilePath: String? = null,
  ): PendingSocialAction =
    PendingSocialAction(
      platform = SocialPlatform.INSTAGRAM,
      captionOrMessage = captionText.trim(),
      mediaFilePath = mediaFilePath,
      summary =
        "Copy caption and open Instagram Sharesheet" +
          if (!mediaFilePath.isNullOrBlank()) " with your attached media." else ".",
    )

  fun prepareFacebookPost(
    captionText: String,
    mediaFilePath: String? = null,
  ): PendingSocialAction =
    PendingSocialAction(
      platform = SocialPlatform.FACEBOOK,
      captionOrMessage = captionText.trim(),
      mediaFilePath = mediaFilePath,
      summary =
        "Copy post text and open Facebook Sharesheet" +
          if (!mediaFilePath.isNullOrBlank()) " with your attached media." else ".",
    )

  fun buildWhatsAppIntent(phoneOrName: String, messageText: String): Intent {
    val digits = phoneOrName.filter { it.isDigit() }
    return if (digits.length >= 7) {
      val uri =
        Uri.parse(
          "https://wa.me/$digits?text=${Uri.encode(messageText.trim())}"
        )
      Intent(Intent.ACTION_VIEW, uri).apply {
        if (visibilityManager.isPackageInstalled(SocialPlatform.WHATSAPP.packageName)) {
          setPackage(SocialPlatform.WHATSAPP.packageName)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      val send =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, messageText.trim())
          if (visibilityManager.isPackageInstalled(SocialPlatform.WHATSAPP.packageName)) {
            setPackage(SocialPlatform.WHATSAPP.packageName)
          }
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      if (verifier.verifyIntentResolvable(send)) send
      else Intent.createChooser(send, "Send via WhatsApp").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }
  }

  fun buildShareToPlatformIntent(action: PendingSocialAction): Intent {
    val file = action.mediaFilePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    val base =
      if (file != null) {
        val uri =
          runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
          }.getOrNull()
        Intent(Intent.ACTION_SEND).apply {
          type = if (file.extension.lowercase() in setOf("mp4", "mov", "webm")) "video/*" else "image/*"
          if (uri != null) {
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          if (action.captionOrMessage.isNotBlank()) {
            putExtra(Intent.EXTRA_TEXT, action.captionOrMessage)
          }
        }
      } else {
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, action.captionOrMessage)
        }
      }

    if (visibilityManager.isPackageInstalled(action.platform.packageName)) {
      base.setPackage(action.platform.packageName)
      base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (verifier.verifyIntentResolvable(base)) {
        return base
      }
      base.setPackage(null)
    }

    return Intent.createChooser(base, "Share to ${action.platform.displayName}").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  suspend fun executeConfirmedSocialAction(action: PendingSocialAction): DeviceActionResult {
    // Copy caption to clipboard so user can paste cleanly in Instagram/Facebook if stripped by app
    if (action.captionOrMessage.isNotBlank() && action.platform != SocialPlatform.WHATSAPP) {
      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      cm?.setPrimaryClip(ClipData.newPlainText("${action.platform.displayName} Caption", action.captionOrMessage))
    }

    val intent =
      when (action.platform) {
        SocialPlatform.WHATSAPP -> buildWhatsAppIntent(action.recipientOrHandle, action.captionOrMessage)
        SocialPlatform.INSTAGRAM,
        SocialPlatform.FACEBOOK -> buildShareToPlatformIntent(action)
      }

    val res =
      runCatching {
        context.startActivity(intent)
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Share to ${action.platform.displayName}",
          targetLabel = action.platform.displayName,
          packageName = action.platform.packageName,
          success = true,
          statusLabel = "Composer Opened",
          verifiedDetail =
            if (isPlatformInstalled(action.platform)) {
              "Opened ${action.platform.displayName} composer for your final confirmation."
            } else {
              "${action.platform.displayName} is not installed; opened Android Sharesheet fallback."
            },
        )
      }.getOrElse { err ->
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Share to ${action.platform.displayName}",
          targetLabel = action.platform.displayName,
          packageName = action.platform.packageName,
          success = false,
          statusLabel = "Failed",
          verifiedDetail = err.message ?: "Unable to open ${action.platform.displayName}.",
        )
      }

    addOnRepository?.recordDeviceAction(res)
    return res
  }

  companion object {
    fun tryParseSocialCommand(command: String): PendingSocialAction? {
      val clean = command.trim()
      val lower = clean.lowercase()

      if ("whatsapp" in lower && ("send" in lower || "message" in lower || "chat" in lower)) {
        val afterPrefix =
          clean.replace(
            Regex("^(?:please\\s+)?(?:send\\s+)?(?:a\\s+)?whatsapp\\s*(?:message)?\\s*(?:to)?\\s*", RegexOption.IGNORE_CASE),
            "",
          )
        val phoneMatch = Regex("^([+\\d][\\d\\s()-]{5,20}\\d)\\b").find(afterPrefix)
        val number =
          phoneMatch?.groupValues?.getOrNull(1)?.let { PhoneActionController.sanitizePhoneNumber(it) }
            ?.takeIf { it.length >= 7 }
            .orEmpty()
        val msgBody =
          afterPrefix
            .replace(Regex("^[+\\d][\\d\\s()-]{5,20}\\d\\s*(?:saying|with\\s+message|:)?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { clean }
        return PendingSocialAction(
          platform = SocialPlatform.WHATSAPP,
          recipientOrHandle = number,
          captionOrMessage = msgBody,
          summary =
            "Prepare WhatsApp message" +
              (if (number.isNotBlank()) " to $number" else "") +
              ": \"${msgBody.take(70)}\".",
        )
      }

      if ("instagram" in lower && ("post" in lower || "caption" in lower || "share" in lower)) {
        val caption =
          clean
            .replace(Regex("^(?:please\\s+)?(?:share|post|create\\s+caption)\\s+(?:to|on)\\s+instagram\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { clean }
        return PendingSocialAction(
          platform = SocialPlatform.INSTAGRAM,
          captionOrMessage = caption,
          summary = "Prepare Instagram share & copy caption to clipboard: \"${caption.take(70)}\".",
        )
      }

      if ("facebook" in lower && ("post" in lower || "caption" in lower || "share" in lower)) {
        val caption =
          clean
            .replace(Regex("^(?:please\\s+)?(?:share|post)\\s+(?:to|on)\\s+facebook\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { clean }
        return PendingSocialAction(
          platform = SocialPlatform.FACEBOOK,
          captionOrMessage = caption,
          summary = "Prepare Facebook share & copy post text: \"${caption.take(70)}\".",
        )
      }

      return null
    }
  }
}
