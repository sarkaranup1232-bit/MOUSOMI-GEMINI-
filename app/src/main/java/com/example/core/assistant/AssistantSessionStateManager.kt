package com.example.core.assistant

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantVisualState {
  HIDDEN,
  STANDARD_ASSISTANT,
  LIVE_ASSISTANT,
}

/**
 * Explicit top-level UI surface state machine for the floating/session Assistant UI:
 * - [CLOSED]: Assistant session UI is inactive/closed.
 * - [EXPANDED]: Assistant card/dock is expanded and interactive.
 * - [DRAWING]: Isolated screen-area drawing/selection mode; blocks accidental outside-tap collapse or dismissal.
 * - [COLLAPSED_ORB]: Compact draggable floating Orb mode; preserves live voice/screen share/conversation
 *   while passing outside touches through to the underlying screen.
 */
enum class AssistantSurfaceState {
  CLOSED,
  EXPANDED,
  DRAWING,
  COLLAPSED_ORB,
}

enum class AssistantSessionPhase {
  INACTIVE,
  ACTIVATING,
  LISTENING,
  USER_SPEAKING,
  AI_RESPONDING,
  SCREEN_SHARING,
  DRAWING,
  PANEL_EXPANDED,
  KEYBOARD_VISIBLE,
  PAUSED,
  STOPPING,
  ERROR,
}

enum class VoiceSubsystemPhase {
  IDLE,
  CONNECTING,
  LISTENING,
  USER_SPEAKING,
  AI_RESPONDING,
  PAUSED,
  ERROR,
}

enum class EdgeActivationEdge {
  NONE,
  BOTTOM,
  BOTTOM_CORNER,
  LEFT,
  RIGHT,
}

data class EdgeActivationSignal(
  val edge: EdgeActivationEdge = EdgeActivationEdge.NONE,
  val token: Long = 0L,
  val triggeredAtMs: Long = 0L,
)

/**
 * Central @Singleton coordinator for Assistant lifecycle across:
 * - Explicit UI surface state machine ([AssistantSurfaceState])
 * - Two visual Assistant modes: [AssistantVisualState.STANDARD_ASSISTANT] & [AssistantVisualState.LIVE_ASSISTANT]
 * - Voice, Edge Light, Floating Bubble/Orb position, Floating Panel, IME Keyboard, Screen Share, and Drawing.
 * Subsystems are held as independent StateFlows so collapsing to [AssistantSurfaceState.COLLAPSED_ORB]
 * or closing the keyboard never kills active voice, conversation state, or screen capture unexpectedly.
 */
@Singleton
class AssistantSessionStateManager @Inject constructor() {

  private val _surfaceState = MutableStateFlow(AssistantSurfaceState.CLOSED)
  val surfaceState: StateFlow<AssistantSurfaceState> = _surfaceState.asStateFlow()

  private val _orbXFraction = MutableStateFlow(0.86f)
  val orbXFraction: StateFlow<Float> = _orbXFraction.asStateFlow()

  private val _orbYFraction = MutableStateFlow(0.70f)
  val orbYFraction: StateFlow<Float> = _orbYFraction.asStateFlow()

  private val _visualState = MutableStateFlow(AssistantVisualState.HIDDEN)
  val visualState: StateFlow<AssistantVisualState> = _visualState.asStateFlow()

  private val _isSessionActive = MutableStateFlow(false)
  val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

  private val _isActivating = MutableStateFlow(false)
  val isActivating: StateFlow<Boolean> = _isActivating.asStateFlow()

  private val _isStopping = MutableStateFlow(false)
  val isStopping: StateFlow<Boolean> = _isStopping.asStateFlow()

  private val _isPaused = MutableStateFlow(false)
  val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

  private val _voicePhase = MutableStateFlow(VoiceSubsystemPhase.IDLE)
  val voicePhase: StateFlow<VoiceSubsystemPhase> = _voicePhase.asStateFlow()

  private val _isBubbleVisible = MutableStateFlow(false)
  val isBubbleVisible: StateFlow<Boolean> = _isBubbleVisible.asStateFlow()

  private val _isPanelExpanded = MutableStateFlow(false)
  val isPanelExpanded: StateFlow<Boolean> = _isPanelExpanded.asStateFlow()

  private val _isKeyboardVisible = MutableStateFlow(false)
  val isKeyboardVisible: StateFlow<Boolean> = _isKeyboardVisible.asStateFlow()

  private val _isScreenSharing = MutableStateFlow(false)
  val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

  private val _isDrawingActive = MutableStateFlow(false)
  val isDrawingActive: StateFlow<Boolean> = _isDrawingActive.asStateFlow()

