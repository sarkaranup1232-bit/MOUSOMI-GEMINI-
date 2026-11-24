package com.example.domain.memory

import com.example.domain.model.MemoryRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formats retrieved active personal memories cleanly into prompt context across any LLM provider
 * (Gemini, Groq, HTTPS Proxy, Auto) without bloating token budgets or leaking credentials.
 */
@Singleton
class PersonalizedResponseEngine @Inject constructor(
  private val retrievalEngine: MemoryRetrievalEngine,
  private val privacyManager: MemoryPrivacyManager,
) {
  suspend fun buildPersonalizationContext(
    query: String,
    memoryEnabled: Boolean,
    limit: Int = 6,
  ): Pair<String, List<MemoryRecord>> {
    if (!memoryEnabled) return "" to emptyList()
    val relevant = retrievalEngine.retrieveForPrompt(query = query, limit = limit)
    if (relevant.isEmpty()) return "" to emptyList()
    val sanitizedLines = privacyManager.sanitizeForPromptInjection(relevant)
    if (sanitizedLines.isEmpty()) return "" to emptyList()
    val block =
      buildString {
        append("Personalized User Memory Profile (Active & Confirmed):\n")
        sanitizedLines.forEach { append(it).append('\n') }
      }.trimEnd()
    return block to relevant
  }
}
