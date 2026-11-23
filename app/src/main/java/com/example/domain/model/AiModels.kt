package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ProviderType {
  GEMINI,
  GROQ,
  CLOUDFLARE,
  PROXY,
}

enum class OperatingMode(
  val label: String,
  val shortLabel: String,
  val description: String,
) {
  NORMAL_CHAT(
    label = "Normal Chat",
    shortLabel = "Normal",
    description = "Direct conversational AI using the active model or Mousomi Auto",
  ),
  ANALYSIS_THINK(
    label = "Analysis / Think",
    shortLabel = "Analysis",
    description = "Deeper structured reasoning budget without exposing raw internal thoughts",
  ),
  GOOGLE_AI_SEARCH(
    label = "Google AI Search",
    shortLabel = "AI Search",
    description = "Real-time grounded web search with inline clickable citations",
  ),
  DEEP_RESEARCH(
    label = "Deep Research",
    shortLabel = "Deep Research",
    description = "7-stage live multi-source research, verification, and structured report",
  ),
  TASK_MODE(
    label = "Task Mode",
    shortLabel = "Task Mode",
    description = "Structured multi-step planning, tool selection, approval gates & step execution",
  ),
}

enum class TaskCategory(val label: String) {
  CASUAL_CONVERSATION("Casual Conversation"),
  GENERAL_KNOWLEDGE("General Knowledge"),
  COMPLEX_REASONING("Complex Reasoning"),
  CODING("Coding"),
  IMAGE_ANALYSIS("Image Analysis"),
  DOCUMENT_ANALYSIS("Document Analysis"),
  RESEARCH_REQUESTS("Research"),
  DATA_ANALYSIS("Data Analysis"),
}

enum class LatencyTier {
  ULTRA_FAST,
  BALANCED,
  DEEP_REASONING,
}

enum class ResponseLanguage(val label: String, val localeTag: String) {
  AUTO("Auto (Match Prompt)", "en-US"),
  ENGLISH("English", "en-US"),
  BENGALI("বাংলা (Bengali)", "bn-BD"),
  HINDI("हिन्दी (Hindi)", "hi-IN"),
}

data class AiCapabilities(
  val supportsStreaming: Boolean,
  val supportsVision: Boolean,
  val supportsMultiImage: Boolean,
  val supportsSystemInstruction: Boolean,
  val maxContextTokens: Int,
  val supportsGoogleSearchGrounding: Boolean = false,
  val supportsAudioInput: Boolean = false,
  val supportsVideoFrameAnalysis: Boolean = supportsVision,
  val supportsImageGeneration: Boolean = false,
  val supportsLiveAudio: Boolean = false,
  val supportsPromptGuard: Boolean = false,
)

data class AiModelSpec(
  val id: String,
  val displayName: String,
  val shortLabel: String,
  val provider: ProviderType,
  val latencyTier: LatencyTier,
  val capabilities: AiCapabilities,
  val description: String,
  val selectorSlotId: String = id,
  val fallbackModelIds: List<String> = emptyList(),
)

object VerifiedModelCatalog {
  const val HYDROGEN_SLOT_ID = "gemini_4_hydrogen"
  const val IMAGE_STUDIO_SLOT_ID = "gemini_image_studio"
  const val FLASH_LITE_SLOT_ID = "gemini_flash_lite"
  const val LIVE_AUDIO_SLOT_ID = "gemini_live_audio"
  const val GROQ_REASONING_SLOT_ID = "groq_high_speed_reasoning"
  const val GROQ_HELPER_GUARD_SLOT_ID = "groq_helper_guard"
  const val MOUSOMI_AUTO_SLOT_ID = "mousomi_auto"

  const val DEFAULT_HYDROGEN_BACKING_MODEL_ID = "gemini-4-hydrogen"
  const val LEGACY_HYDROGEN_FALLBACK_ID = "gemini-3.8-flash"
  const val DEFAULT_IMAGE_MODEL_ID = "gemini-nano-banana-2.1"
  const val DEPRECATED_IMAGE_MODEL_ID = "gemini-3.1-flash-image"
  const val DEFAULT_FLASH_LITE_MODEL_ID = "gemini-3.5-flash-lite"
  const val DEFAULT_LIVE_AUDIO_MODEL_ID = "gemini-3.1-flash-live-preview"
  const val DEFAULT_GROQ_REASONING_MODEL_ID = "qwen/qwen3.8-27b"
  const val FALLBACK_GROQ_REASONING_MODEL_ID = "openai/gpt-oss-120b"
  const val FALLBACK_GROQ_MODEL_ID = "llama-3.3-70b-versatile"
  const val DEFAULT_GROQ_HELPER_MODEL_ID = "openai/gpt-oss-20b"
  const val GROQ_SAFEGUARD_MODEL_ID = "openai/gpt-oss-safeguard-20b"

