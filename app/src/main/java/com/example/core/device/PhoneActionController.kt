package com.example.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.data.repository.AddOnRepository
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class PendingPhoneActionType(val title: String) {
  DIAL_PHONE("Open Phone Dialer"),
  SEND_SMS("Open SMS Composer"),
}

data class PendingPhoneAction(
  val id: String = "ph_${UUID.randomUUID()}",
  val type: PendingPhoneActionType,
  val targetNumber: String,
  val messageBody: String = "",
  val displayLabel: String,
  val summary: String,
  val candidateContacts: List<ResolvedContact> = emptyList(),
  val isContactAmbiguous: Boolean = false,
  val needsContactsPermission: Boolean = false,
)

/**
 * Verified Android Device & Phone Action Controller.
 * - Uses safe zero-permission ACTION_DIAL & ACTION_SENDTO intents (never CALL_PHONE or silent SMS).
 * - External calling / messaging ALWAYS returns a [PendingPhoneAction] requiring explicit user
 *   confirmation before launching the system dialer or SMS composer.
 * - Supports resolving named contacts via [ContactResolver] when READ_CONTACTS is granted, or
 *   offering direct Android Contact Picker / phone-number fallback when permission is not granted.
 */
@Singleton
class PhoneActionController @Inject constructor(
  @ApplicationContext private val context: Context,
  private val appLauncherManager: AppLauncherManager,
  private val verifier: DeviceActionVerifier,
  private val addOnRepository: AddOnRepository? = null,
  private val contactResolver: ContactResolver? = null,
) {

  fun prepareDialNumber(phoneNumber: String, contactLabel: String = ""): PendingPhoneAction {
    val cleanNumber = sanitizePhoneNumber(phoneNumber)
    val label = contactLabel.trim().ifBlank { cleanNumber.ifBlank { "Phone Number" } }
    return PendingPhoneAction(
      type = PendingPhoneActionType.DIAL_PHONE,
      targetNumber = cleanNumber,
      displayLabel = label,
      summary =
        if (cleanNumber.isNotBlank()) "Prepare Android Dialer for $label ($cleanNumber). You tap Call yourself."
        else "Open Android Phone Dialer.",
    )
  }

  fun prepareSms(
    phoneNumber: String,
    messageBody: String,
    contactLabel: String = "",
  ): PendingPhoneAction {
    val cleanNumber = sanitizePhoneNumber(phoneNumber)
    val label = contactLabel.trim().ifBlank { cleanNumber.ifBlank { "SMS Recipient" } }
    return PendingPhoneAction(
      type = PendingPhoneActionType.SEND_SMS,
      targetNumber = cleanNumber,
      messageBody = messageBody.trim(),
      displayLabel = label,
      summary =
        "Open system SMS composer to $label" +
          if (messageBody.isNotBlank()) " with draft: \"${messageBody.trim().take(80)}\"" else ".",
    )
  }

  fun resolveAndPreparePhoneAction(initial: PendingPhoneAction): PendingPhoneAction {
    val resolver = contactResolver ?: return initial
    if (initial.targetNumber.isNotBlank() && ContactResolver.looksLikeDirectPhoneNumber(initial.targetNumber)) {
      return initial
    }
    val query = initial.displayLabel.trim()
    if (query.isBlank()) return initial

    return when (val lookup = resolver.resolveContact(query)) {
      is ContactLookupOutcome.UniqueMatch -> {
        val c = lookup.contact
        if (initial.type == PendingPhoneActionType.DIAL_PHONE) {
          prepareDialNumber(c.phoneNumber, c.displayName)
        } else {
          prepareSms(c.phoneNumber, initial.messageBody, c.displayName)
        }
      }
      is ContactLookupOutcome.AmbiguousMatches -> {
        initial.copy(
          candidateContacts = lookup.candidates,
          isContactAmbiguous = true,
          needsContactsPermission = false,
          summary = "Multiple contacts match \"$query\". Choose the exact contact to continue.",
        )
      }
      is ContactLookupOutcome.PermissionRequired -> {
        initial.copy(
          needsContactsPermission = true,
          summary =
            "Grant Contacts access or tap Pick Contact so Mousomi can find \"$query\" before opening ${initial.type.title}.",
        )
      }
      is ContactLookupOutcome.NotFound -> {
        initial.copy(
          needsContactsPermission = false,
          summary = "No contact matched \"$query\" with a phone number. Enter a number or tap Pick Contact.",
        )
      }
    }
  }

  fun buildDialIntent(phoneNumber: String): Intent {
    val clean = sanitizePhoneNumber(phoneNumber)
    val uri = if (clean.isNotBlank()) Uri.parse("tel:$clean") else Uri.parse("tel:")
    return Intent(Intent.ACTION_DIAL, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
  }

  fun buildSmsIntent(phoneNumber: String, body: String): Intent {
    val clean = sanitizePhoneNumber(phoneNumber)
    val uri = Uri.parse("smsto:${Uri.encode(clean)}")
    return Intent(Intent.ACTION_SENDTO, uri).apply {
      if (body.isNotBlank()) putExtra("sms_body", body.trim())
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildMapsSearchIntent(query: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query.trim())}")).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

  fun buildNavigationIntent(destination: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(destination.trim())}")).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

  fun buildAlarmsIntent(): Intent =
    Intent(AlarmClock.ACTION_SHOW_ALARMS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

  fun buildShareMediaIntent(filePath: String, mimeType: String = "image/*", caption: String = ""): Intent? {
    val file = File(filePath)
    if (!file.exists()) return null
    val contentUri =
      runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      }.getOrNull() ?: return null
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, contentUri)
        if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    return Intent.createChooser(send, "Share via Mousomi AI").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  suspend fun executeConfirmedAction(action: PendingPhoneAction): DeviceActionResult {
    val intent =
      when (action.type) {
        PendingPhoneActionType.DIAL_PHONE -> buildDialIntent(action.targetNumber)
        PendingPhoneActionType.SEND_SMS -> buildSmsIntent(action.targetNumber, action.messageBody)
      }
    val res = dispatchVerifiedIntent(
      intent = intent,
      category = DeviceActionCategory.SYSTEM_SETTINGS,
      command = action.type.title,
      targetLabel = action.displayLabel,
      successStatus = "Composer Opened",
      successDetail = action.summary,
    )
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  suspend fun openMapsOrNavigation(query: String, turnByTurnNavigation: Boolean): DeviceActionResult {
    val primary =
      if (turnByTurnNavigation) buildNavigationIntent(query) else buildMapsSearchIntent(query)
    val resolvedIntent =
      if (verifier.verifyIntentResolvable(primary)) primary
      else buildMapsSearchIntent(query)
    val res =
      dispatchVerifiedIntent(
        intent = resolvedIntent,
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = if (turnByTurnNavigation) "Navigate to $query" else "Maps: $query",
        targetLabel = query.ifBlank { "Maps" },
        successStatus = "Maps Opened",
        successDetail = "Opened Maps for '$query'.",
      )
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  suspend fun openAlarms(): DeviceActionResult {
    val intent = buildAlarmsIntent()
    val res =
      dispatchVerifiedIntent(
        intent = intent,
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Open Alarms",
        targetLabel = "Clock / Alarms",
        successStatus = "Clock Opened",
        successDetail = "Opened Android Clock alarms screen.",
      )
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  fun openContactsApp(): DeviceActionResult =
    openQuickSetting(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI), "Contacts")

  fun openQuickSetting(actionIntent: Intent, label: String): DeviceActionResult {
    actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return dispatchVerifiedIntent(
      intent = actionIntent,
      category = DeviceActionCategory.SYSTEM_SETTINGS,
      command = "Open $label",
      targetLabel = label,
      successStatus = "Opened",
      successDetail = "Opened $label.",
    )
  }

  fun openWifiSettings(): DeviceActionResult =
    openQuickSetting(Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi Settings")

  fun openBluetoothSettings(): DeviceActionResult =
    openQuickSetting(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth Settings")

  fun openCamera(): DeviceActionResult =
    openQuickSetting(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera")

  fun openInstalledApp(name: String): DeviceActionResult =
    appLauncherManager.launchAppByName(name)

  private fun dispatchVerifiedIntent(
    intent: Intent,
    category: DeviceActionCategory,
    command: String,
    targetLabel: String,
    successStatus: String,
    successDetail: String,
  ): DeviceActionResult {
    if (!verifier.verifyIntentResolvable(intent)) {
      return DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = category,
        command = command,
        targetLabel = targetLabel,
        success = false,
        statusLabel = "App Unavailable",
        verifiedDetail = "No compatible Android app handles $targetLabel on this device.",
      )
    }
    return runCatching {
      context.startActivity(intent)
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = category,
        command = command,
        targetLabel = targetLabel,
        success = true,
        statusLabel = successStatus,
        verifiedDetail = successDetail,
      )
    }.getOrElse { err ->
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = category,
        command = command,
        targetLabel = targetLabel,
        success = false,
        statusLabel = "Blocked",
        verifiedDetail = err.message ?: "Android blocked launching $targetLabel.",
      )
    }
  }

  companion object {
    fun sanitizePhoneNumber(raw: String): String =
      raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.take(24)

    fun tryParseConfirmationCommand(command: String): PendingPhoneAction? {
      val clean = command.trim()
      val lower = clean.lowercase()

      // 1. Numeric SMS command
      val smsMatch =
        Regex(
          "^(?:please\\s+)?(?:send\\s+(?:an?\\s+)?(?:sms|text\\s+message)|text|sms)\\s+(?:to\\s+)?([+\\d][\\d\\s()-]{2,20})(?:\\s+(?:saying|with\\s+message|:)\\s+(.+))?$",
          RegexOption.IGNORE_CASE,
        ).find(clean)
      if (smsMatch != null) {
        val number = sanitizePhoneNumber(smsMatch.groupValues[1])
        val body = smsMatch.groupValues.getOrNull(2).orEmpty().trim()
        if (number.isNotBlank()) {
          return PendingPhoneAction(
            type = PendingPhoneActionType.SEND_SMS,
            targetNumber = number,
            messageBody = body,
            displayLabel = number,
            summary = "Prepare SMS composer to $number" + if (body.isNotBlank()) " (\"${body.take(70)}\")" else ".",
          )
        }
      }

      // 2. Named-contact SMS command: "Send SMS to Alice saying I will be late"
      val namedSmsMatch =
        Regex(
          "^(?:please\\s+)?(?:send\\s+(?:an?\\s+)?(?:sms|text\\s+message)|text|sms)\\s+(?:to\\s+)?([A-Za-z][A-Za-z\\s.'-]{1,40}?)\\s+(?:saying|with\\s+message|:)\\s+(.+)$",
          RegexOption.IGNORE_CASE,
        ).find(clean)
      if (namedSmsMatch != null) {
        val name = namedSmsMatch.groupValues[1].trim()
        val body = namedSmsMatch.groupValues[2].trim()
        if (name.isNotBlank()) {
          return PendingPhoneAction(
            type = PendingPhoneActionType.SEND_SMS,
            targetNumber = "",
            messageBody = body,
            displayLabel = name,
            summary = "Resolve contact \"$name\" and open SMS composer (\"${body.take(70)}\").",
          )
        }
      }

      // 3. Numeric Dial / Call command
      if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ") ||
        lower.startsWith("please call ") || lower.startsWith("please dial ")
      ) {
        val token = Regex("([+\\d][\\d\\s()-]{1,20}\\d|\\d{3,})").find(clean)?.groupValues?.getOrNull(1).orEmpty()
        val digits = sanitizePhoneNumber(token)
        if (digits.length >= 3) {
          return PendingPhoneAction(
            type = PendingPhoneActionType.DIAL_PHONE,
            targetNumber = digits,
            displayLabel = digits,
            summary = "Open Android Phone Dialer with $digits (you tap Call yourself).",
          )
        }
        val targetName =
          clean
            .replace(Regex("^(?:please\\s+)?(?:call|dial|phone)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd('.', '!', '?')
        if (targetName.length in 2..48 && targetName.any { it.isLetter() }) {
          return PendingPhoneAction(
            type = PendingPhoneActionType.DIAL_PHONE,
            targetNumber = "",
            displayLabel = targetName,
            summary = "Look up contact \"$targetName\" and open Android Phone Dialer (you tap Call yourself).",
          )
        }
      }
      return null
    }
  }
}
