package com.example.core.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.data.repository.AddOnRepository
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated WhatsApp Integration Module:
 * - Launch WhatsApp / specific contact chat (https://wa.me/...)
 * - Prepare text, image, and document Sharesheet payloads (behind explicit confirmation)
 * - Launch WhatsApp VoIP/Video call via official ContactsContract Data URI when READ_CONTACTS is granted
 * - Read WhatsApp notification summaries when Notification Listener is enabled by user
 */
@Singleton
class WhatsAppIntegration @Inject constructor(
  @ApplicationContext private val context: Context,
  private val socialIntegrationManager: SocialIntegrationManager,
  private val appLauncherManager: AppLauncherManager,
  private val notificationEngine: NotificationIntelligenceEngine,
  private val verifier: DeviceActionVerifier,
  private val addOnRepository: AddOnRepository? = null,
) {

  fun isInstalled(): Boolean = socialIntegrationManager.isPlatformInstalled(SocialPlatform.WHATSAPP)

  fun openHome(): DeviceActionResult = appLauncherManager.launchAppByName("WhatsApp")

  fun prepareMessage(recipientNameOrNumber: String, messageText: String): PendingSocialAction =
    socialIntegrationManager.resolveAndPrepareSocialAction(
      socialIntegrationManager.prepareWhatsAppMessage(recipientNameOrNumber, messageText)
    )

  suspend fun launchWhatsAppCallByName(contactName: String, isVideoCall: Boolean = false): DeviceActionResult {
    val pkg = socialIntegrationManager.resolveInstalledWhatsAppPackage()
      ?: return recordOutcome(
        command = "WhatsApp call $contactName",
        target = contactName,
        success = false,
        status = "WhatsApp Not Installed",
        detail = "WhatsApp is not installed on this device.",
      )

    val hasContacts =
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasContacts) {
      openHome()
      return recordOutcome(
        command = "WhatsApp call $contactName",
        target = contactName,
        success = false,
        status = "Contacts Permission Required",
        detail =
          "Android requires Contacts permission to query a contact's WhatsApp VoIP Data row. Opened WhatsApp so you can tap Call directly.",
      )
    }

    val targetMime =
      if (isVideoCall) "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
      else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
    var matchedDataId: Long? = null
    var matchedName: String = contactName
    runCatching {
      context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DISPLAY_NAME),
        "${ContactsContract.Data.MIMETYPE} = ? AND LOWER(${ContactsContract.Data.DISPLAY_NAME}) LIKE ?",
        arrayOf(targetMime, "%${contactName.trim().lowercase()}%"),
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          matchedDataId = cursor.getLong(0)
          matchedName = cursor.getString(1).orEmpty().ifBlank { contactName }
        }
      }
    }

    val dataId = matchedDataId
      ?: run {
        val fallbackChat = socialIntegrationManager.resolveAndPrepareSocialAction(
          socialIntegrationManager.prepareWhatsAppMessage(contactName, "")
        )
        return if (!fallbackChat.isContactAmbiguous && ContactResolver.looksLikeDirectPhoneNumber(fallbackChat.recipientOrHandle)) {
          val waChatIntent = socialIntegrationManager.buildWhatsAppIntent(fallbackChat.recipientOrHandle, "")
          context.startActivity(waChatIntent)
          recordOutcome(
            command = "WhatsApp call $contactName",
            target = contactName,
            success = true,
            status = "WhatsApp Chat Opened",
            detail = "No direct WhatsApp VoIP Data row matched '$contactName'; opened their WhatsApp chat so you can tap Call.",
          )
        } else {
          openHome()
          recordOutcome(
            command = "WhatsApp call $contactName",
            target = contactName,
            success = false,
            status = "Select Contact in WhatsApp",
            detail = "Could not resolve a direct WhatsApp VoIP ID for '$contactName'. Opened WhatsApp instead.",
          )
        }
      }

    val callIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, dataId.toString()), targetMime)
        setPackage(pkg)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    if (!verifier.verifyIntentResolvable(callIntent)) {
      openHome()
      return recordOutcome(
        command = "WhatsApp call $contactName",
        target = matchedName,
        success = false,
        status = "Opened WhatsApp",
        detail = "WhatsApp rejected direct call URI on this device version. Opened WhatsApp so you can confirm the call manually.",
      )
    }
    return runCatching {
      context.startActivity(callIntent)
      recordOutcome(
        command = "WhatsApp call $contactName",
        target = matchedName,
        success = true,
        status = "WhatsApp Call Launched",
        detail = "Opened WhatsApp ${if (isVideoCall) "video" else "voice"} call screen for $matchedName.",
      )
    }.getOrElse { err ->
      recordOutcome(
        command = "WhatsApp call $contactName",
        target = matchedName,
        success = false,
        status = "Failed",
        detail = err.message ?: "Unable to launch WhatsApp call.",
      )
    }
  }

  fun recentWhatsAppNotificationSummaries(): List<String> =
    notificationEngine.summaries.value
      .filter { it.packageName == SocialPlatform.WHATSAPP.packageName || it.packageName == SocialIntegrationManager.WHATSAPP_BUSINESS_PACKAGE }
      .take(5)
      .map { "${it.title}: ${it.summary}" }

  private suspend fun recordOutcome(
    command: String,
    target: String,
    success: Boolean,
    status: String,
    detail: String,
  ): DeviceActionResult {
    val res =
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.LAUNCH_APP,
        command = command,
        targetLabel = target,
        packageName = SocialPlatform.WHATSAPP.packageName,
        success = success,
        statusLabel = status,
        verifiedDetail = detail,
      )
    addOnRepository?.recordDeviceAction(res)
    return res
  }
}

