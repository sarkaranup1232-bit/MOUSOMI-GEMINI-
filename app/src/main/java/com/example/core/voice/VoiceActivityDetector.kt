package com.example.core.voice

import com.example.domain.model.VoiceAudioMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VadSignal {
  SILENCE,
  SPEECH_ONSET,
  USER_SPEAKING,
  BARGE_IN_INTERRUPT,
}

data class VadSnapshot(
  val isSpeechDetected: Boolean = false,
  val signal: VadSignal = VadSignal.SILENCE,
  val silenceDurationMs: Long = 0L,
  val activeSpeechDurationMs: Long = 0L,
  val energyNormalized: Float = 0f,
)

/**
 * Adaptive Voice Activity Detector with hysteresis & hangover protection:
 * - Prevents rapid flicker from brief noise bursts by requiring consecutive onset frames.
 * - Tolerates ordinary silent pauses cleanly without dropping Live Voice sessions.
 * - Detects authentic user barge-in when the user speaks louder than threshold during AI playback.
 */
@Singleton
class VoiceActivityDetector @Inject constructor() {

  private val _state = MutableStateFlow(VadSnapshot())
  val state: StateFlow<VadSnapshot> = _state.asStateFlow()

  private var consecutiveActiveFrames = 0
  private var lastSpeechActiveAtMs = 0L
  private var speechStartedAtMs = 0L

  @Synchronized
  fun evaluate(
    metrics: VoiceAudioMetrics,
    isAiSpeaking: Boolean,
    allowInterruptions: Boolean,
    nowMs: Long = metrics.timestampMs,
  ): VadSnapshot {
    val rms = metrics.rmsNormalized
    val threshold = if (isAiSpeaking) BARGE_IN_THRESHOLD else SPEECH_THRESHOLD
    val rawAbove = metrics.isVoiceActive && rms >= threshold

    if (rawAbove) {
      consecutiveActiveFrames += 1
      if (speechStartedAtMs == 0L) speechStartedAtMs = nowMs
      lastSpeechActiveAtMs = nowMs
    } else {
      consecutiveActiveFrames = 0
    }

    val withinHangover =
      lastSpeechActiveAtMs > 0L && (nowMs - lastSpeechActiveAtMs) < SILENCE_HANGOVER_MS

    val detected =
      if (isAiSpeaking) {
        allowInterruptions && consecutiveActiveFrames >= BARGE_IN_ONSET_FRAMES
      } else {
        consecutiveActiveFrames >= SPEECH_ONSET_FRAMES || withinHangover
      }

    val signal =
      when {
        isAiSpeaking && detected -> VadSignal.BARGE_IN_INTERRUPT
        consecutiveActiveFrames == SPEECH_ONSET_FRAMES -> VadSignal.SPEECH_ONSET
        detected -> VadSignal.USER_SPEAKING
        else -> VadSignal.SILENCE
      }

    if (!detected && !rawAbove) {
      speechStartedAtMs = 0L
    }

    val silenceMs =
      if (detected || lastSpeechActiveAtMs == 0L) 0L
      else (nowMs - lastSpeechActiveAtMs).coerceAtLeast(0L)

    val speechMs =
      if (detected && speechStartedAtMs > 0L) (nowMs - speechStartedAtMs).coerceAtLeast(0L)
      else 0L

    val next =
      VadSnapshot(
        isSpeechDetected = detected,
        signal = signal,
        silenceDurationMs = silenceMs,
        activeSpeechDurationMs = speechMs,
        energyNormalized = rms,
      )
    _state.value = next
    return next
  }

  @Synchronized
  fun reset() {
    consecutiveActiveFrames = 0
    lastSpeechActiveAtMs = 0L
    speechStartedAtMs = 0L
    _state.value = VadSnapshot()
  }

  companion object {
    private const val SPEECH_THRESHOLD = 0.08f
    private const val BARGE_IN_THRESHOLD = 0.26f
    private const val SPEECH_ONSET_FRAMES = 2
    private const val BARGE_IN_ONSET_FRAMES = 3
    private const val SILENCE_HANGOVER_MS = 520L
  }
}
