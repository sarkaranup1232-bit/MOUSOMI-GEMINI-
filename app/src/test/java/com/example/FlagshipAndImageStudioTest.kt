package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.ai.image.ImageStorageManager
import com.example.ai.image.PollinationsAspectRatio
import com.example.ai.image.PollinationsImageService
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerateResponse
import com.example.ai.provider.dto.GeminiModelsListResponse
import com.example.ai.provider.dto.GeminiOperationResponse
import com.example.ai.provider.dto.GeminiLongRunningRequest
import com.example.core.image.BengaliPosterCompositor
import com.example.core.image.ImageProcessor
import com.example.core.image.LocalPhotoEditorEngine
import com.example.core.image.VisionEditPlanner
import com.example.core.security.SecurityValidator
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.AppSettings
import com.example.domain.model.DomainConversation
import com.example.domain.model.DomainMessage
import com.example.domain.model.EditExecutionTarget
import com.example.domain.model.ImageAdjustmentParams
import com.example.domain.model.ImageEditFeatureType
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.NormPoint
import com.example.domain.model.PosterDesignSpec
import com.example.domain.model.PosterFontChoice
import com.example.domain.model.PosterTextLayer
import com.example.domain.model.RoutingDecision
import com.example.domain.model.TaskCategory
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoicePreferences
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.router.DefaultModelRouter
import com.example.ui.components.ChatMessagesList
import com.example.ui.model.AiModelOption
import com.example.ui.model.ChatMessage
import com.example.ui.model.MousomiUiState
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.ModelSelectionBottomSheet
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import com.example.ui.viewmodel.MousomiViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FlagshipAndImageStudioTest {

  @get:Rule val composeRule = createComposeRule()

  private val stubGeminiApi =
    object : GeminiApiService {
      override suspend fun listModels(apiKey: String, pageSize: Int): Response<GeminiModelsListResponse> =
        Response.success(GeminiModelsListResponse())

      override suspend fun streamGenerateContent(
        model: String,
        alt: String,
        apiKey: String,
        request: GeminiGenerateRequest,
      ): Response<ResponseBody> = Response.success("".toResponseBody("text/plain".toMediaType()))

      override suspend fun generateContent(
        model: String,
        apiKey: String,
        request: GeminiGenerateRequest,
      ): Response<GeminiGenerateResponse> = Response.success(GeminiGenerateResponse())

      override suspend fun predictLongRunning(
        model: String,
        apiKey: String,
        request: GeminiLongRunningRequest,
      ): Response<GeminiOperationResponse> = Response.success(GeminiOperationResponse())

      override suspend fun getOperation(
        operationName: String,
        apiKey: String,
      ): Response<GeminiOperationResponse> = Response.success(GeminiOperationResponse())
    }

  @Test
  fun `PollinationsImageService builds Flux URL, downloads PNG bytes, and rejects HTML errors`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pngBytes =
      ByteArrayOutputStream().use { out ->
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.CYAN)
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
      }

    var serveHtmlError = false
    val interceptor = Interceptor { chain ->
      val req = chain.request()
      val (code, contentType, bodyBytes) =
        if (serveHtmlError) {
          Triple(200, "text/html", "<!DOCTYPE html><html><body>Error</body></html>".toByteArray())
        } else {
          Triple(200, "image/png", pngBytes)
        }
      okhttp3.Response.Builder()
        .request(req)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("OK")
        .header("Content-Type", contentType)
        .body(bodyBytes.toResponseBody(contentType.toMediaType()))
        .build()
    }

    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
    val service = PollinationsImageService(client, stubGeminiApi, ImageStorageManager(context), null)

    val url = service.buildImageUrl(
      prompt = "Cyberpunk Dhaka dusk",
      width = PollinationsAspectRatio.PORTRAIT_3_4.widthPx,
      height = PollinationsAspectRatio.PORTRAIT_3_4.heightPx,
      seed = 777L,
    )
    assertTrue(url.startsWith("https://image.pollinations.ai/prompt/Cyberpunk%20Dhaka%20dusk"))
    assertTrue(url.contains("width=768&height=1024&model=flux&nologo=true&seed=777"))

    val okResult = service.generateImage(prompt = "Cyberpunk Dhaka dusk", seed = 777L)
    assertTrue(okResult.isSuccess)
    val img = okResult.getOrThrow()
    assertEquals(ImageGenerationStatus.COMPLETED, img.status)
    assertEquals("pollinations:flux", img.providerModel)
    assertTrue(File(img.filePath).exists())

    serveHtmlError = true
    val failResult = service.generateImage(prompt = "Cyberpunk Dhaka dusk", seed = 778L)
    assertTrue(failResult.isFailure)
  }

  @Test
  fun `DefaultModelRouter supports all 4 Flagship models including zero-key Pollinations Flux`() {
    val router = DefaultModelRouter()
    val geminiSettings = AppSettings(geminiApiKey = "test-gemini-key")

    val hydrogen = router.route("Deep research", false, 0, VerifiedModelCatalog.HYDROGEN_SLOT_ID, geminiSettings).getOrThrow()
    assertEquals(VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID, hydrogen.selectedModel.id)

    val flash38 = router.route("Latest headlines", false, 0, VerifiedModelCatalog.FLAGSHIP_FLASH_38_ID, geminiSettings).getOrThrow()
    assertEquals(VerifiedModelCatalog.FLAGSHIP_FLASH_38_ID, flash38.selectedModel.id)

    val flash36 = router.route("Write Kotlin binary search", false, 0, VerifiedModelCatalog.BALANCED_CODING_36_ID, geminiSettings).getOrThrow()
    assertEquals(VerifiedModelCatalog.BALANCED_CODING_36_ID, flash36.selectedModel.id)

    val zeroKeySettings = AppSettings(geminiApiKey = "", groqApiKey = "")
    val flux = router.route("Draw mountain sunrise", false, 0, VerifiedModelCatalog.POLLINATIONS_FLUX_ID, zeroKeySettings).getOrThrow()
    assertEquals(VerifiedModelCatalog.POLLINATIONS_FLUX_ID, flux.selectedModel.id)
  }

  @Test
  fun `BengaliPosterCompositor, LocalPhotoEditorEngine, and VisionEditPlanner work offline`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val compositor = BengaliPosterCompositor(context)
    val posterFile =
      compositor.renderPoster(
        PosterDesignSpec(
          layers = listOf(
            PosterTextLayer(id = "h1", text = "মৌসুমী এআই স্টুডিও", fontChoice = PosterFontChoice.NOTO_SANS_BENGALI),
          )
        ),
        targetScaleForPreview = 0.5f,
      ).getOrThrow()
    assertTrue(posterFile.exists() && posterFile.length() > 0L)

    val editor = LocalPhotoEditorEngine(context)
    val srcFile = File(context.cacheDir, "studio_src_test.png")
    val srcBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    srcBmp.eraseColor(Color.BLUE)
    FileOutputStream(srcFile).use { srcBmp.compress(Bitmap.CompressFormat.PNG, 90, it) }

    val edited = editor.applyAdjustments(
      sourceUriOrPath = srcFile.absolutePath,
      params = ImageAdjustmentParams(brightness = 0.2f, contrast = 1.15f),
      rotateDegrees = 90f,
    ).getOrThrow()
    assertTrue(edited.exists() && edited.length() > 0L)

    val mask = editor.renderMaskPngPayload(
      brushPaths = listOf(listOf(NormPoint(0.2f, 0.2f), NormPoint(0.6f, 0.6f))),
      brushRadiusNorm = 0.05f,
      targetWidth = 64,
      targetHeight = 64,
    ).getOrThrow()
    assertTrue(mask.base64Data.isNotBlank())

    val planner = VisionEditPlanner(stubGeminiApi)
    val posterPlan = planner.planEdit("Add Bengali poster quote saying শুভ নববর্ষ", null, AppSettings())
    assertEquals(EditExecutionTarget.LOCAL_POSTER_COMPOSITOR, posterPlan.executionTarget)
  }

  @Test
  fun `ModelSelectionBottomSheet shows 4 flagship modes and keeps single Selected checkmark`() {
    var selectedFlagshipId = ""
    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        ModelSelectionBottomSheet(
          selectedModel = AiModelOption.GEMINI_4_HYDROGEN,
          onSelectModel = {},
          onDismiss = {},
          activeHydrogenBackingId = VerifiedModelCatalog.FLAGSHIP_FLASH_38_ID,
          onSelectFlagshipMode = { id, _ -> selectedFlagshipId = id },
        )
      }
    }

    composeRule.onNodeWithTag("flagship_mode_gemini-4-hydrogen").assertIsDisplayed()
    composeRule.onNodeWithTag("flagship_mode_gemini-3.8-flash").assertIsDisplayed()
    composeRule.onNodeWithTag("flagship_mode_gemini-3.6-flash").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("flagship_mode_pollinations-flux").performScrollTo().performClick()
    assertEquals("pollinations-flux", selectedFlagshipId)
    composeRule.onAllNodesWithContentDescription("Selected").assertCountEquals(1)
  }

  @Test
  fun `ChatMessagesList renders clickable follow-up chips on completed assistant message`() {
    var clickedChip = ""
    val msgs = listOf(
      ChatMessage(
        id = "m_user",
        isUser = true,
        text = "Explain quantum computing",
        timestampLabel = "10:00",
      ),
      ChatMessage(
        id = "m_ai",
        isUser = false,
        text = "Quantum computers use qubits.",
        timestampLabel = "10:01",
        followUpSuggestions = listOf("Give a real-world example", "Explain qubits simply"),
      ),
    )

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        ChatMessagesList(
          messages = msgs,
          responseState = AiResponseState.Idle,
          voiceState = VoiceState.Idle,
          onOpenSettings = {},
          onEditUserMessage = {},
          onRegenerateMessage = {},
          onSpeakMessage = {},
          onStopSpeaking = {},
          onFollowUpSuggestionClick = { clickedChip = it },
        )
      }
    }

    composeRule.onNodeWithTag("follow_up_chip_0").assertIsDisplayed().performClick()
    assertEquals("Give a real-world example", clickedChip)
  }

  @Test
  fun `ImageStudioScreen displays all 8 tool tabs and switches cleanly to Bengali Poster tool`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = createMinimalViewModel(context)

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        val state by vm.uiState.collectAsStateWithLifecycle()
        ImageStudioScreen(
          uiState = state,
          viewModel = vm,
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithText("Creative Image Studio").assertIsDisplayed()
    composeRule.onNodeWithTag("studio_tab_TEXT_TO_IMAGE").assertIsDisplayed()
    composeRule.onNodeWithTag("studio_prompt_input").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("studio_generate_button").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("studio_tab_BATCH_MULTI_PHOTO").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("studio_tab_BENGALI_ENGLISH_POSTER").performScrollTo().performClick()
    composeRule.onNodeWithTag("studio_render_poster_button").performScrollTo().assertIsDisplayed()
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
          emit(
            OrchestrationEvent.Routed(
              RoutingDecision(
                selectedModel = VerifiedModelCatalog.geminiFlash,
                category = TaskCategory.CASUAL_CONVERSATION,
                reason = "test",
                useProxy = false,
              )
            )
          )
          emit(OrchestrationEvent.StateChanged(AiResponseState.Completed("Ready", "Gemini 3.8 Flash")))
        }
        override fun deriveConversationTitle(firstPrompt: String): String = firstPrompt.take(24)
      }

    val stt =
      object : SpeechToTextGateway {
        override fun startListening(languageTag: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) = Unit
        override fun stopListening() = Unit
        override fun release() = Unit
      }
    val tts =
      object : TextToSpeechGateway {
        override fun speak(messageId: String, text: String, preferences: VoicePreferences, onStart: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) = Unit
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