/**
 * Dedicated Instagram Integration Module:
 * Open Instagram, generate captions & hashtags, share via official Sharesheet.
 */
@Singleton
class InstagramIntegration @Inject constructor(
  private val socialIntegrationManager: SocialIntegrationManager,
  private val appLauncherManager: AppLauncherManager,
) {
  fun openInstagram(): DeviceActionResult = appLauncherManager.launchAppByName("Instagram")

  fun prepareCaptionAndShare(topic: String, mediaFilePath: String? = null): PendingSocialAction {
    val draft = SocialIntegrationManager.generateSocialDraftTemplate(SocialPlatform.INSTAGRAM, topic)
    return socialIntegrationManager.prepareInstagramPost(draft, mediaFilePath)
  }
}

/**
 * Dedicated Facebook Integration Module:
 * Open Facebook, draft posts, and open official Sharesheet workflows.
 */
@Singleton
class FacebookIntegration @Inject constructor(
  private val socialIntegrationManager: SocialIntegrationManager,
  private val appLauncherManager: AppLauncherManager,
) {
  fun openFacebook(): DeviceActionResult = appLauncherManager.launchAppByName("Facebook")

  fun preparePostAndShare(topic: String, mediaFilePath: String? = null): PendingSocialAction {
    val draft = SocialIntegrationManager.generateSocialDraftTemplate(SocialPlatform.FACEBOOK, topic)
    return socialIntegrationManager.prepareFacebookPost(draft, mediaFilePath)
  }
}

/**
 * Dedicated YouTube Integration Module:
 * Open YouTube, search videos inside the official YouTube app/web fallback, launch video links, and share links.
 */
@Singleton
class YouTubeIntegration @Inject constructor(
  @ApplicationContext private val context: Context,
  private val socialIntegrationManager: SocialIntegrationManager,
  private val appLauncherManager: AppLauncherManager,
  private val verifier: DeviceActionVerifier,
  private val addOnRepository: AddOnRepository? = null,
) {
  fun openYouTube(): DeviceActionResult = appLauncherManager.launchAppByName("YouTube")

  suspend fun searchVideos(query: String): DeviceActionResult {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return openYouTube()

    val appSearch =
      Intent(Intent.ACTION_SEARCH).apply {
        setPackage(SocialPlatform.YOUTUBE.packageName)
        putExtra("query", cleanQuery)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    val webSearch =
      Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}"),
      ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    val targetIntent = if (verifier.verifyIntentResolvable(appSearch)) appSearch else webSearch
    val res =
      if (!verifier.verifyIntentResolvable(targetIntent)) {
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Search YouTube: $cleanQuery",
          targetLabel = "YouTube",
          packageName = SocialPlatform.YOUTUBE.packageName,
          success = false,
          statusLabel = "Unavailable",
          verifiedDetail = "Neither YouTube nor a browser resolved to search '$cleanQuery'.",
        )
      } else {
        runCatching {
          context.startActivity(targetIntent)
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.LAUNCH_APP,
            command = "Search YouTube: $cleanQuery",
            targetLabel = "YouTube Search",
            packageName = SocialPlatform.YOUTUBE.packageName,
            success = true,
            statusLabel = "YouTube Search Opened",
            verifiedDetail = "Opened YouTube search results for \"$cleanQuery\".",
          )
        }.getOrElse { err ->
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.LAUNCH_APP,
            command = "Search YouTube: $cleanQuery",
            targetLabel = "YouTube",
            success = false,
            statusLabel = "Failed",
            verifiedDetail = err.message ?: "Unable to launch YouTube search.",
          )
        }
      }
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  suspend fun launchVideoUrl(videoUrl: String): DeviceActionResult {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl.trim())).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    val res =
      runCatching {
        context.startActivity(intent)
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Open YouTube video",
          targetLabel = "YouTube Video",
          packageName = SocialPlatform.YOUTUBE.packageName,
          success = true,
          statusLabel = "Video Opened",
          verifiedDetail = "Opened video URL in YouTube / browser.",
        )
      }.getOrElse { err ->
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Open YouTube video",
          targetLabel = "YouTube Video",
          success = false,
          statusLabel = "Failed",
          verifiedDetail = err.message ?: "Could not open video URL.",
        )
      }
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  fun prepareVideoShare(descriptionOrUrl: String, mediaFilePath: String? = null): PendingSocialAction =
    socialIntegrationManager.prepareYouTubeShare(descriptionOrUrl, mediaFilePath)
}
