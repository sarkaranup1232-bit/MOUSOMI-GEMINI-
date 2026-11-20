package com.example.ui.model

import com.example.core.voice.VoiceState
import com.example.domain.model.ActiveEditImageTarget
import com.example.domain.model.AgentBudgetConfig
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AgentTaskLogItem
import com.example.domain.model.AiResponseState
import com.example.domain.model.AutomationTaskRecord
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.DomainConversation
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageVersionRecord
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.MemoryRecord
import com.example.domain.model.NeonComposerState
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.NotificationSummaryRecord
import com.example.domain.model.OneTapActivationResult
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.PermissionCategoryItem
import com.example.domain.model.PrivacyAndPerformanceConfig
import com.example.domain.model.ReminderEntry
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
    description = "New Base Intelligence · Advanced AI Engine",
  ),
  GEMINI_PRO(
    modelId = VerifiedModelCatalog.geminiPro.id,
    shortLabel = "Pro",
    displayName = "Gemini Pro",
    description = "Advanced Reasoning · Coding · Complex Analysis",
  ),
  GEMINI_FLASH(
    modelId = VerifiedModelCatalog.geminiFlash.id,
    shortLabel = "Flash",
    displayName = "Gemini Flash",
    description = "Fast Responses · Everyday AI · Multimodal Tasks",
  ),
  GROQ_REASONING(
    modelId = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
    shortLabel = "Groq",
    displayName = "Groq Reasoning",
    description = "Fast Inference · Logical Reasoning · Technical Assistance",
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

data class MousomiUiState(
  val activeConversationId: String = "conv_default",
  val conversations: List<DomainConversation> = emptyList(),
  val messages: List<ChatMessage> = emptyList(),
  val inputText: String = "",
  val editingMessageId: String? = null,
  val inputMode: InputMode = InputMode.CHAT,
  val selectedModel: AiModelOption = AiModelOption.GEMINI_4_HYDROGEN,
  val pendingAttachments: List<AttachmentPreview> = emptyList(),
  val responseState: AiResponseState = AiResponseState.Idle,
  val activeRoutingReason: String? = null,
  val themeMode: AppThemeMode = AppThemeMode.DARK,
  val connectionMode: ConnectionMode = ConnectionMode.DIRECT_DEV_KEYS,
  val proxyBaseUrl: String = "",
  val geminiApiKey: String = "",
  val groqApiKey: String = "",
  val hydrogenBackingModelId: String = VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID,
  val groqReasoningModelId: String = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
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
  val isVoiceDialogOpen: Boolean = false,
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
  val authSession: com.example.domain.auth.AuthUserSession = com.example.domain.auth.AuthUserSession(),
) {
  val pendingAttachment: AttachmentPreview?
    get() = pendingAttachments.firstOrNull()

  val isGenerating: Boolean
    get() =
      responseState is AiResponseState.Loading ||
        responseState is AiResponseState.Thinking ||
        responseState is AiResponseState.Searching ||
        responseState is AiResponseState.Generating ||
        responseState is AiResponseState.Streaming

  val isAnyLiveSessionActive: Boolean
    get() =
      isGenerating ||
        isGeminiLiveOpen ||
        voiceState is VoiceState.Listening ||
        voiceState is VoiceState.Speaking ||
        screenShareStatus != ScreenShareStatus.STOPPED

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
      val hasGeneratedImgInProgress =
        messages.lastOrNull()?.blocks?.any {
          it is ResponseBlock.GeneratedImageBlock &&
            it.image.status.name !in setOf("COMPLETED", "FAILED")
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
