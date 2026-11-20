package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.edge.EdgeLightStateMapper
import com.example.domain.model.MemoryCategory
import com.example.domain.model.ScreenShareStatus
import com.example.ui.components.ChatMessagesList
import com.example.ui.components.DrawingAnnotationOverlay
import com.example.ui.components.GeminiTopBar
import com.example.ui.components.GeminiWelcomeCanvas
import com.example.ui.components.InAppFloatingOrbOverlay
import com.example.ui.components.NeonChatComposer
import com.example.ui.components.geminiBackgroundGlow
import com.example.ui.model.MousomiUiState
import com.example.ui.model.SuggestionItem
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
  onNavigateToAssistantHub: (Int) -> Unit = {},
  onNavigateToLens: () -> Unit = {},
) {
  val context = LocalContext.current
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

  LaunchedEffect(uiState.pendingNavigateToLens) {
    if (uiState.pendingNavigateToLens) {
      viewModel.onConsumeNavigateToLens()
      onNavigateToLens()
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
    )

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = !uiState.isGeminiLiveOpen && !uiState.isDrawingOverlayOpen,
    drawerContent = {
      MousomiDrawerContent(
        conversations = uiState.conversations,
        activeConversationId = uiState.activeConversationId,
        onSelectConversation = { id ->
          viewModel.onSelectConversation(id)
          coroutineScope.launch { drawerState.close() }
        },
        onDeleteConversation = viewModel::onDeleteConversation,
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
            onLiveClick = { viewModel.onToggleGeminiLive(true) },
            onSendClick = viewModel::onSendMessage,
            onStopGeneration = viewModel::onStopGeneration,
            onContextualAction = viewModel::onSelectContextualAction,
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

      if (uiState.isVoiceDialogOpen) {
        VoiceInputDialog(
          hasAudioPermission = uiState.hasAudioPermission,
          voiceState = uiState.voiceState,
          onStartListening = viewModel::onStartVoiceListening,
          onStopListening = viewModel::onStopVoiceListening,
          onUseTranscript = viewModel::onInputChanged,
          onDismiss = { viewModel.onToggleVoiceDialog(false) },
        )
      }

      AnimatedVisibility(
        visible = uiState.isGeminiLiveOpen && !uiState.isGeminiLiveMinimized,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        val latestAssistantText =
          uiState.messages.lastOrNull { !it.isUser && it.text.isNotBlank() }?.text.orEmpty()
        GeminiLiveOverlay(
          voiceState = uiState.voiceState,
          voiceRmsNormalized = uiState.voiceRmsNormalized,
          isThinking = uiState.isGenerating,
          voiceGender = uiState.voicePreferences.voiceGender,
          latestAssistantText = latestAssistantText,
          onSelectVoiceGender = viewModel::onUpdateVoiceGender,
          onStartListening = viewModel::onStartVoiceListening,
          onStopListening = viewModel::onStopVoiceListening,
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
          audioMetrics = uiState.voiceAudioMetrics,
          onStartListening = viewModel::onStartVoiceListening,
          onStopListening = viewModel::onStopVoiceListening,
          onExpand = { viewModel.onToggleGeminiLiveMinimized(false) },
          onClose = { viewModel.onToggleGeminiLive(false) },
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
            onNavigateToAssistantHub = onNavigateToAssistantHub,
          )
        },
      )
    }
  }
}
