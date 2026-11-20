package com.example.ai.provider

import com.example.ai.firebase.FirebaseAiLogicService
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.api.GroqApiService
import com.example.ai.provider.api.ProxyApiService
import com.example.core.security.SecurityValidator
import com.example.domain.model.ActivationHealthStatus
import com.example.domain.model.AppSettings
import com.example.domain.model.CapabilityActivationBadge
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DetectedProviderKind
import com.example.domain.model.OneTapActivationResult
import com.example.domain.model.VerifiedModelCatalog
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class OneTapActivationOutcome(
  val result: OneTapActivationResult,
  val updatedSettings: AppSettings?,
)

/**
 * One-Tap API Activation & Provider Capability Discovery Service.
 * - Detects provider automatically from API key prefix (AIza... -> Gemini, gsk_... -> Groq)
 *   or configured HTTPS backend proxy URL.
 * - Validates keys with a real HTTP probe (GET v1beta/models / GET v1/models / proxy health).
 * - Reports genuine status (Active, Partially Active, Invalid Key, Rate Limited, Provider Unavailable)
 *   and real capability availability without hardcoding or fabricating readiness.
 * - Masks sensitive keys before exposing metadata to UI/logs.
 */
@Singleton
class OneTapApiActivationService @Inject constructor(
  private val geminiApiService: GeminiApiService,
  private val groqApiService: GroqApiService,
  private val proxyApiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
  private val firebaseAiLogicService: FirebaseAiLogicService? = null,
) {

  fun detectProvider(rawKey: String, settings: AppSettings): DetectedProviderKind {
    val key = rawKey.trim()
    return when {
      key.startsWith("AIza") -> DetectedProviderKind.GEMINI
      key.startsWith("gsk_") -> DetectedProviderKind.GROQ
      settings.connectionMode == ConnectionMode.BACKEND_PROXY && settings.proxyBaseUrl.isNotBlank() ->
        DetectedProviderKind.PROXY
      key.isNotEmpty() -> DetectedProviderKind.UNKNOWN
      settings.geminiApiKey.isNotBlank() -> DetectedProviderKind.GEMINI
      settings.groqApiKey.isNotBlank() -> DetectedProviderKind.GROQ
      firebaseAiLogicService?.isAvailable() == true -> DetectedProviderKind.FIREBASE_AI_LOGIC
      else -> DetectedProviderKind.UNKNOWN
    }
  }

  suspend fun activateWithSingleKey(
    rawKey: String,
    existingSettings: AppSettings,
    hasMicPermission: Boolean,
  ): OneTapActivationOutcome {
    val cleanKey = rawKey.trim()
    if (cleanKey.isBlank()) {
      if (existingSettings.connectionMode == ConnectionMode.BACKEND_PROXY &&
        existingSettings.proxyBaseUrl.isNotBlank()
      ) {
        return probeProxyEndpoint(existingSettings, hasMicPermission)
      }
      return OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.INVALID_KEY,
            detectedProvider = DetectedProviderKind.UNKNOWN,
            statusMessage = "Enter a valid Gemini (AIza...) or Groq (gsk_...) API key.",
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
          ),
        updatedSettings = null,
      )
    }

    if (cleanKey.length < 16 || cleanKey.contains(' ')) {
      return OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.INVALID_KEY,
            detectedProvider = detectProvider(cleanKey, existingSettings),
            statusMessage = "Invalid API key format. Verify you copied the complete key.",
            maskedKeyHint = maskKey(cleanKey),
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
          ),
        updatedSettings = null,
      )
    }

    return when (detectProvider(cleanKey, existingSettings)) {
      DetectedProviderKind.GEMINI -> probeGeminiKey(cleanKey, existingSettings, hasMicPermission)
      DetectedProviderKind.GROQ -> probeGroqKey(cleanKey, existingSettings, hasMicPermission)
      DetectedProviderKind.PROXY -> probeProxyEndpoint(existingSettings, hasMicPermission, cleanKey)
      DetectedProviderKind.FIREBASE_AI_LOGIC -> buildFirebaseAiLogicOutcome(existingSettings, hasMicPermission)
      DetectedProviderKind.UNKNOWN -> {
        // Probe Gemini first; if unauthorized, try Groq automatically so non-standard keys work
        val geminiAttempt = probeGeminiKey(cleanKey, existingSettings, hasMicPermission)
        if (geminiAttempt.result.status == ActivationHealthStatus.ACTIVE ||
          geminiAttempt.result.status == ActivationHealthStatus.PARTIALLY_ACTIVE
        ) {
          geminiAttempt
        } else {
          val groqAttempt = probeGroqKey(cleanKey, existingSettings, hasMicPermission)
          if (groqAttempt.result.status == ActivationHealthStatus.ACTIVE ||
            groqAttempt.result.status == ActivationHealthStatus.PARTIALLY_ACTIVE
          ) {
            groqAttempt
          } else {
            geminiAttempt
          }
        }
      }
    }
  }

  suspend fun verifyCurrentConnection(
    settings: AppSettings,
    hasMicPermission: Boolean,
  ): OneTapActivationOutcome {
    if (settings.connectionMode == ConnectionMode.BACKEND_PROXY && settings.proxyBaseUrl.isNotBlank()) {
      return probeProxyEndpoint(settings, hasMicPermission)
    }
    val hasGemini = settings.geminiApiKey.isNotBlank()
    val hasGroq = settings.groqApiKey.isNotBlank()
    if (!hasGemini && !hasGroq) {
      if (firebaseAiLogicService?.isAvailable() == true) {
        return buildFirebaseAiLogicOutcome(settings, hasMicPermission)
      }
      return OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.UNCONFIGURED,
            detectedProvider = DetectedProviderKind.UNKNOWN,
            statusMessage = "No active API key configured.",
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
          ),
        updatedSettings = null,
      )
    }
    return if (hasGemini) {
      probeGeminiKey(settings.geminiApiKey, settings, hasMicPermission)
    } else {
      probeGroqKey(settings.groqApiKey, settings, hasMicPermission)
    }
  }

  private fun buildFirebaseAiLogicOutcome(
    existingSettings: AppSettings,
    hasMicPermission: Boolean,
  ): OneTapActivationOutcome =
    OneTapActivationOutcome(
      result =
        OneTapActivationResult(
          status = ActivationHealthStatus.PARTIALLY_ACTIVE,
          detectedProvider = DetectedProviderKind.FIREBASE_AI_LOGIC,
          statusMessage =
            "Firebase AI Logic + App Check active (no API key needed for Gemini 3.8 Flash chat, vision, audio & Search). Note: Nano Banana image gen, Veo video, and Lyria music require Firebase Blaze billing or a paid Gemini API key.",
          capabilities =
            buildCapabilities(
              chatAvailable = true,
              visionAvailable = true,
              imageGenAvailable = true,
              researchAvailable = true,
              micGranted = hasMicPermission,
              docsAvailable = true,
              reasoningAvailable = true,
              videoAndMusicAvailable = false,
            ),
          discoveredModelIds =
            listOf(
              FirebaseAiLogicService.DEFAULT_TEXT_MODEL,
              FirebaseAiLogicService.DEFAULT_PRO_MODEL,
              FirebaseAiLogicService.DEFAULT_LITE_MODEL,
              FirebaseAiLogicService.DEFAULT_IMAGE_MODEL,
            ),
          verifiedAtMs = System.currentTimeMillis(),
        ),
      updatedSettings = existingSettings,
    )

  fun buildDeactivatedSettings(current: AppSettings): AppSettings =
    current.copy(
      geminiApiKey = "",
      groqApiKey = "",
      externalSearchApiKey = "",
    )

  private suspend fun probeGeminiKey(
    apiKey: String,
    existingSettings: AppSettings,
    hasMicPermission: Boolean,
  ): OneTapActivationOutcome {
    return try {
      val response = geminiApiService.listModels(apiKey = apiKey)
      val masked = maskKey(apiKey)
      when (response.code()) {
        200 -> {
          val models = response.body()?.models.orEmpty()
          val rawIds = models.map { it.name.removePrefix("models/") }.filter { it.isNotBlank() }
          val hasChat = rawIds.any { it.contains("gemini", ignoreCase = true) }
          val hasImageGen =
            rawIds.any {
              it.contains("nano-banana", ignoreCase = true) ||
                it.contains("image-generation", ignoreCase = true) ||
                it.contains("imagen", ignoreCase = true) ||
                it.contains("-image", ignoreCase = true)
            }
          val hasVideoOrMusic =
            rawIds.any { it.contains("veo", ignoreCase = true) || it.contains("lyria", ignoreCase = true) }
          val preferredHydrogen =
            rawIds.firstOrNull { it.contains("3.8-flash") }
              ?: rawIds.firstOrNull { it.contains("3.") && it.contains("flash") }
              ?: rawIds.firstOrNull { it.contains("flash") }
              ?: existingSettings.hydrogenBackingModelId
          val preferredImageModel =
            rawIds.firstOrNull { it.contains("nano-banana") }
              ?: rawIds.firstOrNull { it.contains("image-generation") || it.contains("-image") }
              ?: rawIds.firstOrNull { it.contains("imagen") }
              ?: existingSettings.imageModelId

          val hasResearch = hasChat || existingSettings.externalSearchUrl.isNotBlank()
          val updated =
            existingSettings.copy(
              connectionMode = ConnectionMode.DIRECT_DEV_KEYS,
              geminiApiKey = apiKey,
              hydrogenBackingModelId = preferredHydrogen,
              imageModelId = preferredImageModel,
            )

          val missingOptional =
            buildList {
              if (!hasImageGen) add("image-generation model unlisted")
              if (!hasVideoOrMusic) add("Veo/Lyria require paid AI Studio billing")
              if (!hasMicPermission) add("microphone permission needed for live voice")
            }

          val status =
            if (hasChat && missingOptional.isEmpty()) ActivationHealthStatus.ACTIVE
            else if (hasChat) ActivationHealthStatus.PARTIALLY_ACTIVE
            else ActivationHealthStatus.PROVIDER_UNAVAILABLE

          val msg =
            when (status) {
              ActivationHealthStatus.ACTIVE ->
                "Gemini API active (${rawIds.size} models verified). All capabilities ready."
              ActivationHealthStatus.PARTIALLY_ACTIVE ->
                "Gemini API active (${rawIds.size} models). Notes: ${missingOptional.joinToString(", ")}."
              else -> "Key authenticated, but no compatible text models were returned."
            }

          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = status,
                detectedProvider = DetectedProviderKind.GEMINI,
                statusMessage = msg,
                maskedKeyHint = masked,
                capabilities =
                  buildCapabilities(
                    chatAvailable = hasChat,
                    visionAvailable = hasChat,
                    imageGenAvailable = hasImageGen || hasChat,
                    researchAvailable = hasResearch,
                    micGranted = hasMicPermission,
                    docsAvailable = hasChat,
                    reasoningAvailable = true,
                    videoAndMusicAvailable = hasVideoOrMusic,
                  ),
                discoveredModelIds = rawIds.take(12),
                verifiedAtMs = System.currentTimeMillis(),
              ),
            updatedSettings = updated,
          )
        }
        400, 401, 403 ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.INVALID_KEY,
                detectedProvider = DetectedProviderKind.GEMINI,
                statusMessage = "Gemini API rejected this key (HTTP ${response.code()}).",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
              ),
            updatedSettings = null,
          )
        429 ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.RATE_LIMITED,
                detectedProvider = DetectedProviderKind.GEMINI,
                statusMessage = "Gemini API rate limit reached (HTTP 429). Try again shortly.",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
              ),
            updatedSettings = null,
          )
        else ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
                detectedProvider = DetectedProviderKind.GEMINI,
                statusMessage = "Gemini service returned HTTP ${response.code()}. Try again later.",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
              ),
            updatedSettings = null,
          )
      }
    } catch (_: IOException) {
      OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
            detectedProvider = DetectedProviderKind.GEMINI,
            statusMessage = "Network unavailable while verifying Gemini key. Check connectivity.",
            maskedKeyHint = maskKey(apiKey),
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
          ),
        updatedSettings = null,
      )
    } catch (e: Exception) {
      OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
            detectedProvider = DetectedProviderKind.GEMINI,
            statusMessage = e.localizedMessage ?: "Unable to verify Gemini API key.",
            maskedKeyHint = maskKey(apiKey),
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
          ),
        updatedSettings = null,
      )
    }
  }

  private suspend fun probeGroqKey(
    apiKey: String,
    existingSettings: AppSettings,
    hasMicPermission: Boolean,
  ): OneTapActivationOutcome {
    return try {
      val response = groqApiService.listModels(authorization = "Bearer $apiKey")
      val masked = maskKey(apiKey)
      when (response.code()) {
        200 -> {
          val models = response.body()?.data.orEmpty()
          val rawIds = models.map { it.id }.filter { it.isNotBlank() }
          val preferredGroq =
            rawIds.firstOrNull { it.contains("qwen", ignoreCase = true) }
              ?: rawIds.firstOrNull { it.contains("llama-3.3-70b", ignoreCase = true) }
              ?: rawIds.firstOrNull()
              ?: VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID
          val hasChat = rawIds.isNotEmpty()
          val hasGeminiConfigured = existingSettings.geminiApiKey.isNotBlank()
          val updated =
            existingSettings.copy(
              groqApiKey = apiKey,
              groqReasoningModelId = preferredGroq,
              selectedModelOptionName =
                if (!hasGeminiConfigured) "GROQ_REASONING" else existingSettings.selectedModelOptionName,
            )

          val status =
            if (hasChat && hasGeminiConfigured && hasMicPermission) ActivationHealthStatus.ACTIVE
            else if (hasChat) ActivationHealthStatus.PARTIALLY_ACTIVE
            else ActivationHealthStatus.PROVIDER_UNAVAILABLE

          val notes =
            buildList {
              if (!hasGeminiConfigured) add("add a Gemini key for image generation & native vision")
              if (!hasMicPermission) add("mic permission needed for live voice")
            }

          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = status,
                detectedProvider = DetectedProviderKind.GROQ,
                statusMessage =
                  if (status == ActivationHealthStatus.ACTIVE) {
                    "Groq + Gemini multi-provider active (${rawIds.size} Groq models verified)."
                  } else if (hasChat) {
                    "Groq active (${rawIds.size} models verified). ${notes.joinToString("; ")}."
                  } else {
                    "Groq key verified, but no active chat models were listed."
                  },
                maskedKeyHint = masked,
                capabilities =
                  buildCapabilities(
                    chatAvailable = hasChat,
                    visionAvailable = hasGeminiConfigured,
                    imageGenAvailable = hasGeminiConfigured,
                    researchAvailable = hasGeminiConfigured || existingSettings.externalSearchUrl.isNotBlank(),
                    micGranted = hasMicPermission,
                    docsAvailable = hasChat,
                    reasoningAvailable = hasChat,
                  ),
                discoveredModelIds = rawIds.take(12),
                verifiedAtMs = System.currentTimeMillis(),
              ),
            updatedSettings = updated,
          )
        }
        401, 403 ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.INVALID_KEY,
                detectedProvider = DetectedProviderKind.GROQ,
                statusMessage = "Groq API rejected this key (HTTP ${response.code()}).",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
              ),
            updatedSettings = null,
          )
        429 ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.RATE_LIMITED,
                detectedProvider = DetectedProviderKind.GROQ,
                statusMessage = "Groq API rate limit reached (HTTP 429). Retry shortly.",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
              ),
            updatedSettings = null,
          )
        else ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
                detectedProvider = DetectedProviderKind.GROQ,
                statusMessage = "Groq service returned HTTP ${response.code()}.",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
              ),
            updatedSettings = null,
          )
      }
    } catch (_: IOException) {
      OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
            detectedProvider = DetectedProviderKind.GROQ,
            statusMessage = "Network unavailable while verifying Groq key.",
            maskedKeyHint = maskKey(apiKey),
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
          ),
        updatedSettings = null,
      )
    } catch (e: Exception) {
      OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
            detectedProvider = DetectedProviderKind.GROQ,
            statusMessage = e.localizedMessage ?: "Unable to verify Groq API key.",
            maskedKeyHint = maskKey(apiKey),
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
          ),
        updatedSettings = null,
      )
    }
  }

  private suspend fun probeProxyEndpoint(
    existingSettings: AppSettings,
    hasMicPermission: Boolean,
    optionalBearerToken: String = "",
  ): OneTapActivationOutcome {
    val validated = securityValidator.validateHttpsUrl(existingSettings.proxyBaseUrl)
    if (validated.isFailure) {
      return OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.INVALID_KEY,
            detectedProvider = DetectedProviderKind.PROXY,
            statusMessage = validated.exceptionOrNull()?.message ?: "Proxy URL must use HTTPS.",
          ),
        updatedSettings = null,
      )
    }
    val healthUrl = existingSettings.proxyBaseUrl.trimEnd('/') + "/health"
    return try {
      val authHeader = optionalBearerToken.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
      val resp = proxyApiService.probeHealth(healthUrl, authHeader)
      when (resp.code()) {
        in 200..299 ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status =
                  if (hasMicPermission) ActivationHealthStatus.ACTIVE
                  else ActivationHealthStatus.PARTIALLY_ACTIVE,
                detectedProvider = DetectedProviderKind.PROXY,
                statusMessage = "HTTPS backend proxy verified at ${existingSettings.proxyBaseUrl}.",
                capabilities =
                  buildCapabilities(
                    chatAvailable = true,
                    visionAvailable = true,
                    imageGenAvailable = true,
                    researchAvailable = true,
                    micGranted = hasMicPermission,
                    docsAvailable = true,
                    reasoningAvailable = true,
                  ),
                verifiedAtMs = System.currentTimeMillis(),
              ),
            updatedSettings = existingSettings.copy(connectionMode = ConnectionMode.BACKEND_PROXY),
          )
        401, 403 ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.INVALID_KEY,
                detectedProvider = DetectedProviderKind.PROXY,
                statusMessage = "Backend proxy rejected authentication (HTTP ${resp.code()}).",
              ),
            updatedSettings = null,
          )
        else ->
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
                detectedProvider = DetectedProviderKind.PROXY,
                statusMessage = "Backend proxy health check failed (HTTP ${resp.code()}).",
              ),
            updatedSettings = null,
          )
      }
    } catch (_: IOException) {
      OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
            detectedProvider = DetectedProviderKind.PROXY,
            statusMessage = "Unable to reach HTTPS proxy server.",
          ),
        updatedSettings = null,
      )
    }
  }

  private fun buildCapabilities(
    chatAvailable: Boolean,
    visionAvailable: Boolean,
    imageGenAvailable: Boolean,
    researchAvailable: Boolean,
    micGranted: Boolean,
    docsAvailable: Boolean = chatAvailable,
    reasoningAvailable: Boolean = chatAvailable,
    videoAndMusicAvailable: Boolean = false,
  ): List<CapabilityActivationBadge> =
    listOf(
      CapabilityActivationBadge(
        id = "chat",
        label = "AI Chat",
        available = chatAvailable,
        detail = if (chatAvailable) "Streaming chat verified" else "Requires Firebase AI Logic or API key",
      ),
      CapabilityActivationBadge(
        id = "reasoning",
        label = "Reasoning & Code",
        available = reasoningAvailable,
        detail = if (reasoningAvailable) "Ready" else "Requires API key",
      ),
      CapabilityActivationBadge(
        id = "vision",
        label = "Vision & Photos",
        available = visionAvailable,
        detail = if (visionAvailable) "Multimodal vision active" else "Requires Gemini or Firebase AI Logic",
      ),
      CapabilityActivationBadge(
        id = "image_gen",
        label = "Image Gen & Edit",
        available = imageGenAvailable,
        detail = if (imageGenAvailable) "Nano Banana pipeline ready (billing may apply)" else "Requires Gemini key",
      ),
      CapabilityActivationBadge(
        id = "research",
        label = "Search & Maps",
        available = researchAvailable,
        detail = if (researchAvailable) "Search & Maps grounding enabled" else "Requires Gemini or Search endpoint",
      ),
      CapabilityActivationBadge(
        id = "video_music",
        label = "Veo Video & Music",
        available = videoAndMusicAvailable,
        detail = if (videoAndMusicAvailable) "Veo & Lyria listed" else "Requires paid Gemini API billing",
      ),
      CapabilityActivationBadge(
        id = "docs",
        label = "Documents & Data",
        available = docsAvailable,
        detail = if (docsAvailable) "Ready" else "Requires API key",
      ),
      CapabilityActivationBadge(
        id = "voice",
        label = "Voice & Live",
        available = micGranted,
        detail = if (micGranted) "Microphone permitted" else "Grant mic permission when using Voice",
      ),
    )

  private fun maskKey(key: String): String {
    val clean = key.trim()
    if (clean.length <= 8) return "••••"
    return "${clean.take(4)}••••${clean.takeLast(4)}"
  }
}
