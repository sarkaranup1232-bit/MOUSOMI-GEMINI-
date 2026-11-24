package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.core.overlay.OrbPositionHelper
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.OrbPopupAction
import com.example.domain.model.OrbQuickAction
import com.example.domain.model.OrbVisualMode
import com.example.domain.model.VoiceAudioMetrics
import kotlin.math.roundToInt

/**
 * Draggable in-app Floating AI Orb using OrbitalSmokeOrb (continuous neon border, orbital particles,
 * real-time audio smoke) with edge snapping, double-tap Voice shortcut, and 12-action FloatingOrbitPopup.
 */
@Composable
fun InAppFloatingOrbOverlay(
  config: FloatingOrbConfig,
  visualState: EdgeLightVisualState,
  onPositionCommitted: (xFraction: Float, yFraction: Float) -> Unit,
  onQuickAction: (OrbQuickAction) -> Unit,
  modifier: Modifier = Modifier,
  audioMetrics: VoiceAudioMetrics = VoiceAudioMetrics.SILENT,
  onPopupAction: ((OrbPopupAction) -> Unit)? = null,
  onSelectScreenRegion: (() -> Unit)? = null,
) {
  if (!config.enabled || !config.inAppPreviewVisible) return

  var menuOpen by remember { mutableStateOf(false) }
  var isDragging by remember { mutableStateOf(false) }
  val density = LocalDensity.current

  val orbMode =
    when {
      menuOpen -> OrbVisualMode.FOCUSED
      visualState == EdgeLightVisualState.LISTENING -> OrbVisualMode.LISTENING
      visualState == EdgeLightVisualState.SPEAKING -> OrbVisualMode.SPEAKING
      visualState == EdgeLightVisualState.GENERATING -> OrbVisualMode.IMAGE_GENERATION
      visualState == EdgeLightVisualState.THINKING ||
        visualState == EdgeLightVisualState.SEARCHING ||
        visualState == EdgeLightVisualState.RESEARCHING -> OrbVisualMode.PROCESSING
      visualState == EdgeLightVisualState.COMPLETED -> OrbVisualMode.SUCCESS
      visualState == EdgeLightVisualState.ERROR -> OrbVisualMode.ERROR
      else -> OrbVisualMode.IDLE
    }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val maxW = with(density) { maxWidth.toPx() }.coerceAtLeast(100f)
    val maxH = with(density) { maxHeight.toPx() }.coerceAtLeast(100f)
    val orbPx = with(density) { config.sizeDp.dp.toPx() }.coerceIn(64f, 240f)
    val usableW = (maxW - orbPx).coerceAtLeast(1f)
    val usableH = (maxH - orbPx).coerceAtLeast(1f)

    var dragX by remember(config.positionXFraction, usableW) {
      mutableFloatStateOf(config.positionXFraction.coerceIn(0f, 1f) * usableW)
    }
    var dragY by remember(config.positionYFraction, usableH) {
      mutableFloatStateOf(config.positionYFraction.coerceIn(0.08f, 0.88f) * usableH)
    }

    val latestSnapToEdge by rememberUpdatedState(config.snapToEdge)
    val latestDoubleTapVoice by rememberUpdatedState(config.doubleTapForVoice)

    Box(
      modifier =
        Modifier
          .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
          .size(config.sizeDp.dp)
          .alpha(config.opacity.coerceIn(0.4f, 1.0f))
          .scale(if (isDragging) 1.08f else 1f)
          .semantics {
            contentDescription = "Mousomi Floating AI Orb"
            onClick {
              menuOpen = !menuOpen
              true
            }
          }
          .pointerInput(usableW, usableH) {
            val touchSlop = viewConfiguration.touchSlop
            var lastTapUpMs = 0L
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              down.consume()
              val startX = dragX
              val startY = dragY
              var accDx = 0f
              var accDy = 0f
              var exceededSlop = false
              var cancelled = false

              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                  change.consume()
                  break
                }
                val delta = change.position - change.previousPosition
                accDx += delta.x
                accDy += delta.y
                if (!exceededSlop && hypot(accDx, accDy) > touchSlop) {
                  exceededSlop = true
                  isDragging = true
                }
                if (exceededSlop) {
                  dragX = (startX + accDx).coerceIn(0f, usableW)
                  dragY = (startY + accDy).coerceIn(0.08f * usableH, 0.88f * usableH)
                }
                change.consume()
              }

              if (exceededSlop) {
                isDragging = false
                val rawXFrac = (dragX / usableW).coerceIn(0f, 1f)
                val snappedXFrac = OrbPositionHelper.computeSnappedXFraction(rawXFrac, latestSnapToEdge)
                val clampedYFrac = OrbPositionHelper.clampFraction(dragY / usableH, 0.08f, 0.88f)
                dragX = snappedXFrac * usableW
                dragY = clampedYFrac * usableH
                onPositionCommitted(snappedXFrac, clampedYFrac)
              } else if (!cancelled) {
                val now = System.currentTimeMillis()
                val isDoubleTap = latestDoubleTapVoice && (now - lastTapUpMs) in 40L..280L
                if (isDoubleTap) {
                  lastTapUpMs = 0L
                  menuOpen = false
                  onQuickAction(OrbQuickAction.START_VOICE)
                } else {
                  lastTapUpMs = now
                  menuOpen = !menuOpen
                }
              }
            }
          }
          .testTag("floating_ai_orb"),
      contentAlignment = Alignment.Center,
    ) {
      OrbitalSmokeOrb(
        mode = orbMode,
        audioMetrics = audioMetrics,
        orbSize = config.sizeDp.dp,
        showCenterSparkle = true,
      )
    }

    if (menuOpen) {
      OrbitDismissScrim(
        visible = true,
        onDismiss = { menuOpen = false },
      ) {
        Box(modifier = Modifier.testTag("floating_orb_quick_panel")) {
          FloatingOrbitPopup(
            onSelectAction = { popupAction ->
              menuOpen = false
              if (onPopupAction != null) {
                onPopupAction(popupAction)
              } else {
                onQuickAction(mapPopupToQuickAction(popupAction))
              }
            },
            onDrawScreen = {
              menuOpen = false
              onQuickAction(OrbQuickAction.DRAW_ON_SCREEN)
            },
            onSelectScreenRegion =
              onSelectScreenRegion?.let { launch ->
                {
                  menuOpen = false
                  launch()
                }
              },
            onDismiss = { menuOpen = false },
          )
        }
      }
    }
  }
}

private fun mapPopupToQuickAction(popup: OrbPopupAction): OrbQuickAction =
  when (popup) {
    OrbPopupAction.VOICE_ASSISTANT -> OrbQuickAction.START_VOICE
    OrbPopupAction.SCREEN_ASSISTANT -> OrbQuickAction.ANALYZE_SCREEN
    OrbPopupAction.DEVICE_ACTIONS,
    OrbPopupAction.MULTI_TASK_AGENT,
    OrbPopupAction.ALARM_AND_REMINDER -> OrbQuickAction.OPEN_FULL_ASSISTANT
    else -> OrbQuickAction.ASK_MOUSOMI
  }
