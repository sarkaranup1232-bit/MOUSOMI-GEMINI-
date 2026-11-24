package com.example.core.agent

import com.example.core.accessibility.AccessibilityActionResult
import com.example.core.accessibility.AccessibilityAgentController
import com.example.core.accessibility.AccessibilityGlobalAction
import com.example.core.device.AgentCommandIntent
import com.example.core.device.AssistantAgentCommandRouter
import com.example.core.device.PendingPhoneAction
import com.example.core.device.PendingSocialAction
import com.example.core.device.PhoneActionController
import com.example.core.device.SocialIntegrationManager
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.domain.repository.SettingsRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentToolDomain(val title: String) {
  PHONE_CONTROL("Phone & Device Control"),
  CONTACTS_AND_MESSAGING("Contacts, Dialer & SMS"),
  SOCIAL_MEDIA("WhatsApp & Social Platforms"),
  NOTIFICATION_ASSISTANT("Notification Summaries & Read-Aloud"),
  ACCESSIBILITY_UI("User-Authorized Accessibility Actions"),
  SCREEN_CAPTURE_AND_SELECTION("Screen Share, Recording & Drawing Crop"),
  ALARMS_AND_REMINDERS("Alarms, Timers & Reminders"),
  MULTIMODAL_AI_CHAT("Multimodal AI Understanding"),
}

data class AgentToolDescriptor(
  val id: String,
  val domain: AgentToolDomain,
  val displayName: String,
  val description: String,
  val requiredPermissionNote: String,
  val requiresExplicitConfirmation: Boolean,
)

object AgentToolRegistry {
  val allTools: List<AgentToolDescriptor> =
    listOf(
      AgentToolDescriptor(
        id = "screen_selection_crop",
        domain = AgentToolDomain.SCREEN_CAPTURE_AND_SELECTION,
        displayName = "Screen Selection & Freehand Crop",
        description = "Crops rectangle or masked freehand selections from authorized frames and attaches to Chat Bar.",
        requiredPermissionNote = "Consented MediaProjection (or internal window capture for Mousomi UI)",
        requiresExplicitConfirmation = false,
      ),
      AgentToolDescriptor(
        id = "phone_control",
        domain = AgentToolDomain.PHONE_CONTROL,
        displayName = "App & System Settings Control",
        description = "Launches installed apps, system settings panels, camera, maps, and verifies outcomes honestly.",
        requiredPermissionNote = "Scoped Android <queries> package visibility",
        requiresExplicitConfirmation = false,
      ),
      AgentToolDescriptor(
        id = "contacts_dial_sms",
        domain = AgentToolDomain.CONTACTS_AND_MESSAGING,
        displayName = "Contacts, Dialer & SMS",
        description = "Resolves named contacts (with disambiguation) and opens Dialer/SMS with user confirmation.",
        requiredPermissionNote = "Optional contextual READ_CONTACTS or zero-permission Contact Picker",
        requiresExplicitConfirmation = true,
      ),
      AgentToolDescriptor(
        id = "social_media_share",
        domain = AgentToolDomain.SOCIAL_MEDIA,
        displayName = "WhatsApp, Instagram, Facebook & YouTube",
        description = "Prepares messages, drafts, and media Sharesheet intents with confirmation before handoff.",
        requiredPermissionNote = "Explicit user confirmation + installed package visibility",
        requiresExplicitConfirmation = true,
      ),
      AgentToolDescriptor(
        id = "notification_read_aloud",
        domain = AgentToolDomain.NOTIFICATION_ASSISTANT,
        displayName = "Notification Summaries & Read-Aloud",
        description = "Announces incoming notifications respecting ringer mode, exclusions, pause state, and OTP blocking.",
        requiredPermissionNote = "User-enabled Notification Listener Service",
        requiresExplicitConfirmation = false,
      ),
      AgentToolDescriptor(
        id = "accessibility_ui_agent",
        domain = AgentToolDomain.ACCESSIBILITY_UI,
        displayName = "Accessibility UI Assistant",
        description = "Reads visible non-sensitive UI nodes, clicks labeled controls, scrolls, and inputs non-sensitive text.",
        requiredPermissionNote = "Optional user-enabled Accessibility Service (blocks passwords/OTP/PIN/payments)",
        requiresExplicitConfirmation = true,
      ),
      AgentToolDescriptor(
        id = "alarms_reminders",
        domain = AgentToolDomain.ALARMS_AND_REMINDERS,
        displayName = "Alarms, Timers & Reminders",
        description = "Schedules exact alarms, countdown timers, and structured reminders.",
        requiredPermissionNote = "Optional SCHEDULE_EXACT_ALARM / POST_NOTIFICATIONS",
        requiresExplicitConfirmation = false,
      ),
    )
}

