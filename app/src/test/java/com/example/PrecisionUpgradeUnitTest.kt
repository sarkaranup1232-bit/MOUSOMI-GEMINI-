package com.example

import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.assistant.VoiceSubsystemPhase
import com.example.core.display.DisplayGeometry
import com.example.core.display.DisplayGeometryManager
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.overlay.OrbPositionHelper
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightIntensity
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.ScreenShareStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecisionUpgradeUnitTest {

  @Test
  fun `assistant session state manager debounces duplicate triggers and isolates UI transitions from voice and screen share`() {
    val manager = AssistantSessionStateManager()
    assertEquals(AssistantSessionPhase.INACTIVE, manager.currentPhase.value)

    // First trigger activates edge signal, bubble, and expanded panel
    assertTrue(manager.activateFromTrigger(EdgeActivationEdge.BOTTOM_CORNER, expandPanel = true, nowMs = 1_000L))
    assertEquals(1L, manager.edgeActivation.value.token)
    assertTrue(manager.isSessionActive.value)
    assertTrue(manager.isBubbleVisible.value)
    assertTrue(manager.isPanelExpanded.value)

    // Duplicate trigger within 650ms while already expanded is ignored cleanly
    assertFalse(manager.activateFromTrigger(EdgeActivationEdge.BOTTOM_CORNER, expandPanel = true, nowMs = 1_250L))
    assertEquals(1L, manager.edgeActivation.value.token)

    // Voice & screen share start independently
    manager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
    manager.onScreenSharingChanged(true)
    assertEquals(AssistantSessionPhase.LISTENING, manager.currentPhase.value)

    // Opening and closing keyboard never drops voice, bubble, or screen share
    manager.onKeyboardVisibilityChanged(true)
    assertTrue(manager.isKeyboardVisible.value)
    manager.onKeyboardVisibilityChanged(false)
    assertFalse(manager.isKeyboardVisible.value)
    assertEquals(VoiceSubsystemPhase.LISTENING, manager.voicePhase.value)
    assertTrue(manager.isScreenSharing.value)
    assertTrue(manager.isBubbleVisible.value)

    // Collapsing panel back to bubble preserves active voice & screen share
    manager.onPanelExpandedChanged(false)
    assertFalse(manager.isPanelExpanded.value)
    assertTrue(manager.isBubbleVisible.value)
    assertEquals(VoiceSubsystemPhase.LISTENING, manager.voicePhase.value)
    assertTrue(manager.isScreenSharing.value)

    // Dismissing bubble stops bubble & voice but keeps independent screen share running if active
    manager.onBubbleDismissed(stopAssistantVoice = true)
    assertFalse(manager.isBubbleVisible.value)
    assertEquals(VoiceSubsystemPhase.IDLE, manager.voicePhase.value)
    assertTrue(manager.isScreenSharing.value)
    assertEquals(AssistantSessionPhase.SCREEN_SHARING, manager.currentPhase.value)

    // Ending live assistant completely resets everything cleanly to INACTIVE
    manager.onEndLiveAssistant(keepScreenShare = false)
    assertEquals(AssistantSessionPhase.INACTIVE, manager.currentPhase.value)
    assertFalse(manager.isSessionActive.value)
    assertFalse(manager.isScreenSharing.value)
  }

  @Test
  fun `display geometry manager keeps floating panel safely above IME keyboard and restores bottom layout`() {
    val geometry =
      DisplayGeometry(
        screenWidthPx = 1080,
        screenHeightPx = 2400,
        density = 2.625f,
        statusBarTopPx = 84,
        navBarBottomPx = 126,
        imeBottomPx = 0,
      )

    val normalPlacement = DisplayGeometryManager.computePanelPlacement(geometry)
    assertEquals(geometry.safeBottomWithoutImePx + (16f * geometry.density).toInt(), normalPlacement.bottomMarginPx)

    val imeHeight = 820
    val imeGeometry = geometry.copy(imeBottomPx = imeHeight)
    val imePlacement = DisplayGeometryManager.computePanelPlacement(imeGeometry, windowAlreadyShiftedByIme = false)
    assertTrue(imePlacement.bottomMarginPx > imeHeight)
    assertTrue(imePlacement.maxHeightDp in 190..460)
    assertTrue(imePlacement.maxHeightDp < normalPlacement.maxHeightDp)
  }

  @Test
  fun `display geometry dismiss target hit test and bubble clamping behave accurately`() {
    val geometry =
      DisplayGeometry(
        screenWidthPx = 1080,
        screenHeightPx = 2400,
        density = 2.625f,
        statusBarTopPx = 80,
        navBarBottomPx = 120,
        imeBottomPx = 0,
      )
    val dismiss = DisplayGeometryManager.computeDismissTargetSpec(geometry)
    val orbSizePx = (56f * geometry.density).toInt()

    // Orb centered on dismiss target should register as hit
    val hitX = (dismiss.centerX - orbSizePx / 2f).toInt()
    val hitY = (dismiss.centerY - orbSizePx / 2f).toInt()
    assertTrue(DisplayGeometryManager.isPointInsideDismissTarget(hitX + orbSizePx / 2f, hitY + orbSizePx / 2f, dismiss))
    assertTrue(OrbPositionHelper.isInsideBottomDismissZone(hitX, hitY, orbSizePx, geometry))

    // Orb at top-left should NOT register inside bottom dismiss zone
    assertFalse(OrbPositionHelper.isInsideBottomDismissZone(40, 200, orbSizePx, geometry))

    // Snap left / right works cleanly within screen bounds
    assertTrue(OrbPositionHelper.computeSnappedXPixel(120, orbSizePx, geometry, snapToEdge = true) < 100)
    assertTrue(OrbPositionHelper.computeSnappedXPixel(800, orbSizePx, geometry, snapToEdge = true) > 800)

    val clamped = DisplayGeometryManager.clampBubbleCoordinates(-50, 9999, orbSizePx, geometry)
    assertTrue(clamped.first >= 0)
    assertTrue(clamped.second <= geometry.screenHeightPx - geometry.safeBottomWithoutImePx - orbSizePx)
  }

  @Test
  fun `edge light mapper applies intensity profiles and hides overlay glow when assistant session is inactive`() {
    val baseConfig =
      EdgeLightConfig(
        enabled = true,
        assistantEdgeGlowEnabled = true,
        preset = EdgeLightPreset.GEMINI_FLOW,
        brightness = 0.85f,
        scheduleEnabled = false,
      )

    val subtle =
      EdgeLightStateMapper.resolve(
        config = baseConfig.copy(intensity = EdgeLightIntensity.SUBTLE),
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Listening(),
        voiceRmsNormalized = 0.3f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
        assistantSessionPhase = AssistantSessionPhase.LISTENING,
        isOverlayHost = true,
      )
    val maximum =
      EdgeLightStateMapper.resolve(
        config = baseConfig.copy(intensity = EdgeLightIntensity.MAXIMUM),
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Listening(),
        voiceRmsNormalized = 0.3f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
        assistantSessionPhase = AssistantSessionPhase.LISTENING,
        isOverlayHost = true,
      )
    assertTrue(subtle.isVisible)
    assertTrue(maximum.isVisible)
    assertTrue(maximum.brightness >= subtle.brightness)
    assertTrue(maximum.thicknessDp > subtle.thicknessDp)
    assertTrue(maximum.glowScale > subtle.glowScale)

    // Inactive / paused / stopping overlay host MUST NOT stay stuck glowing over other apps
    listOf(
      AssistantSessionPhase.INACTIVE,
      AssistantSessionPhase.PAUSED,
      AssistantSessionPhase.STOPPING,
    ).forEach { inactivePhase ->
      val overlayIdle =
        EdgeLightStateMapper.resolve(
          config = baseConfig.copy(intensity = EdgeLightIntensity.BRIGHT),
          responseState = AiResponseState.Idle,
          voiceState = VoiceState.Idle,
          voiceRmsNormalized = 0f,
          screenShareStatus = ScreenShareStatus.STOPPED,
          notificationAlertActive = false,
          isSystemPowerSave = false,
          currentHour = 12,
          assistantSessionPhase = inactivePhase,
          isOverlayHost = true,
        )
      assertFalse("Overlay should hide in phase $inactivePhase", overlayIdle.isVisible)
      assertEquals(0f, overlayIdle.brightness, 0.001f)
    }
  }
}
