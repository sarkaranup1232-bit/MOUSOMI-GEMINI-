package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import com.example.core.screen.CropHandle
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionToolMode
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint

/**
 * Recognizes finger-drawing strokes and interactive corner/edge/reposition boundary drags
 * over a snapped selection frame.
 * Uses [rememberUpdatedState] for [currentSnappedBox] and callbacks so updating the bounding box
 * mid-drag never restarts [pointerInput] or interrupts an active resize/move gesture.
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
      var activeHandle: CropHandle? = null
      detectDragGestures(
        onDragStart = { startOffset ->
          val w = size.width.coerceAtLeast(1).toFloat()
          val h = size.height.coerceAtLeast(1).toFloat()
          val normStart = NormPoint((startOffset.x / w).coerceIn(0f, 1f), (startOffset.y / h).coerceIn(0f, 1f))
          val box = latestBox
          activeHandle =
            if (box != null) {
              geometryEngine.hitTestHandle(normStart, box)
            } else {
              null
            }
          if (activeHandle == null) {
            latestOnStrokeStart(normStart)
          }
        },
        onDrag = { change, dragAmount ->
          change.consume()
          val w = size.width.coerceAtLeast(1).toFloat()
          val h = size.height.coerceAtLeast(1).toFloat()
          val handle = activeHandle
          if (handle != null) {
            latestOnHandleDrag(handle, dragAmount.x / w, dragAmount.y / h)
          } else {
            val pos = change.position
            latestOnStrokeMove(NormPoint((pos.x / w).coerceIn(0f, 1f), (pos.y / h).coerceIn(0f, 1f)))
          }
        },
        onDragEnd = {
          if (activeHandle != null) {
            activeHandle = null
            latestOnHandleDragEnd()
          } else {
            latestOnStrokeEnd()
          }
        },
        onDragCancel = {
          if (activeHandle != null) {
            activeHandle = null
            latestOnHandleDragEnd()
          } else {
            latestOnStrokeEnd()
          }
        },
      )
    }
  }
}
