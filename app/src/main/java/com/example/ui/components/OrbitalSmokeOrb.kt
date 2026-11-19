package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.OrbVisualMode
import com.example.domain.model.VoiceAudioMetrics
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SubtleGold
import com.example.ui.theme.WarmAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class OrbitParticleSeed(
  val ringIndex: Int,
  val baseAngleRad: Float,
  val speedMultiplier: Float,
  val sizeDp: Float,
  val hopFrequency: Float,
  val colorIndex: Int,
)

private data class SmokePuffSeed(
  val radialOffset: Float,
  val phaseOffset: Float,
  val swirlSpeed: Float,
  val puffScale: Float,
  val bandSelector: Int,
)

/**
 * Unified Orbital Holographic AI Orb (Modules 34, 35, 36, 37, 38 & 42):
 * - Continuous seamless multicolor neon perimeter border across all 8 OrbVisualModes.
 * - Pre-allocated lightweight orbital particle ring + glowing trails with zero per-frame allocations.
 * - Real-time voice-reactive swirling translucent smoke & dust inside the orb driven strictly
 *   by genuine VoiceAudioMetrics (AudioRecord PCM DSP) or real TTS utterance progress.
 */
@Composable
fun OrbitalSmokeOrb(
  mode: OrbVisualMode,
  audioMetrics: VoiceAudioMetrics = VoiceAudioMetrics.SILENT,
  fallbackRms: Float = 0f,
  isMuted: Boolean = false,
  reducedMotion: Boolean = false,
  lowPowerMode: Boolean = false,
  orbSize: Dp = 180.dp,
  showCenterSparkle: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val palette =
    remember(mode, isMuted) {
      when {
        isMuted -> listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF334155), Color(0xFF475569))
        mode == OrbVisualMode.LISTENING ->
          listOf(NeonCyan, ElectricBlue, NeonViolet, NeonMagenta, NeonCyan)
        mode == OrbVisualMode.PROCESSING ->
          listOf(ElectricBlue, NeonViolet, NeonPurple, NeonCyan, ElectricBlue)
        mode == OrbVisualMode.SPEAKING ->
          listOf(NeonGreen, NeonCyan, ElectricBlue, NeonPurple, NeonPink, NeonGreen)
        mode == OrbVisualMode.IMAGE_GENERATION ->
          listOf(NeonMagenta, NeonPink, WarmAmber, SubtleGold, NeonCyan, NeonMagenta)
        mode == OrbVisualMode.SUCCESS ->
          listOf(NeonGreen, NeonCyan, ElectricBlue, NeonGreen)
        mode == OrbVisualMode.ERROR ->
          listOf(Color(0xFFEF4444), WarmAmber, Color(0xFFF97316), Color(0xFFEF4444))
        mode == OrbVisualMode.FOCUSED ->
          listOf(ElectricBlue, NeonCyan, NeonMagenta, NeonViolet, NeonPink, ElectricBlue)
        else ->
          listOf(ElectricBlue, NeonCyan, NeonViolet, NeonPurple, NeonMagenta, NeonPink, WarmAmber, SubtleGold, ElectricBlue)
      }
    }

  val transition = rememberInfiniteTransition(label = "orbital_smoke_orb")
  val orbitDurationMs =
    when {
      reducedMotion -> 12000
      mode == OrbVisualMode.PROCESSING || mode == OrbVisualMode.IMAGE_GENERATION -> 2600
      mode == OrbVisualMode.LISTENING || mode == OrbVisualMode.SPEAKING -> 3800
      else -> 6400
    }

  val rotationDeg by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = if (reducedMotion) 0f else 360f,
      animationSpec = infiniteRepeatable(tween(orbitDurationMs, easing = LinearEasing), RepeatMode.Restart),
      label = "orbit_rot",
    )

  val smokePhase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec = infiniteRepeatable(tween(if (reducedMotion) 9000 else 4400, easing = LinearEasing), RepeatMode.Restart),
      label = "smoke_phase",
    )

  val breath by
    transition.animateFloat(
      initialValue = 0.94f,
      targetValue = if (reducedMotion) 0.98f else 1.05f,
      animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "breath",
    )

  // Compute genuine audio excitation from real PCM DSP metrics or active TTS utterance cadence
  val measuredRms =
    when {
      isMuted -> 0f
      mode == OrbVisualMode.LISTENING -> maxOf(audioMetrics.rmsNormalized, fallbackRms).coerceIn(0f, 1f)
      mode == OrbVisualMode.SPEAKING ->
        maxOf(fallbackRms, audioMetrics.rmsNormalized).let { real ->
          if (real > 0.02f) real else 0.24f
        }.coerceIn(0f, 1f)
      else -> 0f
    }

  val smoothEnergy by
    animateFloatAsState(
      targetValue = measuredRms,
      animationSpec = tween(110),
      label = "audio_energy_smooth",
    )

  val pitchNorm =
    if (audioMetrics.pitchConfidence > 0.35f && audioMetrics.estimatedPitchHz > 0f) {
      ((audioMetrics.estimatedPitchHz - 80f) / 320f).coerceIn(0f, 1f)
    } else {
      0.35f
    }

  val particleSeeds = remember(lowPowerMode) {
    val count = if (lowPowerMode) 8 else 14
    List(count) { idx ->
      OrbitParticleSeed(
        ringIndex = idx % 3,
        baseAngleRad = (idx * (2f * PI.toFloat() / count)),
        speedMultiplier = 0.7f + (idx % 4) * 0.28f,
        sizeDp = 2.0f + (idx % 3) * 0.8f,
        hopFrequency = 1.0f + (idx % 3) * 0.6f,
        colorIndex = idx,
      )
    }
  }

  val smokeSeeds = remember(lowPowerMode) {
    val count = if (lowPowerMode) 4 else 7
    List(count) { idx ->
      SmokePuffSeed(
        radialOffset = 0.14f + (idx % 3) * 0.14f,
        phaseOffset = idx * 0.95f,
        swirlSpeed = if (idx % 2 == 0) 1.0f else -0.8f,
        puffScale = 0.34f + (idx % 3) * 0.11f,
        bandSelector = idx % 3,
      )
    }
  }

  Box(
    modifier = modifier.size(orbSize).testTag("orbital_smoke_orb"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(orbSize)) {
      val minDim = size.minDimension
      val radius = minDim / 2f
      val center = Offset(size.width / 2f, size.height / 2f)
      val scaleFactor = if (isMuted) 0.94f else breath + smoothEnergy * 0.08f
      val coreRadius = radius * 0.62f * scaleFactor
      val ringRadius = radius * 0.80f * scaleFactor

      // 1. Soft Outer Ambient Glow
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                palette[0].copy(alpha = 0.28f + smoothEnergy * 0.22f),
                palette[2 % palette.size].copy(alpha = 0.10f),
                Color.Transparent,
              ),
            center = center,
            radius = radius * (1.02f + smoothEnergy * 0.10f),
          ),
        radius = radius,
        center = center,
      )

      // 2. Central Dark Glass Sphere Backdrop
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                Color(0xFF0E1730),
                Color(0xFF060B18),
                Color(0xFF02040A),
              ),
            center = Offset(center.x - coreRadius * 0.2f, center.y - coreRadius * 0.2f),
            radius = coreRadius * 1.15f,
          ),
        radius = coreRadius,
        center = center,
      )

      // 3. Real-time Audio-Reactive Swirling Translucent Smoke Layers & Mist Inside Sphere
      val smokeIntensity =
        when {
          isMuted -> 0.12f
          mode == OrbVisualMode.LISTENING -> (0.28f + smoothEnergy * 0.72f).coerceIn(0f, 1f)
          mode == OrbVisualMode.SPEAKING -> (0.35f + smoothEnergy * 0.55f).coerceIn(0f, 1f)
          mode == OrbVisualMode.PROCESSING || mode == OrbVisualMode.IMAGE_GENERATION -> 0.52f
          else -> 0.24f
        }

      smokeSeeds.forEachIndexed { idx, puff ->
        val bandEnergy =
          when (puff.bandSelector) {
            0 -> audioMetrics.lowBandEnergy
            1 -> audioMetrics.midBandEnergy
            else -> audioMetrics.highBandEnergy
          }
        val activeLift = if (mode == OrbVisualMode.LISTENING) bandEnergy else smoothEnergy
        val theta = smokePhase * puff.swirlSpeed + puff.phaseOffset + pitchNorm * 1.2f
        val orbitR = coreRadius * (puff.radialOffset + activeLift * 0.16f)
        val puffCenter =
          Offset(
            x = center.x + cos(theta) * orbitR,
            y = center.y + sin(theta * 1.15f) * orbitR * 0.85f,
          )
        val puffRadius = (coreRadius * (puff.puffScale + activeLift * 0.16f)).coerceAtMost(coreRadius * 0.92f)
        val colorA = palette[idx % palette.size]
        val colorB = palette[(idx + 2) % palette.size]
        drawCircle(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  colorA.copy(alpha = (0.36f * smokeIntensity).coerceIn(0.06f, 0.55f)),
                  colorB.copy(alpha = (0.16f * smokeIntensity).coerceIn(0.03f, 0.30f)),
                  Color.Transparent,
                ),
              center = puffCenter,
              radius = puffRadius,
            ),
          radius = puffRadius,
          center = puffCenter,
        )
      }

      // 4. Inner Luminous Core Specular Highlight
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                Color.White.copy(alpha = 0.22f + smoothEnergy * 0.28f),
                palette[1 % palette.size].copy(alpha = 0.22f * smokeIntensity),
                Color.Transparent,
              ),
            center = Offset(center.x - coreRadius * 0.18f, center.y - coreRadius * 0.22f),
            radius = coreRadius * 0.72f,
          ),
        radius = coreRadius * 0.72f,
        center = center,
      )

      // 5. Continuous Seamless Multicolor Neon Perimeter Border (zero corner/arc gaps)
      val borderStroke = (minDim * 0.032f).coerceIn(2.dp.toPx(), 5.5.dp.toPx())
      rotate(degrees = rotationDeg, pivot = center) {
        drawCircle(
          brush = Brush.sweepGradient(colors = palette, center = center),
          radius = coreRadius + borderStroke * 0.4f,
          center = center,
          style = Stroke(width = borderStroke),
        )
      }

      // 6. Lightweight Multi-Orbit Luminous Particles & Trails around Outer Ring
      val ringFactor = 0.86f + smoothEnergy * 0.07f
      rotate(degrees = -rotationDeg * 0.45f, pivot = center) {
        drawCircle(
          brush =
            Brush.sweepGradient(
              colors = palette.map { it.copy(alpha = 0.28f) },
              center = center,
            ),
          radius = ringRadius * ringFactor,
          center = center,
          style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      particleSeeds.forEach { seed ->
        val angle =
          seed.baseAngleRad +
            Math.toRadians(rotationDeg.toDouble()).toFloat() * seed.speedMultiplier
        val hopOffset =
          if (reducedMotion) 0f
          else sin(smokePhase * seed.hopFrequency + seed.baseAngleRad) * (radius * 0.055f)
        val ellipticalStretch = if (seed.ringIndex == 1) 0.90f else 1.0f
        val r = (ringRadius * ringFactor + hopOffset).coerceAtMost(radius * 0.94f)
        val px = center.x + cos(angle) * r
        val py = center.y + sin(angle) * r * ellipticalStretch
        val pColor = palette[seed.colorIndex % palette.size]
        val pRadius = (seed.sizeDp.dp.toPx() * (minDim / 180.dp.toPx()).coerceIn(0.45f, 1.3f))
          .coerceAtLeast(1.5f)

        // Subtle glowing tail behind each orbiting particle
        val trailAngle = angle - 0.18f
        val tx = center.x + cos(trailAngle) * r
        val ty = center.y + sin(trailAngle) * r * ellipticalStretch
        drawLine(
          color = pColor.copy(alpha = 0.35f),
          start = Offset(tx, ty),
          end = Offset(px, py),
          strokeWidth = pRadius * 0.9f,
          cap = StrokeCap.Round,
        )
        drawCircle(
          color = pColor.copy(alpha = 0.92f),
          radius = pRadius,
          center = Offset(px, py),
        )
      }
    }

    if (showCenterSparkle) {
      GeminiLiveWaveformIcon(
        tint = Color.White,
        size = orbSize * 0.26f,
      )
    }
  }
}
