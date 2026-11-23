package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.core.voice.LiveSessionPhase
import com.example.core.voice.OrbReactiveFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Circular Rainbow Live Waveform button matching Reference Images 1 & 2:
 * Cyan -> Blue -> Lavender/Purple -> Peach/Gold -> Rose radial/linear pastel glow disc
 * with crisp dark 4-bar waveform icon centered inside.
 */
@Composable
fun RainbowLiveWaveformDiscButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  isLiveActive: Boolean = false,
  isMicActive: Boolean = false,
  voiceRmsNormalized: Float = 0f,
  contentDescriptionText: String = "Start Live Voice",
) {
  val activeEnergy =
    if (isMicActive || isLiveActive) voiceRmsNormalized.coerceIn(0f, 1f)
    else 0f
  val smoothRms by
    animateFloatAsState(
      targetValue = activeEnergy,
      animationSpec = tween(durationMillis = 75, easing = FastOutSlowInEasing),
      label = "disc_rms_energy",
    )

  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = Color.Transparent,
    border =
      if (isLiveActive || isMicActive) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f))
      else BorderStroke(0.6.dp, Color.White.copy(alpha = 0.25f)),
    shadowElevation = 6.dp,
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .size(size)
        .semantics {
          role = Role.Button
          contentDescription = contentDescriptionText
        },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = this.size.width
      val h = this.size.height
      val cx = w / 2f
      val cy = h / 2f
      val r = minOf(w, h) / 2f

      // Rich pastel rainbow disc matching reference image bottom-right Live button
      drawCircle(
        brush =
          Brush.linearGradient(
            colors =
              listOf(
                Color(0xFF38BDF8), // Sky Cyan (top-left)
                Color(0xFF6366F1), // Electric Indigo
                Color(0xFFC084FC), // Lavender Purple
                Color(0xFFF9A8D4), // Soft Rose Pink
                Color(0xFFFDE68A), // Soft Peach Gold (bottom-right)
              ),
            start = Offset(0f, 0f),
            end = Offset(w, h),
          ),
        radius = r,
        center = Offset(cx, cy),
      )

      // Subtle inner cyan highlight on top-left rim
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(Color(0x66FFFFFF), Color.Transparent),
            center = Offset(cx - r * 0.35f, cy - r * 0.35f),
            radius = r * 0.85f,
          ),
        radius = r,
        center = Offset(cx, cy),
      )

      // 4 crisp waveform bars driven proportionally by real RMS when mic is active
      val barTint = Color(0xFF0A0D18)
      val barWidth = (w * 0.082f).coerceAtLeast(2.2f)
      val gap = w * 0.075f
      val totalWidth = barWidth * 4f + gap * 3f
      val startX = cx - totalWidth / 2f
      val baseHeights = floatArrayOf(0.20f, 0.44f, 0.30f, 0.40f)
      val rmsWeights = floatArrayOf(0.26f, 0.34f, 0.28f, 0.32f)
      val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

      baseHeights.forEachIndexed { idx, baseFrac ->
        val reactiveFrac =
          if (isMicActive || isLiveActive) {
            (baseFrac * 0.65f + smoothRms * rmsWeights[idx]).coerceIn(0.14f, 0.76f)
          } else {
            baseFrac
          }
        val barH = h * reactiveFrac
        val bx = startX + idx * (barWidth + gap)
        val by = cy - barH / 2f
        drawRoundRect(
          color = barTint,
          topLeft = Offset(bx, by),
          size = Size(barWidth, barH),
          cornerRadius = corner,
        )
      }
    }
  }
}

/**
 * Compact inline Chat Bar dictation strip with real microphone RMS-reactive bars,
 * clear Listening / Paused / Error status, Retry button on error, and Start / Pause / Stop controls.
 * Completely replaces the old large blocking listening card.
 */
