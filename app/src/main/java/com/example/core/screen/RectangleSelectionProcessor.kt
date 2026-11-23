package com.example.core.screen

import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Handles rectangular box creation and precise interactive corner/edge boundary adjustment
 * and drag-to-reposition operations.
 */
@Singleton
class RectangleSelectionProcessor @Inject constructor() {

  fun fromDragCorners(start: NormPoint, end: NormPoint): SelectionGeometryResult {
    val box =
      FreehandPathProcessor.ensureMinimumReadableBox(
        minX = min(start.x, end.x),
        minY = min(start.y, end.y),
        maxX = max(start.x, end.x),
        maxY = max(start.y, end.y),
        padding = 0f,
      )
    return SelectionGeometryResult(
      boundingBox = box,
      detectedShape = DetectedRegionShape.RECTANGLE,
      simplifiedContour = rectContour(box),
      cornerRadiusFraction = 0.08f,
      confidence = 1.0f,
      isClosedLoop = true,
    )
  }

  /**
   * Evaluates whether a freehand stroke traces an axis-aligned rectangle (comparing point proximity
   * to the bounding box perimeter edges) with a confidence score in [0f..1f].
   */
  fun scoreRectangularity(points: List<NormPoint>): Float {
    if (points.size < 5) return 0f
    var minX = 1f
    var minY = 1f
    var maxX = 0f
    var maxY = 0f
    points.forEach { p ->
      minX = min(minX, p.x)
      minY = min(minY, p.y)
      maxX = max(maxX, p.x)
      maxY = max(maxY, p.y)
    }
    val w = (maxX - minX).coerceAtLeast(0.001f)
    val h = (maxY - minY).coerceAtLeast(0.001f)
    val diag = kotlin.math.hypot(w, h)
    val tol = diag * 0.055f

    val onPerimeterCount =
      points.count { p ->
        val dxLeft = abs(p.x - minX)
        val dxRight = abs(p.x - maxX)
        val dyTop = abs(p.y - minY)
        val dyBottom = abs(p.y - maxY)
        minOf(dxLeft, dxRight, dyTop, dyBottom) <= tol
      }
    return (onPerimeterCount.toFloat() / points.size).coerceIn(0f, 1f)
  }

  /**
   * Adjusts an existing [NormBoundingBox] by dragging a corner handle, edge handle, or moving
   * the whole selection while optionally preserving an aspect ratio (`width / height` in normalized space).
   */
  fun adjustBox(
    box: NormBoundingBox,
    handle: CropHandle,
    deltaXNorm: Float,
    deltaYNorm: Float,
    minSpan: Float = FreehandPathProcessor.MIN_SPAN,
    targetAspectRatio: Float? = null,
  ): NormBoundingBox {
    if (handle == CropHandle.MOVE) {
      val w = (box.right - box.left).coerceIn(minSpan, 1f)
      val h = (box.bottom - box.top).coerceIn(minSpan, 1f)
      val newLeft = (box.left + deltaXNorm).coerceIn(0f, (1f - w).coerceAtLeast(0f))
      val newTop = (box.top + deltaYNorm).coerceIn(0f, (1f - h).coerceAtLeast(0f))
      return NormBoundingBox(
        left = newLeft,
        top = newTop,
        right = (newLeft + w).coerceAtMost(1f),
        bottom = (newTop + h).coerceAtMost(1f),
      )
    }

    var left = box.left
    var top = box.top
    var right = box.right
    var bottom = box.bottom

    when (handle) {
      CropHandle.TOP_LEFT -> {
        left = (left + deltaXNorm).coerceIn(0f, right - minSpan)
        top = (top + deltaYNorm).coerceIn(0f, bottom - minSpan)
      }
      CropHandle.TOP_RIGHT -> {
        right = (right + deltaXNorm).coerceIn(left + minSpan, 1f)
        top = (top + deltaYNorm).coerceIn(0f, bottom - minSpan)
      }
      CropHandle.BOTTOM_LEFT -> {
        left = (left + deltaXNorm).coerceIn(0f, right - minSpan)
        bottom = (bottom + deltaYNorm).coerceIn(top + minSpan, 1f)
      }
      CropHandle.BOTTOM_RIGHT -> {
        right = (right + deltaXNorm).coerceIn(left + minSpan, 1f)
        bottom = (bottom + deltaYNorm).coerceIn(top + minSpan, 1f)
      }
      CropHandle.LEFT -> {
        left = (left + deltaXNorm).coerceIn(0f, right - minSpan)
      }
      CropHandle.TOP -> {
        top = (top + deltaYNorm).coerceIn(0f, bottom - minSpan)
      }
      CropHandle.RIGHT -> {
        right = (right + deltaXNorm).coerceIn(left + minSpan, 1f)
      }
      CropHandle.BOTTOM -> {
        bottom = (bottom + deltaYNorm).coerceIn(top + minSpan, 1f)
      }
      CropHandle.MOVE -> Unit
    }

    if (targetAspectRatio != null && targetAspectRatio > 0.1f) {
      val curW = right - left
      val desiredH = (curW / targetAspectRatio).coerceIn(minSpan, 1f)
      if (handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT || handle == CropHandle.TOP) {
        top = (bottom - desiredH).coerceIn(0f, bottom - minSpan)
      } else {
        bottom = (top + desiredH).coerceIn(top + minSpan, 1f)
      }
      val actualH = bottom - top
      val alignedW = (actualH * targetAspectRatio).coerceIn(minSpan, 1f)
      if (handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT || handle == CropHandle.LEFT) {
        left = (right - alignedW).coerceIn(0f, right - minSpan)
      } else {
        right = (left + alignedW).coerceIn(left + minSpan, 1f)
      }
    }

    return NormBoundingBox(left = left, top = top, right = right, bottom = bottom)
  }

  fun enforceAspectRatio(
    box: NormBoundingBox,
    targetAspectRatio: Float?,
    minSpan: Float = FreehandPathProcessor.MIN_SPAN,
  ): NormBoundingBox {
    if (targetAspectRatio == null || targetAspectRatio <= 0.1f) return box
    val cx = (box.left + box.right) * 0.5f
    val cy = (box.top + box.bottom) * 0.5f
    var w = (box.right - box.left).coerceAtLeast(minSpan)
    var h = (w / targetAspectRatio).coerceAtLeast(minSpan)
    if (h > 0.92f) {
      h = 0.92f
      w = (h * targetAspectRatio).coerceIn(minSpan, 0.96f)
    }
    if (w > 0.96f) {
      w = 0.96f
      h = (w / targetAspectRatio).coerceIn(minSpan, 0.92f)
    }
    val left = (cx - w * 0.5f).coerceIn(0f, 1f - w)
    val top = (cy - h * 0.5f).coerceIn(0f, 1f - h)
    return NormBoundingBox(left = left, top = top, right = left + w, bottom = top + h)
  }

  private fun rectContour(box: NormBoundingBox): List<NormPoint> =
    listOf(
      NormPoint(box.left, box.top),
      NormPoint(box.right, box.top),
      NormPoint(box.right, box.bottom),
      NormPoint(box.left, box.bottom),
      NormPoint(box.left, box.top),
    )
}
