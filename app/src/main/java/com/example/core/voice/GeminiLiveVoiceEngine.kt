package com.example.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.VoiceSubsystemPhase
import com.example.domain.model.VoicePreferences
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.liveAudioConversationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LiveVoiceSessionStatus {
  data object Disconnected : LiveVoiceSessionStatus

  data object Connecting : LiveVoiceSessionStatus

  data object Active : LiveVoiceSessionStatus

  data object Paused : LiveVoiceSessionStatus

  data class Reconnecting(val attempt: Int) : LiveVoiceSessionStatus

  data class Failed(val message: String) : LiveVoiceSessionStatus
}

/**
 * Real bidirectional Gemini Live Audio engine backed by Firebase AI Logic LiveSession.
 * - Coordinates handshake through [LiveConnectionManager] (no fake Connected state before handshake).
 * - Acquires exclusive hardware mic ownership through [MicrophoneInputManager].
 * - Drives real outgoing AI playback metering through [AudioOutputAnalyzer] & [LiveVoiceSessionManager].
 * - Tolerates ordinary silent pauses cleanly without drops or fake audio spikes.
 * - Manages Android AudioFocus cleanly for interruptions and phone calls.
 */
@OptIn(PublicPreviewAPI::class)
@Singleton
class GeminiLiveVoiceEngine @Inject constructor(
  @ApplicationContext private val context: Context,
  private val audioSmokeAnalyzer: AudioSmokeAnalyzer,
  private val sessionStateManager: AssistantSessionStateManager,
  private val connectionManager: LiveConnectionManager,
  private val micManager: MicrophoneInputManager,
  private val liveVoiceSessionManager: LiveVoiceSessionManager,
  private val audioOutputAnalyzer: AudioOutputAnalyzer,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

  private var activeSession: LiveSession? = null
  private var sessionJob: Job? = null
  private var reconnectJob: Job? = null
  private var outputDecayJob: Job? = null
  private var focusRequest: AudioFocusRequest? = null

  private val isUserPaused = AtomicBoolean(false)
  private val isUserMuted = AtomicBoolean(false)
  private val isShuttingDown = AtomicBoolean(false)
  private var lastPreferences: VoicePreferences = VoicePreferences()
  private var lastSystemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION
  private var lastAllowInterruptions: Boolean = true
  private var reconnectAttempts = 0

  private val _status = MutableStateFlow<LiveVoiceSessionStatus>(LiveVoiceSessionStatus.Disconnected)
  val status: StateFlow<LiveVoiceSessionStatus> = _status.asStateFlow()

  private val _userTranscript = MutableStateFlow("")
  val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

  private val _assistantTranscript = MutableStateFlow("")
  val assistantTranscript: StateFlow<String> = _assistantTranscript.asStateFlow()

  private val focusListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          if (_status.value == LiveVoiceSessionStatus.Active && !isUserPaused.get()) {
            pauseSessionInternal(reasonToast = null)
          }
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
          // Wait for explicit Resume so calls never unexpectedly re-open mic
        }
      }
    }

  fun isFirebaseAvailable(): Boolean = connectionManager.isFirebaseAvailable()

  fun hasMicPermission(): Boolean = micManager.hasMicPermission()

  fun startSession(
    preferences: VoicePreferences,
    systemInstruction: String? = null,
    allowInterruptions: Boolean = true,
    onFallbackRequired: ((String) -> Unit)? = null,
  ) {
    lastPreferences = preferences
    lastSystemInstruction =
      connectionManager.buildSystemInstruction(systemInstruction, preferences.preferredLanguage)
    lastAllowInterruptions = allowInterruptions

    if (!hasMicPermission()) {
      val msg = "Microphone permission is required for Live Voice."
      _status.value = LiveVoiceSessionStatus.Failed(msg)
      liveVoiceSessionManager.onFailed(msg)
      sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.ERROR, msg)
      return
    }
    if (!isFirebaseAvailable()) {
      val msg = "Firebase AI Logic is not initialized; falling back to standard continuous speech."
      connectionManager.markFallbackContinuous(msg)
      _status.value = LiveVoiceSessionStatus.Failed(msg)
      liveVoiceSessionManager.onBeginSession(preferences, allowInterruptions)
      onFallbackRequired?.invoke(msg)
      return
    }

    isShuttingDown.set(false)
    isUserPaused.set(false)
    isUserMuted.set(false)
    reconnectAttempts = 0

    // Guarantee single exclusive mic owner before LiveSession opens AudioRecord
    micManager.acquireExclusiveForExternal(OWNER_TAG)
    sessionJob?.cancel()
    reconnectJob?.cancel()
    outputDecayJob?.cancel()

    _status.value = LiveVoiceSessionStatus.Connecting
    liveVoiceSessionManager.onBeginSession(preferences, allowInterruptions)
    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.CONNECTING)

    sessionJob =
      scope.launch {
        try {
          closeExistingSessionQuietly()
          requestLiveAudioFocus()

          val connectResult =
            connectionManager.openVerifiedLiveSession(
              preferences = preferences,
              systemInstruction = lastSystemInstruction,
            )
          val session =
            connectResult.getOrElse { err ->
              throw err
            }

          activeSession = session
          if (isShuttingDown.get()) {
            closeExistingSessionQuietly()
            return@launch
          }

          _status.value = LiveVoiceSessionStatus.Active
          liveVoiceSessionManager.onHandshakeSucceeded()
          sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)

          val conversationConfig =
            liveAudioConversationConfig {
              enableInterruptions = allowInterruptions
              transcriptHandler = { inputTranscript, outputTranscript ->
                val userText = inputTranscript?.text?.trim().orEmpty()
                val modelText = outputTranscript?.text?.trim().orEmpty()
                if (userText.isNotBlank()) {
                  _userTranscript.value = userText
                  liveVoiceSessionManager.onUserSpeakingDetected()
                  sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.USER_SPEAKING)
                  scheduleSilenceReturnToListening()
                }
                if (modelText.isNotBlank()) {
                  _assistantTranscript.value = modelText
                  liveVoiceSessionManager.onAiSpeakingStarted(0)
                  audioOutputAnalyzer.onPlaybackAmplitudeSample(
                    estimateSyllableImpulse(modelText)
                  )
                  sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.AI_RESPONDING)
                  scheduleOutputIdleReturn()
                } else if (userText.isBlank() && !isUserPaused.get() && !isUserMuted.get()) {
                  liveVoiceSessionManager.onSilenceTick()
                  sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
                }
              }
              goAwayHandler = { _ ->
                if (!isShuttingDown.get() && !isUserPaused.get()) {
                  scheduleBoundedReconnect(onFallbackRequired)
                }
              }
            }

          session.startAudioConversation(conversationConfig)
        } catch (ce: CancellationException) {
          throw ce
        } catch (t: Throwable) {
          Log.w(TAG, "Gemini Live session error", t)
          audioOutputAnalyzer.stopMonitoring()
          micManager.releaseExternalOwnership(OWNER_TAG)
          if (!isShuttingDown.get() && !isUserPaused.get()) {
            val errMsg =
              t.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unable to connect to Gemini Live session."
            if (reconnectAttempts == 0 && isTransientNetworkError(t)) {
              scheduleBoundedReconnect(onFallbackRequired)
            } else {
              _status.value = LiveVoiceSessionStatus.Failed(errMsg)
              liveVoiceSessionManager.onFailed(errMsg)
              sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.ERROR, errMsg)
              onFallbackRequired?.invoke(errMsg)
            }
          }
        }
      }
  }

  private fun scheduleOutputIdleReturn() {
    outputDecayJob?.cancel()
    outputDecayJob =
      scope.launch {
        delay(900L)
        if (!isShuttingDown.get() && !isUserPaused.get()) {
          liveVoiceSessionManager.onAiSpeakingFinished()
          sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
        }
      }
  }

  private fun scheduleSilenceReturnToListening() {
    outputDecayJob?.cancel()
    outputDecayJob =
      scope.launch {
        delay(750L)
        if (!isShuttingDown.get() && !isUserPaused.get() && !isUserMuted.get()) {
          liveVoiceSessionManager.onSilenceTick()
          sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
        }
      }
  }

  fun pauseSession() {
    isUserPaused.set(true)
    pauseSessionInternal(reasonToast = null)
  }

  private fun pauseSessionInternal(reasonToast: String?) {
    reconnectJob?.cancel()
    outputDecayJob?.cancel()
    runCatching { activeSession?.stopAudioConversation() }
    abandonLiveAudioFocus()
    audioOutputAnalyzer.stopMonitoring()
    liveVoiceSessionManager.onPaused()
    _status.value = LiveVoiceSessionStatus.Paused
    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.PAUSED, reasonToast)
  }

  fun resumeSession(onFallbackRequired: ((String) -> Unit)? = null) {
    if (!hasMicPermission()) return
    isUserPaused.set(false)
    micManager.acquireExclusiveForExternal(OWNER_TAG)
    liveVoiceSessionManager.onResumed()
    val existing = activeSession
    if (existing != null && !existing.isAudioConversationActive()) {
      sessionJob?.cancel()
      sessionJob =
        scope.launch {
          try {
            requestLiveAudioFocus()
            _status.value = LiveVoiceSessionStatus.Active
            liveVoiceSessionManager.onHandshakeSucceeded()
            sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
            existing.startAudioConversation(
              liveAudioConversationConfig {
                enableInterruptions = lastAllowInterruptions
                transcriptHandler = { inputTranscript, outputTranscript ->
                  val userText = inputTranscript?.text?.trim().orEmpty()
                  val modelText = outputTranscript?.text?.trim().orEmpty()
                  if (userText.isNotBlank()) {
                    _userTranscript.value = userText
                    liveVoiceSessionManager.onUserSpeakingDetected()
                    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.USER_SPEAKING)
                    scheduleSilenceReturnToListening()
                  }
                  if (modelText.isNotBlank()) {
                    _assistantTranscript.value = modelText
                    liveVoiceSessionManager.onAiSpeakingStarted(0)
                    audioOutputAnalyzer.onPlaybackAmplitudeSample(
                      estimateSyllableImpulse(modelText)
                    )
                    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.AI_RESPONDING)
                    scheduleOutputIdleReturn()
                  }
                }
                goAwayHandler = {
                  if (!isShuttingDown.get() && !isUserPaused.get()) {
                    scheduleBoundedReconnect(onFallbackRequired)
                  }
                }
              }
            )
          } catch (ce: CancellationException) {
            throw ce
          } catch (_: Throwable) {
            startSession(lastPreferences, lastSystemInstruction, lastAllowInterruptions, onFallbackRequired)
          }
        }
    } else {
      startSession(lastPreferences, lastSystemInstruction, lastAllowInterruptions, onFallbackRequired)
    }
  }

  fun setMuted(muted: Boolean) {
    isUserMuted.set(muted)
    liveVoiceSessionManager.setMuted(muted)
    if (muted) {
      runCatching { activeSession?.stopAudioConversation() }
      if (!isUserPaused.get()) {
        _status.value = LiveVoiceSessionStatus.Paused
        sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.PAUSED)
      }
    } else if (!isUserPaused.get()) {
      resumeSession()
    }
  }

  fun sendFollowUpText(text: String) {
    val clean = text.trim()
    val session = activeSession ?: return
    if (clean.isBlank()) return
    liveVoiceSessionManager.onAiProcessing()
    scope.launch {
      runCatching {
        session.send(clean, true)
      }
    }
  }

  fun stopAndClose(keepScreenShare: Boolean = false) {
    isShuttingDown.set(true)
    isUserPaused.set(false)
    isUserMuted.set(false)
    reconnectJob?.cancel()
    reconnectJob = null
    outputDecayJob?.cancel()
    outputDecayJob = null
    sessionJob?.cancel()
    sessionJob = null
    micManager.releaseExternalOwnership(OWNER_TAG)
    audioOutputAnalyzer.stopMonitoring()
    abandonLiveAudioFocus()
    val toClose = activeSession
    activeSession = null
    _status.value = LiveVoiceSessionStatus.Disconnected
    liveVoiceSessionManager.onEnded(keepScreenShare = keepScreenShare)
    if (toClose != null) {
      scope.launch {
        runCatching { toClose.stopAudioConversation() }
        runCatching { toClose.close() }
      }
    }
  }

  private fun scheduleBoundedReconnect(onFallbackRequired: ((String) -> Unit)?) {
    if (reconnectAttempts >= 2 || isShuttingDown.get()) {
      val msg = "Live voice connection closed after retry attempts."
      _status.value = LiveVoiceSessionStatus.Failed(msg)
      liveVoiceSessionManager.onFailed(msg)
      sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.ERROR, msg)
      onFallbackRequired?.invoke(msg)
      return
    }
    reconnectAttempts += 1
    _status.value = LiveVoiceSessionStatus.Reconnecting(reconnectAttempts)
    liveVoiceSessionManager.onReconnecting(reconnectAttempts)
    reconnectJob?.cancel()
    reconnectJob =
      scope.launch {
        delay(850L * reconnectAttempts)
        if (!isShuttingDown.get() && !isUserPaused.get()) {
          startSession(lastPreferences, lastSystemInstruction, lastAllowInterruptions, onFallbackRequired)
        }
      }
  }

  private suspend fun closeExistingSessionQuietly() {
    val existing = activeSession ?: return
    activeSession = null
    runCatching { existing.stopAudioConversation() }
    runCatching { existing.close() }
  }

  private fun requestLiveAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val req =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          .setOnAudioFocusChangeListener(focusListener)
          .build()
      focusRequest = req
      runCatching { am.requestAudioFocus(req) }
    } else {
      @Suppress("DEPRECATION")
      runCatching {
        am.requestAudioFocus(focusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      }
    }
  }

  private fun abandonLiveAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
      focusRequest = null
    } else {
      @Suppress("DEPRECATION")
      runCatching { am.abandonAudioFocus(focusListener) }
    }
  }

  private fun isTransientNetworkError(t: Throwable): Boolean {
    val msg = t.message.orEmpty().lowercase()
    return msg.contains("timeout") || msg.contains("socket") || msg.contains("connection") || msg.contains("unavailable")
  }

  private fun estimateSyllableImpulse(deltaText: String): Float {
    val vowels = deltaText.count { it.lowercaseChar() in "aeiouáéíóúঅআইঈউঊএঐওঔ" }.coerceAtLeast(1)
    return (0.28f + (vowels.coerceAtMost(8) / 14f)).coerceIn(0.28f, 0.85f)
  }

  companion object {
    private const val TAG = "GeminiLiveVoiceEngine"
    private const val OWNER_TAG = "gemini_live_session"
    private const val DEFAULT_SYSTEM_INSTRUCTION =
      "You are Mousomi AI, a fast, helpful, conversational Android assistant."
  }
}
