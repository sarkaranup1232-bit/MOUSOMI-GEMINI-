package com.example.domain.memory

import com.example.domain.model.MemoryCategory
import javax.inject.Inject
import javax.inject.Singleton

data class PendingMemoryConfirmation(
  val id: String = "pmc_${System.currentTimeMillis()}",
  val category: MemoryCategory,
  val extractedContent: String,
  val originalStatement: String,
  val tags: List<String> = emptyList(),
  val promptMessage: String,
  val isExplicitSaveRequest: Boolean = false,
)

/**
 * Detects preference / favorite / routine / personal info statements during conversation
 * and ensures explicit user confirmation before writing long-term personal memory
 * (unless the user explicitly commanded "remember ..." / "save ... to memory").
 */
@Singleton
class PreferenceConfirmationManager @Inject constructor(
  private val repository: PersonalMemoryRepository,
  private val privacyManager: MemoryPrivacyManager,
) {
  fun inspectUserStatement(
    rawInput: String,
    memoryEnabled: Boolean,
  ): PendingMemoryConfirmation? {
    if (!memoryEnabled) return null
    val clean = rawInput.trim()
    if (clean.length < 4) return null
    if (privacyManager.containsSensitiveFinancialOrSecretData(clean)) return null

    // Skip questions or retrieval commands ("what is my favorite song", "play my favorite song")
    val lower = clean.lowercase().trimEnd('.', '!', '?')
    if (lower.startsWith("what") ||
      lower.startsWith("who") ||
      lower.startsWith("which") ||
      lower.startsWith("show ") ||
      lower.startsWith("play ") ||
      lower.startsWith("remove ") ||
      lower.startsWith("delete ") ||
      lower.endsWith("?")
    ) {
      return null
    }

    val explicitRemember =
      lower.startsWith("remember ") ||
        lower.startsWith("please remember ") ||
        lower.startsWith("save this ") ||
        lower.startsWith("don't forget ") ||
        lower.startsWith("dont forget ")

    // 1. Favorite song
    Regex(
      "(?:my\\s+(?:favou?rite|fav)\\s+song\\s+(?:is|:)|remember\\s+that\\s+my\\s+(?:favou?rite|fav)\\s+song\\s+is)\\s+(.+)$",
      RegexOption.IGNORE_CASE,
    ).find(clean)?.let { m ->
      val song = m.groupValues[1].trim().trimEnd('.', '!')
      if (song.isNotBlank()) {
        return PendingMemoryConfirmation(
          category = MemoryCategory.FAVORITE_SONG,
          extractedContent = song,
          originalStatement = clean,
          tags = listOf("favorite", "song", "music"),
          promptMessage = "Would you like me to save \"$song\" as a favorite song?",
          isExplicitSaveRequest = explicitRemember,
        )
      }
    }

    // 2. Favorite singer / artist / band
    Regex(
      "my\\s+(?:favou?rite|fav)\\s+(?:singer|artist|band|musician)\\s+(?:is|:)\\s+(.+)$",
      RegexOption.IGNORE_CASE,
    ).find(clean)?.let { m ->
      val artist = m.groupValues[1].trim().trimEnd('.', '!')
      if (artist.isNotBlank()) {
        return PendingMemoryConfirmation(
          category = MemoryCategory.FAVORITE_ARTIST,
          extractedContent = artist,
          originalStatement = clean,
          tags = listOf("favorite", "artist", "music"),
          promptMessage = "Would you like me to remember \"$artist\" as your favorite artist?",
          isExplicitSaveRequest = explicitRemember,
        )
      }
    }

    // 3. Favorite music genre
    Regex(
      "my\\s+(?:favou?rite|fav)\\s+(?:music\\s+genre|genre)\\s+(?:is|:)\\s+(.+)$",
      RegexOption.IGNORE_CASE,
    ).find(clean)?.let { m ->
      val genre = m.groupValues[1].trim().trimEnd('.', '!')
      if (genre.isNotBlank()) {
        return PendingMemoryConfirmation(
          category = MemoryCategory.FAVORITE_MUSIC_GENRE,
          extractedContent = genre,
          originalStatement = clean,
          tags = listOf("favorite", "genre", "music"),
          promptMessage = "Would you like me to remember \"$genre\" as your favorite music genre?",
          isExplicitSaveRequest = explicitRemember,
        )
      }
    }

    // 4. Favorite music / video app
    Regex(
      "my\\s+(?:favou?rite|fav)\\s+(?:music\\s+)?app\\s+(?:is|:)\\s+(.+)$",
      RegexOption.IGNORE_CASE,
    ).find(clean)?.let { m ->
      val app = m.groupValues[1].trim().trimEnd('.', '!')
      if (app.isNotBlank()) {
        return PendingMemoryConfirmation(
          category = MemoryCategory.FAVORITE_APP,
          extractedContent = app,
          originalStatement = clean,
          tags = listOf("favorite", "app"),
          promptMessage = "Would you like me to remember \"$app\" as your preferred app?",
          isExplicitSaveRequest = explicitRemember,
        )
      }
    }

    // 5. Language preference
    Regex(
      "(?:i\\s+prefer\\s+(?:talking|speaking|chatting|replies?)\\s+in|please\\s+(?:always\\s+)?(?:speak|talk|reply)\\s+in|my\\s+preferred\\s+language\\s+is)\\s+([A-Za-z\\s]+)$",
      RegexOption.IGNORE_CASE,
    ).find(clean)?.let { m ->
      val lang = m.groupValues[1].trim().trimEnd('.', '!')
      if (lang.isNotBlank()) {
        return PendingMemoryConfirmation(
          category = MemoryCategory.LANGUAGE_PREFERENCE,
          extractedContent = "Preferred language: $lang",
          originalStatement = clean,
          tags = listOf("language", "preference"),
          promptMessage = "Would you like me to remember your language preference (\"$lang\") for future conversations?",
          isExplicitSaveRequest = explicitRemember,
        )
      }
    }

    // 6. Conversation style preference ("I prefer short answers", "give me concise replies")
    Regex(
      "(?:i\\s+(?:prefer|like)\\s+(short|concise|brief|detailed|step[- ]by[- ]step|simple)\\s+(?:answers|replies|explanations)|always\\s+give\\s+(?:me\\s+)?(short|concise|detailed)\\s+(?:answers|replies))$",
      RegexOption.IGNORE_CASE,
    ).find(clean)?.let { _ ->
      return PendingMemoryConfirmation(
        category = MemoryCategory.CONVERSATION_PREFERENCE,
        extractedContent = clean.trimEnd('.'),
        originalStatement = clean,
        tags = listOf("style", "conversation"),
        promptMessage = "Would you like me to remember this conversation style for future chats?",
        isExplicitSaveRequest = explicitRemember,
      )
    }

    // 7. Explicit "Remember that ..." / "Remember my ..."
    if (explicitRemember) {
      val payload =
        clean
          .replace(Regex("^(?:please\\s+)?(?:remember\\s+(?:that)?|save\\s+this\\s*:?|don'?t\\s+forget\\s+(?:that)?)\\s*", RegexOption.IGNORE_CASE), "")
          .trim()
          .trimEnd('.')
      if (payload.length >= 3) {
        val cat =
          when {
            "routine" in lower || "every morning" in lower || "every day" in lower -> MemoryCategory.DAILY_ROUTINE
            "hobby" in lower || "love playing" in lower || "enjoy " in lower -> MemoryCategory.HOBBY_INTEREST
            "my name is" in lower || "i live in" in lower || "i work" in lower -> MemoryCategory.IMPORTANT_INFO
            else -> MemoryCategory.CUSTOM_PREFERENCE
          }
        return PendingMemoryConfirmation(
          category = cat,
          extractedContent = payload,
          originalStatement = clean,
          tags = listOf(cat.name.lowercase()),
          promptMessage = "Would you like me to remember \"$payload\" for future conversations?",
          isExplicitSaveRequest = true,
        )
      }
    }

    // 8. Implicit preference/hobby/routine/name statements (require confirmation!)
    if (lower.startsWith("i prefer ") || lower.startsWith("my favorite ") || lower.startsWith("my favourite ") ||
      lower.startsWith("my hobby is ") || lower.startsWith("i love listening to ") ||
      lower.startsWith("my daily routine ") || lower.startsWith("my name is ")
    ) {
      val cat =
        when {
          "hobby" in lower || "listening to" in lower -> MemoryCategory.HOBBY_INTEREST
          "routine" in lower -> MemoryCategory.DAILY_ROUTINE
          "name is" in lower -> MemoryCategory.IMPORTANT_INFO
          else -> MemoryCategory.PREFERENCE
        }
      return PendingMemoryConfirmation(
        category = cat,
        extractedContent = clean.trimEnd('.'),
        originalStatement = clean,
        tags = listOf(cat.name.lowercase()),
        promptMessage = "Would you like me to remember this for future conversations?",
        isExplicitSaveRequest = false,
      )
    }

    return null
  }

  suspend fun confirmAndSave(
    pending: PendingMemoryConfirmation,
    encryptAtRest: Boolean = true,
  ): MemoryWriteOutcome {
    return repository.saveConfirmedMemory(
      category = pending.category,
      content = pending.extractedContent,
      originalStatement = pending.originalStatement,
      tags = pending.tags,
      importance = if (pending.category == MemoryCategory.IMPORTANT_INFO) 0.9f else 0.8f,
      confidence = if (pending.isExplicitSaveRequest) 0.96f else 0.90f,
      encryptAtRest = encryptAtRest,
    )
  }
}
