package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.core.voice.OrbReactiveFrame
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

internal data class OrbitParticleSeed(
  val ringIndex: Int,
  val baseAngleRad: Float,
  val speedMultiplier: Float,
  val sizeDp: Float,
  val hopFrequency: Float,
  val colorIndex: Int,
  val isDust: Boolean,
)

internal data class SmokeCloudSeed(
  val radialOffset: Float,
  val phaseOffset: Float,
  val swirlSpeed: Float,
  val puffScale: Float,
  val bandSelector: Int,
  val ellipticity: Float,
)

/**
 * Real-time Smoke & Nebula Orb Rendering Engine optimized for Android 12+ Compose Canvas.
 *
 * Renders:
 * - Outer purple/violet & cyan atmospheric aura
 * - Deep obsidian-navy volumetric sphere backdrop
 * - Swirling multi-layer translucent nebula clouds driven by real frequency bands
 * - Luminous liquid plasma ribbon trails & fine atmospheric dust particles
 * - Continuous seamless neon rim border reacting smoothly to [OrbReactiveFrame]
 *
 * Pre-allocates all particle/cloud seeds and reuses the waveform/ribbon [Path] across frames.
 */
object OrbRenderingEngine {

  internal fun createParticleSeeds(lowPowerMode: Boolean): List<OrbitParticleSeed> {
    val count = if (lowPowerMode) 10 else 20
    return List(count) { idx ->
      OrbitParticleSeed(
        ringIndex = idx % 3,
        baseAngleRad = (idx * (2f * PI.toFloat() / count)),
        speedMultiplier = 0.65f + (idx % 4) * 0.28f,
        sizeDp = if (idx % 3 == 0) 1.3f else 2.0f + (idx % 3) * 0.65f,
        hopFrequency = 0.9f + (idx % 4) * 0.45f,
        colorIndex = idx,
        isDust = idx % 3 == 0,
      )
    }
  }

  internal fun createSmokeSeeds(lowPowerMode: Boolean): List<SmokeCloudSeed> {
    val count = if (lowPowerMode) 5 else 9
    return List(count) { idx ->
      SmokeCloudSeed(
        radialOffset = 0.10f + (idx % 4) * 0.11f,
        phaseOffset = idx * 0.78f,
        swirlSpeed = if (idx % 2 == 0) 1.0f else -0.82f,
        puffScale = 0.34f + (idx % 3) * 0.12f,
        bandSelector = idx % 3,
        ellipticity = 0.78f + (idx % 3) * 0.12f,
      )
    }
  }

