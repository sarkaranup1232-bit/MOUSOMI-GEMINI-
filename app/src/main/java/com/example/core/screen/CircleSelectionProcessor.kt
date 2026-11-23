package com.example.core.screen

import com.example.domain.model.NormPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Fits circles and ovals to user strokes (both explicit Circle tool drags from center-to-edge
 * and freehand circular/oval gestures), returning refined enclosed crop boundaries.
 */
@Singleton
class CircleSelectionProcessor @Inject constructor() {

  /**
   * Interprets a drag from center [center] to perimeter [edgePoint] as an explicit circle/oval.
   */
  fun fromCenterRadiusDrag(
    center: NormPoint,
    edgePoint: NormPoint,
  ): SelectionGeometryResult {
    val radius = hypot(edgePoint.x - center.x, edgePoint.y - center.y).coerceAtLeast(0.04f)
    val box =
      FreehandPathProcessor.ensureMinimumReadableBox(
        minX = center.x - radius,
        minY = center.y - radius,
        maxX = center.x + radius,
        maxY = center.y + radius,
        padding = 0.004f,
      )
    val contour = sampleEllipseContour(
      cx = (box.left + box.right) * 0.5f,
      cy = (box.top + box.bottom) * 0.5f,
      rx = (box.right - box.left) * 0.5f,
      ry = (box.bottom - box.top) * 0.5f,
    )
    return SelectionGeometryResult(
      boundingBox = box,
      detectedShape = DetectedRegionShape.CIRCLE,
      simplifiedContour = contour,
      cornerRadiusFraction = 0.24f,
      confidence = 1.0f,
      isClosedLoop = true,
    )
  }

  /**
   * Evaluates whether a freehand point sequence resembles a circle or oval and computes a clean
   * fitted result + circularity confidence score in [0f..1f].
   */
  fun fitStrokeToCircleOrOval(points: List<NormPoint>): Pair<SelectionGeometryResult, Float>? {
    if (points.size < 6) return null
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

    val width = (maxX - minX).coerceAtLeast(0.001f)
    val height = (maxY - minY).coerceAtLeast(0.001f)
    val cx = (minX + maxX) * 0.5f
    val cy = (minY + maxY) * 0.5f
    val rx = width * 0.5f
    val ry = height * 0.5f

    // Normalized radial error against the fitted ellipse ((x-cx)/rx)^2 + ((y-cy)/ry)^2 == 1
    var meanRadialError = 0f
    points.forEach { p ->
      val nx = (p.x - cx) / rx
      val ny = (p.y - cy) / ry
      val radialDist = hypot(nx, ny)
      meanRadialError += abs(radialDist - 1.0f)
    }
    meanRadialError /= points.size

    val endGap = hypot(points.last().x - points.first().x, points.last().y - points.first().y)
    val diag = hypot(width, height)
    val closedBonus = if (endGap <= diag * 0.35f) 1.0f else 0.72f
    val score = ((1.0f - (meanRadialError * 1.65f)).coerceIn(0f, 1f)) * closedBonus

    val aspectRatio = width / height
    val isNearlyCircle = aspectRatio in 0.78f..1.28f
    val shape = if (isNearlyCircle) DetectedRegionShape.CIRCLE else DetectedRegionShape.OVAL

    val box = FreehandPathProcessor.ensureMinimumReadableBox(minX, minY, maxX, maxY, padding = 0.008f)
    val contour =
      sampleEllipseContour(
        cx = (box.left + box.right) * 0.5f,
        cy = (box.top + box.bottom) * 0.5f,
        rx = (box.right - box.left) * 0.5f,
        ry = (box.bottom - box.top) * 0.5f,
      )

    val result =
      SelectionGeometryResult(
        boundingBox = box,
        detectedShape = shape,
        simplifiedContour = contour,
        cornerRadiusFraction = if (isNearlyCircle) 0.22f else 0.16f,
        confidence = score,
        isClosedLoop = endGap <= diag * 0.35f,
      )
    return result to score
  }

  private fun sampleEllipseContour(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    steps: Int = 36,
  ): List<NormPoint> =
    (0..steps).map { idx ->
      val theta = (2.0 * PI * idx) / steps
      NormPoint(
        x = (cx + rx * cos(theta)).toFloat().coerceIn(0f, 1f),
        y = (cy + ry * sin(theta)).toFloat().coerceIn(0f, 1f),
      )
    }
}
