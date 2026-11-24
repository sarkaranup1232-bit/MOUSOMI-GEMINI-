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
  YOUTUBE("YouTube", "com.google.android.youtube"),
}

data class PendingSocialAction(
  val id: String = "soc_${UUID.randomUUID()}",
  val platform: SocialPlatform,
  val recipientOrHandle: String = "",
  val captionOrMessage: String,
  val mediaFilePath: String? = null,
  val summary: String,
  val candidateContacts: List<ResolvedContact> = emptyList(),
  val isContactAmbiguous: Boolean = false,
  val needsContactsPermission: Boolean = false,
  val noteOrLimitation: String = "",
)

/**
 * Verified Social Integration Manager for WhatsApp, Instagram, Facebook, and YouTube.
 * - Never auto-sends messages or silently posts content.
 * - Always produces a [PendingSocialAction] for explicit user confirmation first,
 *   then opens the official composer / App Link / Android Sharesheet with clean fallback.
 * - Resolves named contacts for WhatsApp using [ContactResolver] when permission is granted,
 *   or offers Android Contact Picker / direct number fallback cleanly.
 */
@Singleton
class SocialIntegrationManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val visibilityManager: PackageVisibilityManager,
  private val verifier: DeviceActionVerifier,
  private val addOnRepository: AddOnRepository? = null,
  private val contactResolver: ContactResolver? = null,
) {

  fun resolveInstalledWhatsAppPackage(): String? =
    when {
      visibilityManager.isPackageInstalled(SocialPlatform.WHATSAPP.packageName) -> SocialPlatform.WHATSAPP.packageName
      visibilityManager.isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
      else -> null
    }

  fun isPlatformInstalled(platform: SocialPlatform): Boolean =
    visibilityManager.isPackageInstalled(platform.packageName) ||
      (platform == SocialPlatform.WHATSAPP && visibilityManager.isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE))

  fun prepareWhatsAppMessage(
    phoneOrName: String,
    messageText: String,
    resolvedDisplayLabel: String = "",
  ): PendingSocialAction {
    val cleanTarget = phoneOrName.trim()
    val cleanMsg = messageText.trim()
    val display = resolvedDisplayLabel.trim().ifBlank { cleanTarget }
    return PendingSocialAction(
      platform = SocialPlatform.WHATSAPP,
      recipientOrHandle = cleanTarget,
      captionOrMessage = cleanMsg,
      summary =
        buildString {
          append("Open WhatsApp composer")
          if (display.isNotBlank()) append(" for $display")
          if (cleanMsg.isNotBlank()) append(" with message: \"${cleanMsg.take(80)}\"")
          append(". Nothing is sent until you tap Send in WhatsApp.")
        },
      noteOrLimitation =
        if (!isPlatformInstalled(SocialPlatform.WHATSAPP)) {
          "WhatsApp is not installed; Mousomi can open Android Sharesheet instead."
        } else {
          ""
        },
    )
  }

  fun resolveAndPrepareSocialAction(initial: PendingSocialAction): PendingSocialAction {
    if (initial.platform != SocialPlatform.WHATSAPP) return initial
    val target = initial.recipientOrHandle.trim()
    if (target.isBlank() || ContactResolver.looksLikeDirectPhoneNumber(target)) {
      return initial.copy(
        noteOrLimitation =
          if (!isPlatformInstalled(SocialPlatform.WHATSAPP)) {
            "WhatsApp is not installed; Mousomi can open Android Sharesheet fallback."
          } else {
            initial.noteOrLimitation
          }
      )
    }

    val resolver = contactResolver ?: return initial
    return when (val lookup = resolver.resolveContact(target)) {
      is ContactLookupOutcome.UniqueMatch -> {
        val c = lookup.contact
        prepareWhatsAppMessage(
          phoneOrName = c.phoneNumber,
          messageText = initial.captionOrMessage,
          resolvedDisplayLabel = "${c.displayName} (${c.phoneNumber})",
        )
      }
      is ContactLookupOutcome.AmbiguousMatches -> {
        initial.copy(
          candidateContacts = lookup.candidates,
          isContactAmbiguous = true,
          needsContactsPermission = false,
          summary = "Multiple contacts match \"$target\". Select the intended recipient before opening WhatsApp.",
        )
      }
      is ContactLookupOutcome.PermissionRequired -> {
        initial.copy(
          needsContactsPermission = true,
          summary =
            "Grant Contacts access or tap Pick Contact so Mousomi can find \"$target\", or tap Confirm to pick the chat directly in WhatsApp.",
          noteOrLimitation = "Contacts permission is needed only for automatic name lookup.",
        )
      }
      is ContactLookupOutcome.NotFound -> {
        initial.copy(
          needsContactsPermission = false,
          summary =
            "No phone contact matched \"$target\". Pick a contact or tap Confirm to choose \"$target\" inside WhatsApp.",
          noteOrLimitation = "Recipient not found in device contacts.",
        )
      }
    }
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
      noteOrLimitation =
        "Instagram ignores pre-filled text from external shares for security, so Mousomi copies your caption to clipboard to paste.",
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
      noteOrLimitation =
        "Facebook restricts silent auto-posting; Mousomi copies your draft text and opens the share composer for manual review.",
    )

  fun prepareYouTubeShare(
    descriptionOrQuery: String,
    mediaFilePath: String? = null,
  ): PendingSocialAction =
    PendingSocialAction(
      platform = SocialPlatform.YOUTUBE,
      captionOrMessage = descriptionOrQuery.trim(),
      mediaFilePath = mediaFilePath,
      summary =
        if (!mediaFilePath.isNullOrBlank()) {
          "Copy title/description and open YouTube upload Sharesheet."
        } else {
          "Copy video description & open YouTube."
        },
      noteOrLimitation =
        "YouTube upload/publishing requires explicit creator confirmation inside the YouTube app.",
    )

  fun buildWhatsAppIntent(phoneOrName: String, messageText: String): Intent {
    val waPackage = resolveInstalledWhatsAppPackage()
    val digits = phoneOrName.filter { it.isDigit() }
    return if (ContactResolver.looksLikeDirectPhoneNumber(phoneOrName) && digits.length >= 7) {
      val uri =
        Uri.parse(
          "https://wa.me/$digits?text=${Uri.encode(messageText.trim())}"
        )
      Intent(Intent.ACTION_VIEW, uri).apply {
        if (waPackage != null) {
          setPackage(waPackage)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      val send =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, messageText.trim())
          if (waPackage != null) {
            setPackage(waPackage)
          }
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      if (waPackage != null && verifier.verifyIntentResolvable(send)) send
      else Intent.createChooser(send.apply { setPackage(null) }, "Send via WhatsApp").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }

  fun buildShareToPlatformIntent(action: PendingSocialAction): Intent {
    val file = action.mediaFilePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    if (action.platform == SocialPlatform.YOUTUBE && file == null && isPlatformInstalled(SocialPlatform.YOUTUBE)) {
      val launch = context.packageManager.getLaunchIntentForPackage(SocialPlatform.YOUTUBE.packageName)
      if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (verifier.verifyIntentResolvable(launch)) return launch
      }
    }

    val base =
      if (file != null) {
        val uri =
          runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
          }.getOrNull()
        Intent(Intent.ACTION_SEND).apply {
          type = if (file.extension.lowercase() in setOf("mp4", "mov", "webm", "mkv")) "video/*" else "image/*"
          if (uri != null) {
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          if (action.captionOrMessage.isNotBlank()) {
            putExtra(Intent.EXTRA_TEXT, action.captionOrMessage)
            putExtra(Intent.EXTRA_SUBJECT, action.captionOrMessage.lineSequence().firstOrNull().orEmpty().take(90))
          }
        }
      } else {
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, action.captionOrMessage)
        }
      }

    val targetPkg =
      if (action.platform == SocialPlatform.WHATSAPP) resolveInstalledWhatsAppPackage()
      else action.platform.packageName.takeIf { visibilityManager.isPackageInstalled(it) }

    if (targetPkg != null) {
      base.setPackage(targetPkg)
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
    // Copy caption/description to clipboard so user can paste cleanly in Instagram/Facebook/YouTube
    if (action.captionOrMessage.isNotBlank() && action.platform != SocialPlatform.WHATSAPP) {
      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      cm?.setPrimaryClip(ClipData.newPlainText("${action.platform.displayName} Draft", action.captionOrMessage))
    }

    val intent =
      when (action.platform) {
        SocialPlatform.WHATSAPP ->
          if (!action.mediaFilePath.isNullOrBlank()) {
            buildShareToPlatformIntent(action)
          } else {
            buildWhatsAppIntent(action.recipientOrHandle, action.captionOrMessage)
          }
        SocialPlatform.INSTAGRAM,
        SocialPlatform.FACEBOOK,
        SocialPlatform.YOUTUBE -> buildShareToPlatformIntent(action)
      }

    val installed = isPlatformInstalled(action.platform)
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
          statusLabel = if (installed) "Composer Opened" else "Sharesheet Opened",
          verifiedDetail =
            if (installed) {
              "Opened ${action.platform.displayName} composer for your final confirmation." +
                (if (action.platform != SocialPlatform.WHATSAPP && action.captionOrMessage.isNotBlank()) " Draft copied to clipboard." else "")
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
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun generateSocialDraftTemplate(
      platform: SocialPlatform,
      topic: String,
    ): String {
      val cleanTopic = topic.trim().ifBlank { "New update" }
      val slugTag =
        cleanTopic
          .lowercase()
          .replace(Regex("[^a-z0-9]+"), "")
          .take(24)
          .ifBlank { "update" }
      return when (platform) {
        SocialPlatform.WHATSAPP -> cleanTopic
        SocialPlatform.INSTAGRAM ->
          "$cleanTopic\n\n#${slugTag} #DailyHighlights #Photography"
        SocialPlatform.FACEBOOK ->
          "$cleanTopic — would love to hear your thoughts in the comments!"
        SocialPlatform.YOUTUBE ->
          "$cleanTopic\n\nIn this video, we cover $cleanTopic.\n\n#${slugTag} #YouTube"
      }
    }

    fun tryParseSocialCommand(command: String): PendingSocialAction? {
      val clean = command.trim()
      val lower = clean.lowercase()

      // 1. WhatsApp: supports both phone numbers and named contacts
      if ("whatsapp" in lower && ("send" in lower || "message" in lower || "chat" in lower || lower.startsWith("whatsapp "))) {
        // Pattern A: "send a whatsapp message to Alice saying Hello" / "whatsapp Bob: Hello"
        val namedPattern =
          Regex(
            "^(?:please\\s+)?(?:can\\s+you\\s+)?(?:send\\s+(?:a\\s+)?)?(?:whatsapp\\s+(?:message\\s+)?(?:to\\s+)?|message\\s+(?:to\\s+)?)([A-Za-z][A-Za-z\\s.'-]{0,40}?)\\s+(?:saying|with\\s+message|:|that)\\s+(.+)$",
            RegexOption.IGNORE_CASE,
          ).find(clean)
        if (namedPattern != null) {
          val candidateRecipient =
            namedPattern.groupValues[1]
              .replace(Regex("\\bon\\s+whatsapp$", RegexOption.IGNORE_CASE), "")
              .trim()
          val msgBody = namedPattern.groupValues[2].trim()
          if (candidateRecipient.isNotBlank() && msgBody.isNotBlank()) {
            return PendingSocialAction(
              platform = SocialPlatform.WHATSAPP,
              recipientOrHandle = candidateRecipient,
              captionOrMessage = msgBody,
              summary = "Prepare WhatsApp message to $candidateRecipient: \"${msgBody.take(70)}\".",
            )
          }
        }

        // Pattern B: "message Alice on WhatsApp saying ..."
        val onWaPattern =
          Regex(
            "^(?:please\\s+)?(?:can\\s+you\\s+)?(?:send\\s+(?:a\\s+)?message\\s+to|message)\\s+(.+?)\\s+on\\s+whatsapp\\s+(?:saying|with\\s+message|:)\\s*(.+)$",
            RegexOption.IGNORE_CASE,
          ).find(clean)
        if (onWaPattern != null) {
          val recipient = onWaPattern.groupValues[1].trim()
          val body = onWaPattern.groupValues[2].trim()
          return PendingSocialAction(
            platform = SocialPlatform.WHATSAPP,
            recipientOrHandle = recipient,
            captionOrMessage = body,
            summary = "Prepare WhatsApp message to $recipient: \"${body.take(70)}\".",
          )
        }

        val afterPrefix =
          clean.replace(
            Regex("^(?:please\\s+)?(?:can\\s+you\\s+)?(?:send\\s+)?(?:a\\s+)?whatsapp\\s*(?:message)?\\s*(?:to)?\\s*", RegexOption.IGNORE_CASE),
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

      if ("instagram" in lower && ("post" in lower || "caption" in lower || "share" in lower || "hashtag" in lower)) {
        val caption =
          clean
            .replace(
              Regex("^(?:please\\s+)?(?:share|post|draft|create\\s+caption|write\\s+caption)\\s+(?:for|to|on)\\s+instagram\\s*:?\\s*", RegexOption.IGNORE_CASE),
              "",
            )
            .trim()
            .ifBlank { clean }
        return PendingSocialAction(
          platform = SocialPlatform.INSTAGRAM,
          captionOrMessage = caption,
          summary = "Prepare Instagram share & copy caption to clipboard: \"${caption.take(70)}\".",
          noteOrLimitation = "Mousomi opens the Instagram Sharesheet with your caption copied for review.",
        )
      }

      if ("facebook" in lower && ("post" in lower || "caption" in lower || "share" in lower)) {
        val caption =
          clean
            .replace(Regex("^(?:please\\s+)?(?:share|post|draft)\\s+(?:for|to|on)\\s+facebook\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { clean }
        return PendingSocialAction(
          platform = SocialPlatform.FACEBOOK,
          captionOrMessage = caption,
          summary = "Prepare Facebook share & copy post text: \"${caption.take(70)}\".",
          noteOrLimitation = "Mousomi opens Facebook's share composer for your final confirmation.",
        )
      }

      if ("youtube" in lower && ("share" in lower || "upload" in lower || "description" in lower || "post" in lower)) {
        val desc =
          clean
            .replace(
              Regex("^(?:please\\s+)?(?:share|upload|create\\s+description|post)\\s+(?:for|to|on)\\s+youtube\\s*:?\\s*", RegexOption.IGNORE_CASE),
              "",
            )
            .trim()
            .ifBlank { clean }
        return PendingSocialAction(
          platform = SocialPlatform.YOUTUBE,
          captionOrMessage = desc,
          summary = "Prepare YouTube share/upload draft & copy description: \"${desc.take(70)}\".",
          noteOrLimitation = "YouTube requires creator confirmation inside the YouTube app before publishing.",
        )
      }

      return null
    }
  }
}
