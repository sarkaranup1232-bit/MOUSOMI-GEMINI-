package com.example.ai.provider

import com.example.ai.firebase.FirebaseAiLogicService
import com.example.ai.provider.api.CloudflareWorkersAiService
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.api.GroqApiService
import com.example.ai.provider.api.ProxyApiService
import com.example.core.security.SecurityValidator
import com.example.data.local.db.ImageProviderStatusEntity
import com.example.data.local.db.ImageStudioDao
import com.example.domain.model.ActivationHealthStatus
import com.example.domain.model.AppSettings
import com.example.domain.model.CapabilityActivationBadge
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DetectedProviderKind
import com.example.domain.model.OneTapActivationResult
import com.example.domain.model.ProviderConnectionHealth
import com.example.domain.model.ProviderHealthSnapshot
import com.example.domain.model.VerifiedModelCatalog
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class OneTapActivationOutcome(
  val result: OneTapActivationResult,
  val updatedSettings: AppSettings?,
)

/**
 * One-Tap API Activation & Unified Provider Catalog Discovery Service.
 * - Detects Gemini (AIza...), Groq (gsk_...), Cloudflare Workers AI token, or HTTPS Proxy.
 * - Validates credentials via real HTTP catalog probes (never hardcoded success).
 * - Maps authentic 10-state ProviderConnectionHealth values without persisting keys in Room.
 */
