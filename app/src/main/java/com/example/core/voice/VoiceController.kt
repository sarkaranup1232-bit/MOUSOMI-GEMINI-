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

object SpeechRecognizerDiagnostics {
  fun resolveExternalRecognitionComponent(context: Context): android.content.ComponentName? {
    val intent = Intent(android.speech.RecognitionService.SERVICE_INTERFACE)
    val pm = context.packageManager
    val candidates =
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.queryIntentServices(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION")
          pm.queryIntentServices(intent, 0)
        }
      }.getOrElse { emptyList() }

    val externalServices =
      candidates
        .mapNotNull { it.serviceInfo }
        .filter { it.packageName != context.packageName && it.enabled }

    if (externalServices.isEmpty()) return null

    val preferredPackages =
      listOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.tts",
        "com.google.android.as",
        "com.samsung.android.bixby.agent",
        "com.samsung.android.scs",
      )

    val chosen =
      preferredPackages.firstNotNullOfOrNull { pkg ->
        externalServices.firstOrNull { it.packageName == pkg }
      } ?: externalServices.first()

    return android.content.ComponentName(chosen.packageName, chosen.name)
  }

  fun mapErrorToMessage(error: Int): Pair<String, Boolean> {
    val recoverableSilence =
      error == SpeechRecognizer.ERROR_NO_MATCH ||
        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    val message =
      when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
          "Microphone permission is required for voice input."
        SpeechRecognizer.ERROR_NO_MATCH ->
          "No clear speech detected. Tap Retry or speak closer."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
          "No speech detected before timeout. Tap Start or Retry."
        SpeechRecognizer.ERROR_AUDIO ->
          "Microphone audio busy or unavailable. Close other audio apps and Retry."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
          "Speech network timed out (error 1). Check connection or tap Retry."
        SpeechRecognizer.ERROR_NETWORK ->
          "Network error during speech recognition. Check connection or tap Retry."
        SpeechRecognizer.ERROR_SERVER ->
          "Speech service temporarily unavailable. Tap Retry."
        SpeechRecognizer.ERROR_CLIENT ->
          "Speech recognizer could not bind or was reset. Tap Retry."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
          "Speech recognizer is busy. Wait a moment and tap Retry."
        12,
        13 ->
          "Selected speech language is unavailable on this device. Switch language or Retry."
        14 ->
          "Speech service disconnected. Tap Retry."
        15 ->
          "Too many speech requests. Wait a moment and tap Retry."
        else ->
          "Speech recognition error ($error). Tap Retry."
      }
    return message to recoverableSilence
  }
}

