package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ai.firebase.FirebaseAiLogicService
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerateResponse
import com.example.ai.provider.dto.GeminiLongRunningRequest
import com.example.ai.provider.dto.GeminiModelsListResponse
import com.example.ai.provider.dto.GeminiOperationResponse
import com.example.ai.vision.LensIntelligenceService
import com.example.core.image.ImageProcessor
import com.example.core.lens.LensAutoScanTracker
import com.example.core.lens.LensFrameTelemetry
import com.example.core.security.SecurityValidator
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.core.voice.VoiceController
import com.example.domain.auth.AuthUserSession
import com.example.domain.auth.GoogleSignInOutcome
import com.example.domain.auth.OptionalAuthGateway
import com.example.domain.model.AppSettings
import com.example.domain.model.DomainConversation
import com.example.domain.model.DomainMessage
import com.example.domain.model.LensAnalysisResult
import com.example.domain.model.LensAnnotatedComponent
import com.example.domain.model.LensAutoScanState
import com.example.domain.model.LensCalloutColor
import com.example.domain.model.LensDetectedBox
import com.example.domain.model.LensFollowUpTurn
import com.example.domain.model.LensMode
import com.example.domain.model.LensReferenceImage
import com.example.domain.model.SearchResponse
import com.example.domain.model.SearchResultItem
import com.example.domain.model.VoicePreferences
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.SettingsRepository
import com.example.research.search.SearchQueryRequest
import com.example.research.search.SearchService
import com.example.research.search.SearchTopic
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MousomiLensScreenContent
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import com.example.ui.viewmodel.LensUiState
import com.example.ui.viewmodel.MousomiViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MousomiLensAndAuthTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `auto scan tracker rejects dark or shaky frames and completes 10 second single shot countdown`() {
    val tracker = LensAutoScanTracker(countdownDurationMs = 10_000L, preLockStabilizeMs = 700L)
    val steadyBox = listOf(LensDetectedBox(1, "Botanical Plant", 0.25f, 0.25f, 0.75f, 0.75f))

    val darkState = tracker.onFrame(LensFrameTelemetry(steadyBox, avgLuma = 12f, motionDelta = 0.01f, timestampMs = 100L))
    assertTrue(darkState is LensAutoScanState.Unstable)

    val motionState = tracker.onFrame(LensFrameTelemetry(steadyBox, avgLuma = 120f, motionDelta = 0.22f, timestampMs = 200L))
    assertTrue(motionState is LensAutoScanState.Unstable)

    val initialLock = tracker.onFrame(LensFrameTelemetry(steadyBox, avgLuma = 120f, motionDelta = 0.01f, timestampMs = 1_000L))
    assertTrue(initialLock is LensAutoScanState.Scanning)

    val startCountdown = tracker.onFrame(LensFrameTelemetry(steadyBox, avgLuma = 120f, motionDelta = 0.01f, timestampMs = 1_800L))
    assertTrue(startCountdown is LensAutoScanState.CountingDown)
    assertEquals(10, (startCountdown as LensAutoScanState.CountingDown).secondsRemaining)

    val midCountdown = tracker.onFrame(LensFrameTelemetry(steadyBox, avgLuma = 120f, motionDelta = 0.01f, timestampMs = 6_800L))
    assertTrue(midCountdown is LensAutoScanState.CountingDown)
    assertEquals(5, (midCountdown as LensAutoScanState.CountingDown).secondsRemaining)

    val triggered = tracker.onFrame(LensFrameTelemetry(steadyBox, avgLuma = 120f, motionDelta = 0.01f, timestampMs = 11_850L))
    assertTrue(triggered is LensAutoScanState.Triggered)
    assertTrue(tracker.hasCapturedCurrentSession)

    val afterCapture = tracker.onFrame(LensFrameTelemetry(steadyBox, avgLuma = 120f, motionDelta = 0.01f, timestampMs = 25_000L))
    assertTrue(afterCapture is LensAutoScanState.Scanning)
  }

  @Test
  fun `auto scan tracker cancel resets active countdown cleanly`() {
    val tracker = LensAutoScanTracker()
    val box = listOf(LensDetectedBox(7, "Coffee Mug", 0.3f, 0.3f, 0.7f, 0.7f))

    tracker.onFrame(LensFrameTelemetry(box, avgLuma = 110f, motionDelta = 0.01f, timestampMs = 1_000L))
    val counting = tracker.onFrame(LensFrameTelemetry(box, avgLuma = 110f, motionDelta = 0.01f, timestampMs = 1_900L))
    assertTrue(counting is LensAutoScanState.CountingDown)

    val cancelled = tracker.cancelActiveCountdown()
    assertTrue(cancelled is LensAutoScanState.Scanning)
    assertFalse(tracker.hasCapturedCurrentSession)
  }

  @Test
  fun `lens intelligence service parses fenced JSON components, colors, and uncertainty honestly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val json = Json { ignoreUnknownKeys = true }
    val service = createLensIntelligenceService(context, json)

    val samplePayload =
      """
      ```json
      {
        "identifiedName": "Catharanthus roseus (Madagascar Periwinkle)",
        "confidenceLabel": "High",
        "isUncertain": false,
        "uncertaintyReason": "",
        "category": "Flowering Plant",
        "visualCharacteristics": "Five glossy magenta petals with a darker eye ring and opposite oval green leaves.",
        "purposeAndFunctionality": "Ornamental evergreen subshrub valued for bright blooms and medicinal alkaloids.",
        "howItWorks": "Photosynthesis occurs across the chlorophyll-rich ovate leaves while insect pollinators feed at the corolla tube.",
        "practicalApplications": ["Garden border ornamental", "Botanical study specimen"],
        "interestingFacts": ["Produces blooms reliably in warm sunlight"],
        "safetyInformation": "Toxic if ingested by pets or humans due to vinca alkaloids.",
        "relatedDiscoveries": ["Vinca minor", "Apocynaceae family"],
        "referenceSearchQueries": ["Catharanthus roseus", "Madagascar periwinkle flower"],
        "components": [
          {
            "number": 1,
            "title": "Toxic Alkaloid Warning",
            "shortExplanation": "Keep away from pets",
            "detailedExplanation": "All parts of the plant contain toxic alkaloids if chewed.",
            "colorCategory": "RED",
            "normX": 0.50,
            "normY": 0.45,
            "normLeft": 0.30,
            "normTop": 0.25,
            "normRight": 0.70,
            "normBottom": 0.65
          },
          {
            "number": 2,
            "title": "Healthy Glossy Foliage",
            "shortExplanation": "Confirmed green leaf",
            "detailedExplanation": "Glossy midrib-veined leaves indicate adequate hydration.",
            "colorCategory": "GREEN",
            "normX": 0.35,
            "normY": 0.72
          }
        ]
      }
      ```
      """.trimIndent()

    val parsed =
      service.parseLensAnalysisResponse(
        rawText = samplePayload,
        captureId = "cap_1",
        capturedFilePath = "/tmp/periwinkle.jpg",
        mode = LensMode.IDENTIFY,
        modelUsed = "gemini-2.5-flash",
      )

    assertEquals("Catharanthus roseus (Madagascar Periwinkle)", parsed.identifiedName)
    assertFalse(parsed.isUncertain)
    assertEquals(2, parsed.components.size)
    assertEquals(LensCalloutColor.RED, parsed.components[0].colorCategory)
    assertEquals(LensCalloutColor.GREEN, parsed.components[1].colorCategory)
    assertEquals(2, parsed.practicalApplications.size)
    assertTrue(parsed.safetyInformation.contains("Toxic"))
  }

  @Test
  fun `auth screen renders dynamic smoky background while preserving Mousomi AI logo and sign in card`() {
    var signInInvoked = false
    val fakeAuth =
      object : OptionalAuthGateway {
        private val flow = MutableStateFlow(AuthUserSession())
        override fun observeSession() = flow
        override fun getCurrentSession() = flow.value
        override suspend fun attemptSilentSignIn(): Boolean = false
        override suspend fun signInWithGoogle(activity: android.app.Activity): GoogleSignInOutcome {
          signInInvoked = true
          return GoogleSignInOutcome.Error("Test click verified")
        }
        override suspend fun signOut() = Unit
        override fun registerBeforeSignOutHook(hook: () -> Unit) = Unit
      }

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        AuthScreen(authGateway = fakeAuth, onAuthSuccess = {})
      }
    }

    composeRule.onNodeWithTag("gemini_smoky_ambient_background").assertIsDisplayed()
    composeRule.onNodeWithTag("auth_screen_card").assertIsDisplayed()
    composeRule.onNodeWithText("Mousomi AI").assertIsDisplayed()
    composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed().performClick()
    assertTrue(signInInvoked)
  }

  @Test
  fun `mousomi lens live viewfinder displays permission prompt, 5 modes, countdown cancel and switch controls`() {
    var selectedMode = LensMode.SEARCH
    var cancelledCountdown = false

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        MousomiLensScreenContent(
          uiState =
            LensUiState(
              selectedMode = LensMode.SEARCH,
              autoScanState =
                LensAutoScanState.CountingDown(
                  secondsRemaining = 8,
                  progress = 0.2f,
                  targetLabel = "Periwinkle",
                ),
            ),
          hasCameraPermission = false,
          onRequestCameraPermission = {},
          onPickGallery = {},
          onToggleCameraFacing = {},
          onToggleFlash = {},
          onToggleAutoScan = {},
          onCancelCountdown = { cancelledCountdown = true },
          onSelectMode = { selectedMode = it },
          onFrameTelemetry = {},
          createOutputFile = { File("/tmp/cap.jpg") },
          onCapturedFile = {},
          onSelectComponent = {},
          onToggleAnnotations = {},
          onAskFollowUp = {},
          onStartCompareSecondObject = {},
          onCancelCompareCaptureMode = {},
          onDismissComparison = {},
          onContinueInChat = {},
          onScanAnother = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("mousomi_lens_screen").assertIsDisplayed()
    composeRule.onNodeWithTag("lens_grant_camera_button").assertIsDisplayed()
    composeRule.onNodeWithTag("lens_gallery_button").assertIsDisplayed()
    composeRule.onNodeWithTag("lens_scan_button").assertIsDisplayed()
    composeRule.onNodeWithTag("lens_switch_camera_button").assertIsDisplayed()

    LensMode.entries.forEach { mode ->
      composeRule.onNodeWithTag("lens_mode_${mode.name}").assertIsDisplayed()
    }
    composeRule.onNodeWithTag("lens_mode_TRANSLATE").performClick()
    assertEquals(LensMode.TRANSLATE, selectedMode)

    composeRule.onNodeWithTag("lens_cancel_countdown_button").assertIsDisplayed().performClick()
    assertTrue(cancelledCountdown)
  }

  @Test
  fun `mousomi lens response view separates user capture from web reference and supports interactive callouts`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imgFile = File(context.cacheDir, "lens_test_photo.jpg")
    val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
    FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bmp.recycle()

    val result =
      LensAnalysisResult(
        captureId = "cap_99",
        capturedFilePath = imgFile.absolutePath,
        mode = LensMode.IDENTIFY,
        identifiedName = "Raspberry Pi 5 Board",
        confidenceLabel = "High",
        category = "Single-Board Computer",
        visualCharacteristics = "Green PCB with silver SoC heatsink mount and dual micro-HDMI ports.",
        purposeAndFunctionality = "Compact ARM64 desktop and embedded computing board.",
        howItWorks = "Broadcom BCM2712 quad-core ARM Cortex-A76 processor coordinates GPIO and PCIe lanes.",
        practicalApplications = listOf("Robotics controller", "Home edge server"),
        safetyInformation = "Handle by board edges to avoid ESD damage.",
        components =
          listOf(
            LensAnnotatedComponent(
              id = "comp_1",
              number = 1,
              title = "BCM2712 SoC",
              shortExplanation = "Main processor",
              detailedExplanation = "Quad-core 2.4GHz ARM Cortex-A76 system on chip.",
              colorCategory = LensCalloutColor.BLUE,
              normX = 0.45f,
              normY = 0.45f,
            ),
            LensAnnotatedComponent(
              id = "comp_2",
              number = 2,
              title = "Power Input Port",
              shortExplanation = "5V 5A USB-C warning",
              detailedExplanation = "Requires 5V/5A USB-C PD power for full peripheral current.",
              colorCategory = LensCalloutColor.RED,
              normX = 0.25f,
              normY = 0.78f,
            ),
          ),
        referenceImages =
          listOf(
            LensReferenceImage(
              id = "ref_1",
              title = "Raspberry Pi",
              imageUrl = "https://upload.wikimedia.org/wikipedia/commons/sample.jpg",
              sourceWebsite = "Wikipedia (en.wikipedia.org)",
              sourcePageUrl = "https://en.wikipedia.org/wiki/Raspberry_Pi",
              caption = "Web Reference • Raspberry Pi",
            )
          ),
        modelUsed = "gemini-2.5-flash",
      )

    var selectedId: String? = "comp_1"
    var continuedResult: LensAnalysisResult? = null

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        MousomiLensScreenContent(
          uiState =
            LensUiState(
              capturedImagePath = imgFile.absolutePath,
              analysisResult = result,
              selectedComponentId = selectedId,
            ),
          hasCameraPermission = true,
          onRequestCameraPermission = {},
          onPickGallery = {},
          onToggleCameraFacing = {},
          onToggleFlash = {},
          onToggleAutoScan = {},
          onCancelCountdown = {},
          onSelectMode = {},
          onFrameTelemetry = {},
          createOutputFile = { imgFile },
          onCapturedFile = {},
          onSelectComponent = { selectedId = it },
          onToggleAnnotations = {},
          onAskFollowUp = {},
          onStartCompareSecondObject = {},
          onCancelCompareCaptureMode = {},
          onDismissComparison = {},
          onContinueInChat = { continuedResult = it },
          onScanAnother = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("lens_captured_image_card").assertIsDisplayed()
    composeRule.onNodeWithText("Your Captured Image").assertIsDisplayed()
    composeRule.onNodeWithTag("lens_callout_pin_2").performClick()
    assertEquals("comp_2", selectedId)

    composeRule.onNodeWithTag("lens_identified_name").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("lens_continue_in_chat_button").assertIsDisplayed().performClick()
    assertEquals("cap_99", continuedResult?.captureId)

    composeRule.onNodeWithText("Web Reference Images (External Sources)").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("lens_web_ref_card").assertIsDisplayed()
    composeRule.onNodeWithText("Web Reference").assertIsDisplayed()
  }

  @Test
  fun `mousomi viewmodel imports lens analysis into chat and routes natural open lens commands`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val chatRepo = InMemoryChatRepository()
      val vm = createSimpleViewModel(context, chatRepo)

      vm.onInputChanged("Open Mousomi Lens")
      vm.onSendMessage()
      assertTrue(vm.uiState.value.pendingNavigateToLens)
      vm.onConsumeNavigateToLens()
      assertFalse(vm.uiState.value.pendingNavigateToLens)

      val imgFile = File(context.cacheDir, "lens_vm_import.jpg")
      FileOutputStream(imgFile).use { it.write(byteArrayOf(1, 2, 3, 4)) }

      val analysis =
        LensAnalysisResult(
          captureId = "cap_vm",
          capturedFilePath = imgFile.absolutePath,
          mode = LensMode.IDENTIFY,
          identifiedName = "Monstera deliciosa",
          confidenceLabel = "High",
          purposeAndFunctionality = "Tropical climbing plant with fenestrated leaves.",
        )
      val followUps = listOf(LensFollowUpTurn("f1", "Does it need direct sun?", "Bright indirect light is ideal."))

      vm.onImportLensAnalysisToChat(analysis, followUps)
      kotlinx.coroutines.delay(150L)

      val msgs = chatRepo.getMessagesSnapshot(vm.uiState.value.activeConversationId)
      assertEquals(2, msgs.size)
      assertTrue(msgs[0].isUser)
      assertEquals(1, msgs[0].imageUris.size)
      assertFalse(msgs[1].isUser)
      assertTrue(msgs[1].text.contains("Monstera deliciosa"))
      assertTrue(msgs[1].text.contains("Bright indirect light is ideal."))
    }

  private fun createLensIntelligenceService(context: Context, json: Json): LensIntelligenceService {
    val fakeGeminiApi =
      object : GeminiApiService {
        override suspend fun listModels(apiKey: String, pageSize: Int): Response<GeminiModelsListResponse> =
          Response.success(GeminiModelsListResponse())
        override suspend fun streamGenerateContent(
          model: String,
          alt: String,
          apiKey: String,
          request: GeminiGenerateRequest,
        ): Response<okhttp3.ResponseBody> = Response.error(400, "".toResponseBody(null))
        override suspend fun generateContent(
          model: String,
          apiKey: String,
          request: GeminiGenerateRequest,
        ): Response<GeminiGenerateResponse> = Response.success(GeminiGenerateResponse())
        override suspend fun predictLongRunning(
          model: String,
          apiKey: String,
          request: GeminiLongRunningRequest,
        ): Response<GeminiOperationResponse> = Response.error(400, "".toResponseBody(null))
        override suspend fun getOperation(
          operationName: String,
          apiKey: String,
        ): Response<GeminiOperationResponse> = Response.error(400, "".toResponseBody(null))
      }
    val fakeSearch =
      object : SearchService {
        override suspend fun search(
          request: SearchQueryRequest,
          settings: AppSettings,
        ): Result<SearchResponse> =
          Result.success(
            SearchResponse(
              query = request.query,
              results = emptyList(),
              page = 1,
              hasMorePages = false,
              providerUsed = "test",
              retrievedAtMs = 0L,
            )
          )
        override suspend fun searchMultipleQueries(
          queries: List<String>,
          topic: SearchTopic,
          settings: AppSettings,
        ): Result<List<SearchResultItem>> = Result.success(emptyList())
        override fun clearCache() = Unit
      }
    return LensIntelligenceService(
      firebaseAiLogicService = FirebaseAiLogicService(context, SecurityValidator()),
      geminiApiService = fakeGeminiApi,
      searchService = fakeSearch,
      imageProcessor = ImageProcessor(context),
      okHttpClient = OkHttpClient(),
      securityValidator = SecurityValidator(),
      json = json,
    )
  }

  private fun createSimpleViewModel(context: Context, chatRepo: ChatRepository): MousomiViewModel {
    val fakeOrchestrator =
      object : AiOrchestrator {
        override fun processMessage(request: OrchestrationRequest): Flow<OrchestrationEvent> = flow {}
        override fun deriveConversationTitle(firstPrompt: String): String = firstPrompt.take(24)
      }
    val fakeSettings =
      object : SettingsRepository {
        private val flow = MutableStateFlow(AppSettings())
        override fun observeSettings() = flow
        override suspend fun getSettingsSnapshot() = flow.value
        override suspend fun saveSettings(settings: AppSettings) {
          flow.value = settings
        }
      }
    val fakeVoice =
      VoiceController(
        sttGateway =
          object : SpeechToTextGateway {
            override fun startListening(
              languageTag: String,
              onPartial: (String) -> Unit,
              onFinal: (String) -> Unit,
              onError: (String) -> Unit,
            ) = Unit
            override fun stopListening() = Unit
            override fun release() = Unit
          },
        ttsGateway =
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
          },
      )
    return MousomiViewModel(
      aiOrchestrator = fakeOrchestrator,
      chatRepository = chatRepo,
      settingsRepository = fakeSettings,
      imageProcessor = ImageProcessor(context),
      voiceController = fakeVoice,
      securityValidator = SecurityValidator(),
    )
  }

  private class InMemoryChatRepository : ChatRepository {
    private val convs = MutableStateFlow<List<DomainConversation>>(emptyList())
    private val msgs = MutableStateFlow<List<DomainMessage>>(emptyList())

    override fun observeConversations() = convs
    override fun observeMessages(conversationId: String) =
      msgs.map { list -> list.filter { it.conversationId == conversationId }.sortedBy { it.timestampMs } }
    override suspend fun getMessagesSnapshot(conversationId: String) =
      msgs.value.filter { it.conversationId == conversationId }.sortedBy { it.timestampMs }
    override suspend fun ensureConversation(conversationId: String, initialTitle: String): DomainConversation {
      val now = System.currentTimeMillis()
      val existing = convs.value.firstOrNull { it.id == conversationId }
      if (existing != null) return existing
      val created = DomainConversation(conversationId, initialTitle, now, now)
      convs.value = listOf(created) + convs.value
      return created
    }
    override suspend fun updateConversationTitle(conversationId: String, title: String) = Unit
    override suspend fun upsertMessage(message: DomainMessage) {
      msgs.value = msgs.value.filterNot { it.id == message.id } + message
    }
    override suspend fun deleteConversation(conversationId: String) = Unit
    override suspend fun deleteMessage(messageId: String) {
      msgs.value = msgs.value.filterNot { it.id == messageId }
    }
    override suspend fun deleteMessagesFrom(conversationId: String, fromTimestampMs: Long) = Unit
  }
}
