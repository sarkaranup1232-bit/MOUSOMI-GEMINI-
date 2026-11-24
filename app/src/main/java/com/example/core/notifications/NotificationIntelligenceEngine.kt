package com.example.core.notifications

import android.media.AudioManager
import com.example.core.security.KeystoreCryptoManager
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.NotificationSummaryRecord
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class NotificationIntelligenceEngine @Inject constructor(
  private val cryptoManager: KeystoreCryptoManager,
) {
  private val _summaries = MutableStateFlow<List<NotificationSummaryRecord>>(emptyList())
  val summaries: StateFlow<List<NotificationSummaryRecord>> = _summaries.asStateFlow()

  private val _notificationPulseTick = MutableStateFlow(0L)
  val notificationPulseTick: StateFlow<Long> = _notificationPulseTick.asStateFlow()

  private val _configState = MutableStateFlow(NotificationIntelligenceConfig())
  val configState: StateFlow<NotificationIntelligenceConfig> = _configState.asStateFlow()

  private var config = NotificationIntelligenceConfig()

  fun updateConfig(newConfig: NotificationIntelligenceConfig) {
    config = newConfig
    _configState.value = newConfig
    if (!newConfig.enabled && !newConfig.readAloudEnabled) {
      clearAll()
    }
  }

  fun onNotificationPosted(
    packageName: String,
    appLabel: String,
    title: String,
    bodyText: String,
  ): NotificationSummaryRecord? {
    if (!config.enabled && !config.readAloudEnabled) return null
    if (packageName in config.excludedPackages) return null
    if (config.allowedPackages.isNotEmpty() && packageName !in config.allowedPackages) {
      return null
    }

    val combined = "$title $bodyText".trim()
    if (combined.isBlank()) return null

    // Security policy: strictly block OTPs, 2FA verification codes, PINs, passwords, and tokens
    if (config.redactSensitiveCodes && containsOtpOrPrivateCode(combined)) {
      return null
    }

    val importance = classifyImportance(title, bodyText)
    val conciseSummary =
      bodyText.replace(Regex("\\s+"), " ").trim().take(180).ifBlank { title }

    // Never auto-send; generate manual draft suggestions only when enabled
    val draftReply =
      if (config.generateDraftRepliesForReview && looksLikeMessage(title, bodyText)) {
        "Thanks for the update — I'll review this shortly."
      } else {
        null
      }

    val record =
      NotificationSummaryRecord(
        id = "notif_${UUID.randomUUID()}",
        packageName = packageName,
        appLabel = appLabel.ifBlank { packageName.substringAfterLast('.') },
        title = title.ifBlank { appLabel },
        summary = conciseSummary,
        importanceLevel = importance,
        suggestedDraftReply = draftReply,
        receivedAtMs = System.currentTimeMillis(),
      )

    if (config.enabled) {
      _summaries.update { current -> (listOf(record) + current).take(25) }
      _notificationPulseTick.value = System.currentTimeMillis()
    }
    return record
  }

  /**
   * Builds a spoken notification announcement if Read-Aloud is enabled, not paused, not in
   * silent/vibrate mode, not excluded, and safe from OTP/credentials.
   */
  fun buildReadAloudAnnouncement(
    record: NotificationSummaryRecord,
    ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    isMicCurrentlyListening: Boolean = false,
  ): String? {
    if (!config.readAloudEnabled || config.readAloudPaused) return null
    if (ringerMode != AudioManager.RINGER_MODE_NORMAL) return null
    if (isMicCurrentlyListening) return null
    if (record.packageName in config.excludedPackages) return null

    val sender = record.title.replace(Regex("\\s+"), " ").trim().take(48)
    val appName = record.appLabel.replace(Regex("\\s+"), " ").trim().take(32)
    val isWhatsApp =
      record.packageName == "com.whatsapp" || record.packageName == "com.whatsapp.w4b"

    if (!config.readAloudContentEnabled) {
      return if (isWhatsApp && sender.isNotBlank()) {
        "New WhatsApp message from $sender."
      } else if (sender.isNotBlank() && sender != appName) {
        "New notification from $appName: $sender."
      } else {
        "New notification from $appName."
      }
    }

    val combined = "${record.title} ${record.summary}"
    if (config.redactSensitiveCodes && containsOtpOrPrivateCode(combined)) {
      return "Sensitive message from $appName withheld for security."
    }

    val spokenBody = record.summary.take(140)
    return if (isWhatsApp && sender.isNotBlank()) {
      "WhatsApp from $sender: $spokenBody"
    } else if (sender.isNotBlank() && sender != appName) {
      "$appName from $sender: $spokenBody"
    } else {
      "$appName: $spokenBody"
    }
  }

  fun setReadAloudPaused(paused: Boolean) {
    val updated = config.copy(readAloudPaused = paused)
    updateConfig(updated)
  }

  fun dismissSummary(id: String) {
    _summaries.update { list -> list.filterNot { it.id == id } }
  }

  fun clearAll() {
    _summaries.value = emptyList()
  }

  fun containsOtpOrPrivateCode(text: String): Boolean {
    if (cryptoManager.containsSensitiveCredentials(text)) return true
    val otpKeywords =
      Regex(
        "\\b(otp|verification code|verify code|2fa|one[- ]time|auth code|security code|pin is|do not share)\\b",
        RegexOption.IGNORE_CASE,
      )
    val digitsCode = Regex("\\b\\d{4,8}\\b")
    return otpKeywords.containsMatchIn(text) && digitsCode.containsMatchIn(text)
  }

  private fun classifyImportance(title: String, body: String): String {
    val text = "$title $body".lowercase()
    return when {
      text.containsAny("urgent", "missed call", "deadline", "alert", "emergency") -> "High"
      body.contains("?") ||
        text.containsAny("message", "reply", "meeting", "calendar", "reminder", "can you", "please") ->
        "Normal"
      else -> "Low"
    }
  }

  private fun looksLikeMessage(title: String, body: String): Boolean {
    val lower = "$title $body".lowercase()
    return body.contains("?") || lower.containsAny("can you", "please", "let me know", "when", "hey")
  }

  private fun String.containsAny(vararg tokens: String): Boolean = tokens.any { contains(it) }
}