@Singleton
class AndroidSpeechToTextEngine @Inject constructor(
  @ApplicationContext private val context: Context,
) : SpeechToTextGateway {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private var recognizer: SpeechRecognizer? = null
  private var listenFocusRequest: AudioFocusRequest? = null
  private var activeListenToken: Long = 0L
  private var pendingStartRunnable: Runnable? = null

  private fun requestListenAudioFocus() {
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
      listenFocusRequest = req
      runCatching { am.requestAudioFocus(req) }
    } else {
      @Suppress("DEPRECATION")
      runCatching {
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
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

  private fun destroyCurrentRecognizer() {
    val existing = recognizer ?: return
    recognizer = null
    runCatching { existing.cancel() }
    runCatching { existing.destroy() }
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
      pendingStartRunnable?.let { mainHandler.removeCallbacks(it) }
      pendingStartRunnable = null

      val hadExisting = recognizer != null
      val token = ++activeListenToken
      destroyCurrentRecognizer()
      abandonListenAudioFocus()

      val startTask = Runnable {
        if (token != activeListenToken) return@Runnable
        launchRecognizerInternal(
          token = token,
          languageTag = languageTag,
          preferOnDeviceFallback = false,
          onPartial = onPartial,
          onFinal = onFinal,
          onError = onError,
          onRmsNormalized = onRmsNormalized,
        )
      }

      if (hadExisting) {
        pendingStartRunnable = startTask
        mainHandler.postDelayed(startTask, 75L)
      } else {
        startTask.run()
      }
    }
  }

  private fun launchRecognizerInternal(
    token: Long,
    languageTag: String,
    preferOnDeviceFallback: Boolean,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (message: String, isRecoverableSilence: Boolean) -> Unit,
    onRmsNormalized: (Float) -> Unit,
  ) {
    if (token != activeListenToken) return

    val externalComponent = SpeechRecognizerDiagnostics.resolveExternalRecognitionComponent(context)
    val canOnDevice =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    if (externalComponent == null && !canOnDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
      onRmsNormalized(0f)
      onError("No speech recognition service is available on this device.", false)
      return
    }

    val sr =
      when {
        preferOnDeviceFallback && canOnDevice ->
          runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
              SpeechRecognizer.createSpeechRecognizer(context)
            }
          }.getOrNull()
        externalComponent != null ->
          runCatching { SpeechRecognizer.createSpeechRecognizer(context, externalComponent) }.getOrNull()
        canOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
          runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        else -> null
      }

    if (sr == null) {
      onRmsNormalized(0f)
      onError("External speech recognition service is not installed or accessible.", false)
      return
    }

    recognizer = sr
    requestListenAudioFocus()

    var retriedOnDevice = preferOnDeviceFallback
    sr.setRecognitionListener(
      object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
          if (token != activeListenToken) return
          val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
          onRmsNormalized(normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
          if (token == activeListenToken) onRmsNormalized(0f)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
          if (token != activeListenToken) return
          val text =
            partialResults
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              .orEmpty()
          if (text.isNotBlank()) onPartial(text)
        }

        override fun onResults(results: Bundle?) {
          if (token != activeListenToken) return
          onRmsNormalized(0f)
          abandonListenAudioFocus()
          destroyCurrentRecognizer()
          val text =
            results
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              .orEmpty()
          if (text.isNotBlank()) {
            onFinal(text)
          } else {
            onError("No speech detected. Speak clearly or tap Retry.", true)
          }
        }

        override fun onError(error: Int) {
          if (token != activeListenToken) return
          onRmsNormalized(0f)
          abandonListenAudioFocus()
          destroyCurrentRecognizer()

          // Retry once with Android 12 on-device recognizer on network/timeout errors (1 or 2)
          val isNetworkFailure =
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
              error == SpeechRecognizer.ERROR_NETWORK
          if (isNetworkFailure && !retriedOnDevice && canOnDevice) {
            retriedOnDevice = true
            mainHandler.postDelayed(
              {
                if (token == activeListenToken) {
                  launchRecognizerInternal(
                    token = token,
                    languageTag = languageTag,
                    preferOnDeviceFallback = true,
                    onPartial = onPartial,
                    onFinal = onFinal,
                    onError = onError,
                    onRmsNormalized = onRmsNormalized,
                  )
                }
              },
              80L,
            )
            return
          }

          val (message, recoverableSilence) = SpeechRecognizerDiagnostics.mapErrorToMessage(error)
          onError(message, recoverableSilence)
        }
      }
    )

    val resolvedLanguage =
      languageTag.takeIf { it.isNotBlank() && it != "und" }
        ?: Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolvedLanguage)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, resolvedLanguage)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
        if (preferOnDeviceFallback) {
          putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
      }

    runCatching { sr.startListening(intent) }.onFailure { err ->
      abandonListenAudioFocus()
      destroyCurrentRecognizer()
      onRmsNormalized(0f)
      onError(err.localizedMessage ?: "Unable to start speech recognizer.", false)
    }
  }

  override fun stopListening() {
    mainHandler.post {
      activeListenToken++
      pendingStartRunnable?.let { mainHandler.removeCallbacks(it) }
      pendingStartRunnable = null
      abandonListenAudioFocus()
      destroyCurrentRecognizer()
    }
  }

  override fun release() {
    mainHandler.post {
      activeListenToken++
      pendingStartRunnable?.let { mainHandler.removeCallbacks(it) }
      pendingStartRunnable = null
      abandonListenAudioFocus()
      destroyCurrentRecognizer()
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
  private var activeUtteranceId: String? = null
  private var onStartCallback: ((String) -> Unit)? = null
  private var onDoneCallback: (() -> Unit)? = null
  private var onErrorCallback: ((String) -> Unit)? = null
  private var onSpeechProgressListener: ((Float) -> Unit)? = null
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
                  if (utteranceId != activeUtteranceId) return
                  onSpeechProgressListener?.invoke(0.45f)
                  activeMessageId?.let { onStartCallback?.invoke(it) }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                  if (utteranceId != activeUtteranceId) return
                  val tokenSpan = (end - start).coerceIn(1, 14)
                  val progressRatio = (tokenSpan / 10f).coerceIn(0.28f, 0.96f)
                  onSpeechProgressListener?.invoke(progressRatio)
                }

                override fun onDone(utteranceId: String?) {
                  if (utteranceId != activeUtteranceId) return
                  onSpeechProgressListener?.invoke(0f)
                  abandonAudioFocus()
                  onDoneCallback?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                  if (utteranceId != activeUtteranceId) return
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
    val cleanText = SpeechTextSanitizer.sanitizeForSpeech(text)
    if (cleanText.isBlank()) {
      onSpeechProgressListener?.invoke(0f)
      onDone()
      return
    }

    val utteranceId = UUID.randomUUID().toString()
    activeUtteranceId = utteranceId
    activeMessageId = messageId
    onStartCallback = onStart
    onDoneCallback = onDone
    onErrorCallback = onError

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
      // Smooth pace & natural tone avoiding harsh robotic distortion
      engine.setSpeechRate(preferences.speechRate.coerceIn(0.75f, 1.45f))
      val effectivePitch =
        (preferences.pitch * preferences.voiceGender.preferredPitch).coerceIn(0.86f, 1.14f)
      engine.setPitch(effectivePitch)
      engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
  }

  private fun selectPreferredVoice(
    engine: TextToSpeech,
    locale: Locale,
    gender: VoiceGender,
  ) {
    runCatching {
      val voices = engine.voices ?: return
      val desiredMale = gender == VoiceGender.MALE
      val bestVoice =
        voices
          .filter { it.locale.language.equals(locale.language, ignoreCase = true) }
          .maxByOrNull { v ->
            SpeechTextSanitizer.scoreVoiceCandidate(
              voiceName = v.name,
              localeLanguage = v.locale.language,
              targetLanguage = locale.language,
              desiredMale = desiredMale,
              isNetworkRequired = v.isNetworkConnectionRequired,
              qualityScore = v.quality,
              latencyScore = v.latency,
            )
          }
      bestVoice?.let { engine.voice = it }
    }
  }

  override fun stop() {
    activeUtteranceId = null
    onDoneCallback = null
    onStartCallback = null
    onErrorCallback = null
    onSpeechProgressListener?.invoke(0f)
    runCatching { tts?.stop() }
    abandonAudioFocus()
  }

  override fun release() {
    activeUtteranceId = null
    onDoneCallback = null
    onStartCallback = null
    onErrorCallback = null
    runCatching { tts?.stop() }
    abandonAudioFocus()
    runCatching { tts?.shutdown() }
    tts = null
    isReady = false
  }
}