@Composable
fun ChatBarVoiceWaveformStrip(
  rmsNormalized: Float,
  isListening: Boolean,
  isPaused: Boolean,
  errorMessage: String?,
  onStartOrResume: () -> Unit,
  onPause: () -> Unit,
  onStop: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val clampedRms = if (isListening && !isPaused && errorMessage.isNullOrBlank()) rmsNormalized.coerceIn(0f, 1f) else 0f
  val smoothRms by
    animateFloatAsState(
      targetValue = clampedRms,
      animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing),
      label = "chat_bar_rms",
    )

  // Real sample history ring updated strictly when real mic RMS changes (stays flat when silent/paused)
  val barCount = 14
  val historySamples = remember { androidx.compose.runtime.mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }
  androidx.compose.runtime.LaunchedEffect(rmsNormalized, isListening, isPaused, errorMessage) {
    if (!isListening || isPaused || !errorMessage.isNullOrBlank()) {
      for (i in historySamples.indices) {
        historySamples[i] = 0f
      }
    } else {
      historySamples.removeAt(0)
      historySamples.add(rmsNormalized.coerceIn(0f, 1f))
    }
  }

  Surface(
    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    color = Color(0xFF0E1526).copy(alpha = 0.92f),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          when {
            !errorMessage.isNullOrBlank() -> Color(0xFFF59E0B).copy(alpha = 0.65f)
            isListening && !isPaused -> Color(0xFF22D3EE).copy(alpha = 0.7f)
            else -> Color.White.copy(alpha = 0.18f)
          },
      ),
    modifier = modifier.testTag("chat_bar_voice_strip"),
  ) {
    androidx.compose.foundation.layout.Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Real voice-reactive waveform canvas
        Canvas(
          modifier =
            Modifier
              .size(width = 96.dp, height = 24.dp)
              .testTag("voice_bar_waveform")
        ) {
          val w = size.width
          val h = size.height
          val cy = h * 0.5f
          val barW = (w / (barCount * 1.8f)).coerceAtLeast(2.2f)
          val step = (w - barW) / (barCount - 1).coerceAtLeast(1)
          val spectrum =
            listOf(
              Color(0xFF22D3EE),
              Color(0xFF3B82F6),
              Color(0xFF8B5CF6),
              Color(0xFFEC4899),
              Color(0xFFFDE68A),
            )
          for (i in 0 until barCount) {
            val hist = historySamples.getOrElse(i) { 0f }
            val envelope = sin(((i + 1) / (barCount + 1f)) * PI).toFloat().coerceIn(0.3f, 1f)
            val energy = (hist * 0.72f + smoothRms * 0.28f) * envelope
            val barH =
              if (!isListening || isPaused || !errorMessage.isNullOrBlank()) {
                3f * density
              } else {
                (3.5f * density + energy * (h - 4f * density)).coerceIn(3f * density, h)
              }
            val colorIdx = ((i.toFloat() / barCount) * spectrum.size).toInt().coerceIn(0, spectrum.lastIndex)
            drawRoundRect(
              color =
                if (!errorMessage.isNullOrBlank()) Color(0xFFF59E0B).copy(alpha = 0.65f)
                else if (isPaused) Color.White.copy(alpha = 0.42f)
                else spectrum[colorIdx],
              topLeft = Offset(i * step, cy - barH * 0.5f),
              size = Size(barW, barH),
              cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
          }
        }

        androidx.compose.material3.Text(
          text =
            when {
              !errorMessage.isNullOrBlank() -> "Mic inactive"
              isListening && !isPaused -> "Listening…"
              else -> "Paused — edit or speak more"
            },
          color =
            when {
              !errorMessage.isNullOrBlank() -> Color(0xFFFBBF24)
              isListening && !isPaused -> Color(0xFF22D3EE)
              else -> Color.White.copy(alpha = 0.85f)
            },
          fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
          fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
          maxLines = 1,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )

        // Start / Resume (or Retry on error)
        val canStart = !isListening || isPaused || !errorMessage.isNullOrBlank()
        Surface(
          onClick = if (!errorMessage.isNullOrBlank()) onRetry else onStartOrResume,
          enabled = canStart,
          shape = CircleShape,
          color =
            if (!errorMessage.isNullOrBlank()) Color(0xFFF59E0B).copy(alpha = 0.22f)
            else if (canStart) Color(0xFF22D3EE).copy(alpha = 0.22f)
            else Color.White.copy(alpha = 0.06f),
          border =
            BorderStroke(
              1.dp,
              if (!errorMessage.isNullOrBlank()) Color(0xFFF59E0B).copy(alpha = 0.72f)
              else if (canStart) Color(0xFF22D3EE).copy(alpha = 0.7f)
              else Color.White.copy(alpha = 0.14f),
            ),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_bar_start_button"),
        ) {
          androidx.compose.material3.Text(
            text = if (!errorMessage.isNullOrBlank()) "Retry" else "Start",
            color =
              when {
                !errorMessage.isNullOrBlank() -> Color(0xFFFBBF24)
                canStart -> Color(0xFF22D3EE)
                else -> Color.White.copy(alpha = 0.35f)
              },
            fontSize = androidx.compose.ui.unit.TextUnit(11.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
          )
        }

        // Pause
        val canPause = isListening && !isPaused && errorMessage.isNullOrBlank()
        Surface(
          onClick = onPause,
          enabled = canPause,
          shape = CircleShape,
          color = if (canPause) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
          border =
            BorderStroke(
              1.dp,
              if (canPause) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
            ),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_bar_pause_button"),
        ) {
          androidx.compose.material3.Text(
            text = "Pause",
            color = if (canPause) Color.White else Color.White.copy(alpha = 0.35f),
            fontSize = androidx.compose.ui.unit.TextUnit(11.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
          )
        }

        // Stop
        Surface(
          onClick = onStop,
          shape = CircleShape,
          color = Color(0xFFEF4444).copy(alpha = 0.20f),
          border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.65f)),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_bar_stop_button"),
        ) {
          androidx.compose.material3.Text(
            text = "Stop",
            color = Color(0xFFFCA5A5),
            fontSize = androidx.compose.ui.unit.TextUnit(11.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
          )
        }
      }

      if (!errorMessage.isNullOrBlank()) {
        androidx.compose.material3.Text(
          text = errorMessage,
          color = Color(0xFFFDE68A),
          fontSize = androidx.compose.ui.unit.TextUnit(11.5f, androidx.compose.ui.unit.TextUnitType.Sp),
          maxLines = 2,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          modifier = Modifier.testTag("voice_bar_error_text"),
        )
      }
    }
  }
}

