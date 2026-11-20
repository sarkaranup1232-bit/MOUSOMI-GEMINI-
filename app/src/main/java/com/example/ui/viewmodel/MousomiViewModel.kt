package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.firebase.FirebaseAiLogicService
import com.example.ai.image.ImageStorageManager
import com.example.ai.media.VideoAndMusicGenerationService
import com.example.ai.provider.OneTapApiActivationService
import com.example.core.alarms.ReminderManager
import com.example.core.alarms.TimerManager
import com.example.core.automation.ControlledAutomationEngine
import com.example.core.device.DeviceActionExecutor
import com.example.core.device.InstalledAppRepository
import com.example.core.image.ImageProcessor
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.performance.SmartPerformanceMonitor
import com.example.core.permissions.EmergencyStopController
import com.example.core.permissions.PermissionManager
import com.example.core.screen.RegionCropper
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.security.SecurityValidator
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.data.remote.firestore.FirestoreCloudSyncRepository
import com.example.data.repository.AddOnRepository
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.Part4OperationsRepository
import com.example.data.repository.Part5Repository
import com.example.domain.agent.AutonomousAgentOrchestrator
import com.example.domain.auth.OptionalAuthGateway
import com.example.domain.memory.LongTermMemoryManager
import com.example.domain.memory.MemoryWriteOutcome
import com.example.domain.model.ActiveEditImageTarget
import com.example.domain.model.ActivationHealthStatus
import com.example.domain.model.AgentBudgetConfig
import com.example.domain.model.AiResponseState
import com.example.domain.model.AlarmRecurrence
import com.example.domain.model.AnnotationStroke
import com.example.domain.model.AppSettings
import com.example.domain.model.AutomationTaskRecord
import com.example.domain.model.AutomationWorkflowType
import com.example.domain.model.CapabilityActivationBadge
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DetectedProviderKind
import com.example.domain.model.DomainMessage
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.GeneratedMusicResult
import com.example.domain.model.GeneratedVideoResult
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.LensAnalysisResult
import com.example.domain.model.LensFollowUpTurn
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.MemoryCategory
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.OneTapActivationResult
import com.example.domain.model.OrbPopupAction
import com.example.domain.model.OrbQuickAction
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.PrivacyAndPerformanceConfig
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.ReminderItemType
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.ScreenIntelligenceConfig
import com.example.domain.model.TokenUsage
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePreferences
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.task.TaskExecutionEngine
import com.example.domain.task.TaskStateManager
import com.example.ui.model.AiModelOption
import com.example.ui.model.AttachmentPreview
import com.example.ui.model.ChatMessage
import com.example.ui.model.InputMode
import com.example.ui.model.MousomiUiState
import com.example.ui.model.SuggestionIconType
import com.example.ui.model.SuggestionItem
import com.example.ui.theme.AppThemeMode
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MousomiViewModel @Inject constructor(
  private val aiOrchestrator: AiOrchestrator,
  private val chatRepository: ChatRepository,
  private val settingsRepository: SettingsRepository,
  private val imageProcessor: ImageProcessor,
  private val voiceController: VoiceController,
  private val securityValidator: SecurityValidator,
  private val tempMediaCacheManager: TempMediaCacheManager? = null,
  private val screenShareSessionManager: ScreenShareSessionManager? = null,
  private val longTermMemoryManager: LongTermMemoryManager? = null,
  private val notificationEngine: NotificationIntelligenceEngine? = null,
  private val automationEngine: ControlledAutomationEngine? = null,
  private val agentOrchestrator: AutonomousAgentOrchestrator? = null,
  private val operationsRepository: Part4OperationsRepository? = null,
  private val performanceMonitor: SmartPerformanceMonitor? = null,
  private val part5Repository: Part5Repository? = null,
  private val imageStorageManager: ImageStorageManager? = null,
  private val permissionManager: PermissionManager? = null,
  private val emergencyStopController: EmergencyStopController? = null,
  private val deviceActionExecutor: DeviceActionExecutor? = null,
  private val installedAppRepository: InstalledAppRepository? = null,
  private val reminderManager: ReminderManager? = null,
  private val timerManager: TimerManager? = null,
  private val taskExecutionEngine: TaskExecutionEngine? = null,
  private val taskStateManager: TaskStateManager? = null,
  private val addOnRepository: AddOnRepository? = null,
  private val oneTapApiActivationService: OneTapApiActivationService? = null,
  private val authGateway: OptionalAuthGateway? = null,
  private val firestoreSync: FirestoreCloudSyncRepository? = null,
  private val videoAndMusicService: VideoAndMusicGenerationService? = null,
  private val firebaseAiLogicService: FirebaseAiLogicService? = null,
) : ViewModel() {

  constructor(
    aiOrchestrator: AiOrchestrator,
    chatRepository: ChatRepository,
    settingsRepository: SettingsRepository,
    imageProcessor: ImageProcessor,
    voiceController: VoiceController,
    securityValidator: SecurityValidator,
  ) : this(
    aiOrchestrator = aiOrchestrator,
    chatRepository = chatRepository,
    settingsRepository = settingsRepository,
    imageProcessor = imageProcessor,
    voiceController = voiceController,
    securityValidator = securityValidator,
    tempMediaCacheManager = null,
    screenShareSessionManager = null,
    longTermMemoryManager = null,
    notificationEngine = null,
    automationEngine = null,
    agentOrchestrator = null,
    operationsRepository = null,
    performanceMonitor = null,
    part5Repository = null,
    imageStorageManager = null,
    permissionManager = null,
    emergencyStopController = null,
    deviceActionExecutor = null,
    installedAppRepository = null,
    reminderManager = null,
    timerManager = null,
    taskExecutionEngine = null,
    taskStateManager = null,
    addOnRepository = null,
    oneTapApiActivationService = null,
    authGateway = null,
    firestoreSync = null,
    videoAndMusicService = null,
    firebaseAiLogicService = null,
  )

  val defaultSuggestions: List<SuggestionItem> =
    listOf(
      SuggestionItem(
        id = "story",
        promptText = "Spark a story idea from my experiences",
        iconType = SuggestionIconType.PENCIL,
      ),
      SuggestionItem(
        id = "captions",
        promptText = "Create engaging video captions",
        iconType = SuggestionIconType.VIDEO_CAPTIONS,
      ),
      SuggestionItem(
        id = "wallpaper",
        promptText = "Make a cutout phone wallpaper",
        iconType = SuggestionIconType.WALLPAPER_SPARKLES,
      ),
    )

  private val _uiState = MutableStateFlow(MousomiUiState())
  val uiState: StateFlow<MousomiUiState> = _uiState.asStateFlow()

  private var activeGenerationJob: Job? = null
  private var completedPulseJob: Job? = null
  private var messagesObserverJob: Job? = null
  private var imageVersionsObserverJob: Job? = null
  private var firestoreConversationsJob: Job? = null
  private var firestoreMessagesJob: Job? = null
  private var activeAssistantMessageId: String? = null
  private var liveSessionStartedAtMs: Long? = null
  private var liveSessionTurnsCount: Int = 0
  private var nextRequestImageGenFlag: Boolean = false
  private var nextRequestDeepResearchFlag: Boolean = false

  init {
    observeAuthAndCloudSync()
    observeSettings()
    observeConversations()
    observeVoiceState()
    observePart4Streams()
    observePart5Streams()
    observeAddOnStreams()
    observeActiveConversationMessages(_uiState.value.activeConversationId)
    refreshPowerState()
    onRefreshPermissions()
    onRefreshInstalledApps()
    viewModelScope.launch { reminderManager?.checkMissedReminders() }
    emergencyStopController?.registerStopListener {
      activeGenerationJob?.cancel()
      activeGenerationJob = null
      activeAssistantMessageId = null
      _uiState.update {
        it.copy(
          isGeminiLiveOpen = false,
          isGeminiLiveMinimized = false,
          isVoiceDialogOpen = false,
          responseState = AiResponseState.Cancelled,
          statusToastMessage = "Emergency Stop executed: all active sessions stopped.",
        )
      }
    }
  }

  private fun observeAuthAndCloudSync() {
    val gateway = authGateway ?: return
    gateway.registerBeforeSignOutHook { stopCloudAndMediaSessions() }
    viewModelScope.launch {
      gateway.observeSession().collectLatest { session ->
        _uiState.update { it.copy(authSession = session) }
        if (session.isSignedIn && session.uid.isNotBlank() && firestoreSync != null) {
          firestoreSync.syncUserProfileAndPreferences(
            displayName = session.displayName,
            email = session.email,
            photoUrl = session.photoUrl,
            settings = buildCurrentAppSettingsSnapshot(),
          )
          startFirestoreConversationsObserver()
        } else {
          firestoreConversationsJob?.cancel()
          firestoreMessagesJob?.cancel()
        }
      }
    }
  }

  private fun startFirestoreConversationsObserver() {
    val sync = firestoreSync ?: return
    val impl = chatRepository as? ChatRepositoryImpl ?: return
    firestoreConversationsJob?.cancel()
    firestoreConversationsJob =
      viewModelScope.launch {
        sync.observeConversations()
          .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptyList())
          }
          .collectLatest { remoteConvs ->
            if (remoteConvs.isNotEmpty()) {
              impl.mergeRemoteConversations(remoteConvs)
            }
          }
      }
  }

  fun onSignOut() {
    stopCloudAndMediaSessions()
    viewModelScope.launch {
      authGateway?.signOut()
    }
  }

  private fun stopCloudAndMediaSessions() {
    activeGenerationJob?.cancel()
    activeGenerationJob = null
    firestoreConversationsJob?.cancel()
    firestoreMessagesJob?.cancel()
    messagesObserverJob?.cancel()
    imageVersionsObserverJob?.cancel()
    completedPulseJob?.cancel()
    activeAssistantMessageId = null
    voiceController.stopListening()
    voiceController.stopSpeaking()
  }

  fun refreshPowerState() {
    val powerSave = performanceMonitor?.isPowerSaveMode() ?: false
    _uiState.update { it.copy(isSystemPowerSave = powerSave) }
  }

  fun onRefreshPermissions() {
    val items = permissionManager?.refreshAll(_uiState.value.screenShareStatus) ?: return
    val micGranted = items.any { it.category.name == "MICROPHONE" && it.state.name == "GRANTED" }
    _uiState.update { it.copy(permissionCategories = items, hasAudioPermission = micGranted) }
  }

  private fun observeSettings() {
    viewModelScope.launch {
      settingsRepository.observeSettings().collectLatest { settings ->
        val mode =
          runCatching { AppThemeMode.valueOf(settings.themeModeName) }
            .getOrDefault(AppThemeMode.DARK)
        val savedModel =
          runCatching { AiModelOption.valueOf(settings.selectedModelOptionName) }
            .getOrDefault(AiModelOption.GEMINI_4_HYDROGEN)
        notificationEngine?.updateConfig(settings.notificationConfig)
        screenShareSessionManager?.updateConfig(settings.screenIntelligenceConfig)
        performanceMonitor?.schedulePeriodicCacheCleanup(settings.privacyPerformanceConfig.autoCleanupTempFiles)

        _uiState.update { current ->
          val hydratedActivation =
            if (current.activationResult.verifiedAtMs != null) {
              current.activationResult
            } else {
              deriveActivationResultFromStoredSettings(settings, current.hasAudioPermission)
            }
          current.copy(
            themeMode = mode,
            selectedModel = savedModel,
            connectionMode = settings.connectionMode,
            proxyBaseUrl = settings.proxyBaseUrl,
            geminiApiKey = settings.geminiApiKey,
            groqApiKey = settings.groqApiKey,
            hydrogenBackingModelId = settings.hydrogenBackingModelId,
            groqReasoningModelId = settings.groqReasoningModelId,
            imageModelId = settings.imageModelId,
            externalSearchUrl = settings.externalSearchUrl,
            externalSearchApiKey = settings.externalSearchApiKey,
            analyticsSandboxEnabled = settings.analyticsSandboxEnabled,
            composerGlowIntensity = settings.composerGlowIntensity,
            voicePreferences = settings.voicePreferences,
            edgeLightConfig = settings.edgeLightConfig,
            floatingOrbConfig = settings.floatingOrbConfig,
            screenIntelligenceConfig = settings.screenIntelligenceConfig,
            agentBudgetConfig = settings.agentBudgetConfig,
            notificationConfig = settings.notificationConfig,
            privacyPerformanceConfig = settings.privacyPerformanceConfig,
            activationResult = hydratedActivation,
          )
        }
      }
    }
  }

  private fun observePart4Streams() {
    viewModelScope.launch {
      voiceController.audioRmsNormalized.collectLatest { rms ->
        _uiState.update { it.copy(voiceRmsNormalized = rms) }
      }
    }
    screenShareSessionManager?.let { mgr ->
      viewModelScope.launch {
        mgr.status.collectLatest { s ->
          _uiState.update { it.copy(screenShareStatus = s) }
          onRefreshPermissions()
        }
      }
      viewModelScope.launch {
        mgr.frames.collectLatest { f -> _uiState.update { it.copy(capturedScreenFrames = f) } }
      }
      viewModelScope.launch {
        mgr.lastStatusMessage.collectLatest { msg -> _uiState.update { it.copy(screenShareStatusMessage = msg) } }
      }
    }
    longTermMemoryManager?.let { mem ->
      viewModelScope.launch {
        mem.observeMemories().collectLatest { list -> _uiState.update { it.copy(memories = list) } }
      }
    }
    notificationEngine?.let { engine ->
      viewModelScope.launch {
        engine.summaries.collectLatest { list -> _uiState.update { it.copy(notificationSummaries = list) } }
      }
      viewModelScope.launch {
        engine.notificationPulseTick.collectLatest { tick ->
          if (tick > 0L && _uiState.value.edgeLightConfig.notificationAlertPulseEnabled) {
            _uiState.update { it.copy(notificationPulseActive = true) }
            delay(2800L)
            _uiState.update { it.copy(notificationPulseActive = false) }
          }
        }
      }
    }
    agentOrchestrator?.let { orch ->
      viewModelScope.launch {
        orch.activeSteps.collectLatest { steps -> _uiState.update { it.copy(activeAgentSteps = steps) } }
      }
    }
    operationsRepository?.let { repo ->
      viewModelScope.launch {
        repo.observeAgentTasks().collectLatest { logs -> _uiState.update { it.copy(agentTaskHistory = logs) } }
      }
      viewModelScope.launch {
        repo.observeAutomationTasks().collectLatest { autos -> _uiState.update { it.copy(automationHistory = autos) } }
      }
    }
  }

  private fun observePart5Streams() {
    permissionManager?.let { mgr ->
      viewModelScope.launch {
        mgr.categories.collectLatest { list -> _uiState.update { it.copy(permissionCategories = list) } }
      }
    }
  }

  private fun observeAddOnStreams() {
    viewModelScope.launch {
      voiceController.audioMetrics.collectLatest { metrics ->
        _uiState.update { it.copy(voiceAudioMetrics = metrics) }
      }
    }
    viewModelScope.launch {
      voiceController.ttsSpeechEnergy.collectLatest { speechEnergy ->
        if (_uiState.value.voiceState is VoiceState.Speaking) {
          _uiState.update { it.copy(voiceRmsNormalized = speechEnergy) }
        }
      }
    }
    reminderManager?.let { mgr ->
      viewModelScope.launch {
        mgr.observeReminders().collectLatest { list -> _uiState.update { it.copy(reminders = list) } }
      }
    }
    addOnRepository?.let { repo ->
      viewModelScope.launch {
        repo.observeManagedTasks().collectLatest { tasks ->
          _uiState.update { it.copy(managedTasks = tasks) }
        }
      }
      viewModelScope.launch {
        repo.observeDeviceActionLogs().collectLatest { logs ->
          _uiState.update { it.copy(deviceActionLogs = logs) }
        }
      }
    }
    taskStateManager?.let { mgr ->
      viewModelScope.launch {
        mgr.tasks.collectLatest { liveTasks ->
          if (liveTasks.isNotEmpty() && addOnRepository == null) {
            _uiState.update { it.copy(managedTasks = liveTasks) }
          }
        }
      }
    }
  }

  private fun observeConversations() {
    viewModelScope.launch {
      chatRepository.observeConversations().collectLatest { list ->
        _uiState.update { it.copy(conversations = list) }
      }
    }
  }

  private fun observeVoiceState() {
    viewModelScope.launch {
      voiceController.voiceState.collectLatest { vState ->
        _uiState.update { state ->
          val updatedInput =
            if (vState is VoiceState.TranscriptReady) vState.transcript else state.inputText
          val closeVoice = vState is VoiceState.TranscriptReady
          state.copy(
            voiceState = vState,
            inputText = updatedInput,
            isVoiceDialogOpen = if (closeVoice) false else state.isVoiceDialogOpen,
          )
        }
        if (vState is VoiceState.TranscriptReady) {
          val transcript = vState.transcript.trim()
          voiceController.resetState()
          if (transcript.isNotBlank() && (_uiState.value.isGeminiLiveOpen || _uiState.value.privacyPerformanceConfig.continuousVoiceMode)) {
            if (_uiState.value.isGeminiLiveOpen) {
              liveSessionTurnsCount += 1
            }
            onSendMessage()
          }
        }
      }
    }
  }

  private fun observeActiveConversationMessages(conversationId: String) {
    messagesObserverJob?.cancel()
    messagesObserverJob =
      viewModelScope.launch {
        chatRepository.observeMessages(conversationId).collectLatest { domainMessages ->
          val mapped = domainMessages.map(::toUiMessage)
          _uiState.update { state ->
            val streamingId = activeAssistantMessageId
            val currentStreaming =
              if (state.isGenerating && streamingId != null) {
                state.messages.firstOrNull { it.id == streamingId }
              } else {
                null
              }
            val combined =
              if (currentStreaming != null && mapped.none { it.id == streamingId }) {
                mapped + currentStreaming
              } else {
                mapped
              }
            state.copy(messages = combined)
          }
        }
      }

    imageVersionsObserverJob?.cancel()
    part5Repository?.let { repo ->
      imageVersionsObserverJob =
        viewModelScope.launch {
          repo.observeConversationImageVersions(conversationId).collectLatest { records ->
            val grouped = records.groupBy { it.rootMediaId }.mapValues { (_, list) -> list.sortedBy { it.versionNumber } }
            _uiState.update { it.copy(imageVersionsByRoot = grouped) }
          }
        }
    }

    firestoreMessagesJob?.cancel()
    val sync = firestoreSync
    val impl = chatRepository as? ChatRepositoryImpl
    if (sync != null && impl != null && authGateway?.getCurrentSession()?.isSignedIn == true) {
      firestoreMessagesJob =
        viewModelScope.launch {
          sync.observeMessages(conversationId)
            .catch { error ->
              val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
              if (firestoreError == null && error is CancellationException) throw error
              emit(emptyList())
            }
            .collectLatest { remoteMsgs ->
              if (remoteMsgs.isNotEmpty()) {
                impl.mergeRemoteMessages(remoteMsgs)
              }
            }
        }
    }
  }

  fun onClearStatusToast() {
    _uiState.update { it.copy(statusToastMessage = null) }
  }

  fun onInputChanged(newText: String) {
    _uiState.update { it.copy(inputText = newText) }
  }

  fun onSelectSuggestion(prompt: String) {
    _uiState.update { it.copy(inputText = prompt) }
  }

  fun onSelectMode(mode: InputMode) {
    _uiState.update { it.copy(inputMode = mode) }
  }

  fun onToggleModelMenu(open: Boolean) {
    _uiState.update { it.copy(isModelMenuOpen = open) }
  }

  fun onSelectModel(model: AiModelOption) {
    _uiState.update { it.copy(selectedModel = model, isModelMenuOpen = false) }
    persistCurrentSettings()
  }

  fun onToggleAttachmentSheet(open: Boolean) {
    _uiState.update { it.copy(isAttachmentSheetOpen = open) }
  }

  fun onAttachSampleImage() {
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.SampleInlineArtwork).distinct()
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedPhoto(uri: String) {
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.PickedPhotoUri(uri)).take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedPhotos(uris: List<String>) {
    if (uris.isEmpty()) return
    _uiState.update { state ->
      val additions = uris.map { AttachmentPreview.PickedPhotoUri(it) }
      val updated = (state.pendingAttachments + additions).distinct().take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedVideo(uriString: String) {
    val item = buildMediaItem(uriString, MediaAttachmentKind.VIDEO, "video/mp4", "Selected Video")
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.MediaFile(item)).take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedAudio(uriString: String) {
    val item = buildMediaItem(uriString, MediaAttachmentKind.AUDIO, "audio/*", "Selected Audio")
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.MediaFile(item)).take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedDocuments(uris: List<String>) {
    if (uris.isEmpty()) return
    val newFiles =
      uris.take(4).map { uri ->
        buildMediaItem(uri, MediaAttachmentKind.DOCUMENT, "application/octet-stream", "Document")
      }
    _uiState.update { state ->
      val updated =
        (state.pendingAttachments + newFiles.map { AttachmentPreview.MediaFile(it) })
          .distinct()
          .take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun createCameraCaptureTempUri(context: Context): Uri? =
    runCatching {
      val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
      val file = File(dir, "mousomi_cam_${System.currentTimeMillis()}.jpg")
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

  private fun buildMediaItem(
    uriString: String,
    kind: MediaAttachmentKind,
    defaultMime: String,
    fallbackLabel: String,
  ): PendingMediaItem {
    val uri = Uri.parse(uriString)
    val resolvedName =
      tempMediaCacheManager?.resolveDisplayName(uri)
        ?: uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { fallbackLabel }
        ?: fallbackLabel
    val resolvedSize = tempMediaCacheManager?.resolveFileSize(uri) ?: 0L
    return PendingMediaItem(
      id = "media_${UUID.randomUUID()}",
      uriString = uriString,
      displayName = securityValidator.sanitizeFilename(resolvedName),
      mimeType = defaultMime,
      byteSize = resolvedSize,
      kind = kind,
    )
  }

  fun onRemovePendingAttachment(attachment: AttachmentPreview) {
    _uiState.update { state ->
      state.copy(pendingAttachments = state.pendingAttachments - attachment)
    }
  }

  fun onClearPendingAttachment() {
    _uiState.update { it.copy(pendingAttachments = emptyList()) }
  }

  fun onCancelEditImageTarget() {
    _uiState.update { it.copy(activeEditTarget = null) }
  }

  fun onToggleImageToolsSheet(open: Boolean) {
    _uiState.update { it.copy(isImageToolsSheetOpen = open) }
  }

  fun onToggleMoreToolsSheet(open: Boolean) {
    _uiState.update { it.copy(isMoreToolsSheetOpen = open) }
  }

  fun onSelectContextualAction(actionId: String) {
    when (actionId) {
      "open_image_tools" -> {
        _uiState.update { it.copy(isImageToolsSheetOpen = true) }
      }
      "open_more_tools" -> {
        _uiState.update { it.copy(isMoreToolsSheetOpen = true) }
      }
      "open_lens" -> {
        onRequestOpenLens()
      }
      "generate_image", "create_image" -> {
        nextRequestImageGenFlag = true
        _uiState.update {
          it.copy(
            isImageToolsSheetOpen = false,
            inputText = it.inputText.ifBlank { "Generate a high-detail illustration of " },
          )
        }
      }
      "deep_research" -> {
        nextRequestDeepResearchFlag = true
        _uiState.update {
          it.copy(
            isMoreToolsSheetOpen = false,
            inputText = it.inputText.ifBlank { "Deep research: " },
          )
        }
      }
      "web_search" -> {
        nextRequestDeepResearchFlag = true
        _uiState.update {
          it.copy(
            isMoreToolsSheetOpen = false,
            inputText = it.inputText.ifBlank { "Search the web for: " },
          )
        }
      }
      "data_analysis" -> {
        _uiState.update {
          it.copy(
            isMoreToolsSheetOpen = false,
            inputText = "Analyze this dataset and highlight key statistics and chart trends: ",
          )
        }
      }
      "coding_assistant" -> {
        _uiState.update {
          it.copy(
            isMoreToolsSheetOpen = false,
            inputText = "Help me write, debug, or review code for: ",
          )
        }
      }
      "task_mode" -> {
        _uiState.update { it.copy(inputMode = InputMode.TASK, isMoreToolsSheetOpen = false) }
      }
      "live_voice" -> {
        onToggleGeminiLive(true)
      }
      "edit_image" -> {
        ensureEditTargetFromLatestContext()
        _uiState.update { it.copy(isImageToolsSheetOpen = false, inputText = it.inputText.ifBlank { "Edit the image: " }) }
      }
      "analyze_image" -> {
        _uiState.update { it.copy(isImageToolsSheetOpen = false, activeEditTarget = null, inputText = "Analyze this image in detail") }
      }
      "enhance" -> {
        ensureEditTargetFromLatestContext()
        _uiState.update { it.copy(isImageToolsSheetOpen = false, inputText = "Enhance lighting, sharpness, and color balance while preserving the subject") }
      }
      "remove_bg" -> {
        ensureEditTargetFromLatestContext()
        _uiState.update { it.copy(isImageToolsSheetOpen = false, inputText = "Remove the background cleanly and keep the main subject crisp") }
      }
      "change_bg" -> {
        ensureEditTargetFromLatestContext()
        _uiState.update { it.copy(isImageToolsSheetOpen = false, inputText = "Change the background to ") }
      }
      "add_text" -> {
        ensureEditTargetFromLatestContext()
        _uiState.update { it.copy(isImageToolsSheetOpen = false, inputText = "Add clean typographic text saying \"\"") }
      }
      "create_variations" -> {
        ensureEditTargetFromLatestContext()
        nextRequestImageGenFlag = true
        _uiState.update { it.copy(isImageToolsSheetOpen = false, inputText = "Create 3 creative visual variations based on ") }
      }
      "remove_obj" -> {
        ensureEditTargetFromLatestContext()
        _uiState.update { it.copy(isImageToolsSheetOpen = false, inputText = "Remove unwanted background objects cleanly") }
      }
      "draw_region" -> {
        val latestGenerated =
          _uiState.value.messages
            .asReversed()
            .flatMap { it.blocks }
            .filterIsInstance<ResponseBlock.GeneratedImageBlock>()
            .firstOrNull { it.image.status == ImageGenerationStatus.COMPLETED && it.image.filePath.isNotBlank() }
            ?.image
        _uiState.update {
          it.copy(
            isMoreToolsSheetOpen = false,
            isImageToolsSheetOpen = false,
            drawingTargetImage = latestGenerated,
            isDrawingOverlayOpen = true,
          )
        }
      }
    }
  }

  private fun ensureEditTargetFromLatestContext() {
    val state = _uiState.value
    if (state.activeEditTarget != null) return
    val latestGenerated =
      state.messages
        .asReversed()
        .flatMap { it.blocks }
        .filterIsInstance<ResponseBlock.GeneratedImageBlock>()
        .firstOrNull { it.image.status == ImageGenerationStatus.COMPLETED && it.image.filePath.isNotBlank() }
        ?.image
    if (latestGenerated != null) {
      _uiState.update {
        it.copy(
          activeEditTarget =
            ActiveEditImageTarget(
              mediaId = latestGenerated.mediaId,
              rootMediaId = latestGenerated.rootMediaId,
              versionNumber = latestGenerated.versionNumber,
              filePath = latestGenerated.filePath,
              promptHistory = latestGenerated.prompt,
            )
        )
      }
    } else {
      val latestPhoto =
        state.pendingAttachments.filterIsInstance<AttachmentPreview.PickedPhotoUri>().lastOrNull()?.uri
      if (latestPhoto != null) {
        val id = "usr_${UUID.randomUUID()}"
        _uiState.update {
          it.copy(
            activeEditTarget =
              ActiveEditImageTarget(
                mediaId = id,
                rootMediaId = id,
                versionNumber = 1,
                uriString = latestPhoto,
              )
          )
        }
      }
    }
  }

  fun onStartEditGeneratedImage(image: GeneratedImageResult) {
    _uiState.update {
      it.copy(
        activeEditTarget =
          ActiveEditImageTarget(
            mediaId = image.mediaId,
            rootMediaId = image.rootMediaId,
            versionNumber = image.versionNumber,
            filePath = image.filePath,
            promptHistory = image.prompt,
          ),
        inputText = if (it.inputText.isBlank()) "Edit v${image.versionNumber}: " else it.inputText,
      )
    }
  }

  fun onSelectRegionForGeneratedImage(image: GeneratedImageResult) {
    _uiState.update {
      it.copy(
        drawingTargetImage = image,
        isDrawingOverlayOpen = true,
      )
    }
  }

  fun onAskAboutGeneratedImage(image: GeneratedImageResult) {
    if (image.filePath.isBlank()) return
    val uriStr = Uri.fromFile(File(image.filePath)).toString()
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.PickedPhotoUri(uriStr)).distinct().take(4)
      state.copy(
        pendingAttachments = updated,
        activeEditTarget = null,
        inputText = "Explain and analyze what is shown in this image",
      )
    }
  }

  fun onSaveGeneratedImage(image: GeneratedImageResult) {
    if (image.filePath.isBlank()) return
    viewModelScope.launch {
      val res = imageStorageManager?.saveToGallery(image.filePath, image.prompt)
      val toast =
        res?.fold(
          onSuccess = { "Saved image v${image.versionNumber} to Pictures/Mousomi AI" },
          onFailure = { err -> err.message ?: "Unable to save image." },
        ) ?: "Image saved inside app storage: ${image.filePath}"
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onShareGeneratedImage(context: Context, image: GeneratedImageResult) {
    if (image.filePath.isBlank()) return
    runCatching {
      val uri =
        imageStorageManager?.getShareableContentUri(image.filePath)
          ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(image.filePath))
      val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = image.mimeType.ifBlank { "image/png" }
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, image.prompt)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      context.startActivity(Intent.createChooser(intent, "Share Mousomi Image"))
    }.onFailure { err ->
      _uiState.update { it.copy(statusToastMessage = "Unable to share image: ${err.localizedMessage}") }
    }
  }

  fun onRegenerateGeneratedImage(image: GeneratedImageResult) {
    if (_uiState.value.isGenerating) return
    val prompt = image.editInstruction ?: image.prompt
    viewModelScope.launch {
      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = prompt,
        attachments = emptyList(),
        imageGenerationRequested = true,
      )
    }
  }

  fun onSaveGeneratedVideo(video: GeneratedVideoResult) {
    if (video.filePath.isBlank()) return
    viewModelScope.launch {
      val res = videoAndMusicService?.saveVideoToGallery(video.filePath, video.prompt)
      val toast =
        res?.fold(
          onSuccess = { "Saved video to Movies/Mousomi AI" },
          onFailure = { err -> err.message ?: "Unable to save video." },
        ) ?: "Video stored at: ${video.filePath}"
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onShareGeneratedVideo(context: Context, video: GeneratedVideoResult) {
    if (video.filePath.isBlank()) return
    runCatching {
      val uri =
        videoAndMusicService?.getShareableMediaUri(video.filePath)
          ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(video.filePath))
      val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = video.mimeType.ifBlank { "video/mp4" }
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, video.prompt)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      context.startActivity(Intent.createChooser(intent, "Share Mousomi Video"))
    }.onFailure { err ->
      _uiState.update { it.copy(statusToastMessage = "Unable to share video: ${err.localizedMessage}") }
    }
  }

  fun onSaveGeneratedMusic(music: GeneratedMusicResult) {
    if (music.filePath.isBlank()) return
    viewModelScope.launch {
      val res = videoAndMusicService?.saveAudioToMusicFolder(music.filePath, music.prompt, music.mimeType)
      val toast =
        res?.fold(
          onSuccess = { "Saved audio track to Music/Mousomi AI" },
          onFailure = { err -> err.message ?: "Unable to save audio track." },
        ) ?: "Audio stored at: ${music.filePath}"
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onShareGeneratedMusic(context: Context, music: GeneratedMusicResult) {
    if (music.filePath.isBlank()) return
    runCatching {
      val uri =
        videoAndMusicService?.getShareableMediaUri(music.filePath)
          ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(music.filePath))
      val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = music.mimeType.ifBlank { "audio/*" }
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, music.prompt)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      context.startActivity(Intent.createChooser(intent, "Share Mousomi Music"))
    }.onFailure { err ->
      _uiState.update { it.copy(statusToastMessage = "Unable to share music: ${err.localizedMessage}") }
    }
  }

  fun onBeginEditMessage(message: ChatMessage) {
    if (!message.isUser || _uiState.value.isGenerating) return
    _uiState.update {
      it.copy(
        editingMessageId = message.id,
        inputText = message.text,
        pendingAttachments = message.attachments,
      )
    }
  }

  fun onCancelEditing() {
    _uiState.update {
      it.copy(editingMessageId = null, inputText = "", pendingAttachments = emptyList())
    }
  }

  fun onSendMessage() {
    val state = _uiState.value
    val trimmed = state.inputText.trim()
    val attachments = state.pendingAttachments
    val editTarget = state.activeEditTarget
    val requestImgGen = nextRequestImageGenFlag
    val requestDeepResearch = nextRequestDeepResearchFlag
    nextRequestImageGenFlag = false
    nextRequestDeepResearchFlag = false

    if (trimmed.isEmpty() && attachments.isEmpty() && editTarget == null) return

    val editTargetId = state.editingMessageId
    if (editTargetId == null && attachments.isEmpty() && editTarget == null && shouldLaunchMousomiLens(trimmed)) {
      _uiState.update {
        it.copy(
          inputText = "",
          pendingNavigateToLens = true,
          statusToastMessage = "Opening Mousomi Lens live visual scanner…",
        )
      }
      return
    }

    if (editTargetId != null) {
      val editMessage = state.messages.firstOrNull { it.id == editTargetId }
      if (editMessage != null) {
        viewModelScope.launch {
          chatRepository.deleteMessagesFrom(state.activeConversationId, editMessage.timestampMs)
          executePrompt(
            conversationId = state.activeConversationId,
            prompt = trimmed,
            attachments = attachments,
            imageGenerationRequested = requestImgGen,
            deepResearchRequested = requestDeepResearch,
            activeEditTarget = editTarget,
          )
        }
        _uiState.update {
          it.copy(
            editingMessageId = null,
            inputText = "",
            pendingAttachments = emptyList(),
            activeEditTarget = null,
          )
        }
        return
      }
    }

    _uiState.update {
      it.copy(
        inputText = "",
        pendingAttachments = emptyList(),
        activeEditTarget = null,
      )
    }
    viewModelScope.launch {
      executePrompt(
        conversationId = state.activeConversationId,
        prompt = trimmed,
        attachments = attachments,
        imageGenerationRequested = requestImgGen,
        deepResearchRequested = requestDeepResearch,
        activeEditTarget = editTarget,
      )
    }
  }

  fun onRegenerateFromMessage(assistantMessage: ChatMessage) {
    if (_uiState.value.isGenerating) return
    val currentMessages = _uiState.value.messages
    val index = currentMessages.indexOfFirst { it.id == assistantMessage.id }
    if (index <= 0) return

    val precedingUser = currentMessages.subList(0, index).lastOrNull { it.isUser } ?: return
    viewModelScope.launch {
      chatRepository.deleteMessagesFrom(
        conversationId = _uiState.value.activeConversationId,
        fromTimestampMs = assistantMessage.timestampMs,
      )
      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = precedingUser.text,
        attachments = precedingUser.attachments,
        skipSavingUserTurn = true,
      )
    }
  }

  fun onStopGeneration() {
    activeGenerationJob?.cancel()
    activeGenerationJob = null
    val streamingId = activeAssistantMessageId
    val streamingMsg = _uiState.value.messages.firstOrNull { it.id == streamingId }
    val partial = streamingMsg?.text.orEmpty()

    _uiState.update { state ->
      val updated =
        state.messages.map { msg ->
          if (msg.id == streamingId) msg.copy(isStreaming = false) else msg
        }
      state.copy(messages = updated, responseState = AiResponseState.Cancelled)
    }

    if (streamingId != null && partial.isNotBlank()) {
      viewModelScope.launch {
        chatRepository.upsertMessage(
          DomainMessage(
            id = streamingId,
            conversationId = _uiState.value.activeConversationId,
            isUser = false,
            text = partial,
            timestampMs = System.currentTimeMillis(),
            modelUsed = _uiState.value.selectedModel.displayName,
            blocks = streamingMsg?.blocks.orEmpty(),
          )
        )
      }
    }
    activeAssistantMessageId = null
  }

  fun onTriggerEmergencyStop() {
    if (emergencyStopController != null) {
      emergencyStopController.triggerEmergencyStop("Emergency Stop triggered by user.")
    } else {
      onStopGeneration()
      voiceController.stopListening()
      voiceController.stopSpeaking()
      voiceController.resetState()
      onStopScreenShare()
      _uiState.update {
        it.copy(
          isGeminiLiveOpen = false,
          isGeminiLiveMinimized = false,
          isVoiceDialogOpen = false,
          statusToastMessage = "Emergency Stop executed: active voice, generation, and screen sharing stopped.",
        )
      }
    }
    onRefreshPermissions()
  }

  private fun decodeFileToProcessedImage(filePath: String): ProcessedImagePayload? {
    return runCatching {
      val file = File(filePath)
      if (!file.exists()) return null
      val bytes = file.readBytes()
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      ProcessedImagePayload(
        uriString = Uri.fromFile(file).toString(),
        mimeType = if (filePath.endsWith("jpg", true) || filePath.endsWith("jpeg", true)) "image/jpeg" else "image/png",
        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
        width = bounds.outWidth.coerceAtLeast(1),
        height = bounds.outHeight.coerceAtLeast(1),
        byteSize = bytes.size,
      )
    }.getOrNull()
  }

  private suspend fun executePrompt(
    conversationId: String,
    prompt: String,
    attachments: List<AttachmentPreview>,
    skipSavingUserTurn: Boolean = false,
    imageGenerationRequested: Boolean = false,
    deepResearchRequested: Boolean = false,
    activeEditTarget: ActiveEditImageTarget? = null,
    regionOverlayImage: ProcessedImagePayload? = null,
  ) {
    activeGenerationJob?.cancel()
    completedPulseJob?.cancel()

    val historyBefore = chatRepository.getMessagesSnapshot(conversationId)
    val nowMs = System.currentTimeMillis()
    val userText = prompt.ifEmpty { "Please analyze the attached file or media." }
    val pendingMedia =
      attachments.mapNotNull { (it as? AttachmentPreview.MediaFile)?.item }

    if (!skipSavingUserTurn) {
      val userDomain =
        DomainMessage(
          id = "msg_${UUID.randomUUID()}",
          conversationId = conversationId,
          isUser = true,
          text = userText,
          timestampMs = nowMs,
          imageUris =
            attachments.mapNotNull { (it as? AttachmentPreview.PickedPhotoUri)?.uri },
          isSampleArtwork = attachments.any { it is AttachmentPreview.SampleInlineArtwork },
          attachedMediaSummaries = pendingMedia,
        )
      chatRepository.upsertMessage(userDomain)
    }

    val processedImages = mutableListOf<ProcessedImagePayload>()
    for (attachment in attachments) {
      val result =
        when (attachment) {
          AttachmentPreview.SampleInlineArtwork ->
            imageProcessor.processDrawableResource(R.drawable.img_inline_preview_art_1795023054486)
          is AttachmentPreview.PickedPhotoUri ->
            imageProcessor.processUri(attachment.uri)
          is AttachmentPreview.MediaFile -> null
        }
      result?.getOrNull()?.let { processedImages.add(it) }
    }

    if (activeEditTarget != null && processedImages.isEmpty()) {
      val targetPayload =
        activeEditTarget.filePath?.let(::decodeFileToProcessedImage)
          ?: activeEditTarget.uriString?.let { imageProcessor.processUri(it).getOrNull() }
      if (targetPayload != null) {
        processedImages.add(targetPayload)
      }
    }

    val assistantMsgId = "msg_${UUID.randomUUID()}"
    activeAssistantMessageId = assistantMsgId
    var routedModelName = _uiState.value.selectedModel.displayName
    var routedCategoryLabel: String? = null
    var latestUsage: TokenUsage? = null
    var latestBlocks: List<ResponseBlock> = emptyList()

    val placeholderAssistant =
      ChatMessage(
        id = assistantMsgId,
        isUser = false,
        text = "",
        timestampLabel = formatTime(System.currentTimeMillis()),
        timestampMs = nowMs + 1,
        isStreaming = true,
      )

    _uiState.update { state ->
      state.copy(
        messages = state.messages + placeholderAssistant,
        responseState = AiResponseState.Loading,
        isCompletedPulseActive = false,
      )
    }

    val job =
      viewModelScope.launch {
        try {
          aiOrchestrator
            .processMessage(
              OrchestrationRequest(
                conversationId = conversationId,
                userPrompt = userText,
                historyMessages = historyBefore,
                currentTurnImages = processedImages,
                requestedModelId = _uiState.value.selectedModel.modelId,
                isTaskMode = _uiState.value.inputMode == InputMode.TASK,
                pendingMediaItems = pendingMedia,
                deepResearchRequested = deepResearchRequested,
                imageGenerationRequested = imageGenerationRequested,
                activeEditTarget = activeEditTarget,
                regionOverlayImage = regionOverlayImage,
              )
            )
            .collect { event ->
              when (event) {
                is OrchestrationEvent.Routed -> {
                  routedModelName = event.decision.selectedModel.displayName
                  routedCategoryLabel = event.decision.category.label
                  _uiState.update { it.copy(activeRoutingReason = event.decision.reason) }
                }
                is OrchestrationEvent.TitleSuggested -> {
                  chatRepository.updateConversationTitle(conversationId, event.title)
                }
                is OrchestrationEvent.BlocksUpdated -> {
                  latestBlocks = event.blocks
                  _uiState.update { ui ->
                    val updated =
                      ui.messages.map { msg ->
                        if (msg.id == assistantMsgId) msg.copy(blocks = latestBlocks) else msg
                      }
                    ui.copy(messages = updated)
                  }
                }
                is OrchestrationEvent.StateChanged -> {
                  val stateVal = event.state
                  _uiState.update { ui ->
                    val updatedMessages =
                      when (stateVal) {
                        is AiResponseState.Streaming -> {
                          latestUsage = stateVal.tokenUsage ?: latestUsage
                          ui.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                              msg.copy(
                                text = stateVal.partialText,
                                modelUsed = stateVal.modelName,
                                categoryLabel = routedCategoryLabel,
                                tokenUsage = latestUsage,
                                isStreaming = true,
                                blocks = latestBlocks,
                              )
                            } else msg
                          }
                        }
                        is AiResponseState.Completed -> {
                          latestUsage = stateVal.tokenUsage ?: latestUsage
                          ui.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                              msg.copy(
                                text = stateVal.fullText,
                                modelUsed = stateVal.modelName,
                                categoryLabel = routedCategoryLabel,
                                tokenUsage = latestUsage,
                                isStreaming = false,
                                blocks = latestBlocks,
                              )
                            } else msg
                          }
                        }
                        is AiResponseState.Error -> {
                          ui.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                              msg.copy(
                                text = stateVal.message,
                                modelUsed = routedModelName,
                                isError = true,
                                isStreaming = false,
                                blocks = latestBlocks,
                              )
                            } else msg
                          }
                        }
                        else -> ui.messages
                      }
                    ui.copy(messages = updatedMessages, responseState = stateVal)
                  }

                  when (stateVal) {
                    is AiResponseState.Completed -> {
                      val assistantDomain =
                        DomainMessage(
                          id = assistantMsgId,
                          conversationId = conversationId,
                          isUser = false,
                          text = stateVal.fullText,
                          timestampMs = nowMs + 1,
                          modelUsed = stateVal.modelName,
                          categoryLabel = routedCategoryLabel,
                          tokenUsage = stateVal.tokenUsage ?: latestUsage,
                          isError = false,
                          blocks = latestBlocks,
                        )
                      chatRepository.upsertMessage(assistantDomain)
                      activeAssistantMessageId = null

                      // Persist any completed generated images & rich cards into Room v4 tables
                      latestBlocks.forEach { blk ->
                        when (blk) {
                          is ResponseBlock.GeneratedImageBlock -> {
                            if (blk.image.status == ImageGenerationStatus.COMPLETED && blk.image.filePath.isNotBlank()) {
                              part5Repository?.saveImageVersion(conversationId, assistantMsgId, blk.image)
                            }
                          }
                          is ResponseBlock.RichLinkCardBlock -> {
                            part5Repository?.saveRichCards(assistantMsgId, blk.cards)
                          }
                          else -> Unit
                        }
                      }

                      completedPulseJob?.cancel()
                      completedPulseJob =
                        viewModelScope.launch {
                          _uiState.update { it.copy(isCompletedPulseActive = true) }
                          delay(1500L)
                          _uiState.update { it.copy(isCompletedPulseActive = false) }
                        }

                      val voicePrefs = _uiState.value.voicePreferences
                      if (voicePrefs.autoSpeakResponses || _uiState.value.isGeminiLiveOpen) {
                        voiceController.speakResponse(assistantMsgId, stateVal.fullText, voicePrefs)
                      }
                      if (_uiState.value.privacyPerformanceConfig.memoryEnabled && longTermMemoryManager != null) {
                        val updatedHistory = historyBefore + assistantDomain
                        if (updatedHistory.size >= 4) {
                          longTermMemoryManager.summarizeConversationAndPersist(conversationId, updatedHistory)
                        }
                      }
                    }
                    is AiResponseState.Error -> {
                      chatRepository.upsertMessage(
                        DomainMessage(
                          id = assistantMsgId,
                          conversationId = conversationId,
                          isUser = false,
                          text = stateVal.message,
                          timestampMs = nowMs + 1,
                          modelUsed = routedModelName,
                          isError = true,
                          blocks = latestBlocks,
                        )
                      )
                      activeAssistantMessageId = null
                    }
                    else -> Unit
                  }
                }
              }
            }
        } catch (e: CancellationException) {
          throw e
        }
      }
    activeGenerationJob = job
  }

  // --- Part 4 Actions: Edge Light, Floating Orb, Drawing, Screen Intelligence, Memory, Automation ---

  fun onUpdateEdgeLightConfig(config: EdgeLightConfig) {
    _uiState.update { it.copy(edgeLightConfig = config) }
    persistCurrentSettings()
  }

  fun onUpdateFloatingOrbConfig(config: FloatingOrbConfig) {
    _uiState.update { it.copy(floatingOrbConfig = config) }
    persistCurrentSettings()
  }

  fun onOrbPositionCommitted(xFraction: Float, yFraction: Float) {
    val updated =
      _uiState.value.floatingOrbConfig.copy(
        positionXFraction = xFraction,
        positionYFraction = yFraction,
      )
    onUpdateFloatingOrbConfig(updated)
  }

  fun onHandleOrbQuickAction(
    action: OrbQuickAction,
    onRequestScreenShare: () -> Unit = {},
    onNavigateToControlCenter: () -> Unit = {},
  ) {
    when (action) {
      OrbQuickAction.ASK_MOUSOMI -> _uiState.update { it.copy(inputMode = InputMode.CHAT) }
      OrbQuickAction.START_VOICE -> onToggleVoiceDialog(true)
      OrbQuickAction.ANALYZE_SCREEN -> analyzeLatestScreenFrameOrPrompt(onRequestScreenShare)
      OrbQuickAction.START_SCREEN_SHARE -> onRequestScreenShare()
      OrbQuickAction.DRAW_ON_SCREEN -> _uiState.update { it.copy(drawingTargetImage = null, isDrawingOverlayOpen = true) }
      OrbQuickAction.QUICK_NOTE -> _uiState.update { it.copy(isQuickNoteDialogOpen = true) }
      OrbQuickAction.OPEN_FULL_ASSISTANT -> onNavigateToControlCenter()
    }
  }

  fun onHandleOrbPopupAction(
    action: OrbPopupAction,
    onRequestScreenShare: () -> Unit = {},
    onRequestCameraCapture: () -> Unit = {},
    onNavigateToAssistantHub: (Int) -> Unit = {},
  ) {
    when (action) {
      OrbPopupAction.VOICE_ASSISTANT -> onToggleVoiceDialog(true)
      OrbPopupAction.OPEN_CHAT -> _uiState.update { it.copy(inputMode = InputMode.CHAT) }
      OrbPopupAction.QUICK_ASK -> _uiState.update { it.copy(inputMode = InputMode.CHAT, inputText = "") }
      OrbPopupAction.OPEN_CAMERA -> onRequestCameraCapture()
      OrbPopupAction.ANALYZE_IMAGE -> onSelectContextualAction("analyze_image")
      OrbPopupAction.GENERATE_IMAGE -> onSelectContextualAction("generate_image")
      OrbPopupAction.SCREEN_ASSISTANT -> analyzeLatestScreenFrameOrPrompt(onRequestScreenShare)
      OrbPopupAction.DEEP_RESEARCH -> onSelectContextualAction("deep_research")
      OrbPopupAction.DEVICE_ACTIONS -> {
        onSetRequestedHubTab(2)
        onRefreshInstalledApps()
        onNavigateToAssistantHub(2)
      }
      OrbPopupAction.MULTI_TASK_AGENT -> {
        onSetRequestedHubTab(0)
        onNavigateToAssistantHub(0)
      }
      OrbPopupAction.ALARM_AND_REMINDER -> {
        onSetRequestedHubTab(1)
        onNavigateToAssistantHub(1)
      }
      OrbPopupAction.EMERGENCY_STOP -> onTriggerEmergencyStop()
    }
  }

  fun onSetRequestedHubTab(tab: Int) {
    _uiState.update { it.copy(requestedHubTab = tab.coerceIn(0, 2)) }
  }

  fun onRefreshInstalledApps(query: String = _uiState.value.appSearchQuery) {
    val apps =
      installedAppRepository?.let { repo ->
        if (query.isBlank()) repo.getAllInstalledApps() else repo.searchApps(query)
      } ?: emptyList()
    _uiState.update { it.copy(installedApps = apps) }
  }

  fun onAppSearchQueryChange(query: String) {
    _uiState.update { it.copy(appSearchQuery = query) }
    onRefreshInstalledApps(query)
  }

  fun onExecuteDeviceCommand(command: String) {
    viewModelScope.launch {
      val res = deviceActionExecutor?.executeNaturalCommand(command)
      val toast = res?.let { "${it.statusLabel}: ${it.verifiedDetail}" } ?: "Device assistant unavailable."
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onLaunchInstalledApp(appName: String) {
    onExecuteDeviceCommand("Open $appName")
  }

  fun onStartQuickTimer(durationSeconds: Int, label: String) {
    viewModelScope.launch {
      val res = timerManager?.startTimer(durationSeconds, label)
      val toast = res?.second ?: "Timer manager unavailable."
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onCreateReminder(
    type: ReminderItemType,
    title: String,
    hour: Int,
    minute: Int,
    recurrence: AlarmRecurrence,
    tomorrow: Boolean,
  ) {
    viewModelScope.launch {
      val entry = reminderManager?.createExplicitReminder(type, title, hour, minute, recurrence, tomorrow)
      val toast = entry?.verificationNote?.ifBlank { "Scheduled ${entry.title}" } ?: "Reminder manager unavailable."
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onCancelReminder(id: String) {
    viewModelScope.launch {
      reminderManager?.cancelReminder(id)
      _uiState.update { it.copy(statusToastMessage = "Cancelled reminder.") }
    }
  }

  fun onDeleteReminder(id: String) {
    viewModelScope.launch {
      reminderManager?.deleteReminder(id)
    }
  }

  fun onCheckMissedReminders() {
    viewModelScope.launch {
      val missed = reminderManager?.checkMissedReminders().orEmpty()
      val msg =
        if (missed.isEmpty()) "All scheduled alarms & reminders are up to date."
        else "${missed.size} missed reminder(s) detected and marked."
      _uiState.update { it.copy(statusToastMessage = msg) }
    }
  }

  fun onRunCompoundInstruction(instruction: String) {
    viewModelScope.launch {
      val report = taskExecutionEngine?.planAndExecuteMultiTask(instruction, approveSensitiveTasks = false)
      val msg =
        report?.let {
          "Batch planned ${it.tasks.size} step(s): ${it.completedCount} done, ${it.failedCount} failed, ${it.awaitingCount} awaiting approval."
        } ?: "Multi-task engine unavailable."
      _uiState.update { it.copy(statusToastMessage = msg) }
    }
  }

  fun onApproveOrResumeTask(task: ManagedTaskItem) {
    viewModelScope.launch {
      taskExecutionEngine?.resumeOrApproveTask(task)
    }
  }

  fun onPauseTask(task: ManagedTaskItem) {
    viewModelScope.launch {
      taskExecutionEngine?.pauseTask(task)
    }
  }

  fun onRetryTask(task: ManagedTaskItem) {
    viewModelScope.launch {
      taskExecutionEngine?.retryTask(task)
    }
  }

  fun onCancelTask(task: ManagedTaskItem) {
    viewModelScope.launch {
      taskExecutionEngine?.cancelTask(task)
    }
  }

  fun onStopAllTasks() {
    viewModelScope.launch {
      taskExecutionEngine?.stopAllActiveTasks()
      _uiState.update { it.copy(statusToastMessage = "Stopped all active Multi-Task Agent operations.") }
    }
  }

  fun onClearFinishedTasks() {
    viewModelScope.launch {
      taskStateManager?.clearFinished()
    }
  }

  fun onToggleDrawingOverlay(open: Boolean) {
    _uiState.update {
      it.copy(
        isDrawingOverlayOpen = open,
        drawingTargetImage = if (!open) null else it.drawingTargetImage,
      )
    }
  }

  fun onToggleQuickNoteDialog(open: Boolean) {
    _uiState.update { it.copy(isQuickNoteDialogOpen = open) }
  }

  fun onSaveDrawingAnnotation(
    box: NormBoundingBox,
    strokes: List<AnnotationStroke>,
  ) {
    val targetImage = _uiState.value.drawingTargetImage
    val notesFromLabels =
      strokes.mapNotNull { it.textLabel.takeIf { lbl -> lbl.isNotBlank() } }.joinToString(", ")
    _uiState.update {
      it.copy(
        isDrawingOverlayOpen = false,
        drawingTargetImage = null,
        statusToastMessage = "Saved drawing annotation (${strokes.size} strokes).",
      )
    }
    viewModelScope.launch {
      part5Repository?.saveDrawingAnnotation(
        targetMediaId = targetImage?.mediaId,
        strokes = strokes,
        box = box,
        promptNote = notesFromLabels,
      )
    }
  }

  fun onAnalyzeAnnotatedRegion(
    box: NormBoundingBox,
    strokes: List<AnnotationStroke>,
  ) {
    val targetImage = _uiState.value.drawingTargetImage
    _uiState.update { it.copy(isDrawingOverlayOpen = false, drawingTargetImage = null) }
    val sourcePath = targetImage?.filePath ?: screenShareSessionManager?.getLatestFrame()?.filePath
    val notesFromLabels =
      strokes.mapNotNull { it.textLabel.takeIf { lbl -> lbl.isNotBlank() } }.joinToString(", ")

    viewModelScope.launch {
      part5Repository?.saveDrawingAnnotation(
        targetMediaId = targetImage?.mediaId,
        strokes = strokes,
        box = box,
        promptNote = notesFromLabels,
      )

      val cropUriString: String? =
        if (sourcePath != null && tempMediaCacheManager != null) {
          val raw = BitmapFactory.decodeFile(sourcePath)
          val cropped = raw?.let { RegionCropper.cropBitmapToNormBox(it, box) }
          raw?.recycle()
          if (cropped != null) {
            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            cropped.recycle()
            val savedFile = tempMediaCacheManager.saveTempJpeg(baos.toByteArray(), prefix = "annotated_crop_")
            Uri.fromFile(savedFile).toString()
          } else null
        } else null

      val prompt =
        buildString {
          append(if (targetImage != null) "Analyze this selected region of the image" else "Analyze this annotated screen region")
          if (notesFromLabels.isNotBlank()) {
            append(" (user annotations: $notesFromLabels)")
          }
          append(
            " at normalized area [left=${"%.2f".format(box.left)}, top=${"%.2f".format(box.top)}, right=${"%.2f".format(box.right)}, bottom=${"%.2f".format(box.bottom)}]. Explain clearly what is shown."
          )
        }

      val attachments =
        if (cropUriString != null) listOf(AttachmentPreview.PickedPhotoUri(cropUriString))
        else listOf(AttachmentPreview.SampleInlineArtwork)

      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = prompt,
        attachments = attachments,
      )
    }
  }

  fun analyzeLatestScreenFrameOrPrompt(onRequestScreenShare: () -> Unit = {}) {
    val latest = screenShareSessionManager?.getLatestFrame()
    if (latest == null) {
      onRequestScreenShare()
      return
    }
    screenShareSessionManager?.markAnalyzing()
    val uriString = Uri.fromFile(File(latest.filePath)).toString()
    viewModelScope.launch {
      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = "Analyze what is currently visible on my shared screen and provide concise, helpful insights.",
        attachments = listOf(AttachmentPreview.PickedPhotoUri(uriString)),
      )
      screenShareSessionManager?.markSessionActive()
    }
  }

  fun onMarkScreenShareRequestingConsent() {
    screenShareSessionManager?.markRequestingConsent()
  }

  fun onStopScreenShare() {
    screenShareSessionManager?.stopSession()
  }

  fun onUpdateScreenIntelligenceConfig(config: ScreenIntelligenceConfig) {
    screenShareSessionManager?.updateConfig(config)
    _uiState.update { it.copy(screenIntelligenceConfig = config) }
    persistCurrentSettings()
  }

  fun onUpdateAgentBudgetConfig(config: AgentBudgetConfig) {
    _uiState.update { it.copy(agentBudgetConfig = config) }
    persistCurrentSettings()
  }

  fun onSaveMemoryItem(category: MemoryCategory, content: String, importance: Float = 0.7f) {
    viewModelScope.launch {
      val encrypt = _uiState.value.privacyPerformanceConfig.encryptSensitiveMemory
      val outcome =
        longTermMemoryManager?.saveMemory(
          category = category,
          rawContent = content,
          importance = importance,
          encryptAtRest = encrypt,
        )
      val status =
        when (outcome) {
          is MemoryWriteOutcome.Saved ->
            if (outcome.mergedDuplicate) "Merged with similar existing memory." else "Saved to Memory Center."
          is MemoryWriteOutcome.BlockedSensitive -> outcome.reason
          null -> "Memory manager unavailable."
        }
      _uiState.update { it.copy(memoryStatusMessage = status) }
    }
  }

  fun onDeleteMemoryItem(id: String) {
    viewModelScope.launch { longTermMemoryManager?.deleteMemory(id) }
  }

  fun onClearAllMemories() {
    viewModelScope.launch {
      longTermMemoryManager?.clearAllMemories()
      _uiState.update { it.copy(memoryStatusMessage = "Cleared all stored memories.") }
    }
  }

  fun onUpdateNotificationConfig(config: NotificationIntelligenceConfig) {
    notificationEngine?.updateConfig(config)
    _uiState.update { it.copy(notificationConfig = config) }
    persistCurrentSettings()
  }

  fun onDismissNotificationSummary(id: String) {
    notificationEngine?.dismissSummary(id)
  }

  fun onRunAutomationWorkflow(
    workflowType: AutomationWorkflowType,
    title: String,
    targetArg: String = "",
    triggerSource: String = "Manual",
  ) {
    viewModelScope.launch {
      automationEngine?.createPendingOrExecute(
        workflowType = workflowType,
        title = title,
        targetArg = targetArg,
        triggerSource = triggerSource,
        confirmedByUser = false,
      )
    }
  }

  fun onConfirmAutomationTask(task: AutomationTaskRecord) {
    viewModelScope.launch { automationEngine?.executeConfirmed(task) }
  }

  fun onCancelAutomationTask(task: AutomationTaskRecord) {
    viewModelScope.launch { automationEngine?.cancelPendingTask(task) }
  }

  fun onUpdatePrivacyPerformanceConfig(config: PrivacyAndPerformanceConfig) {
    performanceMonitor?.schedulePeriodicCacheCleanup(config.autoCleanupTempFiles)
    _uiState.update { it.copy(privacyPerformanceConfig = config) }
    persistCurrentSettings()
  }

  // --- Navigation, Voice & Settings Methods ---

  fun onSelectConversation(conversationId: String) {
    activeGenerationJob?.cancel()
    activeAssistantMessageId = null
    _uiState.update {
      it.copy(
        activeConversationId = conversationId,
        responseState = AiResponseState.Idle,
        editingMessageId = null,
        pendingAttachments = emptyList(),
        activeEditTarget = null,
      )
    }
    observeActiveConversationMessages(conversationId)
  }

  fun onDeleteConversation(conversationId: String) {
    viewModelScope.launch {
      chatRepository.deleteConversation(conversationId)
      if (_uiState.value.activeConversationId == conversationId) {
        onNewChat()
      }
    }
  }

  fun onLoadSampleConversation() {
    val convId = "conv_${UUID.randomUUID()}"
    val now = System.currentTimeMillis()
    viewModelScope.launch {
      chatRepository.ensureConversation(convId, "Cutout Phone Wallpaper")
      chatRepository.upsertMessage(
        DomainMessage(
          id = "msg_${UUID.randomUUID()}",
          conversationId = convId,
          isUser = true,
          text = "Make a cutout phone wallpaper with glowing midnight blue glass prism vibes",
          timestampMs = now,
          isSampleArtwork = false,
        )
      )
      chatRepository.upsertMessage(
        DomainMessage(
          id = "msg_${UUID.randomUUID()}",
          conversationId = convId,
          isUser = false,
          text =
            "### Prism Cutout Wallpaper Preview\nHere is a sample multimodal preview bubble. Configure your **Gemini** or **Groq** API key (or an HTTPS proxy) in **Settings** to analyze your own photos, generate & edit images, or run live research.",
          timestampMs = now + 1,
          isSampleArtwork = true,
          modelUsed = "Gemini 4 Hydrogen",
        )
      )
      onSelectConversation(convId)
    }
  }

  fun onNewChat() {
    activeGenerationJob?.cancel()
    activeAssistantMessageId = null
    val newId = "conv_${UUID.randomUUID()}"
    _uiState.update {
      it.copy(
        activeConversationId = newId,
        messages = emptyList(),
        inputText = "",
        editingMessageId = null,
        pendingAttachments = emptyList(),
        activeEditTarget = null,
        responseState = AiResponseState.Idle,
        activeRoutingReason = null,
      )
    }
    observeActiveConversationMessages(newId)
  }

  fun onToggleProfileDialog(open: Boolean) {
    _uiState.update { it.copy(isProfileDialogOpen = open) }
  }

  fun onRequestOpenLens() {
    _uiState.update {
      it.copy(
        isAttachmentSheetOpen = false,
        isMoreToolsSheetOpen = false,
        isImageToolsSheetOpen = false,
        pendingNavigateToLens = true,
      )
    }
  }

  fun onConsumeNavigateToLens() {
    _uiState.update { it.copy(pendingNavigateToLens = false) }
  }

  fun onImportLensAnalysisToChat(
    result: LensAnalysisResult,
    followUpTurns: List<LensFollowUpTurn>,
  ) {
    val convId = _uiState.value.activeConversationId
    val now = System.currentTimeMillis()
    val captureUri =
      result.capturedFilePath
        .takeIf { it.isNotBlank() }
        ?.let { Uri.fromFile(File(it)).toString() }
    val userPrompt =
      "Analyzed with Mousomi Lens (${result.mode.label} mode): ${result.identifiedName}"
    val assistantSummary =
      buildString {
        append(result.toChatSummaryMarkdown())
        if (followUpTurns.isNotEmpty()) {
          appendLine()
          appendLine()
          appendLine("### Lens Follow-Up Q&A")
          followUpTurns.forEach { turn ->
            appendLine("**Q:** ${turn.question}")
            appendLine(turn.answerMarkdown)
            appendLine()
          }
        }
      }.trim()
    val blocks =
      buildList {
        if (result.verifiedSources.isNotEmpty()) {
          add(ResponseBlock.SourceCitations(result.verifiedSources))
        }
      }

    viewModelScope.launch {
      chatRepository.ensureConversation(convId, "Lens: ${result.identifiedName}".take(42))
      chatRepository.upsertMessage(
        DomainMessage(
          id = "msg_${UUID.randomUUID()}",
          conversationId = convId,
          isUser = true,
          text = userPrompt,
          timestampMs = now,
          imageUris = listOfNotNull(captureUri),
        )
      )
      chatRepository.upsertMessage(
        DomainMessage(
          id = "msg_${UUID.randomUUID()}",
          conversationId = convId,
          isUser = false,
          text = assistantSummary,
          timestampMs = now + 1,
          modelUsed = result.modelUsed.ifBlank { "Mousomi Lens Vision" },
          categoryLabel = "Mousomi Lens",
          blocks = blocks,
        )
      )
      _uiState.update { it.copy(statusToastMessage = "Lens analysis added to Chat") }
    }
  }

  private fun shouldLaunchMousomiLens(prompt: String): Boolean {
    val lower = prompt.trim().lowercase(Locale.US)
    if (lower.length > 90) return false
    return lower == "open lens" ||
      lower == "open mousomi lens" ||
      lower == "start lens" ||
      lower == "live lens" ||
      lower == "launch lens" ||
      lower.contains("open lens") ||
      lower.contains("mousomi lens") ||
      lower.contains("open the camera and analyze") ||
      lower.contains("open camera and analyze") ||
      lower.contains("open camera to scan") ||
      lower.contains("start visual scan")
  }

  fun onToggleGeminiLive(open: Boolean) {
    if (open) {
      liveSessionStartedAtMs = System.currentTimeMillis()
      liveSessionTurnsCount = 0
      voiceController.playActivationChime(_uiState.value.voicePreferences)
      if (_uiState.value.hasAudioPermission) {
        voiceController.interruptSpeakingAndListen(_uiState.value.voicePreferences.preferredLanguage)
      }
    } else {
      val startedAt = liveSessionStartedAtMs
      if (startedAt != null) {
        val convId = _uiState.value.activeConversationId
        val prefs = _uiState.value.voicePreferences
        val turns = liveSessionTurnsCount
        liveSessionStartedAtMs = null
        viewModelScope.launch {
          part5Repository?.recordVoiceSession(
            sessionId = "voice_${UUID.randomUUID()}",
            conversationId = convId,
            voiceGender = prefs.voiceGender,
            languageTag = prefs.preferredLanguage.localeTag,
            turnsCount = turns,
            startedAtMs = startedAt,
            endedAtMs = System.currentTimeMillis(),
          )
        }
      }
      voiceController.stopListening()
      voiceController.stopSpeaking()
    }
    _uiState.update {
      it.copy(
        isGeminiLiveOpen = open,
        isGeminiLiveMinimized = if (!open) false else false,
      )
    }
  }

  fun onToggleGeminiLiveMinimized(minimized: Boolean) {
    _uiState.update {
      it.copy(
        isGeminiLiveMinimized = if (it.isGeminiLiveOpen) minimized else false,
      )
    }
  }

  fun onSendLiveFollowUp(promptText: String) {
    val clean = promptText.trim()
    if (clean.isEmpty()) return
    liveSessionTurnsCount += 1
    viewModelScope.launch {
      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = clean,
        attachments = _uiState.value.pendingAttachments,
      )
    }
  }

  fun onToggleVoiceDialog(open: Boolean) {
    if (!open) {
      voiceController.stopListening()
    }
    _uiState.update { it.copy(isVoiceDialogOpen = open) }
  }

  fun onAudioPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(hasAudioPermission = granted, isVoiceDialogOpen = true) }
    if (granted) {
      voiceController.playActivationChime(_uiState.value.voicePreferences)
      voiceController.interruptSpeakingAndListen(_uiState.value.voicePreferences.preferredLanguage)
    }
    onRefreshPermissions()
  }

  fun onStartVoiceListening() {
    voiceController.playActivationChime(_uiState.value.voicePreferences)
    if (_uiState.value.privacyPerformanceConfig.allowVoiceInterruption) {
      voiceController.interruptSpeakingAndListen(_uiState.value.voicePreferences.preferredLanguage)
    } else {
      voiceController.startListening(_uiState.value.voicePreferences.preferredLanguage)
    }
  }

  fun onStopVoiceListening() {
    voiceController.stopListening()
  }

  fun onSpeakMessage(message: ChatMessage) {
    voiceController.speakResponse(
      messageId = message.id,
      text = message.text,
      preferences = _uiState.value.voicePreferences,
    )
  }

  fun onStopSpeaking() {
    voiceController.stopSpeaking()
  }

  fun onUpdateThemeMode(mode: AppThemeMode) {
    _uiState.update { it.copy(themeMode = mode) }
    persistCurrentSettings()
  }

  fun onUpdateGeminiApiKey(key: String) {
    _uiState.update { it.copy(geminiApiKey = key, settingsErrorMessage = null) }
  }

  fun onUpdateGroqApiKey(key: String) {
    _uiState.update { it.copy(groqApiKey = key, settingsErrorMessage = null) }
  }

  fun onUpdateConnectionMode(mode: ConnectionMode) {
    _uiState.update { it.copy(connectionMode = mode, settingsErrorMessage = null) }
  }

  fun onUpdateProxyBaseUrl(url: String) {
    _uiState.update { it.copy(proxyBaseUrl = url, settingsErrorMessage = null) }
  }

  fun onUpdateHydrogenBackingModelId(id: String) {
    _uiState.update { it.copy(hydrogenBackingModelId = id, settingsErrorMessage = null) }
  }

  fun onUpdateGroqReasoningModelId(id: String) {
    _uiState.update { it.copy(groqReasoningModelId = id, settingsErrorMessage = null) }
  }

  fun onUpdateImageModelId(id: String) {
    _uiState.update { it.copy(imageModelId = id, settingsErrorMessage = null) }
  }

  fun onUpdateExternalSearchUrl(url: String) {
    _uiState.update { it.copy(externalSearchUrl = url, settingsErrorMessage = null) }
  }

  fun onUpdateExternalSearchApiKey(key: String) {
    _uiState.update { it.copy(externalSearchApiKey = key, settingsErrorMessage = null) }
  }

  fun onUpdateAnalyticsSandboxEnabled(enabled: Boolean) {
    _uiState.update { it.copy(analyticsSandboxEnabled = enabled) }
  }

  fun onUpdateComposerGlowIntensity(intensity: Float) {
    _uiState.update { it.copy(composerGlowIntensity = intensity.coerceIn(0.25f, 1.0f)) }
  }

  fun onClearTempMediaCache() {
    tempMediaCacheManager?.cleanupAllTempFiles()
  }

  fun onUpdatePreferredLanguage(language: ResponseLanguage) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(preferredLanguage = language))
    }
  }

  fun onUpdateVoiceGender(gender: VoiceGender) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(voiceGender = gender))
    }
    persistCurrentSettings()
  }

  fun onUpdateActivationSound(enabled: Boolean) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(activationSoundEnabled = enabled))
    }
  }

  fun onUpdateAutoSpeak(enabled: Boolean) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(autoSpeakResponses = enabled))
    }
  }

  fun onUpdateSpeechRate(rate: Float) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(speechRate = rate))
    }
  }

  fun onSaveSettings(): Boolean {
    val state = _uiState.value
    if (state.connectionMode == ConnectionMode.BACKEND_PROXY) {
      val validated = securityValidator.validateHttpsUrl(state.proxyBaseUrl)
      if (validated.isFailure) {
        _uiState.update {
          it.copy(
            settingsErrorMessage =
              validated.exceptionOrNull()?.message ?: "Proxy URL must use HTTPS."
          )
        }
        return false
      }
    }
    if (state.externalSearchUrl.isNotBlank()) {
      val validatedSearch = securityValidator.validateHttpsUrl(state.externalSearchUrl)
      if (validatedSearch.isFailure) {
        _uiState.update {
          it.copy(
            settingsErrorMessage =
              validatedSearch.exceptionOrNull()?.message ?: "External Search URL must use HTTPS."
          )
        }
        return false
      }
    }
    persistCurrentSettings()
    return true
  }

  // --- One-Tap API Activation Flows ---

  fun onOneTapApiKeyChanged(key: String) {
    _uiState.update { it.copy(oneTapApiKeyInput = key, settingsErrorMessage = null) }
  }

  fun onActivateOneTapApiKey(rawKey: String = _uiState.value.oneTapApiKeyInput) {
    val service = oneTapApiActivationService
    if (service == null) {
      _uiState.update {
        it.copy(
          activationResult =
            OneTapActivationResult(
              status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
              statusMessage = "Activation service is initializing. Please try again.",
            )
        )
      }
      return
    }
    if (_uiState.value.isActivatingApi) return
    val currentSettings = buildCurrentAppSettingsSnapshot()
    val hasMic = _uiState.value.hasAudioPermission
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isActivatingApi = true,
          activationResult =
            it.activationResult.copy(
              status = ActivationHealthStatus.VALIDATING,
              statusMessage = "Detecting provider and verifying API connection…",
            ),
        )
      }
      val outcome = service.activateWithSingleKey(rawKey, currentSettings, hasMic)
      val saved = outcome.updatedSettings
      if (saved != null) {
        settingsRepository.saveSettings(saved)
      }
      _uiState.update { state ->
        state.copy(
          isActivatingApi = false,
          activationResult = outcome.result,
          oneTapApiKeyInput = if (saved != null) "" else state.oneTapApiKeyInput,
          statusToastMessage = outcome.result.statusMessage,
        )
      }
    }
  }

  fun onVerifyCurrentConnection() {
    val service = oneTapApiActivationService ?: return
    if (_uiState.value.isActivatingApi) return
    val currentSettings = buildCurrentAppSettingsSnapshot()
    val hasMic = _uiState.value.hasAudioPermission
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isActivatingApi = true,
          activationResult =
            it.activationResult.copy(
              status = ActivationHealthStatus.VALIDATING,
              statusMessage = "Verifying active provider connection…",
            ),
        )
      }
      val outcome = service.verifyCurrentConnection(currentSettings, hasMic)
      val saved = outcome.updatedSettings
      if (saved != null) {
        settingsRepository.saveSettings(saved)
      }
      _uiState.update {
        it.copy(
          isActivatingApi = false,
          activationResult = outcome.result,
          statusToastMessage = outcome.result.statusMessage,
        )
      }
    }
  }

  fun onDeactivateApiCredentials() {
    val current = buildCurrentAppSettingsSnapshot()
    val cleared =
      oneTapApiActivationService?.buildDeactivatedSettings(current)
        ?: current.copy(geminiApiKey = "", groqApiKey = "", externalSearchApiKey = "")
    viewModelScope.launch {
      settingsRepository.saveSettings(cleared)
      _uiState.update {
        it.copy(
          geminiApiKey = "",
          groqApiKey = "",
          externalSearchApiKey = "",
          oneTapApiKeyInput = "",
          activationResult =
            OneTapActivationResult(
              status = ActivationHealthStatus.UNCONFIGURED,
              detectedProvider = DetectedProviderKind.UNKNOWN,
              statusMessage = "API credentials cleared. Enter an API key to activate Mousomi AI.",
            ),
          statusToastMessage = "Cleared API credentials safely.",
        )
      }
    }
  }

  private fun deriveActivationResultFromStoredSettings(
    settings: AppSettings,
    hasMic: Boolean,
  ): OneTapActivationResult {
    val mask: (String) -> String = { raw ->
      val clean = raw.trim()
      if (clean.length <= 8) "••••" else "${clean.take(4)}••••${clean.takeLast(4)}"
    }
    return when {
      settings.connectionMode == ConnectionMode.BACKEND_PROXY && settings.proxyBaseUrl.isNotBlank() ->
        OneTapActivationResult(
          status = ActivationHealthStatus.PARTIALLY_ACTIVE,
          detectedProvider = DetectedProviderKind.PROXY,
          statusMessage = "HTTPS backend proxy configured. Tap Verify Connection to check server health.",
          capabilities =
            listOf(
              CapabilityActivationBadge("chat", "AI Chat", true, "Configured"),
              CapabilityActivationBadge("voice", "Voice & Live", hasMic, if (hasMic) "Mic ready" else "Mic permission needed"),
            ),
        )
      settings.geminiApiKey.isNotBlank() ->
        OneTapActivationResult(
          status =
            if (hasMic) ActivationHealthStatus.ACTIVE else ActivationHealthStatus.PARTIALLY_ACTIVE,
          detectedProvider = DetectedProviderKind.GEMINI,
          maskedKeyHint = mask(settings.geminiApiKey),
          statusMessage =
            if (hasMic) "Gemini API key configured (${mask(settings.geminiApiKey)})."
            else "Gemini key saved (${mask(settings.geminiApiKey)}). Grant mic permission for live voice.",
          capabilities =
            listOf(
              CapabilityActivationBadge("chat", "AI Chat", true, "Saved key ready"),
              CapabilityActivationBadge("vision", "Vision & Photos", true, "Saved key ready"),
              CapabilityActivationBadge("image_gen", "Image Gen & Edit", true, "Saved key ready"),
              CapabilityActivationBadge("research", "Deep Research", true, "Saved key ready"),
              CapabilityActivationBadge("voice", "Voice & Live", hasMic, if (hasMic) "Mic ready" else "Grant mic when needed"),
            ),
        )
      settings.groqApiKey.isNotBlank() ->
        OneTapActivationResult(
          status = ActivationHealthStatus.PARTIALLY_ACTIVE,
          detectedProvider = DetectedProviderKind.GROQ,
          maskedKeyHint = mask(settings.groqApiKey),
          statusMessage = "Groq key saved (${mask(settings.groqApiKey)}). Add a Gemini key for vision & image tools.",
          capabilities =
            listOf(
              CapabilityActivationBadge("chat", "AI Chat", true, "Saved key ready"),
              CapabilityActivationBadge("reasoning", "Reasoning & Code", true, "Saved key ready"),
              CapabilityActivationBadge("vision", "Vision & Photos", false, "Requires Gemini key"),
              CapabilityActivationBadge("image_gen", "Image Gen & Edit", false, "Requires Gemini key"),
              CapabilityActivationBadge("voice", "Voice & Live", hasMic, if (hasMic) "Mic ready" else "Grant mic when needed"),
            ),
        )
      firebaseAiLogicService?.isAvailable() == true ->
        OneTapActivationResult(
          status = ActivationHealthStatus.PARTIALLY_ACTIVE,
          detectedProvider = DetectedProviderKind.FIREBASE_AI_LOGIC,
          statusMessage =
            "Firebase AI Logic + App Check active (no user API key needed for Gemini 3.8 Flash chat, vision, audio & Search). Note: Nano Banana image gen, Veo video, and Lyria music require Firebase Blaze billing or a paid Gemini API key.",
          capabilities =
            listOf(
              CapabilityActivationBadge("chat", "AI Chat", true, "Firebase AI Logic"),
              CapabilityActivationBadge("reasoning", "Reasoning & Code", true, "Firebase AI Logic"),
              CapabilityActivationBadge("vision", "Vision & Photos", true, "Firebase AI Logic"),
              CapabilityActivationBadge("research", "Search & Maps", true, "Search grounding ready"),
              CapabilityActivationBadge("image_gen", "Image Gen & Edit", true, "Blaze or paid key"),
              CapabilityActivationBadge("video_music", "Veo Video & Music", false, "Requires paid Gemini API billing"),
              CapabilityActivationBadge("voice", "Voice & Live", hasMic, if (hasMic) "Mic ready" else "Grant mic when needed"),
            ),
        )
      else -> OneTapActivationResult()
    }
  }

  private fun buildCurrentAppSettingsSnapshot(): AppSettings {
    val state = _uiState.value
    return AppSettings(
      connectionMode = state.connectionMode,
      proxyBaseUrl = state.proxyBaseUrl,
      geminiApiKey = state.geminiApiKey,
      groqApiKey = state.groqApiKey,
      themeModeName = state.themeMode.name,
      voicePreferences = state.voicePreferences,
      selectedModelOptionName = state.selectedModel.name,
      hydrogenBackingModelId = state.hydrogenBackingModelId,
      groqReasoningModelId = state.groqReasoningModelId,
      imageModelId = state.imageModelId,
      externalSearchUrl = state.externalSearchUrl,
      externalSearchApiKey = state.externalSearchApiKey,
      analyticsSandboxEnabled = state.analyticsSandboxEnabled,
      composerGlowIntensity = state.composerGlowIntensity,
      edgeLightConfig = state.edgeLightConfig,
      floatingOrbConfig = state.floatingOrbConfig,
      screenIntelligenceConfig = state.screenIntelligenceConfig,
      agentBudgetConfig = state.agentBudgetConfig,
      notificationConfig = state.notificationConfig,
      privacyPerformanceConfig = state.privacyPerformanceConfig,
    )
  }

  private fun persistCurrentSettings() {
    val state = _uiState.value
    viewModelScope.launch {
      settingsRepository.saveSettings(
        AppSettings(
          connectionMode = state.connectionMode,
          proxyBaseUrl = state.proxyBaseUrl,
          geminiApiKey = state.geminiApiKey,
          groqApiKey = state.groqApiKey,
          themeModeName = state.themeMode.name,
          voicePreferences = state.voicePreferences,
          selectedModelOptionName = state.selectedModel.name,
          hydrogenBackingModelId = state.hydrogenBackingModelId,
          groqReasoningModelId = state.groqReasoningModelId,
          imageModelId = state.imageModelId,
          externalSearchUrl = state.externalSearchUrl,
          externalSearchApiKey = state.externalSearchApiKey,
          analyticsSandboxEnabled = state.analyticsSandboxEnabled,
          composerGlowIntensity = state.composerGlowIntensity,
          edgeLightConfig = state.edgeLightConfig,
          floatingOrbConfig = state.floatingOrbConfig,
          screenIntelligenceConfig = state.screenIntelligenceConfig,
          agentBudgetConfig = state.agentBudgetConfig,
          notificationConfig = state.notificationConfig,
          privacyPerformanceConfig = state.privacyPerformanceConfig,
        )
      )
    }
  }

  private fun toUiMessage(domain: DomainMessage): ChatMessage {
    val attachments = buildList {
      if (domain.isSampleArtwork) add(AttachmentPreview.SampleInlineArtwork)
      domain.imageUris.forEach { add(AttachmentPreview.PickedPhotoUri(it)) }
      domain.attachedMediaSummaries.forEach { add(AttachmentPreview.MediaFile(it)) }
    }
    return ChatMessage(
      id = domain.id,
      isUser = domain.isUser,
      text = domain.text,
      timestampLabel = formatTime(domain.timestampMs),
      timestampMs = domain.timestampMs,
      attachments = attachments,
      modelUsed = domain.modelUsed,
      categoryLabel = domain.categoryLabel,
      tokenUsage = domain.tokenUsage,
      isError = domain.isError,
      isStreaming = false,
      blocks = domain.blocks,
    )
  }

  private fun formatTime(timestampMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMs))
  }

  override fun onCleared() {
    super.onCleared()
    voiceController.release()
    tempMediaCacheManager?.cleanupAllTempFiles()
  }
}