object DictationTranscriptMerger {
  fun mergePartial(committedPrefix: String, partialTranscript: String): String {
    val cleanPartial = partialTranscript.trim()
    if (cleanPartial.isEmpty()) return committedPrefix
    val cleanPrefix = committedPrefix.trimEnd()
    return if (cleanPrefix.isEmpty()) cleanPartial else "$cleanPrefix $cleanPartial"
  }

  fun appendFinalTurn(committedPrefix: String, finalTranscript: String): String {
    val cleanTurn = finalTranscript.trim()
    if (cleanTurn.isEmpty()) return committedPrefix
    val cleanPrefix = committedPrefix.trimEnd()
    return if (cleanPrefix.isEmpty()) cleanTurn else "$cleanPrefix $cleanTurn"
  }
}

@Singleton
class VoiceController @Inject constructor(
  private val sttGateway: SpeechToTextGateway,
  private val ttsGateway: TextToSpeechGateway,
  private val audioSmokeAnalyzer: AudioSmokeAnalyzer? = null,
  private val audioOutputAnalyzer: AudioOutputAnalyzer? = null,
  private val liveVoiceSessionManager: LiveVoiceSessionManager? = null,
) {
  constructor(
    sttGateway: SpeechToTextGateway,
    ttsGateway: TextToSpeechGateway,
  ) : this(sttGateway, ttsGateway, null, null, null)

  constructor(
    sttGateway: SpeechToTextGateway,
    ttsGateway: TextToSpeechGateway,
    audioSmokeAnalyzer: AudioSmokeAnalyzer?,
  ) : this(sttGateway, ttsGateway, audioSmokeAnalyzer, null, null)

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
      val clamped = energy.coerceIn(0f, 1f)
      _ttsSpeechEnergy.value = clamped
      audioOutputAnalyzer?.onPlaybackAmplitudeSample(clamped)
      if (clamped > 0f) {
        _audioRmsNormalized.value = clamped
        liveVoiceSessionManager?.onAiSpeakingAmplitude(clamped)
      }
    }
  }

  fun startListening(language: ResponseLanguage) {
    ttsGateway.stop()
    audioOutputAnalyzer?.stopMonitoring()
    // Ensure hardware AudioRecord analyzer is released so SpeechRecognizer owns the mic exclusively
    audioSmokeAnalyzer?.stopCapture()
    _audioRmsNormalized.value = 0f
    _voiceState.value = VoiceState.Listening()
    sttGateway.startListeningWithRmsDetailed(
      languageTag = language.effectiveLocaleTag(),
      onPartial = { partial ->
        if (_voiceState.value is VoiceState.Listening) {
          _voiceState.value = VoiceState.Listening(partial)
        }
        if (partial.isNotBlank()) {
          liveVoiceSessionManager?.onUserSpeakingDetected()
        }
      },
      onFinal = { finalTranscript ->
        audioSmokeAnalyzer?.publishExternalRms(0f)
        _audioRmsNormalized.value = 0f
        _voiceState.value = VoiceState.TranscriptReady(finalTranscript)
        if (finalTranscript.isNotBlank()) {
          liveVoiceSessionManager?.onAiProcessing()
        }
      },
      onError = { err, isRecoverableSilence ->
        audioSmokeAnalyzer?.publishExternalRms(0f)
        _audioRmsNormalized.value = 0f
        _voiceState.value = VoiceState.Error(err, isRecoverableSilence)
        if (isRecoverableSilence) {
          liveVoiceSessionManager?.onSilenceTick()
        }
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
    audioSmokeAnalyzer?.publishExternalRms(0f)
    audioSmokeAnalyzer?.stopCapture()
    _audioRmsNormalized.value = 0f
    if (_voiceState.value is VoiceState.Listening ||
      _voiceState.value is VoiceState.Error ||
      _voiceState.value is VoiceState.TranscriptReady
    ) {
      _voiceState.value = VoiceState.Idle
    }
  }

  fun speakResponse(messageId: String, text: String, preferences: VoicePreferences) {
    sttGateway.stopListening()
    audioSmokeAnalyzer?.stopCapture()
    _ttsSpeechEnergy.value = 0.35f
    _voiceState.value = VoiceState.Speaking(messageId)
    audioOutputAnalyzer?.startMonitoring(0)
    liveVoiceSessionManager?.onAiSpeakingStarted(0)
    ttsGateway.speak(
      messageId = messageId,
      text = text,
      preferences = preferences,
      onStart = { id -> _voiceState.value = VoiceState.Speaking(id) },
      onDone = {
        _ttsSpeechEnergy.value = 0f
        _audioRmsNormalized.value = 0f
        audioOutputAnalyzer?.stopMonitoring()
        liveVoiceSessionManager?.onAiSpeakingFinished()
        _voiceState.value = VoiceState.Idle
        _ttsCompletedTick.value = _ttsCompletedTick.value + 1L
      },
      onError = { err ->
        _ttsSpeechEnergy.value = 0f
        _audioRmsNormalized.value = 0f
        audioOutputAnalyzer?.stopMonitoring()
        liveVoiceSessionManager?.onAiSpeakingFinished()
        _voiceState.value = VoiceState.Error(err, isRecoverableSilence = false)
      },
    )
  }

  fun stopSpeaking() {
    ttsGateway.stop()
    audioOutputAnalyzer?.stopMonitoring()
    liveVoiceSessionManager?.onAiSpeakingFinished()
    _ttsSpeechEnergy.value = 0f
    _audioRmsNormalized.value = 0f
    _voiceState.value = VoiceState.Idle
  }

  fun playActivationChime(preferences: VoicePreferences) {
    if (!preferences.activationSoundEnabled) return
    runCatching {
      val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 48)
      tg.startTone(ToneGenerator.TONE_PROP_ACK, 95)
      Handler(Looper.getMainLooper()).postDelayed({ runCatching { tg.release() } }, 160L)
    }
  }

  fun resetState() {
    audioSmokeAnalyzer?.stopCapture()
    audioOutputAnalyzer?.stopMonitoring()
    _ttsSpeechEnergy.value = 0f
    _audioRmsNormalized.value = 0f
    _voiceState.value = VoiceState.Idle
  }

  fun release() {
    audioSmokeAnalyzer?.release()
    audioOutputAnalyzer?.stopMonitoring()
    sttGateway.release()
    ttsGateway.release()
  }
}
