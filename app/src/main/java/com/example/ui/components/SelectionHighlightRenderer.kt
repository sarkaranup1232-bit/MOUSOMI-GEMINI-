package com.example.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import com.example.core.screen.CropHandle
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionGeometryResult
import com.example.core.screen.SelectionToolMode
import com.example.domain.model.NormPoint
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet

/**
 * Renders:
 * 1) Smooth glowing quadratic-Bezier drawing stroke + finger trailing halo while drawing.
 * 2) Spring-snapped bright cutout scrim (outer dimmed, inside 100% crystal bright) +
 *    animated NeonSelectionBorder with rounded corner brackets & interactive resize/move handles.
 */
@Composable
fun SelectionHighlightRenderer(
  toolMode: SelectionToolMode,
  activeStrokePoints: List<NormPoint>,
  snappedResult: SelectionGeometryResult?,
  geometryEngine: SelectionGeometryEngine,
  onStrokeStart: (NormPoint) -> Unit,
  onStrokeMove: (NormPoint) -> Unit,
  onStrokeEnd: () -> Unit,
  onHandleDrag: (CropHandle, Float, Float) -> Unit,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  dimBackgroundAlpha: Float = 0.56f,
) {
  val snappedBox = snappedResult?.boundingBox

  // Smooth spring animation so snapping into rounded frame feels liquid like the reference video
  val animLeft by
    animateFloatAsState(
      targetValue = snappedBox?.left ?: 0.5f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
      label = "snap_left",
    )
  val animTop by
    animateFloatAsState(
      targetValue = snappedBox?.top ?: 0.5f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
      label = "snap_top",
    )
  val animRight by
    animateFloatAsState(
      targetValue = snappedBox?.right ?: 0.5f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
      label = "snap_right",
    )
  val animBottom by
    animateFloatAsState(
      targetValue = snappedBox?.bottom ?: 0.5f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
      label = "snap_bottom",
    )

  val infiniteTransition = rememberInfiniteTransition(label = "highlight_neon_anim")
  val sweepAngle by
    if (!reducedMotion) {
      infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "highlight_sweep",
      )
    } else {
      remember { mutableFloatStateOf(30f) }
    }

  val pulseAlpha by
    if (!reducedMotion) {
      infiniteTransition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1350), RepeatMode.Reverse),
        label = "highlight_pulse",
      )
    } else {
      remember { mutableFloatStateOf(0.92f) }
    }

  val paint =
    remember {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
      }
    }
  val shaderMatrix = remember { Matrix() }
  val nativeRect = remember { RectF() }

  Canvas(
    modifier =
      modifier
        .fillMaxSize()
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .selectionDrawingGesture(
          enabled = true,
          toolMode = toolMode,
          currentSnappedBox = snappedBox,
          geometryEngine = geometryEngine,
          onStrokeStart = onStrokeStart,
          onStrokeMove = onStrokeMove,
          onStrokeEnd = onStrokeEnd,
          onHandleDrag = onHandleDrag,
        )
        .testTag("selection_highlight_canvas")
  ) {
    val w = size.width
    val h = size.height
    if (w < 8f || h < 8f) return@Canvas

    if (snappedBox != null) {
      val rect =
        Rect(
          left = (animLeft * w).coerceIn(0f, w - 4f),
          top = (animTop * h).coerceIn(0f, h - 4f),
          right = (animRight * w).coerceIn((animLeft * w) + 4f, w),
          bottom = (animBottom * h).coerceIn((animTop * h) + 4f, h),
        )
      val cornerPx =
        (minOf(rect.width, rect.height) * (snappedResult?.cornerRadiusFraction ?: 0.1f))
          .coerceIn(14f * density, 26f * density)

      // Subtle dark outer scrim while keeping the inside region 100% crystal-clear bright
      drawRect(color = Color.Black.copy(alpha = dimBackgroundAlpha), size = size)
      drawRoundRect(
        color = Color.Transparent,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        blendMode = BlendMode.Clear,
      )

      // Subtle internal tint pulse inside border edge without obscuring content
      drawRoundRect(
        color = NeonCyan.copy(alpha = 0.04f),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(cornerPx, cornerPx),
      )

      drawNeonSelectionFrame(
        rect = rect,
        cornerRadiusPx = cornerPx,
        sweepAngleDeg = sweepAngle,
        pulseAlpha = pulseAlpha,
        paint = paint,
        shaderMatrix = shaderMatrix,
        nativeRect = nativeRect,
      )
    } else if (activeStrokePoints.isNotEmpty()) {
      // Very light ambient dim so the bright glowing drawing finger stroke stands out sharply
      drawRect(color = Color.Black.copy(alpha = 0.24f), size = size)

      val pts = activeStrokePoints.map { Offset(it.x * w, it.y * h) }
      if (toolMode == SelectionToolMode.RECTANGLE && pts.size >= 2) {
        val left = minOf(pts.first().x, pts.last().x)
        val top = minOf(pts.first().y, pts.last().y)
        val right = maxOf(pts.first().x, pts.last().x).coerceAtLeast(left + 8f)
        val bottom = maxOf(pts.first().y, pts.last().y).coerceAtLeast(top + 8f)
        val previewRect = Rect(left, top, right, bottom)
        drawRoundRect(
          color = Color.Transparent,
          topLeft = previewRect.topLeft,
          size = previewRect.size,
          cornerRadius = CornerRadius(18f * density, 18f * density),
          blendMode = BlendMode.Clear,
        )
        drawNeonSelectionFrame(
          rect = previewRect,
          cornerRadiusPx = 18f * density,
          sweepAngleDeg = sweepAngle,
          pulseAlpha = 0.95f,
          paint = paint,
          shaderMatrix = shaderMatrix,
          nativeRect = nativeRect,
          showSideResizePills = false,
        )
      } else if (toolMode == SelectionToolMode.CIRCLE && pts.size >= 2) {
        val center = pts.first()
        val edge = pts.last()
        val radius = kotlin.math.hypot(edge.x - center.x, edge.y - center.y).coerceAtLeast(16f)
        val circleRect =
          Rect(
            left = (center.x - radius).coerceAtLeast(0f),
            top = (center.y - radius).coerceAtLeast(0f),
            right = (center.x + radius).coerceAtMost(w),
            bottom = (center.y + radius).coerceAtMost(h),
          )
        drawOval(
          color = Color.Transparent,
          topLeft = circleRect.topLeft,
          size = circleRect.size,
          blendMode = BlendMode.Clear,
        )
        drawOval(
          brush = Brush.linearGradient(listOf(NeonCyan, ElectricBlue, NeonViolet, NeonMagenta)),
          topLeft = circleRect.topLeft,
          size = circleRect.size,
          style = Stroke(width = 4f * density),
        )
      } else {
        val smoothPath = buildSmoothQuadraticPath(pts)
        // Outer luminous cyan-violet halo
        drawPath(
          path = smoothPath,
          brush = Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.55f), ElectricBlue.copy(alpha = 0.55f), NeonMagenta.copy(alpha = 0.55f))),
          style = Stroke(width = 14f * density, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Crisp glowing white-cyan inner core
        drawPath(
          path = smoothPath,
          color = Color.White.copy(alpha = 0.96f),
          style = Stroke(width = 4.6f * density, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Soft finger trailing halo dot at fingertip
        val tip = pts.last()
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(NeonCyan.copy(alpha = 0.65f), NeonViolet.copy(alpha = 0.25f), Color.Transparent),
            center = tip,
            radius = 22f * density,
          ),
          radius = 22f * density,
          center = tip,
        )
        drawCircle(color = Color.White, radius = 4.5f * density, center = tip)
      }
    } else {
      // Ready-to-draw ambient scrim (very light so underlying content is crisp & readable)
      drawRect(color = Color.Black.copy(alpha = 0.14f), size = size)
    }
  }
}

private fun buildSmoothQuadraticPath(points: List<Offset>): Path {
  val path = Path()
  if (points.isEmpty()) return path
  path.moveTo(points.first().x, points.first().y)
  if (points.size == 1) {
    path.lineTo(points.first().x + 0.5f, points.first().y + 0.5f)
    return path
  }
  for (i in 1 until points.size - 1) {
    val midX = (points[i].x + points[i + 1].x) * 0.5f
    val midY = (points[i].y + points[i + 1].y) * 0.5f
    path.quadraticTo(points[i].x, points[i].y, midX, midY)
  }
  path.lineTo(points.last().x, points.last().y)
  return path
}
