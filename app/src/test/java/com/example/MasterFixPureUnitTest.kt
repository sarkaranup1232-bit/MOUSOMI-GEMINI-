package com.example

import com.example.core.assistant.AssistantSessionPhase
import com.example.core.device.AgentCommandIntent
import com.example.core.device.AssistantAgentCommandRouter
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.screen.CircleSelectionProcessor
import com.example.core.screen.CropHandle
import com.example.core.screen.DetectedRegionShape
import com.example.core.screen.FreehandPathProcessor
import com.example.core.screen.RectangleSelectionProcessor
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenRecordingPhase
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionToolMode
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.NormPoint
import com.example.domain.model.ScreenShareStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterFixPureUnitTest {

  private val geometryEngine =
    SelectionGeometryEngine(
      freehandProcessor = FreehandPathProcessor(),
      circleProcessor = CircleSelectionProcessor(),
      rectangleProcessor = RectangleSelectionProcessor(),
    )

  @Test
  fun `selection geometry engine snaps rectangle and freehand strokes cleanly and adjusts handles`() {
    val rectStroke =
      listOf(
        NormPoint(0.20f, 0.25f),
        NormPoint(0.45f, 0.45f),
        NormPoint(0.75f, 0.65f),
      )
    val rectSnapped = geometryEngine.processStroke(rectStroke, SelectionToolMode.RECTANGLE)
    assertNotNull(rectSnapped)
    assertEquals(DetectedRegionShape.RECTANGLE, rectSnapped!!.detectedShape)
    assertTrue(rectSnapped.boundingBox.left <= 0.21f)
    assertTrue(rectSnapped.boundingBox.right >= 0.74f)

    val adjusted =
      geometryEngine.adjustSnappedResult(
        current = rectSnapped,
        handle = CropHandle.BOTTOM_RIGHT,
        deltaXNorm = 0.05f,
        deltaYNorm = 0.05f,
      )
    assertTrue(adjusted.boundingBox.right > rectSnapped.boundingBox.right)
    assertTrue(adjusted.boundingBox.bottom > rectSnapped.boundingBox.bottom)

    val freehandLoop =
      listOf(
        NormPoint(0.30f, 0.30f),
        NormPoint(0.55f, 0.25f),
        NormPoint(0.65f, 0.45f),
        NormPoint(0.50f, 0.65f),
        NormPoint(0.28f, 0.55f),
        NormPoint(0.30f, 0.31f),
      )
    val freehandSnapped = geometryEngine.processStroke(freehandLoop, SelectionToolMode.SMART_LOOP)
    assertNotNull(freehandSnapped)
    assertTrue(freehandSnapped!!.boundingBox.isValid())
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
  fun `edge light state mapper keeps neon spectrum visible during live and screen share sessions`() {
    val activeShareParams =
      EdgeLightStateMapper.resolve(
        config = EdgeLightConfig(enabled = true, assistantEdgeGlowEnabled = true, preset = EdgeLightPreset.GEMINI_FLOW),
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Listening(),
        voiceRmsNormalized = 0.55f,
        screenShareStatus = ScreenShareStatus.ACTIVE,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 14,
        assistantSessionPhase = AssistantSessionPhase.LISTENING,
        isOverlayHost = true,
      )
    assertTrue(activeShareParams.isVisible)
    assertEquals(EdgeLightVisualState.LISTENING, activeShareParams.visualState)
    assertTrue(activeShareParams.colorArgs.contains(0xFF22D3EEL))
    assertTrue(activeShareParams.colorArgs.contains(0xFF3B82F6L))
    assertTrue(activeShareParams.colorArgs.contains(0xFF8B5CF6L))
    assertTrue(activeShareParams.colorArgs.contains(0xFFEC4899L))
    assertTrue(activeShareParams.colorArgs.contains(0xFFFDE68AL))
  }
}