sealed interface PlannedAgentOutcome {
  data class ConfirmPhoneAction(val action: PendingPhoneAction) : PlannedAgentOutcome
  data class ConfirmSocialAction(val action: PendingSocialAction) : PlannedAgentOutcome
  data class LaunchCommand(val intent: AgentCommandIntent) : PlannedAgentOutcome
  data class TimerRequested(val durationSeconds: Int, val label: String) : PlannedAgentOutcome
  data class NotificationControlHandled(
    val message: String,
    val setPaused: Boolean? = null,
    val setReadAloudEnabled: Boolean? = null,
  ) : PlannedAgentOutcome
  data class AccessibilityHandled(val result: AccessibilityActionResult) : PlannedAgentOutcome
  data class AgentTaskExecuted(val report: AgentExecutionReport) : PlannedAgentOutcome
  data class ImageGenerationRequested(val prompt: String) : PlannedAgentOutcome
  data object ForwardToMultimodalAi : PlannedAgentOutcome
}

/**
 * Modular Agent Task Planner routing natural voice & text requests across Phone Control,
 * Contacts/WhatsApp/Social, Notification Read-Aloud controls, Timers, Accessibility UI nodes,
 * System/Media/Music/Productivity actions, and Screen Capture tools before falling back to
 * multimodal AI generation.
 */
