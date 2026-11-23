package com.example.domain.model

enum class ActivationHealthStatus(val label: String) {
  UNCONFIGURED("Not Activated"),
  VALIDATING("Verifying Connection…"),
  ACTIVE("Active"),
  PARTIALLY_ACTIVE("Partially Active"),
  INVALID_KEY("Invalid Key"),
  RATE_LIMITED("Rate Limited"),
  PROVIDER_UNAVAILABLE("Provider Unavailable"),
}

enum class ProviderConnectionHealth(val label: String, val isOperational: Boolean) {
  NOT_CONFIGURED("Not Configured", false),
  API_KEY_SAVED("API Key Saved", true),
  VALIDATING("Validating", true),
  CONNECTED("Connected", true),
  REQUEST_SUCCESSFUL("Request Successful", true),
  RATE_LIMITED("Rate Limited", false),
  QUOTA_EXHAUSTED("Quota Exhausted", false),
  AUTHENTICATION_FAILED("Authentication Failed", false),
  TEMPORARILY_UNAVAILABLE("Temporarily Unavailable", false),
  DISCONNECTED("Disconnected", false),
}

enum class DetectedProviderKind(val label: String) {
  FIREBASE_AI_LOGIC("Firebase AI Logic (App Check)"),
  GEMINI("Google Gemini"),
  GROQ("Groq Cloud"),
  CLOUDFLARE("Cloudflare Workers AI"),
  PROXY("HTTPS Proxy"),
  UNKNOWN("Auto-Detect"),
}

data class CapabilityActivationBadge(
  val id: String,
  val label: String,
  val available: Boolean,
  val detail: String,
)

data class ProviderHealthSnapshot(
  val providerId: String,
  val displayName: String,
  val status: ProviderConnectionHealth = ProviderConnectionHealth.NOT_CONFIGURED,
  val activeModelId: String = "",
  val quotaDetail: String = "",
  val isFreeTier: Boolean = true,
  val lastCheckedAtMs: Long? = null,
)

data class OneTapActivationResult(
  val status: ActivationHealthStatus = ActivationHealthStatus.UNCONFIGURED,
  val detectedProvider: DetectedProviderKind = DetectedProviderKind.UNKNOWN,
  val statusMessage: String = "Enter a valid Gemini, Groq, or Cloudflare credential to activate Mousomi AI.",
  val maskedKeyHint: String = "",
  val capabilities: List<CapabilityActivationBadge> = emptyList(),
  val discoveredModelIds: List<String> = emptyList(),
  val verifiedAtMs: Long? = null,
  val providerSnapshots: List<ProviderHealthSnapshot> = emptyList(),
)
