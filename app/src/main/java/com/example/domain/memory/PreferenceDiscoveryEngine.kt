package com.example.domain.memory

import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryRecord
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveryQuestionPrompt(
  val topicKey: String,
  val targetCategory: MemoryCategory,
  val questionText: String,
)

/**
 * Periodically asks at most one natural preference-discovery question during conversation,
 * skipping any category already answered/stored and honoring user disable toggles.
 */
@Singleton
class PreferenceDiscoveryEngine @Inject constructor(
  private val repository: PersonalMemoryRepository,
) {
  private val askedTopicsInProcess = LinkedHashSet<String>()
  private var lastAskedAtMs: Long = 0L

  suspend fun maybeSuggestDiscoveryQuestion(
    userPrompt: String,
    memoryEnabled: Boolean,
    proactiveQuestionsEnabled: Boolean,
    userTurnCountInConversation: Int,
    nowMs: Long = System.currentTimeMillis(),
  ): DiscoveryQuestionPrompt? {
    if (!memoryEnabled || !proactiveQuestionsEnabled) return null
    val lower = userPrompt.lowercase()
    // Do not interrupt urgent task commands, errors, or sensitive flows with discovery prompts
    if (userTurnCountInConversation < 2) return null
    if (nowMs - lastAskedAtMs < 10 * 60 * 1000L && lastAskedAtMs > 0L) return null
    if (listOf("call ", "dial ", "sms ", "whatsapp ", "alarm", "timer", "otp", "password").any { it in lower }) {
      return null
    }

    val existing = repository.getActiveSnapshot()
    return selectNextCandidate(existing, askedTopicsInProcess, lower)?.also {
      askedTopicsInProcess.add(it.topicKey)
      lastAskedAtMs = nowMs
    }
  }

  fun markTopicAskedOrDeclined(topicKey: String) {
    askedTopicsInProcess.add(topicKey)
  }

  companion object {
    private val CANDIDATE_QUESTIONS =
      listOf(
        DiscoveryQuestionPrompt(
          topicKey = "favorite_song",
          targetCategory = MemoryCategory.FAVORITE_SONG,
          questionText = "By the way, what is one of your favorite songs? I can remember it so I can play it faster next time.",
        ),
        DiscoveryQuestionPrompt(
          topicKey = "favorite_artist",
          targetCategory = MemoryCategory.FAVORITE_ARTIST,
          questionText = "Which singer or band do you enjoy listening to the most?",
        ),
        DiscoveryQuestionPrompt(
          topicKey = "language_pref",
          targetCategory = MemoryCategory.LANGUAGE_PREFERENCE,
          questionText = "Which language do you prefer we usually talk in (English, Bengali, or Hindi)?",
        ),
        DiscoveryQuestionPrompt(
          topicKey = "reply_style",
          targetCategory = MemoryCategory.CONVERSATION_PREFERENCE,
          questionText = "Do you prefer quick concise answers or detailed step-by-step explanations?",
        ),
        DiscoveryQuestionPrompt(
          topicKey = "music_app",
          targetCategory = MemoryCategory.FAVORITE_APP,
          questionText = "Which music app do you usually prefer (like Spotify or YouTube Music)?",
        ),
        DiscoveryQuestionPrompt(
          topicKey = "hobbies",
          targetCategory = MemoryCategory.HOBBY_INTEREST,
          questionText = "What are some hobbies or topics you love exploring in your free time?",
        ),
      )

    fun selectNextCandidate(
      existingMemories: List<MemoryRecord>,
      alreadyAskedTopics: Set<String>,
      contextualHintLower: String = "",
    ): DiscoveryQuestionPrompt? {
      val storedCategories = existingMemories.filter { it.isActive && it.isApproved }.map { it.category }.toSet()
      val remaining =
        CANDIDATE_QUESTIONS.filter { q ->
          q.topicKey !in alreadyAskedTopics && q.targetCategory !in storedCategories
        }
      if (remaining.isEmpty()) return null

      // Prefer a contextually relevant unanswered question first
      if ("music" in contextualHintLower || "song" in contextualHintLower) {
        remaining.firstOrNull { it.topicKey == "favorite_song" || it.topicKey == "favorite_artist" }?.let { return it }
      }
      if ("bengali" in contextualHintLower || "hindi" in contextualHintLower || "bangla" in contextualHintLower) {
        remaining.firstOrNull { it.topicKey == "language_pref" }?.let { return it }
      }
      return remaining.first()
    }
  }
}