/**
 * Organic Multi-Layered Glowing Audio Waveform hovering directly above the Live Orb.
 * Driven strictly by real [OrbReactiveFrame] input/output energies:
 * - Silent / Idle pauses gently settle to a thin calm luminous spine.
 * - Active user speech or AI playback opens expressive multi-layered translucent waves.
 */
@Composable
fun OrganicLiveWaveform(
  reactiveFrame: OrbReactiveFrame,
  isPaused: Boolean = false,
  isMuted: Boolean = false,
  reducedMotion: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val rawAmp =
    when {
      isPaused || isMuted -> 0f
      reactiveFrame.sessionPhase == LiveSessionPhase.USER_SPEAKING ||
        reactiveFrame.sessionPhase == LiveSessionPhase.AI_SPEAKING ->
        reactiveFrame.reactiveAmplitude.coerceIn(0f, 1f)
      reactiveFrame.sessionPhase == LiveSessionPhase.AI_PROCESSING -> 0.28f
      reactiveFrame.sessionPhase == LiveSessionPhase.LISTENING ->
        (reactiveFrame.reactiveAmplitude * 0.75f).coerceIn(0.03f, 0.9f)
      else -> 0.04f
    }

  val smoothAmp by
    animateFloatAsState(
      targetValue = rawAmp,
      animationSpec = tween(durationMillis = 95, easing = FastOutSlowInEasing),
      label = "organic_wave_amp",
    )

  val smoothLow by
    animateFloatAsState(
      targetValue = if (isPaused || isMuted) 0f else reactiveFrame.lowBandEnergy.coerceIn(0f, 1f),
      animationSpec = tween(110),
      label = "organic_wave_low",
    )
  val smoothMid by
    animateFloatAsState(
      targetValue = if (isPaused || isMuted) 0f else reactiveFrame.midBandEnergy.coerceIn(0f, 1f),
      animationSpec = tween(110),
      label = "organic_wave_mid",
    )
  val smoothHigh by
    animateFloatAsState(
      targetValue = if (isPaused || isMuted) 0f else reactiveFrame.highBandEnergy.coerceIn(0f, 1f),
      animationSpec = tween(110),
      label = "organic_wave_high",
    )

  val transition = rememberInfiniteTransition(label = "organic_waveform_phase")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = if (reducedMotion) 6000 else 2200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "wave_phase",
    )

  val ribbonPaths = remember { Array(4) { Path() } }

  Canvas(
    modifier =
      modifier
        .size(width = 114.dp, height = 54.dp)
        .testTag("live_organic_waveform")
  ) {
    val w = size.width
    val h = size.height
    val midY = h * 0.50f
    val maxPeak = h * 0.45f

    // 4 overlapping organic translucent ribbons matching Image 2 (cyan, emerald, purple, magenta-pink)
    val layerColors =
      listOf(
        Color(0xFF22D3EE), // Cyan
        Color(0xFF34D399), // Soft Emerald
        Color(0xFFA855F7), // Purple
        Color(0xFFF472B6), // Pink / Magenta
      )
    val freqMultipliers = floatArrayOf(1.6f, 2.3f, 3.1f, 2.7f)
    val phaseShifts = floatArrayOf(0.0f, 1.35f, 2.7f, 4.1f)
    val directionSigns = floatArrayOf(1f, -1f, 0.88f, -0.92f)
    val bandEnergies =
      floatArrayOf(
        maxOf(smoothAmp, smoothLow),
        maxOf(smoothAmp * 0.85f, smoothMid),
        maxOf(smoothAmp * 0.80f, smoothHigh),
        smoothAmp * 0.92f,
      )

    val steps = 44
    for (layer in 0 until 4) {
      val path = ribbonPaths[layer]
      path.reset()
      val energy = (0.08f + bandEnergies[layer] * 0.92f).coerceIn(0.06f, 1.0f)
      val peakHeight = maxPeak * energy * (if (layer == 0) 1.0f else 0.86f - layer * 0.06f)

      // Upper contour
      for (i in 0..steps) {
        val xNorm = i / steps.toFloat()
        val x = xNorm * w
        // Bell envelope tapers smoothly to 0 at left & right edges
        val envelope = sin(xNorm * PI.toFloat()).let { it * it }
        val harmonic =
          sin(xNorm * freqMultipliers[layer] * PI.toFloat() + phase * directionSigns[layer] + phaseShifts[layer])
        val upperY = midY - (envelope * peakHeight * (0.45f + 0.55f * kotlin.math.abs(harmonic)))
        if (i == 0) path.moveTo(x, upperY) else path.lineTo(x, upperY)
      }
      // Lower symmetric/organic contour back to start
      for (i in steps downTo 0) {
        val xNorm = i / steps.toFloat()
        val x = xNorm * w
        val envelope = sin(xNorm * PI.toFloat()).let { it * it }
        val harmonic =
          cos(xNorm * (freqMultipliers[layer] * 0.92f) * PI.toFloat() - phase * directionSigns[layer] + phaseShifts[layer])
        val lowerY = midY + (envelope * peakHeight * (0.38f + 0.54f * kotlin.math.abs(harmonic)))
        path.lineTo(x, lowerY)
      }
      path.close()

      val baseCol = layerColors[layer]
      drawPath(
        path = path,
        brush =
          Brush.horizontalGradient(
            colors =
              listOf(
                Color.Transparent,
                baseCol.copy(alpha = (0.24f + smoothAmp * 0.30f).coerceIn(0.14f, 0.58f)),
                Color.White.copy(alpha = (0.14f + smoothAmp * 0.24f).coerceIn(0.08f, 0.38f)),
                baseCol.copy(alpha = (0.24f + smoothAmp * 0.30f).coerceIn(0.14f, 0.58f)),
                Color.Transparent,
              )
          ),
      )
      drawPath(
        path = path,
        color = baseCol.copy(alpha = (0.42f + smoothAmp * 0.42f).coerceIn(0.25f, 0.88f)),
        style = Stroke(width = 1.15.dp.toPx(), cap = StrokeCap.Round),
      )
    }

    // Crisp white-cyan central glowing horizontal spine tapering cleanly at both ends
    drawLine(
      brush =
        Brush.horizontalGradient(
          colors =
            listOf(
              Color.Transparent,
              Color(0xFF67E8F9).copy(alpha = 0.72f),
              Color.White.copy(alpha = 0.94f),
              Color(0xFF67E8F9).copy(alpha = 0.72f),
              Color.Transparent,
            )
        ),
      start = Offset(w * 0.04f, midY),
      end = Offset(w * 0.96f, midY),
      strokeWidth = (1.6f + smoothAmp * 1.1f).dp.toPx(),
      cap = StrokeCap.Round,
    )
  }
}

