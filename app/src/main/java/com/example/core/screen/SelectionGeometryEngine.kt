package com.example.core.screen

import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Coordinates FreehandPathProcessor, CircleSelectionProcessor, and RectangleSelectionProcessor
 * to turn any rough finger stroke or drag gesture into a crisp, snapped rounded selection frame.
 */
@Singleton
class SelectionGeometryEngine @Inject constructor(
  val freehandProcessor: FreehandPathProcessor,
  val circleProcessor: CircleSelectionProcessor,
  val rectangleProcessor: RectangleSelectionProcessor,
) {

  fun processStroke(
    rawPoints: List<NormPoint>,
    toolMode: SelectionToolMode,
  ): SelectionGeometryResult? {
    if (rawPoints.isEmpty()) return null
    val first = rawPoints.first()
    val last = rawPoints.last()

    // Tap or tiny gesture expands into a smart readable box around tap point
    if (rawPoints.size == 1 || strokeSpan(rawPoints) < 0.025f) {
      val box =
        FreehandPathProcessor.ensureMinimumReadableBox(
          minX = first.x - 0.07f,
          minY = first.y - 0.05f,
          maxX = first.x + 0.07f,
          maxY = first.y + 0.05f,
          padding = 0f,
        )
      return SelectionGeometryResult(
        boundingBox = box,
        detectedShape = DetectedRegionShape.RECTANGLE,
        cornerRadiusFraction = 0.1f,
        confidence = 0.85f,
      )
    }

    return when (toolMode) {
      SelectionToolMode.RECTANGLE -> {
        val span = strokeSpan(rawPoints)
        val endGap = hypot(last.x - first.x, last.y - first.y)
        if (rawPoints.size >= 4 && endGap < span * 0.45f) {
          val minX = rawPoints.minOf { it.x }
          val minY = rawPoints.minOf { it.y }
          val maxX = rawPoints.maxOf { it.x }
          val maxY = rawPoints.maxOf { it.y }
          rectangleProcessor.fromDragCorners(NormPoint(minX, minY), NormPoint(maxX, maxY))
        } else {
          rectangleProcessor.fromDragCorners(first, last)
        }
      }
      SelectionToolMode.CIRCLE -> {
        // Support either center-out drag OR circular loop draw
        val pathSpan = rawPoints.size
        val circleFit = if (pathSpan >= 8) circleProcessor.fitStrokeToCircleOrOval(rawPoints) else null
        if (circleFit != null && circleFit.second >= 0.58f) {
          circleFit.first
        } else {
          circleProcessor.fromCenterRadiusDrag(first, last)
        }
      }
      SelectionToolMode.FREEHAND -> freehandProcessor.processFreehand(rawPoints)
      SelectionToolMode.SMART_LOOP -> classifyAndSnapSmartStroke(rawPoints)
    }
  }

  private fun classifyAndSnapSmartStroke(rawPoints: List<NormPoint>): SelectionGeometryResult? {
    val smoothed = freehandProcessor.smoothPath(rawPoints)
    val circleCandidate = circleProcessor.fitStrokeToCircleOrOval(smoothed)
    val rectScore = rectangleProcessor.scoreRectangularity(smoothed)
    val freehandCandidate = freehandProcessor.processFreehand(smoothed) ?: return null

    if (circleCandidate != null && circleCandidate.second >= 0.64f && circleCandidate.second >= rectScore) {
      return circleCandidate.first
    }
    if (rectScore >= 0.72f) {
      return SelectionGeometryResult(
        boundingBox = freehandCandidate.boundingBox,
        detectedShape = DetectedRegionShape.RECTANGLE,
        simplifiedContour = smoothed,
        cornerRadiusFraction = 0.08f,
        confidence = rectScore,
        isClosedLoop = freehandCandidate.isClosedLoop,
      )
    }
    return freehandCandidate
  }

  fun adjustSnappedResult(
    current: SelectionGeometryResult,
    handle: CropHandle,
    deltaXNorm: Float,
    deltaYNorm: Float,
    targetAspectRatio: Float? = null,
  ): SelectionGeometryResult {
    val updatedBox =
      rectangleProcessor.adjustBox(
        box = current.boundingBox,
        handle = handle,
        deltaXNorm = deltaXNorm,
        deltaYNorm = deltaYNorm,
        targetAspectRatio = targetAspectRatio,
      )
    return current.copy(boundingBox = updatedBox)
  }

  /**
   * Determines which interactive crop handle or interior move region a normalized touch hits.
   */
  fun hitTestHandle(
    point: NormPoint,
    box: NormBoundingBox,
    cornerTolerance: Float = 0.048f,
    edgeTolerance: Float = 0.036f,
  ): CropHandle? {
    if (hypot(point.x - box.left, point.y - box.top) <= cornerTolerance) return CropHandle.TOP_LEFT
    if (hypot(point.x - box.right, point.y - box.top) <= cornerTolerance) return CropHandle.TOP_RIGHT
    if (hypot(point.x - box.left, point.y - box.bottom) <= cornerTolerance) return CropHandle.BOTTOM_LEFT
    if (hypot(point.x - box.right, point.y - box.bottom) <= cornerTolerance) return CropHandle.BOTTOM_RIGHT

    val inVertSpan = point.y in (box.top - edgeTolerance)..(box.bottom + edgeTolerance)
    val inHorizSpan = point.x in (box.left - edgeTolerance)..(box.right + edgeTolerance)
    if (inVertSpan && abs(point.x - box.left) <= edgeTolerance) return CropHandle.LEFT
    if (inVertSpan && abs(point.x - box.right) <= edgeTolerance) return CropHandle.RIGHT
    if (inHorizSpan && abs(point.y - box.top) <= edgeTolerance) return CropHandle.TOP
    if (inHorizSpan && abs(point.y - box.bottom) <= edgeTolerance) return CropHandle.BOTTOM

    if (point.x in box.left..box.right && point.y in box.top..box.bottom) {
      return CropHandle.MOVE
    }
    return null
  }

  private fun strokeSpan(points: List<NormPoint>): Float {
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
    return hypot(maxX - minX, maxY - minY)
  }
}
