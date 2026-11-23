package com.example.core.edge

import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.voice.LiveSessionPhase
import com.example.core.voice.OrbReactiveFrame
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveBorderLightState(
  val isActive: Boolean = false,
  val intensity: Float = 0f,
  val reactiveBoost: Float = 0f,
  val mappedPhase: AssistantSessionPhase = AssistantSessionPhase.INACTIVE,
)

/**
 * Synchronizes the 4-side screen-edge Neon Border Light cleanly with Live Voice lifecycle:
 * - Activation bloom when Live Assistant opens
 * - Listening perimeter glow pulsing with real user mic RMS
 * - AI speaking shimmer driven by real AI output audio amplitude
 * - Calm ambient edge during silent listening pauses
 * - Clean fade-out when Live session pauses, ends, or triggers Emergency Stop
 */
@Singleton
class BorderLightController @Inject constructor(
  private val sessionStateManager: AssistantSessionStateManager,
) {

  private val _state = MutableStateFlow(LiveBorderLightState())
  val state: StateFlow<LiveBorderLightState> = _state.asStateFlow()

  fun onLiveStarted(edge: EdgeActivationEdge = EdgeActivationEdge.BOTTOM) {
    sessionStateManager.activateFromTrigger(edge = edge, expandPanel = false)
    _state.value =
      LiveBorderLightState(
        isActive = true,
        intensity = 0.82f,
        reactiveBoost = 0.15f,
        mappedPhase = AssistantSessionPhase.LISTENING,
      )
  }

  fun syncWithOrbFrame(frame: OrbReactiveFrame) {
    val mapped =
      when (frame.sessionPhase) {
        LiveSessionPhase.IDLE,
        LiveSessionPhase.ENDED -> AssistantSessionPhase.INACTIVE
        LiveSessionPhase.PAUSED -> AssistantSessionPhase.PAUSED
        LiveSessionPhase.REQUESTING_PERMISSION,
        LiveSessionPhase.CONNECTING,
        LiveSessionPhase.RECONNECTING -> AssistantSessionPhase.ACTIVATING
        LiveSessionPhase.LISTENING -> AssistantSessionPhase.LISTENING
        LiveSessionPhase.USER_SPEAKING -> AssistantSessionPhase.USER_SPEAKING
        LiveSessionPhase.AI_PROCESSING,
        LiveSessionPhase.AI_SPEAKING -> AssistantSessionPhase.AI_RESPONDING
        LiveSessionPhase.ERROR -> AssistantSessionPhase.ERROR
      }
    val active =
      frame.sessionPhase != LiveSessionPhase.IDLE &&
        frame.sessionPhase != LiveSessionPhase.ENDED &&
        frame.sessionPhase != LiveSessionPhase.PAUSED

    _state.value =
      LiveBorderLightState(
        isActive = active,
        intensity =
          when {
            !active -> 0f
            frame.isCalmIdle -> 0.56f
            else -> (0.72f + frame.reactiveAmplitude * 0.28f).coerceIn(0.55f, 1f)
          },
        reactiveBoost = if (active) frame.reactiveAmplitude else 0f,
        mappedPhase = mapped,
      )
  }

  fun onPaused() {
    sessionStateManager.onPausedChanged(true)
    _state.value =
      LiveBorderLightState(
        isActive = false,
        intensity = 0f,
        reactiveBoost = 0f,
        mappedPhase = AssistantSessionPhase.PAUSED,
      )
  }

  fun onStopped(keepScreenShare: Boolean = false) {
    sessionStateManager.onEndLiveAssistant(keepScreenShare = keepScreenShare)
    _state.value = LiveBorderLightState()
  }
}
