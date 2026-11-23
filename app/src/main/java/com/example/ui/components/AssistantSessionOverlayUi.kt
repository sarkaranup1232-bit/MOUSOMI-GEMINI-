package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.assistant.AssistantVisualState
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.assistant.EdgeActivationSignal
import com.example.core.device.PendingPhoneAction
import com.example.core.device.PendingSocialAction
import com.example.core.edge.EdgeLightRenderParams
import com.example.core.voice.OrbReactiveFrame
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.WarmAmber

/**
 * Unified System-Level Assistant Overlay UI strictly aligned with Reference Image 1 & Reference Image 2:
 *
 * State 1 ([AssistantVisualState.STANDARD_ASSISTANT]):
 * - Pitch-black backdrop
 * - Full-screen rounded neon Border Light (cyan -> royal blue -> violet -> magenta -> soft gold)
 * - Left-aligned dark frosted-glass "Share screen with Live" capsule above bottom bar
 * - Bottom glassmorphic Chat Bar: '+' button, "Ask Mousomi..." input, Mic icon, Rainbow circular button
 *
 * State 2 ([AssistantVisualState.LIVE_ASSISTANT]):
 * - Preserves 100% of State 1 unchanged in fixed positions
 * - Overlays the small floating Draggable Iridescent Smoke Orb + Organic Audio Waveform bottom-right
 *   above the Rainbow button, with drag-to-'X' dismissal cleanly returning to State 1.
 */