  fun createHydrogenSpec(backingModelId: String = DEFAULT_HYDROGEN_BACKING_MODEL_ID): AiModelSpec {
    val cleanId = backingModelId.trim().ifBlank { DEFAULT_HYDROGEN_BACKING_MODEL_ID }
    return AiModelSpec(
      id = cleanId,
      selectorSlotId = HYDROGEN_SLOT_ID,
      displayName = "Gemini 4 Hydrogen",
      shortLabel = "4 Hydrogen",
      provider = ProviderType.GEMINI,
      latencyTier = LatencyTier.DEEP_REASONING,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = true,
          supportsMultiImage = true,
          supportsSystemInstruction = true,
          maxContextTokens = 1_048_576,
          supportsGoogleSearchGrounding = true,
          supportsAudioInput = true,
          supportsVideoFrameAnalysis = true,
        ),
      description = "Advanced Intelligence & Multimodal Reasoning · Search Grounding",
      fallbackModelIds = listOf(LEGACY_HYDROGEN_FALLBACK_ID, "gemini-2.5-flash"),
    )
  }

  fun createImageStudioSpec(imageModelId: String = DEFAULT_IMAGE_MODEL_ID): AiModelSpec {
    val cleanId = imageModelId.trim().ifBlank { DEFAULT_IMAGE_MODEL_ID }
    return AiModelSpec(
      id = cleanId,
      selectorSlotId = IMAGE_STUDIO_SLOT_ID,
      displayName = "Gemini Image Studio",
      shortLabel = "Image Studio",
      provider = ProviderType.GEMINI,
      latencyTier = LatencyTier.BALANCED,
      capabilities =
        AiCapabilities(
          supportsStreaming = false,
          supportsVision = true,
          supportsMultiImage = true,
          supportsSystemInstruction = true,
          maxContextTokens = 128_000,
          supportsImageGeneration = true,
        ),
      description = "AI Image Generation & Creative Editing · Free-First Router ($cleanId)",
      fallbackModelIds = listOf(DEPRECATED_IMAGE_MODEL_ID),
    )
  }

  val geminiFlashLite =
    AiModelSpec(
      id = DEFAULT_FLASH_LITE_MODEL_ID,
      selectorSlotId = FLASH_LITE_SLOT_ID,
      displayName = "Gemini Flash-Lite",
      shortLabel = "Flash-Lite",
      provider = ProviderType.GEMINI,
      latencyTier = LatencyTier.ULTRA_FAST,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = true,
          supportsMultiImage = true,
          supportsSystemInstruction = true,
          maxContextTokens = 1_048_576,
          supportsGoogleSearchGrounding = true,
          supportsAudioInput = true,
          supportsVideoFrameAnalysis = true,
        ),
      description = "Ultra-Fast Intelligence · Low Latency Everyday Tasks",
      fallbackModelIds = listOf("gemini-2.5-flash-lite", LEGACY_HYDROGEN_FALLBACK_ID),
    )

  val geminiLiveAudio =
    AiModelSpec(
      id = DEFAULT_LIVE_AUDIO_MODEL_ID,
      selectorSlotId = LIVE_AUDIO_SLOT_ID,
      displayName = "Gemini Live Audio",
      shortLabel = "Live Audio",
      provider = ProviderType.GEMINI,
      latencyTier = LatencyTier.ULTRA_FAST,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = true,
          supportsMultiImage = false,
          supportsSystemInstruction = true,
          maxContextTokens = 128_000,
          supportsAudioInput = true,
          supportsLiveAudio = true,
        ),
      description = "Real-Time Voice Intelligence · Duplex Mic & Natural Speech",
      fallbackModelIds = listOf(DEFAULT_HYDROGEN_BACKING_MODEL_ID),
    )

  fun createGroqReasoningSpec(modelId: String = DEFAULT_GROQ_REASONING_MODEL_ID): AiModelSpec {
    val cleanId = modelId.trim().ifBlank { DEFAULT_GROQ_REASONING_MODEL_ID }
    return AiModelSpec(
      id = cleanId,
      selectorSlotId = GROQ_REASONING_SLOT_ID,
      displayName = "Groq High-Speed Reasoning",
      shortLabel = "Groq Reason",
      provider = ProviderType.GROQ,
      latencyTier = LatencyTier.DEEP_REASONING,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = false,
          supportsMultiImage = false,
          supportsSystemInstruction = true,
          maxContextTokens = 131_072,
          supportsGoogleSearchGrounding = false,
          supportsAudioInput = false,
          supportsVideoFrameAnalysis = false,
        ),
      description = "Fast Reasoning & Coding ($cleanId / $FALLBACK_GROQ_REASONING_MODEL_ID)",
      fallbackModelIds = listOf(FALLBACK_GROQ_REASONING_MODEL_ID, FALLBACK_GROQ_MODEL_ID),
    )
  }

  fun createGroqHelperGuardSpec(modelId: String = DEFAULT_GROQ_HELPER_MODEL_ID): AiModelSpec {
    val cleanId = modelId.trim().ifBlank { DEFAULT_GROQ_HELPER_MODEL_ID }
    return AiModelSpec(
      id = cleanId,
      selectorSlotId = GROQ_HELPER_GUARD_SLOT_ID,
      displayName = "Groq Ultra-Fast Helper & Guard",
      shortLabel = "Helper & Guard",
      provider = ProviderType.GROQ,
      latencyTier = LatencyTier.ULTRA_FAST,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = false,
          supportsMultiImage = false,
          supportsSystemInstruction = true,
          maxContextTokens = 128_000,
          supportsPromptGuard = true,
        ),
      description = "Ultra-Fast Helper & Prompt Safety ($cleanId / $GROQ_SAFEGUARD_MODEL_ID)",
      fallbackModelIds = listOf(GROQ_SAFEGUARD_MODEL_ID, "llama-3.1-8b-instant"),
    )
  }

  val mousomiAuto =
    AiModelSpec(
      id = "auto",
      selectorSlotId = MOUSOMI_AUTO_SLOT_ID,
      displayName = "Mousomi Auto",
      shortLabel = "Auto",
      provider = ProviderType.GEMINI,
      latencyTier = LatencyTier.BALANCED,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = true,
          supportsMultiImage = true,
          supportsSystemInstruction = true,
          maxContextTokens = 1_048_576,
          supportsGoogleSearchGrounding = true,
          supportsAudioInput = true,
          supportsVideoFrameAnalysis = true,
          supportsImageGeneration = true,
          supportsLiveAudio = true,
        ),
      description = "Smart Automatic Router · Displays the actual active backing model",
    )

  // Preserved legacy aliases for compatibility with existing router & tests
  val geminiImageStudio: AiModelSpec = createImageStudioSpec()
  val geminiPro: AiModelSpec = createHydrogenSpec()
  val geminiFlash: AiModelSpec = geminiFlashLite
  val groqReasoning: AiModelSpec = createGroqReasoningSpec()
  val groqHelperGuard: AiModelSpec = createGroqHelperGuardSpec()

  val groqLlama70b =
    AiModelSpec(
      id = "llama-3.3-70b-versatile",
      displayName = "Groq Llama 3.3 70B",
      shortLabel = "Groq 70B",
      provider = ProviderType.GROQ,
      latencyTier = LatencyTier.BALANCED,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = false,
          supportsMultiImage = false,
          supportsSystemInstruction = true,
          maxContextTokens = 128_000,
        ),
      description = "High-speed versatile text reasoning via Groq",
    )

  val groqLlama8bInstant =
    AiModelSpec(
      id = "llama-3.1-8b-instant",
      displayName = "Groq Llama 3.1 8B",
      shortLabel = "Groq 8B",
      provider = ProviderType.GROQ,
      latencyTier = LatencyTier.ULTRA_FAST,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = false,
          supportsMultiImage = false,
          supportsSystemInstruction = true,
          maxContextTokens = 128_000,
        ),
      description = "Ultra-low-latency text chat via Groq",
    )

  fun buildSevenSelectableModels(settings: AppSettings = AppSettings()): List<AiModelSpec> =
    listOf(
      createHydrogenSpec(settings.hydrogenBackingModelId),
      createImageStudioSpec(settings.imageModelId),
      geminiFlashLite,
      geminiLiveAudio,
      createGroqReasoningSpec(settings.groqReasoningModelId),
      createGroqHelperGuardSpec(settings.groqHelperGuardModelId),
      mousomiAuto,
    )

  val sevenSelectableModels: List<AiModelSpec> = buildSevenSelectableModels()

  val allModels: List<AiModelSpec> =
    sevenSelectableModels + listOf(groqLlama70b, groqLlama8bInstant)

  fun resolveModelBySlot(slotId: String, settings: AppSettings = AppSettings()): AiModelSpec =
    when (slotId) {
      HYDROGEN_SLOT_ID -> createHydrogenSpec(settings.hydrogenBackingModelId)
      IMAGE_STUDIO_SLOT_ID -> createImageStudioSpec(settings.imageModelId)
      FLASH_LITE_SLOT_ID, "gemini_flash" -> geminiFlashLite
      LIVE_AUDIO_SLOT_ID -> geminiLiveAudio
      GROQ_REASONING_SLOT_ID, "groq_reasoning" -> createGroqReasoningSpec(settings.groqReasoningModelId)
      GROQ_HELPER_GUARD_SLOT_ID -> createGroqHelperGuardSpec(settings.groqHelperGuardModelId)
      MOUSOMI_AUTO_SLOT_ID, "auto" -> mousomiAuto
      else -> createHydrogenSpec(settings.hydrogenBackingModelId)
    }
}

