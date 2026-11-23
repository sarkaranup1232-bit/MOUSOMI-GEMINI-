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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.voice.VoiceState
import com.example.domain.model.OrbVisualMode
import com.example.domain.model.VoiceAudioMetrics
import com.example.ui.theme.NeonCyan
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AnimatedAIOrb(
  voiceState: VoiceState,
  voiceRmsNormalized: Float,
  isThinking: Boolean = false,
  isMuted: Boolean = false,
  orbSize: Dp = 196.dp,
  modifier: Modifier = Modifier,
  audioMetrics: VoiceAudioMetrics = VoiceAudioMetrics.SILENT,
  reducedMotion: Boolean = false,
  lowPowerMode: Boolean = false,
  reactiveFrame: com.example.core.voice.OrbReactiveFrame? = null,
) {
  val mode =
    when {
      reactiveFrame != null -> reactiveFrame.orbMode
      isThinking -> OrbVisualMode.PROCESSING
      voiceState is VoiceState.Speaking -> OrbVisualMode.SPEAKING
      voiceState is VoiceState.Listening -> OrbVisualMode.LISTENING
      voiceState is VoiceState.Error -> OrbVisualMode.ERROR
      else -> OrbVisualMode.IDLE
    }

  Box(
    modifier = modifier.size(orbSize).testTag("animated_ai_orb"),
    contentAlignment = Alignment.Center,
  ) {
    OrbitalSmokeOrb(
      mode = mode,
      audioMetrics = audioMetrics,
      fallbackRms = voiceRmsNormalized,
      isMuted = isMuted,
      reducedMotion = reducedMotion,
      lowPowerMode = lowPowerMode,
      orbSize = orbSize,
      showCenterSparkle = true,
      reactiveFrame = reactiveFrame,
    )
  }
}

@Composable
fun VoiceWaveform(
  isActive: Boolean,
  rmsNormalized: Float,
  accentColor: Color = NeonCyan,
  barCount: Int = 18,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "voice_waveform")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
      label = "wave_phase",
    )

  Row(
    modifier = modifier.height(42.dp).testTag("voice_waveform"),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Canvas(modifier = Modifier.width((barCount * 8).dp).height(42.dp)) {
      val barW = 4.dp.toPx()
      val spacing = (size.width - barCount * barW) / (barCount - 1).coerceAtLeast(1)
      val maxH = size.height
      for (i in 0 until barCount) {
        val wave = if (isActive) ((sin(phase + i * 0.55f) + 1f) * 0.5f) else 0.15f
        val amplitude =
          if (isActive) (0.2f + wave * (0.45f + rmsNormalized * 0.55f)).coerceIn(0.15f, 1f)
          else 0.14f
        val barH = (maxH * amplitude).coerceAtLeast(5.dp.toPx())
        val left = i * (barW + spacing)
        val top = (maxH - barH) / 2f
        drawRoundRect(
          color = accentColor.copy(alpha = 0.45f + amplitude * 0.55f),
          topLeft = Offset(left, top),
          size = Size(barW, barH),
          cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
      }
    }
  }
}
