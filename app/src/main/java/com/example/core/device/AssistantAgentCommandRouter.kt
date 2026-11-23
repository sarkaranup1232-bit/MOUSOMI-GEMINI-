package com.example.core.device

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AgentCommandIntent {
  data class StartScreenRecording(val rawCommand: String) : AgentCommandIntent
  data object StopScreenRecording : AgentCommandIntent
  data object CancelScreenRecording : AgentCommandIntent
  data object StartScreenShare : AgentCommandIntent
  data object StopScreenShare : AgentCommandIntent
  data class AnalyzeCurrentScreen(val question: String) : AgentCommandIntent
  data object StartScreenSelection : AgentCommandIntent
  data class OpenTargetOrSetting(val command: String) : AgentCommandIntent
  data class UnsupportedSilentToggle(
    val featureName: String,
    val explanation: String,
    val fallbackSettingsQuery: String?,
  ) : AgentCommandIntent
}

/**
 * Classifies natural voice and text assistant commands into verified local agent actions vs
 * multimodal AI queries. Explicitly surfaces Android OS restrictions for silent system toggles
 * instead of pretending an action succeeded.
 */
@Singleton
class AssistantAgentCommandRouter @Inject constructor() {

  fun classify(rawInput: String): AgentCommandIntent? {
    val clean = rawInput.trim()
    if (clean.isEmpty()) return null
    val lower =
      clean
        .lowercase(Locale.US)
        .replace(Regex("[.!?]+$"), "")
        .trim()

    // 1. Screen Recording commands
    if (matchesAny(
        lower,
        "cancel screen recording",
        "cancel recording",
        "abort screen recording",
        "discard screen recording",
      )
    ) {
      return AgentCommandIntent.CancelScreenRecording
    }

    if (matchesAny(
        lower,
        "stop screen recording",
        "turn off screen recording",
        "end screen recording",
        "stop recording screen",
        "stop recording my screen",
        "stop recording",
      )
    ) {
      return AgentCommandIntent.StopScreenRecording
    }

    if (matchesAny(
        lower,
        "turn on screen recording",
        "start screen recording",
        "begin screen recording",
        "record screen",
        "record my screen",
        "enable screen recording",
        "can you record my screen",
        "please record my screen",
      ) ||
      (lower.contains("screen recording") &&
        (lower.contains("start") || lower.contains("turn on") || lower.contains("enable") || lower.contains("begin")))
    ) {
      return AgentCommandIntent.StartScreenRecording(clean)
    }

    // 2. Screen Share commands
    if (matchesAny(
        lower,
        "stop screen sharing",
        "stop sharing screen",
        "stop sharing my screen",
        "stop screen share",
        "turn off screen sharing",
        "turn off screen share",
        "end screen share",
      )
    ) {
      return AgentCommandIntent.StopScreenShare
    }

    if (matchesAny(
        lower,
        "share my screen",
        "share screen",
        "start screen sharing",
        "start screen share",
        "turn on screen share",
        "turn on screen sharing",
        "share screen with live",
      )
    ) {
      return AgentCommandIntent.StartScreenShare
    }

    // 3. Screen Selection / Area Drawing
    if (matchesAny(
        lower,
        "select screen",
        "screen select",
        "screen selection",
        "select on screen",
        "circle to search",
        "draw on screen",
        "select area on screen",
      ) ||
      lower.contains("select region on screen") ||
      lower.contains("highlight on screen") ||
      lower.contains("circle on screen") ||
      lower.contains("crop my screen") ||
      lower.contains("crop the screen") ||
      lower.contains("start screen selection")
    ) {
      return AgentCommandIntent.StartScreenSelection
    }

    // 4. Screen Analysis (only direct requests about currently displayed screen)
    if (matchesAny(
        lower,
        "analyze my screen",
        "analyze current screen",
        "analyze the screen",
        "what is on my screen",
        "what's on my screen",
        "whats on my screen",
        "explain what is on my screen",
        "read my screen",
        "describe my screen",
      ) ||
      lower.contains("on my screen right now")
    ) {
      return AgentCommandIntent.AnalyzeCurrentScreen(clean)
    }

    // 5. OS-restricted silent hardware/system toggles on Android 12+
    val silentToggleMatch = detectUnsupportedSilentToggle(lower)
    if (silentToggleMatch != null) {
      return silentToggleMatch
    }

    // 6. Verified direct app launches, settings panels, alarms, maps, clipboard
    if (isDirectDeviceAction(lower)) {
      return AgentCommandIntent.OpenTargetOrSetting(clean)
    }

    return null
  }

