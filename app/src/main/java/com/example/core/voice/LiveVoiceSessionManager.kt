package com.example.core.voice

import com.example.core.edge.BorderLightController
import com.example.domain.model.VoiceAudioMetrics
import com.example.domain.model.VoicePreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates the full Hyper-Powerful Live Orbit Voice lifecycle across:
 * - [LiveConnectionManager] (real bidirectional handshake)
 * - [MicrophoneInputManager] (exclusive hardware mic gate)
 * - [AudioLevelAnalyzer] + [AudioOutputAnalyzer] (genuine input & AI playback amplitude)
 * - [VoiceActivityDetector] (noise gating, ordinary silence tolerance, barge-in)
 * - [AudioReactiveOrbController] (real-time Smoke Orb state)
 * - [BorderLightController] (synchronized 4-side Neon Border Light)
 * - Live duration timer (`00:00:24`) for the top-right floating control bar.
 */
@Singleton
class LiveVoiceSessionManager @Inject constructor(
  private val connectionManager: LiveConnectionManager,
  private val micManager: MicrophoneInputManager,
  private val audioLevelAnalyzer: AudioLevelAnalyzer,
  private val audioOutputAnalyzer: AudioOutputAnalyzer,
  private val vad: VoiceActivityDetector,
  private val orbController: AudioReactiveOrbController,
  private val borderLightController: BorderLightController,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val _phase = MutableStateFlow(LiveSessionPhase.IDLE)
  val phase: StateFlow<LiveSessionPhase> = _phase.asStateFlow()

  private val _elapsedSeconds = MutableStateFlow(0L)
  val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

  private val _isPaused = MutableStateFlow(false)
  val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

  private val _isMuted = MutableStateFlow(false)
  val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

  private val _isMinimized = MutableStateFlow(false)
  val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

  val connectionState: StateFlow<LiveConnectionState> = connectionManager.connectionState
  val orbFrame: StateFlow<OrbReactiveFrame> = orbController.frame
  val vadSnapshot: StateFlow<VadSnapshot> = vad.state
  val outputAmplitude: StateFlow<Float> = audioOutputAnalyzer.outputAmplitude

  private var allowInterruptionsFlag: Boolean = true
  private var timerJob: Job? = null

  init {
    scope.launch {
      audioLevelAnalyzer.metrics.collectLatest { metrics ->
        recomputeReactiveFrame(metrics = metrics, outputAmp = audioOutputAnalyzer.outputAmplitude.value)
      }
    }
    scope.launch {
      audioOutputAnalyzer.outputAmplitude.collectLatest { outAmp ->
        recomputeReactiveFrame(metrics = audioLevelAnalyzer.metrics.value, outputAmp = outAmp)
      }
    }
  }

  fun onBeginSession(preferences: VoicePreferences, allowInterruptions: Boolean) {
    allowInterruptionsFlag = allowInterruptions
    _isPaused.value = false
    _isMuted.value = false
    _isMinimized.value = false
    _elapsedSeconds.value = 0L
    vad.reset()
    transitionPhase(
      if (micManager.hasMicPermission()) LiveSessionPhase.CONNECTING
      else LiveSessionPhase.REQUESTING_PERMISSION
    )
    borderLightController.onLiveStarted()
    startDurationTimer()
  }

  fun onHandshakeSucceeded() {
    if (_isPaused.value) {
      transitionPhase(LiveSessionPhase.PAUSED)
      return
    }
    transitionPhase(LiveSessionPhase.LISTENING)
  }

  fun onReconnecting(attempt: Int) {
    connectionManager.markReconnecting(attempt)
    transitionPhase(LiveSessionPhase.RECONNECTING)
  }

  fun onUserSpeakingDetected(amplitudeHint: Float? = null) {
    if (_isPaused.value || _isMuted.value) return
    if (_phase.value == LiveSessionPhase.AI_SPEAKING && !allowInterruptionsFlag) return
    if (amplitudeHint != null) {
      audioLevelAnalyzer.publishNormalizedInputRms(amplitudeHint)
    }
    audioOutputAnalyzer.stopMonitoring()
    transitionPhase(LiveSessionPhase.USER_SPEAKING)
  }

  fun onAiProcessing() {
    if (_isPaused.value) return
    transitionPhase(LiveSessionPhase.AI_PROCESSING)
  }

  fun onAiSpeakingStarted(audioSessionId: Int = 0) {
    if (_isPaused.value) return
    audioOutputAnalyzer.startMonitoring(audioSessionId)
    transitionPhase(LiveSessionPhase.AI_SPEAKING)
  }

  fun onAiSpeakingAmplitude(amplitude: Float) {
    if (_isPaused.value) return
    audioOutputAnalyzer.onPlaybackAmplitudeSample(amplitude)
    if (amplitude > 0.02f && _phase.value != LiveSessionPhase.AI_SPEAKING) {
      transitionPhase(LiveSessionPhase.AI_SPEAKING)
    }
  }

  fun onAiSpeakingFinished() {
    audioOutputAnalyzer.stopMonitoring()
    if (!_isPaused.value && _phase.value != LiveSessionPhase.IDLE && _phase.value != LiveSessionPhase.ENDED) {
      transitionPhase(LiveSessionPhase.LISTENING)
    }
  }

  fun onSilenceTick() {
    // Ordinary silence leaves Live connection intact and settles orb gently back to Listening calm idle
    audioLevelAnalyzer.decayStep(0.60f)
    if (_phase.value == LiveSessionPhase.USER_SPEAKING && !_isPaused.value && !_isMuted.value) {
      transitionPhase(LiveSessionPhase.LISTENING)
    }
  }

  fun onPaused() {
    _isPaused.value = true
    audioOutputAnalyzer.stopMonitoring()
    audioLevelAnalyzer.reset()
    vad.reset()
    borderLightController.onPaused()
    transitionPhase(LiveSessionPhase.PAUSED)
  }

  fun onResumed() {
    _isPaused.value = false
    borderLightController.onLiveStarted()
    transitionPhase(LiveSessionPhase.LISTENING)
  }

  fun setMuted(muted: Boolean) {
    _isMuted.value = muted
    if (muted) {
      audioLevelAnalyzer.reset()
    }
    recomputeReactiveFrame(
      metrics = audioLevelAnalyzer.metrics.value,
      outputAmp = audioOutputAnalyzer.outputAmplitude.value,
    )
  }

  fun setMinimized(minimized: Boolean) {
    _isMinimized.value = minimized
  }

  fun onFailed(message: String) {
    audioOutputAnalyzer.stopMonitoring()
    audioLevelAnalyzer.reset()
    connectionManager.markFailed(message)
    transitionPhase(LiveSessionPhase.ERROR)
  }

  fun onEnded(keepScreenShare: Boolean = false) {
    timerJob?.cancel()
    timerJob = null
    _elapsedSeconds.value = 0L
    _isPaused.value = false
    _isMuted.value = false
    _isMinimized.value = false
    audioOutputAnalyzer.stopMonitoring()
    micManager.releaseAll()
    vad.reset()
    connectionManager.markDisconnected()
    orbController.reset()
    borderLightController.onStopped(keepScreenShare = keepScreenShare)
    _phase.value = LiveSessionPhase.IDLE
  }

  private fun transitionPhase(next: LiveSessionPhase) {
    _phase.value = next
    recomputeReactiveFrame(
      metrics = audioLevelAnalyzer.metrics.value,
      outputAmp = audioOutputAnalyzer.outputAmplitude.value,
    )
  }

  private fun recomputeReactiveFrame(metrics: VoiceAudioMetrics, outputAmp: Float) {
    val curPhase = _phase.value
    val isAiSpeaking = curPhase == LiveSessionPhase.AI_SPEAKING
    val vadSnap =
      if (curPhase == LiveSessionPhase.LISTENING ||
        curPhase == LiveSessionPhase.USER_SPEAKING ||
        isAiSpeaking
      ) {
        vad.evaluate(
          metrics = metrics,
          isAiSpeaking = isAiSpeaking,
          allowInterruptions = allowInterruptionsFlag,
        )
      } else {
        VadSnapshot()
      }

    val frame =
      orbController.update(
        sessionPhase = curPhase,
        inputMetrics = metrics,
        outputAmplitude = outputAmp,
        vad = vadSnap,
        isMuted = _isMuted.value,
      )
    borderLightController.syncWithOrbFrame(frame)
  }

  private fun startDurationTimer() {
    timerJob?.cancel()
    timerJob =
      scope.launch {
        while (isActive) {
          delay(1000L)
          val p = _phase.value
          if (p == LiveSessionPhase.IDLE || p == LiveSessionPhase.ENDED) break
          if (!_isPaused.value && p != LiveSessionPhase.ERROR) {
            _elapsedSeconds.value = _elapsedSeconds.value + 1L
          }
        }
      }
  }

  companion object {
    fun formatElapsedMmSs(totalSeconds: Long): String {
      val safe = totalSeconds.coerceAtLeast(0L)
      val hours = safe / 3600L
      val mins = (safe % 3600L) / 60L
      val secs = safe % 60L
      return String.format("%02d:%02d:%02d", hours, mins, secs)
    }
  }
}
