package com.example.ui.model

import com.example.core.accessibility.AccessibilityActionResult
import com.example.core.accessibility.AccessibilityWorkflowState
import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.EdgeActivationSignal
import com.example.core.device.PendingPhoneAction
import com.example.core.device.PendingSocialAction
import com.example.core.screen.AttachedVisualItem
import com.example.core.screen.ImageEditState
import com.example.core.screen.ScreenSelectionUiState
import com.example.core.voice.LiveConnectionState
import com.example.core.voice.LiveSessionPhase
import com.example.core.voice.OrbReactiveFrame
import com.example.core.voice.VoiceState
import com.example.domain.model.ActiveEditImageTarget
import com.example.domain.model.AgentBudgetConfig
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AgentTaskLogItem
import com.example.domain.model.AiResponseState
import com.example.domain.model.AutomationTaskRecord
import com.example.domain.model.BackgroundReplaceStyle
import com.example.domain.model.BatchImageItemState
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.DomainConversation
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EditPlan
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageAdjustmentParams
import com.example.domain.model.ImageEditFeatureType
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.ImageVersionRecord
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.MemoryRecord
import com.example.domain.model.ModelSubstitutionPrompt
import com.example.domain.model.NeonComposerState
import com.example.domain.model.NormPoint
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.NotificationSummaryRecord
import com.example.domain.model.OneTapActivationResult
import com.example.domain.model.OperatingMode
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.PermissionCategoryItem
import com.example.domain.model.PosterAspectRatio
import com.example.domain.model.PosterDesignSpec
import com.example.domain.model.PosterFontChoice
import com.example.domain.model.PosterTemplatePreset
import com.example.domain.model.PosterTextLayer
import com.example.domain.model.PrivacyAndPerformanceConfig
import com.example.domain.model.ReminderEntry
import com.example.domain.model.ResearchActivitySnapshot
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ScreenFrameRecord
import com.example.domain.model.ScreenIntelligenceConfig
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.TokenUsage
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoiceAudioMetrics
import com.example.domain.model.VoicePreferences
import com.example.ui.theme.AppThemeMode

enum class InputMode {
  CHAT,
  TASK,
}

enum class AiModelOption(
  val modelId: String,
  val shortLabel: String,
  val displayName: String,
  val description: String,
) {
  GEMINI_4_HYDROGEN(
    modelId = VerifiedModelCatalog.HYDROGEN_SLOT_ID,
    shortLabel = "4 Hydrogen",
    displayName = "Gemini 4 Hydrogen",
    description = "Advanced Intelligence & Multimodal Reasoning · Search Grounding",
  ),
  GEMINI_IMAGE_STUDIO(
    modelId = VerifiedModelCatalog.IMAGE_STUDIO_SLOT_ID,
    shortLabel = "Image Studio",
    displayName = "Gemini Image Studio",
    description = "AI Image Generation & Creative Editing · Free-First Router",
  ),
  GEMINI_FLASH_LITE(
    modelId = VerifiedModelCatalog.FLASH_LITE_SLOT_ID,
    shortLabel = "Flash-Lite",
    displayName = "Gemini Flash-Lite",
    description = "Ultra-Fast Intelligence · Low Latency Everyday Tasks",
  ),
  GEMINI_LIVE_AUDIO(
    modelId = VerifiedModelCatalog.LIVE_AUDIO_SLOT_ID,
    shortLabel = "Live Audio",
    displayName = "Gemini Live Audio",
    description = "Real-Time Voice Intelligence · Duplex Mic & Natural Speech",
  ),
  GROQ_HIGH_SPEED_REASONING(
    modelId = VerifiedModelCatalog.GROQ_REASONING_SLOT_ID,
    shortLabel = "Groq Reason",
    displayName = "Groq High-Speed Reasoning",
    description = "Fast Reasoning & Coding (Qwen 3.8 27B / GPT-OSS 120B)",
  ),
  GROQ_ULTRA_FAST_HELPER_GUARD(
    modelId = VerifiedModelCatalog.GROQ_HELPER_GUARD_SLOT_ID,
    shortLabel = "Helper & Guard",
    displayName = "Groq Ultra-Fast Helper & Guard",
    description = "Ultra-Fast Helper & Prompt Safety (GPT-OSS 20B / Safeguard)",
  ),
  MOUSOMI_AUTO(
    modelId = VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID,
    shortLabel = "Auto",
    displayName = "Mousomi Auto",
    description = "Smart Automatic Router · Displays the actual active backing model",
  ),
}

