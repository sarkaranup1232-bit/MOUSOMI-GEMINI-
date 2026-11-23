package com.example

import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.AssistantVisualState
import com.example.core.assistant.VoiceSubsystemPhase
import com.example.core.device.PendingPhoneActionType
import com.example.core.device.PhoneActionController
import com.example.core.device.SocialIntegrationManager
import com.example.core.device.SocialPlatform
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.screen.DrawingController
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.DrawingTool
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.NormPoint
import com.example.domain.model.ScreenShareStatus
import com.example.ui.model.AiModelOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemAssistantUpgradeUnitTest {

  @Test
  fun `assistant session state manager transitions cleanly across Standard and Live visual states`() {
    val manager = AssistantSessionStateManager()
    assertEquals(AssistantVisualState.HIDDEN, manager.visualState.value)
    assertFalse(manager.isSessionActive.value)

    // 1. Open Image 1 Standard Assistant
    manager.openStandardAssistant()
    assertEquals(AssistantVisualState.STANDARD_ASSISTANT, manager.visualState.value)
    assertTrue(manager.isSessionActive.value)
    assertTrue(manager.isPanelExpanded.value)
    assertFalse(manager.isBubbleVisible.value)

    // 2. Toggle to Image 2 Live Assistant (Orb + Organic Waveform)
    manager.enterLiveAssistant()
    manager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
    assertEquals(AssistantVisualState.LIVE_ASSISTANT, manager.visualState.value)
    assertTrue(manager.isBubbleVisible.value)
    assertEquals(AssistantSessionPhase.LISTENING, manager.currentPhase.value)

    // 3. Drag Orb to 'X' dismiss -> stops Live voice & returns cleanly to Image 1 Standard Assistant
    manager.dismissLiveToStandard()
    assertEquals(AssistantVisualState.STANDARD_ASSISTANT, manager.visualState.value)
    assertFalse(manager.isBubbleVisible.value)
    assertEquals(VoiceSubsystemPhase.IDLE, manager.voicePhase.value)
    assertTrue(manager.isSessionActive.value)
    assertTrue(manager.isPanelExpanded.value)

    // 4. Full dismiss -> HIDDEN
    manager.dismissAssistantSession()
    assertEquals(AssistantVisualState.HIDDEN, manager.visualState.value)
    assertFalse(manager.isSessionActive.value)
    assertEquals(AssistantSessionPhase.INACTIVE, manager.currentPhase.value)
  }

  @Test
  fun `edge light mapper uses cyan-blue-violet-magenta-gold palette and throttles in battery saver mode`() {
    val normalConfig =
      EdgeLightConfig(
        enabled = true,
        preset = EdgeLightPreset.GEMINI_FLOW,
        brightness = 0.92f,
        batterySaverMode = false,
      )
    val standardParams =
      EdgeLightStateMapper.resolve(
        config = normalConfig,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 14,
        assistantSessionPhase = AssistantSessionPhase.PANEL_EXPANDED,
        isOverlayHost = true,
      )

    assertTrue(standardParams.isVisible)
    assertEquals(EdgeLightVisualState.IDLE, standardParams.visualState)
    assertTrue(standardParams.colorArgs.contains(0xFF06B6D4L))
    assertTrue(standardParams.colorArgs.contains(0xFF3B82F6L))
    assertTrue(standardParams.colorArgs.contains(0xFF8B5CF6L))
    assertTrue(standardParams.colorArgs.contains(0xFFEC4899L))
    assertTrue(standardParams.colorArgs.contains(0xFFFDE68AL))

    val batterySaverParams =
      EdgeLightStateMapper.resolve(
        config = normalConfig.copy(batterySaverMode = true),
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = true,
        currentHour = 14,
        assistantSessionPhase = AssistantSessionPhase.PANEL_EXPANDED,
        isOverlayHost = true,
      )

    assertTrue(batterySaverParams.isVisible)
    assertTrue(batterySaverParams.brightness <= 0.45f)
    assertEquals(1, batterySaverParams.glowLayerCount)
  }

  @Test
  fun `drawing controller supports multi-tool strokes, undo, redo, clear, and property updates`() {
    val controller = DrawingController()
    assertFalse(controller.state.value.canUndo)
    assertFalse(controller.state.value.canRedo)

    // Pen stroke
    controller.selectTool(DrawingTool.FREEHAND)
    controller.selectColor(0xFF22D3EEL)
    controller.setStrokeWidth(12f)
    controller.setStrokeAlpha(0.85f)
    controller.beginStroke(NormPoint(0.1f, 0.2f))
    controller.appendStrokePoint(NormPoint(0.4f, 0.5f))
    controller.commitStroke()

    // Highlighter stroke
    controller.selectTool(DrawingTool.HIGHLIGHTER)
    controller.beginStroke(NormPoint(0.2f, 0.3f))
    controller.appendStrokePoint(NormPoint(0.7f, 0.3f))
    controller.commitStroke()

    // Arrow stroke
    controller.selectTool(DrawingTool.ARROW)
    controller.beginStroke(NormPoint(0.5f, 0.5f))
    controller.appendStrokePoint(NormPoint(0.8f, 0.8f))
    controller.commitStroke()

    assertEquals(3, controller.state.value.strokes.size)
    assertTrue(controller.state.value.canUndo)
    assertFalse(controller.state.value.canRedo)

    // Undo twice -> Redo once
    assertTrue(controller.undo())
    assertTrue(controller.undo())
    assertEquals(1, controller.state.value.strokes.size)
    assertTrue(controller.state.value.canRedo)
    assertTrue(controller.redo())
    assertEquals(2, controller.state.value.strokes.size)

    // Adding a new stroke clears redo stack
    controller.selectTool(DrawingTool.RECTANGLE)
    controller.beginStroke(NormPoint(0.2f, 0.2f))
    controller.appendStrokePoint(NormPoint(0.6f, 0.6f))
    controller.commitStroke()
    assertFalse(controller.state.value.canRedo)
    assertEquals(3, controller.state.value.strokes.size)

    // Clear resets everything
    controller.clear()
    assertTrue(controller.state.value.strokes.isEmpty())
    assertFalse(controller.state.value.canUndo)
    assertFalse(controller.state.value.canRedo)
  }

  @Test
  fun `phone action controller requires confirmation gate for dial and sms and sanitizes numbers`() {
    assertEquals("+15550100", PhoneActionController.sanitizePhoneNumber("call +1 (555) 0100"))

    val dialPending = PhoneActionController.tryParseConfirmationCommand("call +15550100")
    assertNotNull(dialPending)
    assertEquals(PendingPhoneActionType.DIAL_PHONE, dialPending?.type)
    assertEquals("+15550100", dialPending?.targetNumber)

    val smsPending = PhoneActionController.tryParseConfirmationCommand("send sms to +15550100 saying Hello from Mousomi")
    assertNotNull(smsPending)
    assertEquals(PendingPhoneActionType.SEND_SMS, smsPending?.type)
    assertEquals("+15550100", smsPending?.targetNumber)
    assertEquals("Hello from Mousomi", smsPending?.messageBody)

    assertNull(PhoneActionController.tryParseConfirmationCommand("tell me a joke about phones"))
  }

  @Test
  fun `social integration manager prepares confirmation actions for WhatsApp, Instagram, and Facebook`() {
    val waPending = SocialIntegrationManager.tryParseSocialCommand("send whatsapp message to +15550199 saying Arriving in 10 mins")
    assertNotNull(waPending)
    assertEquals(SocialPlatform.WHATSAPP, waPending?.platform)
    assertEquals("+15550199", waPending?.recipientOrHandle)
    assertEquals("Arriving in 10 mins", waPending?.captionOrMessage)

    val igPending = SocialIntegrationManager.tryParseSocialCommand("post to instagram: Golden hour over the city")
    assertNotNull(igPending)
    assertEquals(SocialPlatform.INSTAGRAM, igPending?.platform)
    assertEquals("Golden hour over the city", igPending?.captionOrMessage)

    val fbPending = SocialIntegrationManager.tryParseSocialCommand("share to facebook: Weekend road trip update")
    assertNotNull(fbPending)
    assertEquals(SocialPlatform.FACEBOOK, fbPending?.platform)
    assertEquals("Weekend road trip update", fbPending?.captionOrMessage)

    assertNull(SocialIntegrationManager.tryParseSocialCommand("explain photosynthesis"))
  }

  @Test
  fun `verified model catalog preserves exact 7 selectable model options`() {
    assertEquals(7, AiModelOption.entries.size)
  }

  @Test
  fun `repeated bottom corner swipe invocations increment activation token cleanly without stale state`() {
    val manager = AssistantSessionStateManager()
    manager.openStandardAssistant(com.example.core.assistant.EdgeActivationEdge.BOTTOM_CORNER, nowMs = 1000L)
    val firstToken = manager.edgeActivation.value.token
    assertEquals(com.example.core.assistant.EdgeActivationEdge.BOTTOM_CORNER, manager.edgeActivation.value.edge)
    assertTrue(firstToken > 0L)

    // Simulate dismiss / gesture cancellation -> session returns to HIDDEN
    manager.dismissAssistantSession(keepScreenShare = false)
    assertEquals(AssistantVisualState.HIDDEN, manager.visualState.value)

    // Second bottom-corner swipe -> increments token cleanly for neon corner feedback replay
    manager.openStandardAssistant(com.example.core.assistant.EdgeActivationEdge.BOTTOM_CORNER, nowMs = 2000L)
    val secondToken = manager.edgeActivation.value.token
    assertEquals(AssistantVisualState.STANDARD_ASSISTANT, manager.visualState.value)
    assertEquals(com.example.core.assistant.EdgeActivationEdge.BOTTOM_CORNER, manager.edgeActivation.value.edge)
    assertTrue(secondToken > firstToken)
  }

  @Test
  fun `multi-turn dictation merger appends speech turns cleanly for review and editing`() {
    val partial = com.example.core.voice.DictationTranscriptMerger.mergePartial("", "summarize")
    assertEquals("summarize", partial)

    val turn1 = com.example.core.voice.DictationTranscriptMerger.appendFinalTurn("", "Summarize today's notes")
    assertEquals("Summarize today's notes", turn1)

    val turn2Partial = com.example.core.voice.DictationTranscriptMerger.mergePartial(turn1, "and highlight")
    assertEquals("Summarize today's notes and highlight", turn2Partial)

    val turn2Final = com.example.core.voice.DictationTranscriptMerger.appendFinalTurn(turn1, "and highlight action items.")
    assertEquals("Summarize today's notes and highlight action items.", turn2Final)
  }

  @Test
  fun `voice controller stops listening cleanly and maps speech recognition error 1 without false listening state`() {
    var stoppedCount = 0
    val fakeStt =
      object : com.example.core.voice.SpeechToTextGateway {
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
          onError: (message: String, isRecoverableSilence: Boolean) -> Unit,
          onRmsNormalized: (Float) -> Unit,
        ) {
          onRmsNormalized(0.75f)
          onPartial("hello")
        }

        override fun stopListening() {
          stoppedCount += 1
        }

        override fun release() = Unit
      }
    val fakeTts =
      object : com.example.core.voice.TextToSpeechGateway {
        override fun speak(
          messageId: String,
          text: String,
          preferences: com.example.domain.model.VoicePreferences,
          onStart: (String) -> Unit,
          onDone: () -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stop() = Unit
        override fun release() = Unit
      }

    val controller = com.example.core.voice.VoiceController(fakeStt, fakeTts)
    controller.startListening(com.example.domain.model.ResponseLanguage.ENGLISH)
    assertTrue(controller.voiceState.value is VoiceState.Listening)
    assertEquals(0.75f, controller.audioRmsNormalized.value, 0.01f)

    controller.stopListening()
    assertEquals(1, stoppedCount)
    assertEquals(VoiceState.Idle, controller.voiceState.value)
    assertEquals(0f, controller.audioRmsNormalized.value, 0.001f)

    val (errMsg, isSilence) = com.example.core.voice.SpeechRecognizerDiagnostics.mapErrorToMessage(1)
    assertFalse(isSilence)
    assertTrue(errMsg.contains("timed out", ignoreCase = true) || errMsg.contains("error 1", ignoreCase = true))
  }

  @Test
  fun `screen selection 10-second countdown ticks cleanly, pauses when snapped, and auto-cancels at zero`() {
    assertEquals(10, com.example.core.screen.ScreenSelectionManager.SELECTION_TIMEOUT_SECONDS)

    var state =
      com.example.core.screen.ScreenSelectionUiState(
        isActive = true,
        stage = com.example.core.screen.SelectionStage.READY_TO_DRAW,
      )
    assertEquals(10, state.remainingSeconds)

    // Ticks from 10 down to 1 without timing out
    repeat(9) { step ->
      val (next, timedOut) = com.example.core.screen.ScreenSelectionManager.advanceCountdown(state)
      assertFalse(timedOut)
      assertEquals(9 - step, next.remainingSeconds)
      assertTrue(next.isActive)
      state = next
    }
    assertEquals(1, state.remainingSeconds)

    // When user completes a snapped selection, countdown pauses without timing out
    val snapped =
      com.example.core.screen.SelectionGeometryResult(
        boundingBox = com.example.domain.model.NormBoundingBox(0.2f, 0.2f, 0.7f, 0.7f),
        detectedShape = com.example.core.screen.DetectedRegionShape.RECTANGLE,
        confidence = 0.95f,
      )
    val snappedState =
      state.copy(
        stage = com.example.core.screen.SelectionStage.SNAPPED_HIGHLIGHT,
        snappedResult = snapped,
      )
    val (afterSnappedTick, snappedTimedOut) =
      com.example.core.screen.ScreenSelectionManager.advanceCountdown(snappedState)
    assertFalse(snappedTimedOut)
    assertTrue(afterSnappedTick.isActive)

    // Reaching 0 while still waiting for drawing auto-cancels cleanly
    val (expiredState, didTimeout) =
      com.example.core.screen.ScreenSelectionManager.advanceCountdown(state)
    assertTrue(didTimeout)
    assertTrue(expiredState.hasTimedOut)
    assertFalse(expiredState.isActive)
  }
}
