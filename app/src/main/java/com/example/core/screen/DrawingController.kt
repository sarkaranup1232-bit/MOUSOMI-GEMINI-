package com.example.core.screen

import com.example.domain.model.AnnotationStroke
import com.example.domain.model.DrawingTool
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import java.util.UUID
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DrawingUiState(
  val strokes: List<AnnotationStroke> = emptyList(),
  val redoStack: List<AnnotationStroke> = emptyList(),
  val activePoints: List<NormPoint> = emptyList(),
  val selectedTool: DrawingTool = DrawingTool.FREEHAND,
  val selectedColorArgb: Long = 0xFF38BDF8L,
  val strokeWidthPx: Float = 9f,
  val strokeAlpha: Float = 0.92f,
  val hasUnsavedChanges: Boolean = false,
) {
  val canUndo: Boolean get() = strokes.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()
}

/**
 * Pure Kotlin Drawing & Screen Annotation Controller supporting:
 * - Pen (FREEHAND), Highlighter, Arrow, Circle, Rectangle, Selection, Pointer, Text, Eraser
 * - Multi-step Undo, Redo, Clear, Color Palette, Stroke Width, and Alpha
 */
class DrawingController(
  initialColorArgb: Long = 0xFF38BDF8L,
  initialStrokeWidthPx: Float = 9f,
) {
  private val _state =
    MutableStateFlow(
      DrawingUiState(
        selectedColorArgb = initialColorArgb,
        strokeWidthPx = initialStrokeWidthPx,
      )
    )
  val state: StateFlow<DrawingUiState> = _state.asStateFlow()

  fun selectTool(tool: DrawingTool) {
    _state.update { it.copy(selectedTool = tool) }
  }

  fun selectColor(colorArgb: Long) {
    _state.update { it.copy(selectedColorArgb = colorArgb) }
  }

  fun setStrokeWidth(widthPx: Float) {
    _state.update { it.copy(strokeWidthPx = widthPx.coerceIn(3f, 32f)) }
  }

  fun setStrokeAlpha(alpha: Float) {
    _state.update { it.copy(strokeAlpha = alpha.coerceIn(0.15f, 1.0f)) }
  }

  fun beginStroke(point: NormPoint) {
    _state.update { it.copy(activePoints = listOf(point)) }
  }

  fun appendStrokePoint(point: NormPoint) {
    val cur = _state.value
    if (cur.selectedTool == DrawingTool.ERASER) {
      eraseNear(point)
      return
    }
    _state.update { it.copy(activePoints = it.activePoints + point) }
  }

  fun commitStroke() {
    val cur = _state.value
    if (cur.selectedTool == DrawingTool.ERASER || cur.activePoints.size < 2) {
      _state.update { it.copy(activePoints = emptyList()) }
      return
    }
    val effectiveWidth =
      if (cur.selectedTool == DrawingTool.HIGHLIGHTER) cur.strokeWidthPx * 2.4f
      else cur.strokeWidthPx
    val effectiveAlpha =
      if (cur.selectedTool == DrawingTool.HIGHLIGHTER) (cur.strokeAlpha * 0.42f).coerceIn(0.15f, 0.6f)
      else cur.strokeAlpha

    val stroke =
      AnnotationStroke(
        id = "str_${UUID.randomUUID()}",
        tool = cur.selectedTool,
        colorArgb = cur.selectedColorArgb,
        strokeWidthPx = effectiveWidth,
        alpha = effectiveAlpha,
        points = cur.activePoints,
      )
    _state.update {
      it.copy(
        strokes = it.strokes + stroke,
        redoStack = emptyList(),
        activePoints = emptyList(),
        hasUnsavedChanges = true,
      )
    }
  }

  fun addPointerDot(point: NormPoint) {
    val cur = _state.value
    val stroke =
      AnnotationStroke(
        id = "ptr_${UUID.randomUUID()}",
        tool = DrawingTool.POINTER,
        colorArgb = cur.selectedColorArgb,
        strokeWidthPx = cur.strokeWidthPx,
        alpha = cur.strokeAlpha,
        points = listOf(point),
      )
    _state.update {
      it.copy(
        strokes = it.strokes + stroke,
        redoStack = emptyList(),
        hasUnsavedChanges = true,
      )
    }
  }

  fun addTextLabel(point: NormPoint, label: String) {
    val clean = label.trim()
    if (clean.isEmpty()) return
    val cur = _state.value
    val stroke =
      AnnotationStroke(
        id = "txt_${UUID.randomUUID()}",
        tool = DrawingTool.TEXT,
        colorArgb = cur.selectedColorArgb,
        strokeWidthPx = cur.strokeWidthPx,
        alpha = cur.strokeAlpha,
        points = listOf(point),
        textLabel = clean,
      )
    _state.update {
      it.copy(
        strokes = it.strokes + stroke,
        redoStack = emptyList(),
        hasUnsavedChanges = true,
      )
    }
  }

  fun eraseNear(point: NormPoint, thresholdNorm: Float = 0.04f): Boolean {
    val cur = _state.value
    val idx =
      cur.strokes.indexOfLast { s ->
        s.points.any { p -> hypot(p.x - point.x, p.y - point.y) < thresholdNorm }
      }
    if (idx < 0) return false
    val removed = cur.strokes[idx]
    val nextStrokes = cur.strokes.toMutableList().apply { removeAt(idx) }
    _state.update {
      it.copy(
        strokes = nextStrokes,
        redoStack = it.redoStack + removed,
        hasUnsavedChanges = nextStrokes.isNotEmpty(),
      )
    }
    return true
  }

  fun undo(): Boolean {
    val cur = _state.value
    if (cur.strokes.isEmpty()) return false
    val removed = cur.strokes.last()
    val remaining = cur.strokes.dropLast(1)
    _state.update {
      it.copy(
        strokes = remaining,
        redoStack = it.redoStack + removed,
        hasUnsavedChanges = remaining.isNotEmpty(),
      )
    }
    return true
  }

  fun redo(): Boolean {
    val cur = _state.value
    if (cur.redoStack.isEmpty()) return false
    val restored = cur.redoStack.last()
    _state.update {
      it.copy(
        strokes = it.strokes + restored,
        redoStack = it.redoStack.dropLast(1),
        hasUnsavedChanges = true,
      )
    }
    return true
  }

  fun clear() {
    _state.update {
      it.copy(
        strokes = emptyList(),
        redoStack = emptyList(),
        activePoints = emptyList(),
        hasUnsavedChanges = false,
      )
    }
  }

  fun markSaved() {
    _state.update { it.copy(hasUnsavedChanges = false) }
  }

  fun computeTargetBoundingBox(): NormBoundingBox =
    RegionCropper.computeBoundingBox(_state.value.strokes)
      ?: NormBoundingBox(0.05f, 0.1f, 0.95f, 0.9f)
}
