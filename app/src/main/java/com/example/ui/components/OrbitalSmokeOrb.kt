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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.voice.OrbReactiveFrame
import com.example.domain.model.OrbVisualMode
import com.example.domain.model.VoiceAudioMetrics
import com.example.ui.components.OrbRenderingEngine.drawSmokeOrbFrame
import kotlin.math.PI

/**
 * Unified Orbital Holographic Smoke Orb powered by [OrbRenderingEngine]:
 * - Continuous seamless multicolor neon perimeter border across all 8 OrbVisualModes.
 * - Pre-allocated lightweight orbital particles, atmospheric dust & flowing plasma ribbon.
 * - Real-time voice-reactive swirling translucent nebula & smoke inside the orb driven strictly
 *   by genuine [OrbReactiveFrame] / [VoiceAudioMetrics] (mic PCM DSP + AI audio playback amplitude).
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
  reactiveFrame: OrbReactiveFrame? = null,
  modifier: Modifier = Modifier,
) {
  val effectiveMode = reactiveFrame?.orbMode ?: mode
  val effectiveMuted = reactiveFrame?.isMuted ?: isMuted
  val palette = remember(effectiveMode, effectiveMuted) {
    OrbRenderingEngine.paletteFor(effectiveMode, effectiveMuted)
  }

  val transition = rememberInfiniteTransition(label = "orbital_smoke_orb")
  val orbitDurationMs =
    when {
      reducedMotion -> 12000
      effectiveMode == OrbVisualMode.PROCESSING || effectiveMode == OrbVisualMode.IMAGE_GENERATION -> 2500
      effectiveMode == OrbVisualMode.LISTENING || effectiveMode == OrbVisualMode.SPEAKING -> 3600
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
      animationSpec = infiniteRepeatable(tween(if (reducedMotion) 9000 else 4200, easing = LinearEasing), RepeatMode.Restart),
      label = "smoke_phase",
    )

  val breath by
    transition.animateFloat(
      initialValue = 0.94f,
      targetValue = if (reducedMotion) 0.98f else 1.045f,
      animationSpec = infiniteRepeatable(tween(1550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "breath",
    )

  val measuredRms =
    when {
      effectiveMuted -> 0f
      reactiveFrame != null -> reactiveFrame.reactiveAmplitude.coerceIn(0f, 1f)
      effectiveMode == OrbVisualMode.LISTENING -> maxOf(audioMetrics.rmsNormalized, fallbackRms).coerceIn(0f, 1f)
      effectiveMode == OrbVisualMode.SPEAKING -> maxOf(fallbackRms, audioMetrics.rmsNormalized).coerceIn(0f, 1f)
      else -> 0f
    }

  val smoothEnergy by
    animateFloatAsState(
      targetValue = measuredRms,
      animationSpec = tween(105),
      label = "audio_energy_smooth",
    )

  val pitchHz = reactiveFrame?.pitchHz ?: audioMetrics.estimatedPitchHz
  val pitchConf = reactiveFrame?.pitchConfidence ?: audioMetrics.pitchConfidence
  val pitchNorm =
    if (pitchConf > 0.35f && pitchHz > 0f) {
      ((pitchHz - 80f) / 320f).coerceIn(0f, 1f)
    } else {
      0.35f
    }

  val particleSeeds = remember(lowPowerMode) { OrbRenderingEngine.createParticleSeeds(lowPowerMode) }
  val smokeSeeds = remember(lowPowerMode) { OrbRenderingEngine.createSmokeSeeds(lowPowerMode) }
  val ribbonPath = remember { Path() }

  Box(
    modifier = modifier.size(orbSize).testTag("orbital_smoke_orb"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(orbSize)) {
      drawSmokeOrbFrame(
        palette = palette,
        mode = effectiveMode,
        isMuted = effectiveMuted,
        reducedMotion = reducedMotion,
        lowPowerMode = lowPowerMode,
        rotationDeg = rotationDeg,
        smokePhase = smokePhase,
        breath = breath,
        smoothEnergy = smoothEnergy,
        pitchNorm = pitchNorm,
        audioMetrics = audioMetrics,
        reactiveFrame = reactiveFrame,
        particleSeeds = particleSeeds,
        smokeSeeds = smokeSeeds,
        ribbonPath = ribbonPath,
      )
    }

    if (showCenterSparkle) {
      GeminiLiveWaveformIcon(
        tint = Color.White.copy(alpha = if (effectiveMuted) 0.55f else 0.96f),
        size = orbSize * 0.25f,
      )
    }
  }
}
