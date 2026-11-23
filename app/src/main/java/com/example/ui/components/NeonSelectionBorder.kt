package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonViolet

private val SelectionNeonColors =
  intArrayOf(
    NeonCyan.toArgb(),
    ElectricBlue.toArgb(),
    NeonViolet.toArgb(),
    NeonPurple.toArgb(),
    NeonMagenta.toArgb(),
    NeonCyan.toArgb(),
  )

/**
 * Draws the subtle, continuous multicolor neon perimeter edge glow when Live Screen Selection Mode
 * is active across the screen edges.
 */
@Composable
fun SelectionScreenEdgeGlow(
  visible: Boolean,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
) {
  if (!visible) return
  val transition = rememberInfiniteTransition(label = "selection_screen_edge_glow")
  val phase by
    if (!reducedMotion) {
      transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "selection_edge_phase",
      )
    } else {
      remember { androidx.compose.runtime.mutableFloatStateOf(24f) }
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
  val matrix = remember { Matrix() }
  val rectF = remember { RectF() }

  Canvas(modifier = modifier.fillMaxSize().testTag("selection_screen_edge_glow")) {
    val w = size.width
    val h = size.height
    if (w < 12f || h < 12f) return@Canvas
    val strokePx = 4.2f * density
    val cornerPx = 30f * density
    val shader = SweepGradient(0f, 0f, SelectionNeonColors, null)
    matrix.reset()
    matrix.setRotate(phase)
    matrix.postScale(w * 0.5f, h * 0.5f)
    matrix.postTranslate(w * 0.5f, h * 0.5f)
    shader.setLocalMatrix(matrix)
    paint.shader = shader

    drawIntoCanvas { canvas ->
      val nc = canvas.nativeCanvas
      val insetOuter = strokePx * 2.2f
      rectF.set(insetOuter, insetOuter, w - insetOuter, h - insetOuter)
      paint.strokeWidth = strokePx * 3.8f
      paint.alpha = 88
      paint.maskFilter = BlurMaskFilter(strokePx * 3.0f, BlurMaskFilter.Blur.NORMAL)
      nc.drawRoundRect(rectF, cornerPx, cornerPx, paint)

      val insetInner = strokePx * 0.9f
      rectF.set(insetInner, insetInner, w - insetInner, h - insetInner)
      paint.strokeWidth = strokePx * 1.15f
      paint.alpha = 210
      paint.maskFilter = null
      nc.drawRoundRect(rectF, cornerPx, cornerPx, paint)
    }
  }
}

/**
 * Renders the glowing multicolor continuous neon border + four luminous rounded corner brackets
 * around a snapped selection region inside a [DrawScope].
 */
fun DrawScope.drawNeonSelectionFrame(
  rect: Rect,
  cornerRadiusPx: Float,
  sweepAngleDeg: Float,
  pulseAlpha: Float,
  paint: Paint,
  shaderMatrix: Matrix,
  nativeRect: RectF,
  showSideResizePills: Boolean = true,
) {
  if (rect.width < 12f || rect.height < 12f) return

  val cx = rect.center.x
  val cy = rect.center.y
  val safeCorner = cornerRadiusPx.coerceIn(10f, minOf(rect.width, rect.height) * 0.38f)

  val shader = SweepGradient(0f, 0f, SelectionNeonColors, null)
  shaderMatrix.reset()
  shaderMatrix.setRotate(sweepAngleDeg)
  shaderMatrix.postScale((rect.width * 0.5f).coerceAtLeast(8f), (rect.height * 0.5f).coerceAtLeast(8f))
  shaderMatrix.postTranslate(cx, cy)
  shader.setLocalMatrix(shaderMatrix)
  paint.shader = shader

  nativeRect.set(rect.left, rect.top, rect.right, rect.bottom)

  drawIntoCanvas { canvas ->
    val nc = canvas.nativeCanvas

    // Layer 1: Diffused outer neon bloom
    paint.strokeWidth = 6.5f * density
    paint.alpha = (pulseAlpha * 0.42f * 255f).toInt().coerceIn(24, 200)
    paint.maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
    nc.drawRoundRect(nativeRect, safeCorner, safeCorner, paint)

    // Layer 2: Crisp continuous neon frame
    paint.strokeWidth = 2.4f * density
    paint.alpha = (pulseAlpha * 0.95f * 255f).toInt().coerceIn(80, 255)
    paint.maskFilter = null
    nc.drawRoundRect(nativeRect, safeCorner, safeCorner, paint)
  }

  // Layer 3: Four luminous rounded white-cyan corner brackets matching reference video
  val bracketLen = minOf(rect.width * 0.24f, rect.height * 0.24f, 24f * density).coerceAtLeast(10f * density)
  val bracketStroke = 3.6f * density
  val bracketRadius = safeCorner.coerceAtLeast(12f * density)

  fun drawCornerBracket(start: Offset, corner: Offset, end: Offset) {
    val path = Path().apply {
      moveTo(start.x, start.y)
      quadraticTo(corner.x, corner.y, end.x, end.y)
    }
    drawPath(
      path = path,
      color = NeonCyan.copy(alpha = 0.45f),
      style = Stroke(width = bracketStroke * 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawPath(
      path = path,
      color = Color.White.copy(alpha = 0.96f),
      style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }

  val bOff = bracketRadius * 0.65f
  // Top-left
  drawCornerBracket(
    start = Offset(rect.left, rect.top + bracketLen),
    corner = Offset(rect.left, rect.top),
    end = Offset(rect.left + bracketLen, rect.top),
  )
  // Top-right
  drawCornerBracket(
    start = Offset(rect.right - bracketLen, rect.top),
    corner = Offset(rect.right, rect.top),
    end = Offset(rect.right, rect.top + bracketLen),
  )
  // Bottom-left
  drawCornerBracket(
    start = Offset(rect.left, rect.bottom - bracketLen),
    corner = Offset(rect.left, rect.bottom),
    end = Offset(rect.left + bracketLen, rect.bottom),
  )
  // Bottom-right
  drawCornerBracket(
    start = Offset(rect.right - bracketLen, rect.bottom),
    corner = Offset(rect.right, rect.bottom),
    end = Offset(rect.right, rect.bottom - bracketLen),
  )

  if (showSideResizePills && rect.width > 80f * density && rect.height > 60f * density && bOff > 0f) {
    val pillColor = Color.White.copy(alpha = 0.72f)
    val pillHalf = 10f * density
    val pillThickness = 2.6f * density
    drawLine(
      color = pillColor,
      start = Offset(rect.center.x - pillHalf, rect.top),
      end = Offset(rect.center.x + pillHalf, rect.top),
      strokeWidth = pillThickness,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = pillColor,
      start = Offset(rect.center.x - pillHalf, rect.bottom),
      end = Offset(rect.center.x + pillHalf, rect.bottom),
      strokeWidth = pillThickness,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = pillColor,
      start = Offset(rect.left, rect.center.y - pillHalf),
      end = Offset(rect.left, rect.center.y + pillHalf),
      strokeWidth = pillThickness,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = pillColor,
      start = Offset(rect.right, rect.center.y - pillHalf),
      end = Offset(rect.right, rect.center.y + pillHalf),
      strokeWidth = pillThickness,
      cap = StrokeCap.Round,
    )
  }
}