enum class SuggestionIconType {
  PENCIL,
  VIDEO_CAPTIONS,
  WALLPAPER_SPARKLES,
}

data class SuggestionItem(
  val id: String,
  val promptText: String,
  val iconType: SuggestionIconType,
)

sealed class AttachmentPreview {
  data object SampleInlineArtwork : AttachmentPreview()

  data class PickedPhotoUri(val uri: String) : AttachmentPreview()

  data class MediaFile(val item: PendingMediaItem) : AttachmentPreview()
}

data class ChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val timestampLabel: String,
  val timestampMs: Long = System.currentTimeMillis(),
  val attachments: List<AttachmentPreview> = emptyList(),
  val modelUsed: String? = null,
  val categoryLabel: String? = null,
  val tokenUsage: TokenUsage? = null,
  val isError: Boolean = false,
  val isStreaming: Boolean = false,
  val blocks: List<ResponseBlock> = emptyList(),
) {
  val imageAttachment: AttachmentPreview?
    get() = attachments.firstOrNull()
}

data class ImageStudioUiState(
  val activeFeature: ImageEditFeatureType = ImageEditFeatureType.TEXT_TO_IMAGE,
  val promptInput: String = "",
  val sourceUriOrPath: String? = null,
  val sourceWidthPx: Int = 1024,
  val sourceHeightPx: Int = 1024,
  val collageUris: List<String> = emptyList(),
  val outputFilePath: String? = null,
  val beforeFilePath: String? = null,
  val outputHasTransparency: Boolean = false,
  val outputSourceBadge: String? = null,
  val outputWidthPx: Int = 0,
  val outputHeightPx: Int = 0,
  val isBusy: Boolean = false,
  val status: ImageGenerationStatus? = null,
  val errorMessage: String? = null,
  val adjustments: ImageAdjustmentParams = ImageAdjustmentParams(),
  val rotateDegrees: Float = 0f,
  val flipHorizontal: Boolean = false,
  val portraitBokehAmount: Float = 14f,
  val backgroundStyle: BackgroundReplaceStyle = BackgroundReplaceStyle.TRANSPARENT_PNG,
  val edgeFeatherPx: Float = 2.5f,
  val customBackdropUri: String? = null,
  val maskPaths: List<List<NormPoint>> = emptyList(),
  val maskBrushRadiusNorm: Float = 0.055f,
  val posterSpec: PosterDesignSpec =
    PosterDesignSpec(
      aspectRatio = PosterAspectRatio.SQUARE_1_1,
      template = PosterTemplatePreset.POETICS_MIDNIGHT,
      layers =
        listOf(
          PosterTextLayer(
            id = "headline",
            text = "মৌসুমী এআই স্টুডিও",
            fontChoice = PosterFontChoice.HIND_SILIGURI,
            fontSizeSp = 46f,
            yPositionFraction = 0.42f,
          ),
          PosterTextLayer(
            id = "subtitle",
            text = "Crystal-clear Bengali & English typography",
            fontChoice = PosterFontChoice.OUTFIT_CLEAN,
            fontSizeSp = 24f,
            yPositionFraction = 0.62f,
            backdropOpacity = 0.16f,
          ),
        ),
    ),
  val lastEditPlan: EditPlan? = null,
)

