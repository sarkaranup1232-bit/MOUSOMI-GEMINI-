package com.example.core.device

import android.content.Context
import android.content.Intent
import android.provider.CallLog
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.permissions.PermissionCapabilityManager
import com.example.data.repository.AddOnRepository
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated Contacts lookup & formatted listing helper wrapping [ContactResolver].
 * Never requests READ_CONTACTS silently and always reports when permission or picker is needed.
 */
@Singleton
class ContactsService @Inject constructor(
  private val contactResolver: ContactResolver,
  private val phoneActionController: PhoneActionController,
  private val addOnRepository: AddOnRepository? = null,
) {

  fun hasContactsPermission(): Boolean = contactResolver.hasContactsPermission()

  suspend fun searchContactsReport(query: String): DeviceActionResult {
    val clean = query.trim()
    if (clean.isBlank()) {
      val openRes = phoneActionController.openContactsApp()
      addOnRepository?.recordDeviceAction(openRes)
      return openRes
    }
    val outcome = contactResolver.resolveContact(clean)
    val res =
      when (outcome) {
        is ContactLookupOutcome.UniqueMatch -> {
          val c = outcome.contact
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.SYSTEM_SETTINGS,
            command = "Search contact $clean",
            targetLabel = c.displayName,
            success = true,
            statusLabel = "Contact Found",
            verifiedDetail = "${c.displayName} • ${c.phoneNumber}. Say 'Call ${c.displayName}' or 'Send SMS to ${c.displayName}' to prepare an action.",
          )
        }
        is ContactLookupOutcome.AmbiguousMatches -> {
          val list = outcome.candidates.joinToString("\n") { "• ${it.displayName} (${it.phoneNumber})" }
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.SYSTEM_SETTINGS,
            command = "Search contact $clean",
            targetLabel = "${outcome.candidates.size} Contacts",
            success = true,
            statusLabel = "Matches Found",
            verifiedDetail = "Matching contacts for \"$clean\":\n$list",
          )
        }
        is ContactLookupOutcome.PermissionRequired ->
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.SYSTEM_SETTINGS,
            command = "Search contact $clean",
            targetLabel = clean,
            success = false,
            statusLabel = "Contacts Permission Required",
            verifiedDetail = "READ_CONTACTS is not granted. Enable Contacts access in Permissions & Privacy or pick a contact manually.",
          )
        is ContactLookupOutcome.NotFound ->
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.SYSTEM_SETTINGS,
            command = "Search contact $clean",
            targetLabel = clean,
            success = false,
            statusLabel = "No Match",
            verifiedDetail = "No device contact matched \"$clean\".",
          )
      }
    addOnRepository?.recordDeviceAction(res)
    return res
  }
}

/**
 * Dedicated Calling & Telecom Assistant wrapping [PhoneActionController].
 * - Opens Phone Dialer safely via ACTION_DIAL (user taps Call).
 * - Opens Call History safely via CallLog.Calls.CONTENT_URI without reading/scraping private call logs.
 * - Reports Telecom answer/end capability honestly via [PermissionCapabilityManager].
 */
@Singleton
class CallingService @Inject constructor(
  @ApplicationContext private val context: Context,
  private val phoneActionController: PhoneActionController,
  private val verifier: DeviceActionVerifier,
  private val capabilityManager: PermissionCapabilityManager,
  private val addOnRepository: AddOnRepository? = null,
) {

  fun prepareDial(queryOrNumber: String): PendingPhoneAction =
    phoneActionController.resolveAndPreparePhoneAction(
      PhoneActionController.tryParseConfirmationCommand("call $queryOrNumber")
        ?: phoneActionController.prepareDialNumber(queryOrNumber)
    )

  suspend fun confirmAndOpenDialer(action: PendingPhoneAction): DeviceActionResult =
    phoneActionController.executeConfirmedAction(action)

  suspend fun openCallHistory(): DeviceActionResult {
    val intent =
      Intent(Intent.ACTION_VIEW).apply {
        type = CallLog.Calls.CONTENT_TYPE
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    val resolved =
      if (verifier.verifyIntentResolvable(intent)) intent
      else Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    val res =
      if (!verifier.verifyIntentResolvable(resolved)) {
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.SYSTEM_SETTINGS,
          command = "Open Call History",
          targetLabel = "Call History",
          success = false,
          statusLabel = "Unavailable",
          verifiedDetail = "No Android Phone app resolved to display call history.",
        )
      } else {
        runCatching {
          context.startActivity(resolved)
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.SYSTEM_SETTINGS,
            command = "Open Call History",
            targetLabel = "Call History",
            success = true,
            statusLabel = "Dialer / Call Log Opened",
            verifiedDetail = "Opened system Phone / Call History app. Private call log entries are not scraped without explicit Dialer role.",
          )
        }.getOrElse { err ->
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.SYSTEM_SETTINGS,
            command = "Open Call History",
            targetLabel = "Call History",
            success = false,
            statusLabel = "Blocked",
            verifiedDetail = err.message ?: "Could not open Call History.",
          )
        }
      }
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  suspend fun reportCallAnswerEndCapability(): DeviceActionResult {
    val snapshot = capabilityManager.inspectRuntimeCapabilities()
    val res =
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Call Answer / End Check",
        targetLabel = "Telecom Capability",
        success = snapshot.canCallAnswerOrEndDirectly,
        statusLabel = if (snapshot.canCallAnswerOrEndDirectly) "Supported" else "System Restricted",
        verifiedDetail = snapshot.answerEndLimitationReason,
      )
    addOnRepository?.recordDeviceAction(res)
    return res
  }
}

/**
 * Dedicated SMS & Notification-Assisted Messaging Service.
 * Always drafts and prepares the system SMS composer / Sharesheet behind confirmation.
 */
@Singleton
class MessagingService @Inject constructor(
  private val phoneActionController: PhoneActionController,
  private val intentManager: AndroidIntentManager,
  private val notificationEngine: NotificationIntelligenceEngine,
  @ApplicationContext private val context: Context,
) {

  fun prepareSmsDraft(recipientNameOrNumber: String, messageBody: String): PendingPhoneAction =
    phoneActionController.resolveAndPreparePhoneAction(
      phoneActionController.prepareSms(
        phoneNumber = if (ContactResolver.looksLikeDirectPhoneNumber(recipientNameOrNumber)) recipientNameOrNumber else "",
        messageBody = messageBody,
        contactLabel = recipientNameOrNumber,
      )
    )

  suspend fun confirmAndOpenSmsComposer(action: PendingPhoneAction): DeviceActionResult =
    phoneActionController.executeConfirmedAction(action)

  fun openSystemShareSheet(text: String, title: String = "Share via Mousomi AI"): Boolean {
    val intent = intentManager.buildShareTextIntent(text, title)
    return runCatching {
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun latestNotificationReplySuggestions(): List<String> =
    notificationEngine.summaries.value
      .take(4)
      .mapNotNull { summary ->
        summary.suggestedDraftReply?.let { draft ->
          "${summary.appLabel} (${summary.title}): $draft"
        }
      }
}
