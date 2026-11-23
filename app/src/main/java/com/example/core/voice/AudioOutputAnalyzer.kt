package com.example.core.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Measures real outgoing AI voice playback amplitude for Orb & Border Light reactivity.
 * - Uses Android's hardware [Visualizer] waveform capture when AI playback is active and
 *   RECORD_AUDIO permission is granted.
 * - Accepts authentic TTS utterance cadence / PCM output energy updates when Visualizer
 *   is unavailable on restricted devices.
 * - Emits strictly 0f when playback stops so the Orb returns immediately to calm listening/idle.
 */
@Singleton
class AudioOutputAnalyzer @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private var visualizer: Visualizer? = null
  private val isMonitoring = AtomicBoolean(false)

  private val _outputAmplitude = MutableStateFlow(0f)
  val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

  private val _isAiAudioPlaying = MutableStateFlow(false)
  val isAiAudioPlaying: StateFlow<Boolean> = _isAiAudioPlaying.asStateFlow()

  private var smoothedAmplitude: Float = 0f

  fun startMonitoring(audioSessionId: Int = 0) {
    _isAiAudioPlaying.value = true
    if (isMonitoring.get()) return
    val hasMic =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasMic) return

    runCatching {
      val vis = Visualizer(audioSessionId)
      val range = Visualizer.getCaptureSizeRange()
      vis.captureSize = range.getOrElse(1) { range.firstOrNull() ?: 256 }.coerceAtMost(512)
      vis.setDataCaptureListener(
        object : Visualizer.OnDataCaptureListener {
          override fun onWaveFormDataCapture(
            visualizer: Visualizer?,
            waveform: ByteArray?,
            samplingRate: Int,
          ) {
            if (waveform == null || !isMonitoring.get()) return
            val rms = computeWaveformRms(waveform)
            updateSmoothed(rms)
          }

          override fun onFftDataCapture(
            visualizer: Visualizer?,
            fft: ByteArray?,
            samplingRate: Int,
          ) = Unit
        },
        (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(10_000),
        true,
        false,
      )
      vis.enabled = true
      visualizer = vis
      isMonitoring.set(true)
    }
  }

  /**
   * Feeds genuine amplitude measured from outgoing PCM frames or TTS utterance range cadence.
   */
  fun onPlaybackAmplitudeSample(rawAmplitude: Float) {
    val clamped = rawAmplitude.coerceIn(0f, 1f)
    if (clamped > 0.01f) {
      _isAiAudioPlaying.value = true
    }
    updateSmoothed(clamped)
  }

  fun onPlaybackIdle() {
    stopMonitoring()
  }

  fun stopMonitoring() {
    isMonitoring.set(false)
    _isAiAudioPlaying.value = false
    runCatching { visualizer?.enabled = false }
    runCatching { visualizer?.release() }
    visualizer = null
    smoothedAmplitude = 0f
    _outputAmplitude.value = 0f
  }

  @Synchronized
  private fun updateSmoothed(raw: Float) {
    val gated = if (raw < 0.012f) 0f else raw.coerceIn(0f, 1f)
    val alpha = if (gated > smoothedAmplitude) 0.55f else 0.24f
    smoothedAmplitude = (smoothedAmplitude + alpha * (gated - smoothedAmplitude)).coerceIn(0f, 1f)
    _outputAmplitude.value = if (smoothedAmplitude < 0.008f) 0f else smoothedAmplitude
  }

  companion object {
    /**
     * Converts unsigned 8-bit Android Visualizer PCM bytes (128 = zero crossing) into normalized RMS [0..1].
     */
    fun computeWaveformRms(waveform: ByteArray): Float {
      if (waveform.isEmpty()) return 0f
      var sumSq = 0.0
      for (b in waveform) {
        val unsigned = b.toInt() and 0xFF
        val centered = (unsigned - 128) / 128f
        sumSq += centered * centered
      }
      val rms = sqrt(sumSq / waveform.size).toFloat()
      return (rms * 2.8f).coerceIn(0f, 1f)
    }
  }
}