data class RoutingDecision(
  val selectedModel: AiModelSpec,
  val category: TaskCategory,
  val reason: String,
  val useProxy: Boolean,
  val intelligenceIntent: IntelligenceIntent = IntelligenceIntent.NORMAL_AI,
  val resolvedFromAuto: Boolean = false,
  val operatingMode: OperatingMode = OperatingMode.NORMAL_CHAT,
)

@Serializable
data class TokenUsage(
  val promptTokens: Int = 0,
  val completionTokens: Int = 0,
  val totalTokens: Int = 0,
)

@Serializable
data class ProcessedImagePayload(
  val uriString: String,
  val mimeType: String,
  val base64Data: String,
  val width: Int,
  val height: Int,
  val byteSize: Int,
  val exifSummary: String? = null,
)

sealed interface AiResponseState {
  data object Idle : AiResponseState

  data object Loading : AiResponseState

  data class Thinking(val detail: String = "Classifying intent & routing model…") : AiResponseState

  data class Searching(val detail: String = "Gathering context & running tools…") : AiResponseState

  data class Generating(val modelName: String) : AiResponseState

  data class Streaming(
    val partialText: String,
    val modelName: String,
    val tokenUsage: TokenUsage? = null,
  ) : AiResponseState

  data class Completed(
    val fullText: String,
    val modelName: String,
    val tokenUsage: TokenUsage? = null,
  ) : AiResponseState

