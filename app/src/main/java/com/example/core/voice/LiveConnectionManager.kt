package com.example.core.voice

import android.content.Context
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePreferences
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LiveConnectionState {
  data object Disconnected : LiveConnectionState

  data object Connecting : LiveConnectionState

  data object Connected : LiveConnectionState

  data class Reconnecting(val attempt: Int) : LiveConnectionState

  data class FallbackContinuous(val reason: String) : LiveConnectionState

  data class Failed(val message: String) : LiveConnectionState
}

/**
 * Manages authentic Gemini Live bidirectional WebSocket handshake and connection state.
 * Never reports [LiveConnectionState.Connected] prior to a verified `liveModel.connect()` handshake.
 */
@OptIn(PublicPreviewAPI::class)
@Singleton
class LiveConnectionManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private val _connectionState = MutableStateFlow<LiveConnectionState>(LiveConnectionState.Disconnected)
  val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

  fun isFirebaseAvailable(): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

  fun markConnecting() {
    _connectionState.value = LiveConnectionState.Connecting
  }

  fun markReconnecting(attempt: Int) {
    _connectionState.value = LiveConnectionState.Reconnecting(attempt)
  }

  fun markFallbackContinuous(reason: String) {
    _connectionState.value = LiveConnectionState.FallbackContinuous(reason)
  }

  fun markFailed(message: String) {
    _connectionState.value = LiveConnectionState.Failed(message)
  }

  fun markDisconnected() {
    _connectionState.value = LiveConnectionState.Disconnected
  }

  suspend fun openVerifiedLiveSession(
    preferences: VoicePreferences,
    systemInstruction: String,
  ): Result<LiveSession> {
    if (!isFirebaseAvailable()) {
      val msg = "Firebase AI Logic is not initialized; falling back to standard continuous speech."
      _connectionState.value = LiveConnectionState.Failed(msg)
      return Result.failure(IllegalStateException(msg))
    }

    _connectionState.value = LiveConnectionState.Connecting
    return runCatching {
      val voiceName = voiceNameFor(preferences.voiceGender)
      val liveModel: LiveGenerativeModel =
        Firebase.ai.liveModel(
          modelName = VerifiedModelCatalog.DEFAULT_LIVE_AUDIO_MODEL_ID,
          generationConfig =
            liveGenerationConfig {
              responseModality = ResponseModality.AUDIO
              speechConfig = SpeechConfig(voice = Voice(voiceName))
              inputAudioTranscription = AudioTranscriptionConfig()
              outputAudioTranscription = AudioTranscriptionConfig()
            },
          systemInstruction = content { text(systemInstruction) },
        )
      val session = liveModel.connect()
      _connectionState.value = LiveConnectionState.Connected
      session
    }.onFailure { err ->
      val msg = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unable to connect to Gemini Live session."
      _connectionState.value = LiveConnectionState.Failed(msg)
    }
  }

  fun buildSystemInstruction(custom: String?, language: ResponseLanguage): String {
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

  fun voiceNameFor(gender: VoiceGender): String =
    when (gender) {
      VoiceGender.MALE -> "Charon"
      VoiceGender.FEMALE -> "Aoede"
    }

  companion object {
    private const val DEFAULT_SYSTEM_INSTRUCTION =
      "You are Mousomi AI, a fast, helpful, conversational Android assistant."
  }
}
