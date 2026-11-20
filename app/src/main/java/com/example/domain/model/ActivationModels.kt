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

enum class DetectedProviderKind(val label: String) {
  FIREBASE_AI_LOGIC("Firebase AI Logic (App Check)"),
  GEMINI("Google Gemini"),
  GROQ("Groq Cloud"),
  PROXY("HTTPS Proxy"),
  UNKNOWN("Auto-Detect"),
}

data class CapabilityActivationBadge(
  val id: String,
  val label: String,
  val available: Boolean,
  val detail: String,
)

data class OneTapActivationResult(
  val status: ActivationHealthStatus = ActivationHealthStatus.UNCONFIGURED,
  val detectedProvider: DetectedProviderKind = DetectedProviderKind.UNKNOWN,
  val statusMessage: String = "Enter a valid Gemini or Groq API key to activate Mousomi AI.",
  val maskedKeyHint: String = "",
  val capabilities: List<CapabilityActivationBadge> = emptyList(),
  val discoveredModelIds: List<String> = emptyList(),
  val verifiedAtMs: Long? = null,
)