@Composable
fun AssistantSessionOverlayUi(
  visualState: AssistantVisualState,
  edgeLightParams: EdgeLightRenderParams,
  reactiveFrame: OrbReactiveFrame,
  inputText: String,
  onInputChanged: (String) -> Unit,
  onSendQuery: (String) -> Unit,
  onMicClick: () -> Unit,
  onLiveButtonClick: () -> Unit,
  onDismissLiveToStandard: () -> Unit,
  onShareScreenWithLive: () -> Unit,
  onStopGeneration: () -> Unit = {},
  onOpenFullApp: () -> Unit = {},
  onOpenScreenSelection: () -> Unit = {},
  onOpenDrawingStudio: () -> Unit = {},
  onOpenPhotoPicker: () -> Unit = {},
  onCloseSession: () -> Unit = {},
  isGenerating: Boolean = false,
  isVoiceListening: Boolean = false,
  voiceRmsNormalized: Float = 0f,
  isDictationSessionActive: Boolean = false,
  isDictationPaused: Boolean = false,
  voiceErrorText: String? = null,
  onStartDictation: () -> Unit = onMicClick,
  onPauseDictation: () -> Unit = {},
  onStopDictation: () -> Unit = {},
  onRetryDictation: () -> Unit = onMicClick,
  isLivePaused: Boolean = false,
  isLiveMuted: Boolean = false,
  isScreenShareActive: Boolean = false,
  userTranscript: String = "",
  assistantResponseText: String = "",
  statusBannerText: String? = null,
  pendingPhoneAction: PendingPhoneAction? = null,
  onConfirmPhoneAction: (PendingPhoneAction) -> Unit = {},
  onDismissPhoneAction: () -> Unit = {},
  pendingSocialAction: PendingSocialAction? = null,
  onConfirmSocialAction: (PendingSocialAction) -> Unit = {},
  onDismissSocialAction: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var isPlusMenuOpen by remember { mutableStateOf(false) }
  val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Color.Transparent)
        .testTag("system_assistant_root")
  ) {
    // Tapping empty space above the bottom dock cleanly dismisses the compact floating Assistant Session
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onCloseSession,
          )
    )

    // Soft bottom vignette scrim ONLY behind the floating dock so wallpaper/Home screen remains visible above
    Box(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(260.dp)
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f), Color.Black.copy(alpha = 0.62f))
            )
          )
    )

    // Full-screen rounded neon perimeter Border Light
    EdgeLightPerimeterOverlay(params = edgeLightParams)

    // App-side neon corner gesture-feedback arcs immediately following successful system swipe invocation
    CornerGestureNeonFeedbackArcs(activationSignal = edgeLightParams.activationSignal)

    // Optional clean translucent response / transcript / confirmation card (only appears when content exists)
    val hasCardContent =
      assistantResponseText.isNotBlank() ||
        userTranscript.isNotBlank() ||
        !statusBannerText.isNullOrBlank() ||
        pendingPhoneAction != null ||
        pendingSocialAction != null ||
        isPlusMenuOpen

    AnimatedVisibility(
      visible = hasCardContent,
      enter = fadeIn() + slideInVertically { -it / 6 },
      exit = fadeOut(),
      modifier =
        Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(horizontal = 18.dp, vertical = 18.dp)
          .widthIn(max = 600.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0E1320).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (visualState == AssistantVisualState.LIVE_ASSISTANT) "Mousomi Live"
                else "Mousomi Assistant",
              color = NeonCyan,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              IconButton(
                onClick = onOpenFullApp,
                modifier = Modifier.size(34.dp).testTag("assistant_open_app_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.OpenInNew,
                  contentDescription = "Open full Mousomi app",
                  tint = Color.White.copy(alpha = 0.82f),
                  modifier = Modifier.size(18.dp),
                )
              }
              IconButton(
                onClick = onCloseSession,
                modifier = Modifier.size(34.dp).testTag("assistant_close_session_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Close assistant",
                  tint = Color.White.copy(alpha = 0.82f),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }

          if (!statusBannerText.isNullOrBlank()) {
            Text(
              text = statusBannerText,
              color = WarmAmber,
              fontSize = 12.5.sp,
            )
          }

          if (userTranscript.isNotBlank()) {
            Text(
              text = "\"$userTranscript\"",
              color = Color.White.copy(alpha = 0.78f),
              fontSize = 13.sp,
            )
          }

          if (assistantResponseText.isNotBlank()) {
            Text(
              text = assistantResponseText,
              color = Color.White.copy(alpha = 0.96f),
              fontSize = 14.5.sp,
              lineHeight = 20.sp,
              modifier = Modifier.heightIn(max = 220.dp),
            )
          }

          if (isPlusMenuOpen) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              AssistantQuickToolChip(
                icon = Icons.Outlined.CenterFocusWeak,
                label = "Select Screen",
                onClick = {
                  isPlusMenuOpen = false
                  onOpenScreenSelection()
                },
                modifier = Modifier.weight(1f),
              )
              AssistantQuickToolChip(
                icon = Icons.Outlined.Brush,
                label = "Draw",
                onClick = {
                  isPlusMenuOpen = false
                  onOpenDrawingStudio()
                },
                modifier = Modifier.weight(1f),
              )
              AssistantQuickToolChip(
                icon = Icons.Outlined.PhotoLibrary,
                label = "Photos",
                onClick = {
                  isPlusMenuOpen = false
                  onOpenPhotoPicker()
                },
                modifier = Modifier.weight(1f),
              )
            }
          }

          if (pendingPhoneAction != null) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFF151C2F),
              border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Outlined.Phone, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                  Text(pendingPhoneAction.type.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text(pendingPhoneAction.summary, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { onConfirmPhoneAction(pendingPhoneAction) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                  ) { Text("Confirm") }
                  OutlinedButton(onClick = onDismissPhoneAction) { Text("Cancel", color = Color.White) }
                }
              }
            }
          }

          if (pendingSocialAction != null) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFF151C2F),
              border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "Confirm ${pendingSocialAction.platform.displayName}",
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.sp,
                )
                Text(pendingSocialAction.summary, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { onConfirmSocialAction(pendingSocialAction) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                  ) { Text("Open Composer") }
                  OutlinedButton(onClick = onDismissSocialAction) { Text("Cancel", color = Color.White) }
                }
              }
            }
          }
        }
      }
    }

    // Bottom Dock: Preserved Identical Across Both Image 1 (Standard) & Image 2 (Live)
    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .widthIn(max = 620.dp)
          .windowInsetsPadding(bottomInsets)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Left-aligned dark frosted-glass "Share screen with Live" pill directly above bottom chat bar
      Surface(
        onClick = onShareScreenWithLive,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141824).copy(alpha = 0.88f),
        border =
          BorderStroke(
            width = 1.dp,
            color =
              if (isScreenShareActive) NeonCyan.copy(alpha = 0.75f)
              else Color.White.copy(alpha = 0.16f),
          ),
        shadowElevation = 10.dp,
        modifier = Modifier.testTag("assistant_share_screen_live_pill"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
            contentDescription = null,
            tint = if (isScreenShareActive) NeonCyan else Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = if (isScreenShareActive) "Sharing screen with Live" else "Share screen with Live",
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Inline Chat Bar Voice Waveform + Start/Pause/Stop + Error Retry (compact, non-blocking)
      AnimatedVisibility(
        visible =
          visualState == AssistantVisualState.STANDARD_ASSISTANT &&
            (isDictationSessionActive || !voiceErrorText.isNullOrBlank()),
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        ChatBarVoiceWaveformStrip(
          rmsNormalized = voiceRmsNormalized,
          isListening = isVoiceListening && !isDictationPaused,
          isPaused = isDictationPaused,
          errorMessage = voiceErrorText,
          onStartOrResume = onStartDictation,
          onPause = onPauseDictation,
          onStop = onStopDictation,
          onRetry = onRetryDictation,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      // Bottom Glassmorphic Chat Bar: '+' | "Ask Mousomi..." | Mic | Rainbow Live Waveform Button
      Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF121622).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth().testTag("assistant_bottom_chat_bar"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = { isPlusMenuOpen = !isPlusMenuOpen },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("assistant_plus_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Tools & attachments",
              tint = Color.White.copy(alpha = 0.90f),
              modifier = Modifier.size(23.dp),
            )
          }

          Box(
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (inputText.isEmpty()) {
              Text(
                text = "Ask Mousomi...",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = inputText,
              onValueChange = onInputChanged,
              textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
              cursorBrush = SolidColor(NeonCyan),
              maxLines = 3,
              keyboardOptions =
                KeyboardOptions(
                  capitalization = KeyboardCapitalization.Sentences,
                  imeAction = ImeAction.Send,
                ),
              keyboardActions =
                KeyboardActions(
                  onSend = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty() && !isGenerating) {
                      onSendQuery(trimmed)
                    }
                  }
                ),
              modifier = Modifier.fillMaxWidth().testTag("assistant_text_input"),
            )
          }

          IconButton(
            onClick = onMicClick,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("assistant_mic_button"),
          ) {
            Icon(
              imageVector = if (isLiveMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
              contentDescription = "Voice dictation",
              tint =
                when {
                  isLiveMuted -> WarmAmber
                  isVoiceListening -> NeonCyan
                  else -> Color.White.copy(alpha = 0.88f)
                },
              modifier = Modifier.size(22.dp),
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          AnimatedContent(
            targetState =
              when {
                isGenerating -> 2
                inputText.isNotBlank() -> 1
                else -> 0
              },
            label = "assistant_right_button",
          ) { mode ->
            when (mode) {
              2 ->
                Surface(
                  onClick = onStopGeneration,
                  shape = CircleShape,
                  color = Color(0xFFDC2626),
                  modifier = Modifier.size(44.dp).testTag("assistant_stop_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Rounded.Stop,
                      contentDescription = "Stop",
                      tint = Color.White,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              1 ->
                Surface(
                  onClick = { onSendQuery(inputText.trim()) },
                  shape = CircleShape,
                  color = ElectricBlue,
                  modifier = Modifier.size(44.dp).testTag("assistant_send_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Send,
                      contentDescription = "Send",
                      tint = Color.White,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              else ->
                RainbowLiveWaveformDiscButton(
                  onClick = onLiveButtonClick,
                  size = 44.dp,
                  isLiveActive = visualState == AssistantVisualState.LIVE_ASSISTANT,
                  isMicActive = isVoiceListening && !isDictationPaused,
                  voiceRmsNormalized = voiceRmsNormalized,
                  contentDescriptionText =
                    if (visualState == AssistantVisualState.LIVE_ASSISTANT) "Stop Live Voice"
                    else "Start Live Voice",
                  modifier = Modifier.testTag("assistant_live_button"),
                )
            }
          }
        }
      }
    }

    // State 2 Overlay: Small Draggable Iridescent Smoke Orb + Organic Audio Waveform above it
    if (visualState == AssistantVisualState.LIVE_ASSISTANT) {
      DraggableLiveOrbStage(
        reactiveFrame = reactiveFrame,
        isPaused = isLivePaused,
        isMuted = isLiveMuted,
        reducedMotion = false,
        onOrbTap = onMicClick,
        onDismissLive = onDismissLiveToStandard,
        modifier = Modifier.windowInsetsPadding(bottomInsets),
      )
    }
  }
}

@Composable
private fun AssistantQuickToolChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = Color(0xFF171E30),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(imageVector = icon, contentDescription = label, tint = NeonCyan, modifier = Modifier.size(18.dp))
      Text(text = label, color = Color.White, fontSize = 11.5.sp, maxLines = 1)
    }
  }
}

