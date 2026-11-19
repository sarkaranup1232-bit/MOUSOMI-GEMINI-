package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.voice.VoiceState
import com.example.domain.model.DomainConversation
import com.example.domain.model.VoiceAudioMetrics
import com.example.domain.model.VoiceGender
import com.example.ui.components.AnimatedAIOrb
import com.example.ui.components.GeminiFourPointSparkle
import com.example.ui.components.VoiceWaveform
import com.example.ui.model.AiModelOption
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan

@Composable
fun MousomiDrawerContent(
  conversations: List<DomainConversation>,
  activeConversationId: String,
  onSelectConversation: (String) -> Unit,
  onDeleteConversation: (String) -> Unit,
  onNewChat: () -> Unit,
  onPreviewChatBubbles: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenControlCenter: () -> Unit = {},
  onOpenPermissions: () -> Unit = {},
  isEmergencyActive: Boolean = false,
  onTriggerEmergencyStop: () -> Unit = {},
  onOpenAssistantHub: () -> Unit = {},
) {
  val colors = LocalMousomiColors.current
  val onSurface = MaterialTheme.colorScheme.onSurface

  ModalDrawerSheet(
    drawerContainerColor = MaterialTheme.colorScheme.surface,
    drawerContentColor = onSurface,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
      ) {
        GeminiFourPointSparkle(size = 28.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "Mousomi AI",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = onSurface,
          )
          Text(
            text = "Multi-Model Multimodal Platform",
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      NavigationDrawerItem(
        label = { Text("New chat") },
        selected = false,
        onClick = onNewChat,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = colors.activeChipContainer),
        modifier = Modifier.testTag("drawer_new_chat"),
      )

      Spacer(modifier = Modifier.height(6.dp))

      NavigationDrawerItem(
        label = { Text("Preview chat & image bubble") },
        selected = false,
        onClick = onPreviewChatBubbles,
        icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
        modifier = Modifier.testTag("drawer_preview_chat"),
      )

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "RECENT CHATS",
        style = MaterialTheme.typography.labelSmall,
        color = colors.mutedText,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      )

      if (conversations.isEmpty()) {
        Text(
          text = "Your conversation history appears here.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.mutedText,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(conversations, key = { it.id }) { conv ->
            val selected = conv.id == activeConversationId
            NavigationDrawerItem(
              label = {
                Text(
                  text = conv.title,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              selected = selected,
              onClick = { onSelectConversation(conv.id) },
              badge = {
                IconButton(
                  onClick = { onDeleteConversation(conv.id) },
                  modifier = Modifier.size(30.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete conversation",
                    tint = colors.mutedText,
                    modifier = Modifier.size(16.dp),
                  )
                }
              },
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
      Spacer(modifier = Modifier.height(8.dp))

      if (isEmergencyActive) {
        NavigationDrawerItem(
          label = { Text("Emergency Stop Active Sessions", color = Color(0xFFFCA5A5)) },
          selected = false,
          onClick = onTriggerEmergencyStop,
          icon = { Icon(Icons.Rounded.Stop, contentDescription = null, tint = Color(0xFFEF4444)) },
          modifier = Modifier.testTag("drawer_emergency_stop"),
        )
      }

      NavigationDrawerItem(
        label = { Text("Device, Tasks & Alarms") },
        selected = false,
        onClick = onOpenAssistantHub,
        icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
        modifier = Modifier.testTag("drawer_assistant_hub"),
      )

      NavigationDrawerItem(
        label = { Text("Permissions & Privacy") },
        selected = false,
        onClick = onOpenPermissions,
        icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
        modifier = Modifier.testTag("drawer_permissions"),
      )

      NavigationDrawerItem(
        label = { Text("Control Center & Edge Light") },
        selected = false,
        onClick = onOpenControlCenter,
        icon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
        modifier = Modifier.testTag("drawer_control_center"),
      )

      NavigationDrawerItem(
        label = { Text("API Keys, Proxy & Voice") },
        selected = false,
        onClick = onOpenSettings,
        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
        modifier = Modifier.testTag("drawer_settings"),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionBottomSheet(
  selectedModel: AiModelOption,
  onSelectModel: (AiModelOption) -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Choose model",
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
      )
      Text(
        text = "Select one of the four supported Mousomi AI models.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.mutedText,
      )
      Spacer(modifier = Modifier.height(4.dp))

      AiModelOption.entries.forEach { option ->
        val isSelected = option == selectedModel
        Surface(
          onClick = { onSelectModel(option) },
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) colors.activeChipContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          border =
            if (isSelected) BorderStroke(1.5.dp, colors.liveButtonBlue)
            else null,
          modifier = Modifier.fillMaxWidth().testTag("model_option_${option.name}"),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = option.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText,
              )
            }
            if (isSelected) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = colors.liveButtonBlue,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
  onPickFromGallery: () -> Unit,
  onPickMultipleFromGallery: () -> Unit,
  onPickVideo: () -> Unit = {},
  onPickAudio: () -> Unit = {},
  onPickDocuments: () -> Unit = {},
  onTakeCameraPhoto: () -> Unit = {},
  onAttachSamplePreview: () -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Add to prompt",
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AttachmentTile(
          icon = Icons.Outlined.CameraAlt,
          label = "Camera",
          onClick = onTakeCameraPhoto,
          modifier = Modifier.weight(1f).testTag("attach_camera_button"),
        )
        AttachmentTile(
          icon = Icons.Outlined.PhotoLibrary,
          label = "Photo",
          onClick = onPickFromGallery,
          modifier = Modifier.weight(1f).testTag("attach_gallery_button"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Folder,
          label = "Multi (4)",
          onClick = onPickMultipleFromGallery,
          modifier = Modifier.weight(1f).testTag("attach_multi_button"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AttachmentTile(
          icon = Icons.Outlined.VideoLibrary,
          label = "Video",
          onClick = onPickVideo,
          modifier = Modifier.weight(1f).testTag("attach_video_button"),
        )
        AttachmentTile(
          icon = Icons.Outlined.AudioFile,
          label = "Audio",
          onClick = onPickAudio,
          modifier = Modifier.weight(1f).testTag("attach_audio_button"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Description,
          label = "Document",
          onClick = onPickDocuments,
          modifier = Modifier.weight(1f).testTag("attach_document_button"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AttachmentTile(
          icon = Icons.Outlined.ImageSearch,
          label = "Analyze Image",
          onClick = onPickFromGallery,
          modifier = Modifier.weight(1f).testTag("attach_analyze_image_button"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Analytics,
          label = "Analyze Doc",
          onClick = onPickDocuments,
          modifier = Modifier.weight(1f).testTag("attach_analyze_doc_button"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Image,
          label = "Sample Art",
          onClick = onAttachSamplePreview,
          modifier = Modifier.weight(1f).testTag("attach_sample_button"),
        )
      }

      Text(
        text = "Privacy: Uses Android's zero-permission Photo Picker, SAF, and on-demand Camera capture. Temporary copies are automatically cleaned.",
        style = MaterialTheme.typography.labelSmall,
        color = colors.mutedText,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToolsBottomSheet(
  onSelectImageAction: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp)
          .testTag("image_tools_bottom_sheet"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Image Tools",
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AttachmentTile(
          icon = Icons.Outlined.AutoAwesome,
          label = "Generate",
          onClick = { onSelectImageAction("generate_image") },
          modifier = Modifier.weight(1f).testTag("img_tool_generate"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Edit,
          label = "Edit Image",
          onClick = { onSelectImageAction("edit_image") },
          modifier = Modifier.weight(1f).testTag("img_tool_edit"),
        )
        AttachmentTile(
          icon = Icons.Outlined.AutoFixHigh,
          label = "Enhance",
          onClick = { onSelectImageAction("enhance") },
          modifier = Modifier.weight(1f).testTag("img_tool_enhance"),
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AttachmentTile(
          icon = Icons.Outlined.CleaningServices,
          label = "Remove BG",
          onClick = { onSelectImageAction("remove_bg") },
          modifier = Modifier.weight(1f).testTag("img_tool_remove_bg"),
        )
        AttachmentTile(
          icon = Icons.Outlined.TextFields,
          label = "Add Text",
          onClick = { onSelectImageAction("add_text") },
          modifier = Modifier.weight(1f).testTag("img_tool_add_text"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Layers,
          label = "Variations",
          onClick = { onSelectImageAction("create_variations") },
          modifier = Modifier.weight(1f).testTag("img_tool_variations"),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreToolsBottomSheet(
  onSelectToolAction: (String) -> Unit,
  onOpenScreenAssistant: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp)
          .testTag("more_tools_bottom_sheet"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "AI Tools & Capabilities",
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AttachmentTile(
          icon = Icons.Outlined.Public,
          label = "Web Search",
          onClick = { onSelectToolAction("web_search") },
          modifier = Modifier.weight(1f).testTag("more_tool_web_search"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Science,
          label = "Research",
          onClick = { onSelectToolAction("deep_research") },
          modifier = Modifier.weight(1f).testTag("more_tool_research"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Analytics,
          label = "Data Analysis",
          onClick = { onSelectToolAction("data_analysis") },
          modifier = Modifier.weight(1f).testTag("more_tool_data"),
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AttachmentTile(
          icon = Icons.Outlined.Code,
          label = "Coding",
          onClick = { onSelectToolAction("coding_assistant") },
          modifier = Modifier.weight(1f).testTag("more_tool_coding"),
        )
        AttachmentTile(
          icon = Icons.AutoMirrored.Outlined.ScreenShare,
          label = "Screen Assist",
          onClick = onOpenScreenAssistant,
          modifier = Modifier.weight(1f).testTag("more_tool_screen"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Brush,
          label = "Drawing Tools",
          onClick = { onSelectToolAction("draw_region") },
          modifier = Modifier.weight(1f).testTag("more_tool_drawing"),
        )
      }
    }
  }
}

@Composable
private fun AttachmentTile(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = colors.activeChipContainer,
    modifier = modifier.height(86.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontSize = 12.5.sp,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun ProfileAccountDialog(
  onOpenSettings: () -> Unit,
  onOpenPermissions: () -> Unit = {},
  onDismiss: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.avatarOrange),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "C",
            color = Color.White,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "HACKER",
          fontFamily = DisplayFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 18.sp,
        )
        Text(
          text = "Mousomi AI • Multi-Model Engine",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.mutedText,
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
          onClick = {
            onDismiss()
            onOpenSettings()
          },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = colors.liveButtonBlue),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_settings_from_profile"),
        ) {
          Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color.White)
          Spacer(modifier = Modifier.width(8.dp))
          Text("API Keys, Proxy & Settings", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
          onClick = {
            onDismiss()
            onOpenPermissions()
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
          Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Permissions & Privacy")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
          Text("Close")
        }
      }
    }
  }
}

@Composable
fun VoiceInputDialog(
  hasAudioPermission: Boolean,
  voiceState: VoiceState,
  onStartListening: () -> Unit,
  onStopListening: () -> Unit,
  onUseTranscript: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.liveButtonBlue),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
          )
        }
        Spacer(modifier = Modifier.height(14.dp))

        val headline =
          when {
            !hasAudioPermission -> "Microphone Permission Needed"
            voiceState is VoiceState.Listening -> "Listening…"
            voiceState is VoiceState.Error -> "Voice Input Issue"
            else -> "Ready for Voice Input"
          }

        Text(
          text = headline,
          fontFamily = DisplayFontFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 18.sp,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))

        val bodyText =
          when {
            !hasAudioPermission ->
              "Grant microphone permission to dictate prompts in English, Bengali, or Hindi."
            voiceState is VoiceState.Listening && voiceState.partialTranscript.isNotBlank() ->
              "\"${voiceState.partialTranscript}\""
            voiceState is VoiceState.Listening -> "Speak clearly now…"
            voiceState is VoiceState.Error -> voiceState.message
            else -> "Tap Start Listening to dictate with Android SpeechRecognizer."
          }

        Text(
          text = bodyText,
          style = MaterialTheme.typography.bodyMedium,
          color = colors.mutedText,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (hasAudioPermission) {
          Button(
            onClick = {
              if (voiceState is VoiceState.Listening) onStopListening() else onStartListening()
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp),
          ) {
            Text(if (voiceState is VoiceState.Listening) "Stop Listening" else "Start Listening")
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
          onClick = {
            onUseTranscript("Make a cutout phone wallpaper")
            onDismiss()
          },
          shape = CircleShape,
          modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
          Text("Insert Sample Prompt")
        }
        TextButton(onClick = onDismiss) {
          Text("Close")
        }
      }
    }
  }
}

@Composable
fun GeminiLiveOverlay(
  voiceState: VoiceState,
  voiceRmsNormalized: Float = 0f,
  isThinking: Boolean = false,
  voiceGender: VoiceGender = VoiceGender.FEMALE,
  onSelectVoiceGender: (VoiceGender) -> Unit = {},
  onStartListening: () -> Unit,
  onStopListening: () -> Unit,
  onEmergencyStop: () -> Unit = {},
  onClose: () -> Unit,
  audioMetrics: VoiceAudioMetrics = VoiceAudioMetrics.SILENT,
) {
  BackHandler(onBack = onClose)

  var isMuted by remember { mutableStateOf(false) }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(Color(0xFF000000), Color(0xFF04143D), Color(0xFF0B2B7E))
          )
        )
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(24.dp)
        .testTag("mousomi_live_overlay")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        GeminiFourPointSparkle(size = 26.dp, animated = true)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Mousomi Live",
          color = Color.White,
          fontFamily = DisplayFontFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 20.sp,
        )
      }
      Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
      ) {
        Text(
          text =
            when {
              isThinking -> "Thinking"
              voiceState is VoiceState.Listening -> "Listening"
              voiceState is VoiceState.Speaking -> "Speaking"
              else -> "Voice Ready"
            },
          color = NeonCyan,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }

    Column(
      modifier = Modifier.align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AnimatedAIOrb(
        voiceState = voiceState,
        voiceRmsNormalized = voiceRmsNormalized,
        isThinking = isThinking,
        isMuted = isMuted,
        orbSize = 196.dp,
        audioMetrics = audioMetrics,
      )

      Spacer(modifier = Modifier.height(14.dp))

      VoiceWaveform(
        isActive = !isMuted && (voiceState is VoiceState.Listening || voiceState is VoiceState.Speaking),
        rmsNormalized = voiceRmsNormalized,
      )

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text =
          when {
            isMuted -> "Microphone muted"
            isThinking -> "Processing your request…"
            voiceState is VoiceState.Listening && voiceState.partialTranscript.isNotBlank() ->
              voiceState.partialTranscript
            voiceState is VoiceState.Listening -> "Listening to HACKER…"
            voiceState is VoiceState.Speaking -> "Speaking response…"
            else -> "Tap mic to speak with Mousomi AI"
          },
        color = Color.White,
        fontFamily = DisplayFontFamily,
        fontSize = 22.sp,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Voice: ${voiceGender.label} • Interrupt anytime to ask follow-up questions",
        color = Color.White.copy(alpha = 0.72f),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        VoiceGender.entries.forEach { gender ->
          FilterChip(
            selected = voiceGender == gender,
            onClick = { onSelectVoiceGender(gender) },
            label = { Text(gender.label) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                selectedLabelColor = Color.White,
                labelColor = Color.White.copy(alpha = 0.75f),
              ),
            modifier = Modifier.testTag("live_voice_gender_${gender.name}"),
          )
        }
      }
    }

    Row(
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        onClick = {
          isMuted = !isMuted
          if (isMuted) onStopListening() else onStartListening()
        },
        shape = CircleShape,
        color = if (isMuted) Color.White else Color.White.copy(alpha = 0.16f),
        modifier = Modifier.size(62.dp).testTag("live_mute_toggle_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            tint = if (isMuted) Color.Black else Color.White,
            modifier = Modifier.size(26.dp),
          )
        }
      }

      Surface(
        onClick = {
          onEmergencyStop()
          onClose()
        },
        shape = CircleShape,
        color = Color(0xFF991B1B),
        border = BorderStroke(1.dp, Color(0xFFEF4444)),
        modifier = Modifier.height(50.dp).testTag("live_emergency_stop_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Rounded.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
          Text("Emergency Stop", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
      }

      Surface(
        onClick = onClose,
        shape = CircleShape,
        color = Color(0xFFDC2626),
        modifier = Modifier.size(62.dp).testTag("close_gemini_live_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "End Live Voice",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
          )
        }
      }
    }
  }
}

@Composable
fun QuickNoteCaptureDialog(
  onSaveNote: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var noteText by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Quick Note to Memory") },
    text = {
      OutlinedTextField(
        value = noteText,
        onValueChange = { noteText = it },
        label = { Text("Write a quick note…") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().testTag("quick_note_dialog_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = {
          if (noteText.isNotBlank()) {
            onSaveNote(noteText.trim())
            onDismiss()
          }
        },
        modifier = Modifier.testTag("quick_note_save_button"),
      ) { Text("Save") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
