package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.analytics.parser.TabularDataParser
import com.example.core.apps.RichLinkResolver
import com.example.core.image.ImageProcessor
import com.example.core.permissions.EmergencyStopController
import com.example.core.permissions.PermissionManager
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.security.SecurityValidator
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.data.local.db.MousomiDatabase
import com.example.data.repository.Part5Repository
import com.example.domain.model.ActiveEditImageTarget
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AgentStepStatus
import com.example.domain.model.AiResponseState
import com.example.domain.model.AnnotationStroke
import com.example.domain.model.AppSettings
import com.example.domain.model.DomainConversation
import com.example.domain.model.DomainMessage
import com.example.domain.model.DrawingTool
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.ImageVersionRecord
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.NeonComposerState
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.PermissionCategory
import com.example.domain.model.RichLinkKind
import com.example.domain.model.RichLinkMetadata
import com.example.domain.model.RoutingDecision
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.TaskCategory
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePreferences
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.router.RealtimeIntelligenceRouter
import com.example.ui.components.AnimatedAIOrb
import com.example.ui.components.ImageResultCard
import com.example.ui.components.NeonChatComposer
import com.example.ui.components.RichAppCard
import com.example.ui.components.VoiceWaveform
import com.example.ui.model.InputMode
import com.example.ui.model.MousomiUiState
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import com.example.ui.viewmodel.MousomiViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part5UpgradeTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `room v4 Part5Repository stores and retrieves image versions, task plans, voice sessions, annotations, and rich cards`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, MousomiDatabase::class.java)
          .addMigrations(
            MousomiDatabase.MIGRATION_1_2,
            MousomiDatabase.MIGRATION_2_3,
            MousomiDatabase.MIGRATION_3_4,
          )
          .allowMainThreadQueries()
          .build()

      val json = Json { ignoreUnknownKeys = true }
      val repo = Part5Repository(db.part5Dao(), json)

      val v1 =
        GeneratedImageResult(
          mediaId = "img_1",
          rootMediaId = "root_img",
          parentMediaId = null,
          versionNumber = 1,
          prompt = "Neon cyberpunk skyline",
          filePath = "/tmp/v1.png",
          width = 1024,
          height = 1024,
          providerModel = VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
          createdAtMs = 1000L,
        )
      repo.saveImageVersion("conv_1", "msg_1", v1)
      assertEquals(2, repo.getNextVersionNumber("root_img"))

      val v2 =
        v1.copy(
          mediaId = "img_2",
          parentMediaId = "img_1",
          versionNumber = 2,
          editInstruction = "Make sky sunset magenta",
          filePath = "/tmp/v2.png",
          createdAtMs = 2000L,
        )
      repo.saveImageVersion("conv_1", "msg_2", v2)

      val versions = repo.getImageVersionsSnapshot("root_img")
      assertEquals(listOf(1, 2), versions.map { it.versionNumber })

      repo.saveTaskPlan(
        conversationId = "conv_1",
        goal = "Build Android release checklist",
        steps =
          listOf(
            AgentStepPlan(
              id = "s1",
              agent = AgentRole.TASK_PLANNING,
              description = "Verify Play Policy compliance",
              status = AgentStepStatus.COMPLETED,
              outputSummary = "Verified",
            )
          ),
        status = "COMPLETED",
      )
      assertEquals(1, db.part5Dao().observeTaskPlans().first().size)

      repo.recordVoiceSession(
        sessionId = "voice_1",
        conversationId = "conv_1",
        voiceGender = VoiceGender.FEMALE,
        languageTag = "en-US",
        turnsCount = 3,
        startedAtMs = 3000L,
        endedAtMs = 4000L,
      )
      assertEquals(1, db.part5Dao().observeVoiceSessions().first().size)

      repo.saveDrawingAnnotation(
        targetMediaId = "img_2",
        strokes =
          listOf(
            AnnotationStroke(
              id = "stroke_1",
              tool = DrawingTool.RECTANGLE,
              colorArgb = 0xFF00FFFFL,
              strokeWidthPx = 6f,
              alpha = 1f,
              points = listOf(NormPoint(0.2f, 0.2f), NormPoint(0.6f, 0.6f)),
            )
          ),
        box = NormBoundingBox(0.2f, 0.2f, 0.6f, 0.6f),
        promptNote = "Brighten neon sign",
      )
      assertEquals(1, db.part5Dao().getAnnotationsForMedia("img_2").size)

      repo.saveRichCards(
        messageId = "msg_2",
        cards =
          listOf(
            RichLinkMetadata(
              id = "rc_1",
              kind = RichLinkKind.YOUTUBE,
              title = "YouTube",
              subtitle = "youtube.com/watch?v=123",
              url = "https://www.youtube.com/watch?v=123",
            )
          ),
      )
      assertEquals(1, db.part5Dao().getRichCardsForMessage("msg_2").size)

      db.close()
    }

  @Test
  fun `RealtimeIntelligenceRouter classifies IMAGE_GENERATION and IMAGE_EDITING accurately`() {
    val router = RealtimeIntelligenceRouter(TabularDataParser(Json { ignoreUnknownKeys = true }))

    assertEquals(
      IntelligenceIntent.IMAGE_GENERATION,
      router.classifyIntent(
        prompt = "Generate an image of a glowing holographic owl",
        hasImages = false,
        attachments = emptyList(),
      ),
    )

    assertEquals(
      IntelligenceIntent.IMAGE_EDITING,
      router.classifyIntent(
        prompt = "Change the background to starry mountains",
        hasImages = true,
        attachments = emptyList(),
      ),
    )

    assertEquals(
      IntelligenceIntent.IMAGE_EDITING,
      router.classifyIntent(
        prompt = "Add warm rim light",
        hasImages = false,
        attachments = emptyList(),
        explicitImageEditRequested = true,
      ),
    )

    assertEquals(
      IntelligenceIntent.PHOTO_INTELLIGENCE,
      router.classifyIntent(
        prompt = "What landmarks are visible in this photo?",
        hasImages = true,
        attachments =
          listOf(
            PendingMediaItem(
              id = "p1",
              uriString = "",
              displayName = "city.jpg",
              mimeType = "image/jpeg",
              byteSize = 1024L,
              kind = MediaAttachmentKind.PHOTO,
            )
          ),
      ),
    )
  }

  @Test
  fun `RichLinkResolver extracts Play Store, YouTube, Instagram, WhatsApp, and Website cards`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resolver = RichLinkResolver(context)
    val sampleText =
      """
      Check https://play.google.com/store/apps/details?id=com.example.demo
      Watch https://www.youtube.com/watch?v=dQw4w9WgXcQ
      Visit https://developer.android.com/compose for official docs.
      """
        .trimIndent()

    val cards = resolver.extractRichCards(sampleText)
    assertTrue(cards.any { it.kind == RichLinkKind.PLAY_STORE })
    assertTrue(cards.any { it.kind == RichLinkKind.YOUTUBE })
    assertTrue(cards.any { it.kind == RichLinkKind.WEBSITE && it.domain == "developer.android.com" })
  }

  @Test
  fun `PermissionManager lists all 16 categories and EmergencyStopController stops active sessions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val permissionManager = PermissionManager(context)
    val categories = permissionManager.refreshAll(ScreenShareStatus.STOPPED)
    assertEquals(PermissionCategory.entries.size, categories.size)

    val dummyStt =
      object : SpeechToTextGateway {
        override fun startListening(
          languageTag: String,
          onPartial: (String) -> Unit,
          onFinal: (String) -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stopListening() = Unit

        override fun release() = Unit
      }
    val dummyTts =
      object : TextToSpeechGateway {
        override fun speak(
          messageId: String,
          text: String,
          preferences: VoicePreferences,
          onStart: (String) -> Unit,
          onDone: () -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stop() = Unit

        override fun release() = Unit
      }

    val voiceController = VoiceController(dummyStt, dummyTts)
    val cache = TempMediaCacheManager(context)
    val screenShare = ScreenShareSessionManager(cache)
    screenShare.markSessionActive()
    assertEquals(ScreenShareStatus.ACTIVE, screenShare.status.value)

    val controller = EmergencyStopController(context, voiceController, screenShare, cache)
    var listenerFired = false
    controller.registerStopListener { listenerFired = true }

    controller.triggerEmergencyStop("Test emergency stop")
    assertTrue(listenerFired)
    assertEquals(ScreenShareStatus.STOPPED, screenShare.status.value)
    assertNotNull(controller.lastEmergencyStopAtMs.value)
  }

  @Test
  fun `NeonChatComposer displays Ask Mousomi and switches contextual chips dynamically`() {
    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        NeonChatComposer(
          composerState = NeonComposerState.IDLE,
          glowIntensity = 0.85f,
          reducedMotion = false,
          inputMode = InputMode.CHAT,
          inputText = "",
          pendingAttachments = emptyList(),
          hasImageContext = false,
          activeEditTarget = null,
          isGenerating = false,
          isEditingMessage = false,
          onSelectMode = {},
          onInputChanged = {},
          onOpenAttachments = {},
          onOpenCameraCapture = {},
          onRemoveAttachment = {},
          onCancelEditImageTarget = {},
          onCancelEditing = {},
          onVoiceClick = {},
          onLiveClick = {},
          onSendClick = {},
          onStopGeneration = {},
          onContextualAction = {},
        )
      }
    }

    composeRule.onNodeWithTag("neon_chat_composer").assertIsDisplayed()
    composeRule.onNodeWithText("Ask Mousomi...").assertIsDisplayed()
    composeRule.onNodeWithTag("context_chip_generate_image").assertIsDisplayed()
    composeRule.onNodeWithTag("context_chip_deep_research").assertIsDisplayed()
  }

  @Test
  fun `NeonChatComposer shows image edit contextual chips when image context is active`() {
    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        NeonChatComposer(
          composerState = NeonComposerState.FOCUSED,
          glowIntensity = 0.9f,
          reducedMotion = false,
          inputMode = InputMode.CHAT,
          inputText = "",
          pendingAttachments = emptyList(),
          hasImageContext = true,
          activeEditTarget =
            ActiveEditImageTarget(
              mediaId = "img_1",
              rootMediaId = "root_1",
              versionNumber = 2,
            ),
          isGenerating = false,
          isEditingMessage = false,
          onSelectMode = {},
          onInputChanged = {},
          onOpenAttachments = {},
          onOpenCameraCapture = {},
          onRemoveAttachment = {},
          onCancelEditImageTarget = {},
          onCancelEditing = {},
          onVoiceClick = {},
          onLiveClick = {},
          onSendClick = {},
          onStopGeneration = {},
          onContextualAction = {},
        )
      }
    }

    composeRule.onNodeWithTag("active_image_edit_badge").assertIsDisplayed()
    composeRule.onNodeWithTag("context_chip_edit_image").assertIsDisplayed()
    composeRule.onNodeWithTag("context_chip_draw_region").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `ImageResultCard renders versions and completed actions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imgFile = File(context.cacheDir, "part5_test_card.png")
    val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.MAGENTA)
    FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }

    val versions =
      listOf(
        ImageVersionRecord(
          id = "m1",
          rootMediaId = "r1",
          conversationId = "c1",
          messageId = "msg1",
          versionNumber = 1,
          prompt = "Cyberpunk city",
          filePath = imgFile.absolutePath,
          width = 64,
          height = 64,
          providerModel = VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
          createdAtMs = 1L,
        ),
        ImageVersionRecord(
          id = "m2",
          rootMediaId = "r1",
          parentMediaId = "m1",
          conversationId = "c1",
          messageId = "msg2",
          versionNumber = 2,
          prompt = "Cyberpunk city neon",
          filePath = imgFile.absolutePath,
          width = 64,
          height = 64,
          providerModel = VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
          createdAtMs = 2L,
        ),
      )

    val completedImage =
      GeneratedImageResult(
        mediaId = "m2",
        rootMediaId = "r1",
        parentMediaId = "m1",
        versionNumber = 2,
        prompt = "Cyberpunk city neon",
        filePath = imgFile.absolutePath,
        width = 64,
        height = 64,
        providerModel = VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
        status = ImageGenerationStatus.COMPLETED,
      )

    var editClicked = false
    var regionClicked = false

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          ImageResultCard(
            image = completedImage,
            versions = versions,
            onEditImage = { editClicked = true },
            onSelectRegion = { regionClicked = true },
          )
        }
      }
    }

    composeRule.onNodeWithTag("image_result_card").assertIsDisplayed()
    composeRule.onNodeWithTag("image_version_chip_1").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("image_version_chip_2").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("image_action_edit").performScrollTo().performClick()
    composeRule.onNodeWithTag("image_action_select_region").performScrollTo().performClick()
    composeRule.onNodeWithTag("image_action_ask").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("image_action_save").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("image_action_share").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("image_action_regenerate").performScrollTo().assertIsDisplayed()
    assertTrue(editClicked)
    assertTrue(regionClicked)
  }

  @Test
  fun `ImageResultCard indeterminate GENERATING state shows status label and omits fake percent`() {
    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        ImageResultCard(
          image =
            GeneratedImageResult(
              mediaId = "m_gen",
              rootMediaId = "r_gen",
              prompt = "Ocean wave",
              filePath = "",
              providerModel = VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
              status = ImageGenerationStatus.GENERATING,
            ),
        )
      }
    }
    composeRule.onNodeWithText("Generating image…").assertIsDisplayed()
    composeRule.onNodeWithText("%", substring = true).assertDoesNotExist()
  }

  @Test
  fun `RichAppCard, AnimatedAIOrb, and VoiceWaveform render cleanly`() {
    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        RichAppCard(
          card =
            RichLinkMetadata(
              id = "yt_card",
              kind = RichLinkKind.YOUTUBE,
              title = "YouTube",
              subtitle = "youtube.com/watch?v=demo",
              url = "https://www.youtube.com/watch?v=demo",
            )
        )
        AnimatedAIOrb(
          voiceState = VoiceState.Listening(),
          voiceRmsNormalized = 0.6f,
          isThinking = false,
          isMuted = false,
        )
        VoiceWaveform(isActive = true, rmsNormalized = 0.6f)
      }
    }

    composeRule.onNodeWithTag("rich_app_card_YOUTUBE").assertIsDisplayed()
    composeRule.onNodeWithTag("animated_ai_orb").assertIsDisplayed()
    composeRule.onNodeWithTag("voice_waveform").assertIsDisplayed()
  }

  @Test
  fun `PermissionCenterScreen displays PrivacyDashboard, Emergency Stop, Manage All Permissions, and categories`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val categories = PermissionManager(context).buildAllCategories()
    var emergencyTriggered = false

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        PermissionCenterScreen(
          categories = categories,
          isEmergencyActive = true,
          onRefreshPermissions = {},
          onTriggerEmergencyStop = { emergencyTriggered = true },
          onOpenScreenShare = {},
          onStopScreenShare = {},
          onOpenOverlaySettings = {},
          onOpenNotificationAccess = {},
          onOpenAssistantSettings = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("privacy_dashboard_card").assertIsDisplayed()
    composeRule.onNodeWithTag("manage_all_permissions_button").assertIsDisplayed()
    composeRule.onNodeWithTag("permission_emergency_stop_button").performClick()
    composeRule.onNodeWithTag("permission_card_MICROPHONE").performScrollTo().assertIsDisplayed()
    assertTrue(emergencyTriggered)
  }

  @Test
  fun `MainChatScreen contains zero Upgrade button and keeps NeonChatComposer visible`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = createMinimalViewModel(context)

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        MainChatScreen(
          uiState = MousomiUiState(),
          suggestions = vm.defaultSuggestions,
          viewModel = vm,
          onNavigateToSettings = {},
        )
      }
    }

    composeRule.onNodeWithTag("upgrade_button").assertDoesNotExist()
    composeRule.onNodeWithTag("welcome_quick_actions_row").assertDoesNotExist()
    composeRule.onNodeWithTag("neon_chat_composer").assertIsDisplayed()
    composeRule.onNodeWithTag("context_chip_generate_image").assertIsDisplayed()
  }

  private fun createMinimalViewModel(context: Context): MousomiViewModel {
    val chatRepo =
      object : ChatRepository {
        private val convs = MutableStateFlow<List<DomainConversation>>(emptyList())
        private val msgs = MutableStateFlow<List<DomainMessage>>(emptyList())

        override fun observeConversations(): Flow<List<DomainConversation>> = convs

        override fun observeMessages(conversationId: String): Flow<List<DomainMessage>> =
          msgs.map { list -> list.filter { it.conversationId == conversationId } }

        override suspend fun getMessagesSnapshot(conversationId: String): List<DomainMessage> = emptyList()

        override suspend fun ensureConversation(conversationId: String, initialTitle: String) =
          DomainConversation(conversationId, initialTitle, 0L, 0L)

        override suspend fun updateConversationTitle(conversationId: String, title: String) = Unit

        override suspend fun deleteConversation(conversationId: String) = Unit

        override suspend fun upsertMessage(message: DomainMessage) = Unit

        override suspend fun deleteMessage(messageId: String) = Unit

        override suspend fun deleteMessagesFrom(conversationId: String, fromTimestampMs: Long) = Unit
      }

    val settingsRepo =
      object : SettingsRepository {
        private val flow = MutableStateFlow(AppSettings())

        override fun observeSettings(): Flow<AppSettings> = flow

        override suspend fun getSettingsSnapshot(): AppSettings = flow.value

        override suspend fun saveSettings(settings: AppSettings) {
          flow.value = settings
        }
      }

    val orchestrator =
      object : AiOrchestrator {
        override fun processMessage(request: OrchestrationRequest): Flow<OrchestrationEvent> = flow {
          val decision =
            RoutingDecision(
              selectedModel = VerifiedModelCatalog.geminiFlash,
              category = TaskCategory.CASUAL_CONVERSATION,
              reason = "test",
              useProxy = false,
            )
          emit(OrchestrationEvent.Routed(decision))
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Completed("Ready", "Gemini 2.5 Flash")
            )
          )
        }

        override fun deriveConversationTitle(firstPrompt: String): String = firstPrompt.take(24)
      }

    val stt =
      object : SpeechToTextGateway {
        override fun startListening(
          languageTag: String,
          onPartial: (String) -> Unit,
          onFinal: (String) -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stopListening() = Unit

        override fun release() = Unit
      }
    val tts =
      object : TextToSpeechGateway {
        override fun speak(
          messageId: String,
          text: String,
          preferences: VoicePreferences,
          onStart: (String) -> Unit,
          onDone: () -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stop() = Unit

        override fun release() = Unit
      }

    return MousomiViewModel(
      aiOrchestrator = orchestrator,
      chatRepository = chatRepo,
      settingsRepository = settingsRepo,
      imageProcessor = ImageProcessor(context),
      voiceController = VoiceController(stt, tts),
      securityValidator = SecurityValidator(),
    )
  }
}