/**
 * App-side curved neon gesture-feedback indicator rendered inside Mousomi's Assistant Session
 * immediately after successful bottom-corner swipe invocation.
 *
 * Note: Android/Samsung SystemUI owns the pre-invocation white corner handle drawn over other apps;
 * this indicator provides matching native-style rounded neon feedback inside Mousomi's session
 * window without altering the phone-edge Border Light or system navigation bar.
 */
@Composable
private fun CornerGestureNeonFeedbackArcs(
  activationSignal: EdgeActivationSignal,
  modifier: Modifier = Modifier,
) {
  if (activationSignal.token <= 0L || activationSignal.edge == EdgeActivationEdge.NONE) return

  val progress = remember { Animatable(0f) }
  LaunchedEffect(activationSignal.token) {
    progress.snapTo(0f)
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 540, easing = FastOutSlowInEasing),
    )
  }

  val p = progress.value
  if (p >= 0.995f) return

  val localView = LocalView.current
  val density = LocalDensity.current.density
  val cornerRadii =
    remember(density, activationSignal.token) {
      val fallback = 34f * density
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val insets = localView.rootWindowInsets
        val bl = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.toFloat()?.takeIf { it > 4f } ?: fallback
        val br = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius?.toFloat()?.takeIf { it > 4f } ?: fallback
        bl to br
      } else {
        fallback to fallback
      }
    }

  val paint =
    remember {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
      }
    }
  val blurFilter = remember(density) { BlurMaskFilter((7f * density).coerceAtLeast(4f), BlurMaskFilter.Blur.NORMAL) }
  val fullPath = remember { Path() }
  val segmentPath = remember { Path() }
  val pathMeasure = remember { PathMeasure() }
  val arcRect = remember { RectF() }
  val shaderMatrix = remember { android.graphics.Matrix() }
  var cachedLeftShader by remember { mutableStateOf<LinearGradient?>(null) }
  var cachedRightShader by remember { mutableStateOf<LinearGradient?>(null) }
  var cachedShaderW by remember { mutableStateOf(-1f) }
  var cachedShaderH by remember { mutableStateOf(-1f) }

  // Smooth fade-in then graceful fade-out as the session settles
  val fadeAlpha =
    when {
      p < 0.14f -> (p / 0.14f).coerceIn(0f, 1f)
      p > 0.58f -> (1f - (p - 0.58f) / 0.42f).coerceIn(0f, 1f)
      else -> 1f
    }
  val sweepReveal = (0.28f + (p / 0.55f).coerceIn(0f, 1f) * 0.72f).coerceIn(0.28f, 1f)
  val liftOffsetDp = p * 10f

  Canvas(
    modifier =
      modifier
        .fillMaxSize()
        .testTag("corner_gesture_neon_indicator")
  ) {
    val w = size.width
    val h = size.height
    if (w < 40f || h < 40f) return@Canvas

    val baseInset = 10f * this.density
    val liftPx = liftOffsetDp * this.density
    val inset = baseInset + liftPx * 0.25f
    val coreWidth = 3.5f * this.density
    val glowWidth = 9.5f * this.density
    val sideRun = 38f * this.density
    val bottomRun = 28f * this.density
    val maxCornerBand = 56f * this.density + sideRun + bottomRun

    if (cachedLeftShader == null || cachedRightShader == null || cachedShaderW != w || cachedShaderH != h) {
      cachedShaderW = w
      cachedShaderH = h
      val colorsLeft =
        intArrayOf(
          0xFF22D3EE.toInt(), // Cyan
          0xFF3B82F6.toInt(), // Electric Blue
          0xFF8B5CF6.toInt(), // Violet
          0xFFEC4899.toInt(), // Magenta
          0xFFFDE68A.toInt(), // Subtle Soft-Gold highlight
        )
      val colorsRight =
        intArrayOf(
          0xFFFDE68A.toInt(), // Subtle Soft-Gold highlight
          0xFFEC4899.toInt(), // Magenta
          0xFF8B5CF6.toInt(), // Violet
          0xFF3B82F6.toInt(), // Electric Blue
          0xFF22D3EE.toInt(), // Cyan
        )
      cachedLeftShader =
        LinearGradient(
          baseInset,
          h - baseInset - maxCornerBand,
          baseInset + maxCornerBand,
          h - baseInset,
          colorsLeft,
          null,
          Shader.TileMode.CLAMP,
        )
      cachedRightShader =
        LinearGradient(
          w - baseInset - maxCornerBand,
          h - baseInset,
          w - baseInset,
          h - baseInset - maxCornerBand,
          colorsRight,
          null,
          Shader.TileMode.CLAMP,
        )
    }

    fun drawCornerArc(isLeft: Boolean) {
      val radius = ((if (isLeft) cornerRadii.first else cornerRadii.second) - inset * 0.65f)
        .coerceIn(18f * this.density, 56f * this.density)
      val yBottom = h - inset - (liftPx * 0.35f)

      fullPath.reset()
      if (isLeft) {
        val xLeft = inset
        fullPath.moveTo(xLeft + radius + bottomRun, yBottom)
        fullPath.lineTo(xLeft + radius, yBottom)
        arcRect.set(xLeft, yBottom - 2f * radius, xLeft + 2f * radius, yBottom)
        fullPath.arcTo(arcRect, 90f, 90f, false)
        fullPath.lineTo(xLeft, yBottom - radius - sideRun)
      } else {
        val xRight = w - inset
        fullPath.moveTo(xRight - radius - bottomRun, yBottom)
        fullPath.lineTo(xRight - radius, yBottom)
        arcRect.set(xRight - 2f * radius, yBottom - 2f * radius, xRight, yBottom)
        fullPath.arcTo(arcRect, 90f, -90f, false)
        fullPath.lineTo(xRight, yBottom - radius - sideRun)
      }

      pathMeasure.setPath(fullPath, false)
      val len = pathMeasure.length
      if (len <= 1f) return
      val startD = (len * 0.12f * p).coerceAtMost(len * 0.3f)
      val endD = (startD + len * sweepReveal).coerceAtMost(len)
      segmentPath.reset()
      pathMeasure.getSegment(startD, endD, segmentPath, true)

      val shader = if (isLeft) cachedLeftShader!! else cachedRightShader!!
      shaderMatrix.reset()
      shaderMatrix.setTranslate(0f, -liftPx * 0.35f)
      shader.setLocalMatrix(shaderMatrix)

      drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        paint.shader = shader

        // Soft outer glow (restrained, not overly bright)
        paint.strokeWidth = glowWidth
        paint.alpha = (fadeAlpha * 0.34f * 255f).toInt().coerceIn(0, 255)
        paint.maskFilter = blurFilter
        nc.drawPath(segmentPath, paint)

        // Crisp rounded neon stroke
        paint.strokeWidth = coreWidth
        paint.alpha = (fadeAlpha * 0.90f * 255f).toInt().coerceIn(0, 255)
        paint.maskFilter = null
        nc.drawPath(segmentPath, paint)
      }
    }

    drawCornerArc(isLeft = true)
    drawCornerArc(isLeft = false)
  }
}
