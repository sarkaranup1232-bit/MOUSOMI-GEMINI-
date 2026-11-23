package com.example

import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.edge.BorderLightController
import com.example.core.voice.AudioLevelAnalyzer
import com.example.core.voice.AudioOutputAnalyzer
import com.example.core.voice.AudioReactiveOrbController
import com.example.core.voice.LiveSessionPhase
import com.example.core.voice.LiveVoiceSessionManager
import com.example.core.voice.SpeechTextSanitizer
import com.example.core.voice.VadSignal
import com.example.core.voice.VoiceActivityDetector
import com.example.domain.model.OrbVisualMode
import com.example.domain.model.VoiceAudioMetrics
import com.example.domain.model.VoiceGender
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOrbitVoiceUnitTest {

  @Test
  fun `audio level analyzer emits silence for zero pcm frames and real energy for 440hz tone`() {
    val analyzer = AudioLevelAnalyzer()
    val silence = ShortArray(512) { 0 }

    val silentMetrics = analyzer.processPcmFrame(silence, timestampMs = 1_000L)
    assertEquals(0f, silentMetrics.rmsNormalized, 0.0001f)
    assertFalse(silentMetrics.isVoiceActive)

    // Generate authentic 440Hz sine wave at ~55% amplitude
    val tone =
      ShortArray(512) { i ->
        (sin(2.0 * PI * 440.0 * i / 16000.0) * 18_000.0).toInt().toShort()
      }

    var latest = VoiceAudioMetrics.SILENT
    repeat(3) { idx ->
      latest = analyzer.processPcmFrame(tone, timestampMs = 1_050L + idx * 32L)
    }

    assertTrue("Expected elevated RMS for active speech tone", latest.rmsNormalized > 0.35f)
    assertTrue("Expected voice activity detected for 440Hz tone", latest.isVoiceActive)
    assertTrue("Expected mid/low band spectral energy", latest.midBandEnergy > 0.05f || latest.lowBandEnergy > 0.05f)

    // Decay step should smoothly lower energy toward silence
    val decayed = analyzer.decayStep(decayFactor = 0.5f, timestampMs = 1_200L)
    assertTrue(decayed.rmsNormalized < latest.rmsNormalized)
    assertFalse(decayed.isVoiceActive)

    analyzer.reset()
    assertEquals(0f, analyzer.metrics.value.rmsNormalized, 0.0001f)
  }

  @Test
  fun `audio output analyzer converts unsigned 8bit visualizer waveforms into genuine rms`() {
    val flatSilence = ByteArray(128) { 128.toByte() }
    assertEquals(0f, AudioOutputAnalyzer.computeWaveformRms(flatSilence), 0.0001f)

    val activeOutput =
      ByteArray(128) { idx ->
        if (idx % 2 == 0) 200.toByte() else 56.toByte()
      }
    val rms = AudioOutputAnalyzer.computeWaveformRms(activeOutput)
    assertTrue("Expected active Visualizer output RMS in (0.3..1.0], was $rms", rms in 0.3f..1f)
  }

  @Test
  fun `voice activity detector tolerates silence and triggers barge in only on sustained louder speech`() {
    val vad = VoiceActivityDetector()

    // Initial silent ticks stay in SILENCE without throwing
    val silent = VoiceAudioMetrics(rmsNormalized = 0.01f, isVoiceActive = false, timestampMs = 100L)
    val s1 = vad.evaluate(silent, isAiSpeaking = false, allowInterruptions = true, nowMs = 100L)
    assertFalse(s1.isSpeechDetected)
    assertEquals(VadSignal.SILENCE, s1.signal)

    // Speech onset requires consecutive frames above threshold
    val activeSpeech = VoiceAudioMetrics(rmsNormalized = 0.22f, isVoiceActive = true, timestampMs = 130L)
    vad.evaluate(activeSpeech, isAiSpeaking = false, allowInterruptions = true, nowMs = 130L)
    val s2 =
      vad.evaluate(
        activeSpeech.copy(timestampMs = 160L),
        isAiSpeaking = false,
        allowInterruptions = true,
        nowMs = 160L,
      )
    assertTrue(s2.isSpeechDetected)
    assertEquals(VadSignal.SPEECH_ONSET, s2.signal)

    // Subsequent silent gap within hangover remains smooth
    val hangover = vad.evaluate(silent.copy(timestampMs = 280L), isAiSpeaking = false, allowInterruptions = true, nowMs = 280L)
    assertTrue(hangover.isSpeechDetected)

    // After hangover expires (> 520ms), settles cleanly back to silence
    val expired = vad.evaluate(silent.copy(timestampMs = 900L), isAiSpeaking = false, allowInterruptions = true, nowMs = 900L)
    assertFalse(expired.isSpeechDetected)
    assertEquals(VadSignal.SILENCE, expired.signal)

    // Barge-in during AI speaking requires sustained high energy frames (3 frames >= 0.26f)
    vad.reset()
    val loudBargeIn = VoiceAudioMetrics(rmsNormalized = 0.36f, isVoiceActive = true, timestampMs = 1_000L)
    val b1 = vad.evaluate(loudBargeIn, isAiSpeaking = true, allowInterruptions = true, nowMs = 1_000L)
    val b2 = vad.evaluate(loudBargeIn, isAiSpeaking = true, allowInterruptions = true, nowMs = 1_030L)
    val b3 = vad.evaluate(loudBargeIn, isAiSpeaking = true, allowInterruptions = true, nowMs = 1_060L)
    assertFalse(b1.isSpeechDetected)
    assertFalse(b2.isSpeechDetected)
    assertTrue(b3.isSpeechDetected)
    assertEquals(VadSignal.BARGE_IN_INTERRUPT, b3.signal)
  }

  @Test
  fun `audio reactive orb controller tracks real input output amplitudes and resets cleanly`() {
    val controller = AudioReactiveOrbController()
    val activeVad = com.example.core.voice.VadSnapshot(isSpeechDetected = true, signal = VadSignal.USER_SPEAKING)

    // Idle state yields calm amplitude 0
    val idle = controller.update(LiveSessionPhase.IDLE, VoiceAudioMetrics.SILENT, 0.5f, activeVad, isMuted = false)
    assertEquals(OrbVisualMode.IDLE, idle.orbMode)
    assertEquals(0f, idle.reactiveAmplitude, 0.001f)
    assertTrue(idle.isCalmIdle)

    // User speaking tracks input RMS
    val input = VoiceAudioMetrics(rmsNormalized = 0.62f, isVoiceActive = true, lowBandEnergy = 0.4f, midBandEnergy = 0.7f, highBandEnergy = 0.3f)
    val speakingUser = controller.update(LiveSessionPhase.USER_SPEAKING, input, 0f, activeVad, isMuted = false)
    assertEquals(OrbVisualMode.LISTENING, speakingUser.orbMode)
    assertTrue(speakingUser.reactiveAmplitude > 0.2f)
    assertFalse(speakingUser.isCalmIdle)

    // AI speaking tracks real output amplitude
    val speakingAi = controller.update(LiveSessionPhase.AI_SPEAKING, VoiceAudioMetrics.SILENT, 0.78f, activeVad, isMuted = false)
    assertEquals(OrbVisualMode.SPEAKING, speakingAi.orbMode)
    assertEquals(0.78f, speakingAi.outputAmplitude, 0.001f)
    assertTrue(speakingAi.reactiveAmplitude > 0.3f)

    // Muted suppresses amplitude to zero cleanly
    val muted = controller.update(LiveSessionPhase.LISTENING, input, 0f, activeVad, isMuted = true)
    assertEquals(OrbVisualMode.IDLE, muted.orbMode)
    assertTrue(muted.isMuted)

    controller.reset()
    assertEquals(0f, controller.frame.value.reactiveAmplitude, 0.0001f)
  }

  @Test
  fun `speech text sanitizer removes markdown code and URLs cleanly while voice scoring avoids espeak`() {
    val raw =
      """
      ### Summary
      Check [Mousomi Docs](https://example.com/docs/guide) and run:
      ```kotlin
      println("hello")
      ```
      Visit https://developer.android.com/guide for `AudioRecord` details!
      """.trimIndent()

    val clean = SpeechTextSanitizer.sanitizeForSpeech(raw)
    assertFalse(clean.contains("###"))
    assertFalse(clean.contains("```"))
    assertFalse(clean.contains("https://"))
    assertTrue(clean.contains("Mousomi Docs"))
    assertTrue(clean.contains("Code block omitted"))
    assertTrue(clean.contains("link to developer.android.com"))
    assertTrue(clean.contains("AudioRecord"))

    assertEquals(1.0f, VoiceGender.FEMALE.preferredPitch, 0.001f)
    assertEquals(0.96f, VoiceGender.MALE.preferredPitch, 0.001f)

    val googleFemale =
      SpeechTextSanitizer.scoreVoiceCandidate(
        voiceName = "en-us-x-sfg-local",
        localeLanguage = "en",
        targetLanguage = "en",
        desiredMale = false,
        isNetworkRequired = false,
        qualityScore = 400,
        latencyScore = 200,
      )
    val robotic =
      SpeechTextSanitizer.scoreVoiceCandidate(
        voiceName = "espeak-en-robot",
        localeLanguage = "en",
        targetLanguage = "en",
        desiredMale = false,
        isNetworkRequired = false,
        qualityScore = 200,
        latencyScore = 300,
      )
    assertTrue("Expected Google local female voice to outscore espeak", googleFemale > robotic)
  }

  @Test
  fun `border light controller and timer formatter track live session lifecycle cleanly`() {
    val sessionManager = AssistantSessionStateManager()
    val borderLight = BorderLightController(sessionManager)

    assertEquals("00:00:24", LiveVoiceSessionManager.formatElapsedMmSs(24L))
    assertEquals("01:02:05", LiveVoiceSessionManager.formatElapsedMmSs(3725L))

    borderLight.onLiveStarted()
    assertTrue(borderLight.state.value.isActive)
    assertEquals(AssistantSessionPhase.LISTENING, borderLight.state.value.mappedPhase)

    borderLight.syncWithOrbFrame(
      com.example.core.voice.OrbReactiveFrame(
        orbMode = OrbVisualMode.SPEAKING,
        sessionPhase = LiveSessionPhase.AI_SPEAKING,
        reactiveAmplitude = 0.72f,
        isCalmIdle = false,
      )
    )
    assertTrue(borderLight.state.value.isActive)
    assertEquals(AssistantSessionPhase.AI_RESPONDING, borderLight.state.value.mappedPhase)
    assertTrue(borderLight.state.value.intensity > 0.85f)

    borderLight.onPaused()
    assertFalse(borderLight.state.value.isActive)
    assertEquals(0f, borderLight.state.value.intensity, 0.0001f)

    borderLight.onStopped(keepScreenShare = false)
    assertFalse(borderLight.state.value.isActive)
    assertEquals(AssistantSessionPhase.INACTIVE, borderLight.state.value.mappedPhase)
  }
}