  private val _edgeActivation = MutableStateFlow(EdgeActivationSignal())
  val edgeActivation: StateFlow<EdgeActivationSignal> = _edgeActivation.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val _currentPhase = MutableStateFlow(AssistantSessionPhase.INACTIVE)
  val currentPhase: StateFlow<AssistantSessionPhase> = _currentPhase.asStateFlow()

  private var lastTriggerAtMs: Long = 0L
  private var activationTokenCounter: Long = 0L
  private var stateBeforeDrawing: AssistantSurfaceState = AssistantSurfaceState.EXPANDED

  @Synchronized
  fun updateOrbPosition(xFraction: Float, yFraction: Float) {
    _orbXFraction.value = xFraction.coerceIn(0.02f, 0.98f)
    _orbYFraction.value = yFraction.coerceIn(0.06f, 0.90f)
  }

  /**
   * Validates and executes explicit transitions on the 4-state UI machine.
   * Rejects accidental or invalid transitions (e.g., collapsing to Orb during active DRAWING,
   * or entering DRAWING directly from CLOSED).
   */
  @Synchronized
  fun transitionSurfaceState(
    target: AssistantSurfaceState,
    allowForceClose: Boolean = false,
  ): Boolean {
    val current = _surfaceState.value
    if (current == target) return true

    val allowed =
      when (current) {
        AssistantSurfaceState.CLOSED ->
          target == AssistantSurfaceState.EXPANDED ||
            target == AssistantSurfaceState.COLLAPSED_ORB
        AssistantSurfaceState.EXPANDED ->
          target == AssistantSurfaceState.DRAWING ||
            target == AssistantSurfaceState.COLLAPSED_ORB ||
            target == AssistantSurfaceState.CLOSED
        AssistantSurfaceState.DRAWING ->
          target == AssistantSurfaceState.EXPANDED ||
            (target == AssistantSurfaceState.COLLAPSED_ORB &&
              allowForceClose) ||
            (target == AssistantSurfaceState.CLOSED && allowForceClose)
        AssistantSurfaceState.COLLAPSED_ORB ->
          target == AssistantSurfaceState.EXPANDED ||
            target == AssistantSurfaceState.DRAWING ||
            target == AssistantSurfaceState.CLOSED
      }
    if (!allowed) return false

    when (target) {
      AssistantSurfaceState.CLOSED -> {
        dismissAssistantSession(keepScreenShare = _isScreenSharing.value)
      }
      AssistantSurfaceState.EXPANDED -> {
        _isStopping.value = false
        _isDrawingActive.value = false
        _isSessionActive.value = true
        _isPanelExpanded.value = true
        if (_visualState.value == AssistantVisualState.HIDDEN) {
          _visualState.value = AssistantVisualState.STANDARD_ASSISTANT
        }
        if (_visualState.value != AssistantVisualState.LIVE_ASSISTANT) {
          _isBubbleVisible.value = false
        }
        _surfaceState.value = AssistantSurfaceState.EXPANDED
        recomputePhase()
      }
      AssistantSurfaceState.DRAWING -> {
        stateBeforeDrawing =
          if (current == AssistantSurfaceState.COLLAPSED_ORB) AssistantSurfaceState.COLLAPSED_ORB
          else AssistantSurfaceState.EXPANDED
        _isStopping.value = false
        _isSessionActive.value = true
        _isDrawingActive.value = true
        _isKeyboardVisible.value = false
        _surfaceState.value = AssistantSurfaceState.DRAWING
        recomputePhase()
      }
      AssistantSurfaceState.COLLAPSED_ORB -> {
        _isStopping.value = false
        _isDrawingActive.value = false
        _isPanelExpanded.value = false
        _isKeyboardVisible.value = false
        _isActivating.value = false
        _isSessionActive.value = true
        _isBubbleVisible.value = true
        if (_visualState.value == AssistantVisualState.HIDDEN) {
          _visualState.value = AssistantVisualState.STANDARD_ASSISTANT
        }
        _surfaceState.value = AssistantSurfaceState.COLLAPSED_ORB
        recomputePhase()
      }
    }
    return true
  }

  /**
   * EXPANDED (or COLLAPSED_ORB) -> DRAWING. Blocks outside-tap dismissal while drawing.
   */
  @Synchronized
  fun enterDrawingMode(): Boolean = transitionSurfaceState(AssistantSurfaceState.DRAWING)

  /**
   * DRAWING -> EXPANDED after user confirms a cropped/selected region.
   */
  @Synchronized
  fun confirmDrawingMode(): Boolean = transitionSurfaceState(AssistantSurfaceState.EXPANDED)

