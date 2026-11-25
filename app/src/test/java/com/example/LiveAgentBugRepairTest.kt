package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.example.core.apps.RichLinkResolver
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.assistant.AssistantLifecycleState
import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.AssistantSurfaceState
import com.example.core.assistant.VoiceSubsystemPhase
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.overlay.FloatingAssistantSessionStore
import com.example.core.voice.SpeechTextSanitizer
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.RichLinkKind
import com.example.domain.model.ScreenShareStatus
import com.example.ui.assistant.MousomiAssistActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveAgentBugRepairTest {

  @Test
  fun `assistant session state manager validates lifecycle transitions and locks drawing state`() {
    val manager = AssistantSessionStateManager()
    assertEquals(AssistantLifecycleState.CLOSED, manager.lifecycleState.value)

    manager.openStandardAssistant(EdgeActivationEdge.BOTTOM_CORNER, nowMs = 100L)
    assertEquals(AssistantLifecycleState.INITIAL_ASSISTANT, manager.lifecycleState.value)
    assertEquals(AssistantSurfaceState.EXPANDED, manager.surfaceState.value)

    manager.enterLiveAssistant(EdgeActivationEdge.BOTTOM, nowMs = 200L)
    manager.onVoicePhaseChanged(VoiceSubsystemPhase.CONNECTING)
    assertEquals(AssistantLifecycleState.LIVE_CONNECTING, manager.lifecycleState.value)

    manager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)
    assertEquals(AssistantLifecycleState.LIVE_LISTENING, manager.lifecycleState.value)

    manager.onVoicePhaseChanged(VoiceSubsystemPhase.USER_SPEAKING)
    assertEquals(AssistantLifecycleState.LIVE_PROCESSING, manager.lifecycleState.value)

    manager.onVoicePhaseChanged(VoiceSubsystemPhase.AI_RESPONDING)
    assertEquals(AssistantLifecycleState.LIVE_SPEAKING, manager.lifecycleState.value)

    manager.onPausedChanged(true)
    assertEquals(AssistantLifecycleState.LIVE_PAUSED, manager.lifecycleState.value)
    manager.onPausedChanged(false)
    manager.onVoicePhaseChanged(VoiceSubsystemPhase.LISTENING)

    assertTrue(manager.collapseToOrb())
    assertEquals(AssistantLifecycleState.COLLAPSED_ORB, manager.lifecycleState.value)

    assertTrue(manager.enterDrawingMode())
    assertEquals(AssistantLifecycleState.DRAWING, manager.lifecycleState.value)

    // Accidental panel/live transitions must NOT corrupt active DRAWING state
    manager.enterLiveAssistant(EdgeActivationEdge.BOTTOM, nowMs = 300L)
    assertEquals(AssistantSurfaceState.DRAWING, manager.surfaceState.value)
    manager.onPanelExpandedChanged(false)
    assertEquals(AssistantSurfaceState.DRAWING, manager.surfaceState.value)
    assertFalse(manager.collapseToOrb())

    assertTrue(manager.cancelDrawingMode())
    assertEquals(AssistantSurfaceState.COLLAPSED_ORB, manager.surfaceState.value)

    manager.onBeginStopping()
    assertEquals(AssistantLifecycleState.STOPPING, manager.lifecycleState.value)

    manager.onEndLiveAssistant(keepScreenShare = false)
    assertEquals(AssistantLifecycleState.CLOSED, manager.lifecycleState.value)
  }

  @Test
  fun `edge light state mapper respects assistant session settings, pauses, and power saving`() {
    val offConfig =
      EdgeLightConfig(
        enabled = false,
        assistantEdgeGlowEnabled = false,
        systemOverlayEnabled = false,
      )
    val offParams =
      EdgeLightStateMapper.resolve(
        config = offConfig,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Listening(),
        voiceRmsNormalized = 0.5f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
        forcePreview = false,
        assistantSessionPhase = AssistantSessionPhase.LISTENING,
        isAssistantSession = true,
      )
    assertFalse(offParams.isVisible)
    assertEquals(0f, offParams.brightness, 0.0001f)

    val assistantGlowOnlyConfig =
      EdgeLightConfig(
        enabled = false,
        assistantEdgeGlowEnabled = true,
        batterySaverMode = true,
        reducedMotion = true,
      )
    val activeGlow =
      EdgeLightStateMapper.resolve(
        config = assistantGlowOnlyConfig,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Listening(),
        voiceRmsNormalized = 0.7f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
        forcePreview = false,
        assistantSessionPhase = AssistantSessionPhase.LISTENING,
        isAssistantSession = true,
      )
    assertTrue(activeGlow.isVisible)
    assertEquals(0f, activeGlow.speedMultiplier, 0.0001f)
    assertTrue(activeGlow.breathingOnly)
    assertEquals(1, activeGlow.glowLayerCount)

    val pausedGlow =
      EdgeLightStateMapper.resolve(
        config = assistantGlowOnlyConfig,
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
        forcePreview = false,
        assistantSessionPhase = AssistantSessionPhase.PAUSED,
        isAssistantSession = true,
      )
    assertFalse(pausedGlow.isVisible)
  }

  @Test
  fun `speech text sanitizer removes emojis and markdown while keeping Bengali and English clear`() {
    val raw = "## উত্তর 🔥✨\n**Mousomi AI** কাজ করছে 🚀👨‍💻! আজ আবহাওয়া কেমন?"
    val clean = SpeechTextSanitizer.sanitizeForSpeech(raw)

    assertFalse(clean.contains("🔥"))
    assertFalse(clean.contains("🚀"))
    assertFalse(clean.contains("**"))
    assertFalse(clean.contains("##"))
    assertTrue(clean.contains("উত্তর"))
    assertTrue(clean.contains("Mousomi AI"))
    assertTrue(clean.contains("কাজ করছে"))
    assertTrue(clean.contains("আজ আবহাওয়া কেমন?"))
    assertEquals("bn-IN", SpeechTextSanitizer.detectDominantLanguageTag(clean, fallbackTag = "en-US"))
  }

  @Test
  fun `floating session store deduplicates identical re-emitted live transcript turns`() {
    val store = FloatingAssistantSessionStore()
    store.appendLiveTranscriptTurn(isUser = false, text = "Hello from Mousomi Live")
    store.appendLiveTranscriptTurn(isUser = false, text = "Hello from Mousomi Live")
    assertEquals(1, store.state.value.turns.size)

    store.appendLiveTranscriptTurn(isUser = true, text = "Tell me today's news")
    assertEquals(2, store.state.value.turns.size)
  }

  @Test
  fun `rich link resolver ignores casual app mentions and extracts real URLs and explicit launches`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val shadowPm = shadowOf(context.packageManager)
    val ytPkg = "com.google.android.youtube"
    val ytAppInfo = ApplicationInfo().apply {
      packageName = ytPkg
      nonLocalizedLabel = "YouTube"
    }
    val ytPkgInfo = PackageInfo().apply {
      packageName = ytPkg
      applicationInfo = ytAppInfo
    }
    shadowPm.installPackage(ytPkgInfo)

    val resolver = RichLinkResolver(context)

    // Casual prose mention without actionable launch intent or URL must NOT emit launcher cards
    val casualCards = resolver.extractRichCards("Unlike YouTube or WhatsApp, this is purely informational text.")
    assertTrue("Expected no app launcher cards for casual prose", casualCards.isEmpty())

    // Explicit HTTPS URL and actionable launch verb should emit structured cards
    val actionableCards = resolver.extractRichCards("Please open YouTube or check https://developer.android.com/compose")
    assertTrue(actionableCards.any { it.kind == RichLinkKind.WEBSITE && it.domain == "developer.android.com" })
    assertTrue(actionableCards.any { it.kind == RichLinkKind.YOUTUBE && it.packageName == ytPkg })
  }

  @Test
  fun `mousomi assist activity does not use FLAG_ACTIVITY_NO_HISTORY so permission dialogs preserve session`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val info = context.packageManager.getActivityInfo(ComponentName(context, MousomiAssistActivity::class.java), 0)
    assertEquals(0, info.flags and ActivityInfo.FLAG_NO_HISTORY)
  }
}