/**
 * Small Floating Iridescent Smoke Orb matching Reference Image 2:
 * - Soft pink, purple, cyan, electric blue translucent swirling smoke layers
 * - Luminous rim + glowing white/cyan 4-point center star
 * - Subtle drifting micro-particles around the upper-right perimeter
 */
@Composable
fun SmallIridescentSmokeOrb(
  reactiveFrame: OrbReactiveFrame,
  isPaused: Boolean = false,
  isMuted: Boolean = false,
  reducedMotion: Boolean = false,
  orbDiameter: Dp = 66.dp,
  modifier: Modifier = Modifier,
) {
  val rawEnergy =
    if (isPaused || isMuted) 0f else reactiveFrame.reactiveAmplitude.coerceIn(0f, 1f)
  val smoothEnergy by
    animateFloatAsState(
      targetValue = rawEnergy,
      animationSpec = tween(100),
      label = "small_orb_energy",
    )

  val transition = rememberInfiniteTransition(label = "small_iridescent_orb")
  val swirlPhase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = if (reducedMotion) 9000 else 3800, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "swirl_phase",
    )
  val pulse by
    transition.animateFloat(
      initialValue = 0.96f,
      targetValue = if (reducedMotion) 1.0f else 1.045f,
      animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "orb_pulse",
    )

  val sparklePath = remember { Path() }
  val particleOffsets = remember {
    listOf(
      Triple(0.86f, 0.22f, 1.5f),
      Triple(0.95f, 0.34f, 1.2f),
      Triple(0.91f, 0.56f, 1.6f),
      Triple(0.82f, 0.14f, 1.1f),
      Triple(0.14f, 0.25f, 1.2f),
      Triple(0.18f, 0.78f, 1.3f),
      Triple(0.88f, 0.72f, 1.1f),
    )
  }

  Box(
    modifier = modifier.size(orbDiameter + 18.dp).testTag("live_iridescent_smoke_orb"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val center = Offset(cx, cy)
      val orbRadius = (orbDiameter.toPx() / 2f) * pulse * (1f + smoothEnergy * 0.06f)

      // 1. Soft atmospheric outer nebula halo (cyan + violet/pink)
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                Color(0xFF38BDF8).copy(alpha = (0.30f + smoothEnergy * 0.24f).coerceIn(0.18f, 0.58f)),
                Color(0xFFEC4899).copy(alpha = (0.16f + smoothEnergy * 0.16f).coerceIn(0.10f, 0.36f)),
                Color.Transparent,
              ),
            center = center,
            radius = orbRadius * 1.48f,
          ),
        radius = orbRadius * 1.48f,
        center = center,
      )

      // 2. Translucent iridescent sphere base
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                Color(0xFF1E1B4B).copy(alpha = 0.85f),
                Color(0xFF0F172A).copy(alpha = 0.92f),
                Color(0xFF090D1C),
              ),
            center = center,
            radius = orbRadius,
          ),
        radius = orbRadius,
        center = center,
      )

      // 3. Swirling pink / purple / cyan / blue translucent smoke puffs inside the sphere
      val puffSpecs =
        listOf(
          Triple(Color(0xFFEC4899), 0.0f, 0.62f), // Neon Pink
          Triple(Color(0xFF22D3EE), 2.1f, 0.68f), // Cyan
          Triple(Color(0xFFA855F7), 4.2f, 0.60f), // Purple
          Triple(Color(0xFF3B82F6), 1.05f, 0.64f), // Electric Blue
        )
      puffSpecs.forEachIndexed { idx, (col, baseOffset, sizeRatio) ->
        val angle = swirlPhase * (if (idx % 2 == 0) 1f else -0.85f) + baseOffset
        val shiftR = orbRadius * (0.28f + smoothEnergy * 0.12f)
        val pCenter =
          Offset(
            x = cx + cos(angle) * shiftR,
            y = cy + sin(angle * 1.1f) * shiftR,
          )
        val pRad = orbRadius * sizeRatio
        drawCircle(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  col.copy(alpha = if (isMuted) 0.22f else (0.56f + smoothEnergy * 0.30f).coerceIn(0.35f, 0.88f)),
                  col.copy(alpha = 0.14f),
                  Color.Transparent,
                ),
              center = pCenter,
              radius = pRad,
            ),
          radius = pRad,
          center = pCenter,
        )
      }

      // 4. Luminous iridescent rim border
      drawCircle(
        brush =
          Brush.sweepGradient(
            colors =
              listOf(
                Color(0xFFEC4899),
                Color(0xFFA855F7),
                Color(0xFF22D3EE),
                Color(0xFF38BDF8),
                Color(0xFFEC4899),
              ),
            center = center,
          ),
        radius = orbRadius * 0.98f,
        center = center,
        style = Stroke(width = 2.0.dp.toPx()),
      )

      // 5. Crisp 4-point white-cyan Gemini sparkle in the sphere center
      val sr = orbRadius * (0.44f + smoothEnergy * 0.08f)
      val waist = sr * 0.24f
      sparklePath.reset()
      sparklePath.moveTo(cx, cy - sr)
      sparklePath.cubicTo(cx + waist * 0.45f, cy - waist, cx + waist, cy - waist * 0.45f, cx + sr, cy)
      sparklePath.cubicTo(cx + waist, cy + waist * 0.45f, cx + waist * 0.45f, cy + waist, cx, cy + sr)
      sparklePath.cubicTo(cx - waist * 0.45f, cy + waist, cx - waist, cy + waist * 0.45f, cx - sr, cy)
      sparklePath.cubicTo(cx - waist, cy - waist * 0.45f, cx - waist * 0.45f, cy - waist, cx, cy - sr)
      sparklePath.close()

      drawPath(
        path = sparklePath,
        brush =
          Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFA5F3FC), Color(0xFF67E8F9)),
            center = center,
            radius = sr,
          ),
      )

      // 6. Subtle floating cyan/emerald micro-particles (clean & non-cluttered)
      particleOffsets.forEachIndexed { idx, (fx, fy, radDp) ->
        val drift = if (reducedMotion) 0f else sin(swirlPhase + idx * 1.1f) * 3.dp.toPx()
        val px = size.width * fx + drift * 0.6f
        val py = size.height * fy + drift
        val col = if (idx % 2 == 0) Color(0xFF67E8F9) else Color(0xFFA7F3D0)
        drawCircle(
          color = col.copy(alpha = 0.55f + smoothEnergy * 0.35f),
          radius = radDp.dp.toPx(),
          center = Offset(px, py),
        )
      }
    }
  }
}