  /**
   * DRAWING -> prior surface state (usually EXPANDED) on explicit Cancel.
   */
  @Synchronized
  fun cancelDrawingMode(): Boolean {
    if (_surfaceState.value != AssistantSurfaceState.DRAWING) return false
    val restoreTarget =
      if (stateBeforeDrawing == AssistantSurfaceState.COLLAPSED_ORB) {
        AssistantSurfaceState.COLLAPSED_ORB
      } else {
        AssistantSurfaceState.EXPANDED
      }
    return transitionSurfaceState(restoreTarget, allowForceClose = true)
  }

  /**
   * Outside tap handler: transitions EXPANDED -> COLLAPSED_ORB without ending Live Audio,
   * voice session, screen share, or conversation history.
   * Explicitly blocked while in DRAWING or CLOSED.
   */
  @Synchronized
  fun collapseToOrb(): Boolean {
    if (_surfaceState.value != AssistantSurfaceState.EXPANDED) return false
    return transitionSurfaceState(AssistantSurfaceState.COLLAPSED_ORB)
  }

  /**
   * Compact Orb tap handler: restores COLLAPSED_ORB -> EXPANDED seamlessly.
   */
  @Synchronized
  fun expandFromOrb(): Boolean {
    if (_surfaceState.value != AssistantSurfaceState.COLLAPSED_ORB) return false
    return transitionSurfaceState(AssistantSurfaceState.EXPANDED)
  }

  /**
   * Explicit Close/End action: transitions EXPANDED or COLLAPSED_ORB -> CLOSED.
   * Outside taps must NEVER invoke this method.
   */
  @Synchronized
  fun closeAssistant(
    keepScreenShare: Boolean = false,
    force: Boolean = false,
  ): Boolean {
    val cur = _surfaceState.value
    if (cur == AssistantSurfaceState.DRAWING && !force) return false
    dismissAssistantSession(keepScreenShare = keepScreenShare)
    return true
  }

  @Synchronized
  fun openStandardAssistant(
    edge: EdgeActivationEdge = EdgeActivationEdge.BOTTOM_CORNER,
    nowMs: Long = System.currentTimeMillis(),
  ) {
    activationTokenCounter += 1L
    _isStopping.value = false
    _isPaused.value = false
    _lastError.value = null
    _isActivating.value = true
    _isSessionActive.value = true
    _isDrawingActive.value = false
    _isPanelExpanded.value = true
    _isBubbleVisible.value = false
    _visualState.value = AssistantVisualState.STANDARD_ASSISTANT
    _surfaceState.value = AssistantSurfaceState.EXPANDED
    _edgeActivation.value =
      EdgeActivationSignal(
        edge = if (edge == EdgeActivationEdge.NONE) EdgeActivationEdge.BOTTOM_CORNER else edge,
        token = activationTokenCounter,
        triggeredAtMs = nowMs,
      )
    recomputePhase()
  }

  @Synchronized
  fun enterLiveAssistant(
    edge: EdgeActivationEdge = EdgeActivationEdge.BOTTOM,
    nowMs: Long = System.currentTimeMillis(),
  ) {
    activationTokenCounter += 1L
    _isStopping.value = false
    _isPaused.value = false
    _lastError.value = null
    _isSessionActive.value = true
    _isDrawingActive.value = false
    _isBubbleVisible.value = true
    _visualState.value = AssistantVisualState.LIVE_ASSISTANT
    if (_surfaceState.value == AssistantSurfaceState.CLOSED) {
      _isPanelExpanded.value = true
      _surfaceState.value = AssistantSurfaceState.EXPANDED
    }
    _edgeActivation.value =
      EdgeActivationSignal(
        edge = if (edge == EdgeActivationEdge.NONE) EdgeActivationEdge.BOTTOM else edge,
        token = activationTokenCounter,
        triggeredAtMs = nowMs,
      )
    recomputePhase()
  }

  @Synchronized
  fun dismissLiveToStandard() {
    _isPaused.value = false
    _voicePhase.value = VoiceSubsystemPhase.IDLE
    _isBubbleVisible.value = false
    _isDrawingActive.value = false
    _isActivating.value = false
    _isSessionActive.value = true
    _isPanelExpanded.value = true
    _visualState.value = AssistantVisualState.STANDARD_ASSISTANT
    _surfaceState.value = AssistantSurfaceState.EXPANDED
    recomputePhase()
  }