@Singleton
class AgentTaskPlanner @Inject constructor(
  private val commandRouter: AssistantAgentCommandRouter,
  private val phoneActionController: PhoneActionController,
  private val socialIntegrationManager: SocialIntegrationManager,
  private val accessibilityController: AccessibilityAgentController,
  private val notificationEngine: NotificationIntelligenceEngine,
  private val settingsRepository: SettingsRepository,
  private val agentOrchestrator: AgentOrchestrator? = null,
) {

  suspend fun planAndRoute(rawInput: String): PlannedAgentOutcome {
    val clean = rawInput.trim()
    if (clean.isBlank()) return PlannedAgentOutcome.ForwardToMultimodalAi

    // 1. Notification Read-Aloud voice/text controls
    parseNotificationControl(clean)?.let { ctrl ->
      val currentSettings = settingsRepository.getSettingsSnapshot()
      var updatedNotif = currentSettings.notificationConfig
      if (ctrl.setPaused != null) {
        updatedNotif = updatedNotif.copy(readAloudPaused = ctrl.setPaused)
        notificationEngine.setReadAloudPaused(ctrl.setPaused)
      }
      if (ctrl.setReadAloudEnabled != null) {
        updatedNotif = updatedNotif.copy(readAloudEnabled = ctrl.setReadAloudEnabled)
        notificationEngine.updateConfig(updatedNotif)
      }
      settingsRepository.saveSettings(currentSettings.copy(notificationConfig = updatedNotif))
      return ctrl
    }

    // 2. Countdown timer commands ("set a timer for 5 minutes", "timer 30 seconds for tea")
    parseTimerRequest(clean)?.let { return it }

    // 3. Confirmation-gated Phone actions (Dial / SMS with ContactResolver lookup)
    PhoneActionController.tryParseConfirmationCommand(clean)?.let { initialPhone ->
      val resolved = phoneActionController.resolveAndPreparePhoneAction(initialPhone)
      return PlannedAgentOutcome.ConfirmPhoneAction(resolved)
    }

    // 4. Confirmation-gated WhatsApp / Instagram / Facebook / YouTube actions
    SocialIntegrationManager.tryParseSocialCommand(clean)?.let { initialSocial ->
      val resolved = socialIntegrationManager.resolveAndPrepareSocialAction(initialSocial)
      return PlannedAgentOutcome.ConfirmSocialAction(resolved)
    }

    // 5. Screen recording, sharing, selection, app launches, settings, image generation, & structured agent tasks
    commandRouter.classify(clean)?.let { cmd ->
      when (cmd) {
        is AgentCommandIntent.GenerateImage -> {
          return PlannedAgentOutcome.ImageGenerationRequested(cmd.imagePrompt)
        }
        is AgentCommandIntent.ExecuteAgentTask -> {
          val report = agentOrchestrator?.executeAgentTask(cmd.rawCommand)
          if (report != null) {
            return PlannedAgentOutcome.AgentTaskExecuted(report)
          }
        }
        else -> Unit
      }
      return PlannedAgentOutcome.LaunchCommand(cmd)
    }

    // 6. Optional Accessibility UI actions (click labeled node, scroll, type, global nav)
    parseAndExecuteAccessibilityCommand(clean)?.let { accResult ->
      return PlannedAgentOutcome.AccessibilityHandled(accResult)
    }

    return PlannedAgentOutcome.ForwardToMultimodalAi
  }

  private fun parseAndExecuteAccessibilityCommand(clean: String): AccessibilityActionResult? {
    val lower = clean.lowercase(Locale.US).trimEnd('.', '!', '?')
    return when {
      lower == "scroll down" || lower == "scroll forward" || lower == "swipe down" ->
        accessibilityController.performScroll(forward = true)
      lower == "scroll up" || lower == "scroll back" || lower == "swipe up" ->
        accessibilityController.performScroll(forward = false)
      lower == "go back" || lower == "press back" || lower == "navigate back" ->
        accessibilityController.performGlobalNav(AccessibilityGlobalAction.BACK)
      lower == "go home" || lower == "press home" ->
        accessibilityController.performGlobalNav(AccessibilityGlobalAction.HOME)
      lower == "open recents" || lower == "recent apps" ->
        accessibilityController.performGlobalNav(AccessibilityGlobalAction.RECENTS)
      lower == "open notifications shade" || lower == "pull down notifications" ->
        accessibilityController.performGlobalNav(AccessibilityGlobalAction.NOTIFICATIONS)
      lower.startsWith("tap ") || lower.startsWith("click ") || lower.startsWith("press button ") -> {
        val target =
          clean
            .replace(Regex("^(?:tap|click|press\\s+button)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd('.', '!', '?')
        if (target.isBlank()) return null
        accessibilityController.performNodeClick(targetText = target, confirmedSensitiveAction = false)
      }
      lower.startsWith("type ") && " into " in lower -> {
        val match =
          Regex("^type\\s+(.+?)\\s+into\\s+(.+)$", RegexOption.IGNORE_CASE).find(clean)
            ?: return null
        accessibilityController.performTextInput(
          targetFieldHintOrNull = match.groupValues[2].trim(),
          inputText = match.groupValues[1].trim(),
        )
      }
      else -> null
    }
  }

  companion object {
    fun parseNotificationControl(clean: String): PlannedAgentOutcome.NotificationControlHandled? {
      val lower = clean.lowercase(Locale.US).trim().trimEnd('.', '!', '?')
      return when {
        lower.contains("pause") && lower.contains("notification") ->
          PlannedAgentOutcome.NotificationControlHandled(
            message = "Notification Read-Aloud paused.",
            setPaused = true,
          )
        lower.contains("resume") && lower.contains("notification") ->
          PlannedAgentOutcome.NotificationControlHandled(
            message = "Notification Read-Aloud resumed.",
            setPaused = false,
            setReadAloudEnabled = true,
          )
        (lower.contains("stop") || lower.contains("mute") || lower.contains("turn off")) &&
          lower.contains("read aloud") ->
          PlannedAgentOutcome.NotificationControlHandled(
            message = "Turned off Notification Read-Aloud.",
            setReadAloudEnabled = false,
          )
        (lower.contains("start") || lower.contains("enable") || lower.contains("turn on")) &&
          (lower.contains("read aloud") || lower.contains("read notifications aloud")) ->
          PlannedAgentOutcome.NotificationControlHandled(
            message = "Enabled Notification Read-Aloud (respects ringer mode & privacy settings).",
            setPaused = false,
            setReadAloudEnabled = true,
          )
        else -> null
      }
    }

    fun parseTimerRequest(clean: String): PlannedAgentOutcome.TimerRequested? {
      val regex =
        Regex(
          "^(?:please\\s+)?(?:set|start)\\s+(?:a\\s+)?timer\\s+(?:for\\s+)?(\\d+)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)(?:\\s+(?:for|called|labeled)\\s+(.+))?$",
          RegexOption.IGNORE_CASE,
        )
      val m = regex.find(clean.trim()) ?: return null
      val amount = m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: return null
      val unit = m.groupValues[2].lowercase(Locale.US)
      val seconds =
        when {
          unit.startsWith("h") -> (amount * 3600).coerceAtMost(86400)
          unit.startsWith("m") -> (amount * 60).coerceAtMost(86400)
          else -> amount.coerceAtMost(86400)
        }
      val label = m.groupValues.getOrNull(3)?.trim().orEmpty().ifBlank { "Mousomi Timer" }
      return PlannedAgentOutcome.TimerRequested(durationSeconds = seconds, label = label)
    }
  }
}