/**
 * Draggable Live Assistant Orb + Organic Waveform stage matching Reference Image 2:
 * - Default anchor: bottom-right directly above the Rainbow Live button.
 * - Freely draggable within safe screen bounds.
 * - Dragging reveals a clean bottom-center circular 'X' dismiss target;
 *   dropping inside 'X' calls [onDismissLive] and exits cleanly back to Standard State.
 */
@Composable
fun DraggableLiveOrbStage(
  reactiveFrame: OrbReactiveFrame,
  isPaused: Boolean,
  isMuted: Boolean,
  reducedMotion: Boolean,
  onOrbTap: () -> Unit,
  onDismissLive: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val maxWidthPx = with(density) { maxWidth.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    val stageWidthPx = with(density) { 116.dp.toPx() }
    val stageHeightPx = with(density) { 142.dp.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val bottomDockReservedPx = with(density) { 104.dp.toPx() }

    val defaultX = (maxWidthPx - stageWidthPx - marginPx).coerceAtLeast(marginPx)
    val defaultY = (maxHeightPx - stageHeightPx - bottomDockReservedPx).coerceAtLeast(marginPx)

    var hasUserDragged by remember { mutableStateOf(false) }
    var posX by remember { mutableFloatStateOf(defaultX) }
    var posY by remember { mutableFloatStateOf(defaultY) }
    var isDragging by remember { mutableStateOf(false) }
    var isHoveringDismiss by remember { mutableStateOf(false) }

    val activeX =
      if (hasUserDragged) posX.coerceIn(marginPx, (maxWidthPx - stageWidthPx - marginPx).coerceAtLeast(marginPx))
      else defaultX
    val activeY =
      if (hasUserDragged) posY.coerceIn(marginPx, (maxHeightPx - stageHeightPx - marginPx).coerceAtLeast(marginPx))
      else defaultY

    val dismissCenterX = maxWidthPx / 2f
    val dismissCenterY = (maxHeightPx - with(density) { 132.dp.toPx() }).coerceAtLeast(stageHeightPx)
    val dismissDropRadiusPx = with(density) { 64.dp.toPx() }

    // Bottom-center circular 'X' dismiss drop zone shown when dragging
    AnimatedVisibility(
      visible = isDragging,
      enter = fadeIn() + scaleIn(initialScale = 0.8f),
      exit = fadeOut() + scaleOut(targetScale = 0.8f),
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 104.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = if (isHoveringDismiss) Color(0xFFDC2626) else Color(0xFF0E1424).copy(alpha = 0.92f),
        border =
          BorderStroke(
            width = if (isHoveringDismiss) 2.dp else 1.2.dp,
            color = if (isHoveringDismiss) Color.White else Color.White.copy(alpha = 0.42f),
          ),
        shadowElevation = 12.dp,
        modifier = Modifier.size(if (isHoveringDismiss) 62.dp else 54.dp).testTag("live_orb_dismiss_x"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Drop here to stop Live Voice",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
          )
        }
      }
    }

    // Floating Waveform + Orb Cluster
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
      modifier =
        Modifier
          .offset { IntOffset(activeX.roundToInt(), activeY.roundToInt()) }
          .pointerInput(Unit) {
            detectTapGestures(onTap = { onOrbTap() })
          }
          .pointerInput(maxWidthPx, maxHeightPx) {
            detectDragGestures(
              onDragStart = {
                if (!hasUserDragged) {
                  posX = defaultX
                  posY = defaultY
                  hasUserDragged = true
                }
                isDragging = true
                isHoveringDismiss = false
              },
              onDrag = { change, dragAmount ->
                change.consume()
                posX = (posX + dragAmount.x).coerceIn(marginPx, (maxWidthPx - stageWidthPx - marginPx).coerceAtLeast(marginPx))
                posY = (posY + dragAmount.y).coerceIn(marginPx, (maxHeightPx - stageHeightPx - marginPx).coerceAtLeast(marginPx))
                val orbCenterX = posX + stageWidthPx / 2f
                val orbCenterY = posY + stageHeightPx * 0.70f
                val dist = hypot(orbCenterX - dismissCenterX, orbCenterY - dismissCenterY)
                val nowHover = dist <= dismissDropRadiusPx
                if (nowHover && !isHoveringDismiss) {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                isHoveringDismiss = nowHover
              },
              onDragEnd = {
                val shouldDismiss = isHoveringDismiss
                isDragging = false
                isHoveringDismiss = false
                if (shouldDismiss) {
                  hasUserDragged = false
                  onDismissLive()
                }
              },
              onDragCancel = {
                isDragging = false
                isHoveringDismiss = false
              },
            )
          }
          .testTag("draggable_live_orb_stage"),
    ) {
      OrganicLiveWaveform(
        reactiveFrame = reactiveFrame,
        isPaused = isPaused,
        isMuted = isMuted,
        reducedMotion = reducedMotion,
      )
      SmallIridescentSmokeOrb(
        reactiveFrame = reactiveFrame,
        isPaused = isPaused,
        isMuted = isMuted,
        reducedMotion = reducedMotion,
        orbDiameter = 66.dp,
      )
    }
  }
}
