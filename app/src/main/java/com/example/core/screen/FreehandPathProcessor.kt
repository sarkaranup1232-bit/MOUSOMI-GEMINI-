package com.example.core.screen

import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Processes raw freehand strokes:
 * - Resamples & Douglas-Peucker simplifies jittery finger input into smooth paths.
 * - Detects closed-loop vs lasso selections via start-end proximity or angular sweep.
 * - Extracts crisp clamped normalized bounding boxes with minimum legible dimensions.
 */
@Singleton
class FreehandPathProcessor @Inject constructor() {

  fun smoothPath(
    rawPoints: List<NormPoint>,
    minSampleDistance: Float = 0.008f,
    simplificationEpsilon: Float = 0.006f,
  ): List<NormPoint> {
    if (rawPoints.size <= 2) return rawPoints.map { clampPoint(it) }
    val resampled = mutableListOf(clampPoint(rawPoints.first()))
    for (i in 1 until rawPoints.size) {
      val pt = clampPoint(rawPoints[i])
      val prev = resampled.last()
      if (hypot(pt.x - prev.x, pt.y - prev.y) >= minSampleDistance) {
        resampled.add(pt)
      }
    }
    val last = clampPoint(rawPoints.last())
    if (resampled.last() != last) {
      resampled.add(last)
    }
    if (resampled.size <= 3) return resampled
    return douglasPeucker(resampled, simplificationEpsilon)
  }

  fun isClosedLoop(points: List<NormPoint>): Boolean {
    if (points.size < 6) return false
    var minX = 1f
    var minY = 1f
    var maxX = 0f
    var maxY = 0f
    points.forEach { p ->
      if (p.x < minX) minX = p.x
      if (p.y < minY) minY = p.y
      if (p.x > maxX) maxX = p.x
      if (p.y > maxY) maxY = p.y
    }
    val diag = hypot(maxX - minX, maxY - minY).coerceAtLeast(0.001f)
    val endGap = hypot(points.last().x - points.first().x, points.last().y - points.first().y)
    if (endGap <= diag * 0.34f) return true

    // Fallback angular sweep around centroid
    val cx = (minX + maxX) * 0.5f
    val cy = (minY + maxY) * 0.5f
    var sweep = 0.0
    var prevAngle = atan2((points.first().y - cy).toDouble(), (points.first().x - cx).toDouble())
    for (i in 1 until points.size) {
      val angle = atan2((points[i].y - cy).toDouble(), (points[i].x - cx).toDouble())
      var delta = angle - prevAngle
      while (delta > Math.PI) delta -= 2.0 * Math.PI
      while (delta < -Math.PI) delta += 2.0 * Math.PI
      sweep += delta
      prevAngle = angle
    }
    return abs(sweep) >= Math.toRadians(225.0)
  }

  fun processFreehand(
    rawPoints: List<NormPoint>,
    paddingFraction: Float = 0.012f,
  ): SelectionGeometryResult? {
    if (rawPoints.isEmpty()) return null
    val smoothed = smoothPath(rawPoints)
    var minX = 1f
    var minY = 1f
    var maxX = 0f
    var maxY = 0f
    smoothed.forEach { p ->
      minX = min(minX, p.x)
      minY = min(minY, p.y)
      maxX = max(maxX, p.x)
      maxY = max(maxY, p.y)
    }

    val box = ensureMinimumReadableBox(minX, minY, maxX, maxY, paddingFraction)
    val closed = isClosedLoop(smoothed)
    return SelectionGeometryResult(
      boundingBox = box,
      detectedShape = DetectedRegionShape.FREEHAND_LOOP,
      simplifiedContour = smoothed,
      cornerRadiusFraction = 0.12f,
      confidence = if (closed) 0.94f else 0.78f,
      isClosedLoop = closed,
    )
  }

  private fun douglasPeucker(points: List<NormPoint>, epsilon: Float): List<NormPoint> {
    if (points.size < 3) return points
    val first = points.first()
    val last = points.last()
    var maxDist = 0f
    var index = 0
    for (i in 1 until points.size - 1) {
      val dist = perpendicularDistance(points[i], first, last)
      if (dist > maxDist) {
        maxDist = dist
        index = i
      }
    }
    return if (maxDist > epsilon) {
      val left = douglasPeucker(points.subList(0, index + 1), epsilon)
      val right = douglasPeucker(points.subList(index, points.size), epsilon)
      left.dropLast(1) + right
    } else {
      listOf(first, last)
    }
  }

  private fun perpendicularDistance(p: NormPoint, a: NormPoint, b: NormPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len < 1e-5f) return hypot(p.x - a.x, p.y - a.y)
    return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / len
  }

  private fun clampPoint(p: NormPoint): NormPoint =
    NormPoint(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))

  companion object {
    const val MIN_SPAN = 0.06f

    fun ensureMinimumReadableBox(
      minX: Float,
      minY: Float,
      maxX: Float,
      maxY: Float,
      padding: Float,
    ): NormBoundingBox {
      val pad = padding.coerceIn(0f, 0.08f)
      var left = (minX - pad).coerceIn(0f, 1f - MIN_SPAN)
      var top = (minY - pad).coerceIn(0f, 1f - MIN_SPAN)
      var right = (maxX + pad).coerceIn(MIN_SPAN, 1f)
      var bottom = (maxY + pad).coerceIn(MIN_SPAN, 1f)

      if (right - left < MIN_SPAN) {
        val cx = (left + right) * 0.5f
        left = (cx - MIN_SPAN * 0.5f).coerceIn(0f, 1f - MIN_SPAN)
        right = (left + MIN_SPAN).coerceAtMost(1f)
      }
      if (bottom - top < MIN_SPAN) {
        val cy = (top + bottom) * 0.5f
        top = (cy - MIN_SPAN * 0.5f).coerceIn(0f, 1f - MIN_SPAN)
        bottom = (top + MIN_SPAN).coerceAtMost(1f)
      }
      return NormBoundingBox(left = left, top = top, right = right, bottom = bottom)
    }
  }
}
