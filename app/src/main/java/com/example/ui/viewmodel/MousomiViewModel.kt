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
import com.example.core.accessibility.AccessibilityActionResult
import com.example.core.accessibility.AccessibilityAgentController
import com.example.core.agent.AgentOrchestrator
import com.example.core.agent.AgentTaskPlanner
import com.example.core.agent.PlannedAgentOutcome
import com.example.core.device.CalendarReminderService
import com.example.core.voice.HotwordCapabilityManager
import com.example.domain.memory.PreferenceConfirmationManager
import com.example.domain.memory.PreferenceDiscoveryEngine
import com.example.core.alarms.ReminderManager
import com.example.core.alarms.TimerManager
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.automation.ControlledAutomationEngine
import com.example.core.device.AgentCommandIntent
import com.example.core.device.AssistantAgentCommandRouter
import com.example.core.device.ContactResolver
import com.example.core.device.DeviceActionExecutor
import com.example.core.device.InstalledAppRepository
import com.example.core.device.PendingPhoneAction
import com.example.core.device.PendingSocialAction
import com.example.core.device.PhoneActionController
import com.example.core.device.ResolvedContact
import com.example.core.device.SocialIntegrationManager
import com.example.core.image.ImageProcessor
import com.example.core.image.ImageStudioFacade
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.performance.SmartPerformanceMonitor
import com.example.core.permissions.EmergencyStopController
import com.example.core.permissions.PermissionManager
import com.example.core.screen.AttachedVisualItem
import com.example.core.screen.CircleSelectionProcessor
import com.example.core.screen.CropAspectPreset
import com.example.core.screen.CropHandle
import com.example.core.screen.DetectedRegionShape
import com.example.core.screen.FreehandPathProcessor
import com.example.core.screen.ImageAttachmentManager
import com.example.core.screen.ImageEditingController
import com.example.core.screen.RectangleSelectionProcessor
import com.example.core.screen.RegionCropper
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenRecordingPhase
import com.example.core.screen.ScreenSelectionManager
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.screen.ScreenshotCaptureManager
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionGeometryResult
import com.example.core.screen.SelectionToolMode
import com.example.core.screen.SmartCropEngine
import com.example.core.screen.VisualQueryProcessor
import com.example.core.security.SecurityValidator
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.DictationTranscriptMerger
import com.example.core.voice.GeminiLiveVoiceEngine
import com.example.core.voice.LiveConnectionState
import com.example.core.voice.LiveSessionPhase
import com.example.core.voice.LiveVoiceControlCommand
import com.example.core.voice.LiveVoiceSessionManager
import com.example.core.voice.LiveVoiceSessionStatus
import com.example.core.voice.OrbReactiveFrame
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceOptionInfo
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
import com.example.domain.model.BackgroundReplaceStyle
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DetectedProviderKind
import com.example.domain.model.DomainMessage
import com.example.domain.model.DrawingTool
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.GeneratedMusicResult
import com.example.domain.model.GeneratedVideoResult
import com.example.domain.model.ImageAdjustmentParams
import com.example.domain.model.ImageEditFeatureType
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.ImageProcessingSource
import com.example.domain.model.LensAnalysisResult
import com.example.domain.model.LensFollowUpTurn
import com.example.domain.model.LiveDiscoveredModel
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.MemoryCategory
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.OneTapActivationResult
import com.example.domain.model.OperatingMode
import com.example.domain.model.OrbPopupAction
import com.example.domain.model.OrbQuickAction
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.PosterDesignSpec
import com.example.domain.model.PrivacyAndPerformanceConfig
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.ProviderTestReport
import com.example.domain.model.ReminderItemType
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.ScreenIntelligenceConfig
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.TokenUsage
import com.example.domain.model.VerifiedModelCatalog
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
  private val screenSelectionManager: ScreenSelectionManager? = null,
  private val selectionGeometryEngine: SelectionGeometryEngine? = null,
  private val imageAttachmentManager: ImageAttachmentManager? = null,
  private val imageEditingController: ImageEditingController? = null,
  private val visualQueryProcessor: VisualQueryProcessor? = null,
  private val geminiLiveVoiceEngine: GeminiLiveVoiceEngine? = null,
  private val assistantSessionStateManager: AssistantSessionStateManager? = null,
  private val liveVoiceSessionManager: LiveVoiceSessionManager? = null,
  private val phoneActionController: PhoneActionController? = null,
  private val socialIntegrationManager: SocialIntegrationManager? = null,
  private val screenRecordingManager: ScreenRecordingManager? = null,
  private val agentCommandRouter: AssistantAgentCommandRouter? = null,
  private val screenshotCaptureManager: ScreenshotCaptureManager? = null,
  private val smartCropEngine: SmartCropEngine? = null,
  private val agentTaskPlanner: AgentTaskPlanner? = null,
  private val contactResolver: ContactResolver? = null,
  private val accessibilityAgentController: AccessibilityAgentController? = null,
  private val systemAgentOrchestrator: AgentOrchestrator? = null,
  private val preferenceConfirmationManager: PreferenceConfirmationManager? = null,
  private val preferenceDiscoveryEngine: PreferenceDiscoveryEngine? = null,
  private val hotwordCapabilityManager: HotwordCapabilityManager? = null,
  private val calendarReminderService: CalendarReminderService? = null,
  private val imageStudioFacade: ImageStudioFacade? = null,
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
    screenSelectionManager = null,
    selectionGeometryEngine = null,
    imageAttachmentManager = null,
    imageEditingController = null,
    visualQueryProcessor = null,
    geminiLiveVoiceEngine = null,
    assistantSessionStateManager = null,
    liveVoiceSessionManager = null,
    phoneActionController = null,
    socialIntegrationManager = null,
    screenRecordingManager = null,
    agentCommandRouter = null,
    screenshotCaptureManager = null,
    smartCropEngine = null,
    agentTaskPlanner = null,
    contactResolver = null,
    accessibilityAgentController = null,
    systemAgentOrchestrator = null,
    preferenceConfirmationManager = null,
    preferenceDiscoveryEngine = null,
    hotwordCapabilityManager = null,
    calendarReminderService = null,
    imageStudioFacade = null,
  )

  val geometryEngineForUi: SelectionGeometryEngine by lazy {
    selectionGeometryEngine ?: SelectionGeometryEngine(
      freehandProcessor = FreehandPathProcessor(),
      circleProcessor = CircleSelectionProcessor(),
      rectangleProcessor = RectangleSelectionProcessor(),
    )
  }

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
  private var silentRetryCount: Int = 0
  private var nextRequestImageGenFlag: Boolean = false
  private var nextRequestDeepResearchFlag: Boolean = false
  private var pendingOpenLiveAfterPermission: Boolean = false
  private var dictationCommittedPrefix: String = ""

  init {
    observeAuthAndCloudSync()
    observeSettings()
    observeConversations()
    observeVoiceState()
    observePart4Streams()
    observePart5Streams()
    observeAddOnStreams()
    observeScreenSelectionStreams()
    observeActiveConversationMessages(_uiState.value.activeConversationId)
    refreshPowerState()
    onRefreshPermissions()
    onRefreshInstalledApps()
    viewModelScope.launch { reminderManager?.checkMissedReminders() }
    emergencyStopController?.registerStopListener {
      activeGenerationJob?.cancel()
      activeGenerationJob = null
      activeAssistantMessageId = null
      silentRetryCount = 0
      pendingOpenLiveAfterPermission = false
      dictationCommittedPrefix = ""
      screenSelectionManager?.stopAndCleanup()
      imageEditingController?.cancelEdit()
      liveVoiceSessionManager?.onEnded(keepScreenShare = false)
        _uiState.update {
          it.copy(
            isGeminiLiveOpen = false,
            isGeminiLiveMinimized = false,
            isLiveVoicePaused = false,
            isLiveVoiceMuted = false,
            isVoiceDialogOpen = false,
            isInlineDictationSessionActive = false,
            isInlineDictationPaused = false,
            inlineDictationError = null,
            isUsingGeminiLiveSocket = false,
            liveAssistantTranscript = "",
            liveSessionPhase = LiveSessionPhase.IDLE,
            liveConnectionState = LiveConnectionState.Disconnected,
            liveElapsedSeconds = 0L,
            orbReactiveFrame = OrbReactiveFrame.IDLE,
            responseState = AiResponseState.Cancelled,
            statusToastMessage = "Emergency Stop executed: all active sessions stopped.",
          )
        }
      }
    }

  private fun observeScreenSelectionStreams() {
    screenSelectionManager?.let { mgr ->
      viewModelScope.launch {
        mgr.state.collectLatest { selState ->
          _uiState.update { it.copy(screenSelectionState = selState) }
        }
      }
    }
    var knownSelectionUris = emptySet<String>()
    imageAttachmentManager?.let { attachMgr ->
      viewModelScope.launch {
        attachMgr.attachedVisuals.collectLatest { visuals ->
          val currentUris = visuals.map { it.croppedUriString }.toSet()
          val removedUris = knownSelectionUris - currentUris
          val addedVisuals = visuals.filter { it.croppedUriString !in knownSelectionUris }
          knownSelectionUris = currentUris

          _uiState.update { state ->
            var updatedPending =
              state.pendingAttachments.filterNot { att ->
                att is AttachmentPreview.PickedPhotoUri && att.uri in removedUris
              }
            addedVisuals.forEach { vis ->
              val preview = AttachmentPreview.PickedPhotoUri(vis.croppedUriString)
              if (preview !in updatedPending) {
                updatedPending = (updatedPending + preview).takeLast(4)
              }
            }
            state.copy(
              attachedVisualsByUri = visuals.associateBy { it.croppedUriString },
              pendingAttachments = updatedPending,
              isAttachmentSheetOpen = if (addedVisuals.isNotEmpty()) false else state.isAttachmentSheetOpen,
              shouldRequestComposerFocus = state.shouldRequestComposerFocus || addedVisuals.isNotEmpty(),
            )
          }

          visuals.filter { it.isSettling }.forEach { settlingItem ->
            viewModelScope.launch {
              delay(420L)
              attachMgr.markSettled(settlingItem.id)
            }
          }
        }
      }
    }
    imageEditingController?.let { editCtrl ->
      viewModelScope.launch {
        editCtrl.editState.collectLatest { editState ->
          _uiState.update { it.copy(activeImageEditState = editState) }
        }
      }
    }
  }

  private fun observeAuthAndCloudSync() {
    val gateway = authGateway ?: return
    gateway.registerBeforeSignOutHook { stopCloudAndMediaSessions() }
    viewModelScope.launch {
      gateway.observeSession().collectLatest { session ->
        _uiState.update { it.copy(authSession = session) }
        if (session.isSignedIn && !session.isGuest && session.uid.isNotBlank() && firestoreSync != null) {
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
    val hotwordReport = hotwordCapabilityManager?.inspectCapabilities()
    _uiState.update {
      it.copy(
        permissionCategories = items,
        hasAudioPermission = micGranted,
        hotwordCapabilityReport = hotwordReport,
      )
    }
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
        val savedOpMode =
          runCatching { OperatingMode.valueOf(settings.operatingModeName) }
            .getOrDefault(OperatingMode.NORMAL_CHAT)
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
            operatingMode = savedOpMode,
            inputMode = if (savedOpMode == OperatingMode.TASK_MODE) InputMode.TASK else InputMode.CHAT,
            connectionMode = settings.connectionMode,
            proxyBaseUrl = settings.proxyBaseUrl,
            geminiApiKey = settings.geminiApiKey,
            groqApiKey = settings.groqApiKey,
            cloudflareAccountId = settings.cloudflareAccountId,
            cloudflareApiToken = settings.cloudflareApiToken,
            strictFreeImageOnly = settings.strictFreeImageOnly,
            allowPaidImageFallbackApproved = settings.allowPaidImageFallbackApproved,
            hydrogenBackingModelId = settings.hydrogenBackingModelId,
            groqReasoningModelId = settings.groqReasoningModelId,
            groqHelperGuardModelId = settings.groqHelperGuardModelId,
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
            availableTtsVoices = voiceController.getAvailableVoices(settings.voicePreferences.preferredLanguage),
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
    assistantSessionStateManager?.let { mgr ->
      viewModelScope.launch {
        mgr.currentPhase.collectLatest { phase ->
          _uiState.update { it.copy(assistantSessionPhase = phase) }
        }
      }
      viewModelScope.launch {
        mgr.edgeActivation.collectLatest { signal ->
          _uiState.update { it.copy(edgeActivationSignal = signal) }
        }
      }
    }
    liveVoiceSessionManager?.let { liveMgr ->
      viewModelScope.launch {
        liveMgr.phase.collectLatest { livePhase ->
          _uiState.update { it.copy(liveSessionPhase = livePhase) }
        }
      }
      viewModelScope.launch {
        liveMgr.connectionState.collectLatest { conn ->
          _uiState.update { it.copy(liveConnectionState = conn) }
        }
      }
      viewModelScope.launch {
        liveMgr.elapsedSeconds.collectLatest { secs ->
          _uiState.update { it.copy(liveElapsedSeconds = secs) }
        }
      }
      viewModelScope.launch {
        liveMgr.orbFrame.collectLatest { frame ->
          _uiState.update { state ->
            val syncRms =
              if (frame.sessionPhase != LiveSessionPhase.IDLE &&
                frame.sessionPhase != LiveSessionPhase.ENDED &&
                frame.sessionPhase != LiveSessionPhase.PAUSED
              ) {
                maxOf(state.voiceRmsNormalized, frame.reactiveAmplitude)
              } else {
                state.voiceRmsNormalized
              }
            state.copy(orbReactiveFrame = frame, voiceRmsNormalized = syncRms)
          }
        }
      }
    }
    geminiLiveVoiceEngine?.let { engine ->
      viewModelScope.launch {
        engine.status.collectLatest { liveStatus ->
          if (!_uiState.value.isUsingGeminiLiveSocket) return@collectLatest
          when (liveStatus) {
            LiveVoiceSessionStatus.Connecting -> {
              _uiState.update {
                it.copy(voiceState = VoiceState.Listening("Connecting to Gemini Live…"))
              }
            }
            LiveVoiceSessionStatus.Active -> {
              _uiState.update {
                it.copy(
                  voiceState = VoiceState.Listening(engine.userTranscript.value),
                  isLiveVoicePaused = false,
                )
              }
            }
            LiveVoiceSessionStatus.Paused -> {
              _uiState.update { it.copy(voiceState = VoiceState.Idle) }
            }
            is LiveVoiceSessionStatus.Reconnecting -> {
              _uiState.update {
                it.copy(voiceState = VoiceState.Listening("Reconnecting Gemini Live (${liveStatus.attempt})…"))
              }
            }
            LiveVoiceSessionStatus.Disconnected -> {
              _uiState.update {
                it.copy(isUsingGeminiLiveSocket = false, voiceState = VoiceState.Idle)
              }
            }
            is LiveVoiceSessionStatus.Failed -> {
              _uiState.update {
                it.copy(
                  isUsingGeminiLiveSocket = false,
                  voiceState = VoiceState.Error(liveStatus.message, isRecoverableSilence = false),
                )
              }
            }
          }
        }
      }
      viewModelScope.launch {
        engine.userTranscript.collectLatest { userTxt ->
          val st = _uiState.value
          if (st.isUsingGeminiLiveSocket && st.isGeminiLiveOpen && !st.isLiveVoicePaused && !st.isLiveVoiceMuted) {
            if (userTxt.isNotBlank()) {
              liveSessionTurnsCount += 1
            }
            _uiState.update { it.copy(voiceState = VoiceState.Listening(userTxt)) }
          }
        }
      }
      viewModelScope.launch {
        engine.assistantTranscript.collectLatest { aiTxt ->
          val st = _uiState.value
          if (st.isUsingGeminiLiveSocket && st.isGeminiLiveOpen && aiTxt.isNotBlank()) {
            _uiState.update {
              it.copy(
                liveAssistantTranscript = aiTxt,
                voiceState = if (st.isLiveVoicePaused) VoiceState.Idle else VoiceState.Speaking("live"),
              )
            }
          }
        }
      }
    }
    screenShareSessionManager?.let { mgr ->
      viewModelScope.launch {
        mgr.status.collectLatest { s ->
          _uiState.update { it.copy(screenShareStatus = s) }
          assistantSessionStateManager?.onScreenSharingChanged(s != ScreenShareStatus.STOPPED)
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
    screenRecordingManager?.let { recMgr ->
      viewModelScope.launch {
        recMgr.state.collectLatest { recState ->
          _uiState.update { it.copy(screenRecordingState = recState) }
        }
      }
    }
    longTermMemoryManager?.let { mem ->
      viewModelScope.launch {
        mem.observeMemories().collectLatest { list -> _uiState.update { it.copy(memories = list) } }
      }
    }
    calendarReminderService?.observeTodoNotes()?.let { todoFlow ->
      viewModelScope.launch {
        todoFlow.collectLatest { items -> _uiState.update { it.copy(todoNotes = items) } }
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
    accessibilityAgentController?.let { acc ->
      viewModelScope.launch {
        acc.state.collectLatest { accState ->
          _uiState.update { it.copy(accessibilityState = accState) }
        }
      }
    }
    imageStudioFacade?.batchProcessor?.let { batch ->
      viewModelScope.launch {
        batch.items.collectLatest { items ->
          _uiState.update { it.copy(batchImageItems = items) }
        }
      }
      viewModelScope.launch {
        batch.isRunning.collectLatest { running ->
          _uiState.update { it.copy(isBatchRunning = running) }
        }
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
        if (_uiState.value.isUsingGeminiLiveSocket) return@collectLatest
        val snapshotBefore = _uiState.value
        val inLiveOverlay = snapshotBefore.isGeminiLiveOpen

        when (vState) {
          is VoiceState.Listening -> {
            _uiState.update { state ->
              val updatedInput =
                if (!state.isGeminiLiveOpen &&
                  state.isInlineDictationSessionActive &&
                  !state.isInlineDictationPaused &&
                  vState.partialTranscript.isNotBlank()
                ) {
                  DictationTranscriptMerger.mergePartial(dictationCommittedPrefix, vState.partialTranscript)
                } else {
                  state.inputText
                }
              state.copy(
                voiceState = vState,
                inputText = updatedInput,
                inlineDictationError = null,
                isVoiceDialogOpen = false,
              )
            }
          }
          is VoiceState.TranscriptReady -> {
            silentRetryCount = 0
            val transcript = vState.transcript.trim()
            if (inLiveOverlay) {
              _uiState.update { state ->
                state.copy(
                  voiceState = vState,
                  inputText = if (transcript.isNotBlank()) transcript else state.inputText,
                  isVoiceDialogOpen = false,
                )
              }
              voiceController.resetState()
              if (transcript.isNotBlank()) {
                liveSessionTurnsCount += 1
                onSendMessage()
              } else if (snapshotBefore.hasAudioPermission &&
                !snapshotBefore.isLiveVoicePaused &&
                !snapshotBefore.isLiveVoiceMuted
              ) {
                delay(480L)
                voiceController.startListening(snapshotBefore.voicePreferences.preferredLanguage)
              }
            } else {
              // Standard Chat Bar multi-turn dictation: insert/append into inputText for review & editing (never auto-send)
              val merged =
                if (transcript.isNotBlank()) {
                  DictationTranscriptMerger.appendFinalTurn(dictationCommittedPrefix, transcript)
                } else {
                  dictationCommittedPrefix
                }
              dictationCommittedPrefix = merged
              _uiState.update { state ->
                state.copy(
                  voiceState = VoiceState.Idle,
                  inputText = merged,
                  isVoiceDialogOpen = false,
                  isInlineDictationSessionActive = true,
                  isInlineDictationPaused = true,
                  inlineDictationError = null,
                )
              }
              voiceController.resetState()
            }
          }
          is VoiceState.Error -> {
            if (inLiveOverlay) {
              _uiState.update { it.copy(voiceState = vState, isVoiceDialogOpen = false) }
              val currentState = _uiState.value
              if (currentState.hasAudioPermission &&
                !currentState.isLiveVoicePaused &&
                !currentState.isLiveVoiceMuted &&
                !currentState.isGenerating
              ) {
                if (vState.isRecoverableSilence && silentRetryCount < 2) {
                  silentRetryCount += 1
                  delay(520L)
                  val latest = _uiState.value
                  if (latest.isGeminiLiveOpen &&
                    !latest.isUsingGeminiLiveSocket &&
                    !latest.isLiveVoicePaused &&
                    !latest.isLiveVoiceMuted &&
                    !latest.isGenerating
                  ) {
                    voiceController.startListening(latest.voicePreferences.preferredLanguage)
                  }
                } else {
                  silentRetryCount = 0
                  _uiState.update {
                    it.copy(
                      isLiveVoicePaused = true,
                      statusToastMessage =
                        if (vState.isRecoverableSilence) "Live mic resting after silence — tap Resume when ready."
                        else "Live mic paused: ${vState.message}",
                    )
                  }
                }
              }
            } else {
              // Inline Chat Bar error: stop showing active Listening, surface concise inline Retry
              _uiState.update { state ->
                state.copy(
                  voiceState = vState,
                  isVoiceDialogOpen = false,
                  isInlineDictationPaused = true,
                  inlineDictationError =
                    if (state.isInlineDictationSessionActive) vState.message
                    else state.inlineDictationError,
                )
              }
            }
          }
          else -> {
            _uiState.update { it.copy(voiceState = vState, isVoiceDialogOpen = false) }
          }
        }
      }
    }

    // Continuous turn loop for STT fallback mode: resume listening right after AI TTS finishes speaking
    viewModelScope.launch {
      voiceController.ttsCompletedTick.collectLatest { tick ->
        if (tick <= 0L) return@collectLatest
        delay(360L) // Brief acoustic echo guard before reopening SpeechRecognizer
        val latest = _uiState.value
        if (latest.isUsingGeminiLiveSocket) return@collectLatest
        val keepListening =
          (latest.isGeminiLiveOpen || latest.privacyPerformanceConfig.continuousVoiceMode) &&
            latest.hasAudioPermission &&
            !latest.isLiveVoicePaused &&
            !latest.isLiveVoiceMuted &&
            !latest.isGenerating
        if (keepListening) {
          silentRetryCount = 0
          voiceController.startListening(latest.voicePreferences.preferredLanguage)
        }
      }
    }

    // Sync notification actions (Pause / Resume / Stop) back to active Live Voice session
    viewModelScope.launch {
      voiceController.liveControlCommands.collect { cmd ->
        when (cmd) {
          LiveVoiceControlCommand.PAUSE -> {
            if (_uiState.value.isGeminiLiveOpen && !_uiState.value.isLiveVoicePaused) {
              onTogglePauseLiveVoice()
            }
          }
          LiveVoiceControlCommand.RESUME -> {
            if (_uiState.value.isGeminiLiveOpen && _uiState.value.isLiveVoicePaused) {
              onTogglePauseLiveVoice()
            }
          }
          LiveVoiceControlCommand.STOP -> {
            if (_uiState.value.isGeminiLiveOpen) {
              onToggleGeminiLive(false)
            }
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
    val activeSession = authGateway?.getCurrentSession()
    if (sync != null && impl != null && activeSession?.isSignedIn == true && !activeSession.isGuest) {
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
    val snap = _uiState.value
    if (snap.isInlineDictationPaused || snap.voiceState !is VoiceState.Listening) {
      dictationCommittedPrefix = newText
    }
    _uiState.update { it.copy(inputText = newText) }
  }

  fun onSelectSuggestion(prompt: String) {
    _uiState.update { it.copy(inputText = prompt) }
  }

  fun onSelectMode(mode: InputMode) {
    _uiState.update { state ->
      val syncedOpMode =
        when {
          mode == InputMode.TASK -> OperatingMode.TASK_MODE
          state.operatingMode == OperatingMode.TASK_MODE -> OperatingMode.NORMAL_CHAT
          else -> state.operatingMode
        }
      state.copy(inputMode = mode, operatingMode = syncedOpMode)
    }
    persistCurrentSettings()
  }

  fun onSelectOperatingMode(mode: OperatingMode) {
    _uiState.update { state ->
      state.copy(
        operatingMode = mode,
        inputMode = if (mode == OperatingMode.TASK_MODE) InputMode.TASK else InputMode.CHAT,
      )
    }
    persistCurrentSettings()
  }

  fun onToggleModelMenu(open: Boolean) {
    _uiState.update { it.copy(isModelMenuOpen = open) }
  }

  fun onSelectModel(model: AiModelOption) {
    _uiState.update { it.copy(selectedModel = model, resolvedAutoModelName = null, isModelMenuOpen = false) }
    persistCurrentSettings()
  }

  fun onSelectFlagshipModelMode(modelId: String, option: AiModelOption) {
    _uiState.update { state ->
      when (modelId) {
        VerifiedModelCatalog.POLLINATIONS_FLUX_ID ->
          state.copy(
            selectedModel = AiModelOption.GEMINI_IMAGE_STUDIO,
            imageModelId = VerifiedModelCatalog.POLLINATIONS_FLUX_ID,
            resolvedAutoModelName = "Pollinations.ai • Flux Studio",
            isModelMenuOpen = false,
          )
        else -> {
          val flagshipName =
            when (modelId) {
              VerifiedModelCatalog.FLAGSHIP_FLASH_38_ID -> VerifiedModelCatalog.createFlash38Spec().displayName
              VerifiedModelCatalog.BALANCED_CODING_36_ID -> VerifiedModelCatalog.createFlash36Spec().displayName
              else -> VerifiedModelCatalog.createHydrogenSpec(modelId).displayName
            }
          state.copy(
            selectedModel = option,
            hydrogenBackingModelId = modelId,
            resolvedAutoModelName = flagshipName,
            isModelMenuOpen = false,
          )
        }
      }
    }
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
    if (attachment is AttachmentPreview.PickedPhotoUri) {
      imageAttachmentManager?.removeByUri(attachment.uri)
    }
    _uiState.update { state ->
      state.copy(pendingAttachments = state.pendingAttachments - attachment)
    }
  }

  fun onClearPendingAttachment() {
    imageAttachmentManager?.clearAll(deleteSourceFrames = false)
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
      "open_image_studio" -> {
        openImageStudio(ImageEditFeatureType.TEXT_TO_IMAGE)
      }
      "open_more_tools" -> {
        _uiState.update { it.copy(isMoreToolsSheetOpen = true) }
      }
      "open_lens" -> {
        onRequestOpenLens()
      }
      "screen_selection" -> {
        _uiState.update {
          it.copy(
            isAttachmentSheetOpen = false,
            isMoreToolsSheetOpen = false,
            isImageToolsSheetOpen = false,
            pendingStartScreenSelection = true,
          )
        }
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
            operatingMode = OperatingMode.DEEP_RESEARCH,
            inputMode = InputMode.CHAT,
            isMoreToolsSheetOpen = false,
            inputText = it.inputText.ifBlank { "Deep research: " },
          )
        }
      }
      "web_search" -> {
        nextRequestDeepResearchFlag = true
        _uiState.update {
          it.copy(
            operatingMode = OperatingMode.GOOGLE_AI_SEARCH,
            inputMode = InputMode.CHAT,
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
        _uiState.update {
          it.copy(
            operatingMode = OperatingMode.TASK_MODE,
            inputMode = InputMode.TASK,
            isMoreToolsSheetOpen = false,
          )
        }
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
    if (_uiState.value.isInlineDictationSessionActive && !_uiState.value.isGeminiLiveOpen) {
      onStopInlineDictation()
    }
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

    if (editTargetId == null && attachments.isEmpty() && editTarget == null && shouldLaunchScreenSelection(trimmed)) {
      _uiState.update {
        it.copy(
          inputText = "",
          pendingStartScreenSelection = true,
          statusToastMessage = "Starting Live Screen Selection…",
        )
      }
      return
    }

    if (editTargetId == null && attachments.isEmpty() && editTarget == null && state.pendingMemoryConfirmation != null) {
      val replyLower = trimmed.lowercase(Locale.US).trimEnd('.', '!', '?')
      if (replyLower in setOf("yes", "yes please", "save it", "save", "remember it", "sure", "ok", "okay", "yeah", "yep")) {
        _uiState.update { it.copy(inputText = "") }
        onConfirmPendingMemory()
        return
      }
      if (replyLower in setOf("no", "no thanks", "skip", "don't save", "dont save", "cancel", "nope")) {
        _uiState.update { it.copy(inputText = "") }
        onDismissPendingMemory()
        return
      }
    }

    if (editTargetId == null && editTarget == null && trimmed.isNotEmpty() && agentTaskPlanner != null) {
      viewModelScope.launch {
        when (val outcome = agentTaskPlanner.planAndRoute(trimmed)) {
          is PlannedAgentOutcome.ConfirmPhoneAction -> {
            if (attachments.isEmpty()) {
              _uiState.update {
                it.copy(
                  inputText = "",
                  pendingPhoneAction = outcome.action,
                )
              }
              return@launch
            }
          }
          is PlannedAgentOutcome.ConfirmSocialAction -> {
            val firstMediaPath =
              attachments
                .filterIsInstance<AttachmentPreview.PickedPhotoUri>()
                .firstOrNull()
                ?.uri
                ?.let { rawUri -> runCatching { Uri.parse(rawUri).path }.getOrNull() }
                ?.takeIf { path -> File(path).exists() }
            val enrichedAction =
              if (outcome.action.mediaFilePath == null && firstMediaPath != null) {
                outcome.action.copy(mediaFilePath = firstMediaPath)
              } else {
                outcome.action
              }
            if (attachments.isEmpty() || firstMediaPath != null) {
              if (firstMediaPath != null) {
                imageAttachmentManager?.clearStagingKeepFiles()
              }
              _uiState.update {
                it.copy(
                  inputText = "",
                  pendingAttachments = if (firstMediaPath != null) emptyList() else it.pendingAttachments,
                  pendingSocialAction = enrichedAction,
                )
              }
              return@launch
            }
          }
          is PlannedAgentOutcome.TimerRequested -> {
            if (attachments.isEmpty()) {
              _uiState.update { it.copy(inputText = "") }
              val res = timerManager?.startTimer(outcome.durationSeconds, outcome.label)
              _uiState.update {
                it.copy(statusToastMessage = res?.second ?: "Timer scheduled for ${outcome.label}.")
              }
              return@launch
            }
          }
          is PlannedAgentOutcome.NotificationControlHandled -> {
            if (attachments.isEmpty()) {
              _uiState.update {
                it.copy(
                  inputText = "",
                  statusToastMessage = outcome.message,
                )
              }
              return@launch
            }
          }
          is PlannedAgentOutcome.AccessibilityHandled -> {
            if (attachments.isEmpty()) {
              val acc = outcome.result
              if (acc.requiresConfirmation) {
                _uiState.update {
                  it.copy(
                    inputText = "",
                    pendingAccessibilityAction = acc,
                  )
                }
              } else {
                _uiState.update {
                  it.copy(
                    inputText = "",
                    statusToastMessage = "${acc.statusLabel}: ${acc.detail}",
                  )
                }
              }
              return@launch
            }
          }
          is PlannedAgentOutcome.AgentTaskExecuted -> {
            if (attachments.isEmpty()) {
              val convId = state.activeConversationId
              val now = System.currentTimeMillis()
              _uiState.update { it.copy(inputText = "") }
              chatRepository.ensureConversation(convId, trimmed.take(36))
              chatRepository.upsertMessage(
                DomainMessage(
                  id = "msg_${UUID.randomUUID()}",
                  conversationId = convId,
                  isUser = true,
                  text = trimmed,
                  timestampMs = now,
                )
              )
              val assistantMsgId = "msg_${UUID.randomUUID()}"
              chatRepository.upsertMessage(
                DomainMessage(
                  id = assistantMsgId,
                  conversationId = convId,
                  isUser = false,
                  text = outcome.report.summaryText,
                  timestampMs = now + 1,
                  modelUsed = "Device Agent",
                )
              )
              val prefs = _uiState.value.voicePreferences
              if (prefs.autoSpeakResponses || _uiState.value.isGeminiLiveOpen) {
                voiceController.speakResponse(assistantMsgId, outcome.report.summaryText, prefs)
              }
              return@launch
            }
          }
          is PlannedAgentOutcome.ImageGenerationRequested -> {
            imageAttachmentManager?.clearStagingKeepFiles()
            _uiState.update {
              it.copy(
                inputText = "",
                pendingAttachments = emptyList(),
                activeEditTarget = null,
              )
            }
            executePrompt(
              conversationId = state.activeConversationId,
              prompt = trimmed,
              attachments = attachments,
              imageGenerationRequested = true,
              deepResearchRequested = false,
              activeEditTarget = editTarget,
            )
            return@launch
          }
          is PlannedAgentOutcome.LaunchCommand -> {
            if (attachments.isEmpty() && handleRoutedAgentCommand(trimmed, outcome.intent)) {
              return@launch
            }
          }
          PlannedAgentOutcome.ForwardToMultimodalAi -> Unit
        }

        imageAttachmentManager?.clearStagingKeepFiles()
        _uiState.update {
          it.copy(
            inputText = "",
            pendingAttachments = emptyList(),
            activeEditTarget = null,
          )
        }
        executePrompt(
          conversationId = state.activeConversationId,
          prompt = trimmed,
          attachments = attachments,
          imageGenerationRequested = requestImgGen,
          deepResearchRequested = requestDeepResearch,
          activeEditTarget = editTarget,
        )
      }
      return
    }

    if (editTargetId == null && attachments.isEmpty() && editTarget == null) {
      val routedCmd = agentCommandRouter?.classify(trimmed)
      if (routedCmd != null && handleRoutedAgentCommand(trimmed, routedCmd)) {
        return
      }
      val phoneCmd = PhoneActionController.tryParseConfirmationCommand(trimmed)
      if (phoneCmd != null) {
        val resolved = phoneActionController?.resolveAndPreparePhoneAction(phoneCmd) ?: phoneCmd
        _uiState.update {
          it.copy(
            inputText = "",
            pendingPhoneAction = resolved,
          )
        }
        return
      }
      val socialCmd = SocialIntegrationManager.tryParseSocialCommand(trimmed)
      if (socialCmd != null) {
        val resolved = socialIntegrationManager?.resolveAndPrepareSocialAction(socialCmd) ?: socialCmd
        _uiState.update {
          it.copy(
            inputText = "",
            pendingSocialAction = resolved,
          )
        }
        return
      }
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
        imageAttachmentManager?.clearStagingKeepFiles()
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

    imageAttachmentManager?.clearStagingKeepFiles()
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
    val pendingMedia =
      attachments.mapNotNull { (it as? AttachmentPreview.MediaFile)?.item }

    val preparedQuery = visualQueryProcessor?.prepareQuery(prompt, attachments)
    val processedImages = mutableListOf<ProcessedImagePayload>()
    if (preparedQuery != null && preparedQuery.payloads.isNotEmpty()) {
      processedImages.addAll(preparedQuery.payloads)
    }
    for (attachment in attachments) {
      if (attachment is AttachmentPreview.PickedPhotoUri &&
        processedImages.any { it.uriString == attachment.uri }
      ) {
        continue
      }
      val result =
        when (attachment) {
          AttachmentPreview.SampleInlineArtwork ->
            imageProcessor.processDrawableResource(R.drawable.img_inline_preview_art_1795023054486)
          is AttachmentPreview.PickedPhotoUri -> {
            val filePath = Uri.parse(attachment.uri).path
            val directFile = filePath?.let(::decodeFileToProcessedImage)
            if (directFile != null) Result.success(directFile)
            else imageProcessor.processUri(attachment.uri)
          }
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

    if (processedImages.isEmpty() &&
      screenShareSessionManager != null &&
      screenShareSessionManager.status.value != ScreenShareStatus.STOPPED &&
      screenShareSessionManager.status.value != ScreenShareStatus.REQUESTING_CONSENT
    ) {
      val sharedFrame =
        visualQueryProcessor?.prepareScreenSharePayload(
          screenShareSessionManager = screenShareSessionManager,
          screenshotCaptureManager = screenshotCaptureManager,
          smartCropEngine = smartCropEngine,
        )
      if (sharedFrame != null) {
        processedImages.add(sharedFrame.payload)
      }
    }

    val userText =
      preparedQuery?.userDisplayPrompt?.takeIf { it.isNotBlank() }
        ?: prompt.ifEmpty {
          if (processedImages.isNotEmpty()) "Analyze selected region"
          else "Please analyze the attached file or media."
        }
    val modelPromptText =
      preparedQuery?.modelPrompt?.takeIf { it.isNotBlank() } ?: userText

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
                userPrompt = modelPromptText,
                historyMessages = historyBefore,
                currentTurnImages = processedImages,
                requestedModelId = _uiState.value.selectedModel.modelId,
                isTaskMode =
                  _uiState.value.inputMode == InputMode.TASK ||
                    _uiState.value.operatingMode == OperatingMode.TASK_MODE,
                operatingMode = _uiState.value.operatingMode,
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
                  _uiState.update {
                    it.copy(
                      activeRoutingReason = event.decision.reason,
                      resolvedAutoModelName =
                        if (it.selectedModel == AiModelOption.MOUSOMI_AUTO) {
                          event.decision.selectedModel.displayName
                        } else {
                          it.resolvedAutoModelName
                        },
                    )
                  }
                }
                is OrchestrationEvent.TitleSuggested -> {
                  chatRepository.updateConversationTitle(conversationId, event.title)
                }
                is OrchestrationEvent.ResearchTelemetry -> {
                  _uiState.update { it.copy(researchActivity = event.snapshot) }
                }
                is OrchestrationEvent.BlocksUpdated -> {
                  latestBlocks = event.blocks
                  val citations =
                    event.blocks.filterIsInstance<ResponseBlock.SourceCitations>().firstOrNull()?.sources.orEmpty()
                  _uiState.update { ui ->
                    val updated =
                      ui.messages.map { msg ->
                        if (msg.id == assistantMsgId) msg.copy(blocks = latestBlocks) else msg
                      }
                    val updatedResearch =
                      ui.researchActivity?.let { snap ->
                        if (citations.isNotEmpty()) {
                          snap.copy(
                            sourcesReturnedCount = maxOf(snap.sourcesReturnedCount, citations.size),
                            sourcesUsedCount = citations.count { it.usedInAnswer },
                          )
                        } else snap
                      }
                    ui.copy(messages = updated, researchActivity = updatedResearch)
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
                          val followUps = buildFollowUpSuggestions(userText, stateVal.fullText, latestBlocks)
                          ui.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                              msg.copy(
                                text = stateVal.fullText,
                                modelUsed = stateVal.modelName,
                                categoryLabel = routedCategoryLabel,
                                tokenUsage = latestUsage,
                                isStreaming = false,
                                blocks = latestBlocks,
                                followUpSuggestions = followUps,
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
                      val privacyCfg = _uiState.value.privacyPerformanceConfig
                      if (privacyCfg.memoryEnabled && longTermMemoryManager != null) {
                        val updatedHistory = historyBefore + assistantDomain
                        if (updatedHistory.size >= 4) {
                          longTermMemoryManager.summarizeConversationAndPersist(conversationId, updatedHistory)
                        }
                      }
                      if (privacyCfg.memoryEnabled && preferenceConfirmationManager != null) {
                        val candidatePref =
                          preferenceConfirmationManager.inspectUserStatement(
                            rawInput = userText,
                            memoryEnabled = true,
                          )
                        if (candidatePref != null) {
                          if (candidatePref.isExplicitSaveRequest) {
                            val saveOutcome =
                              preferenceConfirmationManager.confirmAndSave(
                                pending = candidatePref,
                                encryptAtRest = privacyCfg.encryptSensitiveMemory,
                              )
                            val savedNote =
                              when (saveOutcome) {
                                is MemoryWriteOutcome.Saved ->
                                  "Saved to My Memory: \"${candidatePref.extractedContent}\""
                                is MemoryWriteOutcome.BlockedSensitive -> saveOutcome.reason
                              }
                            _uiState.update { it.copy(statusToastMessage = savedNote) }
                          } else {
                            _uiState.update { it.copy(pendingMemoryConfirmation = candidatePref) }
                          }
                        } else if (preferenceDiscoveryEngine != null && privacyCfg.proactiveMemoryQuestionsEnabled) {
                          val userTurns = (historyBefore.count { it.isUser } + 1)
                          val nextDiscovery =
                            preferenceDiscoveryEngine.maybeSuggestDiscoveryQuestion(
                              userPrompt = userText,
                              memoryEnabled = true,
                              proactiveQuestionsEnabled = true,
                              userTurnCountInConversation = userTurns,
                            )
                          if (nextDiscovery != null) {
                            _uiState.update { it.copy(pendingDiscoveryPrompt = nextDiscovery) }
                          }
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
                      val liveState = _uiState.value
                      if (liveState.isGeminiLiveOpen &&
                        liveState.hasAudioPermission &&
                        !liveState.isLiveVoicePaused &&
                        !liveState.isLiveVoiceMuted
                      ) {
                        delay(900L)
                        val refreshed = _uiState.value
                        if (refreshed.isGeminiLiveOpen && !refreshed.isLiveVoicePaused && !refreshed.isLiveVoiceMuted && !refreshed.isGenerating) {
                          voiceController.startListening(refreshed.voicePreferences.preferredLanguage)
                        }
                      }
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
      OrbQuickAction.START_VOICE -> onStartInlineDictation()
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
    onNavigateToLens: () -> Unit = {},
    onNavigateToAssistantHub: (Int) -> Unit = {},
  ) {
    when (action) {
      OrbPopupAction.VOICE_ASSISTANT -> onStartInlineDictation()
      OrbPopupAction.OPEN_CHAT -> _uiState.update { it.copy(inputMode = InputMode.CHAT) }
      OrbPopupAction.QUICK_ASK -> _uiState.update { it.copy(inputMode = InputMode.CHAT, inputText = "") }
      OrbPopupAction.OPEN_CAMERA -> {
        onRequestOpenLens()
        onNavigateToLens()
      }
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
      val planned = agentTaskPlanner?.planAndRoute(command)
      when (planned) {
        is PlannedAgentOutcome.ConfirmPhoneAction -> {
          _uiState.update { it.copy(pendingPhoneAction = planned.action) }
          return@launch
        }
        is PlannedAgentOutcome.ConfirmSocialAction -> {
          _uiState.update { it.copy(pendingSocialAction = planned.action) }
          return@launch
        }
        is PlannedAgentOutcome.TimerRequested -> {
          val res = timerManager?.startTimer(planned.durationSeconds, planned.label)
          _uiState.update {
            it.copy(statusToastMessage = res?.second ?: "Timer scheduled for ${planned.label}.")
          }
          return@launch
        }
        is PlannedAgentOutcome.NotificationControlHandled -> {
          _uiState.update { it.copy(statusToastMessage = planned.message) }
          return@launch
        }
        is PlannedAgentOutcome.AccessibilityHandled -> {
          val acc = planned.result
          if (acc.requiresConfirmation) {
            _uiState.update { it.copy(pendingAccessibilityAction = acc) }
          } else {
            _uiState.update { it.copy(statusToastMessage = "${acc.statusLabel}: ${acc.detail}") }
          }
          return@launch
        }
        is PlannedAgentOutcome.AgentTaskExecuted -> {
          _uiState.update { it.copy(statusToastMessage = planned.report.summaryText) }
          return@launch
        }
        is PlannedAgentOutcome.ImageGenerationRequested -> {
          executePrompt(
            conversationId = _uiState.value.activeConversationId,
            prompt = planned.prompt,
            attachments = emptyList(),
            imageGenerationRequested = true,
          )
          return@launch
        }
        is PlannedAgentOutcome.LaunchCommand -> {
          if (handleRoutedAgentCommand(command, planned.intent)) return@launch
        }
        PlannedAgentOutcome.ForwardToMultimodalAi,
        null -> Unit
      }

      val phoneCmd = PhoneActionController.tryParseConfirmationCommand(command)
      if (phoneCmd != null) {
        val resolved = phoneActionController?.resolveAndPreparePhoneAction(phoneCmd) ?: phoneCmd
        _uiState.update { it.copy(pendingPhoneAction = resolved) }
        return@launch
      }
      val socialCmd = SocialIntegrationManager.tryParseSocialCommand(command)
      if (socialCmd != null) {
        val resolved = socialIntegrationManager?.resolveAndPrepareSocialAction(socialCmd) ?: socialCmd
        _uiState.update { it.copy(pendingSocialAction = resolved) }
        return@launch
      }

      val lower = command.trim().lowercase()
      val pac = phoneActionController
      val res =
        when {
          pac != null && (lower.startsWith("navigate to ") || lower.startsWith("directions to ")) ->
            pac.openMapsOrNavigation(
              query = command.trim().substringAfter("to ").trim(),
              turnByTurnNavigation = true,
            )
          pac != null && (lower.startsWith("maps ") || lower.startsWith("find on maps ")) ->
            pac.openMapsOrNavigation(
              query = command.trim().substringAfter(" ").trim(),
              turnByTurnNavigation = false,
            )
          pac != null && lower.contains("alarm") && lower.contains("open") ->
            pac.openAlarms()
          else -> deviceActionExecutor?.executeNaturalCommand(command)
        }
      val toast = res?.let { "${it.statusLabel}: ${it.verifiedDetail}" } ?: "Device assistant unavailable."
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onConfirmPendingPhoneAction() {
    val action = _uiState.value.pendingPhoneAction ?: return
    _uiState.update { it.copy(pendingPhoneAction = null) }
    viewModelScope.launch {
      val res = phoneActionController?.executeConfirmedAction(action)
      val toast = res?.let { "${it.statusLabel}: ${it.verifiedDetail}" } ?: "Phone action controller unavailable."
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onConfirmPendingSocialAction() {
    val action = _uiState.value.pendingSocialAction ?: return
    _uiState.update { it.copy(pendingSocialAction = null) }
    viewModelScope.launch {
      val res = socialIntegrationManager?.executeConfirmedSocialAction(action)
      val toast = res?.let { "${it.statusLabel}: ${it.verifiedDetail}" } ?: "Social integration manager unavailable."
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onSelectAmbiguousContact(candidate: ResolvedContact) {
    _uiState.update { current ->
      val updatedPhone =
        current.pendingPhoneAction?.let { ph ->
          val rebuilt =
            if (ph.type == com.example.core.device.PendingPhoneActionType.DIAL_PHONE) {
              phoneActionController?.prepareDialNumber(candidate.phoneNumber, candidate.displayName)
                ?: ph.copy(
                  targetNumber = candidate.phoneNumber,
                  displayLabel = candidate.displayName,
                  candidateContacts = emptyList(),
                  isContactAmbiguous = false,
                  needsContactsPermission = false,
                  summary = "Prepare Android Dialer for ${candidate.displayName} (${candidate.phoneNumber}). You tap Call yourself.",
                )
            } else {
              phoneActionController?.prepareSms(candidate.phoneNumber, ph.messageBody, candidate.displayName)
                ?: ph.copy(
                  targetNumber = candidate.phoneNumber,
                  displayLabel = candidate.displayName,
                  candidateContacts = emptyList(),
                  isContactAmbiguous = false,
                  needsContactsPermission = false,
                  summary = "Open system SMS composer to ${candidate.displayName} (${candidate.phoneNumber}).",
                )
            }
          rebuilt
        }
      val updatedSocial =
        current.pendingSocialAction?.let { soc ->
          socialIntegrationManager?.prepareWhatsAppMessage(
            phoneOrName = candidate.phoneNumber,
            messageText = soc.captionOrMessage,
            resolvedDisplayLabel = "${candidate.displayName} (${candidate.phoneNumber})",
          ) ?: soc.copy(
            recipientOrHandle = candidate.phoneNumber,
            candidateContacts = emptyList(),
            isContactAmbiguous = false,
            needsContactsPermission = false,
            summary = "Open WhatsApp composer for ${candidate.displayName} (${candidate.phoneNumber}).",
          )
        }
      current.copy(
        pendingPhoneAction = updatedPhone,
        pendingSocialAction = updatedSocial,
      )
    }
  }

  fun onPickedContactUri(uri: Uri) {
    val resolved = contactResolver?.parsePickedContactUri(uri)
    if (resolved == null) {
      _uiState.update {
        it.copy(statusToastMessage = "Selected contact has no phone number or Contacts permission is required for that picker URI.")
      }
      return
    }
    onSelectAmbiguousContact(resolved)
  }

  fun onReResolvePendingContactAction() {
    onRefreshPermissions()
    _uiState.update { current ->
      val updatedPhone =
        current.pendingPhoneAction?.let { ph ->
          phoneActionController?.resolveAndPreparePhoneAction(ph) ?: ph
        }
      val updatedSocial =
        current.pendingSocialAction?.let { soc ->
          socialIntegrationManager?.resolveAndPrepareSocialAction(soc) ?: soc
        }
      current.copy(
        pendingPhoneAction = updatedPhone,
        pendingSocialAction = updatedSocial,
      )
    }
  }

  fun onRefreshAccessibilityState() {
    accessibilityAgentController?.refreshVisibleNodes()
  }

  fun onStopAccessibilityWorkflow() {
    accessibilityAgentController?.stopAllAutomation()
    _uiState.update { it.copy(pendingAccessibilityAction = null, statusToastMessage = "Stopped Accessibility automation.") }
  }

  fun onConfirmPendingAccessibilityAction() {
    val pending = _uiState.value.pendingAccessibilityAction ?: return
    _uiState.update { it.copy(pendingAccessibilityAction = null) }
    val target = pending.targetLabelToConfirm
    if (target.isBlank()) return
    val res = accessibilityAgentController?.performNodeClick(targetText = target, confirmedSensitiveAction = true)
    val toast = res?.let { "${it.statusLabel}: ${it.detail}" } ?: "Accessibility Assistant unavailable."
    _uiState.update { it.copy(statusToastMessage = toast) }
  }

  fun onDismissPendingAccessibilityAction() {
    _uiState.update { it.copy(pendingAccessibilityAction = null) }
  }

  fun onDismissPendingActions() {
    _uiState.update {
      it.copy(
        pendingPhoneAction = null,
        pendingSocialAction = null,
        pendingAccessibilityAction = null,
      )
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
    assistantSessionStateManager?.onDrawingChanged(open)
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

  fun onAttachAnnotatedRegion(
    box: NormBoundingBox,
    strokes: List<AnnotationStroke>,
    internalCaptureFile: File? = null,
  ) {
    val targetImage = _uiState.value.drawingTargetImage
    _uiState.update { it.copy(isDrawingOverlayOpen = false, drawingTargetImage = null) }
    val notesFromLabels =
      strokes.mapNotNull { it.textLabel.takeIf { lbl -> lbl.isNotBlank() } }.joinToString(", ")

    viewModelScope.launch {
      part5Repository?.saveDrawingAnnotation(
        targetMediaId = targetImage?.mediaId,
        strokes = strokes,
        box = box,
        promptNote = notesFromLabels,
      )

      val croppedFile = cropAnnotationRegionToFile(targetImage?.filePath, internalCaptureFile, box, strokes)
      internalCaptureFile?.let { f -> if (f.exists()) tempMediaCacheManager?.cleanupFile(f) }
      if (croppedFile == null) {
        val shareStatus = screenShareSessionManager?.status?.value
        val reason =
          if (shareStatus == null || shareStatus == ScreenShareStatus.STOPPED || shareStatus == ScreenShareStatus.REQUESTING_CONSENT) {
            "No image source or active Screen Share frame. Turn on Share Screen to capture external content, or open an image first."
          } else {
            "Unable to crop the selected region cleanly. Please redraw around the target."
          }
        _uiState.update { it.copy(statusToastMessage = reason) }
        return@launch
      }

      val attachMgr = imageAttachmentManager
      val detectedShape = inferShapeFromStrokes(strokes)
      val sourceForEdit = targetImage?.filePath ?: screenShareSessionManager?.getLatestFrame()?.filePath
      if (attachMgr != null) {
        attachMgr.attachCroppedVisual(
          croppedFile = croppedFile,
          sourceFrameFilePath = sourceForEdit,
          captureMetadata = null,
          normCropRect = box,
          detectedShape = detectedShape,
          isSettling = false,
        )
      } else {
        val uriString = Uri.fromFile(croppedFile).toString()
        _uiState.update { state ->
          val updated = (state.pendingAttachments + AttachmentPreview.PickedPhotoUri(uriString)).distinct().take(4)
          state.copy(pendingAttachments = updated)
        }
      }

      _uiState.update {
        it.copy(
          shouldRequestComposerFocus = true,
          inputText =
            if (it.inputText.isBlank() && notesFromLabels.isNotBlank()) notesFromLabels else it.inputText,
          statusToastMessage = "Cropped selection attached to Chat Bar.",
        )
      }
    }
  }

  fun onAnalyzeAnnotatedRegion(
    box: NormBoundingBox,
    strokes: List<AnnotationStroke>,
  ) {
    val targetImage = _uiState.value.drawingTargetImage
    _uiState.update { it.copy(isDrawingOverlayOpen = false, drawingTargetImage = null) }
    val notesFromLabels =
      strokes.mapNotNull { it.textLabel.takeIf { lbl -> lbl.isNotBlank() } }.joinToString(", ")

    viewModelScope.launch {
      part5Repository?.saveDrawingAnnotation(
        targetMediaId = targetImage?.mediaId,
        strokes = strokes,
        box = box,
        promptNote = notesFromLabels,
      )

      val croppedFile = cropAnnotationRegionToFile(targetImage?.filePath, null, box, strokes)
      if (croppedFile == null) {
        _uiState.update {
          it.copy(
            statusToastMessage =
              "Cannot capture external screen without active Screen Share. Enable Share Screen or attach an image first."
          )
        }
        return@launch
      }

      val cropUriString = Uri.fromFile(croppedFile).toString()
      val prompt =
        buildString {
          append(if (targetImage != null) "Analyze this selected region of the image" else "Analyze this annotated screen region")
          if (notesFromLabels.isNotBlank()) {
            append(" (user annotations: $notesFromLabels)")
          }
          append(". Explain clearly what is shown.")
        }

      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = prompt,
        attachments = listOf(AttachmentPreview.PickedPhotoUri(cropUriString)),
      )
    }
  }

  private fun inferShapeFromStrokes(strokes: List<AnnotationStroke>): DetectedRegionShape {
    val primary = strokes.lastOrNull { it.points.isNotEmpty() } ?: return DetectedRegionShape.RECTANGLE
    return when (primary.tool) {
      DrawingTool.CIRCLE -> DetectedRegionShape.CIRCLE
      DrawingTool.RECTANGLE, DrawingTool.SELECTION -> DetectedRegionShape.RECTANGLE
      DrawingTool.FREEHAND, DrawingTool.HIGHLIGHTER -> DetectedRegionShape.FREEHAND_LOOP
      else -> DetectedRegionShape.RECTANGLE
    }
  }

  private suspend fun cropAnnotationRegionToFile(
    targetFilePath: String?,
    internalCaptureFile: File?,
    box: NormBoundingBox,
    strokes: List<AnnotationStroke>,
  ): File? {
    val sourcePath =
      targetFilePath?.takeIf { it.isNotBlank() && File(it).exists() }
        ?: screenShareSessionManager?.getLatestFrame()?.filePath?.takeIf { it.isNotBlank() && File(it).exists() }
        ?: internalCaptureFile?.takeIf { it.exists() && it.length() > 0L }?.absolutePath
        ?: return null

    val shape = inferShapeFromStrokes(strokes)
    val contour =
      strokes
        .filter { it.points.size >= 3 }
        .flatMap { it.points }
        .takeIf { shape == DetectedRegionShape.FREEHAND_LOOP || shape == DetectedRegionShape.CIRCLE }
        .orEmpty()

    val cropEngine = smartCropEngine
    if (cropEngine != null) {
      val snapped =
        SelectionGeometryResult(
          boundingBox = box,
          detectedShape = shape,
          simplifiedContour = contour,
        )
      val cropped =
        cropEngine.cropSelectionToFile(
          sourceFilePath = sourcePath,
          snappedResult = snapped,
          prefix = "sel_annotated_",
        )
      if (cropped.isSuccess) {
        return cropped.getOrNull()?.file
      }
    }

    if (tempMediaCacheManager == null) return null
    val raw = BitmapFactory.decodeFile(sourcePath) ?: return null
    val cropped = RegionCropper.cropBitmapToNormBox(raw, box)
    raw.recycle()
    if (cropped == null) return null
    val baos = ByteArrayOutputStream()
    cropped.compress(Bitmap.CompressFormat.JPEG, 88, baos)
    cropped.recycle()
    return tempMediaCacheManager.saveTempJpeg(baos.toByteArray(), prefix = "sel_annotated_")
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

  fun onConfirmPendingMemory() {
    val pending = _uiState.value.pendingMemoryConfirmation ?: return
    _uiState.update { it.copy(pendingMemoryConfirmation = null) }
    viewModelScope.launch {
      val encrypt = _uiState.value.privacyPerformanceConfig.encryptSensitiveMemory
      val outcome = preferenceConfirmationManager?.confirmAndSave(pending, encryptAtRest = encrypt)
      val toast =
        when (outcome) {
          is MemoryWriteOutcome.Saved -> "Saved to My Memory: \"${pending.extractedContent}\""
          is MemoryWriteOutcome.BlockedSensitive -> outcome.reason
          null -> "Memory repository unavailable."
        }
      _uiState.update { it.copy(statusToastMessage = toast, memoryStatusMessage = toast) }
    }
  }

  fun onDismissPendingMemory() {
    _uiState.update {
      it.copy(
        pendingMemoryConfirmation = null,
        statusToastMessage = "Okay, I did not save that to My Memory.",
      )
    }
  }

  fun onDismissDiscoveryPrompt() {
    val prompt = _uiState.value.pendingDiscoveryPrompt ?: return
    preferenceDiscoveryEngine?.markTopicAskedOrDeclined(prompt.topicKey)
    _uiState.update { it.copy(pendingDiscoveryPrompt = null) }
  }

  fun onReplyDiscoveryPrompt(answerText: String) {
    val prompt = _uiState.value.pendingDiscoveryPrompt ?: return
    preferenceDiscoveryEngine?.markTopicAskedOrDeclined(prompt.topicKey)
    _uiState.update { it.copy(pendingDiscoveryPrompt = null, inputText = answerText) }
    onSendMessage()
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

  fun onRenameConversation(conversationId: String, newTitle: String) {
    val clean = newTitle.trim().take(80)
    if (clean.isBlank()) return
    viewModelScope.launch {
      chatRepository.updateConversationTitle(conversationId, clean)
      _uiState.update { it.copy(statusToastMessage = "Renamed conversation to \"$clean\"") }
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

  private fun shouldLaunchScreenSelection(prompt: String): Boolean {
    val lower = prompt.trim().lowercase(Locale.US)
    if (lower.length > 90) return false
    return lower == "select screen" ||
      lower == "screen select" ||
      lower == "screen selection" ||
      lower == "select on screen" ||
      lower == "circle to search" ||
      lower.contains("select region on screen") ||
      lower.contains("highlight on screen") ||
      lower.contains("circle on screen") ||
      lower.contains("crop my screen") ||
      lower.contains("crop the screen") ||
      lower.contains("start screen selection")
  }

  // --- Live Screen Selection, Smart Auto-Crop & Compact Image Editor ---

  fun onStartScreenSelectionFlow(onRequestConsent: () -> Unit) {
    _uiState.update {
      it.copy(
        isAttachmentSheetOpen = false,
        isMoreToolsSheetOpen = false,
        isImageToolsSheetOpen = false,
      )
    }
    screenSelectionManager?.markAwaitingConsent(useSystemOverlay = false)
    onRequestConsent()
  }

  fun onConsumePendingStartScreenSelection() {
    _uiState.update { it.copy(pendingStartScreenSelection = false) }
  }

  fun onScreenSelectionConsentCancelled() {
    screenSelectionManager?.cancelSelection()
    _uiState.update { it.copy(statusToastMessage = "Screen selection cancelled.") }
  }

  fun onSelectSelectionTool(mode: SelectionToolMode) {
    screenSelectionManager?.selectToolMode(mode)
  }

  fun onSelectionStrokeStart(point: NormPoint) {
    screenSelectionManager?.onStrokeStart(point)
  }

  fun onSelectionStrokeMove(point: NormPoint) {
    screenSelectionManager?.onStrokeMove(point)
  }

  fun onSelectionStrokeEnd() {
    screenSelectionManager?.onStrokeEnd()
  }

  fun onSelectionAdjustHandle(handle: CropHandle, deltaXNorm: Float, deltaYNorm: Float) {
    screenSelectionManager?.onAdjustHandle(handle, deltaXNorm, deltaYNorm)
  }

  fun onSelectionUndo() {
    screenSelectionManager?.undo()
  }

  fun onSelectionClear() {
    screenSelectionManager?.clearSelection()
  }

  fun onCancelScreenSelection() {
    screenSelectionManager?.cancelSelection()
  }

  fun onConfirmScreenSelection(overlayWidthPx: Int, overlayHeightPx: Int) {
    val mgr = screenSelectionManager ?: return
    viewModelScope.launch {
      val result = mgr.confirmAndAutoCrop(overlayWidthPx = overlayWidthPx, overlayHeightPx = overlayHeightPx)
      result.onFailure { err ->
        _uiState.update {
          it.copy(statusToastMessage = err.message ?: "Unable to crop selected region.")
        }
      }
      result.onSuccess {
        _uiState.update { it.copy(shouldRequestComposerFocus = true) }
      }
    }
  }

  fun onReturnedFromSystemSelection() {
    val latest = imageAttachmentManager?.attachedVisuals?.value.orEmpty()
    _uiState.update { state ->
      var updatedPending = state.pendingAttachments
      latest.forEach { vis ->
        val preview = AttachmentPreview.PickedPhotoUri(vis.croppedUriString)
        if (preview !in updatedPending) {
          updatedPending = (updatedPending + preview).takeLast(4)
        }
      }
      state.copy(
        pendingAttachments = updatedPending,
        attachedVisualsByUri = latest.associateBy { it.croppedUriString },
        shouldRequestComposerFocus = true,
      )
    }
  }

  fun onConsumeComposerFocusRequest() {
    _uiState.update { it.copy(shouldRequestComposerFocus = false) }
  }

  fun onOpenCompactImageEditor(targetUri: String? = null) {
    val ctrl = imageEditingController
    val mgr = imageAttachmentManager
    if (ctrl == null || mgr == null) return

    val targetItem =
      targetUri?.let { mgr.findByUri(it) }
        ?: _uiState.value.pendingAttachments
          .filterIsInstance<AttachmentPreview.PickedPhotoUri>()
          .lastOrNull()
          ?.let { mgr.findByUri(it.uri) }
        ?: mgr.latestEditableVisual()

    if (targetItem != null) {
      ctrl.beginEditing(targetItem)
      return
    }

    // Also allow editing a regular gallery photo attachment if present
    val fallbackUri =
      targetUri
        ?: _uiState.value.pendingAttachments
          .filterIsInstance<AttachmentPreview.PickedPhotoUri>()
          .lastOrNull()
          ?.uri
    if (fallbackUri != null) {
      val path = Uri.parse(fallbackUri).path
      if (!path.isNullOrBlank() && File(path).exists()) {
        val tempItem =
          mgr.attachCroppedVisual(
            croppedFile = File(path),
            sourceFrameFilePath = path,
            captureMetadata = null,
            normCropRect = NormBoundingBox(0.05f, 0.05f, 0.95f, 0.95f),
            detectedShape = DetectedRegionShape.RECTANGLE,
            isSettling = false,
          )
        ctrl.beginEditing(tempItem)
      } else {
        _uiState.update { it.copy(statusToastMessage = "Tap on an attached image or select a screen region to crop.") }
      }
    }
  }

  fun onAdjustImageEditorHandle(handle: CropHandle, deltaXNorm: Float, deltaYNorm: Float) {
    imageEditingController?.adjustHandle(handle, deltaXNorm, deltaYNorm)
  }

  fun onSelectEditorAspectPreset(preset: CropAspectPreset) {
    imageEditingController?.setAspectPreset(preset)
  }

  fun onRotateEditor90() {
    imageEditingController?.rotate90()
  }

  fun onResetEditorCrop() {
    imageEditingController?.resetToOriginal()
  }

  fun onConfirmImageEditor() {
    val ctrl = imageEditingController ?: return
    viewModelScope.launch {
      ctrl.confirmEdit().onFailure { err ->
        _uiState.update { it.copy(statusToastMessage = err.message ?: "Unable to update crop.") }
      }
      _uiState.update { it.copy(shouldRequestComposerFocus = true) }
    }
  }

  fun onCancelImageEditor() {
    imageEditingController?.cancelEdit()
  }

  fun onRetakeScreenSelection(onRequestConsent: () -> Unit) {
    imageEditingController?.cancelEdit()
    onStartScreenSelectionFlow(onRequestConsent)
  }

  fun onRequestPendingLiveAfterPermission() {
    pendingOpenLiveAfterPermission = true
  }

  fun onShareScreenWithLive(
    onRequestStartScreenShare: () -> Unit,
    onRequestStopScreenShare: () -> Unit = {},
  ) {
    val active =
      _uiState.value.screenShareStatus != ScreenShareStatus.STOPPED &&
        _uiState.value.screenShareStatus != ScreenShareStatus.REQUESTING_CONSENT
    if (active) {
      onRequestStopScreenShare()
      return
    }
    if (!_uiState.value.isGeminiLiveOpen) {
      onToggleGeminiLive(true)
    }
    if (_uiState.value.screenShareStatus == ScreenShareStatus.STOPPED) {
      onRequestStartScreenShare()
    }
  }

  fun onSetFloatingLiveModeActive(active: Boolean) {
    _uiState.update {
      it.copy(
        isFloatingLiveModeActive = active,
        isGeminiLiveOpen = if (active) false else it.isGeminiLiveOpen,
        isGeminiLiveMinimized = false,
      )
    }
  }

  fun onMarkScreenRecordingAwaitingConsent() {
    screenRecordingManager?.markAwaitingConsent()
  }

  fun onScreenRecordingConsentCancelled() {
    screenRecordingManager?.markCancelled("Screen recording permission cancelled.")
  }

  fun onDismissScreenRecording() {
    screenRecordingManager?.resetToIdle()
  }

  fun onConsumePendingScreenRecordingServiceAction() {
    _uiState.update { it.copy(pendingScreenRecordingServiceAction = null) }
  }

  private fun handleRoutedAgentCommand(
    rawInput: String,
    intent: AgentCommandIntent,
  ): Boolean {
    return when (intent) {
      is AgentCommandIntent.StartScreenRecording -> {
        screenRecordingManager?.requestConfirmation()
        _uiState.update {
          it.copy(
            inputText = "",
            statusToastMessage = "Review screen recording prompt to grant OS capture consent.",
          )
        }
        true
      }
      AgentCommandIntent.StopScreenRecording -> {
        _uiState.update {
          it.copy(
            inputText = "",
            pendingScreenRecordingServiceAction = "STOP",
            statusToastMessage = "Stopping & saving screen recording…",
          )
        }
        true
      }
      AgentCommandIntent.CancelScreenRecording -> {
        _uiState.update {
          it.copy(
            inputText = "",
            pendingScreenRecordingServiceAction = "CANCEL",
            statusToastMessage = "Cancelling screen recording…",
          )
        }
        true
      }
      AgentCommandIntent.StartScreenSelection -> {
        _uiState.update {
          it.copy(
            inputText = "",
            pendingStartScreenSelection = true,
            statusToastMessage = "Starting screen area selection…",
          )
        }
        true
      }
      AgentCommandIntent.StartScreenShare -> {
        _uiState.update {
          it.copy(
            inputText = "",
            statusToastMessage = "Tap Screen Share or Live Share pill to grant screen capture consent.",
          )
        }
        true
      }
      AgentCommandIntent.StopScreenShare -> {
        onStopScreenShare()
        _uiState.update {
          it.copy(
            inputText = "",
            statusToastMessage = "Screen sharing stopped.",
          )
        }
        true
      }
      is AgentCommandIntent.AnalyzeCurrentScreen -> {
        val active =
          _uiState.value.screenShareStatus != ScreenShareStatus.STOPPED &&
            _uiState.value.screenShareStatus != ScreenShareStatus.REQUESTING_CONSENT
        if (!active) {
          _uiState.update {
            it.copy(
              inputText = "",
              statusToastMessage = "Screen sharing is OFF. Enable Share Screen first to analyze the screen.",
            )
          }
          true
        } else {
          false
        }
      }
      is AgentCommandIntent.UnsupportedSilentToggle -> {
        val convId = _uiState.value.activeConversationId
        _uiState.update {
          it.copy(
            inputText = "",
            statusToastMessage = intent.explanation,
          )
        }
        viewModelScope.launch {
          val now = System.currentTimeMillis()
          chatRepository.upsertMessage(
            DomainMessage(
              id = "msg_${UUID.randomUUID()}",
              conversationId = convId,
              isUser = true,
              text = rawInput,
              timestampMs = now,
            )
          )
          intent.fallbackSettingsQuery?.let { deviceActionExecutor?.executeNaturalCommand(it) }
          chatRepository.upsertMessage(
            DomainMessage(
              id = "msg_${UUID.randomUUID()}",
              conversationId = convId,
              isUser = false,
              text = intent.explanation,
              timestampMs = now + 1,
              modelUsed = "Device Agent",
            )
          )
        }
        true
      }
      is AgentCommandIntent.OpenTargetOrSetting -> {
        val convId = _uiState.value.activeConversationId
        _uiState.update { it.copy(inputText = "") }
        viewModelScope.launch {
          val now = System.currentTimeMillis()
          val res = deviceActionExecutor?.executeNaturalCommand(intent.command)
          chatRepository.upsertMessage(
            DomainMessage(
              id = "msg_${UUID.randomUUID()}",
              conversationId = convId,
              isUser = true,
              text = rawInput,
              timestampMs = now,
            )
          )
          if (res != null) {
            chatRepository.upsertMessage(
              DomainMessage(
                id = "msg_${UUID.randomUUID()}",
                conversationId = convId,
                isUser = false,
                text = res.verifiedDetail,
                timestampMs = now + 1,
                modelUsed = "Device Agent",
              )
            )
          }
        }
        true
      }
      is AgentCommandIntent.ExecuteAgentTask -> {
        val convId = _uiState.value.activeConversationId
        _uiState.update { it.copy(inputText = "") }
        viewModelScope.launch {
          val now = System.currentTimeMillis()
          val report = systemAgentOrchestrator?.executeAgentTask(intent.rawCommand)
          if (report != null) {
            chatRepository.upsertMessage(
              DomainMessage(
                id = "msg_${UUID.randomUUID()}",
                conversationId = convId,
                isUser = true,
                text = rawInput,
                timestampMs = now,
              )
            )
            chatRepository.upsertMessage(
              DomainMessage(
                id = "msg_${UUID.randomUUID()}",
                conversationId = convId,
                isUser = false,
                text = report.summaryText,
                timestampMs = now + 1,
                modelUsed = "Device Agent",
              )
            )
          }
        }
        true
      }
      is AgentCommandIntent.GenerateImage -> false
    }
  }

  fun onToggleGeminiLive(open: Boolean) {
    if (open) {
      liveSessionStartedAtMs = System.currentTimeMillis()
      liveSessionTurnsCount = 0
      silentRetryCount = 0
      pendingOpenLiveAfterPermission = false
      val prefs = _uiState.value.voicePreferences
      val allowInterrupt = _uiState.value.privacyPerformanceConfig.allowVoiceInterruption
      if (!_uiState.value.hasAudioPermission) {
        liveVoiceSessionManager?.onFailed("Microphone permission is required for Live Voice.")
        _uiState.update {
          it.copy(
            isGeminiLiveOpen = false,
            isUsingGeminiLiveSocket = false,
            statusToastMessage = "Microphone permission is required to start Live Voice.",
          )
        }
        return
      }
      assistantSessionStateManager?.activateFromTrigger(
        edge = EdgeActivationEdge.BOTTOM,
        expandPanel = false,
      )
      voiceController.playActivationChime(prefs)
      val canUseLiveSocket =
        geminiLiveVoiceEngine != null &&
          geminiLiveVoiceEngine.isFirebaseAvailable() &&
          _uiState.value.hasAudioPermission
      if (canUseLiveSocket) {
        _uiState.update {
          it.copy(
            isGeminiLiveOpen = true,
            isGeminiLiveMinimized = false,
            isLiveVoicePaused = false,
            isLiveVoiceMuted = false,
            isUsingGeminiLiveSocket = true,
            liveAssistantTranscript = "",
          )
        }
        geminiLiveVoiceEngine.startSession(
          preferences = prefs,
          allowInterruptions = allowInterrupt,
          onFallbackRequired = { reason ->
            viewModelScope.launch {
              _uiState.update { current ->
                current.copy(
                  isUsingGeminiLiveSocket = false,
                  statusToastMessage = "Switched to voice fallback: $reason",
                )
              }
              if (_uiState.value.isGeminiLiveOpen && _uiState.value.hasAudioPermission && !_uiState.value.isLiveVoicePaused) {
                voiceController.interruptSpeakingAndListen(prefs.preferredLanguage)
              }
            }
          },
        )
        return
      } else {
        liveVoiceSessionManager?.onBeginSession(prefs, allowInterrupt)
        voiceController.interruptSpeakingAndListen(prefs.preferredLanguage)
      }
      _uiState.update {
        it.copy(
          isGeminiLiveOpen = true,
          isGeminiLiveMinimized = false,
          isLiveVoicePaused = false,
          isLiveVoiceMuted = false,
          isUsingGeminiLiveSocket = false,
          liveAssistantTranscript = "",
        )
      }
    } else {
      silentRetryCount = 0
      pendingOpenLiveAfterPermission = false
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
      geminiLiveVoiceEngine?.stopAndClose(
        keepScreenShare = _uiState.value.screenShareStatus != ScreenShareStatus.STOPPED
      )
      liveVoiceSessionManager?.onEnded(
        keepScreenShare = _uiState.value.screenShareStatus != ScreenShareStatus.STOPPED
      )
      voiceController.stopListening()
      voiceController.stopSpeaking()
      assistantSessionStateManager?.onEndLiveAssistant(
        keepScreenShare = _uiState.value.screenShareStatus != ScreenShareStatus.STOPPED
      )
      _uiState.update {
        it.copy(
          isGeminiLiveOpen = false,
          isGeminiLiveMinimized = false,
          isLiveVoicePaused = false,
          isLiveVoiceMuted = false,
          isUsingGeminiLiveSocket = false,
          liveAssistantTranscript = "",
        )
      }
    }
  }

  fun onTogglePauseLiveVoice() {
    val state = _uiState.value
    if (!state.isGeminiLiveOpen) return
    val nextPaused = !state.isLiveVoicePaused
    silentRetryCount = 0
    if (state.isUsingGeminiLiveSocket && geminiLiveVoiceEngine != null) {
      if (nextPaused) {
        geminiLiveVoiceEngine.pauseSession()
      } else {
        geminiLiveVoiceEngine.resumeSession()
      }
    } else {
      if (nextPaused) {
        voiceController.stopListening()
        voiceController.stopSpeaking()
      } else if (state.hasAudioPermission && !state.isLiveVoiceMuted && !state.isGenerating) {
        voiceController.startListening(state.voicePreferences.preferredLanguage)
      }
      assistantSessionStateManager?.onPausedChanged(nextPaused)
    }
    _uiState.update { it.copy(isLiveVoicePaused = nextPaused) }
  }

  fun onToggleMuteLiveVoice() {
    val state = _uiState.value
    val nextMuted = !state.isLiveVoiceMuted
    if (state.isUsingGeminiLiveSocket && geminiLiveVoiceEngine != null) {
      geminiLiveVoiceEngine.setMuted(nextMuted)
    } else {
      if (nextMuted) {
        voiceController.stopListening()
      } else if (state.isGeminiLiveOpen && state.hasAudioPermission && !state.isLiveVoicePaused && !state.isGenerating) {
        voiceController.startListening(state.voicePreferences.preferredLanguage)
      }
    }
    _uiState.update { it.copy(isLiveVoiceMuted = nextMuted) }
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
    val cmd = agentCommandRouter?.classify(clean)
    if (cmd != null && handleRoutedAgentCommand(clean, cmd)) {
      return
    }
    val screenShareActive =
      _uiState.value.screenShareStatus != ScreenShareStatus.STOPPED &&
        _uiState.value.screenShareStatus != ScreenShareStatus.REQUESTING_CONSENT
    if (!screenShareActive && _uiState.value.isUsingGeminiLiveSocket && geminiLiveVoiceEngine != null) {
      geminiLiveVoiceEngine.sendFollowUpText(clean)
      return
    }
    viewModelScope.launch {
      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = clean,
        attachments = _uiState.value.pendingAttachments,
      )
    }
  }

  fun onToggleVoiceDialog(open: Boolean) {
    // Large VoiceInputDialog removed: keep state false and route requests to inline Chat Bar dictation
    if (!open) {
      onStopInlineDictation()
    }
    _uiState.update { it.copy(isVoiceDialogOpen = false) }
  }

  fun onAudioPermissionResult(granted: Boolean) {
    val shouldOpenLive = pendingOpenLiveAfterPermission
    pendingOpenLiveAfterPermission = false
    _uiState.update {
      it.copy(
        hasAudioPermission = granted,
        isVoiceDialogOpen = false,
        inlineDictationError =
          if (!granted && !shouldOpenLive) "Microphone permission denied. Grant mic permission to dictate."
          else null,
        statusToastMessage =
          if (!granted) "Microphone permission denied. Voice input cannot start without mic access."
          else it.statusToastMessage,
      )
    }
    if (granted) {
      if (shouldOpenLive) {
        onToggleGeminiLive(true)
      } else {
        onStartInlineDictation()
      }
    }
    onRefreshPermissions()
  }

  fun onStartInlineDictation() {
    val state = _uiState.value
    if (state.isInlineDictationSessionActive &&
      state.voiceState is VoiceState.Listening &&
      !state.isInlineDictationPaused
    ) {
      onPauseInlineDictation()
      return
    }
    dictationCommittedPrefix = state.inputText
    _uiState.update {
      it.copy(
        isVoiceDialogOpen = false,
        isInlineDictationSessionActive = true,
        isInlineDictationPaused = false,
        inlineDictationError = null,
      )
    }
    silentRetryCount = 0
    voiceController.playActivationChime(state.voicePreferences)
    voiceController.interruptSpeakingAndListen(state.voicePreferences.preferredLanguage)
  }

  fun onPauseInlineDictation() {
    voiceController.stopListening()
    dictationCommittedPrefix = _uiState.value.inputText
    _uiState.update {
      it.copy(
        isInlineDictationSessionActive = true,
        isInlineDictationPaused = true,
      )
    }
  }

  fun onStopInlineDictation() {
    voiceController.stopListening()
    dictationCommittedPrefix = _uiState.value.inputText
    _uiState.update {
      it.copy(
        isVoiceDialogOpen = false,
        isInlineDictationSessionActive = false,
        isInlineDictationPaused = false,
        inlineDictationError = null,
      )
    }
  }

  fun onRetryInlineDictation() {
    val state = _uiState.value
    dictationCommittedPrefix = state.inputText
    _uiState.update {
      it.copy(
        isInlineDictationSessionActive = true,
        isInlineDictationPaused = false,
        inlineDictationError = null,
      )
    }
    silentRetryCount = 0
    voiceController.interruptSpeakingAndListen(state.voicePreferences.preferredLanguage)
  }

  fun onStartVoiceListening() {
    silentRetryCount = 0
    if (_uiState.value.isUsingGeminiLiveSocket && geminiLiveVoiceEngine != null) {
      geminiLiveVoiceEngine.resumeSession()
      return
    }
    voiceController.playActivationChime(_uiState.value.voicePreferences)
    if (_uiState.value.privacyPerformanceConfig.allowVoiceInterruption) {
      voiceController.interruptSpeakingAndListen(_uiState.value.voicePreferences.preferredLanguage)
    } else {
      voiceController.startListening(_uiState.value.voicePreferences.preferredLanguage)
    }
  }

  fun onStopVoiceListening() {
    if (_uiState.value.isUsingGeminiLiveSocket && geminiLiveVoiceEngine != null) {
      geminiLiveVoiceEngine.pauseSession()
      return
    }
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
    val voices = voiceController.getAvailableVoices(language)
    _uiState.update {
      it.copy(
        voicePreferences = it.voicePreferences.copy(preferredLanguage = language),
        availableTtsVoices = voices,
      )
    }
    persistCurrentSettings()
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
    persistCurrentSettings()
  }

  fun onUpdateAutoSpeak(enabled: Boolean) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(autoSpeakResponses = enabled))
    }
    persistCurrentSettings()
  }

  fun onUpdateSpeechRate(rate: Float) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(speechRate = rate.coerceIn(0.7f, 1.6f)))
    }
    persistCurrentSettings()
  }

  fun onUpdateVoicePitch(pitch: Float) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(pitch = pitch.coerceIn(0.75f, 1.25f)))
    }
    persistCurrentSettings()
  }

  fun onUpdateVoiceVolume(volume: Float) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(volume = volume.coerceIn(0.2f, 1.0f)))
    }
    persistCurrentSettings()
  }

  fun onUpdateAutoLanguageDetection(enabled: Boolean) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(autoLanguageDetection = enabled))
    }
    persistCurrentSettings()
  }

  fun onUpdatePreferredVoiceName(voiceName: String) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(preferredVoiceName = voiceName))
    }
    persistCurrentSettings()
  }

  fun onRefreshAvailableVoices() {
    val voices = voiceController.getAvailableVoices(_uiState.value.voicePreferences.preferredLanguage)
    _uiState.update { it.copy(availableTtsVoices = voices) }
  }

  fun onPreviewVoice() {
    voiceController.previewVoice(_uiState.value.voicePreferences)
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
          geminiTestReport = null,
          groqTestReport = null,
          discoveredGeminiModels = emptyList(),
          discoveredGroqModels = emptyList(),
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

  fun onTestGeminiConnection() {
    val service = oneTapApiActivationService ?: return
    if (_uiState.value.isTestingGemini) return
    val currentSettings = buildCurrentAppSettingsSnapshot()
    val hasMic = _uiState.value.hasAudioPermission
    viewModelScope.launch {
      _uiState.update { it.copy(isTestingGemini = true, settingsErrorMessage = null) }
      val (report, updatedSettings) =
        service.testGeminiConnection(_uiState.value.geminiApiKey, currentSettings, hasMic)
      if (updatedSettings != null) {
        settingsRepository.saveSettings(updatedSettings)
      }
      _uiState.update {
        it.copy(
          isTestingGemini = false,
          geminiTestReport = report,
          discoveredGeminiModels = report.discoveredModels,
          statusToastMessage = report.message,
        )
      }
    }
  }

  fun onTestGroqConnection() {
    val service = oneTapApiActivationService ?: return
    if (_uiState.value.isTestingGroq) return
    val currentSettings = buildCurrentAppSettingsSnapshot()
    val hasMic = _uiState.value.hasAudioPermission
    viewModelScope.launch {
      _uiState.update { it.copy(isTestingGroq = true, settingsErrorMessage = null) }
      val (report, updatedSettings) =
        service.testGroqConnection(_uiState.value.groqApiKey, currentSettings, hasMic)
      if (updatedSettings != null) {
        settingsRepository.saveSettings(updatedSettings)
      }
      _uiState.update {
        it.copy(
          isTestingGroq = false,
          groqTestReport = report,
          discoveredGroqModels = report.discoveredModels,
          statusToastMessage = report.message,
        )
      }
    }
  }

  fun onSelectDiscoveredGeminiModel(modelId: String) {
    val clean = modelId.trim()
    if (clean.isBlank()) return
    _uiState.update {
      it.copy(
        hydrogenBackingModelId = clean,
        statusToastMessage = "Selected Gemini model: $clean",
      )
    }
    persistCurrentSettings()
  }

  fun onSelectDiscoveredGroqModel(modelId: String) {
    val clean = modelId.trim()
    if (clean.isBlank()) return
    _uiState.update {
      it.copy(
        groqReasoningModelId = clean,
        statusToastMessage = "Selected Groq model: $clean",
      )
    }
    persistCurrentSettings()
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
      cloudflareAccountId = state.cloudflareAccountId,
      cloudflareApiToken = state.cloudflareApiToken,
      strictFreeImageOnly = state.strictFreeImageOnly,
      allowPaidImageFallbackApproved = state.allowPaidImageFallbackApproved,
      themeModeName = state.themeMode.name,
      voicePreferences = state.voicePreferences,
      selectedModelOptionName = state.selectedModel.name,
      operatingModeName = state.operatingMode.name,
      hydrogenBackingModelId = state.hydrogenBackingModelId,
      groqReasoningModelId = state.groqReasoningModelId,
      groqHelperGuardModelId = state.groqHelperGuardModelId,
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
    val snapshot = buildCurrentAppSettingsSnapshot()
    viewModelScope.launch {
      settingsRepository.saveSettings(snapshot)
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

  fun onFollowUpSuggestionClick(suggestion: String) {
    if (suggestion == "Open in Creative Image Studio") {
      val latestPath =
        _uiState.value.messages
          .asReversed()
          .flatMap { it.blocks }
          .filterIsInstance<ResponseBlock.GeneratedImageBlock>()
          .firstOrNull { it.image.filePath.isNotBlank() }
          ?.image
          ?.filePath
      openImageStudio(
        featureType = if (latestPath != null) ImageEditFeatureType.CREATIVE_AI_EDIT else ImageEditFeatureType.TEXT_TO_IMAGE,
        initialImagePath = latestPath,
      )
      return
    }
    _uiState.update { it.copy(inputText = suggestion) }
    onSendMessage()
  }

  private fun buildFollowUpSuggestions(
    userPrompt: String,
    assistantText: String,
    blocks: List<ResponseBlock>,
  ): List<String> {
    if (assistantText.isBlank() && blocks.isEmpty()) return emptyList()
    return when {
      blocks.any { it is ResponseBlock.GeneratedImageBlock } ->
        listOf(
          "Remix this image with cinematic neon lighting",
          "Open in Creative Image Studio",
          "Generate a 16:9 widescreen variation",
        )
      blocks.any { it is ResponseBlock.SourceCitations } ->
        listOf(
          "Summarize key findings in 3 bullets",
          "What are the main pros and cons?",
          "Share the latest developments",
        )
      assistantText.contains("```") || userPrompt.contains("code", ignoreCase = true) ->
        listOf(
          "Add unit tests for this code",
          "Explain edge cases & complexity",
          "Refactor for cleaner readability",
        )
      else ->
        listOf(
          "Explain this in simpler terms",
          "Give a practical example",
          "What should I do next?",
        )
    }
  }

  fun openImageStudio(
    featureType: ImageEditFeatureType = ImageEditFeatureType.TEXT_TO_IMAGE,
    initialImagePath: String? = null,
    initialPrompt: String? = null,
  ) {
    _uiState.update { state ->
      val source =
        initialImagePath
          ?: state.imageStudioState.sourceUriOrPath
          ?: state.pendingAttachments.filterIsInstance<AttachmentPreview.PickedPhotoUri>().lastOrNull()?.uri
      state.copy(
        isImageToolsSheetOpen = false,
        isMoreToolsSheetOpen = false,
        pendingNavigateToImageStudio = true,
        imageStudioState =
          state.imageStudioState.copy(
            activeFeature = featureType,
            sourceUriOrPath = source,
            promptInput = initialPrompt ?: state.imageStudioState.promptInput,
            errorMessage = null,
          ),
      )
    }
  }

  fun onConsumeNavigateToImageStudio() {
    _uiState.update { it.copy(pendingNavigateToImageStudio = false) }
  }

  fun onSelectStudioFeature(feature: ImageEditFeatureType) {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(activeFeature = feature, errorMessage = null))
    }
  }

  fun onUpdateStudioPrompt(prompt: String) {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(promptInput = prompt, errorMessage = null))
    }
  }

  fun onUpdateStudioAspectRatio(label: String) {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(aspectRatioLabel = label))
    }
  }

  fun onUpdateStudioSeed(seed: String) {
    val clean = seed.filter { ch -> ch.isDigit() }.take(12)
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(seedText = clean))
    }
  }

  fun onToggleStudioEnhancePrompt(enabled: Boolean) {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(enhancePromptEnabled = enabled))
    }
  }

  fun onEnhanceStudioPrompt() {
    val facade = imageStudioFacade ?: return
    val raw = _uiState.value.imageStudioState.promptInput.trim()
    if (raw.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(imageStudioState = it.imageStudioState.copy(isEnhancingPrompt = true, errorMessage = null)) }
      val improved = facade.enhancePrompt(raw, buildCurrentAppSettingsSnapshot())
      _uiState.update {
        it.copy(
          imageStudioState =
            it.imageStudioState.copy(
              promptInput = improved.ifBlank { raw },
              isEnhancingPrompt = false,
            )
        )
      }
    }
  }

  fun onStudioPickSourcePhoto(uri: String) {
    _uiState.update {
      it.copy(
        imageStudioState =
          it.imageStudioState.copy(
            sourceUriOrPath = uri,
            beforeFilePath = uri,
            outputFilePath = null,
            maskPaths = emptyList(),
            errorMessage = null,
          )
      )
    }
  }

  fun onStudioPickCustomBackdrop(uri: String) {
    _uiState.update { state ->
      val studio = state.imageStudioState
      state.copy(
        imageStudioState =
          studio.copy(
            customBackdropUri = uri,
            posterSpec = studio.posterSpec.copy(backgroundPhotoPath = uri),
          )
      )
    }
  }

  fun onStudioGenerateFluxImage(remix: Boolean = false) {
    val facade = imageStudioFacade ?: return
    val currentStudio = _uiState.value.imageStudioState
    val nextSeed = if (remix) ((System.currentTimeMillis() % 900_000L) + 100_000L) else null
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          imageStudioState =
            currentStudio.copy(
              isBusy = true,
              status = ImageGenerationStatus.PREPARING_PROMPT,
              errorMessage = null,
              seedText = nextSeed?.toString() ?: currentStudio.seedText,
            )
        )
      }
      val result =
        facade.generateTextToImage(
          state = _uiState.value.imageStudioState,
          settings = buildCurrentAppSettingsSnapshot(),
          overrideSeed = nextSeed,
        ) { stage ->
          _uiState.update { it.copy(imageStudioState = it.imageStudioState.copy(status = stage)) }
        }
      result.fold(
        onSuccess = { generated ->
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.COMPLETED,
                  outputFilePath = generated.filePath,
                  beforeFilePath = null,
                  outputHasTransparency = generated.hasTransparency,
                  outputSourceBadge = generated.sourceBadge,
                  outputWidthPx = generated.width,
                  outputHeightPx = generated.height,
                  errorMessage = null,
                )
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.FAILED,
                  errorMessage = err.message ?: "Failed to generate image.",
                )
            )
          }
        },
      )
    }
  }

  fun onUpdateStudioAdjustments(adjustments: ImageAdjustmentParams) {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(adjustments = adjustments))
    }
  }

  fun onRotateStudioImage() {
    _uiState.update {
      val next = (it.imageStudioState.rotateDegrees + 90f) % 360f
      it.copy(imageStudioState = it.imageStudioState.copy(rotateDegrees = next))
    }
  }

  fun onFlipStudioImage() {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(flipHorizontal = !it.imageStudioState.flipHorizontal))
    }
  }

  fun onApplyStudioPhotoEnhance() {
    val facade = imageStudioFacade ?: return
    viewModelScope.launch {
      _uiState.update {
        it.copy(imageStudioState = it.imageStudioState.copy(isBusy = true, status = ImageGenerationStatus.GENERATING, errorMessage = null))
      }
      val res = facade.runPhotoEnhance(_uiState.value.imageStudioState)
      res.fold(
        onSuccess = { file ->
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.COMPLETED,
                  beforeFilePath = it.imageStudioState.sourceUriOrPath,
                  outputFilePath = file.absolutePath,
                  outputHasTransparency = false,
                  outputSourceBadge = ImageProcessingSource.LOCAL_EDITING.badgeLabel,
                )
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(imageStudioState = it.imageStudioState.copy(isBusy = false, status = ImageGenerationStatus.FAILED, errorMessage = err.message))
          }
        },
      )
    }
  }

  fun onUpdateStudioPortraitSettings(bokeh: Float, feather: Float, style: BackgroundReplaceStyle) {
    _uiState.update {
      it.copy(
        imageStudioState =
          it.imageStudioState.copy(
            portraitBokehAmount = bokeh,
            edgeFeatherPx = feather,
            backgroundStyle = style,
          )
      )
    }
  }

  fun onApplyStudioPortraitOrBg() {
    val facade = imageStudioFacade ?: return
    viewModelScope.launch {
      _uiState.update {
        it.copy(imageStudioState = it.imageStudioState.copy(isBusy = true, status = ImageGenerationStatus.GENERATING, errorMessage = null))
      }
      val res = facade.runSubjectIsolation(_uiState.value.imageStudioState)
      res.fold(
        onSuccess = { cutout ->
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.COMPLETED,
                  beforeFilePath = it.imageStudioState.sourceUriOrPath,
                  outputFilePath = cutout.outputFilePath,
                  outputHasTransparency = cutout.hasTransparency,
                  outputWidthPx = cutout.width,
                  outputHeightPx = cutout.height,
                  outputSourceBadge =
                    if (cutout.usedMlKitSegmentation) "ML Kit Subject AI (On-Device)" else ImageProcessingSource.LOCAL_EDITING.badgeLabel,
                )
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(imageStudioState = it.imageStudioState.copy(isBusy = false, status = ImageGenerationStatus.FAILED, errorMessage = err.message))
          }
        },
      )
    }
  }

  fun onUpdateStudioBrushRadius(radius: Float) {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(maskBrushRadiusNorm = radius))
    }
  }

  fun onAddStudioMaskStroke(points: List<NormPoint>) {
    if (points.isEmpty()) return
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(maskPaths = it.imageStudioState.maskPaths + listOf(points)))
    }
  }

  fun onUndoStudioMaskStroke() {
    _uiState.update {
      val dropped = it.imageStudioState.maskPaths.dropLast(1)
      it.copy(imageStudioState = it.imageStudioState.copy(maskPaths = dropped))
    }
  }

  fun onClearStudioMask() {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(maskPaths = emptyList()))
    }
  }

  fun onApplyStudioObjectRemove() {
    val facade = imageStudioFacade ?: return
    viewModelScope.launch {
      _uiState.update {
        it.copy(imageStudioState = it.imageStudioState.copy(isBusy = true, status = ImageGenerationStatus.PREPARING_PROMPT, errorMessage = null))
      }
      val res =
        facade.runObjectMaskInpaint(
          state = _uiState.value.imageStudioState,
          settings = buildCurrentAppSettingsSnapshot(),
        ) { stage ->
          _uiState.update { it.copy(imageStudioState = it.imageStudioState.copy(status = stage)) }
        }
      res.fold(
        onSuccess = { generated ->
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.COMPLETED,
                  beforeFilePath = generated.beforeFilePath,
                  outputFilePath = generated.filePath,
                  outputWidthPx = generated.width,
                  outputHeightPx = generated.height,
                  outputSourceBadge = generated.sourceBadge,
                )
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(imageStudioState = it.imageStudioState.copy(isBusy = false, status = ImageGenerationStatus.FAILED, errorMessage = err.message))
          }
        },
      )
    }
  }

  fun onApplyStudioCreativeEdit() {
    val facade = imageStudioFacade ?: return
    viewModelScope.launch {
      _uiState.update {
        it.copy(imageStudioState = it.imageStudioState.copy(isBusy = true, status = ImageGenerationStatus.PREPARING_PROMPT, errorMessage = null))
      }
      val (plan, res) =
        facade.runCreativeEditOrPlan(
          state = _uiState.value.imageStudioState,
          settings = buildCurrentAppSettingsSnapshot(),
        ) { stage ->
          _uiState.update { it.copy(imageStudioState = it.imageStudioState.copy(status = stage)) }
        }
      res.fold(
        onSuccess = { generated ->
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.COMPLETED,
                  lastEditPlan = plan,
                  beforeFilePath = generated.beforeFilePath,
                  outputFilePath = generated.filePath,
                  outputHasTransparency = generated.hasTransparency,
                  outputWidthPx = generated.width,
                  outputHeightPx = generated.height,
                  outputSourceBadge = generated.sourceBadge,
                )
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.FAILED,
                  lastEditPlan = plan,
                  errorMessage = err.message,
                )
            )
          }
        },
      )
    }
  }

  fun onUpdatePosterSpec(spec: PosterDesignSpec) {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(posterSpec = spec))
    }
  }

  fun onRenderStudioPoster() {
    val facade = imageStudioFacade ?: return
    viewModelScope.launch {
      _uiState.update {
        it.copy(imageStudioState = it.imageStudioState.copy(isBusy = true, status = ImageGenerationStatus.GENERATING, errorMessage = null))
      }
      val res = facade.renderPoster(_uiState.value.imageStudioState)
      res.fold(
        onSuccess = { file ->
          val ratio = _uiState.value.imageStudioState.posterSpec.aspectRatio
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.COMPLETED,
                  beforeFilePath = null,
                  outputFilePath = file.absolutePath,
                  outputHasTransparency = false,
                  outputWidthPx = ratio.widthPx,
                  outputHeightPx = ratio.heightPx,
                  outputSourceBadge = ImageProcessingSource.LOCAL_TEXT_COMPOSITING.badgeLabel,
                )
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(imageStudioState = it.imageStudioState.copy(isBusy = false, status = ImageGenerationStatus.FAILED, errorMessage = err.message))
          }
        },
      )
    }
  }

  fun onStudioSetBatchPhotos(uris: List<String>) {
    val facade = imageStudioFacade ?: return
    val labeled = uris.mapIndexed { idx, uri -> uri to "Photo ${idx + 1}" }
    facade.batchProcessor.setPhotos(labeled)
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(collageUris = uris, errorMessage = null))
    }
  }

  fun onStudioStartBatch(operation: ImageEditFeatureType) {
    val facade = imageStudioFacade ?: return
    val studio = _uiState.value.imageStudioState
    facade.batchProcessor.startBatch(
      featureType = operation,
      adjustments = studio.adjustments,
      backgroundStyle = studio.backgroundStyle,
    )
  }

  fun onStudioCancelBatch() {
    imageStudioFacade?.batchProcessor?.cancelAll()
  }

  fun onStudioRetryBatchItem(itemId: String, operation: ImageEditFeatureType) {
    val studio = _uiState.value.imageStudioState
    imageStudioFacade?.batchProcessor?.retryItem(
      itemId = itemId,
      featureType = operation,
      adjustments = studio.adjustments,
      backgroundStyle = studio.backgroundStyle,
    )
  }

  fun onStudioSaveBatchItem(itemId: String) {
    val facade = imageStudioFacade ?: return
    viewModelScope.launch {
      val msg = facade.batchProcessor.exportItemToGallery(itemId).fold(
        onSuccess = { it },
        onFailure = { it.message ?: "Unable to export batch photo." },
      )
      _uiState.update { it.copy(statusToastMessage = msg) }
    }
  }

  fun onStudioCreateCollage() {
    val facade = imageStudioFacade ?: return
    val uris =
      _uiState.value.batchImageItems.map { it.sourceUri }.ifEmpty { _uiState.value.imageStudioState.collageUris }
    if (uris.size < 2) return
    viewModelScope.launch {
      _uiState.update {
        it.copy(imageStudioState = it.imageStudioState.copy(isBusy = true, status = ImageGenerationStatus.GENERATING, errorMessage = null))
      }
      facade.createGridCollage(uris).fold(
        onSuccess = { file ->
          _uiState.update {
            it.copy(
              imageStudioState =
                it.imageStudioState.copy(
                  isBusy = false,
                  status = ImageGenerationStatus.COMPLETED,
                  beforeFilePath = null,
                  outputFilePath = file.absolutePath,
                  outputWidthPx = 1080,
                  outputHeightPx = 1080,
                  outputSourceBadge = ImageProcessingSource.LOCAL_EDITING.badgeLabel,
                )
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(imageStudioState = it.imageStudioState.copy(isBusy = false, status = ImageGenerationStatus.FAILED, errorMessage = err.message))
          }
        },
      )
    }
  }

  fun onSaveStudioOutputToGallery() {
    val studio = _uiState.value.imageStudioState
    val path = studio.outputFilePath ?: return
    val facade = imageStudioFacade ?: return
    viewModelScope.launch {
      val toast =
        facade.saveToGallery(
          filePath = path,
          title = studio.promptInput.ifBlank { "Mousomi Studio Artwork" },
          hasTransparency = studio.outputHasTransparency,
        ).fold(
          onSuccess = { it },
          onFailure = { it.message ?: "Unable to save output." },
        )
      _uiState.update { it.copy(statusToastMessage = toast) }
    }
  }

  fun onShareStudioOutput(context: Context) {
    val path = _uiState.value.imageStudioState.outputFilePath ?: return
    val facade = imageStudioFacade
    runCatching {
      val uri =
        facade?.getShareableUri(path)
          ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
      val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = "image/png"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, _uiState.value.imageStudioState.promptInput)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      context.startActivity(Intent.createChooser(intent, "Share Studio Artwork"))
    }.onFailure { err ->
      _uiState.update { it.copy(statusToastMessage = "Unable to share image: ${err.localizedMessage}") }
    }
  }

  fun onOpenStudioOutputInChat() {
    val path = _uiState.value.imageStudioState.outputFilePath ?: return
    val uriStr = Uri.fromFile(File(path)).toString()
    onAttachPickedPhoto(uriStr)
  }

  fun onSetStudioLightboxPath(path: String?) {
    _uiState.update {
      it.copy(imageStudioState = it.imageStudioState.copy(lightboxImagePath = path))
    }
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