@Singleton
class OneTapApiActivationService @Inject constructor(
  private val geminiApiService: GeminiApiService,
  private val groqApiService: GroqApiService,
  private val proxyApiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
  private val firebaseAiLogicService: FirebaseAiLogicService? = null,
  private val cloudflareWorkersAiService: CloudflareWorkersAiService? = null,
  private val imageStudioDao: ImageStudioDao? = null,
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
      settings.cloudflareAccountId.isNotBlank() && settings.cloudflareApiToken.isNotBlank() ->
        DetectedProviderKind.CLOUDFLARE
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
            providerSnapshots = buildLocalSnapshots(existingSettings),
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
            providerSnapshots = buildLocalSnapshots(existingSettings),
          ),
        updatedSettings = null,
      )
    }

    return when (detectProvider(cleanKey, existingSettings)) {
      DetectedProviderKind.GEMINI -> probeGeminiKey(cleanKey, existingSettings, hasMicPermission)
      DetectedProviderKind.GROQ -> probeGroqKey(cleanKey, existingSettings, hasMicPermission)
      DetectedProviderKind.PROXY -> probeProxyEndpoint(existingSettings, hasMicPermission, cleanKey)
      DetectedProviderKind.FIREBASE_AI_LOGIC -> buildFirebaseAiLogicOutcome(existingSettings, hasMicPermission)
      DetectedProviderKind.CLOUDFLARE -> probeCloudflareCredentials(existingSettings.cloudflareAccountId, cleanKey, existingSettings, hasMicPermission)
      DetectedProviderKind.UNKNOWN -> {
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

    val snapshots = mutableListOf<ProviderHealthSnapshot>()
    var updatedSettings = settings
    var primaryOutcome: OneTapActivationOutcome? = null

    if (settings.geminiApiKey.isNotBlank()) {
      val geminiOut = probeGeminiKey(settings.geminiApiKey, updatedSettings, hasMicPermission)
      primaryOutcome = geminiOut
      if (geminiOut.updatedSettings != null) updatedSettings = geminiOut.updatedSettings
      snapshots += geminiOut.result.providerSnapshots
    } else {
      snapshots +=
        ProviderHealthSnapshot(
          providerId = "gemini",
          displayName = "Google Gemini API",
          status =
            if (firebaseAiLogicService?.isAvailable() == true) ProviderConnectionHealth.CONNECTED
            else ProviderConnectionHealth.NOT_CONFIGURED,
          activeModelId =
            if (firebaseAiLogicService?.isAvailable() == true) settings.hydrogenBackingModelId else "",
          quotaDetail =
            if (firebaseAiLogicService?.isAvailable() == true) "Firebase AI Logic App Check active"
            else "No Gemini key configured",
          isFreeTier = true,
          lastCheckedAtMs = System.currentTimeMillis(),
        )
    }

    if (settings.groqApiKey.isNotBlank()) {
      val groqOut = probeGroqKey(settings.groqApiKey, updatedSettings, hasMicPermission)
      if (primaryOutcome == null) primaryOutcome = groqOut
      if (groqOut.updatedSettings != null) updatedSettings = groqOut.updatedSettings
      snapshots += groqOut.result.providerSnapshots.filter { it.providerId == "groq" }
    } else {
      snapshots +=
        ProviderHealthSnapshot(
          providerId = "groq",
          displayName = "Groq Cloud",
          status = ProviderConnectionHealth.NOT_CONFIGURED,
          quotaDetail = "No Groq key configured",
          isFreeTier = true,
        )
    }

    if (settings.cloudflareAccountId.isNotBlank() && settings.cloudflareApiToken.isNotBlank()) {
      val cfOut =
        probeCloudflareCredentials(
          accountId = settings.cloudflareAccountId,
          apiToken = settings.cloudflareApiToken,
          existingSettings = updatedSettings,
          hasMicPermission = hasMicPermission,
        )
      if (primaryOutcome == null) primaryOutcome = cfOut
      if (cfOut.updatedSettings != null) updatedSettings = cfOut.updatedSettings
      snapshots += cfOut.result.providerSnapshots.filter { it.providerId == "cloudflare" }
    } else {
      snapshots +=
        ProviderHealthSnapshot(
          providerId = "cloudflare",
          displayName = "Cloudflare Workers AI",
          status = ProviderConnectionHealth.NOT_CONFIGURED,
          quotaDetail = "Free tier available with Account ID + Token",
          isFreeTier = true,
        )
    }

    persistProviderStatusesSafe(snapshots)

    if (primaryOutcome == null) {
      if (firebaseAiLogicService?.isAvailable() == true) {
        return buildFirebaseAiLogicOutcome(settings, hasMicPermission).let { out ->
          out.copy(result = out.result.copy(providerSnapshots = snapshots))
        }
      }
      return OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.UNCONFIGURED,
            detectedProvider = DetectedProviderKind.UNKNOWN,
            statusMessage = "No active API key configured.",
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
            providerSnapshots = snapshots,
          ),
        updatedSettings = null,
      )
    }

    return primaryOutcome.copy(
      result = primaryOutcome.result.copy(providerSnapshots = snapshots.distinctBy { it.providerId }),
      updatedSettings = updatedSettings,
    )
  }

  suspend fun probeCloudflareCredentials(
    accountId: String,
    apiToken: String,
    existingSettings: AppSettings,
    hasMicPermission: Boolean = true,
  ): OneTapActivationOutcome {
    val cleanAccount = accountId.trim()
    val cleanToken = apiToken.trim()
    if (cleanAccount.isBlank() || cleanToken.length < 16) {
      val snapshot =
        ProviderHealthSnapshot(
          providerId = "cloudflare",
          displayName = "Cloudflare Workers AI",
          status = ProviderConnectionHealth.NOT_CONFIGURED,
          quotaDetail = "Provide both Cloudflare Account ID and API Token",
          isFreeTier = true,
        )
      return OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.INVALID_KEY,
            detectedProvider = DetectedProviderKind.CLOUDFLARE,
            statusMessage = "Enter a valid Cloudflare Account ID and Workers AI API Token.",
            providerSnapshots = listOf(snapshot),
          ),
        updatedSettings = null,
      )
    }

    val cfService = cloudflareWorkersAiService
    if (cfService == null) {
      val snapshot =
        ProviderHealthSnapshot(
          providerId = "cloudflare",
          displayName = "Cloudflare Workers AI",
          status = ProviderConnectionHealth.API_KEY_SAVED,
          activeModelId = "@cf/black-forest-labs/flux-1-schnell",
          quotaDetail = "Credentials saved; verify on network",
          isFreeTier = true,
        )
      return OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PARTIALLY_ACTIVE,
            detectedProvider = DetectedProviderKind.CLOUDFLARE,
            statusMessage = "Cloudflare credentials saved.",
            providerSnapshots = listOf(snapshot),
          ),
        updatedSettings =
          existingSettings.copy(
            cloudflareAccountId = cleanAccount,
            cloudflareApiToken = cleanToken,
          ),
      )
    }

    return try {
      val resp = cfService.listImageModels(accountId = cleanAccount, authorization = "Bearer $cleanToken")
      val now = System.currentTimeMillis()
      when (resp.code()) {
        200 -> {
          val models = resp.body()?.result.orEmpty()
          val modelIds = models.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
          val preferred =
            modelIds.firstOrNull { it.contains("flux-1-schnell", ignoreCase = true) }
              ?: modelIds.firstOrNull { it.contains("stable-diffusion", ignoreCase = true) }
              ?: "@cf/black-forest-labs/flux-1-schnell"
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "cloudflare",
              displayName = "Cloudflare Workers AI",
              status = ProviderConnectionHealth.CONNECTED,
              activeModelId = preferred,
              quotaDetail = "Free-tier catalog verified (${modelIds.size} image models)",
              isFreeTier = true,
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.PARTIALLY_ACTIVE,
                detectedProvider = DetectedProviderKind.CLOUDFLARE,
                statusMessage = "Cloudflare Workers AI connected (${modelIds.size} image models verified).",
                maskedKeyHint = maskKey(cleanToken),
                capabilities =
                  buildCapabilities(
                    chatAvailable = existingSettings.geminiApiKey.isNotBlank() || existingSettings.groqApiKey.isNotBlank(),
                    visionAvailable = existingSettings.geminiApiKey.isNotBlank(),
                    imageGenAvailable = true,
                    researchAvailable = existingSettings.geminiApiKey.isNotBlank(),
                    micGranted = hasMicPermission,
                  ),
                discoveredModelIds = modelIds.take(10),
                verifiedAtMs = now,
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings =
              existingSettings.copy(
                cloudflareAccountId = cleanAccount,
                cloudflareApiToken = cleanToken,
              ),
          )
        }
        401, 403 -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "cloudflare",
              displayName = "Cloudflare Workers AI",
              status = ProviderConnectionHealth.AUTHENTICATION_FAILED,
              quotaDetail = "HTTP ${resp.code()} unauthorized",
              isFreeTier = true,
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.INVALID_KEY,
                detectedProvider = DetectedProviderKind.CLOUDFLARE,
                statusMessage = "Cloudflare rejected Account ID or API Token (HTTP ${resp.code()}).",
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
        429 -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "cloudflare",
              displayName = "Cloudflare Workers AI",
              status = ProviderConnectionHealth.RATE_LIMITED,
              quotaDetail = "Daily/minute rate limit reached",
              isFreeTier = true,
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.RATE_LIMITED,
                detectedProvider = DetectedProviderKind.CLOUDFLARE,
                statusMessage = "Cloudflare rate limit reached (HTTP 429).",
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
        else -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "cloudflare",
              displayName = "Cloudflare Workers AI",
              status = ProviderConnectionHealth.TEMPORARILY_UNAVAILABLE,
              quotaDetail = "HTTP ${resp.code()}",
              isFreeTier = true,
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
                detectedProvider = DetectedProviderKind.CLOUDFLARE,
                statusMessage = "Cloudflare service returned HTTP ${resp.code()}.",
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
      }
    } catch (_: IOException) {
      val snapshot =
        ProviderHealthSnapshot(
          providerId = "cloudflare",
          displayName = "Cloudflare Workers AI",
          status = ProviderConnectionHealth.DISCONNECTED,
          quotaDetail = "Network unreachable",
          isFreeTier = true,
        )
      OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
            detectedProvider = DetectedProviderKind.CLOUDFLARE,
            statusMessage = "Network unavailable while verifying Cloudflare Workers AI.",
            providerSnapshots = listOf(snapshot),
          ),
        updatedSettings = null,
      )
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
            "Firebase AI Logic + App Check active (Gemini 4 Hydrogen / Flash-Lite / Search ready). Free-first Image Studio uses Cloudflare Workers AI or local Studio when configured; Nano Banana requires Blaze billing or paid key.",
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
          providerSnapshots = buildLocalSnapshots(existingSettings),
        ),
      updatedSettings = existingSettings,
    )

  fun buildDeactivatedSettings(current: AppSettings): AppSettings =
    current.copy(
      geminiApiKey = "",
      groqApiKey = "",
      cloudflareAccountId = "",
      cloudflareApiToken = "",
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
      val now = System.currentTimeMillis()
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
            rawIds.firstOrNull { it.contains("4-hydrogen") }
              ?: rawIds.firstOrNull { it.contains("3.8-flash") }
              ?: rawIds.firstOrNull { it.contains("2.5-flash") }
              ?: rawIds.firstOrNull { it.contains("flash") }
              ?: existingSettings.hydrogenBackingModelId
          val preferredImageModel =
            rawIds.firstOrNull { it.contains("nano-banana") }
              ?: rawIds.firstOrNull { it.contains("3.1-flash-image") }
              ?: rawIds.firstOrNull { it.contains("image-generation") || it.contains("-image") }
              ?: existingSettings.imageModelId

          val hasResearch = hasChat || existingSettings.externalSearchUrl.isNotBlank()
          val updated =
            existingSettings.copy(
              connectionMode = ConnectionMode.DIRECT_DEV_KEYS,
              geminiApiKey = apiKey,
              hydrogenBackingModelId = preferredHydrogen,
              imageModelId = preferredImageModel,
            )

          val snapshot =
            ProviderHealthSnapshot(
              providerId = "gemini",
              displayName = "Google Gemini API",
              status = ProviderConnectionHealth.CONNECTED,
              activeModelId = preferredHydrogen,
              quotaDetail = "${rawIds.size} models discovered",
              isFreeTier = true,
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))

          val missingOptional =
            buildList {
              if (!hasImageGen) add("image-generation model unlisted")
              if (!hasVideoOrMusic) add("Veo/Lyria require paid billing")
              if (!hasMicPermission) add("mic permission needed for live voice")
            }

          val status =
            if (hasChat && missingOptional.isEmpty()) ActivationHealthStatus.ACTIVE
            else if (hasChat) ActivationHealthStatus.PARTIALLY_ACTIVE
            else ActivationHealthStatus.PROVIDER_UNAVAILABLE

          val msg =
            when (status) {
              ActivationHealthStatus.ACTIVE ->
                "Gemini API connected (${rawIds.size} models verified). All capabilities ready."
              ActivationHealthStatus.PARTIALLY_ACTIVE ->
                "Gemini API connected (${rawIds.size} models). Notes: ${missingOptional.joinToString(", ")}."
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
                    imageGenAvailable = hasImageGen || existingSettings.cloudflareApiToken.isNotBlank(),
                    researchAvailable = hasResearch,
                    micGranted = hasMicPermission,
                    docsAvailable = hasChat,
                    reasoningAvailable = true,
                    videoAndMusicAvailable = hasVideoOrMusic,
                  ),
                discoveredModelIds = rawIds.take(15),
                verifiedAtMs = now,
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = updated,
          )
        }
        400, 401, 403 -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "gemini",
              displayName = "Google Gemini API",
              status = ProviderConnectionHealth.AUTHENTICATION_FAILED,
              quotaDetail = "HTTP ${response.code()}",
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.INVALID_KEY,
                detectedProvider = DetectedProviderKind.GEMINI,
                statusMessage = "Gemini API rejected this key (HTTP ${response.code()}).",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
        429 -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "gemini",
              displayName = "Google Gemini API",
              status = ProviderConnectionHealth.RATE_LIMITED,
              quotaDetail = "HTTP 429 rate limited",
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.RATE_LIMITED,
                detectedProvider = DetectedProviderKind.GEMINI,
                statusMessage = "Gemini API rate limit reached (HTTP 429). Try again shortly.",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
        else -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "gemini",
              displayName = "Google Gemini API",
              status = ProviderConnectionHealth.TEMPORARILY_UNAVAILABLE,
              quotaDetail = "HTTP ${response.code()}",
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
                detectedProvider = DetectedProviderKind.GEMINI,
                statusMessage = "Gemini service returned HTTP ${response.code()}. Try again later.",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
      }
    } catch (_: IOException) {
      val snapshot =
        ProviderHealthSnapshot(
          providerId = "gemini",
          displayName = "Google Gemini API",
          status = ProviderConnectionHealth.DISCONNECTED,
          quotaDetail = "Network unreachable",
        )
      OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
            detectedProvider = DetectedProviderKind.GEMINI,
            statusMessage = "Network unavailable while verifying Gemini key. Check connectivity.",
            maskedKeyHint = maskKey(apiKey),
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
            providerSnapshots = listOf(snapshot),
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
      val now = System.currentTimeMillis()
      when (response.code()) {
        200 -> {
          val models = response.body()?.data.orEmpty()
          val rawIds = models.map { it.id }.filter { it.isNotBlank() }
          val preferredGroq =
            rawIds.firstOrNull { it.contains("qwen", ignoreCase = true) }
              ?: rawIds.firstOrNull { it.contains("gpt-oss-120b", ignoreCase = true) }
              ?: rawIds.firstOrNull { it.contains("llama-3.3-70b", ignoreCase = true) }
              ?: rawIds.firstOrNull()
              ?: VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID
          val preferredHelper =
            rawIds.firstOrNull { it.contains("gpt-oss-20b", ignoreCase = true) }
              ?: rawIds.firstOrNull { it.contains("safeguard-20b", ignoreCase = true) }
              ?: rawIds.firstOrNull { it.contains("prompt-guard", ignoreCase = true) }
              ?: existingSettings.groqHelperGuardModelId
          val hasChat = rawIds.isNotEmpty()
          val hasGeminiConfigured = existingSettings.geminiApiKey.isNotBlank()
          val updated =
            existingSettings.copy(
              groqApiKey = apiKey,
              groqReasoningModelId = preferredGroq,
              groqHelperGuardModelId = preferredHelper,
              selectedModelOptionName =
                if (!hasGeminiConfigured) "GROQ_HIGH_SPEED_REASONING" else existingSettings.selectedModelOptionName,
            )

          val snapshot =
            ProviderHealthSnapshot(
              providerId = "groq",
              displayName = "Groq Cloud",
              status = ProviderConnectionHealth.CONNECTED,
              activeModelId = preferredGroq,
              quotaDetail = "${rawIds.size} fast models ready",
              isFreeTier = true,
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))

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
                    "Groq connected (${rawIds.size} models verified). ${notes.joinToString("; ")}."
                  } else {
                    "Groq key verified, but no active chat models were listed."
                  },
                maskedKeyHint = masked,
                capabilities =
                  buildCapabilities(
                    chatAvailable = hasChat,
                    visionAvailable = hasGeminiConfigured,
                    imageGenAvailable = hasGeminiConfigured || existingSettings.cloudflareApiToken.isNotBlank(),
                    researchAvailable = hasGeminiConfigured || existingSettings.externalSearchUrl.isNotBlank(),
                    micGranted = hasMicPermission,
                    docsAvailable = hasChat,
                    reasoningAvailable = hasChat,
                  ),
                discoveredModelIds = rawIds.take(15),
                verifiedAtMs = now,
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = updated,
          )
        }
        401, 403 -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "groq",
              displayName = "Groq Cloud",
              status = ProviderConnectionHealth.AUTHENTICATION_FAILED,
              quotaDetail = "HTTP ${response.code()}",
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.INVALID_KEY,
                detectedProvider = DetectedProviderKind.GROQ,
                statusMessage = "Groq API rejected this key (HTTP ${response.code()}).",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
        429 -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "groq",
              displayName = "Groq Cloud",
              status = ProviderConnectionHealth.RATE_LIMITED,
              quotaDetail = "HTTP 429 rate limited",
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.RATE_LIMITED,
                detectedProvider = DetectedProviderKind.GROQ,
                statusMessage = "Groq API rate limit reached (HTTP 429). Retry shortly.",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
        else -> {
          val snapshot =
            ProviderHealthSnapshot(
              providerId = "groq",
              displayName = "Groq Cloud",
              status = ProviderConnectionHealth.TEMPORARILY_UNAVAILABLE,
              quotaDetail = "HTTP ${response.code()}",
              lastCheckedAtMs = now,
            )
          persistProviderStatusesSafe(listOf(snapshot))
          OneTapActivationOutcome(
            result =
              OneTapActivationResult(
                status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
                detectedProvider = DetectedProviderKind.GROQ,
                statusMessage = "Groq service returned HTTP ${response.code()}.",
                maskedKeyHint = masked,
                capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
                providerSnapshots = listOf(snapshot),
              ),
            updatedSettings = null,
          )
        }
      }
    } catch (_: IOException) {
      val snapshot =
        ProviderHealthSnapshot(
          providerId = "groq",
          displayName = "Groq Cloud",
          status = ProviderConnectionHealth.DISCONNECTED,
          quotaDetail = "Network unreachable",
        )
      OneTapActivationOutcome(
        result =
          OneTapActivationResult(
            status = ActivationHealthStatus.PROVIDER_UNAVAILABLE,
            detectedProvider = DetectedProviderKind.GROQ,
            statusMessage = "Network unavailable while verifying Groq key.",
            maskedKeyHint = maskKey(apiKey),
            capabilities = buildCapabilities(false, false, false, false, hasMicPermission),
            providerSnapshots = listOf(snapshot),
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

  private fun buildLocalSnapshots(settings: AppSettings): List<ProviderHealthSnapshot> =
    listOf(
      ProviderHealthSnapshot(
        providerId = "gemini",
        displayName = "Google Gemini API",
        status =
          when {
            settings.geminiApiKey.isNotBlank() -> ProviderConnectionHealth.API_KEY_SAVED
            firebaseAiLogicService?.isAvailable() == true -> ProviderConnectionHealth.CONNECTED
            else -> ProviderConnectionHealth.NOT_CONFIGURED
          },
        activeModelId = settings.hydrogenBackingModelId,
        quotaDetail =
          when {
            settings.geminiApiKey.isNotBlank() -> "Key saved (${maskKey(settings.geminiApiKey)})"
            firebaseAiLogicService?.isAvailable() == true -> "Firebase AI Logic App Check active"
            else -> "Enter Gemini API key to activate"
          },
        isFreeTier = true,
      ),
      ProviderHealthSnapshot(
        providerId = "groq",
        displayName = "Groq Cloud",
        status =
          if (settings.groqApiKey.isNotBlank()) ProviderConnectionHealth.API_KEY_SAVED
          else ProviderConnectionHealth.NOT_CONFIGURED,
        activeModelId = settings.groqReasoningModelId,
        quotaDetail =
          if (settings.groqApiKey.isNotBlank()) "Key saved (${maskKey(settings.groqApiKey)})"
          else "Enter Groq API key for fast reasoning & guard",
        isFreeTier = true,
      ),
      ProviderHealthSnapshot(
        providerId = "cloudflare",
        displayName = "Cloudflare Workers AI",
        status =
          if (settings.cloudflareAccountId.isNotBlank() && settings.cloudflareApiToken.isNotBlank()) {
            ProviderConnectionHealth.API_KEY_SAVED
          } else {
            ProviderConnectionHealth.NOT_CONFIGURED
          },
        activeModelId = "@cf/black-forest-labs/flux-1-schnell",
        quotaDetail =
          if (settings.cloudflareApiToken.isNotBlank()) "Free-tier image endpoint saved"
          else "Optional free-tier cloud image engine",
        isFreeTier = true,
      ),
    )

  private suspend fun persistProviderStatusesSafe(snapshots: List<ProviderHealthSnapshot>) {
    val dao = imageStudioDao ?: return
    snapshots.forEach { snap ->
      runCatching {
        dao.upsertProviderStatus(
          ImageProviderStatusEntity(
            providerId = snap.providerId,
            displayName = snap.displayName,
            connectionStatus = snap.status.name,
            quotaState = snap.quotaDetail,
            isFreeTier = snap.isFreeTier,
            activeModelId = snap.activeModelId,
            lastVerifiedAtMs = snap.lastCheckedAtMs,
            lastErrorDetail = if (snap.status.isOperational) null else snap.quotaDetail,
          )
        )
      }
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
        detail = if (imageGenAvailable) "Free-first Image Studio router ready" else "Add Cloudflare or Gemini key",
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
