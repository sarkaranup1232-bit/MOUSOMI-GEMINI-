package com.example.domain.memory

import com.example.core.security.KeystoreCryptoManager
import com.example.domain.model.MemoryRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces strict privacy & security safeguards for personal memory:
 * - Blocks OTPs, passwords, API keys, PINs, card/CVV secrets, and bank/private auth tokens.
 * - Delegates AES-GCM encryption at rest via Android Keystore ([KeystoreCryptoManager]).
 * - Minimizes context prompt injection so only concise, relevant, approved memories enter prompts.
 */
@Singleton
class MemoryPrivacyManager @Inject constructor(
  private val cryptoManager: KeystoreCryptoManager,
) {
  fun validateStorableText(text: String): Result<String> {
    val clean = text.trim()
    if (clean.isBlank()) {
      return Result.failure(IllegalArgumentException("Memory entry cannot be blank."))
    }
    if (containsSensitiveFinancialOrSecretData(clean)) {
      return Result.failure(
        SecurityException(
          "Blocked: passwords, OTPs, PINs, API keys, CVVs, or private account credentials cannot be saved to memory."
        )
      )
    }
    return Result.success(clean)
  }

  fun containsSensitiveFinancialOrSecretData(text: String): Boolean {
    if (cryptoManager.containsSensitiveCredentials(text)) return true
    val lower = text.lowercase()
    val bankPatterns =
      listOf(
        "atm pin",
        "upi pin",
        "mpin",
        "debit card number",
        "credit card number",
        "card number is",
        "bank password",
        "netbanking",
        "private key",
        "seed phrase",
        "recovery phrase",
      )
    if (bankPatterns.any { it in lower }) return true
    // 13-19 digit card-like sequences
    if (Regex("\\b(?:\\d[ -]*?){13,19}\\b").containsMatchIn(text) && ("card" in lower || "cvv" in lower || "visa" in lower || "mastercard" in lower)) {
      return true
    }
    return false
  }

  fun sanitizeForPromptInjection(memories: List<MemoryRecord>, maxTotalChars: Int = 950): List<String> {
    val result = mutableListOf<String>()
    var used = 0
    for (mem in memories) {
      if (!mem.isApproved || !mem.isActive) continue
      if (containsSensitiveFinancialOrSecretData(mem.content)) continue
      val line = "- [${mem.category.label}] ${mem.content.trim().take(180)}"
      if (used + line.length > maxTotalChars) break
      result += line
      used += line.length + 1
    }
    return result
  }
}
