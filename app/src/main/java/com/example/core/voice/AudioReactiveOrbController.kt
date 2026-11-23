package com.example.core.voice

import com.example.domain.model.OrbVisualMode
import com.example.domain.model.VoiceAudioMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LiveSessionPhase {
  IDLE,
  REQUESTING_PERMISSION,
  CONNECTING,
  LISTENING,
  USER_SPEAKING,
  AI_PROCESSING,
  AI_SPEAKING,
  PAUSED,
  RECONNECTING,
  ENDED,
  ERROR,
}

data class OrbReactiveFrame(
  val orbMode: OrbVisualMode = OrbVisualMode.IDLE,
  val sessionPhase: LiveSessionPhase = LiveSessionPhase.IDLE,
  val reactiveAmplitude: Float = 0f,
  val inputAmplitude: Float = 0f,
  val outputAmplitude: Float = 0f,
  val lowBandEnergy: Float = 0f,
  val midBandEnergy: Float = 0f,
  val highBandEnergy: Float = 0f,
  val pitchHz: Float = 0f,
  val pitchConfidence: Float = 0f,
  val swirlVelocity: Float = 0.55f,
  val smokeDensity: Float = 0.35f,
  val haloIntensity: Float = 0.32f,
  val isCalmIdle: Boolean = true,
  val isMuted: Boolean = false,
) {
  companion object {
    val IDLE = OrbReactiveFrame()
  }
}

/**
 * Computes real-time Smoke Orb animation parameters strictly from verified audio measurements:
 * - User microphone RMS + spectral bands via [AudioLevelAnalyzer]
 * - Outgoing AI audio playback RMS via [AudioOutputAnalyzer]
 * - Current [LiveSessionPhase] and [VadSnapshot]
 *
 * Never fabricates motion spikes during ordinary silence or inactive states.
 */
@Singleton
class AudioReactiveOrbController @Inject constructor() {

  private val _frame = MutableStateFlow(OrbReactiveFrame.IDLE)
  val frame: StateFlow<OrbReactiveFrame> = _frame.asStateFlow()

  private var smoothedAmplitude: Float = 0f

  @Synchronized
  fun update(
    sessionPhase: LiveSessionPhase,
    inputMetrics: VoiceAudioMetrics,
    outputAmplitude: Float,
    vad: VadSnapshot,
    isMuted: Boolean,
  ): OrbReactiveFrame {
    val rawInput = if (isMuted) 0f else inputMetrics.rmsNormalized.coerceIn(0f, 1f)
    val rawOutput = outputAmplitude.coerceIn(0f, 1f)

    val targetAmplitude =
      when (sessionPhase) {
        LiveSessionPhase.IDLE,
        LiveSessionPhase.ENDED,
        LiveSessionPhase.PAUSED,
        LiveSessionPhase.ERROR -> 0f
        LiveSessionPhase.CONNECTING,
        LiveSessionPhase.RECONNECTING -> 0.14f
        LiveSessionPhase.AI_PROCESSING -> 0.22f
        LiveSessionPhase.AI_SPEAKING -> rawOutput
        LiveSessionPhase.USER_SPEAKING -> rawInput
        LiveSessionPhase.LISTENING -> if (vad.isSpeechDetected) rawInput else rawInput * 0.65f
        LiveSessionPhase.REQUESTING_PERMISSION -> 0.08f
      }.coerceIn(0f, 1f)

    val alpha = if (targetAmplitude > smoothedAmplitude) 0.45f else 0.20f
    smoothedAmplitude =
      (smoothedAmplitude + alpha * (targetAmplitude - smoothedAmplitude))
        .let { if (it < 0.004f) 0f else it }
        .coerceIn(0f, 1f)

    val orbMode =
      when {
        isMuted -> OrbVisualMode.IDLE
        sessionPhase == LiveSessionPhase.USER_SPEAKING ||
          sessionPhase == LiveSessionPhase.LISTENING -> OrbVisualMode.LISTENING
        sessionPhase == LiveSessionPhase.AI_SPEAKING -> OrbVisualMode.SPEAKING
        sessionPhase == LiveSessionPhase.AI_PROCESSING ||
          sessionPhase == LiveSessionPhase.CONNECTING ||
          sessionPhase == LiveSessionPhase.RECONNECTING -> OrbVisualMode.PROCESSING
        sessionPhase == LiveSessionPhase.ERROR -> OrbVisualMode.ERROR
        else -> OrbVisualMode.IDLE
      }

    val isCalmIdle =
      smoothedAmplitude < 0.04f &&
        (sessionPhase == LiveSessionPhase.IDLE ||
          sessionPhase == LiveSessionPhase.PAUSED ||
          sessionPhase == LiveSessionPhase.ENDED ||
          (sessionPhase == LiveSessionPhase.LISTENING && !vad.isSpeechDetected))

    val swirlVelocity =
      when (orbMode) {
        OrbVisualMode.PROCESSING -> 1.45f
        OrbVisualMode.SPEAKING -> 0.95f + smoothedAmplitude * 1.15f
        OrbVisualMode.LISTENING -> 0.65f + smoothedAmplitude * 1.25f
        OrbVisualMode.ERROR -> 0.45f
        else -> 0.42f
      }

    val smokeDensity =
      when {
        isMuted || sessionPhase == LiveSessionPhase.PAUSED -> 0.22f
        orbMode == OrbVisualMode.SPEAKING -> (0.42f + smoothedAmplitude * 0.58f).coerceIn(0.25f, 1f)
        orbMode == OrbVisualMode.LISTENING -> (0.34f + smoothedAmplitude * 0.66f).coerceIn(0.25f, 1f)
        orbMode == OrbVisualMode.PROCESSING -> 0.62f
        else -> 0.28f
      }

    val haloIntensity =
      when {
        isMuted || sessionPhase == LiveSessionPhase.PAUSED || sessionPhase == LiveSessionPhase.IDLE -> 0.22f
        else -> (0.32f + smoothedAmplitude * 0.68f).coerceIn(0.22f, 1f)
      }

    val next =
      OrbReactiveFrame(
        orbMode = orbMode,
        sessionPhase = sessionPhase,
        reactiveAmplitude = smoothedAmplitude,
        inputAmplitude = rawInput,
        outputAmplitude = rawOutput,
        lowBandEnergy =
          if (sessionPhase == LiveSessionPhase.AI_SPEAKING) rawOutput * 0.72f
          else inputMetrics.lowBandEnergy,
        midBandEnergy =
          if (sessionPhase == LiveSessionPhase.AI_SPEAKING) rawOutput
          else inputMetrics.midBandEnergy,
        highBandEnergy =
          if (sessionPhase == LiveSessionPhase.AI_SPEAKING) rawOutput * 0.64f
          else inputMetrics.highBandEnergy,
        pitchHz = inputMetrics.estimatedPitchHz,
        pitchConfidence = inputMetrics.pitchConfidence,
        swirlVelocity = swirlVelocity,
        smokeDensity = smokeDensity,
        haloIntensity = haloIntensity,
        isCalmIdle = isCalmIdle,
        isMuted = isMuted,
      )
    _frame.value = next
    return next
  }

  @Synchronized
  fun reset() {
    smoothedAmplitude = 0f
    _frame.value = OrbReactiveFrame.IDLE
  }
}
