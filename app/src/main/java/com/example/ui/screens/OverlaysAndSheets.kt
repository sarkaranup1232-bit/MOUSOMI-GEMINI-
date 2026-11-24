package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloseFullscreen
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
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.core.edge.EdgeLightRenderParams
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.voice.LiveConnectionState
import com.example.core.voice.LiveSessionPhase
import com.example.core.voice.LiveVoiceSessionManager
import com.example.core.voice.OrbReactiveFrame
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.DomainConversation
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightIntensity
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.VoiceAudioMetrics
import com.example.domain.model.VoiceGender
import com.example.ui.components.AnimatedAIOrb
import com.example.ui.components.DraggableLiveOrbStage
import com.example.ui.components.EdgeLightPerimeterOverlay
import com.example.ui.components.GeminiFourPointSparkle
import com.example.ui.components.RainbowLiveWaveformDiscButton
import com.example.ui.components.VoiceWaveform
import com.example.ui.model.AiModelOption
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.WarmAmber
import java.util.Calendar

@Composable
fun MousomiDrawerContent(
  conversations: List<DomainConversation>,
  activeConversationId: String,
  onSelectConversation: (String) -> Unit,
  onDeleteConversation: (String) -> Unit,
  onRenameConversation: (String, String) -> Unit = { _, _ -> },
  onNewChat: () -> Unit,
  onPreviewChatBubbles: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenControlCenter: () -> Unit = {},
  onOpenPermissions: () -> Unit = {},
  isEmergencyActive: Boolean = false,
  onTriggerEmergencyStop: () -> Unit = {},
  onOpenAssistantHub: () -> Unit = {},
  onOpenLens: () -> Unit = {},
) {
  val colors = LocalMousomiColors.current
  val onSurface = MaterialTheme.colorScheme.onSurface
  var searchQuery by remember { mutableStateOf("") }
  var renamingConversation by remember { mutableStateOf<DomainConversation?>(null) }
  var renameTitleDraft by remember { mutableStateOf("") }

  val filteredConversations =
    remember(conversations, searchQuery) {
      val q = searchQuery.trim()
      if (q.isEmpty()) conversations
      else conversations.filter { it.title.contains(q, ignoreCase = true) }
    }

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
            text = "Two-API Gemini + Groq Platform",
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

      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search conversations…", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon =
          if (searchQuery.isNotBlank()) {
            {
              IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
              }
            }
          } else null,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("drawer_conversation_search"),
      )

      if (filteredConversations.isEmpty()) {
        Text(
          text =
            if (conversations.isEmpty()) "Your conversation history appears here."
            else "No matching conversations.",
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
          items(filteredConversations, key = { it.id }) { conv ->
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
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  IconButton(
                    onClick = {
                      renamingConversation = conv
                      renameTitleDraft = conv.title
                    },
                    modifier = Modifier.size(28.dp).testTag("rename_conversation_${conv.id}"),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Edit,
                      contentDescription = "Rename conversation",
                      tint = colors.mutedText,
                      modifier = Modifier.size(15.dp),
                    )
                  }
                  IconButton(
                    onClick = { onDeleteConversation(conv.id) },
                    modifier = Modifier.size(28.dp).testTag("delete_conversation_${conv.id}"),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.DeleteOutline,
                      contentDescription = "Delete conversation",
                      tint = colors.mutedText,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              },
            )
          }
        }
      }

      renamingConversation?.let { conv ->
        AlertDialog(
          onDismissRequest = { renamingConversation = null },
          title = { Text("Rename Conversation") },
          text = {
            OutlinedTextField(
              value = renameTitleDraft,
              onValueChange = { renameTitleDraft = it },
              label = { Text("Title") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("rename_conversation_input"),
            )
          },
          confirmButton = {
            Button(
              onClick = {
                val trimmed = renameTitleDraft.trim()
                if (trimmed.isNotBlank()) {
                  onRenameConversation(conv.id, trimmed)
                }
                renamingConversation = null
              },
              modifier = Modifier.testTag("rename_conversation_confirm"),
            ) {
              Text("Save")
            }
          },
          dismissButton = {
            TextButton(onClick = { renamingConversation = null }) {
              Text("Cancel")
            }
          },
        )
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
        label = { Text("Mousomi Lens Visual AI") },
        selected = false,
        onClick = onOpenLens,
        icon = { Icon(Icons.Outlined.CenterFocusWeak, contentDescription = null, tint = NeonCyan) },
        modifier = Modifier.testTag("drawer_mousomi_lens"),
      )

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
        text = "Select one of the seven supported Mousomi AI models.",
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
  onOpenLens: () -> Unit = {},
  onStartScreenSelection: () -> Unit = {},
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
          icon = Icons.Outlined.CenterFocusWeak,
          label = "Live Lens",
          onClick = onOpenLens,
          modifier = Modifier.weight(1f).testTag("attach_lens_button"),
        )
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
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AttachmentTile(
          icon = Icons.Outlined.CenterFocusWeak,
          label = "Select Screen",
          onClick = onStartScreenSelection,
          modifier = Modifier.weight(1f).testTag("attach_screen_select_button"),
        )
        AttachmentTile(
          icon = Icons.Outlined.Image,
          label = "Sample Art",
          onClick = onAttachSamplePreview,
          modifier = Modifier.weight(1f).testTag("attach_sample_button"),
        )
        Spacer(modifier = Modifier.weight(1f))
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
  onOpenLens: () -> Unit = {},
  onStartScreenSelection: () -> Unit = {},
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
          icon = Icons.Outlined.CenterFocusWeak,
          label = "Mousomi Lens",
          onClick = onOpenLens,
          modifier = Modifier.weight(1f).testTag("more_tool_lens"),
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
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AttachmentTile(
          icon = Icons.Outlined.Analytics,
          label = "Data Analysis",
          onClick = { onSelectToolAction("data_analysis") },
          modifier = Modifier.weight(1f).testTag("more_tool_data"),
        )
        AttachmentTile(
          icon = Icons.Outlined.CenterFocusWeak,
          label = "Select Screen",
          onClick = onStartScreenSelection,
          modifier = Modifier.weight(1f).testTag("more_tool_screen_selection"),
        )
        Spacer(modifier = Modifier.weight(1f))
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
  authSession: com.example.domain.auth.AuthUserSession = com.example.domain.auth.AuthUserSession(),
  onOpenSettings: () -> Unit,
  onOpenPermissions: () -> Unit = {},
  onSignOut: (() -> Unit)? = null,
  onDismiss: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val displayTitle =
    when {
      authSession.isSignedIn && authSession.displayName.isNotBlank() -> authSession.displayName
      authSession.isGuest -> "Guest User"
      else -> "Mousomi User"
    }
  val avatarInitial = displayTitle.firstOrNull()?.uppercaseChar()?.toString() ?: "M"
  val subtitleText =
    when {
      authSession.isSignedIn && !authSession.email.isNullOrBlank() -> authSession.email
      authSession.isGuest -> "Local Guest Mode • On-device storage"
      else -> "Mousomi AI • ${authSession.entitlementTier.label}"
    }

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
            text = avatarInitial,
            color = Color.White,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = displayTitle,
          fontFamily = DisplayFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 18.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitleText,
          style = MaterialTheme.typography.bodyMedium,
          color = colors.mutedText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
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
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Permissions & Privacy")
        }

        if ((authSession.isSignedIn || authSession.isGuest) && onSignOut != null) {
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = {
              onDismiss()
              onSignOut()
            },
            shape = RoundedCornerShape(14.dp),
            border =
              BorderStroke(
                1.dp,
                if (authSession.isGuest) colors.liveButtonBlue.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
              ),
            modifier =
              Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag(if (authSession.isGuest) "profile_sign_in_from_guest_button" else "profile_sign_out_button"),
          ) {
            Text(
              text = if (authSession.isGuest) "Sign in with Google" else "Sign Out",
              color = if (authSession.isGuest) colors.liveButtonBlue else MaterialTheme.colorScheme.error,
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp),
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
  isPaused: Boolean = false,
  isMuted: Boolean = false,
  selectedModelLabel: String = "Mousomi Auto",
  voiceGender: VoiceGender = VoiceGender.FEMALE,
  latestAssistantText: String = "",
  onSelectVoiceGender: (VoiceGender) -> Unit = {},
  onStartListening: () -> Unit,
  onStopListening: () -> Unit,
  onTogglePause: () -> Unit = {},
  onToggleMute: () -> Unit = {},
  onSendFollowUp: (String) -> Unit = {},
  onOpenCamera: () -> Unit = {},
  onRunDeepSearch: () -> Unit = {},
  onMinimize: () -> Unit = {},
  onEmergencyStop: () -> Unit = {},
  onClose: () -> Unit,
  audioMetrics: VoiceAudioMetrics = VoiceAudioMetrics.SILENT,
  reactiveFrame: OrbReactiveFrame? = null,
  liveConnectionState: LiveConnectionState = LiveConnectionState.Disconnected,
  liveElapsedSeconds: Long = 0L,
  isScreenShareActive: Boolean = false,
  onShareScreenWithLive: () -> Unit = {},
  onRequestOverlayPermission: (() -> Unit)? = null,
) {
  BackHandler(onBack = onClose)

  val context = LocalContext.current
  val canDrawOverlays = remember(context) { runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false) }
  var followUpText by remember { mutableStateOf("") }
  var showControlsDrawer by remember { mutableStateOf(false) }

  val liveAmplitude =
    if (isPaused || isMuted) 0f
    else (reactiveFrame?.reactiveAmplitude ?: voiceRmsNormalized)

  val effectiveFrame =
    reactiveFrame
      ?: OrbReactiveFrame(
        reactiveAmplitude = liveAmplitude,
        lowBandEnergy = audioMetrics.lowBandEnergy,
        midBandEnergy = audioMetrics.midBandEnergy,
        highBandEnergy = audioMetrics.highBandEnergy,
        sessionPhase =
          when {
            isPaused -> LiveSessionPhase.PAUSED
            isThinking -> LiveSessionPhase.AI_PROCESSING
            voiceState is VoiceState.Speaking -> LiveSessionPhase.AI_SPEAKING
            voiceState is VoiceState.Listening -> LiveSessionPhase.LISTENING
            else -> LiveSessionPhase.IDLE
          },
      )

  val authenticStatusLabel =
    when {
      isPaused -> "Paused"
      isMuted -> "Muted"
      liveConnectionState is LiveConnectionState.Connecting ||
        effectiveFrame.sessionPhase == LiveSessionPhase.CONNECTING -> "Connecting…"
      liveConnectionState is LiveConnectionState.Reconnecting ||
        effectiveFrame.sessionPhase == LiveSessionPhase.RECONNECTING -> "Reconnecting…"
      liveConnectionState is LiveConnectionState.Failed -> "Connection Issue"
      voiceState is VoiceState.Speaking ||
        effectiveFrame.sessionPhase == LiveSessionPhase.AI_SPEAKING -> "AI Speaking"
      effectiveFrame.sessionPhase == LiveSessionPhase.USER_SPEAKING -> "Speech Active"
      isThinking || effectiveFrame.sessionPhase == LiveSessionPhase.AI_PROCESSING -> "Thinking"
      voiceState is VoiceState.Listening ||
        effectiveFrame.sessionPhase == LiveSessionPhase.LISTENING -> "Listening"
      liveConnectionState is LiveConnectionState.Connected -> "Connected"
      liveConnectionState is LiveConnectionState.FallbackContinuous -> "Voice Active"
      voiceState is VoiceState.Error && voiceState.isRecoverableSilence -> "Reconnecting…"
      voiceState is VoiceState.Error -> "Mic Issue"
      else -> "Live Ready"
    }

  val edgeParams: EdgeLightRenderParams =
    EdgeLightStateMapper.resolve(
      config =
        EdgeLightConfig(
          enabled = true,
          assistantEdgeGlowEnabled = true,
          preset = EdgeLightPreset.GEMINI_FLOW,
          intensity = EdgeLightIntensity.BRIGHT,
          brightness = 0.96f,
          borderThicknessDp = 5.5f,
          cornerRadiusDp = 34f,
        ),
      responseState = if (isThinking) AiResponseState.Thinking() else AiResponseState.Idle,
      voiceState = voiceState,
      voiceRmsNormalized = liveAmplitude,
      screenShareStatus = if (isScreenShareActive) ScreenShareStatus.ACTIVE else ScreenShareStatus.STOPPED,
      notificationAlertActive = false,
      isSystemPowerSave = false,
      currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
      forcePreview = true,
    )

  val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Color.Transparent)
        .testTag("mousomi_live_overlay")
  ) {
    // Full-screen rounded neon Border Light without blocking underlying content with black
    EdgeLightPerimeterOverlay(params = edgeParams)

    // Top minimal bar (status pill, timer, minimize, pause/stop controls)
    Column(
      modifier =
        Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            onClick = { showControlsDrawer = !showControlsDrawer },
            shape = CircleShape,
            color = Color(0xFF101524).copy(alpha = 0.85f),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              GeminiFourPointSparkle(size = 16.dp, animated = !isPaused)
              Text(
                text = authenticStatusLabel,
                color = if (isPaused || liveConnectionState is LiveConnectionState.Failed) WarmAmber else NeonCyan,
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }

          if (liveElapsedSeconds > 0L) {
            Surface(
              shape = CircleShape,
              color = Color(0xFF080D1B).copy(alpha = 0.86f),
              border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f)),
              modifier = Modifier.testTag("live_overlay_timer_pill"),
            ) {
              Text(
                text = LiveVoiceSessionManager.formatElapsedMmSs(liveElapsedSeconds),
                color = Color.White.copy(alpha = 0.94f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            onClick = {
              if (isPaused) onTogglePause() else if (voiceState !is VoiceState.Listening && !isThinking) onStartListening() else onTogglePause()
            },
            shape = CircleShape,
            color = if (isPaused) NeonCyan else Color(0xFF101524).copy(alpha = 0.85f),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier.height(34.dp).testTag("live_pause_toggle_button"),
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
              Text(
                text = if (isPaused) "Resume" else "Pause",
                color = if (isPaused) Color.Black else Color.White,
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }

          IconButton(
            onClick = onMinimize,
            modifier = Modifier.size(36.dp).testTag("minimize_gemini_live_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.CloseFullscreen,
              contentDescription = "Minimize Live Assistant",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }

          Surface(
            onClick = {
              onEmergencyStop()
              onClose()
            },
            shape = CircleShape,
            color = Color(0xFF991B1B),
            border = BorderStroke(1.dp, Color(0xFFEF4444)),
            modifier = Modifier.size(34.dp).testTag("live_emergency_stop_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.Rounded.Stop, contentDescription = "Emergency Stop", tint = Color.White, modifier = Modifier.size(16.dp))
            }
          }

          Surface(
            onClick = onClose,
            shape = CircleShape,
            color = Color(0xFFDC2626),
            modifier = Modifier.size(34.dp).testTag("close_gemini_live_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.Rounded.Close, contentDescription = "End Live Voice", tint = Color.White, modifier = Modifier.size(18.dp))
            }
          }
        }
      }

      if (showControlsDrawer) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFF0E1320).copy(alpha = 0.90f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "${voiceGender.label} • $selectedModelLabel",
              color = Color.White.copy(alpha = 0.80f),
              style = MaterialTheme.typography.labelSmall,
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = onOpenCamera,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
              ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Camera Vision", color = Color.White, style = MaterialTheme.typography.labelMedium)
              }
              OutlinedButton(
                onClick = onRunDeepSearch,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
              ) {
                Icon(Icons.Outlined.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Deep Research", color = Color.White, style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }

      if (latestAssistantText.isNotBlank() ||
        (voiceState is VoiceState.Listening && voiceState.partialTranscript.isNotBlank())
      ) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFF0E1320).copy(alpha = 0.82f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
          modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            if (voiceState is VoiceState.Listening && voiceState.partialTranscript.isNotBlank()) {
              Text(
                text = "\"${voiceState.partialTranscript}\"",
                color = NeonCyan,
                style = MaterialTheme.typography.bodySmall,
              )
            }
            if (latestAssistantText.isNotBlank()) {
              Text(
                text = latestAssistantText,
                color = Color.White.copy(alpha = 0.94f),
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    }

    // Bottom Left-Aligned "Share screen with Live" Pill + Glassmorphic "Ask Mousomi..." Bar (Reference Image 2)
    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .windowInsetsPadding(bottomInsets)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
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
          modifier = Modifier.testTag("live_share_screen_pill"),
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
              text = if (isScreenShareActive) "Stop Screen Share" else "Share screen with Live",
              color = Color.White.copy(alpha = 0.94f),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
            )
          }
        }

        if (!canDrawOverlays) {
          Surface(
            onClick = {
              if (onRequestOverlayPermission != null) {
                onRequestOverlayPermission()
              } else {
                val intent =
                  Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                  ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  }
                runCatching { context.startActivity(intent) }
              }
            },
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF10192E).copy(alpha = 0.90f),
            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.72f)),
            shadowElevation = 8.dp,
            modifier = Modifier.testTag("live_float_over_apps_pill"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Layers,
                contentDescription = "Float Over Apps",
                tint = NeonCyan,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Float Over Apps",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }
      }

      Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF121622).copy(alpha = 0.90f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = { showControlsDrawer = !showControlsDrawer }) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Live options",
              tint = Color.White.copy(alpha = 0.90f),
              modifier = Modifier.size(23.dp),
            )
          }

          Box(
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (followUpText.isEmpty()) {
              Text(
                text = "Ask Mousomi...",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = followUpText,
              onValueChange = { followUpText = it },
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
                    val q = followUpText.trim()
                    if (q.isNotEmpty()) {
                      followUpText = ""
                      onSendFollowUp(q)
                    }
                  }
                ),
              modifier = Modifier.fillMaxWidth().testTag("live_follow_up_input"),
            )
          }

          IconButton(
            onClick = onToggleMute,
            modifier = Modifier.testTag("live_mute_toggle_button"),
          ) {
            Icon(
              imageVector = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
              contentDescription = if (isMuted) "Unmute" else "Mute",
              tint = if (isMuted) WarmAmber else Color.White.copy(alpha = 0.88f),
              modifier = Modifier.size(22.dp),
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          if (followUpText.isNotBlank()) {
            Surface(
              onClick = {
                val q = followUpText.trim()
                followUpText = ""
                onSendFollowUp(q)
              },
              shape = CircleShape,
              color = ElectricBlue,
              modifier = Modifier.size(44.dp).testTag("live_follow_up_send"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.Send,
                  contentDescription = "Send follow-up",
                  tint = Color.White,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          } else {
            RainbowLiveWaveformDiscButton(
              onClick = onClose,
              size = 44.dp,
              isLiveActive = true,
              contentDescriptionText = "Return to Standard Assistant",
              modifier = Modifier.testTag("live_follow_up_send"),
            )
          }
        }
      }
    }

    // Floating Draggable Iridescent Smoke Orb + Organic Audio Waveform above it (Reference Image 2)
    DraggableLiveOrbStage(
      reactiveFrame = effectiveFrame,
      isPaused = isPaused,
      isMuted = isMuted,
      reducedMotion = false,
      onOrbTap = {
        if (isPaused) onTogglePause()
        else if (voiceState !is VoiceState.Listening && !isThinking) onStartListening()
        else onToggleMute()
      },
      onDismissLive = onClose,
      modifier = Modifier.windowInsetsPadding(bottomInsets),
    )
  }
}