  private fun detectUnsupportedSilentToggle(lower: String): AgentCommandIntent.UnsupportedSilentToggle? {
    val togglePrefix =
      Regex("^(?:please\\s+)?(?:can you\\s+)?(?:turn\\s+on|turn\\s+off|enable|disable|toggle|switch\\s+on|switch\\s+off)\\s+(.+)$")
        .find(lower)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: return null

    return when {
      togglePrefix == "wifi" || togglePrefix == "wi-fi" ->
        AgentCommandIntent.UnsupportedSilentToggle(
          featureName = "Wi-Fi",
          explanation =
            "Android 12+ blocks third-party assistant apps from silently toggling Wi-Fi on or off. Opening Wi-Fi Settings so you can toggle it directly.",
          fallbackSettingsQuery = "wifi settings",
        )
      togglePrefix == "bluetooth" ->
        AgentCommandIntent.UnsupportedSilentToggle(
          featureName = "Bluetooth",
          explanation =
            "Android 12+ does not permit apps without privileged system signatures to silently toggle Bluetooth. Opening Bluetooth Settings for you.",
          fallbackSettingsQuery = "bluetooth settings",
        )
      togglePrefix.contains("airplane mode") || togglePrefix.contains("flight mode") ->
        AgentCommandIntent.UnsupportedSilentToggle(
          featureName = "Airplane Mode",
          explanation =
            "Android blocks non-system apps from toggling Airplane Mode silently for radio safety. Opening System Settings so you can change it.",
          fallbackSettingsQuery = "settings",
        )
      togglePrefix.contains("mobile data") || togglePrefix.contains("cellular") ||
        togglePrefix.contains("hotspot") ->
        AgentCommandIntent.UnsupportedSilentToggle(
          featureName = togglePrefix.replaceFirstChar { it.uppercase() },
          explanation =
            "Android restricts silent toggling of $togglePrefix to system settings. Opening Android Settings so you can toggle it directly.",
          fallbackSettingsQuery = "settings",
        )
      togglePrefix.contains("flashlight") || togglePrefix == "torch" ||
        togglePrefix.contains("brightness") || togglePrefix.contains("dark mode") ->
        AgentCommandIntent.UnsupportedSilentToggle(
          featureName = togglePrefix.replaceFirstChar { it.uppercase() },
          explanation =
            "Direct silent control for $togglePrefix is restricted without privileged permissions. Opening Display Settings.",
          fallbackSettingsQuery = "display settings",
        )
      else -> null
    }
  }

  private fun isDirectDeviceAction(lower: String): Boolean {
    if (lower.length > 96) return false
    val actionPrefixes =
      listOf(
        "open ",
        "launch ",
        "start app ",
        "please open ",
        "can you open ",
        "please launch ",
        "find app ",
        "search app ",
        "copy ",
        "navigate to ",
        "directions to ",
        "find on maps ",
      )
    if (actionPrefixes.any { lower.startsWith(it) }) {
      val target =
        lower
          .removePrefix("please ")
          .removePrefix("can you ")
          .trim()
      // Avoid hijacking "open a new chat" / "open my mind" / "open lens" if handled specially
      if (target == "lens" || target == "open lens" || target == "open mousomi lens") return false
      if (target.startsWith("open a ") || target.startsWith("open the door") || target.startsWith("open discussion")) {
        return false
      }
      return true
    }
    return lower == "open alarms" || lower == "open camera" || lower == "open settings"
  }

  private fun matchesAny(input: String, vararg phrases: String): Boolean =
    phrases.any { input == it || input.startsWith("$it ") }
}
