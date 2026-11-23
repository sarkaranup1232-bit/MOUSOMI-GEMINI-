package com.example.core.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.VoiceSubsystemPhase
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePreferences
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
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
 * - Maintains a persistent WebSocket session across multi-second silence pauses with zero
 *   repeated SpeechRecognizer chimes or mic contention.
 * - Releases AudioSmokeAnalyzer before acquiring hardware AudioRecord.
 * - Manages Android AudioFocus cleanly for interruptions and phone calls.
 */
@OptIn(PublicPreviewAPI::class)
@Singleton
class GeminiLiveVoiceEngine @Inject constructor(
  @ApplicationContext private val context: Context,
  private val audioSmokeAnalyzer: AudioSmokeAnalyzer,
  private val sessionStateManager: AssistantSessionStateManager,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

  private var activeSession: LiveSession? = null
  private var sessionJob: Job? = null
  private var reconnectJob: Job? = null
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
          // User can tap Resume intentionally; avoid sudden unrequested mic hot-start after calls
        }
      }
    }

  fun isFirebaseAvailable(): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

  fun hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  fun startSession(
    preferences: VoicePreferences,
    systemInstruction: String? = null,
    allowInterruptions: Boolean = true,
    onFallbackRequired: ((String) -> Unit)? = null,
  ) {
    if (!hasMicPermission()) {
      val msg = "Microphone permission is required for Live Voice."
      _status.value = LiveVoiceSessionStatus.Failed(msg)
      sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.ERROR, msg)
      return
    }
    if (!isFirebaseAvailable()) {
      val msg = "Firebase AI Logic is not initialized; falling back to standard speech mode."
      _status.value = LiveVoiceSessionStatus.Failed(msg)
      onFallbackRequired?.invoke(msg)
      return
    }

    lastPreferences = preferences
    lastSystemInstruction =
      buildSystemInstruction(systemInstruction, preferences.preferredLanguage)
    lastAllowInterruptions = allowInterruptions
    isShuttingDown.set(false)
    isUserPaused.set(false)
    isUserMuted.set(false)
    reconnectAttempts = 0

    // Guarantee exclusive hardware mic ownership before LiveSession opens AudioRecord
    audioSmokeAnalyzer.stopCapture()
    sessionJob?.cancel()
    reconnectJob?.cancel()

    _status.value = LiveVoiceSessionStatus.Connecting
    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.CONNECTING)

    sessionJob =
      scope.launch {
        try {
          closeExistingSessionQuietly()
          requestLiveAudioFocus()

          val voiceName = voiceNameFor(preferences.voiceGender)
          val liveModel =
            Firebase.ai.liveModel(
              modelName = VerifiedModelCatalog.DEFAULT_LIVE_AUDIO_MODEL_ID,
              generationConfig =
                liveGenerationConfig {
                  responseModality = ResponseModality.AUDIO
                  speechConfig = SpeechConfig(voice = Voice(voiceName))
                  inputAudioTranscription = AudioTranscriptionConfig()
                  outputAudioTranscription = AudioTranscriptionConfig()
                },
              systemInstruction = content { text(lastSystemInstruction) },
            )

          val session = liveModel.connect()
          activeSession = session
          if (isShuttingDown.get()) {
            closeExistingSessionQuietly()
            return@launch
          }

          _status.value = LiveVoiceSessionStatus.Active
          sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
          audioSmokeAnalyzer.publishExternalRms(0.24f)

          val conversationConfig =
            liveAudioConversationConfig {
              enableInterruptions = allowInterruptions
              transcriptHandler = { inputTranscript, outputTranscript ->
                val userText = inputTranscript?.text?.trim().orEmpty()
                val modelText = outputTranscript?.text?.trim().orEmpty()
                if (userText.isNotBlank()) {
                  _userTranscript.value = userText
                  sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.USER_SPEAKING)
                  audioSmokeAnalyzer.publishExternalRms(0.62f)
                }
                if (modelText.isNotBlank()) {
                  _assistantTranscript.value = modelText
                  sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.AI_RESPONDING)
                  audioSmokeAnalyzer.publishExternalRms(0.75f)
                } else if (userText.isBlank() && !isUserPaused.get() && !isUserMuted.get()) {
                  sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
                  audioSmokeAnalyzer.publishExternalRms(0.2f)
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
          audioSmokeAnalyzer.publishExternalRms(0f)
          if (!isShuttingDown.get() && !isUserPaused.get()) {
            val errMsg = t.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unable to connect to Gemini Live session."
            if (reconnectAttempts == 0 && isTransientNetworkError(t)) {
              scheduleBoundedReconnect(onFallbackRequired)
            } else {
              _status.value = LiveVoiceSessionStatus.Failed(errMsg)
              sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.ERROR, errMsg)
              onFallbackRequired?.invoke(errMsg)
            }
          }
        }
      }
  }

  fun pauseSession() {
    isUserPaused.set(true)
    pauseSessionInternal(reasonToast = null)
  }

  private fun pauseSessionInternal(reasonToast: String?) {
    reconnectJob?.cancel()
    runCatching { activeSession?.stopAudioConversation() }
    abandonLiveAudioFocus()
    audioSmokeAnalyzer.publishExternalRms(0f)
    _status.value = LiveVoiceSessionStatus.Paused
    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.PAUSED, reasonToast)
  }

  fun resumeSession(onFallbackRequired: ((String) -> Unit)? = null) {
    if (!hasMicPermission()) return
    isUserPaused.set(false)
    val existing = activeSession
    if (existing != null && !existing.isAudioConversationActive()) {
      sessionJob?.cancel()
      sessionJob =
        scope.launch {
          try {
            requestLiveAudioFocus()
            _status.value = LiveVoiceSessionStatus.Active
            sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
            existing.startAudioConversation(
              liveAudioConversationConfig {
                enableInterruptions = lastAllowInterruptions
                transcriptHandler = { inputTranscript, outputTranscript ->
                  val userText = inputTranscript?.text?.trim().orEmpty()
                  val modelText = outputTranscript?.text?.trim().orEmpty()
                  if (userText.isNotBlank()) {
                    _userTranscript.value = userText
                    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.USER_SPEAKING)
                  }
                  if (modelText.isNotBlank()) {
                    _assistantTranscript.value = modelText
                    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.AI_RESPONDING)
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
    if (muted) {
      runCatching { activeSession?.stopAudioConversation() }
      audioSmokeAnalyzer.publishExternalRms(0f)
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
    scope.launch {
      runCatching {
        session.send(clean, true)
      }
    }
  }

  fun stopAndClose() {
    isShuttingDown.set(true)
    isUserPaused.set(false)
    isUserMuted.set(false)
    reconnectJob?.cancel()
    reconnectJob = null
    sessionJob?.cancel()
    sessionJob = null
    audioSmokeAnalyzer.publishExternalRms(0f)
    abandonLiveAudioFocus()
    val toClose = activeSession
    activeSession = null
    _status.value = LiveVoiceSessionStatus.Disconnected
    sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.IDLE)
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
      sessionStateManager.onVoicePhaseChanged(VoiceSubsystemPhase.ERROR, msg)
      onFallbackRequired?.invoke(msg)
      return
    }
    reconnectAttempts += 1
    _status.value = LiveVoiceSessionStatus.Reconnecting(reconnectAttempts)
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

  private fun voiceNameFor(gender: VoiceGender): String =
    when (gender) {
      VoiceGender.MALE -> "Charon"
      VoiceGender.FEMALE -> "Aoede"
    }

  private fun buildSystemInstruction(custom: String?, language: ResponseLanguage): String {
    val langHint =
      when (language) {
        ResponseLanguage.BENGALI -> "Respond concisely and naturally in Bengali unless asked otherwise."
        ResponseLanguage.HINDI -> "Respond concisely and naturally in Hindi unless asked otherwise."
        ResponseLanguage.ENGLISH -> "Respond concisely and naturally in English."
        ResponseLanguage.AUTO -> "Match the user's spoken language naturally and respond concisely."
      }
    val base = custom?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_INSTRUCTION
    return "$base $langHint"
  }

  private fun isTransientNetworkError(t: Throwable): Boolean {
    val msg = t.message.orEmpty().lowercase()
    return msg.contains("timeout") || msg.contains("socket") || msg.contains("connection") || msg.contains("unavailable")
  }

  companion object {
    private const val TAG = "GeminiLiveVoiceEngine"
    private const val DEFAULT_SYSTEM_INSTRUCTION =
      "You are Mousomi AI, a fast, helpful, conversational Android assistant."
  }
}
