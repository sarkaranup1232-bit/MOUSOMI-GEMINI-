package com.example.domain.memory

import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Model-agnostic Memory Retrieval Engine backed persistently by [PersonalMemoryRepository].
 * Ensures saved preferences & favorite items are retrieved accurately across restarts,
 * new chats, voice/text mode switches, and AI model switches (Gemini/Groq/Proxy/Auto).
 */
@Singleton
class MemoryRetrievalEngine @Inject constructor(
  private val repository: PersonalMemoryRepository,
) {
  suspend fun retrieveForPrompt(query: String, limit: Int = 6): List<MemoryRecord> {
    val active = repository.getActiveSnapshot()
    if (active.isEmpty()) return emptyList()

    val lower = query.lowercase()
    val pinned = mutableListOf<MemoryRecord>()

    // Always include active language and conversation style preferences when available
    pinned += active.filter {
      it.category == MemoryCategory.LANGUAGE_PREFERENCE ||
        it.category == MemoryCategory.CONVERSATION_PREFERENCE
    }.take(2)

    // Boost music memories when the query relates to music/songs/singers/artists
    if (listOf("song", "music", "singer", "artist", "track", "playlist", "listen", "play").any { it in lower }) {
      pinned += active.filter {
        it.category == MemoryCategory.FAVORITE_SONG ||
          it.category == MemoryCategory.FAVORITE_ARTIST ||
          it.category == MemoryCategory.FAVORITE_MUSIC_GENRE ||
          it.category == MemoryCategory.FAVORITE_APP
      }.take(4)
    }

    // Direct preference / identity questions
    if (listOf("favorite", "favourite", "remember", "about me", "my name", "who am i", "hobby", "routine").any { it in lower }) {
      pinned += active.sortedByDescending { it.importance }.take(4)
    }

    val ranked = LongTermMemoryManager.rankMemoriesForQuery(query = query, memories = active, limit = limit)
    return (pinned + ranked)
      .distinctBy { it.id }
      .take(limit)
  }

  suspend fun getFavoritesByCategory(category: MemoryCategory): List<MemoryRecord> =
    repository.getByCategory(category)
}
