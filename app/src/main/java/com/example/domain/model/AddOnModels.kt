package com.example.domain.model

import kotlinx.serialization.Serializable

// -------------------------
// MODULE 29 & 32: DEVICE ACTIONS & APP LAUNCHER
// -------------------------
@Serializable
enum class DeviceActionCategory(val label: String) {
  LAUNCH_APP("Application Launching"),
  SYSTEM_SETTINGS("System Settings Navigation"),
  ALARM_TIMER_REMINDER("Alarm, Timer & Reminder"),
  MEDIA_SELECTION("Selected Media Access"),
  FILE_SELECTION("File Selection (SAF)"),
  CLIPBOARD_COPY("Text Copying"),
  SHARE_INTENT("Share Content"),
  CALENDAR_EVENT("Calendar Event"),
  NOTIFICATION_CONTROL("Notification Management"),
  WEB_RESEARCH("Web Research"),
  IMAGE_ACTION("Image Intelligence / Editing"),
  SCREEN_ASSISTANT("Screen Assistance"),
  VOICE_INTERACTION("Voice Interaction"),
  BLUETOOTH_ACCESS("Bluetooth Device Integration"),
  MULTI_TASK("Multi-Step Task Execution"),
}

@Serializable
data class InstalledAppInfo(
  val packageName: String,
  val appName: String,
  val isSystemApp: Boolean = false,
)

@Serializable
data class DeviceActionResult(
  val id: String,
  val category: DeviceActionCategory,
  val command: String,
  val targetLabel: String,
  val packageName: String? = null,
  val success: Boolean,
  val statusLabel: String,
  val verifiedDetail: String,
  val timestampMs: Long = System.currentTimeMillis(),
)

// -------------------------
// MODULE 30: SMART ALARM, TIMER & REMINDER AGENT
// -------------------------
@Serializable
enum class AlarmRecurrence(val label: String) {
  ONCE("Once"),
  DAILY("Daily"),
  WEEKLY("Weekly"),
}

@Serializable
enum class ReminderItemType(val label: String) {
  ALARM("Alarm"),
  TIMER("Timer"),
  REMINDER("Reminder"),
}

@Serializable
data class ReminderEntry(
  val id: String,
  val type: ReminderItemType,
  val title: String,
  val hour: Int? = null,
  val minute: Int? = null,
  val durationSeconds: Int? = null,
  val scheduledAtMs: Long,
  val recurrence: AlarmRecurrence = AlarmRecurrence.ONCE,
  val daysOfWeekCsv: String = "",
  val enabled: Boolean = true,
  val status: String = "Scheduled",
  val lastTriggeredAtMs: Long? = null,
  val createdAtMs: Long = System.currentTimeMillis(),
  val verificationNote: String = "",
)

// -------------------------
// MODULE 31 & 41: MULTI-TASK AI AGENT & TASK CENTER
// -------------------------
@Serializable
enum class AgentTaskLifecycleState(val label: String) {
  PENDING("Pending"),
  PLANNING("Planning"),
  WAITING_FOR_PERMISSION("Waiting for Permission"),
  WAITING_FOR_APPROVAL("Waiting for Approval"),
  RUNNING("Running"),
  PAUSED("Paused"),
  COMPLETED("Completed"),
  FAILED("Failed"),
  CANCELLED("Cancelled"),
  VERIFICATION_REQUIRED("Verification Required"),
}

@Serializable
enum class SubTaskKind(val label: String, val requiresApprovalByDefault: Boolean) {
  OPEN_APP("Open App", false),
  OPEN_SETTINGS("Open Settings", false),
  SET_ALARM("Set Alarm", false),
  SET_TIMER("Start Timer", false),
  CREATE_REMINDER("Schedule Reminder", false),
  MODIFY_REMINDER("Modify/Cancel Reminder", false),
  LIST_REMINDERS("Show Alarms & Reminders", false),
  COPY_TEXT("Copy Text", false),
  SHARE_TEXT("Share Content", true),
  CREATE_CALENDAR("Create Calendar Event", true),
  WEB_RESEARCH("Web Research", false),
  GENERATE_IMAGE("Generate Image", false),
  AI_REASONING("AI Reasoning", false),
  SCREEN_ASSIST("Screen Assistance", true),
}

