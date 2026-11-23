package com.example.core.voice

import android.content.Context
import com.example.domain.model.VoiceAudioMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Facade over [MicrophoneInputManager] + [AudioLevelAnalyzer] providing real-time
 * PCM AudioRecord DSP metrics for Mousomi AI's voice-reactive Smoke Orb.
 * - Requires RECORD_AUDIO permission before opening AudioRecord.
 * - Never fabricates microphone energy values; emits VoiceAudioMetrics.SILENT when stopped or silent.
 * - Releases AudioRecord immediately on stop, release, or Emergency Stop.
 */
@Singleton
class AudioSmokeAnalyzer @Inject constructor(
  @ApplicationContext private val context: Context,
  private val audioLevelAnalyzer: AudioLevelAnalyzer,
  private val microphoneInputManager: MicrophoneInputManager,
) {
  val metrics: StateFlow<VoiceAudioMetrics> = audioLevelAnalyzer.metrics
  val isCapturing: StateFlow<Boolean> = microphoneInputManager.isCapturingPcm

  fun hasMicPermission(): Boolean = microphoneInputManager.hasMicPermission()

  fun startCapture(): Boolean = microphoneInputManager.startPcmCapture(ownerTag = "audio_smoke_analyzer")

  fun publishExternalRms(rmsNormalized: Float) {
    microphoneInputManager.publishExternalRms(rmsNormalized)
  }

  fun stopCapture() {
    microphoneInputManager.stopPcmCapture()
  }

  fun release() {
    microphoneInputManager.releaseAll()
  }

  companion object {
    const val SAMPLE_RATE_HZ = AudioLevelAnalyzer.SAMPLE_RATE_HZ
    const val FRAME_SIZE_SHORTS = AudioLevelAnalyzer.FRAME_SIZE_SHORTS

    fun analyzePcmFrame(
      samples: ShortArray,
      sampleCount: Int = samples.size,
      sampleRate: Int = SAMPLE_RATE_HZ,
      noiseFloorRms: Float = AudioLevelAnalyzer.INITIAL_NOISE_FLOOR,
      previousSmoothedRms: Float = 0f,
      timestampMs: Long = System.currentTimeMillis(),
    ): AudioLevelAnalyzer.FrameDspOutcome =
      AudioLevelAnalyzer.analyzePcmFrame(
        samples = samples,
        sampleCount = sampleCount,
        sampleRate = sampleRate,
        noiseFloorRms = noiseFloorRms,
        previousSmoothedRms = previousSmoothedRms,
        timestampMs = timestampMs,
      )
  }
}
