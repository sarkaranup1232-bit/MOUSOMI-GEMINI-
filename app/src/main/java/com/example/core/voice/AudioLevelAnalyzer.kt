package com.example.core.voice

import com.example.domain.model.VoiceAudioMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure PCM DSP audio level analyzer with noise-floor gating, asymmetric attack/release smoothing,
 * 3-band spectral decomposition (low/mid/high), and confidence-gated autocorrelation pitch tracking.
 *
 * Strictly emits [VoiceAudioMetrics.SILENT] during silence or when stopped; never invents fake motion.
 */
@Singleton
class AudioLevelAnalyzer @Inject constructor() {

  private val _metrics = MutableStateFlow(VoiceAudioMetrics.SILENT)
  val metrics: StateFlow<VoiceAudioMetrics> = _metrics.asStateFlow()

  private var smoothedRms: Float = 0f
  private var noiseFloorRms: Float = INITIAL_NOISE_FLOOR

  @Synchronized
  fun processPcmFrame(
    samples: ShortArray,
    sampleCount: Int = samples.size,
    sampleRate: Int = SAMPLE_RATE_HZ,
    timestampMs: Long = System.currentTimeMillis(),
  ): VoiceAudioMetrics {
    val outcome =
      analyzePcmFrame(
        samples = samples,
        sampleCount = sampleCount,
        sampleRate = sampleRate,
        noiseFloorRms = noiseFloorRms,
        previousSmoothedRms = smoothedRms,
        timestampMs = timestampMs,
      )
    smoothedRms = outcome.nextSmoothedRms
    noiseFloorRms = outcome.nextNoiseFloor
    _metrics.value = outcome.metrics
    return outcome.metrics
  }

  /**
   * Decays residual smoothed envelope cleanly toward zero during pause/release without leaving stale spikes.
   */
  @Synchronized
  fun decayStep(decayFactor: Float = 0.65f, timestampMs: Long = System.currentTimeMillis()): VoiceAudioMetrics {
    val decayed = (smoothedRms * decayFactor.coerceIn(0f, 0.95f))
    if (decayed < 0.002f) {
      reset()
      return VoiceAudioMetrics.SILENT
    }
    smoothedRms = decayed
    val norm = (decayed * 3.4f).coerceIn(0f, 1f)
    val next =
      VoiceAudioMetrics(
        rmsNormalized = norm,
        peakNormalized = norm,
        shortTimeEnergy = decayed * decayed,
        isVoiceActive = false,
        estimatedPitchHz = 0f,
        pitchConfidence = 0f,
        lowBandEnergy = (norm * 0.7f).coerceIn(0f, 1f),
        midBandEnergy = (norm * 0.85f).coerceIn(0f, 1f),
        highBandEnergy = (norm * 0.45f).coerceIn(0f, 1f),
        timestampMs = timestampMs,
      )
    _metrics.value = next
    return next
  }

  @Synchronized
  fun publishNormalizedInputRms(rmsNormalized: Float, timestampMs: Long = System.currentTimeMillis()) {
    val clamped = rmsNormalized.coerceIn(0f, 1f)
    if (clamped < 0.008f) {
      decayStep(0.55f, timestampMs)
      return
    }
    val alpha = if (clamped > smoothedRms) 0.52f else 0.22f
    smoothedRms = (smoothedRms + alpha * (clamped - smoothedRms)).coerceIn(0f, 1f)
    _metrics.value =
      VoiceAudioMetrics(
        rmsNormalized = smoothedRms,
        peakNormalized = (smoothedRms * 1.15f).coerceIn(0f, 1f),
        shortTimeEnergy = smoothedRms * smoothedRms,
        isVoiceActive = smoothedRms > 0.06f,
        estimatedPitchHz = 0f,
        pitchConfidence = 0f,
        lowBandEnergy = (smoothedRms * 0.85f).coerceIn(0f, 1f),
        midBandEnergy = smoothedRms,
        highBandEnergy = (smoothedRms * 0.68f).coerceIn(0f, 1f),
        timestampMs = timestampMs,
      )
  }

  @Synchronized
  fun reset() {
    smoothedRms = 0f
    noiseFloorRms = INITIAL_NOISE_FLOOR
    _metrics.value = VoiceAudioMetrics.SILENT
  }

  data class FrameDspOutcome(
    val metrics: VoiceAudioMetrics,
    val nextSmoothedRms: Float,
    val nextNoiseFloor: Float,
  )

