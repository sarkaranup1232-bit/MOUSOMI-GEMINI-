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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class CosmicStar(
  val xFraction: Float,
  val yFraction: Float,
  val radiusPx: Float,
  val phaseOffset: Float,
  val baseAlpha: Float,
  val tint: Color,
)

/**
 * Deep-space neon atmospheric backdrop matching the Mousomi AI Auth reference design:
 * subtle blue/violet nebula fog, scattered twinkling stars, a single tilted elliptical
 * neon orbital arc with an upper-right flare star, and a curved planetary horizon at the bottom.
 */
@Composable
fun DeepSpaceNeonBackground(
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
) {
  val reveal = remember { Animatable(0.35f) }
  LaunchedEffect(Unit) {
    reveal.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
    )
  }

  val infinite = rememberInfiniteTransition(label = "deep_space_neon")
  val driftPhase by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = (2.0 * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 18000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "nebula_drift",
    )
  val twinklePhase by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = (2.0 * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 5200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "star_twinkle",
    )

  val tDrift = if (reducedMotion) 0.45f else driftPhase
  val tTwinkle = if (reducedMotion) 0.8f else twinklePhase
  val revealAlpha = reveal.value.coerceIn(0f, 1f)

  val stars = remember {
    listOf(
      CosmicStar(0.11f, 0.07f, 2.2f, 0.3f, 0.72f, Color(0xFFBAE6FD)),
      CosmicStar(0.24f, 0.14f, 1.6f, 1.1f, 0.55f, Color.White),
      CosmicStar(0.41f, 0.06f, 1.8f, 2.2f, 0.62f, Color(0xFFDDD6FE)),
      CosmicStar(0.67f, 0.09f, 2.1f, 0.7f, 0.68f, Color(0xFF7DD3FC)),
      CosmicStar(0.84f, 0.05f, 1.7f, 2.8f, 0.60f, Color.White),
      CosmicStar(0.91f, 0.17f, 2.3f, 1.9f, 0.74f, Color(0xFFF5D0FE)),
      CosmicStar(0.08f, 0.22f, 1.5f, 2.5f, 0.50f, Color(0xFF93C5FD)),
      CosmicStar(0.34f, 0.19f, 1.4f, 0.4f, 0.45f, Color.White),
      CosmicStar(0.56f, 0.15f, 1.5f, 3.1f, 0.52f, Color(0xFFE9D5FF)),
      CosmicStar(0.14f, 0.81f, 1.9f, 1.4f, 0.68f, Color(0xFFC4B5FD)),
      CosmicStar(0.32f, 0.83f, 1.6f, 0.9f, 0.54f, Color(0xFF7DD3FC)),
      CosmicStar(0.71f, 0.80f, 2.0f, 2.0f, 0.72f, Color(0xFFF5D0FE)),
      CosmicStar(0.87f, 0.77f, 2.2f, 1.2f, 0.76f, Color(0xFFBAE6FD)),
      CosmicStar(0.93f, 0.64f, 1.5f, 2.7f, 0.48f, Color.White),
      CosmicStar(0.05f, 0.62f, 1.6f, 0.5f, 0.50f, Color(0xFF93C5FD)),
    )
  }

  val blurModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Modifier.blur(38.dp)
    } else {
      Modifier
    }

  Box(
    modifier =
      modifier
        .background(Color(0xFF02040C))
        .testTag("deep_space_neon_background")
  ) {
    // 1. Deep cosmos gradient wash
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(
        brush =
          Brush.verticalGradient(
            0.00f to Color(0xFF02040B),
            0.35f to Color(0xFF040817),
            0.70f to Color(0xFF060B22),
            0.90f to Color(0xFF0B163B),
            1.00f to Color(0xFF04091B),
          )
      )
    }

    // 2. Soft drifting blue / purple / magenta nebula fog clouds
    Canvas(modifier = Modifier.fillMaxSize().then(blurModifier)) {
      val w = size.width
      val h = size.height
      if (w <= 1f || h <= 1f) return@Canvas

      drawNebulaCloud(
        center = Offset(w * (0.20f + 0.04f * sin(tDrift)), h * (0.24f + 0.02f * cos(tDrift))),
        radius = maxOf(w, h) * 0.42f,
        core = Color(0xFF1D4ED8),
        edge = Color(0xFF0EA5E9),
        alpha = 0.24f * revealAlpha,
      )
      drawNebulaCloud(
        center = Offset(w * (0.78f + 0.04f * cos(tDrift)), h * (0.26f + 0.02f * sin(tDrift))),
        radius = maxOf(w, h) * 0.44f,
        core = Color(0xFF7E22CE),
        edge = Color(0xFFEC4899),
        alpha = 0.28f * revealAlpha,
      )
      drawNebulaCloud(
        center = Offset(w * 0.14f, h * 0.62f),
        radius = maxOf(w, h) * 0.36f,
        core = Color(0xFF0284C7),
        edge = Color(0xFF1E3A8A),
        alpha = 0.20f * revealAlpha,
      )
      drawNebulaCloud(
        center = Offset(w * 0.85f, h * 0.78f),
        radius = maxOf(w, h) * 0.40f,
        core = Color(0xFF9333EA),
        edge = Color(0xFFD946EF),
        alpha = 0.25f * revealAlpha,
      )
      drawNebulaCloud(
        center = Offset(w * 0.50f, h * 0.89f),
        radius = maxOf(w, h) * 0.48f,
        core = Color(0xFF2563EB),
        edge = Color(0xFF38BDF8),
        alpha = 0.36f * revealAlpha,
      )
    }

    // 3. Stars, single tilted neon orbital ring + upper-right star flare, and bottom planetary horizon
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      if (w <= 1f || h <= 1f) return@Canvas

      // Scattered twinkling cosmic stars
      stars.forEach { star ->
        val pulse = 0.55f + 0.45f * sin(tTwinkle + star.phaseOffset)
        val a = (star.baseAlpha * pulse * revealAlpha).coerceIn(0f, 1f)
        val pos = Offset(w * star.xFraction, h * star.yFraction)
        drawCircle(color = star.tint.copy(alpha = a), radius = star.radiusPx * density * 0.65f, center = pos)
      }

      // Single crisp tilted elliptical neon orbital arc behind the card
      val orbitCenter = Offset(w * 0.52f, h * 0.27f)
      val orbitWidth = w * 1.08f
      val orbitHeight = h * 0.18f
      val orbitTopLeft = Offset(orbitCenter.x - orbitWidth / 2f, orbitCenter.y - orbitHeight / 2f)
      val orbitBrush =
        Brush.linearGradient(
          colors =
            listOf(
              Color(0xFF00E5FF).copy(alpha = 0.28f * revealAlpha),
              Color(0xFF2563EB).copy(alpha = 0.68f * revealAlpha),
              Color(0xFF7C3AED).copy(alpha = 0.84f * revealAlpha),
              Color(0xFFEC4899).copy(alpha = 0.92f * revealAlpha),
            ),
          start = Offset(orbitTopLeft.x, orbitTopLeft.y + orbitHeight),
          end = Offset(orbitTopLeft.x + orbitWidth, orbitTopLeft.y),
        )
      rotate(degrees = -22f + if (reducedMotion) 0f else (1.4f * sin(tDrift)), pivot = orbitCenter) {
        // Outer soft neon aura
        drawOval(
          brush = orbitBrush,
          topLeft = orbitTopLeft,
          size = Size(orbitWidth, orbitHeight),
          style = Stroke(width = 14.dp.toPx()),
          alpha = 0.22f,
        )
        // Mid glow
        drawOval(
          brush = orbitBrush,
          topLeft = orbitTopLeft,
          size = Size(orbitWidth, orbitHeight),
          style = Stroke(width = 5.dp.toPx()),
          alpha = 0.55f,
        )
        // Crisp razor orbital core
        drawOval(
          brush = orbitBrush,
          topLeft = orbitTopLeft,
          size = Size(orbitWidth, orbitHeight),
          style = Stroke(width = 1.6.dp.toPx()),
          alpha = 0.95f,
        )
      }

      // Bright 4-ray orbital highlight flare on upper-right rim
      val flarePos = Offset(w * 0.65f, h * 0.163f)
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                Color.White.copy(alpha = 0.95f * revealAlpha),
                Color(0xFF7DD3FC).copy(alpha = 0.55f * revealAlpha),
                Color(0xFFD946EF).copy(alpha = 0.18f * revealAlpha),
                Color.Transparent,
              ),
            center = flarePos,
            radius = 28.dp.toPx(),
          ),
        radius = 28.dp.toPx(),
        center = flarePos,
        blendMode = BlendMode.Screen,
      )
      val rayLenH = 22.dp.toPx()
      val rayLenV = 14.dp.toPx()
      drawLine(
        color = Color.White.copy(alpha = 0.85f * revealAlpha),
        start = Offset(flarePos.x - rayLenH, flarePos.y),
        end = Offset(flarePos.x + rayLenH, flarePos.y),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawLine(
        color = Color.White.copy(alpha = 0.85f * revealAlpha),
        start = Offset(flarePos.x, flarePos.y - rayLenV),
        end = Offset(flarePos.x, flarePos.y + rayLenV),
        strokeWidth = 1.4.dp.toPx(),
        cap = StrokeCap.Round,
      )

      // Bottom Curved Illuminated Planetary Horizon
      val planetRadius = w * 1.48f
      val planetCenter = Offset(w * 0.50f, h * 0.875f + planetRadius)

      // Outer atmospheric halo rim around the planet curve
      drawCircle(
        brush =
          Brush.radialGradient(
            0.84f to Color.Transparent,
            0.94f to Color(0xFF0EA5E9).copy(alpha = 0.42f * revealAlpha),
            0.975f to Color(0xFF8B5CF6).copy(alpha = 0.36f * revealAlpha),
            1.00f to Color.Transparent,
            center = planetCenter,
            radius = planetRadius + 34.dp.toPx(),
          ),
        radius = planetRadius + 34.dp.toPx(),
        center = planetCenter,
        blendMode = BlendMode.Screen,
      )

      // Dark planet sphere body with subtle blue internal gradient
      drawCircle(
        brush =
          Brush.radialGradient(
            0.00f to Color(0xFF020612),
            0.82f to Color(0xFF050E26),
            0.94f to Color(0xFF0A2458),
            1.00f to Color(0xFF2563EB),
            center = planetCenter,
            radius = planetRadius,
          ),
        radius = planetRadius,
        center = planetCenter,
      )

      // Razor bright curved atmosphere horizon rim (cyan center shifting violet/magenta at edges)
      drawCircle(
        brush =
          Brush.horizontalGradient(
            colors =
              listOf(
                Color(0xFF7C3AED).copy(alpha = 0.65f * revealAlpha),
                Color(0xFF38BDF8).copy(alpha = 0.95f * revealAlpha),
                Color(0xFFE0F2FE).copy(alpha = 0.98f * revealAlpha),
                Color(0xFF38BDF8).copy(alpha = 0.95f * revealAlpha),
                Color(0xFFEC4899).copy(alpha = 0.75f * revealAlpha),
              )
          ),
        radius = planetRadius,
        center = planetCenter,
        style = Stroke(width = 2.6.dp.toPx()),
      )

      // Subtle glowing surface/city-light speckles below the curved horizon
      val surfaceLights =
        listOf(
          Offset(w * 0.24f, h * 0.935f),
          Offset(w * 0.31f, h * 0.918f),
          Offset(w * 0.42f, h * 0.905f),
          Offset(w * 0.51f, h * 0.910f),
          Offset(w * 0.60f, h * 0.908f),
          Offset(w * 0.72f, h * 0.922f),
          Offset(w * 0.80f, h * 0.942f),
        )
      surfaceLights.forEachIndexed { idx, pt ->
        val a = (0.35f + 0.25f * sin(tTwinkle + idx)).coerceIn(0f, 0.7f) * revealAlpha
        drawCircle(
          color = if (idx % 2 == 0) Color(0xFF7DD3FC).copy(alpha = a) else Color(0xFFC4B5FD).copy(alpha = a),
          radius = 1.6.dp.toPx(),
          center = pt,
        )
      }

      // Soft bottom vignette for footer legibility
      drawPath(
        path =
          Path().apply {
            moveTo(0f, h * 0.91f)
            lineTo(w, h * 0.91f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
          },
        brush =
          Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0xFF02040B).copy(alpha = 0.68f)),
            startY = h * 0.91f,
            endY = h,
          ),
      )
    }
  }
}

private fun DrawScope.drawNebulaCloud(
  center: Offset,
  radius: Float,
  core: Color,
  edge: Color,
  alpha: Float,
) {
  val a = alpha.coerceIn(0f, 0.85f)
  if (a <= 0.01f || radius <= 2f) return
  drawCircle(
    brush =
      Brush.radialGradient(
        0.00f to core.copy(alpha = a),
        0.42f to edge.copy(alpha = a * 0.45f),
        0.78f to edge.copy(alpha = a * 0.12f),
        1.00f to Color.Transparent,
        center = center,
        radius = radius,
      ),
    radius = radius,
    center = center,
    blendMode = BlendMode.Screen,
  )
}
