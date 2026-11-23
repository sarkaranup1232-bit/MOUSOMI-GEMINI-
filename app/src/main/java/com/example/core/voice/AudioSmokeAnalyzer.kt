package com.example.core.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.domain.model.VoiceAudioMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time PCM AudioRecord DSP analyzer for Mousomi AI's voice-reactive smoke orb.
 * - Requires RECORD_AUDIO permission before opening AudioRecord.
 * - Never fabricates microphone energy values; emits VoiceAudioMetrics.SILENT when stopped or silent.
 * - Releases AudioRecord immediately on stop, release, or Emergency Stop.
 */
@Singleton
class AudioSmokeAnalyzer @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var captureJob: Job? = null
  private val isCapturingFlag = AtomicBoolean(false)

  private val _metrics = MutableStateFlow(VoiceAudioMetrics.SILENT)
  val metrics: StateFlow<VoiceAudioMetrics> = _metrics.asStateFlow()

  private val _isCapturing = MutableStateFlow(false)
  val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

  fun hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  @SuppressLint("MissingPermission")
  fun startCapture(): Boolean {
    if (!hasMicPermission()) {
      stopCapture()
      return false
    }
    if (isCapturingFlag.get()) return true

    val sampleRate = SAMPLE_RATE_HZ
    val minBufferBytes =
      AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBufferBytes <= 0) return false

    val bufferShorts = (FRAME_SIZE_SHORTS.coerceAtLeast(minBufferBytes / 2))
    val recorder =
      runCatching {
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_RECOGNITION,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferShorts * 2,
        )
      }.getOrNull() ?: return false

    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
      runCatching { recorder.release() }
      return false
    }

    isCapturingFlag.set(true)
    _isCapturing.value = true

    captureJob?.cancel()
    captureJob =
      scope.launch {
        val pcmBuffer = ShortArray(FRAME_SIZE_SHORTS)
        var noiseFloor = INITIAL_NOISE_FLOOR
        var smoothedRms = 0f
        try {
          recorder.startRecording()
          while (isActive && isCapturingFlag.get()) {
            val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
            if (read > 0) {
              val analysis =
                analyzePcmFrame(
                  samples = pcmBuffer,
                  sampleCount = read,
                  sampleRate = sampleRate,
                  noiseFloorRms = noiseFloor,
                  previousSmoothedRms = smoothedRms,
                )
              smoothedRms = analysis.nextSmoothedRms
              noiseFloor = analysis.nextNoiseFloor
              _metrics.value = analysis.metrics
            }
          }
        } catch (_: Throwable) {
          _metrics.value = VoiceAudioMetrics.SILENT
        } finally {
          runCatching { recorder.stop() }
          runCatching { recorder.release() }
          isCapturingFlag.set(false)
          _isCapturing.value = false
          _metrics.value = VoiceAudioMetrics.SILENT
        }
      }
    return true
  }

  fun publishExternalRms(rmsNormalized: Float) {
    if (isCapturingFlag.get()) return
    val clamped = rmsNormalized.coerceIn(0f, 1f)
    if (clamped < 0.01f) {
      _metrics.value = VoiceAudioMetrics.SILENT
    } else {
      _metrics.value =
        VoiceAudioMetrics(
          rmsNormalized = clamped,
          peakNormalized = (clamped * 1.15f).coerceIn(0f, 1f),
          shortTimeEnergy = clamped * clamped,
          isVoiceActive = clamped > 0.08f,
          estimatedPitchHz = 0f,
          pitchConfidence = 0f,
          lowBandEnergy = clamped * 0.85f,
          midBandEnergy = clamped,
          highBandEnergy = clamped * 0.65f,
          timestampMs = System.currentTimeMillis(),
        )
    }
  }

  fun stopCapture() {
    isCapturingFlag.set(false)
    captureJob?.cancel()
    captureJob = null
    _isCapturing.value = false
    _metrics.value = VoiceAudioMetrics.SILENT
  }

  fun release() {
    stopCapture()
  }

  data class FrameDspOutcome(
    val metrics: VoiceAudioMetrics,
    val nextSmoothedRms: Float,
    val nextNoiseFloor: Float,
  )

  companion object {
    const val SAMPLE_RATE_HZ = 16000
    const val FRAME_SIZE_SHORTS = 512
    private const val INITIAL_NOISE_FLOOR = 0.015f

    /**
     * Pure deterministic PCM DSP math — computes genuine amplitude, peak, short-time energy,
     * adaptive VAD, autocorrelation pitch (75..450 Hz) with harmonic clarity confidence, and
     * 3-band spectral approximations (low / mid / high). Returns SILENT on silent buffers.
     */
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

        // Lightweight 3-band separation: low-pass smoothed, difference (high), residual band-pass (mid)
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

      // Strict zero on silent frame so we never invent ambient mic values
      if (rawRms < 0.0005f && maxAbs < 0.0005f) {
        return FrameDspOutcome(
          metrics = VoiceAudioMetrics.SILENT.copy(timestampMs = timestampMs),
          nextSmoothedRms = 0f,
          nextNoiseFloor = noiseFloorRms,
        )
      }

      // Asymmetric attack / release smoothing for crisp voice response
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

      // Confidence-gated normalized autocorrelation pitch detection (75..450 Hz)
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