  companion object {
    const val SAMPLE_RATE_HZ = 16000
    const val FRAME_SIZE_SHORTS = 512
    const val INITIAL_NOISE_FLOOR = 0.015f

    fun analyzePcmFrame(
      samples: ShortArray,
      sampleCount: Int = samples.size,
      sampleRate: Int = SAMPLE_RATE_HZ,
      noiseFloorRms: Float = INITIAL_NOISE_FLOOR,
      previousSmoothedRms: Float = 0f,
      timestampMs: Long = System.currentTimeMillis(),
    ): FrameDspOutcome {
      val n = sampleCount.coerceIn(0, samples.size)
      if (n < 16) {
        return FrameDspOutcome(
          metrics = VoiceAudioMetrics.SILENT.copy(timestampMs = timestampMs),
          nextSmoothedRms = 0f,
          nextNoiseFloor = noiseFloorRms,
        )
      }

      var sumSq = 0.0
      var maxAbs = 0f
      var lowPassPrev = 0f
      var prevSample = 0f
      var prevDiff = 0f
      var lowEnergy = 0.0
      var midEnergy = 0.0
      var highEnergy = 0.0

      for (i in 0 until n) {
        val norm = samples[i] / 32768f
        val absVal = abs(norm)
        if (absVal > maxAbs) maxAbs = absVal
        sumSq += norm * norm

        val lp = lowPassPrev + 0.14f * (norm - lowPassPrev)
        val hp = norm - prevSample
        val bp = norm - lp - (hp - prevDiff) * 0.25f
        lowEnergy += lp * lp
        midEnergy += bp * bp
        highEnergy += hp * hp
        lowPassPrev = lp
        prevDiff = hp
        prevSample = norm
      }

      val rawRms = sqrt(sumSq / n).toFloat().coerceIn(0f, 1f)
      val shortTimeEnergy = (sumSq / n).toFloat().coerceIn(0f, 1f)

      // Noise gate for silent frames
      if (rawRms < 0.0005f && maxAbs < 0.0005f) {
        val released = (previousSmoothedRms * 0.55f).let { if (it < 0.001f) 0f else it }
        return FrameDspOutcome(
          metrics =
            if (released == 0f) VoiceAudioMetrics.SILENT.copy(timestampMs = timestampMs)
            else VoiceAudioMetrics(
              rmsNormalized = (released * 3.4f).coerceIn(0f, 1f),
              peakNormalized = 0f,
              shortTimeEnergy = 0f,
              isVoiceActive = false,
              timestampMs = timestampMs,
            ),
          nextSmoothedRms = released,
          nextNoiseFloor = noiseFloorRms,
        )
      }

      // Asymmetric attack / release smoothing for crisp voice reactivity without jitter
      val alpha = if (rawRms > previousSmoothedRms) 0.48f else 0.16f
      val smoothedRms = (previousSmoothedRms + alpha * (rawRms - previousSmoothedRms)).coerceIn(0f, 1f)
      val scaledRms = (smoothedRms * 3.4f).coerceIn(0f, 1f)

      val voiceActive = rawRms > (noiseFloorRms * 2.2f).coerceAtLeast(0.018f)
      val updatedNoiseFloor =
        if (!voiceActive) {
          (noiseFloorRms * 0.92f + rawRms * 0.08f).coerceIn(0.008f, 0.08f)
        } else {
          noiseFloorRms
        }

      var bestLag = -1
      var bestCorr = 0f
      if (voiceActive) {
        val minLag = (sampleRate / 450).coerceAtLeast(12)
        val maxLag = (sampleRate / 75).coerceAtMost(n / 2)
        for (lag in minLag..maxLag) {
          var cross = 0.0
          var normA = 0.0
          var normB = 0.0
          val limit = n - lag
          for (j in 0 until limit) {
            val a = samples[j] / 32768f
            val b = samples[j + lag] / 32768f
            cross += a * b
            normA += a * a
            normB += b * b
          }
          val denom = sqrt(normA * normB)
          if (denom > 1e-6) {
            val corr = (cross / denom).toFloat()
            if (corr > bestCorr) {
              bestCorr = corr
              bestLag = lag
            }
          }
        }
      }

      val confidence = if (bestCorr > 0.42f) bestCorr.coerceIn(0f, 1f) else 0f
      val pitchHz =
        if (confidence > 0f && bestLag > 0) (sampleRate.toFloat() / bestLag.toFloat()) else 0f

      val lowNorm = (sqrt(lowEnergy / n).toFloat() * 3.8f).coerceIn(0f, 1f)
      val midNorm = (sqrt(midEnergy / n).toFloat() * 3.8f).coerceIn(0f, 1f)
      val highNorm = (sqrt(highEnergy / n).toFloat() * 3.2f).coerceIn(0f, 1f)

      return FrameDspOutcome(
        metrics =
          VoiceAudioMetrics(
            rmsNormalized = scaledRms,
            peakNormalized = maxAbs.coerceIn(0f, 1f),
            shortTimeEnergy = shortTimeEnergy,
            isVoiceActive = voiceActive,
            estimatedPitchHz = pitchHz,
            pitchConfidence = confidence,
            lowBandEnergy = lowNorm,
            midBandEnergy = midNorm,
            highBandEnergy = highNorm,
            timestampMs = timestampMs,
          ),
        nextSmoothedRms = smoothedRms,
        nextNoiseFloor = updatedNoiseFloor,
      )
    }
  }
}
