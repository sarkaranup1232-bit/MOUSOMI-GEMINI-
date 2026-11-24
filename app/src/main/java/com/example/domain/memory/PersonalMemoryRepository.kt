package com.example.domain.memory

import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryConfirmationStatus
import com.example.domain.model.MemoryRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Clean Personal Memory CRUD & search repository backed persistently by Room ([LongTermMemoryManager] + [MemoryDao]).
 * Ensures all saved/edited/deleted items survive restarts, new chats, and AI model switches.
 */
@Singleton
class PersonalMemoryRepository @Inject constructor(
  private val memoryManager: LongTermMemoryManager,
  private val privacyManager: MemoryPrivacyManager,
) {
  fun observeAllMemories(): Flow<List<MemoryRecord>> = memoryManager.observeMemories()

  fun observeActiveMemories(): Flow<List<MemoryRecord>> =
    memoryManager.observeMemories().map { list -> list.filter { it.isApproved && it.isActive } }

  suspend fun getAllSnapshot(): List<MemoryRecord> = memoryManager.getAllMemoriesSnapshot()

  suspend fun getActiveSnapshot(): List<MemoryRecord> =
    memoryManager.getApprovedActiveMemoriesSnapshot()

  suspend fun getByCategory(category: MemoryCategory): List<MemoryRecord> =
    memoryManager.getMemoriesByCategory(category)

  suspend fun saveConfirmedMemory(
    category: MemoryCategory,
    content: String,
    originalStatement: String = content,
    tags: List<String> = emptyList(),
    importance: Float = 0.78f,
    confidence: Float = 0.90f,
    encryptAtRest: Boolean = true,
  ): MemoryWriteOutcome {
    privacyManager.validateStorableText(content).onFailure { err ->
      return MemoryWriteOutcome.BlockedSensitive(err.message ?: "Sensitive content blocked.")
    }
    return memoryManager.saveMemory(
      category = category,
      rawContent = content,
      importance = importance,
      isApproved = true,
      encryptAtRest = encryptAtRest,
      originalStatement = originalStatement,
      tags = tags,
      confidence = confidence,
      confirmationStatus = MemoryConfirmationStatus.CONFIRMED,
      isActive = true,
    )
  }

  suspend fun updateMemory(
    id: String,
    category: MemoryCategory? = null,
    content: String? = null,
    tags: List<String>? = null,
    importance: Float? = null,
    isActive: Boolean? = null,
    encryptAtRest: Boolean = true,
  ): MemoryWriteOutcome? {
    if (!content.isNullOrBlank()) {
      privacyManager.validateStorableText(content).onFailure { err ->
        return MemoryWriteOutcome.BlockedSensitive(err.message ?: "Sensitive content blocked.")
      }
    }
    return memoryManager.updateMemory(
      id = id,
      newCategory = category,
      newContent = content,
      newTags = tags,
      newImportance = importance,
      isActive = isActive,
      encryptAtRest = encryptAtRest,
    )
  }

  suspend fun archiveOrRestore(id: String, isActive: Boolean) {
    memoryManager.setMemoryActive(id, isActive)
  }

  suspend fun deleteMemory(id: String) {
    memoryManager.deleteMemory(id)
  }

  suspend fun deleteCategory(category: MemoryCategory) {
    memoryManager.deleteByCategory(category)
  }

  suspend fun clearAll() {
    memoryManager.clearAllMemories()
  }

  suspend fun search(
    query: String,
    categoryFilter: MemoryCategory? = null,
    includeArchived: Boolean = false,
  ): List<MemoryRecord> {
    val all = memoryManager.getAllMemoriesSnapshot()
    val q = query.trim().lowercase()
    return all.filter { item ->
      (includeArchived || item.isActive) &&
        (categoryFilter == null || item.category == categoryFilter) &&
        (q.isBlank() ||
          item.content.lowercase().contains(q) ||
          item.category.label.lowercase().contains(q) ||
          item.tags.any { it.lowercase().contains(q) } ||
          item.keywords.any { it.lowercase().contains(q) })
    }
  }

  suspend fun exportAsJson(): String = memoryManager.exportMemoriesAsJson()
}
