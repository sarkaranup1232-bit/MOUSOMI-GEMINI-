package com.example.core.agent

import com.example.core.device.AndroidSystemService
import com.example.core.device.CalendarReminderService
import com.example.core.device.CallingService
import com.example.core.device.ContactsService
import com.example.core.device.MediaFileService
import com.example.core.device.MediaPlaybackCommand
import com.example.core.device.VolumeTargetStream
import com.example.core.device.WhatsAppIntegration
import com.example.core.device.YouTubeIntegration
import com.example.core.permissions.PermissionCapabilityManager
import com.example.core.voice.HotwordCapabilityManager
import com.example.domain.memory.FavoriteMusicCommandResult
import com.example.domain.memory.FavoriteMusicManager
import com.example.domain.memory.MusicPlaybackIntegration
import com.example.domain.model.MemoryCategory
import com.example.domain.model.TodoNoteKind
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AgentExecutionReport(
  val summaryText: String,
  val success: Boolean,
  val stepLogs: List<String> = emptyList(),
  val askFavoriteCategory: MemoryCategory? = null,
)

/**
 * Central Android Agent Orchestrator responsible for executing verified Android system,
 * media, communication, productivity, music, and runtime capability tasks — and reporting
 * honest outcomes without ever faking success.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
  private val androidSystemService: AndroidSystemService,
  private val contactsService: ContactsService,
  private val callingService: CallingService,
  private val whatsAppIntegration: WhatsAppIntegration,
  private val youTubeIntegration: YouTubeIntegration,
  private val mediaFileService: MediaFileService,
  private val calendarReminderService: CalendarReminderService,
  private val favoriteMusicManager: FavoriteMusicManager,
  private val musicPlaybackIntegration: MusicPlaybackIntegration,
  private val hotwordCapabilityManager: HotwordCapabilityManager,
  private val capabilityManager: PermissionCapabilityManager,
) {

  suspend fun executeAgentTask(rawCommand: String): AgentExecutionReport {
    val steps = splitCompoundCommand(rawCommand)
    if (steps.size > 1) {
      val logs = mutableListOf<String>()
      var allOk = true
      for (step in steps) {
        val single = executeSingleTask(step)
        logs.add("• $step → ${single.summaryText}")
        if (!single.success) allOk = false
      }
      return AgentExecutionReport(
        summaryText = logs.joinToString("\n"),
        success = allOk,
        stepLogs = logs,
      )
    }
    return executeSingleTask(rawCommand.trim())
  }

  private suspend fun executeSingleTask(clean: String): AgentExecutionReport {
    val lower = clean.lowercase(Locale.US).trimEnd('.', '!', '?')

    // 1. Hotword / Galaxy M51 wake-word & assistant capability report
    if ("wake word" in lower || "hotword" in lower || "always listening" in lower || "hey mousomi" in lower) {
      val report = hotwordCapabilityManager.inspectCapabilities()
      val snapshot = capabilityManager.inspectRuntimeCapabilities()
      val msg =
        buildString {
          appendLine("Assistant Readiness (${snapshot.deviceProfile.manufacturer} ${snapshot.deviceProfile.model}, Android ${snapshot.deviceProfile.androidRelease}):")
          appendLine("• Default Assistant Active: ${if (snapshot.isDefaultAssistant) "Yes" else "No"}")
          appendLine("• Hotword Status: ${report.state.label}")
          appendLine("• Technical Reason: ${report.reasonSummary}")
          append("• Recommended Activation: ${report.recommendedTriggers.firstOrNull() ?: "Use Live Voice in Assistant Session"}")
        }
      return AgentExecutionReport(summaryText = msg, success = report.supportsForegroundLiveMicInSession, stepLogs = listOf(msg))
    }

    // 2. Favorite music / play favorite song / show favorite songs / remove favorite
    favoriteMusicManager.tryHandleMusicCommand(clean)?.let { musicCmd ->
      return when (musicCmd) {
        is FavoriteMusicCommandResult.ShowFavorites ->
          AgentExecutionReport(summaryText = musicCmd.summaryText, success = true)
        is FavoriteMusicCommandResult.RemovedFavorite ->
          AgentExecutionReport(summaryText = musicCmd.message, success = true)
        is FavoriteMusicCommandResult.AskFavoriteToSave ->
          AgentExecutionReport(
            summaryText = musicCmd.message,
            success = false,
            askFavoriteCategory = musicCmd.targetCategory,
          )
        is FavoriteMusicCommandResult.LaunchMusicPlayback -> {
          val launchRes =
            musicPlaybackIntegration.launchMusicPlayback(
              query = musicCmd.query,
              preferredAppHint = musicCmd.preferredAppHint,
              displayLabel = musicCmd.label,
            )
          AgentExecutionReport(
            summaryText = launchRes.verifiedDetail,
            success = launchRes.success,
          )
        }
      }
    }

    // 3. Volume control
    if ("volume" in lower || lower == "mute" || lower == "unmute") {
      val stream =
        when {
          "alarm" in lower -> VolumeTargetStream.ALARM
          "ring" in lower || "ringer" in lower -> VolumeTargetStream.RING
          "notification" in lower -> VolumeTargetStream.NOTIFICATION
          "call" in lower -> VolumeTargetStream.CALL
          else -> VolumeTargetStream.MEDIA
        }
      val pctMatch = Regex("(\\d{1,3})\\s*%?").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
      val res =
        when {
          lower == "mute" || "mute " in lower -> androidSystemService.adjustVolume(stream, mute = true)
          lower == "unmute" || "unmute " in lower -> androidSystemService.adjustVolume(stream, mute = false)
          pctMatch != null && ("set" in lower || "to" in lower) ->
            androidSystemService.adjustVolume(stream, targetPercent = pctMatch)
          "up" in lower || "increase" in lower || "raise" in lower || "louder" in lower ->
            androidSystemService.adjustVolume(stream, deltaDirection = 1)
          "down" in lower || "decrease" in lower || "lower" in lower || "quieter" in lower ->
            androidSystemService.adjustVolume(stream, deltaDirection = -1)
          else -> androidSystemService.adjustVolume(stream)
        }
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 4. Media playback key events
    val mediaCmd =
      when {
        "pause music" in lower || "pause media" in lower || "pause audio" in lower -> MediaPlaybackCommand.PAUSE
        "play music" in lower || "resume music" in lower || "resume media" in lower -> MediaPlaybackCommand.PLAY
        "next song" in lower || "next track" in lower || "skip track" in lower || "skip song" in lower -> MediaPlaybackCommand.NEXT
        "previous song" in lower || "previous track" in lower -> MediaPlaybackCommand.PREVIOUS
        "stop music" in lower || "stop media" in lower -> MediaPlaybackCommand.STOP
        else -> null
      }
    if (mediaCmd != null) {
      val res = androidSystemService.controlMediaPlayback(mediaCmd)
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 5. YouTube search
    if ("youtube" in lower && ("search" in lower || "find" in lower)) {
      val query =
        clean
          .replace(Regex("^(?:please\\s+)?(?:search\\s+youtube\\s+(?:for)?|find\\s+on\\s+youtube|search\\s+(?:videos?\\s+)?(?:for\\s+)?)\\s*", RegexOption.IGNORE_CASE), "")
          .replace(Regex("\\s+on\\s+youtube$", RegexOption.IGNORE_CASE), "")
          .trim()
      val res = youTubeIntegration.searchVideos(query)
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 6. WhatsApp voice / video call
    if ("whatsapp" in lower && "call" in lower) {
      val isVideo = "video" in lower
      val contactName =
        clean
          .replace(Regex("^(?:please\\s+)?(?:make\\s+a\\s+)?(?:whatsapp\\s+(?:video\\s+)?call\\s+(?:to)?|call)\\s*", RegexOption.IGNORE_CASE), "")
          .replace(Regex("\\s+on\\s+whatsapp$", RegexOption.IGNORE_CASE), "")
          .trim()
      val res = whatsAppIntegration.launchWhatsAppCallByName(contactName, isVideoCall = isVideo)
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 7. Call history & Answer/End capability honesty
    if ("call history" in lower || "call log" in lower || "recent calls" in lower) {
      val res = callingService.openCallHistory()
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }
    if ("answer the call" in lower || "end the call" in lower) {
      val res = callingService.reportCallAnswerEndCapability()
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 8. Contact search
    if (lower.startsWith("find contact ") || lower.startsWith("search contact ") || lower.startsWith("lookup contact ")) {
      val query =
        clean
          .replace(Regex("^(?:find|search|lookup)\\s+contact\\s+", RegexOption.IGNORE_CASE), "")
          .trim()
      val res = contactsService.searchContactsReport(query)
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 9. Battery & Device Info
    if ("battery" in lower || "device info" in lower) {
      val res = androidSystemService.getBatteryAndDeviceSummary()
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 10. Foreground Location
    if ("location" in lower || lower == "where am i") {
      val res = androidSystemService.getForegroundLocationSummary()
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 11. Persisted SAF Files
    if (lower.startsWith("find file") || lower.startsWith("search file")) {
      val query =
        clean
          .replace(Regex("^(?:find|search)\\s+files?\\s*(?:for)?\\s*", RegexOption.IGNORE_CASE), "")
          .trim()
      val res = mediaFileService.findPersistedAuthorizedFiles(query)
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }

    // 12. Calendar Events, To-Do, Notes, Daily Schedules
    if (lower.startsWith("create calendar event") || lower.startsWith("add calendar event") || lower.startsWith("schedule meeting")) {
      val title =
        clean
          .replace(Regex("^(?:create\\s+calendar\\s+event|add\\s+calendar\\s+event|schedule\\s+meeting)\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
          .trim()
      val res = calendarReminderService.createCalendarEvent(title)
      return AgentExecutionReport(res.verifiedDetail, res.success)
    }
    if (lower.startsWith("add to-do ") || lower.startsWith("add todo ")) {
      val title = clean.replace(Regex("^add\\s+to-?do\\s+", RegexOption.IGNORE_CASE), "").trim()
      val item = calendarReminderService.addTodoOrNote(TodoNoteKind.TODO, title)
      return AgentExecutionReport("Added To-Do: \"${item.title}\".", true)
    }
    if (lower.startsWith("take a note ") || lower.startsWith("add note ")) {
      val title = clean.replace(Regex("^(?:take\\s+a\\s+note|add\\s+note)\\s+", RegexOption.IGNORE_CASE), "").trim()
      val item = calendarReminderService.addTodoOrNote(TodoNoteKind.NOTE, title)
      return AgentExecutionReport("Saved Note: \"${item.title}\".", true)
    }
    if (lower.startsWith("add to my schedule ")) {
      val title = clean.removePrefix("add to my schedule ").removePrefix("Add to my schedule ").trim()
      val item = calendarReminderService.addTodoOrNote(TodoNoteKind.SCHEDULE, title)
      return AgentExecutionReport("Added to Daily Schedule: \"${item.title}\".", true)
    }
    if (lower == "show my notes") {
      return AgentExecutionReport(calendarReminderService.listSummary(TodoNoteKind.NOTE), true)
    }
    if (lower == "show my todos" || lower == "show my to-dos") {
      return AgentExecutionReport(calendarReminderService.listSummary(TodoNoteKind.TODO), true)
    }
    if (lower == "show my schedule" || lower == "show my daily schedule") {
      return AgentExecutionReport(calendarReminderService.listSummary(TodoNoteKind.SCHEDULE), true)
    }

    return AgentExecutionReport(
      summaryText = "No local Android action matched '$clean'. Handing off to Mousomi AI.",
      success = false,
    )
  }

  companion object {
    fun splitCompoundCommand(rawInput: String): List<String> =
      rawInput
        .split(Regex("\\s*(?:;|\\band\\s+then\\b)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
  }
}
