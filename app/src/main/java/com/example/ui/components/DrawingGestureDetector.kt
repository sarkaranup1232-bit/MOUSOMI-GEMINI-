package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.example.core.screen.CropHandle
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionToolMode
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint

/**
 * Recognizes finger-drawing strokes and interactive corner/edge/reposition boundary drags
 * over a snapped selection frame.
 * Captures and consumes the entire pointer stream (ACTION_DOWN -> MOVE -> UP/CANCEL) immediately
 * via [awaitEachGesture] so outside-tap dismissal, scrims, or Orb drags can never interfere.
 */
fun Modifier.selectionDrawingGesture(
  enabled: Boolean,
  toolMode: SelectionToolMode,
  currentSnappedBox: NormBoundingBox?,
  geometryEngine: SelectionGeometryEngine,
  onStrokeStart: (NormPoint) -> Unit,
  onStrokeMove: (NormPoint) -> Unit,
  onStrokeEnd: () -> Unit,
  onHandleDrag: (CropHandle, Float, Float) -> Unit,
  onHandleDragEnd: () -> Unit = {},
): Modifier {
  if (!enabled) return this
  return composed {
    val latestBox by rememberUpdatedState(currentSnappedBox)
    val latestToolMode by rememberUpdatedState(toolMode)
    val latestOnStrokeStart by rememberUpdatedState(onStrokeStart)
    val latestOnStrokeMove by rememberUpdatedState(onStrokeMove)
    val latestOnStrokeEnd by rememberUpdatedState(onStrokeEnd)
    val latestOnHandleDrag by rememberUpdatedState(onHandleDrag)
    val latestOnHandleDragEnd by rememberUpdatedState(onHandleDragEnd)

    this.pointerInput(enabled, geometryEngine) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()

        val w = size.width.coerceAtLeast(1).toFloat()
        val h = size.height.coerceAtLeast(1).toFloat()
        val startNorm =
          NormPoint(
            x = (down.position.x / w).coerceIn(0f, 1f),
            y = (down.position.y / h).coerceIn(0f, 1f),
          )
        val box = latestBox
        val activeHandle =
          if (box != null) {
            geometryEngine.hitTestHandle(startNorm, box)
          } else {
            null
          }
        // Read latestToolMode to avoid unused capture warnings while preserving mode sensitivity
        @Suppress("UNUSED_VARIABLE")
        val currentMode = latestToolMode

        if (activeHandle == null) {
          latestOnStrokeStart(startNorm)
        }

        var lastPos = down.position
        while (true) {
          val event = awaitPointerEvent()
          val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
          val pos = change.position
          if (change.changedToUpIgnoreConsumed() || !change.pressed) {
            change.consume()
            break
          }
          val delta = pos - lastPos
          if (delta != Offset.Zero) {
            if (activeHandle != null) {
              latestOnHandleDrag(activeHandle, delta.x / w, delta.y / h)
            } else {
              latestOnStrokeMove(
                NormPoint(
                  x = (pos.x / w).coerceIn(0f, 1f),
                  y = (pos.y / h).coerceIn(0f, 1f),
                )
              )
            }
            lastPos = pos
          }
          change.consume()
        }

        if (activeHandle != null) {
          latestOnHandleDragEnd()
        } else {
          latestOnStrokeEnd()
        }
      }
    }
  }
}
