package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenRecordingPhase
import com.example.core.voice.VoiceState
import com.example.domain.model.MemoryCategory
import com.example.domain.model.ScreenShareStatus
import com.example.service.LiveVoiceSessionService
import com.example.service.MousomiOverlayService
import com.example.ui.components.ChatMessagesList
import com.example.ui.components.CompactImageEditorSheet
import com.example.ui.components.DrawingAnnotationOverlay
import com.example.ui.components.GeminiTopBar
import com.example.ui.components.GeminiWelcomeCanvas
import com.example.ui.components.InAppFloatingOrbOverlay
import com.example.ui.components.NeonChatComposer
import com.example.ui.components.ScreenSelectionOverlay
import com.example.ui.components.geminiBackgroundGlow
import com.example.ui.model.MousomiUiState
import com.example.ui.model.SuggestionItem
import com.example.ui.theme.WarmAmber
import com.example.ui.viewmodel.MousomiViewModel
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun MainChatScreen(
  uiState: MousomiUiState,
  suggestions: List<SuggestionItem>,
  viewModel: MousomiViewModel,
  onNavigateToSettings: () -> Unit,
  onNavigateToControlCenter: () -> Unit = {},
  onNavigateToPermissions: () -> Unit = {},
  onRequestStartScreenShare: () -> Unit = {},
  onStopScreenShare: () -> Unit = {},
  onRequestStartScreenSelection: () -> Unit = {},
  onRequestStartScreenRecording: () -> Unit = {},
  onStopScreenRecording: () -> Unit = {},
  onCancelScreenRecording: () -> Unit = {},
  onNavigateToAssistantHub: (Int) -> Unit = {},
  onNavigateToLens: () -> Unit = {},
) {
  val context = LocalContext.current
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val composerFocusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

  LaunchedEffect(uiState.pendingNavigateToLens) {
    if (uiState.pendingNavigateToLens) {
      viewModel.onConsumeNavigateToLens()
      onNavigateToLens()
    }
  }

  LaunchedEffect(uiState.pendingStartScreenSelection) {
    if (uiState.pendingStartScreenSelection) {
      viewModel.onConsumePendingStartScreenSelection()
      onRequestStartScreenSelection()
    }
  }

  LaunchedEffect(uiState.shouldRequestComposerFocus) {
    if (uiState.shouldRequestComposerFocus) {
      runCatching { composerFocusRequester.requestFocus() }
      keyboardController?.show()
      viewModel.onConsumeComposerFocusRequest()
    }
  }

  LaunchedEffect(uiState.statusToastMessage) {
    val msg = uiState.statusToastMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.onClearStatusToast()
    }
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      if (uri != null) {
        viewModel.onAttachPickedPhoto(uri.toString())
      }
    }

  val multiPhotoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.onAttachPickedPhotos(uris.map { it.toString() })
      }
    }

  val videoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      if (uri != null) {
        viewModel.onAttachPickedVideo(uri.toString())
      }
    }

  val audioPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        viewModel.onAttachPickedAudio(uri.toString())
      }
    }

  val pickContactLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickContact()
    ) { uri ->
      if (uri != null) {
        viewModel.onPickedContactUri(uri)
      }
    }

  val contactsPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
      viewModel.onReResolvePendingContactAction()
    }

  val documentPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.onAttachPickedDocuments(uris.map { it.toString() })
      }
    }

  val takePictureLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicture()
    ) { success ->
      val uri = pendingCameraUri
      pendingCameraUri = null
      if (success && uri != null) {
        viewModel.onAttachPickedPhoto(uri.toString())
      }
    }

  val launchCameraFlow: () -> Unit = {
    val uri = viewModel.createCameraCaptureTempUri(context)
    if (uri != null) {
      pendingCameraUri = uri
      takePictureLauncher.launch(uri)
    }
  }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      viewModel.onRefreshPermissions()
      if (granted) {
        launchCameraFlow()
      } else {
        coroutineScope.launch {
          snackbarHostState.showSnackbar("Camera permission denied. Enable it in Permissions & Privacy.")
        }
      }
    }

  val onRequestCameraCapture: () -> Unit = {
    val granted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) {
      launchCameraFlow()
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      viewModel.onAudioPermissionResult(granted)
    }

  val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
  val edgeParams =
    EdgeLightStateMapper.resolve(
      config = uiState.edgeLightConfig,
      responseState = uiState.responseState,
      voiceState = uiState.voiceState,
      voiceRmsNormalized = uiState.voiceRmsNormalized,
      screenShareStatus = uiState.screenShareStatus,
      notificationAlertActive = uiState.notificationPulseActive,
      isSystemPowerSave = uiState.isSystemPowerSave,
      currentHour = currentHour,
      assistantSessionPhase = uiState.assistantSessionPhase,
      edgeActivationSignal = uiState.edgeActivationSignal,
    )

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled =
      !uiState.isGeminiLiveOpen &&
        !uiState.isDrawingOverlayOpen &&
        !uiState.screenSelectionState.isActive &&
        uiState.activeImageEditState == null,
    drawerContent = {
      MousomiDrawerContent(
        conversations = uiState.conversations,
        activeConversationId = uiState.activeConversationId,
        onSelectConversation = { id ->
          viewModel.onSelectConversation(id)
          coroutineScope.launch { drawerState.close() }
        },
        onDeleteConversation = viewModel::onDeleteConversation,
        onRenameConversation = viewModel::onRenameConversation,
        onNewChat = {
          viewModel.onNewChat()
          coroutineScope.launch { drawerState.close() }
        },
        onPreviewChatBubbles = {
          viewModel.onLoadSampleConversation()
          coroutineScope.launch { drawerState.close() }
        },
        onOpenSettings = {
          coroutineScope.launch { drawerState.close() }
          onNavigateToSettings()
        },
        onOpenControlCenter = {
          coroutineScope.launch { drawerState.close() }
          onNavigateToControlCenter()
        },
        onOpenPermissions = {
          coroutineScope.launch { drawerState.close() }
          onNavigateToPermissions()
        },
        isEmergencyActive = uiState.isAnyLiveSessionActive,
        onTriggerEmergencyStop = {
          coroutineScope.launch { drawerState.close() }
          viewModel.onTriggerEmergencyStop()
        },
        onOpenAssistantHub = {
          coroutineScope.launch { drawerState.close() }
          viewModel.onSetRequestedHubTab(0)
          onNavigateToAssistantHub(0)
        },
        onOpenLens = {
          coroutineScope.launch { drawerState.close() }
          onNavigateToLens()
        },
      )
    },
  ) {
    Box(modifier = Modifier.fillMaxSize().geminiBackgroundGlow()) {
      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
          SnackbarHost(
            hostState = snackbarHostState,
            modifier =
              Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
          )
        },
        modifier = Modifier.fillMaxSize(),
      ) { _ ->
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          GeminiTopBar(
            selectedModel = uiState.selectedModel,
            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
            onOpenModelSelector = { viewModel.onToggleModelMenu(true) },
            onNewChat = viewModel::onNewChat,
            onOpenProfile = { viewModel.onToggleProfileDialog(true) },
            isEmergencyActive = uiState.isAnyLiveSessionActive,
            onEmergencyStop = viewModel::onTriggerEmergencyStop,
            onOpenPermissions = onNavigateToPermissions,
            onOpenLens = onNavigateToLens,
            resolvedAutoModelName = uiState.resolvedAutoModelName,
            modifier = Modifier.widthIn(max = 760.dp),
          )

          Box(
            modifier = Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp),
            contentAlignment = Alignment.Center,
          ) {
            Crossfade(
              targetState = uiState.messages.isEmpty(),
              label = "welcome_or_chat_list",
            ) { isWelcome ->
              if (isWelcome) {
                GeminiWelcomeCanvas(
                  suggestions = suggestions,
                  recentConversations = uiState.conversations,
                  onSelectConversation = viewModel::onSelectConversation,
                  onQuickActionClick = { action ->
                    when (action) {
                      "analyze_image" ->
                        photoPickerLauncher.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                      "upload_document" ->
                        documentPickerLauncher.launch(
                          arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "text/csv",
                            "text/plain",
                            "application/json",
                          )
                        )
                      else -> viewModel.onSelectContextualAction(action)
                    }
                  },
                  onSuggestionClick = { item -> viewModel.onSelectSuggestion(item.promptText) },
                )
              } else {
                ChatMessagesList(
                  messages = uiState.messages,
                  responseState = uiState.responseState,
                  voiceState = uiState.voiceState,
                  imageVersionsMap = uiState.imageVersionsByRoot,
                  onOpenSettings = onNavigateToSettings,
                  onEditUserMessage = viewModel::onBeginEditMessage,
                  onRegenerateMessage = viewModel::onRegenerateFromMessage,
                  onSpeakMessage = viewModel::onSpeakMessage,
                  onStopSpeaking = viewModel::onStopSpeaking,
                  onSaveImage = viewModel::onSaveGeneratedImage,
                  onShareImage = { img -> viewModel.onShareGeneratedImage(context, img) },
                  onEditImage = viewModel::onStartEditGeneratedImage,
                  onSelectRegion = viewModel::onSelectRegionForGeneratedImage,
                  onAskAboutImage = viewModel::onAskAboutGeneratedImage,
                  onRegenerateImage = viewModel::onRegenerateGeneratedImage,
                  onApproveOrResumeTask = viewModel::onApproveOrResumeTask,
                  onPauseTask = viewModel::onPauseTask,
                  onRetryTask = viewModel::onRetryTask,
                  onCancelTask = viewModel::onCancelTask,
                  onOpenAssistantHub = { onNavigateToAssistantHub(0) },
                  onSaveVideo = viewModel::onSaveGeneratedVideo,
                  onShareVideo = { vid -> viewModel.onShareGeneratedVideo(context, vid) },
                  onSaveMusic = viewModel::onSaveGeneratedMusic,
                  onShareMusic = { mus -> viewModel.onShareGeneratedMusic(context, mus) },
                )
              }
            }
          }

          NeonChatComposer(
            composerState = uiState.neonComposerState,
            glowIntensity = uiState.composerGlowIntensity,
            reducedMotion = uiState.edgeLightConfig.reducedMotion,
            inputMode = uiState.inputMode,
            inputText = uiState.inputText,
            pendingAttachments = uiState.pendingAttachments,
            hasImageContext = uiState.hasImageContext,
            activeEditTarget = uiState.activeEditTarget,
            isGenerating = uiState.isGenerating,
            isEditingMessage = uiState.editingMessageId != null,
            showDragHandle = uiState.messages.isNotEmpty(),
            operatingMode = uiState.operatingMode,
            onSelectOperatingMode = viewModel::onSelectOperatingMode,
            onOpenImageStudio = { viewModel.onSelectContextualAction("open_image_tools") },
            onSelectMode = viewModel::onSelectMode,
            onInputChanged = viewModel::onInputChanged,
            onOpenAttachments = { viewModel.onToggleAttachmentSheet(true) },
            onOpenCameraCapture = onRequestCameraCapture,
            onRemoveAttachment = viewModel::onRemovePendingAttachment,
            onCancelEditImageTarget = viewModel::onCancelEditImageTarget,
            onCancelEditing = viewModel::onCancelEditing,
            onVoiceClick = {
              val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                  PackageManager.PERMISSION_GRANTED
              if (granted) {
                viewModel.onAudioPermissionResult(true)
              } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            onLiveClick = {
              val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                  PackageManager.PERMISSION_GRANTED
              if (granted) {
                if (Settings.canDrawOverlays(context)) {
                  ContextCompat.startForegroundService(
                    context,
                    MousomiOverlayService.buildStartFloatingLiveIntent(context),
                  )
                  ContextCompat.startForegroundService(
                    context,
                    LiveVoiceSessionService.buildStartIntent(context),
                  )
                  viewModel.onSetFloatingLiveModeActive(true)
                  (context as? Activity)?.moveTaskToBack(true)
                } else {
                  viewModel.onToggleGeminiLive(true)
                }
              } else {
                viewModel.onRequestPendingLiveAfterPermission()
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            onSendClick = viewModel::onSendMessage,
            onStopGeneration = viewModel::onStopGeneration,
            onContextualAction = viewModel::onSelectContextualAction,
            attachedVisualsByUri = uiState.attachedVisualsByUri,
            onEditImagesClick = { viewModel.onOpenCompactImageEditor() },
            composerFocusRequester = composerFocusRequester,
            voiceRmsNormalized = uiState.voiceRmsNormalized,
            isVoiceListening = uiState.voiceState is VoiceState.Listening,
            isInlineDictationActive = uiState.isInlineDictationSessionActive,
            isInlineDictationPaused = uiState.isInlineDictationPaused,
            inlineDictationError = uiState.inlineDictationError,
            onPauseVoiceDictation = viewModel::onPauseInlineDictation,
            onStopVoiceDictation = viewModel::onStopInlineDictation,
            onRetryVoiceDictation = viewModel::onRetryInlineDictation,
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }
      }

      if (uiState.screenShareStatus != ScreenShareStatus.STOPPED) {
        Surface(
          shape = CircleShape,
          color = Color(0xFF0E172A).copy(alpha = 0.94f),
          border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
          modifier =
            Modifier
              .align(Alignment.TopCenter)
              .statusBarsPadding()
              .padding(top = 58.dp)
              .testTag("screen_share_active_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.AutoMirrored.Outlined.ScreenShare, contentDescription = null, tint = Color(0xFF38BDF8))
            Text(
              text = "${uiState.screenShareStatus.label} (${uiState.capturedScreenFrames.size})",
              color = Color.White,
              fontSize = 12.sp,
            )
            TextButton(onClick = onStopScreenShare) {
              Text("Stop", color = Color(0xFFF87171))
            }
          }
        }
      }

      if (uiState.screenRecordingState.phase != ScreenRecordingPhase.IDLE) {
        val recState = uiState.screenRecordingState
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFF10172A).copy(alpha = 0.95f),
          border =
            BorderStroke(
              1.dp,
              if (recState.phase == ScreenRecordingPhase.RECORDING) Color(0xFFEF4444)
              else Color(0xFF38BDF8).copy(alpha = 0.6f),
            ),
          modifier =
            Modifier
              .align(Alignment.TopCenter)
              .statusBarsPadding()
              .padding(
                top = if (uiState.screenShareStatus != ScreenShareStatus.STOPPED) 110.dp else 58.dp,
                start = 16.dp,
                end = 16.dp,
              )
              .testTag("main_screen_recording_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Videocam,
              contentDescription = null,
              tint =
                if (recState.phase == ScreenRecordingPhase.RECORDING) Color(0xFFEF4444)
                else Color(0xFF38BDF8),
            )
            Text(
              text =
                when (recState.phase) {
                  ScreenRecordingPhase.AWAITING_CONFIRMATION ->
                    recState.statusMessage ?: "Confirm MP4 screen recording?"
                  ScreenRecordingPhase.AWAITING_CONSENT ->
                    "Grant screen capture consent to record…"
                  ScreenRecordingPhase.RECORDING ->
                    "REC ${ScreenRecordingManager.formatElapsed(recState.elapsedSeconds)} • ${recState.displayPath}"
                  ScreenRecordingPhase.FINALIZING -> "Finalizing MP4…"
                  ScreenRecordingPhase.SAVED -> "Saved: ${recState.displayPath}"
                  ScreenRecordingPhase.CANCELLED -> recState.statusMessage ?: "Recording cancelled"
                  ScreenRecordingPhase.FAILED -> recState.statusMessage ?: "Recording failed"
                  ScreenRecordingPhase.IDLE -> ""
                },
              color = Color.White,
              fontSize = 12.sp,
              modifier = Modifier.weight(1f, fill = false),
            )
            when (recState.phase) {
              ScreenRecordingPhase.AWAITING_CONFIRMATION -> {
                TextButton(
                  onClick = onRequestStartScreenRecording,
                  modifier = Modifier.testTag("main_rec_confirm"),
                ) { Text("Start", color = Color(0xFF38BDF8)) }
                TextButton(
                  onClick = viewModel::onDismissScreenRecording,
                  modifier = Modifier.testTag("main_rec_cancel"),
                ) { Text("Cancel", color = Color(0xFFF87171)) }
              }
              ScreenRecordingPhase.RECORDING -> {
                TextButton(
                  onClick = onStopScreenRecording,
                  modifier = Modifier.testTag("main_rec_stop"),
                ) { Text("Stop & Save", color = Color(0xFFFCA5A5)) }
                TextButton(
                  onClick = onCancelScreenRecording,
                  modifier = Modifier.testTag("main_rec_cancel"),
                ) { Text("Cancel", color = Color.White.copy(alpha = 0.78f)) }
              }
              ScreenRecordingPhase.SAVED,
              ScreenRecordingPhase.CANCELLED,
              ScreenRecordingPhase.FAILED -> {
                TextButton(onClick = viewModel::onDismissScreenRecording) {
                  Text("Dismiss", color = Color(0xFF38BDF8))
                }
              }
              else -> Unit
            }
          }
        }
      }

      if (uiState.isModelMenuOpen) {
        ModelSelectionBottomSheet(
          selectedModel = uiState.selectedModel,
          onSelectModel = viewModel::onSelectModel,
          onDismiss = { viewModel.onToggleModelMenu(false) },
        )
      }

      if (uiState.isAttachmentSheetOpen) {
        AttachmentBottomSheet(
          onTakeCameraPhoto = {
            viewModel.onToggleAttachmentSheet(false)
            onRequestCameraCapture()
          },
          onOpenLens = {
            viewModel.onToggleAttachmentSheet(false)
            onNavigateToLens()
          },
          onStartScreenSelection = {
            viewModel.onToggleAttachmentSheet(false)
            onRequestStartScreenSelection()
          },
          onPickFromGallery = {
            viewModel.onToggleAttachmentSheet(false)
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          onPickMultipleFromGallery = {
            viewModel.onToggleAttachmentSheet(false)
            multiPhotoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          onPickVideo = {
            viewModel.onToggleAttachmentSheet(false)
            videoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
          },
          onPickAudio = {
            viewModel.onToggleAttachmentSheet(false)
            audioPickerLauncher.launch(arrayOf("audio/*"))
          },
          onPickDocuments = {
            viewModel.onToggleAttachmentSheet(false)
            documentPickerLauncher.launch(
              arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv",
                "text/comma-separated-values",
                "text/plain",
                "application/json",
              )
            )
          },
          onAttachSamplePreview = viewModel::onAttachSampleImage,
          onDismiss = { viewModel.onToggleAttachmentSheet(false) },
        )
      }

      if (uiState.isImageToolsSheetOpen) {
        ImageToolsBottomSheet(
          onSelectImageAction = { actionId ->
            viewModel.onSelectContextualAction(actionId)
          },
          onDismiss = { viewModel.onToggleImageToolsSheet(false) },
        )
      }

      if (uiState.isMoreToolsSheetOpen) {
        MoreToolsBottomSheet(
          onSelectToolAction = { actionId ->
            viewModel.onSelectContextualAction(actionId)
          },
          onOpenScreenAssistant = {
            viewModel.onToggleMoreToolsSheet(false)
            viewModel.analyzeLatestScreenFrameOrPrompt(onRequestStartScreenShare)
          },
          onOpenLens = {
            viewModel.onToggleMoreToolsSheet(false)
            onNavigateToLens()
          },
          onStartScreenSelection = {
            viewModel.onToggleMoreToolsSheet(false)
            onRequestStartScreenSelection()
          },
          onDismiss = { viewModel.onToggleMoreToolsSheet(false) },
        )
      }

      if (uiState.isProfileDialogOpen) {
        ProfileAccountDialog(
          authSession = uiState.authSession,
          onOpenSettings = onNavigateToSettings,
          onOpenPermissions = onNavigateToPermissions,
          onSignOut = viewModel::onSignOut,
          onDismiss = { viewModel.onToggleProfileDialog(false) },
        )
      }

      AnimatedVisibility(
        visible = uiState.isGeminiLiveOpen && !uiState.isGeminiLiveMinimized,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        val latestAssistantText =
          uiState.liveAssistantTranscript.ifBlank {
            uiState.messages.lastOrNull { !it.isUser && it.text.isNotBlank() }?.text.orEmpty()
          }
        GeminiLiveOverlay(
          voiceState = uiState.voiceState,
          voiceRmsNormalized = uiState.voiceRmsNormalized,
          isThinking = uiState.isGenerating,
          isPaused = uiState.isLiveVoicePaused,
          isMuted = uiState.isLiveVoiceMuted,
          selectedModelLabel = uiState.resolvedAutoModelName ?: uiState.selectedModel.displayName,
          voiceGender = uiState.voicePreferences.voiceGender,
          latestAssistantText = latestAssistantText,
          onSelectVoiceGender = viewModel::onUpdateVoiceGender,
          onStartListening = viewModel::onStartVoiceListening,
          onStopListening = viewModel::onStopVoiceListening,
          onTogglePause = viewModel::onTogglePauseLiveVoice,
          onToggleMute = viewModel::onToggleMuteLiveVoice,
          onSendFollowUp = viewModel::onSendLiveFollowUp,
          onOpenCamera = {
            viewModel.onToggleGeminiLiveMinimized(true)
            onRequestCameraCapture()
          },
          onRunDeepSearch = {
            viewModel.onToggleGeminiLiveMinimized(true)
            viewModel.onSelectContextualAction("deep_research")
          },
          onMinimize = { viewModel.onToggleGeminiLiveMinimized(true) },
          onEmergencyStop = viewModel::onTriggerEmergencyStop,
          onClose = { viewModel.onToggleGeminiLive(false) },
          audioMetrics = uiState.voiceAudioMetrics,
          reactiveFrame = uiState.orbReactiveFrame,
          liveConnectionState = uiState.liveConnectionState,
          liveElapsedSeconds = uiState.liveElapsedSeconds,
          isScreenShareActive = uiState.screenShareStatus != ScreenShareStatus.STOPPED,
          onShareScreenWithLive = {
            viewModel.onShareScreenWithLive(
              onRequestStartScreenShare = onRequestStartScreenShare,
              onRequestStopScreenShare = onStopScreenShare,
            )
          },
        )
      }

      AnimatedVisibility(
        visible = uiState.isGeminiLiveOpen && uiState.isGeminiLiveMinimized,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 60.dp, end = 14.dp),
      ) {
        MinimizedGeminiLivePill(
          voiceState = uiState.voiceState,
          voiceRmsNormalized = uiState.voiceRmsNormalized,
          isThinking = uiState.isGenerating,
          isPaused = uiState.isLiveVoicePaused,
          isMuted = uiState.isLiveVoiceMuted,
          audioMetrics = uiState.voiceAudioMetrics,
          onStartListening = viewModel::onStartVoiceListening,
          onStopListening = viewModel::onStopVoiceListening,
          onTogglePause = viewModel::onTogglePauseLiveVoice,
          onToggleMute = viewModel::onToggleMuteLiveVoice,
          onExpand = { viewModel.onToggleGeminiLiveMinimized(false) },
          onClose = { viewModel.onToggleGeminiLive(false) },
          reactiveFrame = uiState.orbReactiveFrame,
        )
      }

      if (uiState.isQuickNoteDialogOpen) {
        QuickNoteCaptureDialog(
          onSaveNote = { text -> viewModel.onSaveMemoryItem(MemoryCategory.QUICK_NOTE, text) },
          onDismiss = { viewModel.onToggleQuickNoteDialog(false) },
        )
      }

      if (uiState.isDrawingOverlayOpen) {
        DrawingAnnotationOverlay(
          backgroundImagePath = uiState.drawingTargetImage?.filePath,
          onAnalyzeMarkedRegion = viewModel::onAnalyzeAnnotatedRegion,
          onAttachMarkedRegion = viewModel::onAttachAnnotatedRegion,
          onSaveDrawing = viewModel::onSaveDrawingAnnotation,
          onClose = { viewModel.onToggleDrawingOverlay(false) },
        )
      }

      InAppFloatingOrbOverlay(
        config = uiState.floatingOrbConfig,
        visualState = edgeParams.visualState,
        onPositionCommitted = viewModel::onOrbPositionCommitted,
        onQuickAction = { action ->
          viewModel.onHandleOrbQuickAction(
            action = action,
            onRequestScreenShare = onRequestStartScreenShare,
            onNavigateToControlCenter = onNavigateToControlCenter,
          )
        },
        audioMetrics = uiState.voiceAudioMetrics,
        onPopupAction = { popup ->
          viewModel.onHandleOrbPopupAction(
            action = popup,
            onRequestScreenShare = onRequestStartScreenShare,
            onRequestCameraCapture = onRequestCameraCapture,
            onNavigateToLens = onNavigateToLens,
            onNavigateToAssistantHub = onNavigateToAssistantHub,
          )
        },
        onSelectScreenRegion = onRequestStartScreenSelection,
      )

      if (uiState.screenSelectionState.isActive && !uiState.screenSelectionState.isSystemOverlayMode) {
        ScreenSelectionOverlay(
          state = uiState.screenSelectionState,
          geometryEngine = viewModel.geometryEngineForUi,
          onSelectTool = viewModel::onSelectSelectionTool,
          onStrokeStart = viewModel::onSelectionStrokeStart,
          onStrokeMove = viewModel::onSelectionStrokeMove,
          onStrokeEnd = viewModel::onSelectionStrokeEnd,
          onHandleDrag = viewModel::onSelectionAdjustHandle,
          onUndo = viewModel::onSelectionUndo,
          onClear = viewModel::onSelectionClear,
          onCancel = viewModel::onCancelScreenSelection,
          onConfirmSelection = viewModel::onConfirmScreenSelection,
          reducedMotion = uiState.edgeLightConfig.reducedMotion,
        )
      }

      val editState = uiState.activeImageEditState
      if (editState != null) {
        CompactImageEditorSheet(
          editState = editState,
          onAdjustHandle = viewModel::onAdjustImageEditorHandle,
          onSelectAspectPreset = viewModel::onSelectEditorAspectPreset,
          onRotate90 = viewModel::onRotateEditor90,
          onRetakeSelection = { viewModel.onRetakeScreenSelection(onRequestStartScreenSelection) },
          onResetCrop = viewModel::onResetEditorCrop,
          onCancel = viewModel::onCancelImageEditor,
          onConfirm = viewModel::onConfirmImageEditor,
        )
      }

      uiState.pendingPhoneAction?.let { phoneAction ->
        AlertDialog(
          onDismissRequest = viewModel::onDismissPendingActions,
          title = { Text(phoneAction.type.title) },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(phoneAction.summary)
              if (phoneAction.candidateContacts.isNotEmpty()) {
                phoneAction.candidateContacts.take(6).forEach { candidate ->
                  OutlinedButton(
                    onClick = { viewModel.onSelectAmbiguousContact(candidate) },
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text("${candidate.displayName} • ${candidate.phoneNumber}")
                  }
                }
              }
              if (phoneAction.needsContactsPermission || phoneAction.targetNumber.isBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (phoneAction.needsContactsPermission) {
                    TextButton(onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                      Text("Allow Contacts")
                    }
                  }
                  TextButton(onClick = { pickContactLauncher.launch(null) }) {
                    Text("Pick Contact")
                  }
                }
              }
            }
          },
          confirmButton = {
            Button(
              onClick = viewModel::onConfirmPendingPhoneAction,
              enabled = !phoneAction.isContactAmbiguous,
              modifier = Modifier.testTag("confirm_phone_action_button"),
            ) {
              Text("Open Composer")
            }
          },
          dismissButton = {
            OutlinedButton(onClick = viewModel::onDismissPendingActions) {
              Text("Cancel")
            }
          },
        )
      }

      uiState.pendingSocialAction?.let { socialAction ->
        AlertDialog(
          onDismissRequest = viewModel::onDismissPendingActions,
          title = { Text("Confirm ${socialAction.platform.displayName}") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(socialAction.summary)
              if (socialAction.noteOrLimitation.isNotBlank()) {
                Text(
                  text = socialAction.noteOrLimitation,
                  style = MaterialTheme.typography.bodySmall,
                  color = WarmAmber,
                )
              }
              if (socialAction.candidateContacts.isNotEmpty()) {
                socialAction.candidateContacts.take(6).forEach { candidate ->
                  OutlinedButton(
                    onClick = { viewModel.onSelectAmbiguousContact(candidate) },
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text("${candidate.displayName} • ${candidate.phoneNumber}")
                  }
                }
              }
              if (socialAction.needsContactsPermission ||
                (socialAction.platform.name == "WHATSAPP" && socialAction.isContactAmbiguous)
              ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (socialAction.needsContactsPermission) {
                    TextButton(onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                      Text("Allow Contacts")
                    }
                  }
                  TextButton(onClick = { pickContactLauncher.launch(null) }) {
                    Text("Pick Contact")
                  }
                }
              }
            }
          },
          confirmButton = {
            Button(
              onClick = viewModel::onConfirmPendingSocialAction,
              enabled = !socialAction.isContactAmbiguous,
              modifier = Modifier.testTag("confirm_social_action_button"),
            ) {
              Text("Open ${socialAction.platform.displayName}")
            }
          },
          dismissButton = {
            OutlinedButton(onClick = viewModel::onDismissPendingActions) {
              Text("Cancel")
            }
          },
        )
      }

      uiState.pendingAccessibilityAction?.let { accAction ->
        AlertDialog(
          onDismissRequest = viewModel::onDismissPendingAccessibilityAction,
          title = { Text(accAction.statusLabel) },
          text = { Text(accAction.pendingActionSummary.ifBlank { accAction.detail }) },
          confirmButton = {
            Button(
              onClick = viewModel::onConfirmPendingAccessibilityAction,
              modifier = Modifier.testTag("confirm_accessibility_action_button"),
            ) {
              Text("Proceed")
            }
          },
          dismissButton = {
            OutlinedButton(onClick = viewModel::onDismissPendingAccessibilityAction) {
              Text("Cancel")
            }
          },
        )
      }

      uiState.pendingMemoryConfirmation?.let { pendingMem ->
        AlertDialog(
          onDismissRequest = viewModel::onDismissPendingMemory,
          icon = {
            Icon(
              imageVector = Icons.Outlined.Psychology,
              contentDescription = null,
            )
          },
          title = { Text("Save to My Memory?") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(pendingMem.promptMessage)
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = pendingMem.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = pendingMem.extractedContent,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }
            }
          },
          confirmButton = {
            Button(
              onClick = viewModel::onConfirmPendingMemory,
              modifier = Modifier.testTag("confirm_memory_save_button"),
            ) {
              Text("Save to My Memory")
            }
          },
          dismissButton = {
            OutlinedButton(
              onClick = viewModel::onDismissPendingMemory,
              modifier = Modifier.testTag("dismiss_memory_save_button"),
            ) {
              Text("Skip")
            }
          },
        )
      }

      uiState.pendingDiscoveryPrompt?.let { discovery ->
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
          tonalElevation = 4.dp,
          modifier =
            Modifier
              .align(Alignment.TopCenter)
              .statusBarsPadding()
              .padding(top = 62.dp, start = 16.dp, end = 16.dp)
              .widthIn(max = 560.dp)
              .testTag("memory_discovery_banner"),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Personalize Mousomi (${discovery.targetCategory.label})",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
              )
              TextButton(
                onClick = viewModel::onDismissDiscoveryPrompt,
                modifier = Modifier.testTag("dismiss_discovery_prompt_button"),
              ) {
                Text("Skip")
              }
            }
            Text(
              text = discovery.questionText,
              style = MaterialTheme.typography.bodyMedium,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = {
                  when (discovery.targetCategory) {
                    MemoryCategory.FAVORITE_SONG -> viewModel.onInputChanged("My favorite song is ")
                    MemoryCategory.LANGUAGE_PREFERENCE -> viewModel.onReplyDiscoveryPrompt("I prefer bilingual English and Bengali")
                    MemoryCategory.FAVORITE_ARTIST -> viewModel.onInputChanged("My favorite singer is ")
                    MemoryCategory.HOBBY_INTEREST -> viewModel.onInputChanged("I enjoy ")
                    else -> viewModel.onInputChanged("Remember my ${discovery.targetCategory.label.lowercase()}: ")
                  }
                  viewModel.onDismissDiscoveryPrompt()
                },
                modifier = Modifier.testTag("reply_discovery_prompt_button"),
              ) {
                Text(
                  if (discovery.targetCategory == MemoryCategory.LANGUAGE_PREFERENCE) "English + Bengali"
                  else "Answer in Chat"
                )
              }
            }
          }
        }
      }
    }
  }
}
