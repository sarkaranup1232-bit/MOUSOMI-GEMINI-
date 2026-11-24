package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.assistant.AssistantVisualState
import com.example.core.device.AgentCommandIntent
import com.example.core.device.AssistantAgentCommandRouter
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.image.ImageProcessor
import com.example.core.screen.CircleSelectionProcessor
import com.example.core.screen.CropCoordinateMapper
import com.example.core.screen.DetectedRegionShape
import com.example.core.screen.FreehandPathProcessor
import com.example.core.screen.ImageAttachmentManager
import com.example.core.screen.RectangleSelectionProcessor
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenRecordingPhase
import com.example.core.screen.ScreenRecordingUiState
import com.example.core.screen.ScreenSelectionUiState
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionGeometryResult
import com.example.core.screen.SelectionStage
import com.example.core.screen.SelectionToolMode
import com.example.core.screen.SmartCropEngine
import com.example.core.screen.VisualQueryProcessor
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.OrbReactiveFrame
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.ScreenShareStatus
import com.example.ui.components.AssistantSessionOverlayUi
import com.example.ui.components.FloatingLivePopupCard
import com.example.ui.components.ScreenSelectionOverlay
import com.example.ui.screens.GeminiLiveOverlay
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MasterFixUpgradeTest {

  @get:Rule val composeRule = createComposeRule()

  private val geometryEngine =
    SelectionGeometryEngine(
      freehandProcessor = FreehandPathProcessor(),
      circleProcessor = CircleSelectionProcessor(),
      rectangleProcessor = RectangleSelectionProcessor(),
    )

  private val sampleEdgeParams =
    EdgeLightStateMapper.resolve(
      config = EdgeLightConfig(enabled = true, assistantEdgeGlowEnabled = true, preset = EdgeLightPreset.GEMINI_FLOW),
      responseState = AiResponseState.Idle,
      voiceState = VoiceState.Idle,
      voiceRmsNormalized = 0f,
      screenShareStatus = ScreenShareStatus.STOPPED,
      notificationAlertActive = false,
      isSystemPowerSave = false,
      currentHour = 14,
    )

  @Test
  fun `assistant overlay suppresses background dismiss scrim when drawing mode or confirmed box is active`() {
    var drawToggleClicks = 0
    var closeCalls = 0
    var confirmedClicks = 0

    val snapped =
      SelectionGeometryResult(
        boundingBox = NormBoundingBox(0.2f, 0.25f, 0.75f, 0.65f),
        detectedShape = DetectedRegionShape.RECTANGLE,
        confidence = 0.94f,
      )

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        AssistantSessionOverlayUi(
          visualState = AssistantVisualState.STANDARD_ASSISTANT,
          edgeLightParams = sampleEdgeParams,
          reactiveFrame = OrbReactiveFrame(),
          inputText = "",
          onInputChanged = {},
          onSendQuery = {},
          onMicClick = {},
          onLiveButtonClick = {},
          onDismissLiveToStandard = {},
          onShareScreenWithLive = {},
          onCloseSession = { closeCalls += 1 },
          isDrawingMode = true,
          snappedDrawingResult = snapped,
          selectionGeometryEngine = geometryEngine,
          onToggleDrawingMode = { drawToggleClicks += 1 },
          onConfirmDrawingSelection = { confirmedClicks += 1 },
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("assistant_outside_dismiss_scrim").assertDoesNotExist()
    composeRule.onNodeWithTag("assistant_drawing_toolbar").assertIsDisplayed()
    composeRule.onNodeWithTag("assistant_draw_select_pill").assertIsDisplayed().performClick()
    assertEquals(1, drawToggleClicks)

    composeRule.onNodeWithTag("assistant_drawing_confirm_button").assertIsDisplayed().performClick()
    assertEquals(1, confirmedClicks)
    assertEquals(0, closeCalls)
  }

  @Test
  fun `assistant overlay shows persistent confirmed selection chip and enables outside scrim only when idle`() {
    var redrawClicks = 0
    var cancelClicks = 0
    var closeCalls = 0

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        AssistantSessionOverlayUi(
          visualState = AssistantVisualState.STANDARD_ASSISTANT,
          edgeLightParams = sampleEdgeParams,
          reactiveFrame = OrbReactiveFrame(),
          inputText = "",
          onInputChanged = {},
          onSendQuery = {},
          onMicClick = {},
          onLiveButtonClick = {},
          onDismissLiveToStandard = {},
          onShareScreenWithLive = {},
          onCloseSession = { closeCalls += 1 },
          isDrawingMode = false,
          confirmedSelectionBox = NormBoundingBox(0.15f, 0.2f, 0.8f, 0.6f),
          onRedrawDrawingSelection = { redrawClicks += 1 },
          onCancelDrawingSelection = { cancelClicks += 1 },
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("assistant_outside_dismiss_scrim").assertDoesNotExist()
    composeRule.onNodeWithTag("assistant_confirmed_selection_chip").assertIsDisplayed()
    composeRule.onNodeWithText("Redraw").performClick()
    composeRule.onNodeWithText("Cancel").performClick()
    assertEquals(1, redrawClicks)
    assertEquals(1, cancelClicks)
    assertEquals(0, closeCalls)
  }

  @Test
  fun `screen selection overlay remains visible indefinitely without countdown timer badge`() {
    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        ScreenSelectionOverlay(
          state =
            ScreenSelectionUiState(
              isActive = true,
              stage = SelectionStage.READY_TO_DRAW,
              toolMode = SelectionToolMode.RECTANGLE,
            ),
          geometryEngine = geometryEngine,
          onSelectTool = {},
          onStrokeStart = {},
          onStrokeMove = {},
          onStrokeEnd = {},
          onHandleDrag = { _, _, _ -> },
          onUndo = {},
          onClear = {},
          onCancel = {},
          onConfirmSelection = { _, _ -> },
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("screen_selection_overlay").assertIsDisplayed()
    composeRule.onNodeWithTag("selection_timer_badge").assertDoesNotExist()
  }

  @Test
  fun `floating live popup displays waveform, honest screen share active status, controls, and recording banner`() {
    var shareClicks = 0
    var drawClicks = 0
    var muteClicks = 0
    var pauseClicks = 0
    var stopClicks = 0
    var closeClicks = 0

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        FloatingLivePopupCard(
          liveStatusLabel = "Voice Active",
          isMicActive = true,
          hasMicPermission = true,
          isPaused = false,
          isMuted = false,
          isThinking = false,
          isScreenShareActive = true,
          voiceRmsNormalized = 0.65f,
          elapsedSeconds = 42L,
          screenRecordingState =
            ScreenRecordingUiState(
              phase = ScreenRecordingPhase.RECORDING,
              elapsedSeconds = 12L,
              displayPath = "Movies/Mousomi AI/Mousomi_1.mp4",
            ),
          onToggleMute = { muteClicks += 1 },
          onTogglePause = { pauseClicks += 1 },
          onToggleScreenShare = { shareClicks += 1 },
          onDrawArea = { drawClicks += 1 },
          onSendFollowUp = {},
          onStopLive = { stopClicks += 1 },
          onClosePopup = { closeClicks += 1 },
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("floating_live_popup_card").assertIsDisplayed()
    composeRule.onNodeWithTag("floating_live_waveform").assertIsDisplayed()
    composeRule.onNodeWithText("Screen Sharing Active").assertIsDisplayed()
    composeRule.onNodeWithTag("floating_live_stop_share_button").assertIsDisplayed()
    composeRule.onNodeWithTag("floating_live_recording_banner").assertIsDisplayed()

    composeRule.onNodeWithTag("floating_live_screen_share_button").performClick()
    composeRule.onNodeWithTag("floating_live_draw_button").performClick()
    composeRule.onNodeWithTag("floating_live_mute_button").performClick()
    composeRule.onNodeWithTag("floating_live_pause_button").performClick()
    composeRule.onNodeWithTag("floating_live_stop_button").performClick()
    composeRule.onNodeWithTag("floating_live_close_button").performClick()

    assertEquals(1, shareClicks)
    assertEquals(1, drawClicks)
    assertEquals(1, muteClicks)
    assertEquals(1, pauseClicks)
    assertEquals(1, stopClicks)
    assertEquals(1, closeClicks)
  }

  @Test
  fun `gemini live overlay offers stop screen share label and float over apps pill when overlay permission missing`() {
    var floatClicks = 0
    var shareClicks = 0

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        GeminiLiveOverlay(
          voiceState = VoiceState.Idle,
          isScreenShareActive = true,
          onStartListening = {},
          onStopListening = {},
          onClose = {},
          onShareScreenWithLive = { shareClicks += 1 },
          onRequestOverlayPermission = { floatClicks += 1 },
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("mousomi_live_overlay").assertIsDisplayed()
    composeRule.onNodeWithTag("live_share_screen_pill").assertIsDisplayed()
    composeRule.onNodeWithText("Stop Screen Share").assertIsDisplayed()
    composeRule.onNodeWithTag("live_float_over_apps_pill").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("live_share_screen_pill").performClick()

    assertEquals(1, floatClicks)
    assertEquals(1, shareClicks)
  }

  @Test
  fun `screen recording manager enforces explicit consent-save lifecycle and never reports unverified path`() {
    val manager = ScreenRecordingManager()
    assertEquals(ScreenRecordingPhase.IDLE, manager.state.value.phase)

    manager.requestConfirmation()
    assertEquals(ScreenRecordingPhase.AWAITING_CONFIRMATION, manager.state.value.phase)
    assertFalse(manager.state.value.isActive)

    manager.markAwaitingConsent()
    assertEquals(ScreenRecordingPhase.AWAITING_CONSENT, manager.state.value.phase)

    manager.markRecordingStarted("Movies/Mousomi AI/Mousomi_Screen_01.mp4")
    assertEquals(ScreenRecordingPhase.RECORDING, manager.state.value.phase)
    assertTrue(manager.state.value.isActive)

    manager.updateElapsedSeconds(65L)
    assertEquals("01:05", ScreenRecordingManager.formatElapsed(manager.state.value.elapsedSeconds))

    manager.markFinalizing()
    assertEquals(ScreenRecordingPhase.FINALIZING, manager.state.value.phase)
    assertTrue(manager.state.value.isActive)

    manager.markSaved("Movies/Mousomi AI/Mousomi_Screen_01.mp4", "content://media/external/video/media/99")
    assertEquals(ScreenRecordingPhase.SAVED, manager.state.value.phase)
    assertEquals("content://media/external/video/media/99", manager.state.value.savedUri)
    assertFalse(manager.state.value.isActive)

    manager.markCancelled("Cancelled by user")
    assertEquals(ScreenRecordingPhase.CANCELLED, manager.state.value.phase)
    assertEquals("", manager.state.value.displayPath)
    assertNull(manager.state.value.savedUri)
  }

  @Test
  fun `assistant agent command router classifies recording, screen share, selection, screen analysis, and OS toggle limits`() {
    val router = AssistantAgentCommandRouter()

    assertTrue(router.classify("start screen recording") is AgentCommandIntent.StartScreenRecording)
    assertEquals(AgentCommandIntent.StopScreenRecording, router.classify("stop screen recording"))
    assertEquals(AgentCommandIntent.CancelScreenRecording, router.classify("cancel recording"))
    assertEquals(AgentCommandIntent.StartScreenShare, router.classify("share my screen"))
    assertEquals(AgentCommandIntent.StopScreenShare, router.classify("stop screen sharing"))
    assertEquals(AgentCommandIntent.StartScreenSelection, router.classify("draw on screen"))
    assertTrue(router.classify("what is on my screen?") is AgentCommandIntent.AnalyzeCurrentScreen)
    assertTrue(router.classify("turn off wifi") is AgentCommandIntent.UnsupportedSilentToggle)
    assertTrue(router.classify("open YouTube") is AgentCommandIntent.OpenTargetOrSetting)
    assertNull(router.classify("summarize quantum tunneling in two sentences"))
  }

  @Test
  fun `visual query processor never attaches screen payload when screen share is stopped, and crops when active`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val cache = TempMediaCacheManager(context)
      val shareSession = ScreenShareSessionManager(cache)
      val processor =
        VisualQueryProcessor(
          imageProcessor = ImageProcessor(context),
          imageAttachmentManager = ImageAttachmentManager(cache),
        )
      val cropEngine = SmartCropEngine(CropCoordinateMapper(), cache)

      // 1. STOPPED -> null payload even if someone calls helper
      assertNull(
        processor.prepareScreenSharePayload(
          screenShareSessionManager = shareSession,
          smartCropEngine = cropEngine,
        )
      )

      // 2. REQUESTING_CONSENT -> null payload
      shareSession.markRequestingConsent()
      assertNull(
        processor.prepareScreenSharePayload(
          screenShareSessionManager = shareSession,
          smartCropEngine = cropEngine,
        )
      )

      // 3. ACTIVE with real captured frame -> valid payload + optional region crop
      shareSession.markSessionActive()
      val bmp = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
      val ingested = shareSession.ingestCapturedBitmap(bmp)
      assertNotNull(ingested)

      val fullPayload =
        processor.prepareScreenSharePayload(
          screenShareSessionManager = shareSession,
          smartCropEngine = cropEngine,
        )
      assertNotNull(fullPayload)
      assertEquals(200, fullPayload!!.width)
      assertEquals(100, fullPayload.height)

      val croppedPayload =
        processor.prepareScreenSharePayload(
          screenShareSessionManager = shareSession,
          smartCropEngine = cropEngine,
          cropBox = NormBoundingBox(0.25f, 0.25f, 0.75f, 0.75f),
        )
      assertNotNull(croppedPayload)
      assertEquals(100, croppedPayload!!.width)
      assertEquals(50, croppedPayload.height)

      shareSession.stopSession()
    }
}
