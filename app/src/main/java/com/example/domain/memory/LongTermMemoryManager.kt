package com.example.domain.memory

import com.example.core.security.KeystoreCryptoManager
import com.example.data.local.db.MemoryDao
import com.example.data.local.db.MemoryItemEntity
import com.example.data.local.db.MemorySummaryEntity
import com.example.domain.model.DomainMessage
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryConfirmationStatus
import com.example.domain.model.MemoryRecord
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface MemoryWriteOutcome {
  data class Saved(val record: MemoryRecord, val mergedDuplicate: Boolean) : MemoryWriteOutcome

  data class BlockedSensitive(val reason: String) : MemoryWriteOutcome
}

@Singleton
class LongTermMemoryManager @Inject constructor(
  private val memoryDao: MemoryDao,
  private val cryptoManager: KeystoreCryptoManager,
) {
  private val json = Json {
    prettyPrint = true
    encodeDefaults = true
  }

  fun observeMemories(): Flow<List<MemoryRecord>> =
    memoryDao.observeAllMemories().map { list -> list.map(::entityToRecord) }

  suspend fun saveMemory(
    category: MemoryCategory,
    rawContent: String,
    importance: Float = 0.7f,
    isApproved: Boolean = true,
    encryptAtRest: Boolean = true,
    originalStatement: String = rawContent,
    tags: List<String> = emptyList(),
    confidence: Float = 0.88f,
    confirmationStatus: MemoryConfirmationStatus =
      if (isApproved) MemoryConfirmationStatus.CONFIRMED
      else MemoryConfirmationStatus.PENDING_CONFIRMATION,
    isActive: Boolean = true,
  ): MemoryWriteOutcome {
    val clean = rawContent.trim()
    if (clean.isBlank()) {
      return MemoryWriteOutcome.BlockedSensitive("Memory cannot be blank.")
    }
    if (cryptoManager.containsSensitiveCredentials(clean) ||
      cryptoManager.containsSensitiveCredentials(originalStatement)
    ) {
      return MemoryWriteOutcome.BlockedSensitive(
        "Blocked: OTPs, passwords, API keys, and sensitive tokens are never stored in memory."
      )
    }

    val existing = memoryDao.getAllMemoriesSnapshot().map(::entityToRecord)
    val duplicate = findDuplicate(clean, existing)
    val now = System.currentTimeMillis()
    val keywords = extractKeywords(clean)
    val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8)

    val record =
      if (duplicate != null) {
        duplicate.copy(
          category = category,
          content = if (clean.length >= duplicate.content.length) clean else duplicate.content,
          originalStatement = originalStatement.trim().ifBlank { duplicate.originalStatement },
          keywords = (duplicate.keywords + keywords).distinct().take(12),
          tags = (duplicate.tags + cleanTags).distinct().take(8),
          importance = max(duplicate.importance, importance).coerceIn(0.1f, 1.0f),
          confidence = max(duplicate.confidence, confidence).coerceIn(0.1f, 1.0f),
          isApproved = isApproved,
          confirmationStatus = confirmationStatus,
          isActive = isActive,
          updatedAtMs = now,
          lastConfirmedAtMs = if (isApproved) now else duplicate.lastConfirmedAtMs,
        )
      } else {
        MemoryRecord(
          id = "mem_${UUID.randomUUID()}",
          category = category,
          content = clean,
          keywords = keywords,
          importance = importance.coerceIn(0.1f, 1.0f),
          isApproved = isApproved,
          createdAtMs = now,
          updatedAtMs = now,
          originalStatement = originalStatement.trim().ifBlank { clean },
          tags = cleanTags,
          confidence = confidence.coerceIn(0.1f, 1.0f),
          confirmationStatus = confirmationStatus,
          isActive = isActive,
          lastConfirmedAtMs = if (isApproved) now else 0L,
        )
      }

    persistRecord(record = record, encryptAtRest = encryptAtRest)
    return MemoryWriteOutcome.Saved(record = record, mergedDuplicate = duplicate != null)
  }

  suspend fun updateMemory(
    id: String,
    newCategory: MemoryCategory? = null,
    newContent: String? = null,
    newTags: List<String>? = null,
    newImportance: Float? = null,
    isActive: Boolean? = null,
    encryptAtRest: Boolean = true,
  ): MemoryWriteOutcome? {
    val existingEntity = memoryDao.getMemoryById(id) ?: return null
    val existing = entityToRecord(existingEntity)
    val updatedContent = newContent?.trim()?.takeIf { it.isNotBlank() } ?: existing.content
    if (cryptoManager.containsSensitiveCredentials(updatedContent)) {
      return MemoryWriteOutcome.BlockedSensitive(
        "Blocked: OTPs, passwords, API keys, and sensitive tokens are never stored in memory."
      )
    }
    val now = System.currentTimeMillis()
    val updated =
      existing.copy(
        category = newCategory ?: existing.category,
        content = updatedContent,
        keywords = extractKeywords(updatedContent),
        tags = newTags?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct() ?: existing.tags,
        importance = newImportance?.coerceIn(0.1f, 1.0f) ?: existing.importance,
        isActive = isActive ?: existing.isActive,
        isApproved = true,
        confirmationStatus = MemoryConfirmationStatus.CONFIRMED,
        updatedAtMs = now,
        lastConfirmedAtMs = now,
      )
    persistRecord(record = updated, encryptAtRest = encryptAtRest)
    return MemoryWriteOutcome.Saved(record = updated, mergedDuplicate = false)
  }

  suspend fun setMemoryActive(id: String, isActive: Boolean) {
    memoryDao.updateActiveFlag(id = id, isActive = isActive, updatedAtMs = System.currentTimeMillis())
  }

  suspend fun deleteMemory(id: String) {
    memoryDao.deleteMemory(id)
  }

  suspend fun deleteByCategory(category: MemoryCategory) {
    memoryDao.deleteByCategory(category.name)
  }

  suspend fun clearAllMemories() {
    memoryDao.clearAllMemories()
  }

  suspend fun getAllMemoriesSnapshot(): List<MemoryRecord> =
    memoryDao.getAllMemoriesSnapshot().map(::entityToRecord)

  suspend fun getApprovedActiveMemoriesSnapshot(): List<MemoryRecord> =
    memoryDao.getApprovedMemoriesSnapshot().map(::entityToRecord)

  suspend fun getMemoriesByCategory(category: MemoryCategory): List<MemoryRecord> =
    memoryDao.getActiveMemoriesByCategory(category.name).map(::entityToRecord)

  suspend fun retrieveRelevantMemories(query: String, limit: Int = 5): List<MemoryRecord> {
    val approved = memoryDao.getApprovedMemoriesSnapshot().map(::entityToRecord)
    return rankMemoriesForQuery(query = query, memories = approved, limit = limit)
  }

  suspend fun summarizeConversationAndPersist(
    conversationId: String,
    messages: List<DomainMessage>,
  ): String {
    val nonError = messages.filter { !it.isError && it.text.isNotBlank() }
    if (nonError.isEmpty()) return ""
    val firstUser = nonError.firstOrNull { it.isUser }?.text.orEmpty().take(120)
    val lastAssistant = nonError.lastOrNull { !it.isUser }?.text.orEmpty().take(180)
    val summary =
      "Topic: $firstUser | Latest: $lastAssistant".replace(Regex("\\s+"), " ").trim()
    if (!cryptoManager.containsSensitiveCredentials(summary)) {
      memoryDao.upsertSummary(
        MemorySummaryEntity(
          conversationId = conversationId,
          summaryText = summary,
          updatedAtMs = System.currentTimeMillis(),
        )
      )
    }
    return summary
  }

  suspend fun exportMemoriesAsJson(): String {
    val all = memoryDao.getAllMemoriesSnapshot().map(::entityToRecord)
    return json.encodeToString(all)
  }

  private suspend fun persistRecord(record: MemoryRecord, encryptAtRest: Boolean) {
    val encodedPayload =
      if (encryptAtRest) cryptoManager.encryptIfSupported(record.content) else record.content
    val isEncrypted = encodedPayload != record.content && encodedPayload.startsWith("enc_v1:")
    val encodedOriginal =
      if (encryptAtRest && record.originalStatement.isNotBlank()) {
        cryptoManager.encryptIfSupported(record.originalStatement)
      } else {
        record.originalStatement
      }

    memoryDao.upsertMemory(
      MemoryItemEntity(
        id = record.id,
        category = record.category.name,
        contentPayload = encodedPayload,
        keywordsCsv = record.keywords.joinToString(","),
        importance = record.importance,
        isApproved = record.isApproved,
        isEncrypted = isEncrypted,
        createdAtMs = record.createdAtMs,
        updatedAtMs = record.updatedAtMs,
        originalStatement = encodedOriginal,
        tagsCsv = record.tags.joinToString(","),
        confidence = record.confidence,
        confirmationStatus = record.confirmationStatus.name,
        isActive = record.isActive,
        lastConfirmedAtMs = record.lastConfirmedAtMs,
      )
    )
  }

  private fun entityToRecord(entity: MemoryItemEntity): MemoryRecord {
    val decrypted =
      if (entity.isEncrypted) cryptoManager.decryptIfEncrypted(entity.contentPayload)
      else entity.contentPayload
    val decryptedOriginal =
      if (entity.originalStatement.startsWith("enc_v1:")) {
        cryptoManager.decryptIfEncrypted(entity.originalStatement)
      } else {
        entity.originalStatement.ifBlank { decrypted }
      }
    val cat =
      runCatching { MemoryCategory.valueOf(entity.category) }
        .getOrDefault(MemoryCategory.FACT)
    val confStatus =
      runCatching { MemoryConfirmationStatus.valueOf(entity.confirmationStatus) }
        .getOrDefault(
          if (entity.isApproved) MemoryConfirmationStatus.CONFIRMED
          else MemoryConfirmationStatus.PENDING_CONFIRMATION
        )
    return MemoryRecord(
      id = entity.id,
      category = cat,
      content = decrypted,
      keywords = entity.keywordsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
      importance = entity.importance,
      isApproved = entity.isApproved,
      createdAtMs = entity.createdAtMs,
      updatedAtMs = entity.updatedAtMs,
      originalStatement = decryptedOriginal,
      tags = entity.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
      confidence = entity.confidence,
      confirmationStatus = confStatus,
      isActive = entity.isActive,
      lastConfirmedAtMs = if (entity.lastConfirmedAtMs > 0L) entity.lastConfirmedAtMs else entity.updatedAtMs,
    )
  }

  companion object {
    private val STOP_WORDS =
      setOf(
        "the", "and", "for", "that", "this", "with", "from", "have", "your", "about",
        "are", "was", "were", "will", "into", "what", "when", "where", "how", "please",
      )

    fun extractKeywords(text: String): List<String> =
      text
        .lowercase()
        .split(Regex("[^a-z0-9\\u0980-\\u09FF\\u0900-\\u097F]+"))
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .distinct()
        .take(10)

    fun findDuplicate(candidate: String, existing: List<MemoryRecord>): MemoryRecord? {
      val candidateTokens = extractKeywords(candidate).toSet()
      val candidateNorm = candidate.trim().lowercase()
      if (candidateTokens.isEmpty()) {
        return existing.firstOrNull { it.content.trim().lowercase() == candidateNorm }
      }
      return existing.firstOrNull { record ->
        if (record.content.trim().lowercase() == candidateNorm) return@firstOrNull true
        val existingTokens = (record.keywords + extractKeywords(record.content)).toSet()
        if (existingTokens.isEmpty()) return@firstOrNull false
        val intersection = candidateTokens.intersect(existingTokens).size
        val union = candidateTokens.union(existingTokens).size
        union > 0 && (intersection.toFloat() / union.toFloat()) >= 0.78f
      }
    }

    fun rankMemoriesForQuery(
      query: String,
      memories: List<MemoryRecord>,
      limit: Int = 5,
    ): List<MemoryRecord> {
      if (memories.isEmpty()) return emptyList()
      val queryTokens = extractKeywords(query).toSet()
      val now = System.currentTimeMillis()
      return memories
        .filter { it.isApproved && it.isActive }
        .map { mem ->
          val memTokens = (mem.keywords + mem.tags + extractKeywords(mem.content)).toSet()
          val overlapScore =
            if (queryTokens.isEmpty()) 0f
            else queryTokens.intersect(memTokens).size.toFloat() / queryTokens.size.toFloat()
          val recencyHours = ((now - mem.updatedAtMs).coerceAtLeast(0L) / 3_600_000f)
          val recencyScore = 1f / (1f + recencyHours * 0.04f)
          val categoryBoost =
            if (mem.category == MemoryCategory.LANGUAGE_PREFERENCE ||
              mem.category == MemoryCategory.CONVERSATION_PREFERENCE
            ) 0.14f else 0f
          val totalScore =
            (overlapScore * 0.52f) + (mem.importance * 0.28f) + (recencyScore * 0.14f) + categoryBoost
          mem to totalScore
        }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
    }
  }
}
