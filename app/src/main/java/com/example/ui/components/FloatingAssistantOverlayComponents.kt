package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.automirrored.outlined.StopScreenShare
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.overlay.FloatingAttachmentItem
import com.example.core.overlay.FloatingChatTurn
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenRecordingPhase
import com.example.core.screen.ScreenRecordingUiState
import com.example.core.voice.LiveVoiceSessionManager
import com.example.core.voice.LiveVoiceSessionStatus
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.VoiceAudioMetrics
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassBorder
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.WarmAmber
import java.io.File
import kotlin.math.sin

/**
 * Compact translucent Liquid Glass Floating Control Bar rendered above other apps.
 * Matches the neon cyan-to-violet bordered floating control pill with:
 * `<` collapse/expand, Draw/Annotate, `[o]` Screen Selection, `||` Pause/Resume, solid Red Stop,
 * plus a dark duration timer (`00:00:24`) & Assistant Profile trigger row underneath.
 */
@Composable
fun FloatingControlBar(
  isPanelOpen: Boolean,
  isProfileMenuOpen: Boolean,
  isPaused: Boolean,
  isBusyOrListening: Boolean,
  onExpandClick: () -> Unit,
  onScreenSelectionClick: () -> Unit,
  onProfileClick: () -> Unit,
  onPauseToggleClick: () -> Unit,
  onCloseClick: () -> Unit,
  modifier: Modifier = Modifier,
  elapsedSeconds: Long = 0L,
  isDrawActive: Boolean = false,
  onDrawClick: () -> Unit = onScreenSelectionClick,
) {
  val borderBrush =
    if (isPaused) {
      Brush.horizontalGradient(listOf(WarmAmber.copy(alpha = 0.75f), NeonPurple.copy(alpha = 0.55f)))
    } else {
      Brush.horizontalGradient(listOf(NeonCyan, ElectricBlue, NeonViolet, NeonPurple))
    }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color(0xFF0A0F1E).copy(alpha = 0.88f),
      border = BorderStroke(if (isBusyOrListening && !isPaused) 1.7.dp else 1.35.dp, borderBrush),
      shadowElevation = 18.dp,
      modifier = Modifier.testTag("floating_control_bar"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // 1. Back / Expand / Collapse Floating Chat Panel (`<`)
        Box(
          modifier =
            Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(if (isPanelOpen) NeonCyan.copy(alpha = 0.16f) else Color.Transparent)
              .clickable(onClick = onExpandClick)
              .testTag("floating_bar_expand"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = if (isPanelOpen) "Collapse assistant panel" else "Expand assistant panel",
            tint = if (isPanelOpen) NeonCyan else Color.White,
            modifier = Modifier.size(22.dp),
          )
        }

        FloatingControlBarDivider()

        // 2. Draw / Annotate Screen (`Pencil`)
        Box(
          modifier =
            Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(if (isDrawActive) ElectricBlue.copy(alpha = 0.22f) else Color.Transparent)
              .clickable(onClick = onDrawClick)
              .testTag("floating_bar_draw"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Draw or annotate screen",
            tint = if (isDrawActive) NeonCyan else Color.White,
            modifier = Modifier.size(19.dp),
          )
        }

        FloatingControlBarDivider()

        // 3. Screen Selection (`[o]`)
        Box(
          modifier =
            Modifier
              .size(42.dp)
              .clip(CircleShape)
              .clickable(onClick = onScreenSelectionClick)
              .testTag("floating_bar_screen_select"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.CenterFocusWeak,
            contentDescription = "Select screen region",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
          )
        }

        FloatingControlBarDivider()

        // 4. Pause / Resume (`||`)
        Box(
          modifier =
            Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(if (isPaused) WarmAmber.copy(alpha = 0.22f) else Color.Transparent)
              .clickable(onClick = onPauseToggleClick)
              .testTag("floating_bar_pause"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = if (isPaused) "Resume Assistant" else "Pause Assistant",
            tint = if (isPaused) WarmAmber else Color.White,
            modifier = Modifier.size(21.dp),
          )
        }

        FloatingControlBarDivider()

        // 5. Close / Stop Floating Assistant (Solid Red rounded square)
        Box(
          modifier =
            Modifier
              .size(42.dp)
              .clip(CircleShape)
              .clickable(onClick = onCloseClick)
              .testTag("floating_bar_close"),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFEF4444))
          )
        }
      }
    }

    // Companion row below right edge: Live Timer (`00:00:24`) + Profile / Edge Light studio toggle
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (elapsedSeconds > 0L || isBusyOrListening) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFF080D1B).copy(alpha = 0.86f),
          border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.14f)),
          modifier = Modifier.testTag("floating_live_timer_pill"),
        ) {
          Text(
            text = LiveVoiceSessionManager.formatElapsedMmSs(elapsedSeconds),
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Surface(
        onClick = onProfileClick,
        shape = CircleShape,
        color = if (isProfileMenuOpen) NeonPurple.copy(alpha = 0.36f) else Color(0xFF080D1B).copy(alpha = 0.84f),
        border =
          BorderStroke(
            width = 0.9.dp,
            color = if (isProfileMenuOpen) NeonCyan else Color.White.copy(alpha = 0.2f),
          ),
        modifier = Modifier.size(32.dp).testTag("floating_bar_profile"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.PersonOutline,
            contentDescription = "Assistant Profile and Edge Light settings",
            tint = if (isProfileMenuOpen) NeonCyan else Color.White.copy(alpha = 0.88f),
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun FloatingControlBarDivider() {
  Box(
    modifier =
      Modifier
        .padding(horizontal = 1.dp)
        .width(1.dp)
        .height(18.dp)
        .background(Color.White.copy(alpha = 0.22f))
  )
}

/**
 * Assistant Profile & live Edge Lighting popover card inside the floating overlay system.
 * Exposes adjustable brightness, glow width, animation speed, battery-saver dimming, and app navigation.
 */
@Composable
fun FloatingProfileQuickMenu(
  activeModelLabel: String,
  edgeLightConfig: EdgeLightConfig,
  onUpdateEdgeLight: (EdgeLightConfig) -> Unit,
  onOpenControlCenter: () -> Unit,
  onOpenSettings: () -> Unit,
  onClearHistory: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = LiquidGlassDark.copy(alpha = 0.94f),
    border = BorderStroke(1.dp, LiquidGlassBorder),
    shadowElevation = 20.dp,
    modifier = modifier.fillMaxWidth().widthIn(max = 380.dp).testTag("floating_profile_menu"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GeminiFourPointSparkle(size = 18.dp, animated = true)
          Column {
            Text(
              text = "Assistant Profile",
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
              fontSize = 14.sp,
            )
            Text(
              text = "Model: ${activeModelLabel.ifBlank { "Mousomi Auto" }}",
              color = NeonCyan,
              fontSize = 11.sp,
            )
          }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close profile menu",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = edgeLightConfig.assistantEdgeGlowEnabled,
          onClick = {
            onUpdateEdgeLight(
              edgeLightConfig.copy(assistantEdgeGlowEnabled = !edgeLightConfig.assistantEdgeGlowEnabled)
            )
          },
          label = { Text("Edge Glow", fontSize = 12.sp) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
              selectedLabelColor = Color.White,
              labelColor = Color.White.copy(alpha = 0.78f),
            ),
          modifier = Modifier.testTag("profile_toggle_edge_glow"),
        )
        FilterChip(
          selected = edgeLightConfig.batterySaverMode,
          onClick = {
            onUpdateEdgeLight(
              edgeLightConfig.copy(batterySaverMode = !edgeLightConfig.batterySaverMode)
            )
          },
          label = { Text("Battery Saver", fontSize = 12.sp) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = WarmAmber.copy(alpha = 0.24f),
              selectedLabelColor = Color.White,
              labelColor = Color.White.copy(alpha = 0.78f),
            ),
          modifier = Modifier.testTag("profile_toggle_battery_saver"),
        )
      }

      Text(
        text = "Brightness (${(edgeLightConfig.brightness * 100).toInt()}%)",
        color = Color.White.copy(alpha = 0.88f),
        fontSize = 11.5.sp,
      )
      Slider(
        value = edgeLightConfig.brightness.coerceIn(0.20f, 1.0f),
        onValueChange = { onUpdateEdgeLight(edgeLightConfig.copy(brightness = it)) },
        valueRange = 0.20f..1.0f,
        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
        modifier = Modifier.height(28.dp).testTag("profile_brightness_slider"),
      )

      Text(
        text = "Glow Width (${edgeLightConfig.borderThicknessDp.toInt()} dp)",
        color = Color.White.copy(alpha = 0.88f),
        fontSize = 11.5.sp,
      )
      Slider(
        value = edgeLightConfig.borderThicknessDp.coerceIn(2f, 12f),
        onValueChange = { onUpdateEdgeLight(edgeLightConfig.copy(borderThicknessDp = it)) },
        valueRange = 2f..12f,
        colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue),
        modifier = Modifier.height(28.dp).testTag("profile_width_slider"),
      )

      Text(
        text = "Animation Speed (${"%.1f".format(edgeLightConfig.animationSpeed)}x)",
        color = Color.White.copy(alpha = 0.88f),
        fontSize = 11.5.sp,
      )
      Slider(
        value = edgeLightConfig.animationSpeed.coerceIn(0.3f, 2.2f),
        onValueChange = { onUpdateEdgeLight(edgeLightConfig.copy(animationSpeed = it)) },
        valueRange = 0.3f..2.2f,
        colors = SliderDefaults.colors(thumbColor = NeonPurple, activeTrackColor = NeonPurple),
        modifier = Modifier.height(28.dp).testTag("profile_speed_slider"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          onClick = onOpenControlCenter,
          shape = RoundedCornerShape(14.dp),
          color = Color.White.copy(alpha = 0.1f),
          modifier = Modifier.weight(1f).testTag("profile_open_studio"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Edge Studio", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
          }
        }
        Surface(
          onClick = onOpenSettings,
          shape = RoundedCornerShape(14.dp),
          color = Color.White.copy(alpha = 0.1f),
          modifier = Modifier.weight(1f).testTag("profile_open_settings"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(Icons.Outlined.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Model Setup", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
          }
        }
      }

      TextButton(
        onClick = onClearHistory,
        modifier = Modifier.align(Alignment.End).testTag("profile_clear_history"),
      ) {
        Text("Clear Floating Chat History", color = Color.White.copy(alpha = 0.72f), fontSize = 11.5.sp)
      }
    }
  }
}

/**
 * Bottom-floating Liquid Glass AI Chat Panel with scrollable conversation history,
 * pending image attachment chips & pre-send visual preview, microphone toggle,
 * image picker button, suggestion pills, and keyboard-safe composer.
 */
@Composable
fun FloatingChatPanelCard(
  promptText: String,
  turns: List<FloatingChatTurn>,
  pendingAttachments: List<FloatingAttachmentItem>,
  previewingAttachment: FloatingAttachmentItem?,
  liveUserTranscript: String,
  activeModelLabel: String,
  statusBanner: String?,
  responseState: AiResponseState,
  voiceState: VoiceState,
  liveStatus: LiveVoiceSessionStatus,
  isPaused: Boolean,
  maxCardHeightDp: Int,
  onPromptChanged: (String) -> Unit,
  onSendPrompt: (String) -> Unit,
  onAttachImage: () -> Unit,
  onStartScreenSelection: () -> Unit,
  onRemoveAttachment: (String) -> Unit,
  onOpenAttachmentPreview: (FloatingAttachmentItem?) -> Unit,
  onToggleVoice: () -> Unit,
  onTogglePause: () -> Unit,
  onEndLive: () -> Unit,
  onClearHistory: () -> Unit,
  onExpandApp: () -> Unit,
  onMinimizePanel: () -> Unit,
  modifier: Modifier = Modifier,
  isScreenShareActive: Boolean = false,
  onShareScreenWithLive: () -> Unit = onStartScreenSelection,
  onStartLiveVoice: () -> Unit = onToggleVoice,
) {
  val isBusy =
    responseState is AiResponseState.Loading ||
      responseState is AiResponseState.Thinking ||
      responseState is AiResponseState.Generating ||
      responseState is AiResponseState.Streaming
  val isMicActive =
    liveStatus == LiveVoiceSessionStatus.Active ||
      liveStatus == LiveVoiceSessionStatus.Connecting ||
      voiceState is VoiceState.Listening
  val hasContentToSend = promptText.isNotBlank() || pendingAttachments.isNotEmpty()
  val listState = rememberLazyListState()

  LaunchedEffect(turns.size, turns.lastOrNull()?.text?.length) {
    if (turns.isNotEmpty()) {
      runCatching { listState.animateScrollToItem(turns.lastIndex) }
    }
  }

  Column(
    modifier = modifier.fillMaxWidth().widthIn(max = 430.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Centered "Share screen with Live >" floating pill above composer (matches reference)
    Surface(
      onClick = onShareScreenWithLive,
      shape = RoundedCornerShape(24.dp),
      color = Color(0xFF070C1A).copy(alpha = 0.88f),
      border =
        BorderStroke(
          width = 1.15.dp,
          brush =
            Brush.horizontalGradient(
              if (isScreenShareActive) listOf(NeonCyan, ElectricBlue, NeonCyan)
              else listOf(Color.White.copy(alpha = 0.35f), NeonCyan.copy(alpha = 0.55f), NeonViolet.copy(alpha = 0.45f))
            ),
        ),
      shadowElevation = 14.dp,
      modifier = Modifier.testTag("share_screen_with_live_pill"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.CenterFocusWeak,
          contentDescription = null,
          tint = if (isScreenShareActive) NeonCyan else Color.White,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = if (isScreenShareActive) "Sharing screen with Live" else "Share screen with Live",
          color = Color.White,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
        )
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.85f),
          modifier = Modifier.size(18.dp),
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color(0xFF0A1020).copy(alpha = 0.92f),
      border =
        BorderStroke(
          width = 1.8.dp,
          brush =
            if (isMicActive || isBusy) Brush.horizontalGradient(MousomiDesignTokens.GoogleNeonSpectrum)
            else Brush.horizontalGradient(listOf(NeonCyan, ElectricBlue, NeonViolet, NeonPurple)),
        ),
      shadowElevation = 22.dp,
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(max = maxCardHeightDp.dp)
          .animateContentSize(
            animationSpec =
              spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
              )
          )
          .testTag("floating_chat_panel"),
    ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Top Panel Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          GeminiFourPointSparkle(size = 20.dp, animated = !isPaused)
          Column {
            Text(
              text = "Mousomi Assistant",
              color = Color.White,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            val statusSub =
              when {
                isPaused || liveStatus == LiveVoiceSessionStatus.Paused -> "Paused"
                liveStatus == LiveVoiceSessionStatus.Connecting -> "Connecting Gemini Live…"
                liveStatus == LiveVoiceSessionStatus.Active -> "Live Audio Active"
                liveStatus is LiveVoiceSessionStatus.Reconnecting -> "Reconnecting Live…"
                voiceState is VoiceState.Listening -> "Listening…"
                isBusy -> activeModelLabel.ifBlank { "Thinking…" }
                activeModelLabel.isNotBlank() -> activeModelLabel
                else -> "Ready over current app"
              }
            Text(
              text = statusSub,
              color = if (isPaused) WarmAmber else NeonCyan,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (turns.isNotEmpty()) {
            IconButton(
              onClick = onClearHistory,
              modifier = Modifier.size(40.dp).testTag("floating_panel_clear_chat"),
            ) {
              Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = "Clear conversation history",
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(18.dp),
              )
            }
          }
          IconButton(
            onClick = onStartScreenSelection,
            modifier = Modifier.size(40.dp).testTag("floating_panel_screen_select"),
          ) {
            Icon(
              imageVector = Icons.Outlined.CenterFocusWeak,
              contentDescription = "Select screen region",
              tint = NeonCyan,
              modifier = Modifier.size(19.dp),
            )
          }
          IconButton(
            onClick = onTogglePause,
            modifier = Modifier.size(40.dp).testTag("floating_panel_pause"),
          ) {
            Icon(
              imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
              contentDescription = if (isPaused) "Resume Assistant" else "Pause Assistant",
              tint = if (isPaused) WarmAmber else Color.White.copy(alpha = 0.86f),
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onExpandApp,
            modifier = Modifier.size(40.dp).testTag("floating_panel_expand_app"),
          ) {
            Icon(
              imageVector = Icons.Outlined.OpenInFull,
              contentDescription = "Open Full Mousomi App",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onMinimizePanel,
            modifier = Modifier.size(40.dp).testTag("floating_panel_minimize"),
          ) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowDown,
              contentDescription = "Minimize panel",
              tint = Color.White.copy(alpha = 0.88f),
              modifier = Modifier.size(21.dp),
            )
          }
        }
      }

      if (!statusBanner.isNullOrBlank()) {
        Text(
          text = statusBanner,
          color = WarmAmber,
          fontSize = 11.5.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (liveUserTranscript.isNotBlank() && isMicActive) {
        Text(
          text = "You: $liveUserTranscript",
          color = NeonCyan.copy(alpha = 0.92f),
          fontSize = 11.5.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      // Expanded Attachment Image Preview (Pre-send inspection)
      AnimatedVisibility(
        visible = previewingAttachment != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        val previewItem = previewingAttachment
        if (previewItem != null) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().testTag("floating_attachment_preview_card"),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Preview • ${previewItem.label}",
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  TextButton(
                    onClick = { onRemoveAttachment(previewItem.uriString) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                  ) {
                    Text("Remove", color = Color(0xFFFCA5A5), fontSize = 11.5.sp)
                  }
                  TextButton(
                    onClick = { onOpenAttachmentPreview(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                  ) {
                    Text("Done", color = NeonCyan, fontSize = 11.5.sp)
                  }
                }
              }
              AsyncImage(
                model = attachmentImageModel(previewItem),
                contentDescription = "Selected image preview",
                contentScale = ContentScale.Fit,
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 160.dp)
                    .clip(RoundedCornerShape(12.dp)),
              )
            }
          }
        }
      }

      // Scrollable Conversation History
      if (turns.isNotEmpty() && previewingAttachment == null) {
        LazyColumn(
          state = listState,
          modifier =
            Modifier
              .fillMaxWidth()
              .weight(1f, fill = false)
              .heightIn(max = (maxCardHeightDp - 190).coerceIn(88, 220).dp)
              .testTag("floating_conversation_history"),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(turns, key = { it.id }) { turn ->
            FloatingConversationBubble(turn = turn)
          }
        }
      }

      // Pending Attached Image Chips & Multimodal Quick Prompts (reference video strip)
      if (pendingAttachments.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          pendingAttachments.forEachIndexed { idx, att ->
            Box(modifier = Modifier.size(58.dp)) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                modifier =
                  Modifier
                    .size(54.dp)
                    .align(Alignment.BottomStart)
                    .clickable { onOpenAttachmentPreview(att) }
                    .testTag("floating_preview_attachment_$idx"),
              ) {
                AsyncImage(
                  model = attachmentImageModel(att),
                  contentDescription = "Attached ${att.label}, tap to preview",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
              Surface(
                onClick = { onRemoveAttachment(att.uriString) },
                shape = CircleShape,
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                modifier =
                  Modifier
                    .size(22.dp)
                    .align(Alignment.TopEnd)
                    .testTag("floating_remove_attachment_$idx"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove image",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                  )
                }
              }
            }
          }

          Surface(
            onClick = {
              if (!isBusy) onSendPrompt("Explain what is shown in this selected image.")
            },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
            modifier = Modifier.testTag("floating_chip_explain"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              GeminiFourPointSparkle(size = 14.dp, animated = false)
              Text("Explain selection", color = Color.White, fontSize = 12.sp)
            }
          }

          Surface(
            onClick = {
              if (!isBusy) onSendPrompt("Extract all readable text and details from this image.")
            },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.testTag("floating_chip_extract"),
          ) {
            Text(
              text = "Extract text",
              color = Color.White,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }

          if (pendingAttachments.any { it.isFromScreenSelection }) {
            Surface(
              onClick = onStartScreenSelection,
              shape = CircleShape,
              color = Color.White.copy(alpha = 0.08f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
              modifier = Modifier.testTag("floating_chip_retake"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Outlined.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Reselect", color = Color.White, fontSize = 12.sp)
              }
            }
          }
        }
      }

      if (isMicActive || liveStatus == LiveVoiceSessionStatus.Paused) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          Surface(
            onClick = onEndLive,
            shape = CircleShape,
            color = Color(0xFF991B1B).copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Color(0xFFEF4444)),
            modifier = Modifier.testTag("floating_end_live"),
          ) {
            Text(
              text = "End Live",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
          }
        }
      }

      // Composer Row: [+] [Ask Mousomi…] [Solid Electric Blue Mic] [Send or Three-Bar Live]
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          onClick = onAttachImage,
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.08f),
          border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.16f)),
          modifier = Modifier.size(44.dp).testTag("floating_attach_image_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Attach image",
              tint = Color.White,
              modifier = Modifier.size(22.dp),
            )
          }
        }

        OutlinedTextField(
          value = promptText,
          onValueChange = onPromptChanged,
          placeholder = {
            Text(
              text = if (pendingAttachments.isNotEmpty()) "Ask about selection…" else "Ask Mousomi…",
              color = Color.White.copy(alpha = 0.62f),
              fontSize = 14.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(22.dp),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions =
            KeyboardActions(
              onSend = {
                if (!isBusy && hasContentToSend) {
                  onSendPrompt(promptText)
                }
              }
            ),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              cursorColor = NeonCyan,
              focusedBorderColor = Color.Transparent,
              unfocusedBorderColor = Color.Transparent,
              focusedContainerColor = Color.Transparent,
              unfocusedContainerColor = Color.Transparent,
            ),
          modifier = Modifier.weight(1f).testTag("floating_prompt_input"),
        )

        Surface(
          onClick = onToggleVoice,
          shape = CircleShape,
          color = if (isMicActive) NeonCyan else ElectricBlue,
          modifier = Modifier.size(44.dp).testTag("floating_mic_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isMicActive) Icons.Outlined.MicOff else Icons.Outlined.Mic,
              contentDescription = if (isMicActive) "Pause Voice Input" else "Start Voice Input",
              tint = if (isMicActive) Color.Black else Color.White,
              modifier = Modifier.size(21.dp),
            )
          }
        }

        if (hasContentToSend || isBusy) {
          Surface(
            onClick = { if (!isBusy && hasContentToSend) onSendPrompt(promptText) },
            shape = CircleShape,
            color = ElectricBlue,
            modifier = Modifier.size(44.dp).testTag("floating_send_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              if (isBusy) {
                CircularProgressIndicator(
                  color = Color.White,
                  strokeWidth = 2.dp,
                  modifier = Modifier.size(18.dp),
                )
              } else {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.Send,
                  contentDescription = "Send prompt",
                  tint = Color.White,
                  modifier = Modifier.size(19.dp),
                )
              }
            }
          }
        } else {
          Surface(
            onClick = onStartLiveVoice,
            shape = CircleShape,
            color = if (liveStatus == LiveVoiceSessionStatus.Active) NeonCyan else Color.White.copy(alpha = 0.09f),
            border =
              BorderStroke(
                width = 0.9.dp,
                color = if (liveStatus == LiveVoiceSessionStatus.Active) NeonCyan else Color.White.copy(alpha = 0.16f),
              ),
            modifier = Modifier.size(44.dp).testTag("floating_live_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              GeminiLiveWaveformIcon(
                tint = if (liveStatus == LiveVoiceSessionStatus.Active) Color.Black else Color.White,
                size = 21.dp,
              )
            }
          }
        }
      }
    }
  }
  }
}

