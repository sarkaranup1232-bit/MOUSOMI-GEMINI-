package com.example.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic, GPU-friendly Google Gemini-inspired atmospheric lighting background.
 * Smoothly reveals on mount, then continuously flows blue, cyan, deep violet, purple,
 * and indigo smoky mist layers rising from the bottom of the screen without blocking touches.
 */
@Composable
fun GeminiSmokyAmbientBackground(
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  intensity: Float = 1.0f,
) {
  val revealProgress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    revealProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
    )
  }

  val infinite = rememberInfiniteTransition(label = "gemini_smoky_ambient")
  val phasePrimary by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = (2.0 * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 14000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "smoky_phase_primary",
    )
  val phaseSecondary by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = (2.0 * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 19500, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "smoky_phase_secondary",
    )
  val phaseVertical by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = (2.0 * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 11200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "smoky_phase_vertical",
    )

  val t1 = if (reducedMotion) 0.4f else phasePrimary
  val t2 = if (reducedMotion) 1.1f else phaseSecondary
  val t3 = if (reducedMotion) 0.8f else phaseVertical
  val reveal = revealProgress.value.coerceIn(0f, 1f)
  val effectiveIntensity = intensity.coerceIn(0.4f, 1.35f) * reveal

  val blurModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Modifier.blur(44.dp)
    } else {
      Modifier
    }

  Box(
    modifier =
      modifier
        .background(Color(0xFF02040B))
        .testTag("gemini_smoky_ambient_background")
  ) {
    // Base deep indigo-to-black atmospheric wash rising from bottom
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      if (w <= 1f || h <= 1f) return@Canvas

      drawRect(
        brush =
          Brush.verticalGradient(
            0.00f to Color(0xFF02040B),
            0.34f to Color(0xFF040714),
            0.62f to Color(0xFF0B112E).copy(alpha = (0.72f * reveal).coerceIn(0f, 1f)),
            0.84f to Color(0xFF17184A).copy(alpha = (0.90f * reveal).coerceIn(0f, 1f)),
            1.00f to Color(0xFF11183D).copy(alpha = (0.95f * reveal).coerceIn(0f, 1f)),
          )
      )
    }

    // Blurred liquid atmospheric clouds rising and drifting organically from the bottom
    Canvas(modifier = Modifier.fillMaxSize().then(blurModifier)) {
      val w = size.width
      val h = size.height
      if (w <= 1f || h <= 1f) return@Canvas

      val riseOffset = (1f - reveal) * (h * 0.24f)

      // 1. Deep Indigo / Midnight Violet Left-Center Rising Cloud
      drawSmokyPlume(
        center =
          Offset(
            x = w * (0.28f + 0.14f * sin(t1 * 1.0f + 0.4f)),
            y = h * (0.82f - 0.08f * sin(t3)) + riseOffset,
          ),
        radius = maxOf(w, h) * (0.58f + 0.06f * cos(t2)),
        coreColor = Color(0xFF312E81),
        midColor = Color(0xFF4C1D95),
        alpha = 0.56f * effectiveIntensity,
      )

      // 2. Rich Violet / Purple Right-Side Drifting Fog
      drawSmokyPlume(
        center =
          Offset(
            x = w * (0.76f + 0.13f * cos(t2 + 0.9f)),
            y = h * (0.76f - 0.10f * cos(t3 * 1.0f + 1.2f)) + riseOffset,
          ),
        radius = maxOf(w, h) * (0.54f + 0.07f * sin(t1)),
        coreColor = Color(0xFF7C3AED),
        midColor = Color(0xFF4C1D95),
        alpha = 0.48f * effectiveIntensity,
      )

      // 3. Electric Royal Blue Core Illumination Rising Center-Bottom
      drawSmokyPlume(
        center =
          Offset(
            x = w * (0.50f + 0.16f * sin(t2 * 1.0f - 0.6f)),
            y = h * (0.88f - 0.09f * sin(t1 + 1.7f)) + riseOffset,
          ),
        radius = maxOf(w, h) * (0.52f + 0.05f * cos(t3)),
        coreColor = Color(0xFF2563EB),
        midColor = Color(0xFF1D4ED8),
        alpha = 0.62f * effectiveIntensity,
      )

      // 4. Luminous Cyan Atmospheric Mist Layer Expanding & Fading Upward
      drawSmokyPlume(
        center =
          Offset(
            x = w * (0.44f + 0.22f * cos(t1 + 2.1f)),
            y = h * (0.79f - 0.11f * sin(t2 + 0.8f)) + riseOffset,
          ),
        radius = maxOf(w, h) * (0.44f + 0.06f * sin(t3)),
        coreColor = Color(0xFF06B6D4),
        midColor = Color(0xFF0284C7),
        alpha = 0.44f * effectiveIntensity,
      )

      // 5. High-Drifting Soft Purple-Blue Veil near Mid-Screen Horizon
      drawSmokyPlume(
        center =
          Offset(
            x = w * (0.58f + 0.18f * sin(t1 * 1.0f + 3.0f)),
            y = h * (0.62f + 0.06f * cos(t2)) + riseOffset,
          ),
        radius = maxOf(w, h) * (0.48f + 0.05f * sin(t3 + 1.5f)),
        coreColor = Color(0xFF6366F1),
        midColor = Color(0xFF9333EA),
        alpha = 0.28f * effectiveIntensity,
      )
    }

    // Fine flowing smoky wisp ribbons & additive soft light diffusion near bottom
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      if (w <= 1f || h <= 1f) return@Canvas

      val riseOffset = (1f - reveal) * (h * 0.20f)
      val baseY = h * 0.84f + riseOffset

      val wispPath1 =
        Path().apply {
          moveTo(-w * 0.1f, h * 1.05f)
          cubicTo(
            w * (0.22f + 0.08f * sin(t1)),
            baseY - h * 0.14f,
            w * (0.55f + 0.09f * cos(t2)),
            baseY - h * 0.04f,
            w * 1.1f,
            baseY - h * 0.18f,
          )
          lineTo(w * 1.1f, h * 1.1f)
          close()
        }

      drawPath(
        path = wispPath1,
        brush =
          Brush.linearGradient(
            colors =
              listOf(
                Color(0xFF22D3EE).copy(alpha = (0.14f * effectiveIntensity).coerceIn(0f, 1f)),
                Color(0xFF3B82F6).copy(alpha = (0.18f * effectiveIntensity).coerceIn(0f, 1f)),
                Color(0xFF9333EA).copy(alpha = (0.16f * effectiveIntensity).coerceIn(0f, 1f)),
                Color.Transparent,
              ),
            start = Offset(0f, h),
            end = Offset(w, baseY - h * 0.20f),
          ),
        blendMode = BlendMode.Screen,
      )

      val wispPath2 =
        Path().apply {
          moveTo(-w * 0.1f, baseY - h * 0.08f)
          cubicTo(
            w * (0.30f + 0.10f * cos(t3)),
            baseY - h * 0.22f,
            w * (0.70f + 0.09f * sin(t1)),
            baseY - h * 0.05f,
            w * 1.1f,
            baseY - h * 0.11f,
          )
          lineTo(w * 1.1f, h * 1.1f)
          lineTo(-w * 0.1f, h * 1.1f)
          close()
        }

      drawPath(
        path = wispPath2,
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                Color(0xFF8B5CF6).copy(alpha = (0.19f * effectiveIntensity).coerceIn(0f, 1f)),
                Color(0xFF06B6D4).copy(alpha = (0.12f * effectiveIntensity).coerceIn(0f, 1f)),
                Color.Transparent,
              ),
            center = Offset(w * (0.5f + 0.15f * sin(t2)), h * 0.92f + riseOffset),
            radius = maxOf(w, h) * 0.72f,
          ),
        blendMode = BlendMode.Screen,
      )
    }
  }
}

private fun DrawScope.drawSmokyPlume(
  center: Offset,
  radius: Float,
  coreColor: Color,
  midColor: Color,
  alpha: Float,
) {
  val clampedAlpha = alpha.coerceIn(0f, 0.9f)
  if (clampedAlpha <= 0.005f || radius <= 2f) return
  drawCircle(
    brush =
      Brush.radialGradient(
        0.00f to coreColor.copy(alpha = clampedAlpha),
        0.34f to midColor.copy(alpha = clampedAlpha * 0.62f),
        0.68f to midColor.copy(alpha = clampedAlpha * 0.18f),
        1.00f to Color.Transparent,
        center = center,
        radius = radius,
      ),
    radius = radius,
    center = center,
    blendMode = BlendMode.Screen,
  )
}
