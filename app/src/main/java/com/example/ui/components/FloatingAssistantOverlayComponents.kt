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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.core.voice.LiveVoiceSessionStatus
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassBorder
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.WarmAmber
import java.io.File

/**
 * Compact translucent Liquid Glass Floating Control Bar rendered above other apps.
 * Includes: Expand, Screen Selection, Assistant Profile, Pause/Resume, and Close controls.
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
) {
  val borderBrush =
    if (isBusyOrListening && !isPaused) {
      Brush.linearGradient(MousomiDesignTokens.GoogleNeonSpectrum)
    } else {
      Brush.linearGradient(
        listOf(
          Color.White.copy(alpha = 0.28f),
          NeonCyan.copy(alpha = 0.35f),
          NeonPurple.copy(alpha = 0.30f),
          Color.White.copy(alpha = 0.22f),
        )
      )
    }

  Surface(
    shape = RoundedCornerShape(28.dp),
    color = LiquidGlassDark.copy(alpha = 0.88f),
    border = BorderStroke(1.2.dp, borderBrush),
    shadowElevation = 18.dp,
    modifier = modifier.testTag("floating_control_bar"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      // Visual drag grip & status sparkle (dragging is handled smoothly at WindowManager layer)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, end = 2.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.DragIndicator,
          contentDescription = "Drag floating toolbar",
          tint = Color.White.copy(alpha = 0.48f),
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(2.dp))
        GeminiFourPointSparkle(size = 15.dp, animated = !isPaused)
      }

      // 1. Expand / Toggle Floating Chat Panel
      Surface(
        onClick = onExpandClick,
        shape = CircleShape,
        color = if (isPanelOpen) NeonCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
        modifier = Modifier.size(44.dp).testTag("floating_bar_expand"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.GridView,
            contentDescription = if (isPanelOpen) "Minimize or expand assistant" else "Expand assistant panel",
            tint = if (isPanelOpen) NeonCyan else Color.White,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      // 2. Screen Selection (pencil/region capture)
      Surface(
        onClick = onScreenSelectionClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.size(44.dp).testTag("floating_bar_screen_select"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Select screen region",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      // 3. Assistant Profile & Edge Light quick controls
      Surface(
        onClick = onProfileClick,
        shape = CircleShape,
        color = if (isProfileMenuOpen) NeonPurple.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
        modifier = Modifier.size(44.dp).testTag("floating_bar_profile"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.PersonOutline,
            contentDescription = "Assistant Profile and Edge Light settings",
            tint = if (isProfileMenuOpen) NeonCyan else Color.White,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      // 4. Pause / Resume
      Surface(
        onClick = onPauseToggleClick,
        shape = CircleShape,
        color = if (isPaused) WarmAmber.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
        modifier = Modifier.size(44.dp).testTag("floating_bar_pause"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
            contentDescription = if (isPaused) "Resume Assistant" else "Pause Assistant",
            tint = if (isPaused) WarmAmber else Color.White,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      // 5. Close Floating Assistant completely
      Surface(
        onClick = onCloseClick,
        shape = CircleShape,
        color = Color(0xFFEF4444).copy(alpha = 0.18f),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
        modifier = Modifier.size(44.dp).testTag("floating_bar_close"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Stop,
            contentDescription = "Close Floating Assistant",
            tint = Color(0xFFFCA5A5),
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
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

  Surface(
    shape = RoundedCornerShape(28.dp),
    color = LiquidGlassDark.copy(alpha = 0.94f),
    border =
      BorderStroke(
        width = 1.8.dp,
        brush =
          if (isMicActive || isBusy) Brush.linearGradient(MousomiDesignTokens.GoogleNeonSpectrum)
          else Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.52f), NeonPurple.copy(alpha = 0.45f))),
      ),
    shadowElevation = 22.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .widthIn(max = 430.dp)
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

      // Composer Row: [+] [Ask Mousomi…] [Mic] [Send]
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          onClick = onAttachImage,
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.12f),
          modifier = Modifier.size(46.dp).testTag("floating_attach_image_button"),
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
              color = Color.White.copy(alpha = 0.56f),
              fontSize = 13.5.sp,
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
              focusedBorderColor = NeonCyan.copy(alpha = 0.65f),
              unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
              focusedContainerColor = Color.White.copy(alpha = 0.07f),
              unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            ),
          modifier = Modifier.weight(1f).testTag("floating_prompt_input"),
        )

        Surface(
          onClick = onToggleVoice,
          shape = CircleShape,
          color = if (isMicActive) NeonCyan else Color.White.copy(alpha = 0.13f),
          modifier = Modifier.size(46.dp).testTag("floating_mic_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isMicActive) Icons.Outlined.MicOff else Icons.Outlined.Mic,
              contentDescription = if (isMicActive) "Pause Voice Input" else "Start Voice Input",
              tint = if (isMicActive) Color.Black else Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        Surface(
          onClick = { if (!isBusy && hasContentToSend) onSendPrompt(promptText) },
          shape = CircleShape,
          color = if (hasContentToSend && !isBusy) ElectricBlue else Color.White.copy(alpha = 0.12f),
          modifier = Modifier.size(46.dp).testTag("floating_send_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (isBusy) {
              CircularProgressIndicator(
                color = NeonCyan,
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