@Composable
fun MinimizedGeminiLivePill(
  voiceState: VoiceState,
  voiceRmsNormalized: Float,
  isThinking: Boolean,
  isPaused: Boolean = false,
  isMuted: Boolean = false,
  audioMetrics: VoiceAudioMetrics,
  onStartListening: () -> Unit,
  onStopListening: () -> Unit,
  onTogglePause: () -> Unit = {},
  onToggleMute: () -> Unit = {},
  onExpand: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  reactiveFrame: OrbReactiveFrame? = null,
) {
  var offsetX by remember { mutableStateOf(0f) }
  var offsetY by remember { mutableStateOf(0f) }

  Surface(
    shape = CircleShape,
    color = Color(0xFF071330).copy(alpha = 0.94f),
    border = BorderStroke(1.2.dp, (if (isPaused) WarmAmber else NeonCyan).copy(alpha = 0.72f)),
    tonalElevation = 8.dp,
    modifier =
      modifier
        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            offsetX += dragAmount.x
            offsetY += dragAmount.y
          }
        }
        .testTag("gemini_live_floating_pill"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      AnimatedAIOrb(
        voiceState = if (isPaused) VoiceState.Idle else voiceState,
        voiceRmsNormalized =
          if (isPaused || isMuted) 0f else (reactiveFrame?.reactiveAmplitude ?: voiceRmsNormalized),
        isThinking = !isPaused && isThinking,
        isMuted = isMuted || isPaused,
        orbSize = 34.dp,
        audioMetrics = audioMetrics,
        reactiveFrame = reactiveFrame,
      )
      Text(
        text =
          when {
            isPaused -> "Paused"
            isMuted -> "Muted"
            reactiveFrame?.sessionPhase == LiveSessionPhase.CONNECTING -> "Connecting"
            reactiveFrame?.sessionPhase == LiveSessionPhase.RECONNECTING -> "Reconnecting"
            isThinking || reactiveFrame?.sessionPhase == LiveSessionPhase.AI_PROCESSING -> "Thinking"
            voiceState is VoiceState.Speaking ||
              reactiveFrame?.sessionPhase == LiveSessionPhase.AI_SPEAKING -> "Speaking"
            voiceState is VoiceState.Listening ||
              reactiveFrame?.sessionPhase == LiveSessionPhase.LISTENING ||
              reactiveFrame?.sessionPhase == LiveSessionPhase.USER_SPEAKING -> "Listening"
            else -> "Live"
          },
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
      )
      IconButton(
        onClick = onToggleMute,
        modifier = Modifier.size(34.dp),
      ) {
        Icon(
          imageVector = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
          contentDescription = if (isMuted) "Unmute Live Voice" else "Mute Live Voice",
          tint = if (isMuted) Color(0xFFFCA5A5) else NeonCyan,
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = onTogglePause,
        modifier = Modifier.size(34.dp),
      ) {
        Icon(
          imageVector = if (isPaused) Icons.Outlined.Mic else Icons.Rounded.Stop,
          contentDescription = if (isPaused) "Resume Live Voice" else "Pause Live Voice",
          tint = if (isPaused) NeonCyan else WarmAmber,
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = onExpand,
        modifier = Modifier.size(34.dp).testTag("expand_gemini_live_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.OpenInFull,
          contentDescription = "Expand Live Assistant",
          tint = Color.White,
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier.size(34.dp).testTag("end_minimized_live_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = "End Live Assistant",
          tint = Color(0xFFFCA5A5),
          modifier = Modifier.size(18.dp),
        )
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