@Composable
private fun FloatingConversationBubble(turn: FloatingChatTurn) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (turn.isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color =
        if (turn.isUser) ElectricBlue.copy(alpha = 0.28f)
        else Color.White.copy(alpha = 0.08f),
      border =
        BorderStroke(
          width = 0.8.dp,
          color = if (turn.isUser) ElectricBlue.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f),
        ),
      modifier = Modifier.widthIn(max = 310.dp),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (!turn.isUser && turn.modelLabel.isNotBlank()) {
          Text(
            text = turn.modelLabel,
            color = NeonCyan.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
          )
        }
        if (turn.attachmentUris.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            turn.attachmentUris.take(3).forEach { uriStr ->
              AsyncImage(
                model = Uri.parse(uriStr),
                contentDescription = "Sent image attachment",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
              )
            }
          }
        }
        val body =
          if (!turn.isUser && turn.text.isBlank()) "Thinking…" else turn.text
        Text(
          text = body,
          color = Color.White.copy(alpha = 0.94f),
          style = MaterialTheme.typography.bodySmall,
          lineHeight = 17.sp,
        )
      }
    }
  }
}

private fun attachmentImageModel(item: FloatingAttachmentItem): Any {
  val file = item.filePath?.let(::File)?.takeIf { it.exists() }
  return file ?: Uri.parse(item.uriString)
}

