package com.example.core.screen

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ScreenRecordingPhase(val label: String) {
  IDLE("Idle"),
  AWAITING_CONFIRMATION("Confirm Recording"),
  AWAITING_CONSENT("Waiting for Consent"),
  RECORDING("Recording"),
  FINALIZING("Saving MP4"),
  SAVED("Saved"),
  CANCELLED("Cancelled"),
  FAILED("Failed"),
}

data class ScreenRecordingUiState(
  val phase: ScreenRecordingPhase = ScreenRecordingPhase.IDLE,
  val elapsedSeconds: Long = 0L,
  val displayPath: String = "",
  val savedUri: String? = null,
  val statusMessage: String? = null,
) {
  val isActive: Boolean
    get() = phase == ScreenRecordingPhase.RECORDING || phase == ScreenRecordingPhase.FINALIZING
}

/**
 * Single source of truth for Mousomi's user-consented MediaProjection screen recording lifecycle.
 * Strictly separates confirmation -> system MediaProjection consent -> active MediaRecorder capture ->
 * verified MediaStore MP4 finalization so a recording path is never reported before save completes.
 */
@Singleton
class ScreenRecordingManager @Inject constructor() {

  private val _state = MutableStateFlow(ScreenRecordingUiState())
  val state: StateFlow<ScreenRecordingUiState> = _state.asStateFlow()

  fun requestConfirmation(
    promptMessage: String =
      "Screen recording captures what appears on your display and saves an MP4 file to Movies/Mousomi AI. Android will show the official screen-capture consent dialog next.",
  ) {
    val cur = _state.value.phase
    if (cur == ScreenRecordingPhase.RECORDING || cur == ScreenRecordingPhase.FINALIZING) {
      return
    }
    _state.value =
      ScreenRecordingUiState(
        phase = ScreenRecordingPhase.AWAITING_CONFIRMATION,
        statusMessage = promptMessage,
      )
  }

  fun markAwaitingConsent() {
    _state.value =
      ScreenRecordingUiState(
        phase = ScreenRecordingPhase.AWAITING_CONSENT,
        statusMessage = "Waiting for Android screen capture permission…",
      )
  }

  fun markRecordingStarted(targetDisplayPath: String) {
    _state.value =
      ScreenRecordingUiState(
        phase = ScreenRecordingPhase.RECORDING,
        elapsedSeconds = 0L,
        displayPath = targetDisplayPath,
        savedUri = null,
        statusMessage = "Recording screen to $targetDisplayPath",
      )
  }

  fun updateElapsedSeconds(seconds: Long) {
    _state.update { cur ->
      if (cur.phase == ScreenRecordingPhase.RECORDING) {
        cur.copy(elapsedSeconds = seconds.coerceAtLeast(0L))
      } else {
        cur
      }
    }
  }

  fun markFinalizing() {
    _state.update { cur ->
      cur.copy(
        phase = ScreenRecordingPhase.FINALIZING,
        statusMessage = "Finalizing screen recording…",
      )
    }
  }

  fun markSaved(displayPath: String, savedUri: String?) {
    require(displayPath.isNotBlank()) { "Verified saved path must not be blank." }
    _state.update { cur ->
      ScreenRecordingUiState(
        phase = ScreenRecordingPhase.SAVED,
        elapsedSeconds = cur.elapsedSeconds,
        displayPath = displayPath,
        savedUri = savedUri,
        statusMessage = "Screen recording saved to $displayPath",
      )
    }
  }

  fun markCancelled(reason: String = "Screen recording cancelled.") {
    _state.value =
      ScreenRecordingUiState(
        phase = ScreenRecordingPhase.CANCELLED,
        elapsedSeconds = 0L,
        displayPath = "",
        savedUri = null,
        statusMessage = reason,
      )
  }

  fun markFailed(reason: String) {
    _state.value =
      ScreenRecordingUiState(
        phase = ScreenRecordingPhase.FAILED,
        elapsedSeconds = 0L,
        displayPath = "",
        savedUri = null,
        statusMessage = reason,
      )
  }

  fun resetToIdle() {
    _state.value = ScreenRecordingUiState()
  }

  companion object {
    fun formatElapsed(seconds: Long): String {
      val s = seconds.coerceAtLeast(0L)
      val hrs = s / 3600L
      val mins = (s % 3600L) / 60L
      val secs = s % 60L
      return if (hrs > 0L) "%02d:%02d:%02d".format(hrs, mins, secs)
      else "%02d:%02d".format(mins, secs)
    }
  }
}