@Serializable
data class ManagedTaskItem(
  val id: String,
  val batchId: String,
  val orderIndex: Int,
  val description: String,
  val kind: SubTaskKind,
  val targetArg: String = "",
  val requiredPermissions: List<String> = emptyList(),
  val dependsOnIds: List<String> = emptyList(),
  val priority: Int = 5,
  val state: AgentTaskLifecycleState = AgentTaskLifecycleState.PENDING,
  val resultMessage: String = "",
  val errorDetails: String = "",
  val retryCount: Int = 0,
  val maxRetries: Int = 2,
  val requiresApproval: Boolean = false,
  val createdAtMs: Long = System.currentTimeMillis(),
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Serializable
data class MultiTaskBatchReport(
  val batchId: String,
  val originalInstruction: String,
  val tasks: List<ManagedTaskItem>,
  val createdAtMs: Long = System.currentTimeMillis(),
) {
  val completedCount: Int get() = tasks.count { it.state == AgentTaskLifecycleState.COMPLETED }
  val failedCount: Int get() = tasks.count { it.state == AgentTaskLifecycleState.FAILED }
  val cancelledCount: Int get() = tasks.count { it.state == AgentTaskLifecycleState.CANCELLED }
  val awaitingCount: Int
    get() =
      tasks.count {
        it.state == AgentTaskLifecycleState.WAITING_FOR_APPROVAL ||
          it.state == AgentTaskLifecycleState.WAITING_FOR_PERMISSION ||
          it.state == AgentTaskLifecycleState.PAUSED
      }
}

// -------------------------
// MODULE 34–39: VOICE DSP SMOKE & FLOATING ORBIT SYSTEM
// -------------------------
data class VoiceAudioMetrics(
  val rmsNormalized: Float = 0f,
  val peakNormalized: Float = 0f,
  val shortTimeEnergy: Float = 0f,
  val isVoiceActive: Boolean = false,
  val estimatedPitchHz: Float = 0f,
  val pitchConfidence: Float = 0f,
  val lowBandEnergy: Float = 0f,
  val midBandEnergy: Float = 0f,
  val highBandEnergy: Float = 0f,
  val timestampMs: Long = System.currentTimeMillis(),
) {
  companion object {
    val SILENT = VoiceAudioMetrics()
  }
}

enum class OrbVisualMode(val label: String) {
  IDLE("Idle"),
  FOCUSED("Focused"),
  LISTENING("Listening"),
  PROCESSING("Processing"),
  SPEAKING("AI Speaking"),
  IMAGE_GENERATION("Generating Image"),
  SUCCESS("Success"),
  ERROR("Error"),
}

enum class OrbPopupAction(val label: String, val shortLabel: String) {
  VOICE_ASSISTANT("Voice Assistant", "Voice"),
  OPEN_CHAT("Open Chat", "Chat"),
  QUICK_ASK("Quick Ask", "Ask"),
  OPEN_CAMERA("Open Camera", "Camera"),
  ANALYZE_IMAGE("Analyze Image", "Vision"),
  GENERATE_IMAGE("Generate Image", "Image"),
  SCREEN_ASSISTANT("Screen Assistant", "Screen"),
  DEEP_RESEARCH("Deep Research", "Research"),
  DEVICE_ACTIONS("Device Actions", "Apps"),
  MULTI_TASK_AGENT("Multi-Task Agent", "Tasks"),
  ALARM_AND_REMINDER("Alarm & Reminder", "Alarms"),
  EMERGENCY_STOP("Emergency Stop", "Stop"),
}
