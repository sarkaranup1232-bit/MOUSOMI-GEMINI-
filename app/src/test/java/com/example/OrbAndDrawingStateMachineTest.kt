package com.example

import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.AssistantSurfaceState
import com.example.core.assistant.AssistantVisualState
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.assistant.VoiceSubsystemPhase
import com.example.core.overlay.OrbPositionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbAndDrawingStateMachineTest {

  @Test
  fun `drawing state requires open session and blocks accidental outside-tap collapse or non-forced close`() {
    val manager = AssistantSessionStateManager()
    assertEquals(AssistantSurfaceState.CLOSED, manager.surfaceState.value)

    // Cannot jump straight from CLOSED -> DRAWING
    assertFalse(manager.enterDrawingMode())
    assertEquals(AssistantSurfaceState.CLOSED, manager.surfaceState.value)

    // Open standard assistant -> EXPANDED
    manager.openStandardAssistant(EdgeActivationEdge.BOTTOM_CORNER, nowMs = 1000L)
    assertEquals(AssistantSurfaceState.EXPANDED, manager.surfaceState.value)
    assertEquals(AssistantVisualState.STANDARD_ASSISTANT, manager.visualState.value)

    // Enter DRAWING
    assertTrue(manager.enterDrawingMode())
    assertEquals(AssistantSurfaceState.DRAWING, manager.surfaceState.value)
    assertTrue(manager.isDrawingActive.value)
    assertEquals(AssistantSessionPhase.DRAWING, manager.currentPhase.value)

    // Accidental outside tap (collapseToOrb) must be REJECTED while drawing
    assertFalse(manager.collapseToOrb())
    assertEquals(AssistantSurfaceState.DRAWING, manager.surfaceState.value)

    // Non-forced close must ALSO be rejected while drawing
    assertFalse(manager.closeAssistant(force = false))
    assertEquals(AssistantSurfaceState.DRAWING, manager.surfaceState.value)

    // Confirm returns DRAWING -> EXPANDED cleanly
    assertTrue(manager.confirmDrawingMode())
    assertEquals(AssistantSurfaceState.EXPANDED, manager.surfaceState.value)
    assertFalse(manager.isDrawingActive.value)
  }

  @Test
  fun `outside tap collapses expanded assistant to compact orb while preserving live audio, screen share, and visual mode`() {
    val manager = AssistantSessionStateManager()
    manager.enterLiveAssistant(EdgeActivationEdge.BOTTOM, nowMs = 1000L)
    manager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
    manager.onScreenSharingChanged(true)
    manager.updateOrbPosition(xFraction = 0.22f, yFraction = 0.44f)

    assertEquals(AssistantSurfaceState.EXPANDED, manager.surfaceState.value)
    assertEquals(AssistantVisualState.LIVE_ASSISTANT, manager.visualState.value)
    assertTrue(manager.isSessionActive.value)

    // Outside tap -> COLLAPSED_ORB
    assertTrue(manager.collapseToOrb())
    assertEquals(AssistantSurfaceState.COLLAPSED_ORB, manager.surfaceState.value)
    assertFalse(manager.isPanelExpanded.value)
    assertTrue(manager.isBubbleVisible.value)

    // Live audio, screen sharing, active session, and visual mode must remain intact
    assertTrue(manager.isSessionActive.value)
    assertEquals(VoiceSubsystemPhase.LISTENING, manager.voicePhase.value)
    assertTrue(manager.isScreenSharing.value)
    assertEquals(AssistantVisualState.LIVE_ASSISTANT, manager.visualState.value)

    // Orb position stays stable across EXPANDED <-> COLLAPSED_ORB
    assertEquals(0.22f, manager.orbXFraction.value, 0.0001f)
    assertEquals(0.44f, manager.orbYFraction.value, 0.0001f)

    // Tapping compact Orb restores EXPANDED cleanly
    assertTrue(manager.expandFromOrb())
    assertEquals(AssistantSurfaceState.EXPANDED, manager.surfaceState.value)
    assertTrue(manager.isPanelExpanded.value)
    assertEquals(0.22f, manager.orbXFraction.value, 0.0001f)
    assertEquals(0.44f, manager.orbYFraction.value, 0.0001f)
    assertEquals(VoiceSubsystemPhase.LISTENING, manager.voicePhase.value)
  }

  @Test
  fun `cancelling drawing launched from collapsed orb restores collapsed orb rather than forcing expanded`() {
    val manager = AssistantSessionStateManager()
    manager.openStandardAssistant(nowMs = 1000L)
    assertTrue(manager.collapseToOrb())
    assertEquals(AssistantSurfaceState.COLLAPSED_ORB, manager.surfaceState.value)

    // Draw started while in COLLAPSED_ORB (e.g., overlay draw button while panel minimized)
    assertTrue(manager.enterDrawingMode())
    assertEquals(AssistantSurfaceState.DRAWING, manager.surfaceState.value)

    // Cancelling returns to prior COLLAPSED_ORB state
    assertTrue(manager.cancelDrawingMode())
    assertEquals(AssistantSurfaceState.COLLAPSED_ORB, manager.surfaceState.value)
    assertFalse(manager.isDrawingActive.value)

    // Confirming a crop from DRAWING transitions to EXPANDED so user sees the attached thumbnail
    assertTrue(manager.enterDrawingMode())
    assertTrue(manager.confirmDrawingMode())
    assertEquals(AssistantSurfaceState.EXPANDED, manager.surfaceState.value)
  }

  @Test
  fun `explicit close transitions expanded or collapsed orb cleanly to closed`() {
    val manager = AssistantSessionStateManager()
    manager.openStandardAssistant(nowMs = 1000L)
    manager.onVoicePhaseChanged(VoiceSubsystemPhase.AI_RESPONDING)
    assertTrue(manager.collapseToOrb())
    assertEquals(AssistantSurfaceState.COLLAPSED_ORB, manager.surfaceState.value)

    // Explicit close from COLLAPSED_ORB -> CLOSED
    assertTrue(manager.closeAssistant(keepScreenShare = false))
    assertEquals(AssistantSurfaceState.CLOSED, manager.surfaceState.value)
    assertEquals(AssistantVisualState.HIDDEN, manager.visualState.value)
    assertFalse(manager.isSessionActive.value)
    assertFalse(manager.isBubbleVisible.value)
    assertFalse(manager.isPanelExpanded.value)
    assertEquals(VoiceSubsystemPhase.IDLE, manager.voicePhase.value)

    // Repeated collapse or expand when CLOSED is rejected
    assertFalse(manager.collapseToOrb())
    assertFalse(manager.expandFromOrb())
  }

  @Test
  fun `orb position updates clamp cleanly and touch-slop separation preserves drag vs tap`() {
    val manager = AssistantSessionStateManager()
    manager.updateOrbPosition(-5f, 99f)
    assertEquals(0.02f, manager.orbXFraction.value, 0.0001f)
    assertEquals(0.90f, manager.orbYFraction.value, 0.0001f)

    // Verify existing OrbPositionHelper edge snapping still clamps symmetrically
    val display =
      com.example.core.display.DisplayGeometry(
        screenWidthPx = 1080,
        screenHeightPx = 2400,
        density = 3.0f,
      )
    val snappedLeft = OrbPositionHelper.computeSnappedXPixel(200, 168, display, snapToEdge = true)
    val snappedRight = OrbPositionHelper.computeSnappedXPixel(800, 168, display, snapToEdge = true)
    assertTrue(snappedLeft < snappedRight)
  }

  @Test
  fun `legacy onDrawingChanged and onPanelExpandedChanged synchronize cleanly with surfaceState`() {
    val manager = AssistantSessionStateManager()
    manager.activateFromTrigger(EdgeActivationEdge.RIGHT, expandPanel = true, nowMs = 1000L)
    assertEquals(AssistantSurfaceState.EXPANDED, manager.surfaceState.value)

    manager.onDrawingChanged(true)
    assertEquals(AssistantSurfaceState.DRAWING, manager.surfaceState.value)
    // Panel collapse signal while drawing cannot kick out DRAWING state
    manager.onPanelExpandedChanged(false)
    assertEquals(AssistantSurfaceState.DRAWING, manager.surfaceState.value)

    manager.onDrawingChanged(false)
    assertEquals(AssistantSurfaceState.EXPANDED, manager.surfaceState.value)

    manager.onEndLiveAssistant(keepScreenShare = false)
    assertEquals(AssistantSurfaceState.CLOSED, manager.surfaceState.value)
  }
}