data class MousomiUiState(
  val activeConversationId: String = "conv_default",
  val conversations: List<DomainConversation> = emptyList(),
  val messages: List<ChatMessage> = emptyList(),
  val inputText: String = "",
  val editingMessageId: String? = null,
  val inputMode: InputMode = InputMode.CHAT,
  val operatingMode: OperatingMode = OperatingMode.NORMAL_CHAT,
  val selectedModel: AiModelOption = AiModelOption.MOUSOMI_AUTO,
  val resolvedAutoModelName: String? = null,
  val pendingAttachments: List<AttachmentPreview> = emptyList(),
  val responseState: AiResponseState = AiResponseState.Idle,
  val activeRoutingReason: String? = null,
  val themeMode: AppThemeMode = AppThemeMode.DARK,
  val connectionMode: ConnectionMode = ConnectionMode.DIRECT_DEV_KEYS,
  val proxyBaseUrl: String = "",
  val geminiApiKey: String = "",
  val groqApiKey: String = "",
  val cloudflareAccountId: String = "",
  val cloudflareApiToken: String = "",
  val strictFreeImageOnly: Boolean = true,
  val allowPaidImageFallbackApproved: Boolean = false,
  val hydrogenBackingModelId: String = VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID,
  val groqReasoningModelId: String = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
  val groqHelperGuardModelId: String = VerifiedModelCatalog.DEFAULT_GROQ_HELPER_MODEL_ID,
  val imageModelId: String = VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
  val externalSearchUrl: String = "",
  val externalSearchApiKey: String = "",
  val analyticsSandboxEnabled: Boolean = false,
  val composerGlowIntensity: Float = 0.85f,
  val voicePreferences: VoicePreferences = VoicePreferences(),
  val voiceState: VoiceState = VoiceState.Idle,
  val voiceRmsNormalized: Float = 0f,
  val settingsErrorMessage: String? = null,
  val isModelMenuOpen: Boolean = false,
  val isAttachmentSheetOpen: Boolean = false,
  val isProfileDialogOpen: Boolean = false,
  val isGeminiLiveOpen: Boolean = false,
  val isGeminiLiveMinimized: Boolean = false,
  val isLiveVoicePaused: Boolean = false,
  val isLiveVoiceMuted: Boolean = false,
  val isVoiceDialogOpen: Boolean = false,
  val isInlineDictationSessionActive: Boolean = false,
  val isInlineDictationPaused: Boolean = false,
  val inlineDictationError: String? = null,
  val isDrawingOverlayOpen: Boolean = false,
  val isQuickNoteDialogOpen: Boolean = false,
  val hasAudioPermission: Boolean = false,
  val edgeLightConfig: EdgeLightConfig = EdgeLightConfig(),
  val floatingOrbConfig: FloatingOrbConfig = FloatingOrbConfig(),
  val screenIntelligenceConfig: ScreenIntelligenceConfig = ScreenIntelligenceConfig(),
  val screenShareStatus: ScreenShareStatus = ScreenShareStatus.STOPPED,
  val capturedScreenFrames: List<ScreenFrameRecord> = emptyList(),
  val screenShareStatusMessage: String? = null,
  val agentBudgetConfig: AgentBudgetConfig = AgentBudgetConfig(),
  val activeAgentSteps: List<AgentStepPlan> = emptyList(),
  val agentTaskHistory: List<AgentTaskLogItem> = emptyList(),
  val memories: List<MemoryRecord> = emptyList(),
  val memoryStatusMessage: String? = null,
  val notificationConfig: NotificationIntelligenceConfig = NotificationIntelligenceConfig(),
  val notificationSummaries: List<NotificationSummaryRecord> = emptyList(),
  val notificationPulseActive: Boolean = false,
  val automationHistory: List<AutomationTaskRecord> = emptyList(),
  val privacyPerformanceConfig: PrivacyAndPerformanceConfig = PrivacyAndPerformanceConfig(),
  val isSystemPowerSave: Boolean = false,
  val activeEditTarget: ActiveEditImageTarget? = null,
  val drawingTargetImage: GeneratedImageResult? = null,
  val imageVersionsByRoot: Map<String, List<ImageVersionRecord>> = emptyMap(),
  val permissionCategories: List<PermissionCategoryItem> = emptyList(),
  val statusToastMessage: String? = null,
  val voiceAudioMetrics: VoiceAudioMetrics = VoiceAudioMetrics.SILENT,
  val reminders: List<ReminderEntry> = emptyList(),
  val managedTasks: List<ManagedTaskItem> = emptyList(),
  val deviceActionLogs: List<DeviceActionResult> = emptyList(),
  val installedApps: List<InstalledAppInfo> = emptyList(),
  val appSearchQuery: String = "",
  val requestedHubTab: Int = 0,
  val oneTapApiKeyInput: String = "",
  val isActivatingApi: Boolean = false,
  val activationResult: OneTapActivationResult = OneTapActivationResult(),
  val isImageToolsSheetOpen: Boolean = false,
  val isMoreToolsSheetOpen: Boolean = false,
  val isCompletedPulseActive: Boolean = false,
  val pendingNavigateToLens: Boolean = false,
  val pendingNavigateToImageStudio: Boolean = false,
  val authSession: com.example.domain.auth.AuthUserSession = com.example.domain.auth.AuthUserSession(),
  val screenSelectionState: ScreenSelectionUiState = ScreenSelectionUiState(),
  val attachedVisualsByUri: Map<String, AttachedVisualItem> = emptyMap(),
  val activeImageEditState: ImageEditState? = null,
  val shouldRequestComposerFocus: Boolean = false,
  val pendingStartScreenSelection: Boolean = false,
  val researchActivity: ResearchActivitySnapshot? = null,
  val isResearchPanelExpanded: Boolean = true,
  val pendingSubstitutionPrompt: ModelSubstitutionPrompt? = null,
  val imageStudioState: ImageStudioUiState = ImageStudioUiState(),
  val batchImageItems: List<BatchImageItemState> = emptyList(),
  val isBatchRunning: Boolean = false,
  val assistantSessionPhase: AssistantSessionPhase = AssistantSessionPhase.INACTIVE,
  val edgeActivationSignal: EdgeActivationSignal = EdgeActivationSignal(),
  val liveAssistantTranscript: String = "",
  val isUsingGeminiLiveSocket: Boolean = false,
  val liveSessionPhase: LiveSessionPhase = LiveSessionPhase.IDLE,
  val liveConnectionState: LiveConnectionState = LiveConnectionState.Disconnected,
  val liveElapsedSeconds: Long = 0L,
  val orbReactiveFrame: OrbReactiveFrame = OrbReactiveFrame.IDLE,
  val pendingPhoneAction: PendingPhoneAction? = null,
  val pendingSocialAction: PendingSocialAction? = null,
  val screenRecordingState: com.example.core.screen.ScreenRecordingUiState =
    com.example.core.screen.ScreenRecordingUiState(),
  val isFloatingLiveModeActive: Boolean = false,
  val pendingScreenRecordingServiceAction: String? = null,
  val accessibilityState: AccessibilityWorkflowState = AccessibilityWorkflowState(),
  val pendingAccessibilityAction: AccessibilityActionResult? = null,
) {
  val pendingAttachment: AttachmentPreview?
    get() = pendingAttachments.firstOrNull()

  val isGenerating: Boolean
    get() =
      responseState is AiResponseState.Loading ||
        responseState is AiResponseState.Thinking ||
        responseState is AiResponseState.Searching ||
        responseState is AiResponseState.Generating ||
        responseState is AiResponseState.Streaming ||
        researchActivity?.isActive == true

  val isAnyLiveSessionActive: Boolean
    get() =
      isGenerating ||
        isGeminiLiveOpen ||
        voiceState is VoiceState.Listening ||
        voiceState is VoiceState.Speaking ||
        screenShareStatus != ScreenShareStatus.STOPPED ||
        screenSelectionState.isActive ||
        imageStudioState.isBusy ||
        isBatchRunning ||
        accessibilityState.isWorkflowRunning

  val hasImageContext: Boolean
    get() =
      activeEditTarget != null ||
        pendingAttachments.any {
          it is AttachmentPreview.SampleInlineArtwork || it is AttachmentPreview.PickedPhotoUri
        } ||
        messages.any { msg ->
          msg.blocks.any { it is ResponseBlock.GeneratedImageBlock } ||
            msg.attachments.any {
              it is AttachmentPreview.PickedPhotoUri || it is AttachmentPreview.SampleInlineArtwork
            }
        }

  val neonComposerState: NeonComposerState
    get() {
      val terminalImageStates =
        setOf(
          ImageGenerationStatus.COMPLETED,
          ImageGenerationStatus.FAILED,
          ImageGenerationStatus.QUOTA_EXHAUSTED,
          ImageGenerationStatus.CANCELLED,
        )
      val hasGeneratedImgInProgress =
        imageStudioState.isBusy ||
          messages.lastOrNull()?.blocks?.any {
            it is ResponseBlock.GeneratedImageBlock && it.image.status !in terminalImageStates
          } == true
      return when {
        voiceState is VoiceState.Listening -> NeonComposerState.VOICE_ACTIVE
        voiceState is VoiceState.Speaking -> NeonComposerState.AI_SPEAKING
        hasGeneratedImgInProgress -> NeonComposerState.IMAGE_GENERATING
        responseState is AiResponseState.Error -> NeonComposerState.ERROR
        isGenerating -> NeonComposerState.AI_THINKING
        isCompletedPulseActive && responseState is AiResponseState.Completed -> NeonComposerState.COMPLETED
        inputText.isNotBlank() -> NeonComposerState.TYPING
        else -> NeonComposerState.IDLE
      }
    }
}
