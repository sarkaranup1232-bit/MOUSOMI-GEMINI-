package com.example

import com.example.analytics.parser.TabularDataParser
import com.example.core.assistant.AssistantSessionPhase
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.network.RetryPolicy
import com.example.core.voice.SpeechTextSanitizer
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.AppSettings
import com.example.domain.model.ConflictingClaim
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.OperatingMode
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SourceVerificationStatus
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.router.DefaultModelRouter
import com.example.domain.router.RealtimeIntelligenceRouter
import com.example.research.search.SearchTopic
import com.example.research.verification.FreshnessVerificationEngine
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoApiMasterSystemUnitTest {

  @Test
  fun `freshness verification engine flags time-sensitive queries, youtube trends, and builds explicit instructions`() {
    val engine = FreshnessVerificationEngine()

    assertTrue(engine.isTimeSensitive("Who is the current CM of West Bengal right now?"))
    assertTrue(engine.isTimeSensitive("Show top YouTube trends in India today"))
    assertTrue(engine.isLatestNewsQuery("What are the latest YouTube trends today?"))
    assertEquals(SearchTopic.NEWS, engine.detectTopic("Latest news headlines", OperatingMode.LATEST_NEWS))

    val sources =
      listOf(
        SearchResultItem(
          id = "s1",
          sourceNumber = 1,
          title = "West Bengal Cabinet Updates 2026",
          url = "https://news.example.com/wb-2026",
          publisher = "News Example",
          publishedTimestamp = "2026-05-12",
          snippet = "Updated report published 2026-05-12 covering policy history since 2021.",
          retrievedTimestampMs = 1_778_000_000_000L,
          verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
          sourceDomain = "news.example.com",
        ),
        SearchResultItem(
          id = "s2",
          sourceNumber = 2,
          title = "Archive Profile",
          url = "https://archive.example.org/wb",
          publisher = "Archive Org",
          publishedTimestamp = null,
          snippet = "Historical profile without explicit publish header.",
          retrievedTimestampMs = 1_778_000_000_000L,
          verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
          sourceDomain = "archive.example.org",
        ),
      )

    val instruction =
      engine.buildVerificationInstruction(
        query = "Who is the current CM of West Bengal?",
        isTimeSensitive = true,
        isLatestNews = false,
        searchProvider = "Google Gemini Search Grounding",
        answerModelLabel = "Groq Llama 3.3 70B",
        sources = sources,
        conflictingClaims = listOf(ConflictingClaim("Officeholder", "Source A", "Claim A", "Source B", "Claim B")),
      )

    assertTrue(instruction.contains("Google Gemini Search Grounding"))
    assertTrue(instruction.contains("Groq Llama 3.3 70B"))
    assertTrue(instruction.contains("publish date unverified"))
    assertTrue(instruction.contains("CONFLICTS DETECTED"))
    assertTrue(instruction.contains("[1]"))
    assertTrue(instruction.contains("[2]"))

    val emptyInstruction =
      engine.buildVerificationInstruction(
        query = "Unverified breaking event",
        isTimeSensitive = true,
        isLatestNews = true,
        searchProvider = "Google Gemini Search Grounding",
        answerModelLabel = "Gemini 4 Hydrogen",
        sources = emptyList(),
        conflictingClaims = emptyList(),
      )
    assertTrue(emptyInstruction.contains("0 verified web sources were returned"))
  }

  @Test
  fun `realtime router triggers live search for current officials and youtube trends`() {
    val router = RealtimeIntelligenceRouter(TabularDataParser(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }))
    val cmIntent =
      router.classifyIntent(
        prompt = "Who is the current CM of West Bengal?",
        hasImages = false,
        attachments = emptyList(),
      )
    assertEquals(IntelligenceIntent.LIVE_SEARCH, cmIntent)

    val ytIntent =
      router.classifyIntent(
        prompt = "What are the top YouTube trends today?",
        hasImages = false,
        attachments = emptyList(),
      )
    assertEquals(IntelligenceIntent.LIVE_SEARCH, ytIntent)
  }

  @Test
  fun `model router preserves selected Groq model when Gemini key is available for Search Coordination`() {
    val router = DefaultModelRouter()
    val withGeminiAndGroq =
      AppSettings(
        geminiApiKey = "AIzaTestGeminiKey1234567890",
        groqApiKey = "gsk_TestGroqKey1234567890",
      )
    val noPromptNeeded =
      router.checkSubstitutionNeeded(
        prompt = "Latest AI news today",
        hasImages = false,
        operatingMode = OperatingMode.LATEST_NEWS,
        requestedSlotOrModelId = VerifiedModelCatalog.GROQ_REASONING_SLOT_ID,
        settings = withGeminiAndGroq,
      )
    assertNull(noPromptNeeded)

    val groqOnly = AppSettings(geminiApiKey = "", groqApiKey = "gsk_TestGroqKey1234567890")
    val warnWhenNoGemini =
      router.checkSubstitutionNeeded(
        prompt = "Latest AI news today",
        hasImages = false,
        operatingMode = OperatingMode.LATEST_NEWS,
        requestedSlotOrModelId = VerifiedModelCatalog.GROQ_REASONING_SLOT_ID,
        settings = groqOnly,
      )
    assertNotNull(warnWhenNoGemini)
  }

  @Test
  fun `retry policy redacts secrets and maps HTTP and network errors clearly`() {
    val leaked = "Error with key AIzaSyAbCdEf1234567890XYZ and gsk_AbCdEf1234567890XYZ"
    val clean = RetryPolicy.redactSecrets(leaked)
    assertFalse(clean.contains("AIzaSyAbCdEf"))
    assertFalse(clean.contains("gsk_AbCdEf"))

    val rateLimit = RetryPolicy.mapHttpError(429, "Quota exceeded", "12")
    assertTrue(rateLimit.isRetryable)
    assertEquals(429, rateLimit.httpStatusCode)
    assertTrue(rateLimit.message.contains("12"))

    val badAuth = RetryPolicy.mapHttpError(401, "Invalid API key")
    assertFalse(badAuth.isRetryable)
    assertEquals(401, badAuth.httpStatusCode)

    val offline = RetryPolicy.mapThrowable(UnknownHostException("generativelanguage.googleapis.com"))
    assertTrue(offline.isRetryable)
    assertTrue(offline.message.contains("offline", ignoreCase = true) || offline.message.contains("internet", ignoreCase = true))
  }

  @Test
  fun `speech sanitizer detects Bengali Hindi English scripts and scores local Google natural voices higher`() {
    assertEquals("bn-IN", SpeechTextSanitizer.detectDominantLanguageTag("নমস্কার, আজকের আবহাওয়া কেমন?"))
    assertEquals("bn-BD", SpeechTextSanitizer.detectDominantLanguageTag("নমস্কার, আজকের আবহাওয়া কেমন?", fallbackTag = "bn-BD"))
    assertEquals("hi-IN", SpeechTextSanitizer.detectDominantLanguageTag("नमस्ते, आज मौसम कैसा है?"))
    assertEquals("en-US", SpeechTextSanitizer.detectDominantLanguageTag("Hello, here is the latest summary."))

    val naturalLocal =
      SpeechTextSanitizer.scoreVoiceCandidate(
        voiceName = "bn-in-x-bnf-local",
        localeLanguage = "bn",
        targetLanguage = "bn",
        desiredMale = false,
        isNetworkRequired = false,
        qualityScore = 500,
        latencyScore = 150,
        localeCountry = "IN",
        targetCountry = "IN",
      )
    val networkOther =
      SpeechTextSanitizer.scoreVoiceCandidate(
        voiceName = "en-us-network",
        localeLanguage = "en",
        targetLanguage = "bn",
        desiredMale = false,
        isNetworkRequired = true,
        qualityScore = 200,
        latencyScore = 300,
        localeCountry = "US",
        targetCountry = "IN",
      )
    assertTrue(naturalLocal > networkOther)
  }

  @Test
  fun `edge light mapper enables standalone overlay Border Light when panel is collapsed but keeps paused hidden`() {
    val standaloneCfg =
      EdgeLightConfig(
        enabled = true,
        systemOverlayEnabled = true,
        assistantEdgeGlowEnabled = true,
        scheduleEnabled = false,
      )
    val standaloneIdle =
      EdgeLightStateMapper.resolve(
        config = standaloneCfg,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 14,
        assistantSessionPhase = AssistantSessionPhase.INACTIVE,
        isOverlayHost = true,
      )
    assertTrue(standaloneIdle.isVisible)

    val paused =
      EdgeLightStateMapper.resolve(
        config = standaloneCfg,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 14,
        assistantSessionPhase = AssistantSessionPhase.PAUSED,
        isOverlayHost = true,
      )
    assertFalse(paused.isVisible)
  }
}
