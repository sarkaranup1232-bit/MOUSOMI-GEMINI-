package com.example

import android.view.Surface
import com.example.core.assistant.AssistantSessionPhase
import com.example.core.display.DisplayGeometry
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.overlay.FloatingAssistantSessionStore
import com.example.core.overlay.FloatingControlBarGeometry
import com.example.core.screen.AttachedVisualItem
import com.example.core.screen.DetectedRegionShape
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.ScreenShareStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingAssistantUnitTest {

  @Test
  fun `FloatingControlBarGeometry clamps coordinates inside portrait and landscape safe cutouts`() {
    val portraitGalaxyM51 =
      DisplayGeometry(
        screenWidthPx = 1080,
        screenHeightPx = 2400,
        density = 2.625f,
        rotation = Surface.ROTATION_0,
        statusBarTopPx = 92,
        navBarBottomPx = 126,
        cutoutTopPx = 92,
      )
    val barW = 756
    val barH = 147

    val (negX, negY) =
      FloatingControlBarGeometry.clampControlBarCoordinates(-200, -100, barW, barH, portraitGalaxyM51)
    assertTrue(negX >= portraitGalaxyM51.safeLeftPx)
    assertTrue(negY >= portraitGalaxyM51.safeTopPx)

    val (maxX, maxY) =
      FloatingControlBarGeometry.clampControlBarCoordinates(3000, 5000, barW, barH, portraitGalaxyM51)
    assertTrue(maxX + barW <= portraitGalaxyM51.screenWidthPx)
    assertTrue(maxY + barH <= portraitGalaxyM51.screenHeightPx - portraitGalaxyM51.safeBottomWithoutImePx)

    val landscapeGeo =
      DisplayGeometry(
        screenWidthPx = 2400,
        screenHeightPx = 1080,
        density = 2.625f,
        rotation = Surface.ROTATION_90,
        statusBarTopPx = 64,
        navBarRightPx = 126,
        cutoutLeftPx = 92,
      )
    val (xf, yf) =
      FloatingControlBarGeometry.fractionFromCoordinates(maxX, maxY, barW, barH, portraitGalaxyM51)
    val (rotX, rotY) =
      FloatingControlBarGeometry.coordinatesFromFraction(xf, yf, barW, barH, landscapeGeo)
    assertTrue(rotX >= landscapeGeo.safeLeftPx)
    assertTrue(rotX + barW <= landscapeGeo.screenWidthPx - landscapeGeo.safeRightPx)
    assertTrue(rotY >= landscapeGeo.safeTopPx)
    assertTrue(rotY + barH <= landscapeGeo.screenHeightPx)
  }

  @Test
  fun `FloatingAssistantSessionStore preserves history across minimize and stages screen crops`() {
    val store = FloatingAssistantSessionStore()
    store.setControlBarVisible(true)
    store.setPanelState(open = true)

    val turnId =
      store.appendUserTurnAndPrepareAssistantPlaceholder(
        userDisplayText = "What is this error?",
        attachmentUris = listOf("file:///tmp/crop1.jpg"),
      )
    store.updateAssistantTurn(turnId, "It is a missing permission check.", modelLabel = "Gemini 2.5 Flash")

    store.minimizePanel()
    assertFalse(store.state.value.isPanelOpen)
    assertTrue(store.state.value.isPanelMinimized)
    assertEquals(2, store.state.value.turns.size)
    assertEquals("It is a missing permission check.", store.state.value.turns.last().text)

    store.attachVisualFromScreenSelection(
      AttachedVisualItem(
        id = "vis_1",
        croppedUriString = "file:///tmp/sel_crop.jpg",
        croppedFilePath = "/tmp/sel_crop.jpg",
        sourceFrameFilePath = "/tmp/full.jpg",
        normCropRect = NormBoundingBox(0.1f, 0.2f, 0.8f, 0.7f),
        detectedShape = DetectedRegionShape.RECTANGLE,
      )
    )
    assertEquals(1, store.state.value.pendingAttachments.size)
    val attached = store.state.value.pendingAttachments.first()
    assertTrue(attached.isFromScreenSelection)

    store.openAttachmentPreview(attached)
    assertNotNull(store.state.value.previewingAttachment)
    store.removePendingAttachment(attached.uriString)
    assertTrue(store.state.value.pendingAttachments.isEmpty())
    assertNull(store.state.value.previewingAttachment)

    store.resetTransientOverlayVisibilityOnly()
    assertFalse(store.state.value.isControlBarVisible)
    assertEquals(2, store.state.value.turns.size)
  }

  @Test
  fun `EdgeLightStateMapper activates during panel or listening and fades off cleanly when paused or stopping`() {
    val cfg =
      EdgeLightConfig(
        enabled = true,
        assistantEdgeGlowEnabled = true,
        brightness = 0.9f,
        borderThicknessDp = 8f,
        animationSpeed = 1.4f,
      )

    val activeParams =
      EdgeLightStateMapper.resolve(
        config = cfg,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0.2f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 14,
        assistantSessionPhase = AssistantSessionPhase.PANEL_EXPANDED,
        isOverlayHost = true,
      )
    assertTrue(activeParams.isVisible)
    assertTrue(activeParams.brightness > 0.5f)

    val powerSaveParams =
      EdgeLightStateMapper.resolve(
        config = cfg,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = true,
        currentHour = 14,
        assistantSessionPhase = AssistantSessionPhase.LISTENING,
        isOverlayHost = true,
      )
    assertTrue(powerSaveParams.isVisible)
    assertTrue(powerSaveParams.brightness < activeParams.brightness)
    assertEquals(1, powerSaveParams.glowLayerCount)

    val stoppingParams =
      EdgeLightStateMapper.resolve(
        config = cfg,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 14,
        assistantSessionPhase = AssistantSessionPhase.STOPPING,
        isOverlayHost = true,
      )
    assertFalse(stoppingParams.isVisible)
    assertEquals(0f, stoppingParams.brightness, 0.001f)
  }
}
