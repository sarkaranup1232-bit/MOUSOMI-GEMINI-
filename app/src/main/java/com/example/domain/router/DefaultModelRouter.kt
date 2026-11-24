package com.example.domain.router

import com.example.ai.firebase.FirebaseAiLogicService
import com.example.domain.model.AiModelSpec
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.ModelSubstitutionPrompt
import com.example.domain.model.OperatingMode
import com.example.domain.model.ProviderType
import com.example.domain.model.RoutingDecision
import com.example.domain.model.TaskCategory
import com.example.domain.model.VerifiedModelCatalog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultModelRouter @Inject constructor(
  private val firebaseAiLogicService: FirebaseAiLogicService? = null,
) : ModelRouter {

  constructor() : this(firebaseAiLogicService = null)

  override fun classifyTask(prompt: String, hasImages: Boolean): TaskCategory {
    if (hasImages) return TaskCategory.IMAGE_ANALYSIS

    val q = prompt.lowercase()
    return when {
      codingKeywords.any { it in q } || "```" in q -> TaskCategory.CODING
      dataKeywords.any { it in q } -> TaskCategory.DATA_ANALYSIS
      documentKeywords.any { it in q } -> TaskCategory.DOCUMENT_ANALYSIS
      researchKeywords.any { it in q } -> TaskCategory.RESEARCH_REQUESTS
      reasoningKeywords.any { it in q } || q.length > 260 -> TaskCategory.COMPLEX_REASONING
      knowledgeKeywords.any { it in q } -> TaskCategory.GENERAL_KNOWLEDGE
      else -> TaskCategory.CASUAL_CONVERSATION
    }
  }

  /**
   * Checks if the user's explicitly selected model lacks a requested capability or provider credentials,
   * returning a substitution confirmation prompt instead of silently swapping the model when interactive
   * user consent is desired. Returns null if the model is compatible or if Mousomi Auto is active.
   */
  fun checkSubstitutionNeeded(
    prompt: String,
    hasImages: Boolean,
    operatingMode: OperatingMode,
    requestedSlotOrModelId: String?,
    settings: AppSettings,
  ): ModelSubstitutionPrompt? {
    val req = requestedSlotOrModelId?.trim().orEmpty()
    if (req.isEmpty() || req == AUTO_MODEL_ID || req == VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID || req == "MOUSOMI_AUTO") {
      return null
    }
    val explicitSpec = resolveExplicitSpec(req, settings) ?: return null
    val useProxy = settings.connectionMode == ConnectionMode.BACKEND_PROXY
    val hasFirebaseAi = firebaseAiLogicService?.isAvailable() == true
    val hasGemini = useProxy || settings.geminiApiKey.isNotBlank() || hasFirebaseAi
    val hasGroq = useProxy || settings.groqApiKey.isNotBlank()

    val hydrogenSpec = VerifiedModelCatalog.createHydrogenSpec(settings.hydrogenBackingModelId)
    val groqSpec = VerifiedModelCatalog.createGroqReasoningSpec(settings.groqReasoningModelId)

    if (hasImages && !explicitSpec.capabilities.supportsVision && hasGemini) {
      return ModelSubstitutionPrompt(
        title = "Vision Not Supported on ${explicitSpec.displayName}",
        message =
          "${explicitSpec.displayName} is text-only and cannot inspect your attached image(s). Switch to ${hydrogenSpec.displayName} for this request?",
        requestedModelId = explicitSpec.id,
        suggestedModelId = hydrogenSpec.selectorSlotId,
        suggestedModelDisplayName = hydrogenSpec.displayName,
        pendingPrompt = prompt,
        pendingMode = operatingMode,
      )
    }

    if ((operatingMode == OperatingMode.GOOGLE_AI_SEARCH ||
        operatingMode == OperatingMode.DEEP_RESEARCH ||
        operatingMode == OperatingMode.LATEST_NEWS) &&
      !explicitSpec.capabilities.supportsGoogleSearchGrounding &&
      !hasGemini
    ) {
      return ModelSubstitutionPrompt(
        title = "Live Web Search Requires Gemini Grounding",
        message =
          "${operatingMode.label} needs Google Search Grounding (provided by Gemini). Configure a Gemini key or switch to Normal Chat / Analysis to continue with ${explicitSpec.displayName}.",
        requestedModelId = explicitSpec.id,
        suggestedModelId = hydrogenSpec.selectorSlotId,
        suggestedModelDisplayName = hydrogenSpec.displayName,
        pendingPrompt = prompt,
        pendingMode = operatingMode,
      )
    }

    if (explicitSpec.provider == ProviderType.GROQ && !hasGroq && hasGemini) {
      return ModelSubstitutionPrompt(
        title = "Groq Not Configured",
        message =
          "Groq API key is missing for ${explicitSpec.displayName}. Switch to ${hydrogenSpec.displayName} instead?",
        requestedModelId = explicitSpec.id,
        suggestedModelId = hydrogenSpec.selectorSlotId,
        suggestedModelDisplayName = hydrogenSpec.displayName,
        pendingPrompt = prompt,
        pendingMode = operatingMode,
      )
    }

    if (explicitSpec.provider == ProviderType.GEMINI && !hasGemini && hasGroq && !hasImages) {
      return ModelSubstitutionPrompt(
        title = "Gemini Unavailable",
        message =
          "Gemini connection is currently unavailable for ${explicitSpec.displayName}. Use ${groqSpec.displayName} instead?",
        requestedModelId = explicitSpec.id,
        suggestedModelId = groqSpec.selectorSlotId,
        suggestedModelDisplayName = groqSpec.displayName,
        pendingPrompt = prompt,
        pendingMode = operatingMode,
      )
    }

    return null
  }

  fun routeWithOperatingMode(
    prompt: String,
    hasImages: Boolean,
    imageCount: Int,
    requestedModelId: String?,
    settings: AppSettings,
    operatingMode: OperatingMode = OperatingMode.NORMAL_CHAT,
  ): Result<RoutingDecision> {
    val effectiveSettings =
      if (settings.operatingModeName == operatingMode.name) settings
      else settings.copy(operatingModeName = operatingMode.name)
    val baseResult = route(prompt, hasImages, imageCount, requestedModelId, effectiveSettings)
    return baseResult.map { decision ->
      decision.copy(operatingMode = operatingMode)
    }
  }

  override fun route(
    prompt: String,
    hasImages: Boolean,
    imageCount: Int,
    requestedModelId: String?,
    settings: AppSettings,
  ): Result<RoutingDecision> {
    val category = classifyTask(prompt, hasImages)
    val useProxy = settings.connectionMode == ConnectionMode.BACKEND_PROXY

    if (useProxy && settings.proxyBaseUrl.isBlank()) {
      return Result.failure(
        IllegalStateException(
          "Backend Proxy mode is active, but no HTTPS Proxy URL is configured in Settings."
        )
      )
    }

    val hasFirebaseAi = firebaseAiLogicService?.isAvailable() == true
    val hasGemini = useProxy || settings.geminiApiKey.isNotBlank() || hasFirebaseAi
    val hasGroq = useProxy || settings.groqApiKey.isNotBlank()

    if (!hasGemini && !hasGroq) {
      return Result.failure(
        IllegalStateException(
          "No API key or HTTPS backend proxy configured. Open Settings to add a Gemini or Groq key."
        )
      )
    }

    val hydrogenSpec = VerifiedModelCatalog.createHydrogenSpec(settings.hydrogenBackingModelId)
    val imageSpec = VerifiedModelCatalog.createImageStudioSpec(settings.imageModelId)
    val flashLiteSpec = VerifiedModelCatalog.geminiFlashLite
    val liveAudioSpec = VerifiedModelCatalog.geminiLiveAudio
    val groqReasoningSpec = VerifiedModelCatalog.createGroqReasoningSpec(settings.groqReasoningModelId)
    val groqHelperSpec = VerifiedModelCatalog.createGroqHelperGuardSpec(settings.groqHelperGuardModelId)

    val req = requestedModelId?.trim().orEmpty()
    val isMousomiAuto =
      req.isEmpty() ||
        req == AUTO_MODEL_ID ||
        req == VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID ||
        req == "MOUSOMI_AUTO"

    if (!isMousomiAuto) {
      val explicitSpec = resolveExplicitSpec(req, settings)
      if (explicitSpec != null) {
        if (hasImages && !explicitSpec.capabilities.supportsVision) {
          if (hasGemini) {
            return Result.success(
              RoutingDecision(
                selectedModel = hydrogenSpec,
                category = category,
                reason =
                  "${explicitSpec.displayName} is text-only; routed to ${hydrogenSpec.displayName} for verified vision.",
                useProxy = useProxy,
              )
            )
          }
          return Result.failure(
            IllegalStateException(
              "${explicitSpec.displayName} does not support image/video analysis, and no Gemini vision key/proxy is configured."
            )
          )
        }

        if (imageCount > 1 && !explicitSpec.capabilities.supportsMultiImage) {
          return Result.failure(
            IllegalStateException(
              "${explicitSpec.displayName} does not support multiple simultaneous image attachments."
            )
          )
        }

        val providerReady =
          useProxy ||
            (explicitSpec.provider == ProviderType.GEMINI && hasGemini) ||
            (explicitSpec.provider == ProviderType.GROQ && hasGroq)

        if (providerReady) {
          return Result.success(
            RoutingDecision(
              selectedModel = explicitSpec,
              category = category,
              reason = "Selected model: ${explicitSpec.displayName} (${explicitSpec.id})",
              useProxy = useProxy,
            )
          )
        }

        if (!hasImages && hasGroq && explicitSpec.provider == ProviderType.GEMINI) {
          return Result.success(
            RoutingDecision(
              selectedModel = groqReasoningSpec,
              category = category,
              reason =
                "Gemini key missing; routed to ${groqReasoningSpec.displayName} (${groqReasoningSpec.id}).",
              useProxy = useProxy,
            )
          )
        }
        if (hasGemini && explicitSpec.provider == ProviderType.GROQ) {
          return Result.success(
            RoutingDecision(
              selectedModel = hydrogenSpec,
              category = category,
              reason = "Groq key missing; routed to ${hydrogenSpec.displayName}.",
              useProxy = useProxy,
            )
          )
        }

        val missingProvider =
          if (explicitSpec.provider == ProviderType.GEMINI) "Google Gemini" else "Groq"
        return Result.failure(
          IllegalStateException(
            "$missingProvider API key is missing in Settings for ${explicitSpec.displayName}."
          )
        )
      }
    }

    // Mousomi Auto intelligent routing across the 7-model ecosystem
    val operatingMode =
      runCatching { OperatingMode.valueOf(settings.operatingModeName) }
        .getOrDefault(OperatingMode.NORMAL_CHAT)

    val preferredOrder: List<AiModelSpec> =
      when {
        hasImages -> listOf(hydrogenSpec, flashLiteSpec, imageSpec)
        operatingMode == OperatingMode.DEEP_RESEARCH ||
          operatingMode == OperatingMode.GOOGLE_AI_SEARCH ||
          operatingMode == OperatingMode.LATEST_NEWS ->
          listOf(hydrogenSpec, flashLiteSpec, groqReasoningSpec)
        operatingMode == OperatingMode.ANALYSIS_THINK ||
          operatingMode == OperatingMode.TASK_MODE ->
          listOf(hydrogenSpec, groqReasoningSpec, flashLiteSpec, groqHelperSpec)
        category == TaskCategory.COMPLEX_REASONING ||
          category == TaskCategory.CODING ||
          category == TaskCategory.DOCUMENT_ANALYSIS ||
          category == TaskCategory.DATA_ANALYSIS ->
          listOf(hydrogenSpec, groqReasoningSpec, flashLiteSpec, groqHelperSpec)
        category == TaskCategory.RESEARCH_REQUESTS ||
          category == TaskCategory.GENERAL_KNOWLEDGE ->
          listOf(hydrogenSpec, flashLiteSpec, groqReasoningSpec, VerifiedModelCatalog.groqLlama70b)
        category == TaskCategory.CASUAL_CONVERSATION ->
          if (hasGroq && !hasGemini) {
            listOf(groqHelperSpec, groqReasoningSpec, VerifiedModelCatalog.groqLlama8bInstant)
          } else {
            listOf(flashLiteSpec, hydrogenSpec, groqHelperSpec, groqReasoningSpec)
          }
        else -> listOf(hydrogenSpec, flashLiteSpec, liveAudioSpec, groqReasoningSpec, groqHelperSpec)
      }

    val chosen =
      preferredOrder.firstOrNull { spec ->
        val hasCreds =
          useProxy ||
            (spec.provider == ProviderType.GEMINI && hasGemini) ||
            (spec.provider == ProviderType.GROQ && hasGroq)
        val supportsVisionOk = !hasImages || spec.capabilities.supportsVision
        val supportsMultiOk = imageCount <= 1 || spec.capabilities.supportsMultiImage
        hasCreds && supportsVisionOk && supportsMultiOk
      }
        ?: return Result.failure(
          IllegalStateException(
            if (hasImages) {
              "Image/video understanding requires a Gemini API key or HTTPS proxy configured in Settings."
            } else {
              "No compatible model available for current credentials."
            }
          )
        )

    return Result.success(
      RoutingDecision(
        selectedModel = chosen,
        category = category,
        reason = "Mousomi Auto (${category.label}) → ${chosen.displayName} (${chosen.id})",
        useProxy = useProxy,
        resolvedFromAuto = true,
        operatingMode = operatingMode,
      )
    )
  }

  private fun resolveExplicitSpec(req: String, settings: AppSettings): AiModelSpec? {
    val hydrogenSpec = VerifiedModelCatalog.createHydrogenSpec(settings.hydrogenBackingModelId)
    val imageSpec = VerifiedModelCatalog.createImageStudioSpec(settings.imageModelId)
    val groqReasoningSpec = VerifiedModelCatalog.createGroqReasoningSpec(settings.groqReasoningModelId)
    val groqHelperSpec = VerifiedModelCatalog.createGroqHelperGuardSpec(settings.groqHelperGuardModelId)
    return when (req) {
      VerifiedModelCatalog.HYDROGEN_SLOT_ID, "GEMINI_4_HYDROGEN", "GEMINI_PRO", hydrogenSpec.id -> hydrogenSpec
      VerifiedModelCatalog.IMAGE_STUDIO_SLOT_ID, "GEMINI_IMAGE_STUDIO", imageSpec.id,
      VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID, VerifiedModelCatalog.DEPRECATED_IMAGE_MODEL_ID -> imageSpec
      VerifiedModelCatalog.FLASH_LITE_SLOT_ID, "GEMINI_FLASH_LITE", "GEMINI_FLASH",
      VerifiedModelCatalog.DEFAULT_FLASH_LITE_MODEL_ID, VerifiedModelCatalog.LEGACY_HYDROGEN_FALLBACK_ID ->
        VerifiedModelCatalog.geminiFlashLite
      VerifiedModelCatalog.LIVE_AUDIO_SLOT_ID, "GEMINI_LIVE_AUDIO", VerifiedModelCatalog.DEFAULT_LIVE_AUDIO_MODEL_ID ->
        VerifiedModelCatalog.geminiLiveAudio
      VerifiedModelCatalog.GROQ_REASONING_SLOT_ID, "GROQ_HIGH_SPEED_REASONING", "GROQ_REASONING",
      groqReasoningSpec.id, VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID -> groqReasoningSpec
      VerifiedModelCatalog.GROQ_HELPER_GUARD_SLOT_ID, "GROQ_ULTRA_FAST_HELPER_GUARD",
      groqHelperSpec.id, VerifiedModelCatalog.DEFAULT_GROQ_HELPER_MODEL_ID, VerifiedModelCatalog.GROQ_SAFEGUARD_MODEL_ID ->
        groqHelperSpec
      VerifiedModelCatalog.groqLlama70b.id -> VerifiedModelCatalog.groqLlama70b
      VerifiedModelCatalog.groqLlama8bInstant.id -> VerifiedModelCatalog.groqLlama8bInstant
      else -> VerifiedModelCatalog.allModels.firstOrNull { it.id == req || it.selectorSlotId == req }
    }
  }

  companion object {
    const val AUTO_MODEL_ID = "auto"

    private val codingKeywords =
      listOf(
        "kotlin", "java", "python", "javascript", "typescript", "compose", "coroutine",
        "sql", "bug", "compile", "stacktrace", "refactor", "algorithm", "function", "class",
        "code", "কোড", "প্রোগ্রাম", "कोड",
      )

    private val dataKeywords =
      listOf(
        "csv", "json", "dataset", "metrics", "chart", "statistics", "table", "sql query",
        "data analysis", "correlation", "excel", "xlsx", "ডেটা", "ডাটা", "डेटा",
      )

    private val documentKeywords =
      listOf(
        "summarize", "summary", "document", "pdf", "docx", "report", "contract", "essay", "notes",
        "সারাংশ", "নথি", "सारांश", "दस्तावेज़",
      )

    private val researchKeywords =
      listOf(
        "research", "compare", "pros and cons", "deep dive", "literature", "citations",
        "গবেষণা", "तुलना", "शोध",
      )

    private val reasoningKeywords =
      listOf(
        "why", "prove", "architect", "strategy", "design system", "tradeoff", "step by step",
        "solve", "equation", "যুক্তি", "ব্যাখ্যা", "तर्क", "विश्लेषण",
      )

    private val knowledgeKeywords =
      listOf(
        "what is", "who is", "when did", "capital of", "explain", "history", "define",
        "কি", "কী", "কাকে বলে", "क्या है", "कौन",
      )
  }
}