  data class Error(
    val message: String,
    val recoverable: Boolean = true,
  ) : AiResponseState

  data object Cancelled : AiResponseState
}

data class DomainConversation(
  val id: String,
  val title: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

data class DomainMessage(
  val id: String,
  val conversationId: String,
  val isUser: Boolean,
  val text: String,
  val timestampMs: Long,
  val imageUris: List<String> = emptyList(),
  val isSampleArtwork: Boolean = false,
  val modelUsed: String? = null,
  val categoryLabel: String? = null,
  val tokenUsage: TokenUsage? = null,
  val isError: Boolean = false,
  val blocks: List<ResponseBlock> = emptyList(),
  val attachedMediaSummaries: List<PendingMediaItem> = emptyList(),
)

enum class ConnectionMode(val label: String) {
  DIRECT_DEV_KEYS("Direct Dev Keys"),
  BACKEND_PROXY("HTTPS Backend Proxy"),
}

data class VoicePreferences(
  val preferredLanguage: ResponseLanguage = ResponseLanguage.AUTO,
  val autoSpeakResponses: Boolean = false,
  val speechRate: Float = 1.0f,
  val pitch: Float = 1.0f,
  val voiceGender: VoiceGender = VoiceGender.FEMALE,
  val activationSoundEnabled: Boolean = true,
)

data class AppSettings(
  val connectionMode: ConnectionMode = ConnectionMode.DIRECT_DEV_KEYS,
  val proxyBaseUrl: String = "",
  val geminiApiKey: String = "",
  val groqApiKey: String = "",
  val cloudflareAccountId: String = "",
  val cloudflareApiToken: String = "",
  val strictFreeImageOnly: Boolean = true,
  val allowPaidImageFallbackApproved: Boolean = false,
  val themeModeName: String = "DARK",
  val voicePreferences: VoicePreferences = VoicePreferences(),
  val selectedModelOptionName: String = "MOUSOMI_AUTO",
  val operatingModeName: String = OperatingMode.NORMAL_CHAT.name,
  val hydrogenBackingModelId: String = VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID,
  val groqReasoningModelId: String = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
  val groqHelperGuardModelId: String = VerifiedModelCatalog.DEFAULT_GROQ_HELPER_MODEL_ID,
  val imageModelId: String = VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
  val externalSearchUrl: String = "",
  val externalSearchApiKey: String = "",
  val analyticsSandboxEnabled: Boolean = false,
  val composerGlowIntensity: Float = 0.85f,
  val edgeLightConfig: EdgeLightConfig = EdgeLightConfig(),
  val floatingOrbConfig: FloatingOrbConfig = FloatingOrbConfig(),
  val screenIntelligenceConfig: ScreenIntelligenceConfig = ScreenIntelligenceConfig(),
  val agentBudgetConfig: AgentBudgetConfig = AgentBudgetConfig(),
  val notificationConfig: NotificationIntelligenceConfig = NotificationIntelligenceConfig(),
  val privacyPerformanceConfig: PrivacyAndPerformanceConfig = PrivacyAndPerformanceConfig(),
)
