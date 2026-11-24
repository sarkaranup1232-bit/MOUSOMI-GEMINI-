package com.example

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.security.ClientRateLimiter
import com.example.core.security.SecurityValidator
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.domain.agent.AutonomousAgentOrchestrator
import com.example.domain.model.AiResponseState
import com.example.domain.model.AppSettings
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.OperatingMode
import com.example.domain.model.ProviderType
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoicePreferences
import com.example.domain.orchestrator.DefaultAiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.SettingsRepository
import com.example.domain.router.DefaultModelRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterUpgradeUnitTest {

  @Test
  fun `all six operating modes exist including independent Latest News and Task Mode`() {
    assertEquals(
      listOf(
        OperatingMode.NORMAL_CHAT,
        OperatingMode.ANALYSIS_THINK,
        OperatingMode.GOOGLE_AI_SEARCH,
        OperatingMode.DEEP_RESEARCH,
        OperatingMode.LATEST_NEWS,
        OperatingMode.TASK_MODE,
      ),
      OperatingMode.entries,
    )
  }

  @Test
  fun `edge light restores visible cyan-blue-purple ambient glow in IDLE and intensifies on active AI states`() {
    val config = EdgeLightConfig(enabled = true, scheduleEnabled = false, brightness = 0.85f)

    val idle =
      EdgeLightStateMapper.resolve(
        config = config,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
      )
    assertTrue(idle.isVisible)
    assertEquals(EdgeLightVisualState.IDLE, idle.visualState)
    assertTrue(idle.glowLayerCount >= 2)
    assertTrue(idle.colorArgs.contains(0xFF06B6D4))
    assertTrue(idle.colorArgs.contains(0xFF3B82F6))
    assertTrue(idle.colorArgs.contains(0xFFA855F7))

    val thinking =
      EdgeLightStateMapper.resolve(
        config = config,
        responseState = AiResponseState.Thinking(),
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
      )
    assertTrue(thinking.isVisible)
    assertEquals(EdgeLightVisualState.THINKING, thinking.visualState)
    assertTrue(thinking.speedMultiplier > idle.speedMultiplier)
  }

  @Test
  fun `model router supports Task Mode routing cleanly under Mousomi Auto`() {
    val router = DefaultModelRouter()
    val settings =
      AppSettings(
        geminiApiKey = "AIzaTestKey1234567890",
        groqApiKey = "gsk_TestKey1234567890",
      )

    val decision =
      router.routeWithOperatingMode(
        prompt = "Break down the deployment checklist and verify every dependency",
        hasImages = false,
        imageCount = 0,
        requestedModelId = VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID,
        settings = settings,
        operatingMode = OperatingMode.TASK_MODE,
      ).getOrThrow()

    assertEquals(OperatingMode.TASK_MODE, decision.operatingMode)
    assertTrue(decision.selectedModel.id.isNotBlank())
  }

  @Test
  fun `orchestrator emits live TaskPlanBlock in Task Mode`() = runTest {
    val settingsRepo =
      object : SettingsRepository {
        private val flowState = MutableStateFlow(AppSettings(geminiApiKey = "AIzaTestKey1234567890"))
        override fun observeSettings(): Flow<AppSettings> = flowState
        override suspend fun getSettingsSnapshot(): AppSettings = flowState.value
        override suspend fun saveSettings(settings: AppSettings) {
          flowState.value = settings
        }
      }

    val fakeProvider =
      object : AiProvider {
        override val providerType: ProviderType = ProviderType.GEMINI
        override fun streamChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
          emit(AiStreamEvent.Delta("Step 1 verified. Ready to proceed."))
          emit(AiStreamEvent.Completed)
        }
      }

    val orchestrator =
      DefaultAiOrchestrator(
        modelRouter = DefaultModelRouter(),
        settingsRepository = settingsRepo,
        securityValidator = SecurityValidator(),
        rateLimiter = ClientRateLimiter(),
        providers = mapOf(ProviderType.GEMINI to fakeProvider),
        tools = emptySet(),
        agentOrchestrator = AutonomousAgentOrchestrator(),
      )

    val events =
      orchestrator.processMessage(
        OrchestrationRequest(
          conversationId = "conv_task_test",
          userPrompt = "Audit the API configuration and draft a rollout plan",
          historyMessages = emptyList(),
          requestedModelId = VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID,
          isTaskMode = true,
          operatingMode = OperatingMode.TASK_MODE,
        )
      ).toList()

    val blockEvents = events.filterIsInstance<OrchestrationEvent.BlocksUpdated>()
    assertTrue(blockEvents.isNotEmpty())
    assertTrue(
      blockEvents.any { ev -> ev.blocks.any { it is ResponseBlock.TaskPlanBlock } }
    )
    assertTrue(
      events.filterIsInstance<OrchestrationEvent.StateChanged>().any { it.state is AiResponseState.Completed }
    )
  }

  @Test
  fun `voice controller increments ttsCompletedTick only on natural completion and preserves recoverable silence flag`() {
    var capturedOnDone: (() -> Unit)? = null
    val fakeStt =
      object : SpeechToTextGateway {
        override fun startListening(
          languageTag: String,
          onPartial: (String) -> Unit,
          onFinal: (String) -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun startListeningWithRmsDetailed(
          languageTag: String,
          onPartial: (String) -> Unit,
          onFinal: (String) -> Unit,
          onError: (String, Boolean) -> Unit,
          onRmsNormalized: (Float) -> Unit,
        ) {
          onError("Listening timed out; resuming mic…", true)
        }

        override fun stopListening() = Unit
        override fun release() = Unit
      }
    val fakeTts =
      object : TextToSpeechGateway {
        override fun speak(
          messageId: String,
          text: String,
          preferences: VoicePreferences,
          onStart: (String) -> Unit,
          onDone: () -> Unit,
          onError: (String) -> Unit,
        ) {
          capturedOnDone = onDone
          onStart(messageId)
        }

        override fun stop() {
          capturedOnDone = null
        }

        override fun release() = Unit
      }

    val controller = VoiceController(fakeStt, fakeTts)
    assertEquals(0L, controller.ttsCompletedTick.value)

    // Manual stop/interrupt must NOT trigger continuous loop auto-restart tick
    controller.speakResponse("m1", "First answer", VoicePreferences())
    controller.stopSpeaking()
    assertEquals(0L, controller.ttsCompletedTick.value)

    // Natural TTS completion DOES trigger continuous loop restart tick
    controller.speakResponse("m2", "Second answer", VoicePreferences())
    capturedOnDone?.invoke()
    assertEquals(1L, controller.ttsCompletedTick.value)

    // Recoverable silence error propagates cleanly so Live Voice auto-restarts
    controller.startListening(ResponseLanguage.ENGLISH)
    val errState = controller.voiceState.value as VoiceState.Error
    assertTrue(errState.isRecoverableSilence)
    assertFalse(controller.voiceState.value is VoiceState.Idle)
  }
}