  @Synchronized
  fun dismissAssistantSession(keepScreenShare: Boolean = false) {
    _isStopping.value = false
    _isActivating.value = false
    _isPaused.value = false
    _isPanelExpanded.value = false
    _isKeyboardVisible.value = false
    _isBubbleVisible.value = false
    _isDrawingActive.value = false
    _voicePhase.value = VoiceSubsystemPhase.IDLE
    _lastError.value = null
    if (!keepScreenShare) {
      _isScreenSharing.value = false
    }
    _isSessionActive.value = _isScreenSharing.value
    _visualState.value = AssistantVisualState.HIDDEN
    _surfaceState.value = AssistantSurfaceState.CLOSED
    recomputePhase()
  }

  @Synchronized
  fun activateFromTrigger(
    edge: EdgeActivationEdge,
    expandPanel: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    debounceMs: Long = 650L,
  ): Boolean {
    if (nowMs - lastTriggerAtMs < debounceMs && _isSessionActive.value && _isPanelExpanded.value) {
      return false
    }
    lastTriggerAtMs = nowMs
    activationTokenCounter += 1L
    _isStopping.value = false
    _isPaused.value = false
    _lastError.value = null
    _isActivating.value = true
    _isSessionActive.value = true
    _isDrawingActive.value = false
    _isBubbleVisible.value = true
    if (expandPanel) {
      _isPanelExpanded.value = true
      _surfaceState.value = AssistantSurfaceState.EXPANDED
    } else if (_surfaceState.value == AssistantSurfaceState.CLOSED) {
      _surfaceState.value = AssistantSurfaceState.COLLAPSED_ORB
    }
    if (_visualState.value == AssistantVisualState.HIDDEN) {
      _visualState.value =
        if (expandPanel) AssistantVisualState.STANDARD_ASSISTANT
        else AssistantVisualState.LIVE_ASSISTANT
    }
    _edgeActivation.value =
      EdgeActivationSignal(
        edge = if (edge == EdgeActivationEdge.NONE) EdgeActivationEdge.BOTTOM_CORNER else edge,
        token = activationTokenCounter,
        triggeredAtMs = nowMs,
      )
    recomputePhase()
    return true
  }

  fun onActivationAnimationSettled() {
    _isActivating.value = false
    recomputePhase()
  }

  fun onBubbleShown(visible: Boolean) {
    _isBubbleVisible.value = visible
    if (visible && !_isStopping.value) {
      _isSessionActive.value = true
      if (_surfaceState.value == AssistantSurfaceState.CLOSED && !_isPanelExpanded.value && !_isDrawingActive.value) {
        _surfaceState.value = AssistantSurfaceState.COLLAPSED_ORB
      }
    }
    recomputePhase()
  }

  fun onPanelExpandedChanged(expanded: Boolean) {
    _isPanelExpanded.value = expanded
    if (expanded) {
      _isStopping.value = false
      _isSessionActive.value = true
      _isBubbleVisible.value = true
      if (_visualState.value == AssistantVisualState.HIDDEN) {
        _visualState.value = AssistantVisualState.STANDARD_ASSISTANT
      }
      if (!_isDrawingActive.value) {
        _surfaceState.value = AssistantSurfaceState.EXPANDED
      }
    } else {
      // Closing panel collapses only UI panel & IME; leaves Voice & Screen Share untouched
      _isKeyboardVisible.value = false
      if (!_isDrawingActive.value) {
        _surfaceState.value =
          if (_isBubbleVisible.value || _isSessionActive.value) AssistantSurfaceState.COLLAPSED_ORB
          else AssistantSurfaceState.CLOSED
      }
    }
    recomputePhase()
  }

  fun onKeyboardVisibilityChanged(visible: Boolean) {
    if (_isKeyboardVisible.value != visible) {
      _isKeyboardVisible.value = visible
      recomputePhase()
    }
  }

  fun onVoicePhaseChanged(phase: VoiceSubsystemPhase, errorMessage: String? = null) {
    _voicePhase.value = phase
    if (phase == VoiceSubsystemPhase.ERROR) {
      _lastError.value = errorMessage ?: "Voice session error"
    } else if (phase != VoiceSubsystemPhase.IDLE) {
      _lastError.value = null
      _isStopping.value = false
      _isSessionActive.value = true
    }
    _isPaused.value = phase == VoiceSubsystemPhase.PAUSED
    recomputePhase()
  }

  fun onPausedChanged(paused: Boolean) {
    _isPaused.value = paused
    if (paused && _voicePhase.value != VoiceSubsystemPhase.IDLE) {
      _voicePhase.value = VoiceSubsystemPhase.PAUSED
    }
    recomputePhase()
  }