/**
 * Compact, draggable liquid-glass Floating Live Popup shown above other apps during Secondary Live Mode.
 * Keeps the underlying screen completely visible and interactive while supporting multi-turn voice,
 * real Screen Share toggle + visible status, Draw / Select Area, and screen recording actions.
 */
@Composable
fun FloatingLivePopupCard(
  liveStatusLabel: String,
  isMicActive: Boolean,
  hasMicPermission: Boolean,
  isPaused: Boolean,
  isMuted: Boolean,
  isThinking: Boolean,
  isScreenShareActive: Boolean,
  voiceRmsNormalized: Float,
  audioMetrics: VoiceAudioMetrics = VoiceAudioMetrics.SILENT,
  elapsedSeconds: Long = 0L,
  latestUserTranscript: String = "",
  latestAssistantTranscript: String = "",
  statusBanner: String? = null,
  screenRecordingState: ScreenRecordingUiState = ScreenRecordingUiState(),
  onToggleMute: () -> Unit,
  onTogglePause: () -> Unit,
  onToggleScreenShare: () -> Unit,
  onDrawArea: () -> Unit,
  onSendFollowUp: (String) -> Unit,
  onStopLive: () -> Unit,
  onClosePopup: () -> Unit,
  onExpandToFullPanel: () -> Unit = {},
  onConfirmScreenRecording: () -> Unit = {},
  onStopScreenRecording: () -> Unit = {},
  onCancelScreenRecording: () -> Unit = {},
  onDismissScreenRecordingStatus: () -> Unit = {},
  onKeyboardInputFocusChanged: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var showQuickInput by remember { mutableStateOf(false) }
  var followUpDraft by remember { mutableStateOf("") }

  LaunchedEffect(showQuickInput) {
    onKeyboardInputFocusChanged(showQuickInput)
  }

  val neonBorderBrush =
    Brush.horizontalGradient(
      colors =
        listOf(
          NeonCyan,
          ElectricBlue,
          NeonViolet,
          Color(0xFFEC4899),
          Color(0xFFFBBF24),
        )
    )

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = Color(0xFF0A1020).copy(alpha = 0.88f),
    border = BorderStroke(1.35.dp, neonBorderBrush),
    shadowElevation = 20.dp,
    modifier =
      modifier
        .widthIn(min = 276.dp, max = 336.dp)
        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        .testTag("floating_live_popup_card"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Header Row: Live status dot + elapsed timer + Expand & Close
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          val indicatorColor =
            when {
              !hasMicPermission -> Color(0xFFEF4444)
              isPaused || isMuted -> WarmAmber
              isMicActive || isThinking -> NeonCyan
              else -> Color(0xFF94A3B8)
            }
          Box(
            modifier =
              Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(indicatorColor)
          )
          Text(
            text = if (!hasMicPermission) "Mic Permission Needed" else liveStatusLabel,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (elapsedSeconds > 0L) {
            Text(
              text = LiveVoiceSessionManager.formatElapsedMmSs(elapsedSeconds),
              color = NeonCyan.copy(alpha = 0.9f),
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          IconButton(
            onClick = onExpandToFullPanel,
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .size(34.dp)
                .testTag("floating_live_expand_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.OpenInFull,
              contentDescription = "Open full assistant panel",
              tint = Color.White.copy(alpha = 0.85f),
              modifier = Modifier.size(16.dp),
            )
          }
          Surface(
            onClick = onClosePopup,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f),
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .size(34.dp)
                .testTag("floating_live_close_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close Live popup",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }

      // Compact audio-reactive RMS bar + concise turn preview (no large Listening Card)
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CompactLiveAudioWaveform(
          amplitude = if (isPaused || isMuted || !hasMicPermission) 0.04f else voiceRmsNormalized,
          isActive = isMicActive && !isPaused && !isMuted && hasMicPermission,
          audioMetrics = audioMetrics,
          modifier =
            Modifier
              .width(58.dp)
              .height(20.dp)
              .testTag("floating_live_waveform"),
        )
        val previewLine =
          when {
            !hasMicPermission -> "Grant microphone permission to talk continuously."
            !statusBanner.isNullOrBlank() -> statusBanner
            latestUserTranscript.isNotBlank() -> "\"$latestUserTranscript\""
            latestAssistantTranscript.isNotBlank() -> latestAssistantTranscript
            isScreenShareActive -> "Sharing screen • Ask about what is visible"
            isPaused -> "Live voice paused • Tap Resume"
            isMuted -> "Microphone muted"
            else -> "Listening naturally…"
          }
        Text(
          text = previewLine,
          color = Color.White.copy(alpha = 0.88f),
          fontSize = 11.5.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 15.sp,
          modifier = Modifier.weight(1f),
        )
      }

      // Honest Screen Share toggle + visible active status + Stop Screen Sharing
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          onClick = onToggleScreenShare,
          shape = RoundedCornerShape(16.dp),
          color =
            if (isScreenShareActive) NeonCyan.copy(alpha = 0.16f)
            else Color.White.copy(alpha = 0.08f),
          border =
            BorderStroke(
              width = 1.dp,
              color =
                if (isScreenShareActive) NeonCyan.copy(alpha = 0.78f)
                else Color.White.copy(alpha = 0.18f),
            ),
          modifier =
            Modifier
              .weight(1f)
              .heightIn(min = 38.dp)
              .testTag("floating_live_screen_share_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector =
                if (isScreenShareActive) Icons.AutoMirrored.Outlined.StopScreenShare
                else Icons.AutoMirrored.Outlined.ScreenShare,
              contentDescription =
                if (isScreenShareActive) "Stop Screen Sharing" else "Share Screen with Mousomi",
              tint = if (isScreenShareActive) NeonCyan else Color.White.copy(alpha = 0.9f),
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = if (isScreenShareActive) "Screen Sharing Active" else "Share Screen",
              color = Color.White,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            if (isScreenShareActive) {
              Text(
                text = "Stop",
                color = Color(0xFFFCA5A5),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("floating_live_stop_share_button"),
              )
            }
          }
        }
      }

      // Screen Recording confirmation / active timer / final status bar inside Floating Live Popup
      if (screenRecordingState.phase != ScreenRecordingPhase.IDLE) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFF151C2E).copy(alpha = 0.95f),
          border =
            BorderStroke(
              1.dp,
              if (screenRecordingState.phase == ScreenRecordingPhase.RECORDING) Color(0xFFEF4444)
              else NeonCyan.copy(alpha = 0.45f),
            ),
          modifier = Modifier.fillMaxWidth().testTag("floating_live_recording_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Videocam,
              contentDescription = null,
              tint =
                if (screenRecordingState.phase == ScreenRecordingPhase.RECORDING) Color(0xFFEF4444)
                else NeonCyan,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text =
                when (screenRecordingState.phase) {
                  ScreenRecordingPhase.AWAITING_CONFIRMATION -> "Record screen (MP4)?"
                  ScreenRecordingPhase.AWAITING_CONSENT -> "Awaiting capture consent…"
                  ScreenRecordingPhase.RECORDING ->
                    "REC ${ScreenRecordingManager.formatElapsed(screenRecordingState.elapsedSeconds)}"
                  ScreenRecordingPhase.FINALIZING -> "Saving MP4…"
                  ScreenRecordingPhase.SAVED -> "Saved: ${screenRecordingState.displayPath}"
                  ScreenRecordingPhase.CANCELLED -> "Recording cancelled"
                  ScreenRecordingPhase.FAILED -> screenRecordingState.statusMessage ?: "Recording failed"
                  ScreenRecordingPhase.IDLE -> ""
                },
              color = Color.White,
              fontSize = 11.sp,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            when (screenRecordingState.phase) {
              ScreenRecordingPhase.AWAITING_CONFIRMATION -> {
                TextButton(
                  onClick = onConfirmScreenRecording,
                  modifier = Modifier.testTag("floating_rec_confirm"),
                ) { Text("Start", color = NeonCyan, fontSize = 11.sp) }
                TextButton(
                  onClick = onCancelScreenRecording,
                  modifier = Modifier.testTag("floating_rec_cancel"),
                ) { Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp) }
              }
              ScreenRecordingPhase.RECORDING -> {
                TextButton(
                  onClick = onStopScreenRecording,
                  modifier = Modifier.testTag("floating_rec_stop"),
                ) { Text("Stop & Save", color = Color(0xFFFCA5A5), fontSize = 11.sp) }
                TextButton(
                  onClick = onCancelScreenRecording,
                  modifier = Modifier.testTag("floating_rec_cancel"),
                ) { Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp) }
              }
              ScreenRecordingPhase.SAVED,
              ScreenRecordingPhase.CANCELLED,
              ScreenRecordingPhase.FAILED -> {
                TextButton(onClick = onDismissScreenRecordingStatus) {
                  Text("Dismiss", color = NeonCyan, fontSize = 11.sp)
                }
              }
              else -> Unit
            }
          }
        }
      }

      // Compact controls strip: Mic/Mute | Pause/Resume | Draw Area | Type Follow-up | Stop
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Surface(
          onClick = onToggleMute,
          shape = CircleShape,
          color =
            if (!isMuted && hasMicPermission && isMicActive) ElectricBlue
            else if (isMuted) WarmAmber.copy(alpha = 0.22f)
            else Color.White.copy(alpha = 0.1f),
          border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.18f)),
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .size(40.dp)
              .testTag("floating_live_mute_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
              contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone",
              tint = Color.White,
              modifier = Modifier.size(19.dp),
            )
          }
        }

        Surface(
          onClick = onTogglePause,
          shape = CircleShape,
          color = if (isPaused) NeonCyan else Color.White.copy(alpha = 0.1f),
          border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.18f)),
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .size(40.dp)
              .testTag("floating_live_pause_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
              contentDescription = if (isPaused) "Resume Live voice" else "Pause Live voice",
              tint = if (isPaused) Color.Black else Color.White,
              modifier = Modifier.size(19.dp),
            )
          }
        }

        Surface(
          onClick = onDrawArea,
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.1f),
          border = BorderStroke(0.9.dp, NeonCyan.copy(alpha = 0.55f)),
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .size(40.dp)
              .testTag("floating_live_draw_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.Draw,
              contentDescription = "Draw or select screen area",
              tint = NeonCyan,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Surface(
          onClick = { showQuickInput = !showQuickInput },
          shape = CircleShape,
          color = if (showQuickInput) NeonViolet.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f),
          border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.18f)),
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .size(40.dp)
              .testTag("floating_live_keyboard_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.Keyboard,
              contentDescription = "Type question for Live Mousomi",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Surface(
          onClick = onStopLive,
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFFDC2626),
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .height(38.dp)
              .testTag("floating_live_stop_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Stop,
              contentDescription = "Stop Live Mode",
              tint = Color.White,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = "Stop",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      AnimatedVisibility(
        visible = showQuickInput,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OutlinedTextField(
            value = followUpDraft,
            onValueChange = { followUpDraft = it },
            placeholder = {
              Text(
                text = "Ask about this screen…",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
              )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions =
              KeyboardActions(
                onSend = {
                  val clean = followUpDraft.trim()
                  if (clean.isNotEmpty()) {
                    followUpDraft = ""
                    showQuickInput = false
                    onSendFollowUp(clean)
                  }
                }
              ),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = NeonCyan,
                focusedBorderColor = NeonCyan.copy(alpha = 0.7f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
              ),
            modifier = Modifier.weight(1f).testTag("floating_live_followup_input"),
          )
          Surface(
            onClick = {
              val clean = followUpDraft.trim()
              if (clean.isNotEmpty()) {
                followUpDraft = ""
                showQuickInput = false
                onSendFollowUp(clean)
              }
            },
            shape = CircleShape,
            color = ElectricBlue,
            modifier = Modifier.size(40.dp).testTag("floating_live_followup_send"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Send Live question",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CompactLiveAudioWaveform(
  amplitude: Float,
  isActive: Boolean,
  audioMetrics: VoiceAudioMetrics,
  modifier: Modifier = Modifier,
) {
  val bars = 9
  Canvas(modifier = modifier) {
    val barWidth = (size.width / (bars * 1.75f)).coerceAtLeast(2.5f)
    val gap = (size.width - barWidth * bars) / (bars - 1).coerceAtLeast(1)
    val midY = size.height / 2f
    val clampedAmp = amplitude.coerceIn(0.04f, 1f)
    for (i in 0 until bars) {
      val bandFactor =
        when (i % 3) {
          0 -> audioMetrics.lowBandEnergy
          1 -> audioMetrics.midBandEnergy
          else -> audioMetrics.highBandEnergy
        }.coerceIn(0f, 1f)
      val weight =
        if (!isActive) 0.16f
        else (0.24f + clampedAmp * 0.58f + bandFactor * 0.35f) *
          (0.65f + 0.35f * sin(i * 0.9f + clampedAmp * 6.2f).coerceIn(0f, 1f))
      val barH = (size.height * weight).coerceIn(3f, size.height)
      val left = i * (barWidth + gap)
      val barColor =
        when {
          !isActive -> Color.White.copy(alpha = 0.32f)
          i < 3 -> NeonCyan
          i < 6 -> ElectricBlue
          else -> NeonViolet
        }
      drawRoundRect(
        color = barColor,
        topLeft = Offset(left, midY - barH / 2f),
        size = Size(barWidth, barH),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
      )
    }
  }
}

