package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.core.screen.CropHandle
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionToolMode
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint

/**
 * Recognizes finger-drawing strokes and interactive corner/edge/reposition boundary drags
 * over a snapped selection frame.
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
  return this.pointerInput(enabled, toolMode, currentSnappedBox) {
    var activeHandle: CropHandle? = null
    detectDragGestures(
      onDragStart = { startOffset ->
        val w = size.width.coerceAtLeast(1).toFloat()
        val h = size.height.coerceAtLeast(1).toFloat()
        val normStart = NormPoint((startOffset.x / w).coerceIn(0f, 1f), (startOffset.y / h).coerceIn(0f, 1f))
        activeHandle =
          if (currentSnappedBox != null) {
            geometryEngine.hitTestHandle(normStart, currentSnappedBox)
          } else {
            null
          }
        if (activeHandle == null) {
          onStrokeStart(normStart)
        }
      },
      onDrag = { change, dragAmount ->
        change.consume()
        val w = size.width.coerceAtLeast(1).toFloat()
        val h = size.height.coerceAtLeast(1).toFloat()
        val handle = activeHandle
        if (handle != null) {
          onHandleDrag(handle, dragAmount.x / w, dragAmount.y / h)
        } else {
          val pos = change.position
          onStrokeMove(NormPoint((pos.x / w).coerceIn(0f, 1f), (pos.y / h).coerceIn(0f, 1f)))
        }
      },
      onDragEnd = {
        if (activeHandle != null) {
          activeHandle = null
          onHandleDragEnd()
        } else {
          onStrokeEnd()
        }
      },
      onDragCancel = {
        if (activeHandle != null) {
          activeHandle = null
          onHandleDragEnd()
        } else {
          onStrokeEnd()
        }
      },
    )
  }
}
