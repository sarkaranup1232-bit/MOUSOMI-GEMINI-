package com.example.core.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.example.domain.model.VoiceAudioMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
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
 * Single-owner microphone capture & hardware coordination manager.
 *
 * Guarantees:
 * - Enforces RECORD_AUDIO runtime permission before opening [AudioRecord].
 * - Never allows duplicate concurrent microphone sessions (`exclusiveCaptureLock`).
 * - Attaches hardware [AcousticEchoCanceler] & [NoiseSuppressor] when supported on Android 12+.
 * - Releases hardware capture cleanly before handing off exclusive mic access to
 *   Gemini Live `LiveSession` or Android `SpeechRecognizer`.
 */
@Singleton
class MicrophoneInputManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val audioLevelAnalyzer: AudioLevelAnalyzer,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var captureJob: Job? = null
  private val exclusiveCaptureLock = AtomicBoolean(false)
  private val externalHandoffOwner = AtomicBoolean(false)

  private val _isCapturingPcm = MutableStateFlow(false)
  val isCapturingPcm: StateFlow<Boolean> = _isCapturingPcm.asStateFlow()

  private val _activeSessionOwner = MutableStateFlow<String?>(null)
  val activeSessionOwner: StateFlow<String?> = _activeSessionOwner.asStateFlow()

  fun hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  /**
   * Reserves exclusive mic ownership for an external engine (e.g. Firebase LiveSession or
   * Android SpeechRecognizer), immediately closing any active PCM AudioRecord capture first.
   */
  fun acquireExclusiveForExternal(ownerTag: String): Boolean {
    if (!hasMicPermission()) {
      stopPcmCapture()
      externalHandoffOwner.set(false)
      _activeSessionOwner.value = null
      return false
    }
    stopPcmCapture()
    externalHandoffOwner.set(true)
    _activeSessionOwner.value = ownerTag
    return true
  }

  fun releaseExternalOwnership(ownerTag: String? = null) {
    if (ownerTag == null || _activeSessionOwner.value == ownerTag) {
      externalHandoffOwner.set(false)
      _activeSessionOwner.value = null
      audioLevelAnalyzer.reset()
    }
  }

  @SuppressLint("MissingPermission")
  fun startPcmCapture(
    ownerTag: String = "pcm_analyzer",
    onMetricsFrame: ((VoiceAudioMetrics) -> Unit)? = null,
  ): Boolean {
    if (!hasMicPermission()) {
      stopPcmCapture()
      return false
    }
    if (externalHandoffOwner.get()) {
      // Do not steal mic while an external engine owns it
      return false
    }
    if (!exclusiveCaptureLock.compareAndSet(false, true)) {
      return true
    }

    val sampleRate = AudioLevelAnalyzer.SAMPLE_RATE_HZ
    val minBufferBytes =
      AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBufferBytes <= 0) {
      exclusiveCaptureLock.set(false)
      return false
    }

    val bufferShorts = AudioLevelAnalyzer.FRAME_SIZE_SHORTS.coerceAtLeast(minBufferBytes / 2)
    val recorder =
      runCatching {
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_COMMUNICATION,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferShorts * 2,
        )
      }.getOrNull()
        ?: runCatching {
          AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferShorts * 2,
          )
        }.getOrNull()

    if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
      runCatching { recorder?.release() }
      exclusiveCaptureLock.set(false)
      return false
    }

    val aec =
      runCatching {
        if (AcousticEchoCanceler.isAvailable()) {
          AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
        } else null
      }.getOrNull()

    val noiseSuppressor =
      runCatching {
        if (NoiseSuppressor.isAvailable()) {
          NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
        } else null
      }.getOrNull()

    _isCapturingPcm.value = true
    _activeSessionOwner.value = ownerTag

    captureJob?.cancel()
    captureJob =
      scope.launch {
        val pcmBuffer = ShortArray(AudioLevelAnalyzer.FRAME_SIZE_SHORTS)
        try {
          recorder.startRecording()
          while (isActive && exclusiveCaptureLock.get()) {
            val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
            if (read > 0) {
              val metrics =
                audioLevelAnalyzer.processPcmFrame(
                  samples = pcmBuffer,
                  sampleCount = read,
                  sampleRate = sampleRate,
                )
              onMetricsFrame?.invoke(metrics)
            }
          }
        } catch (_: Throwable) {
          audioLevelAnalyzer.reset()
        } finally {
          runCatching { recorder.stop() }
          runCatching { aec?.release() }
          runCatching { noiseSuppressor?.release() }
          runCatching { recorder.release() }
          exclusiveCaptureLock.set(false)
          _isCapturingPcm.value = false
          if (_activeSessionOwner.value == ownerTag) {
            _activeSessionOwner.value = null
          }
          audioLevelAnalyzer.reset()
        }
      }

    return true
  }

  fun publishExternalRms(rmsNormalized: Float) {
    if (exclusiveCaptureLock.get()) return
    audioLevelAnalyzer.publishNormalizedInputRms(rmsNormalized)
  }

  fun stopPcmCapture() {
    exclusiveCaptureLock.set(false)
    captureJob?.cancel()
    captureJob = null
    _isCapturingPcm.value = false
    if (!externalHandoffOwner.get()) {
      _activeSessionOwner.value = null
    }
    audioLevelAnalyzer.reset()
  }

  fun releaseAll() {
    stopPcmCapture()
    externalHandoffOwner.set(false)
    _activeSessionOwner.value = null
    audioLevelAnalyzer.reset()
  }
}
