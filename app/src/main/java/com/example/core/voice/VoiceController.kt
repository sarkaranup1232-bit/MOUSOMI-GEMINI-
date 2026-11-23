package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.VoiceAudioMetrics
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LiveVoiceControlCommand {
  PAUSE,
  RESUME,
  STOP,
}

sealed interface VoiceState {
  data object Idle : VoiceState

  data class Listening(val partialTranscript: String = "") : VoiceState

  data class TranscriptReady(val transcript: String) : VoiceState

  data class Speaking(val messageId: String) : VoiceState

  data class Error(
    val message: String,
    val isRecoverableSilence: Boolean = false,
  ) : VoiceState
}

interface SpeechToTextGateway {
  fun startListening(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
  )

  fun startListeningWithRms(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
    onRmsNormalized: (Float) -> Unit,
  ) = startListening(languageTag, onPartial, onFinal, onError)

  fun startListeningWithRmsDetailed(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (message: String, isRecoverableSilence: Boolean) -> Unit,
    onRmsNormalized: (Float) -> Unit,
  ) = startListeningWithRms(
    languageTag = languageTag,
    onPartial = onPartial,
    onFinal = onFinal,
    onError = { msg -> onError(msg, false) },
    onRmsNormalized = onRmsNormalized,
  )

  fun stopListening()

  fun release()
}

interface TextToSpeechGateway {
  fun speak(
    messageId: String,
    text: String,
    preferences: VoicePreferences,
    onStart: (String) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
  )

  fun setSpeechProgressListener(listener: (Float) -> Unit) {}

  fun stop()

  fun release()
}

@Singleton
class AndroidSpeechToTextEngine @Inject constructor(
  @ApplicationContext private val context: Context,
) : SpeechToTextGateway {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private var recognizer: SpeechRecognizer? = null
  private var listenFocusRequest: AudioFocusRequest? = null

  private fun requestListenAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val req =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          .build()
      listenFocusRequest = req
      runCatching { am.requestAudioFocus(req) }
    } else {
      @Suppress("DEPRECATION")
      runCatching {
        am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      }
    }
  }

  private fun abandonListenAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      listenFocusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
      listenFocusRequest = null
    } else {
      @Suppress("DEPRECATION")
      runCatching { am.abandonAudioFocus(null) }
    }
  }

  override fun startListening(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    startListeningWithRmsDetailed(
      languageTag = languageTag,
      onPartial = onPartial,
      onFinal = onFinal,
      onError = { msg, _ -> onError(msg) },
      onRmsNormalized = {},
    )
  }

  override fun startListeningWithRms(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
    onRmsNormalized: (Float) -> Unit,
  ) {
    startListeningWithRmsDetailed(
      languageTag = languageTag,
      onPartial = onPartial,
      onFinal = onFinal,
      onError = { msg, _ -> onError(msg) },
      onRmsNormalized = onRmsNormalized,
    )
  }

  override fun startListeningWithRmsDetailed(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (message: String, isRecoverableSilence: Boolean) -> Unit,
    onRmsNormalized: (Float) -> Unit,
  ) {
    mainHandler.post {
      if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onError("Speech recognition service is unavailable on this device.", false)
        return@post
      }

      runCatching { recognizer?.destroy() }
      requestListenAudioFocus()
      val sr = SpeechRecognizer.createSpeechRecognizer(context)
      recognizer = sr

      sr.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) = Unit
          override fun onBeginningOfSpeech() = Unit
          override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            onRmsNormalized(normalized)
          }

          override fun onBufferReceived(buffer: ByteArray?) = Unit
          override fun onEndOfSpeech() {
            onRmsNormalized(0f)
          }

          override fun onEvent(eventType: Int, params: Bundle?) = Unit

          override fun onPartialResults(partialResults: Bundle?) {
            val text =
              partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) onPartial(text)
          }

          override fun onResults(results: Bundle?) {
            onRmsNormalized(0f)
            abandonListenAudioFocus()
            val text =
              results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
              onFinal(text)
            } else {
              onError("No speech detected. Please try again.", true)
            }
          }

          override fun onError(error: Int) {
            onRmsNormalized(0f)
            abandonListenAudioFocus()
            val recoverableSilence =
              error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_CLIENT
            val message =
              when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                  "Microphone permission is required for voice input."
                SpeechRecognizer.ERROR_NO_MATCH -> "Listening for speech…"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out; resuming mic…"
                SpeechRecognizer.ERROR_AUDIO -> "Audio input interrupted; check microphone availability."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
                else -> "Speech recognition error ($error)."
              }
            onError(message, recoverableSilence)
          }
        }
      )

      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
      runCatching { sr.startListening(intent) }.onFailure { err ->
        abandonListenAudioFocus()
        onError(err.localizedMessage ?: "Unable to start speech recognizer.", false)
      }
    }
  }

  override fun stopListening() {
    mainHandler.post {
      abandonListenAudioFocus()
      runCatching { recognizer?.stopListening() }
      runCatching { recognizer?.cancel() }
    }
  }

  override fun release() {
    mainHandler.post {
      abandonListenAudioFocus()
      runCatching { recognizer?.destroy() }
      recognizer = null
    }
  }
}