  fun onScreenSharingChanged(active: Boolean) {
    _isScreenSharing.value = active
    if (active) {
      _isStopping.value = false
      _isSessionActive.value = true
    }
    recomputePhase()
  }

  fun onDrawingChanged(active: Boolean) {
    _isDrawingActive.value = active
    if (active) {
      stateBeforeDrawing =
        if (_surfaceState.value == AssistantSurfaceState.COLLAPSED_ORB) AssistantSurfaceState.COLLAPSED_ORB
        else AssistantSurfaceState.EXPANDED
      _isStopping.value = false
      _isSessionActive.value = true
      _surfaceState.value = AssistantSurfaceState.DRAWING
    } else if (_surfaceState.value == AssistantSurfaceState.DRAWING) {
      _surfaceState.value =
        if (stateBeforeDrawing == AssistantSurfaceState.COLLAPSED_ORB && !_isPanelExpanded.value) {
          AssistantSurfaceState.COLLAPSED_ORB
        } else {
          AssistantSurfaceState.EXPANDED
        }
    }
    recomputePhase()
  }

  fun onBubbleDismissed(stopAssistantVoice: Boolean = true) {
    _isPanelExpanded.value = false
    _isKeyboardVisible.value = false
    _isBubbleVisible.value = false
    _isActivating.value = false
    if (stopAssistantVoice) {
      _voicePhase.value = VoiceSubsystemPhase.IDLE
      _isPaused.value = false
    }
    val hasRemainingWork = _isScreenSharing.value || _isDrawingActive.value ||
      (_voicePhase.value != VoiceSubsystemPhase.IDLE && !stopAssistantVoice)
    if (!hasRemainingWork) {
      _isSessionActive.value = false
      _isStopping.value = false
      _visualState.value = AssistantVisualState.HIDDEN
      _surfaceState.value = AssistantSurfaceState.CLOSED
    } else if (_isDrawingActive.value) {
      _surfaceState.value = AssistantSurfaceState.DRAWING
    }
    recomputePhase()
  }

  fun onBeginStopping() {
    _isStopping.value = true
    _isActivating.value = false
    _isKeyboardVisible.value = false
    _isPanelExpanded.value = false
    _visualState.value = AssistantVisualState.HIDDEN
    _surfaceState.value = AssistantSurfaceState.CLOSED
    recomputePhase()
  }

  fun onEndLiveAssistant(keepScreenShare: Boolean = false) {
    _isStopping.value = false
    _isActivating.value = false
    _isPaused.value = false
    _isPanelExpanded.value = false
    _isKeyboardVisible.value = false
    _isBubbleVisible.value = false
    _isDrawingActive.value = false
    _voicePhase.value = VoiceSubsystemPhase.IDLE
    _lastError.value = null
    if (!keepScreenShare) {
      _isScreenSharing.value = false
    }
    _isSessionActive.value = _isScreenSharing.value
    _visualState.value = AssistantVisualState.HIDDEN
    _surfaceState.value = AssistantSurfaceState.CLOSED
    recomputePhase()
  }

  fun clearError() {
    _lastError.value = null
    if (_voicePhase.value == VoiceSubsystemPhase.ERROR) {
      _voicePhase.value = VoiceSubsystemPhase.IDLE
    }
    recomputePhase()
  }

  private fun recomputePhase() {
    val next =
      when {
        _isStopping.value -> AssistantSessionPhase.STOPPING
        _lastError.value != null -> AssistantSessionPhase.ERROR
        _isPaused.value -> AssistantSessionPhase.PAUSED
        _isDrawingActive.value -> AssistantSessionPhase.DRAWING
        _voicePhase.value == VoiceSubsystemPhase.USER_SPEAKING -> AssistantSessionPhase.USER_SPEAKING
        _voicePhase.value == VoiceSubsystemPhase.AI_RESPONDING -> AssistantSessionPhase.AI_RESPONDING
        _voicePhase.value == VoiceSubsystemPhase.LISTENING ||
          _voicePhase.value == VoiceSubsystemPhase.CONNECTING -> AssistantSessionPhase.LISTENING
        _isScreenSharing.value -> AssistantSessionPhase.SCREEN_SHARING
        _isKeyboardVisible.value -> AssistantSessionPhase.KEYBOARD_VISIBLE
        _isPanelExpanded.value -> AssistantSessionPhase.PANEL_EXPANDED
        _isActivating.value -> AssistantSessionPhase.ACTIVATING
        _isSessionActive.value || _isBubbleVisible.value -> AssistantSessionPhase.ACTIVATING
        else -> AssistantSessionPhase.INACTIVE
      }
    _currentPhase.value = next
  }
}