  fun paletteFor(mode: OrbVisualMode, isMuted: Boolean): List<Color> =
    when {
      isMuted -> listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF334155), Color(0xFF475569))
      mode == OrbVisualMode.LISTENING ->
        listOf(NeonCyan, ElectricBlue, Color(0xFF6366F1), NeonViolet, NeonPurple, NeonCyan)
      mode == OrbVisualMode.PROCESSING ->
        listOf(ElectricBlue, NeonViolet, NeonPurple, NeonCyan, ElectricBlue)
      mode == OrbVisualMode.SPEAKING ->
        listOf(NeonCyan, Color(0xFF38BDF8), ElectricBlue, NeonViolet, NeonPurple, NeonPink, NeonCyan)
      mode == OrbVisualMode.IMAGE_GENERATION ->
        listOf(NeonMagenta, NeonPink, WarmAmber, SubtleGold, NeonCyan, NeonMagenta)
      mode == OrbVisualMode.SUCCESS ->
        listOf(NeonGreen, NeonCyan, ElectricBlue, NeonGreen)
      mode == OrbVisualMode.ERROR ->
        listOf(Color(0xFFEF4444), WarmAmber, Color(0xFFF97316), Color(0xFFEF4444))
      mode == OrbVisualMode.FOCUSED ->
        listOf(ElectricBlue, NeonCyan, NeonMagenta, NeonViolet, NeonPink, ElectricBlue)
      else ->
        listOf(NeonCyan, ElectricBlue, NeonViolet, NeonPurple, Color(0xFF6366F1), NeonCyan)
    }

  internal fun DrawScope.drawSmokeOrbFrame(
    palette: List<Color>,
    mode: OrbVisualMode,
    isMuted: Boolean,
    reducedMotion: Boolean,
    lowPowerMode: Boolean,
    rotationDeg: Float,
    smokePhase: Float,
    breath: Float,
    smoothEnergy: Float,
    pitchNorm: Float,
    audioMetrics: VoiceAudioMetrics,
    reactiveFrame: OrbReactiveFrame?,
    particleSeeds: List<OrbitParticleSeed>,
    smokeSeeds: List<SmokeCloudSeed>,
    ribbonPath: Path,
  ) {
    val minDim = size.minDimension
    val radius = minDim / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val scaleFactor = if (isMuted) 0.94f else breath + smoothEnergy * 0.09f
    val coreRadius = radius * 0.63f * scaleFactor
    val ringRadius = radius * 0.82f * scaleFactor

    val densityBoost = reactiveFrame?.smokeDensity ?: (0.30f + smoothEnergy * 0.70f)
    val haloIntensity = reactiveFrame?.haloIntensity ?: (0.28f + smoothEnergy * 0.65f)

    // 1. Deep Atmospheric Outer Purple / Violet & Cyan Bloom
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              palette[0].copy(alpha = (0.26f + smoothEnergy * 0.24f).coerceIn(0.08f, 0.55f)),
              palette[(palette.size - 2).coerceAtLeast(0)].copy(alpha = (0.14f + haloIntensity * 0.14f).coerceIn(0.05f, 0.34f)),
              Color.Transparent,
            ),
          center = center,
          radius = radius * (1.03f + smoothEnergy * 0.12f),
        ),
      radius = radius,
      center = center,
    )

    // 2. Dark Obsidian-Navy Volumetric Glass Sphere
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              Color(0xFF0E1B3A),
              Color(0xFF060C1E),
              Color(0xFF02040B),
            ),
          center = Offset(center.x - coreRadius * 0.20f, center.y - coreRadius * 0.22f),
          radius = coreRadius * 1.18f,
        ),
      radius = coreRadius,
      center = center,
    )

    // 3. Multi-Layered Swirling Nebula Smoke Clouds (3-Band Spectral Reactive)
    val smokeIntensity =
      when {
        isMuted -> 0.14f
        mode == OrbVisualMode.LISTENING -> (0.30f + smoothEnergy * 0.70f).coerceIn(0.24f, 1.0f)
        mode == OrbVisualMode.SPEAKING -> (0.38f + smoothEnergy * 0.62f).coerceIn(0.28f, 1.0f)
        mode == OrbVisualMode.PROCESSING || mode == OrbVisualMode.IMAGE_GENERATION -> 0.58f
        else -> (0.22f + densityBoost * 0.18f).coerceIn(0.18f, 0.45f)
      }

    smokeSeeds.forEachIndexed { idx, puff ->
      val bandEnergy =
        when (puff.bandSelector) {
          0 -> reactiveFrame?.lowBandEnergy ?: audioMetrics.lowBandEnergy
          1 -> reactiveFrame?.midBandEnergy ?: audioMetrics.midBandEnergy
          else -> reactiveFrame?.highBandEnergy ?: audioMetrics.highBandEnergy
        }
      val activeLift =
        if (mode == OrbVisualMode.LISTENING || mode == OrbVisualMode.SPEAKING) {
          maxOf(bandEnergy, smoothEnergy * 0.85f)
        } else {
          smoothEnergy
        }
      val theta = smokePhase * puff.swirlSpeed + puff.phaseOffset + pitchNorm * 1.15f
      val orbitR = coreRadius * (puff.radialOffset + activeLift * 0.18f)
      val puffCenter =
        Offset(
          x = center.x + cos(theta) * orbitR,
          y = center.y + sin(theta * 1.12f) * orbitR * puff.ellipticity,
        )
      val puffRadius =
        (coreRadius * (puff.puffScale + activeLift * 0.18f)).coerceAtMost(coreRadius * 0.94f)
      val colorA = palette[idx % palette.size]
      val colorB = palette[(idx + 2) % palette.size]
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                colorA.copy(alpha = (0.38f * smokeIntensity).coerceIn(0.06f, 0.60f)),
                colorB.copy(alpha = (0.18f * smokeIntensity).coerceIn(0.03f, 0.34f)),
                Color.Transparent,
              ),
            center = puffCenter,
            radius = puffRadius,
          ),
        radius = puffRadius,
        center = puffCenter,
      )
    }

    // 4. Flowing Liquid Plasma Ribbon Arcs inside Sphere (Zero-allocation reused Path)
    if (!lowPowerMode && !isMuted) {
      ribbonPath.reset()
      val ribbonR = coreRadius * (0.46f + smoothEnergy * 0.16f)
      val steps = 24
      for (i in 0..steps) {
        val t = (i / steps.toFloat()) * (2f * PI.toFloat())
        val wave = sin(t * 3f + smokePhase * 1.5f) * (coreRadius * (0.06f + smoothEnergy * 0.12f))
        val rr = ribbonR + wave
        val rx = center.x + cos(t + smokePhase * 0.35f) * rr
        val ry = center.y + sin(t + smokePhase * 0.35f) * rr * 0.84f
        if (i == 0) ribbonPath.moveTo(rx, ry) else ribbonPath.lineTo(rx, ry)
      }
      ribbonPath.close()
      drawPath(
        path = ribbonPath,
        color = palette[0].copy(alpha = (0.16f + smoothEnergy * 0.28f).coerceIn(0.10f, 0.48f)),
        style = Stroke(width = (coreRadius * 0.045f).coerceAtLeast(1.5f), cap = StrokeCap.Round),
      )
    }

    // 5. Inner Luminous Specular Core Highlight
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              Color.White.copy(alpha = 0.20f + smoothEnergy * 0.30f),
              palette[1 % palette.size].copy(alpha = 0.24f * smokeIntensity),
              Color.Transparent,
            ),
          center = Offset(center.x - coreRadius * 0.18f, center.y - coreRadius * 0.22f),
          radius = coreRadius * 0.74f,
        ),
      radius = coreRadius * 0.74f,
      center = center,
    )

    // 6. Continuous Seamless Multicolor Neon Perimeter Border
    val borderStroke = (minDim * 0.030f).coerceIn(2.dp.toPx(), 5.5.dp.toPx())
    rotate(degrees = rotationDeg, pivot = center) {
      drawCircle(
        brush = Brush.sweepGradient(colors = palette, center = center),
        radius = coreRadius + borderStroke * 0.35f,
        center = center,
        style = Stroke(width = borderStroke),
      )
    }

    // 7. Outer Harmonic Halo Ring + Orbital Liquid Particles & Atmospheric Dust
    val ringFactor = 0.86f + smoothEnergy * 0.08f
    rotate(degrees = -rotationDeg * 0.45f, pivot = center) {
      drawCircle(
        brush =
          Brush.sweepGradient(
            colors = palette.map { it.copy(alpha = (0.24f + smoothEnergy * 0.26f).coerceIn(0.16f, 0.55f)) },
            center = center,
          ),
        radius = ringRadius * ringFactor,
        center = center,
        style = Stroke(width = (1.2.dp.toPx() + smoothEnergy * 1.1.dp.toPx()), cap = StrokeCap.Round),
      )
    }

    particleSeeds.forEach { seed ->
      val angle =
        seed.baseAngleRad +
          Math.toRadians(rotationDeg.toDouble()).toFloat() * seed.speedMultiplier
      val hopOffset =
        if (reducedMotion) 0f
        else sin(smokePhase * seed.hopFrequency + seed.baseAngleRad) * (radius * (0.045f + smoothEnergy * 0.045f))
      val ellipticalStretch =
        when (seed.ringIndex) {
          1 -> 0.89f
          2 -> 0.94f
          else -> 1.0f
        }
      val baseOrbit = if (seed.isDust) ringRadius * (0.94f + smoothEnergy * 0.08f) else ringRadius * ringFactor
      val r = (baseOrbit + hopOffset).coerceAtMost(radius * 0.96f)
      val px = center.x + cos(angle) * r
      val py = center.y + sin(angle) * r * ellipticalStretch
      val pColor = palette[seed.colorIndex % palette.size]
      val pRadius =
        (seed.sizeDp.dp.toPx() * (minDim / 180.dp.toPx()).coerceIn(0.45f, 1.35f))
          .coerceAtLeast(1.3f)

      if (!seed.isDust) {
        val trailAngle = angle - (0.16f + smoothEnergy * 0.10f)
        val tx = center.x + cos(trailAngle) * r
        val ty = center.y + sin(trailAngle) * r * ellipticalStretch
        drawLine(
          color = pColor.copy(alpha = 0.36f + smoothEnergy * 0.24f),
          start = Offset(tx, ty),
          end = Offset(px, py),
          strokeWidth = pRadius * 0.95f,
          cap = StrokeCap.Round,
        )
      }
      drawCircle(
        color = pColor.copy(alpha = if (seed.isDust) 0.55f + smoothEnergy * 0.35f else 0.92f),
        radius = pRadius,
        center = Offset(px, py),
      )
    }
  }
}