@Singleton
class AndroidTextToSpeechEngine @Inject constructor(
  @ApplicationContext private val context: Context,
) : TextToSpeechGateway {

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private var tts: TextToSpeech? = null
  private var isReady: Boolean = false
  private var activeMessageId: String? = null
  private var onStartCallback: ((String) -> Unit)? = null
  private var onDoneCallback: (() -> Unit)? = null
  private var onErrorCallback: ((String) -> Unit)? = null
  private var onSpeechProgressListener: ((Float) -> Unit)? = null
  private var lastSpeakTextLength: Int = 1
  private var focusRequest: AudioFocusRequest? = null

  override fun setSpeechProgressListener(listener: (Float) -> Unit) {
    onSpeechProgressListener = listener
  }

  private fun requestAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val req =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          .build()
      focusRequest = req
      runCatching { am.requestAudioFocus(req) }
    } else {
      @Suppress("DEPRECATION")
      runCatching {
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      }
    }
  }

  private fun abandonAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
      focusRequest = null
    } else {
      @Suppress("DEPRECATION")
      runCatching { am.abandonAudioFocus(null) }
    }
  }

  private fun ensureInitialized(onInitialized: () -> Unit) {
    if (tts != null && isReady) {
      onInitialized()
      return
    }
    if (tts == null) {
      tts =
        TextToSpeech(context) { status ->
          isReady = status == TextToSpeech.SUCCESS
          if (isReady) {
            tts?.setOnUtteranceProgressListener(
              object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                  onSpeechProgressListener?.invoke(0.45f)
                  activeMessageId?.let { onStartCallback?.invoke(it) }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                  val tokenSpan = (end - start).coerceIn(1, 14)
                  val progressRatio = (tokenSpan / 10f).coerceIn(0.28f, 0.96f)
                  onSpeechProgressListener?.invoke(progressRatio)
                }

                override fun onDone(utteranceId: String?) {
                  onSpeechProgressListener?.invoke(0f)
                  abandonAudioFocus()
                  onDoneCallback?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                  onSpeechProgressListener?.invoke(0f)
                  abandonAudioFocus()
                  onErrorCallback?.invoke("Text-to-speech playback failed.")
                }
              }
            )
            onInitialized()
          } else {
            onSpeechProgressListener?.invoke(0f)
            abandonAudioFocus()
            onErrorCallback?.invoke("Unable to initialize Android Text-to-Speech.")
          }
        }
    }
  }

  override fun speak(
    messageId: String,
    text: String,
    preferences: VoicePreferences,
    onStart: (String) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
  ) {
    activeMessageId = messageId
    onStartCallback = onStart
    onDoneCallback = onDone
    onErrorCallback = onError

    val cleanText = stripMarkdownForSpeech(text)
    if (cleanText.isBlank()) {
      onSpeechProgressListener?.invoke(0f)
      onDone()
      return
    }
    lastSpeakTextLength = cleanText.length.coerceAtLeast(1)

    ensureInitialized {
      val engine = tts ?: return@ensureInitialized
      requestAudioFocus()
      val locale =
        when (preferences.preferredLanguage) {
          ResponseLanguage.BENGALI -> Locale.forLanguageTag("bn-BD")
          ResponseLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
          ResponseLanguage.ENGLISH,
          ResponseLanguage.AUTO -> Locale.US
        }
      engine.language = locale
      selectPreferredVoice(engine, locale, preferences.voiceGender)
      engine.setSpeechRate(preferences.speechRate.coerceIn(0.6f, 1.8f))
      val effectivePitch =
        (preferences.pitch * (preferences.voiceGender.preferredPitch / 1.0f)).coerceIn(0.72f, 1.45f)
      engine.setPitch(effectivePitch)
      engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
  }

  private fun selectPreferredVoice(
    engine: TextToSpeech,
    locale: Locale,
    gender: VoiceGender,
  ) {
    runCatching {
      val voices = engine.voices ?: return
      val matchingLocale =
        voices.filter { v ->
          !v.isNetworkConnectionRequired && v.locale.language == locale.language
        }.ifEmpty {
          voices.filter { it.locale.language == locale.language }
        }
      if (matchingLocale.isEmpty()) return
      val desiredMale = gender == VoiceGender.MALE
      val preferred =
        matchingLocale.firstOrNull { v ->
          val name = v.name.lowercase()
          if (desiredMale) {
            name.contains("male") || name.contains("-m-") || name.endsWith("m") ||
              name.contains("iom") || name.contains("tpd") || name.contains("gba")
          } else {
            name.contains("female") || name.contains("-f-") || name.endsWith("f") ||
              name.contains("sfg") || name.contains("iob") || name.contains("tpf")
          }
        }
      preferred?.let { engine.voice = it }
    }
  }

  override fun stop() {
    onDoneCallback = null
    onStartCallback = null
    onErrorCallback = null
    onSpeechProgressListener?.invoke(0f)
    runCatching { tts?.stop() }
    abandonAudioFocus()
  }

  override fun release() {
    onDoneCallback = null
    onStartCallback = null
    onErrorCallback = null
    runCatching { tts?.stop() }
    abandonAudioFocus()
    runCatching { tts?.shutdown() }
    tts = null
    isReady = false
  }

  private fun stripMarkdownForSpeech(markdown: String): String {
    return markdown
      .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
      .replace(Regex("[*_`#>-]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }
}

@Singleton
class VoiceController @Inject constructor(
  private val sttGateway: SpeechToTextGateway,
  private val ttsGateway: TextToSpeechGateway,
  private val audioSmokeAnalyzer: AudioSmokeAnalyzer? = null,
) {
  constructor(
    sttGateway: SpeechToTextGateway,
    ttsGateway: TextToSpeechGateway,
  ) : this(sttGateway, ttsGateway, null)

  private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
  val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

  private val _audioRmsNormalized = MutableStateFlow(0f)
  val audioRmsNormalized: StateFlow<Float> = _audioRmsNormalized.asStateFlow()

  private val _ttsSpeechEnergy = MutableStateFlow(0f)
  val ttsSpeechEnergy: StateFlow<Float> = _ttsSpeechEnergy.asStateFlow()

  private val _ttsCompletedTick = MutableStateFlow(0L)
  val ttsCompletedTick: StateFlow<Long> = _ttsCompletedTick.asStateFlow()

  private val _liveControlCommands = MutableSharedFlow<LiveVoiceControlCommand>(extraBufferCapacity = 8)
  val liveControlCommands: SharedFlow<LiveVoiceControlCommand> = _liveControlCommands.asSharedFlow()

  fun dispatchLiveControlCommand(command: LiveVoiceControlCommand) {
    _liveControlCommands.tryEmit(command)
  }

  private val _fallbackAudioMetrics = MutableStateFlow(VoiceAudioMetrics.SILENT)
  val audioMetrics: StateFlow<VoiceAudioMetrics>
    get() = audioSmokeAnalyzer?.metrics ?: _fallbackAudioMetrics.asStateFlow()

  init {
    ttsGateway.setSpeechProgressListener { energy ->
      _ttsSpeechEnergy.value = energy.coerceIn(0f, 1f)
      if (energy > 0f) {
        _audioRmsNormalized.value = energy.coerceIn(0f, 1f)
      }
    }
  }

  fun startListening(language: ResponseLanguage) {
    ttsGateway.stop()
    // Ensure hardware AudioRecord analyzer is released so SpeechRecognizer owns the mic exclusively
    audioSmokeAnalyzer?.stopCapture()
    _audioRmsNormalized.value = 0f
    _voiceState.value = VoiceState.Listening()
    sttGateway.startListeningWithRmsDetailed(
      languageTag = language.localeTag,
      onPartial = { partial -> _voiceState.value = VoiceState.Listening(partial) },
      onFinal = { finalTranscript ->
        audioSmokeAnalyzer?.publishExternalRms(0f)
        _audioRmsNormalized.value = 0f
        _voiceState.value = VoiceState.TranscriptReady(finalTranscript)
      },
      onError = { err, isRecoverableSilence ->
        audioSmokeAnalyzer?.publishExternalRms(0f)
        _audioRmsNormalized.value = 0f
        _voiceState.value = VoiceState.Error(err, isRecoverableSilence)
      },
      onRmsNormalized = { rms ->
        val clamped = rms.coerceIn(0f, 1f)
        _audioRmsNormalized.value = clamped
        audioSmokeAnalyzer?.publishExternalRms(clamped)
      },
    )
  }

  fun interruptSpeakingAndListen(language: ResponseLanguage) {
    if (_voiceState.value is VoiceState.Speaking) {
      stopSpeaking()
    }
    startListening(language)
  }

  fun stopListening() {
    sttGateway.stopListening()
    audioSmokeAnalyzer?.stopCapture()
    _audioRmsNormalized.value = 0f
    if (_voiceState.value is VoiceState.Listening || _voiceState.value is VoiceState.Error) {
      _voiceState.value = VoiceState.Idle
    }
  }

  fun speakResponse(messageId: String, text: String, preferences: VoicePreferences) {
    sttGateway.stopListening()
    audioSmokeAnalyzer?.stopCapture()
    _ttsSpeechEnergy.value = 0.4f
    _voiceState.value = VoiceState.Speaking(messageId)
    ttsGateway.speak(
      messageId = messageId,
      text = text,
      preferences = preferences,
      onStart = { id -> _voiceState.value = VoiceState.Speaking(id) },
      onDone = {
        _ttsSpeechEnergy.value = 0f
        _audioRmsNormalized.value = 0f
        _voiceState.value = VoiceState.Idle
        _ttsCompletedTick.value = _ttsCompletedTick.value + 1L
      },
      onError = { err ->
        _ttsSpeechEnergy.value = 0f
        _audioRmsNormalized.value = 0f
        _voiceState.value = VoiceState.Error(err, isRecoverableSilence = false)
      },
    )
  }

  fun stopSpeaking() {
    ttsGateway.stop()
    _ttsSpeechEnergy.value = 0f
    _audioRmsNormalized.value = 0f
    _voiceState.value = VoiceState.Idle
  }

  fun playActivationChime(preferences: VoicePreferences) {
    if (!preferences.activationSoundEnabled) return
    runCatching {
      val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
      tg.startTone(ToneGenerator.TONE_PROP_ACK, 110)
      Handler(Looper.getMainLooper()).postDelayed({ runCatching { tg.release() } }, 180L)
    }
  }

  fun resetState() {
    audioSmokeAnalyzer?.stopCapture()
    _ttsSpeechEnergy.value = 0f
    _audioRmsNormalized.value = 0f
    _voiceState.value = VoiceState.Idle
  }

  fun release() {
    audioSmokeAnalyzer?.release()
    sttGateway.release()
    ttsGateway.release()
  }
}
