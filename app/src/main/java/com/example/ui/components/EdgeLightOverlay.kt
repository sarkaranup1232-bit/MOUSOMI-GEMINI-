package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.edge.EdgeLightRenderParams

/**
 * Bright, symmetric four-side Gemini-style perimeter edge lighting overlay.
 * - Uses API 31+ hardware RoundedCorner radii when available for exact top & bottom corner alignment.
 * - Supports edge-activation line bloom (e.g. from bottom corner / side) expanding smoothly into
 *   the full perimeter glow, and smooth alpha fade-out when the session stops.
 * - Completely non-touch-blocking and lifecycle-aware.
 */
@Composable
fun EdgeLightPerimeterOverlay(
  params: EdgeLightRenderParams,
  modifier: Modifier = Modifier,
  perCornerRadiiPx: FloatArray? = null,
) {
  val visibilityAlpha by
    animateFloatAsState(
      targetValue = if (params.isVisible) 1f else 0f,
      animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
      label = "edge_light_visibility_alpha",
    )

  if (visibilityAlpha <= 0.005f) return

  val lifecycleOwner = LocalLifecycleOwner.current
  val localView = LocalView.current
  val density = LocalDensity.current.density
  var isAppForeground by remember { mutableStateOf(true) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> isAppForeground = true
        Lifecycle.Event.ON_PAUSE -> isAppForeground = false
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Trigger edge-activation reveal whenever activationSignal token changes
  val revealProgress = remember { Animatable(1f) }
  LaunchedEffect(params.activationSignal.token) {
    if (params.activationSignal.token > 0L && params.activationSignal.edge != EdgeActivationEdge.NONE) {
      revealProgress.snapTo(0.02f)
      revealProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
      )
    } else {
      revealProgress.snapTo(1f)
    }
  }

  val shouldAnimate = isAppForeground && params.speedMultiplier > 0f
  val transition = rememberInfiniteTransition(label = "edge_light_transition")
  val durationMs = (3200f / params.speedMultiplier.coerceAtLeast(0.25f)).toInt().coerceIn(950, 9000)

  val phase by
    if (shouldAnimate) {
      transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
          ),
        label = "edge_phase",
      )
    } else {
      remember { mutableStateOf(32f) }
    }

  val pulse by
    if (shouldAnimate) {
      transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = (durationMs / 2).coerceIn(700, 2800), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "edge_pulse",
      )
    } else {
      remember { mutableStateOf(0.96f) }
    }

  val intColors =
    remember(params.colorArgs) {
      val list = params.colorArgs.map { Color(it).toArgb() }
      when {
        list.size >= 2 && list.first() == list.last() -> list.toIntArray()
        list.size >= 2 -> (list + list.first()).toIntArray()
        else ->
          intArrayOf(
            0xFF22D3EE.toInt(),
            0xFF3B82F6.toInt(),
            0xFF8B5CF6.toInt(),
            0xFFA855F7.toInt(),
            0xFF22D3EE.toInt(),
          )
      }
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
  val rectF = remember { RectF() }
  val borderPath = remember { Path() }
  val pathMeasure = remember { android.graphics.PathMeasure() }
  val revealPath = remember { Path() }

  // Query hardware rounded corners once per config change when running inside Android window
  val resolvedCornerRadii4: FloatArray =
    remember(perCornerRadiiPx, params.cornerRadiusDp, density) {
      if (perCornerRadiiPx != null && perCornerRadiiPx.size == 4) {
        perCornerRadiiPx
      } else {
        val fallback = params.cornerRadiusDp * density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val ri = localView.rootWindowInsets
          val tl = ri?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()?.takeIf { it > 4f } ?: fallback
          val tr = ri?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius?.toFloat()?.takeIf { it > 4f } ?: fallback
          val br = ri?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius?.toFloat()?.takeIf { it > 4f } ?: fallback
          val bl = ri?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.toFloat()?.takeIf { it > 4f } ?: fallback
          floatArrayOf(tl, tr, br, bl)
        } else {
          floatArrayOf(fallback, fallback, fallback, fallback)
        }
      }
    }

  Canvas(modifier = modifier.fillMaxSize().testTag("edge_light_perimeter_canvas")) {
    val w = size.width
    val h = size.height
    if (w <= 4f || h <= 4f) return@Canvas

    val cx = w / 2f
    val cy = h / 2f
    val baseStrokePx = (params.thicknessDp * this.density).coerceIn(3f, 26f)
    val glowScale = params.glowScale.coerceIn(0.6f, 1.6f)
    val effectiveAlpha =
      (params.brightness * visibilityAlpha * pulse * (1f + params.voiceReactiveBoost * 0.18f))
        .coerceIn(0.02f, 1.0f)

    val shader: Shader =
      if (params.breathingOnly) {
        LinearGradient(0f, 0f, w, h, intColors, null, Shader.TileMode.CLAMP)
      } else {
        SweepGradient(0f, 0f, intColors, null).apply {
          shaderMatrix.reset()
          shaderMatrix.setRotate(phase)
          shaderMatrix.postScale(w / 2f, h / 2f)
          shaderMatrix.postTranslate(cx, cy)
          setLocalMatrix(shaderMatrix)
        }
      }

    paint.shader = shader
    val rev = revealProgress.value.coerceIn(0f, 1f)

    drawIntoCanvas { canvas ->
      val nc = canvas.nativeCanvas

      fun drawSymmetricRim(
        strokeWidthPx: Float,
        blurRadiusPx: Float,
        alphaScale: Float,
        insetPx: Float,
      ) {
        paint.strokeWidth = strokeWidthPx
        paint.alpha = (effectiveAlpha * alphaScale * 255f).toInt().coerceIn(2, 255)
        paint.maskFilter =
          if (blurRadiusPx > 0.5f) BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
          else null

        val safeInset = insetPx.coerceIn(2f, minOf(w, h) * 0.16f)
        rectF.set(
          safeInset,
          safeInset,
          (w - safeInset).coerceAtLeast(safeInset + 2f),
          (h - safeInset).coerceAtLeast(safeInset + 2f),
        )
        val maxCorner = minOf(rectF.width(), rectF.height()) * 0.45f
        val rTL = (resolvedCornerRadii4[0] - safeInset).coerceIn(4f, maxCorner)
        val rTR = (resolvedCornerRadii4[1] - safeInset).coerceIn(4f, maxCorner)
        val rBR = (resolvedCornerRadii4[2] - safeInset).coerceIn(4f, maxCorner)
        val rBL = (resolvedCornerRadii4[3] - safeInset).coerceIn(4f, maxCorner)
        val radii8 = floatArrayOf(rTL, rTL, rTR, rTR, rBR, rBR, rBL, rBL)

        borderPath.reset()
        borderPath.addRoundRect(rectF, radii8, Path.Direction.CW)

        if (rev >= 0.995f) {
          nc.drawPath(borderPath, paint)
        } else {
          // Smooth reveal: bright trigger line expanding symmetrically around full perimeter
          pathMeasure.setPath(borderPath, true)
          val totalLen = pathMeasure.length
          if (totalLen > 1f) {
            val anchorFrac =
              when (params.activationSignal.edge) {
                EdgeActivationEdge.LEFT -> 0.72f
                EdgeActivationEdge.RIGHT -> 0.25f
                EdgeActivationEdge.BOTTOM,
                EdgeActivationEdge.BOTTOM_CORNER,
                EdgeActivationEdge.NONE -> 0.48f
              }
            val anchorDist = totalLen * anchorFrac
            val halfSpan = (totalLen * 0.5f) * rev.coerceAtLeast(0.08f)
            val startD = anchorDist - halfSpan
            val endD = anchorDist + halfSpan
            revealPath.reset()
            if (startD < 0f) {
              pathMeasure.getSegment(totalLen + startD, totalLen, revealPath, true)
              pathMeasure.getSegment(0f, endD.coerceAtMost(totalLen), revealPath, false)
            } else if (endD > totalLen) {
              pathMeasure.getSegment(startD, totalLen, revealPath, true)
              pathMeasure.getSegment(0f, endD - totalLen, revealPath, false)
            } else {
              pathMeasure.getSegment(startD, endD, revealPath, true)
            }
            nc.drawPath(revealPath, paint)
          }
        }
      }

      // Layer 1: Wide diffused inward bloom
      if (params.glowLayerCount >= 3) {
        drawSymmetricRim(
          strokeWidthPx = baseStrokePx * 3.8f * glowScale,
          blurRadiusPx = baseStrokePx * 3.1f * glowScale,
          alphaScale = 0.34f,
          insetPx = (baseStrokePx * 1.35f).coerceAtLeast(7f),
        )
      }

      // Layer 2: Balanced mid-glow wrapping all 4 rounded corners smoothly
      if (params.glowLayerCount >= 2) {
        drawSymmetricRim(
          strokeWidthPx = baseStrokePx * 2.15f * glowScale,
          blurRadiusPx = baseStrokePx * 1.45f * glowScale,
          alphaScale = 0.62f,
          insetPx = (baseStrokePx * 0.85f).coerceAtLeast(5f),
        )
      }

      // Layer 3: Bright crisp inner neon core aligned cleanly on both left/right and bottom corners
      drawSymmetricRim(
        strokeWidthPx = baseStrokePx * 1.0f,
        blurRadiusPx = baseStrokePx * 0.35f,
        alphaScale = 0.98f,
        insetPx = (baseStrokePx * 0.48f).coerceAtLeast(3f),
      )
    }
  }
}
