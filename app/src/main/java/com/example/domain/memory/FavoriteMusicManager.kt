package com.example.domain.memory

import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryRecord
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FavoriteMusicCommandResult {
  data class ShowFavorites(val summaryText: String, val records: List<MemoryRecord>) : FavoriteMusicCommandResult
  data class RemovedFavorite(val message: String) : FavoriteMusicCommandResult
  data class LaunchMusicPlayback(
    val query: String,
    val preferredAppHint: String?,
    val label: String,
  ) : FavoriteMusicCommandResult
  data class AskFavoriteToSave(val message: String, val targetCategory: MemoryCategory) : FavoriteMusicCommandResult
}

/**
 * Structured management & command handling for the user's Favorite Music Profile:
 * Favorite Songs, Singers/Artists, Music Genres, Playlists, and Preferred Music App.
 */
@Singleton
class FavoriteMusicManager @Inject constructor(
  private val repository: PersonalMemoryRepository,
) {
  suspend fun getFavoriteSongs(): List<MemoryRecord> =
    repository.getByCategory(MemoryCategory.FAVORITE_SONG)

  suspend fun getFavoriteArtists(): List<MemoryRecord> =
    repository.getByCategory(MemoryCategory.FAVORITE_ARTIST)

  suspend fun getFavoriteGenres(): List<MemoryRecord> =
    repository.getByCategory(MemoryCategory.FAVORITE_MUSIC_GENRE)

  suspend fun getPreferredMusicApp(): String? =
    repository.getByCategory(MemoryCategory.FAVORITE_APP)
      .firstOrNull { app ->
        val l = app.content.lowercase()
        "spotify" in l || "youtube" in l || "music" in l || "jiosaavn" in l || "wynk" in l || "gaana" in l
      }
      ?.content

  suspend fun getAllMusicProfile(): List<MemoryRecord> =
    repository.getActiveSnapshot().filter {
      it.category == MemoryCategory.FAVORITE_SONG ||
        it.category == MemoryCategory.FAVORITE_ARTIST ||
        it.category == MemoryCategory.FAVORITE_MUSIC_GENRE ||
        it.category == MemoryCategory.FAVORITE_APP
    }

  suspend fun addFavoriteSong(
    songTitleAndArtist: String,
    encryptAtRest: Boolean = true,
  ): MemoryWriteOutcome =
    repository.saveConfirmedMemory(
      category = MemoryCategory.FAVORITE_SONG,
      content = songTitleAndArtist.trim(),
      originalStatement = "Favorite song: ${songTitleAndArtist.trim()}",
      tags = listOf("favorite", "song", "music"),
      importance = 0.88f,
      encryptAtRest = encryptAtRest,
    )

  suspend fun removeFavoriteByName(targetName: String): Int {
    val q = targetName.trim().lowercase()
    if (q.isBlank()) return 0
    val matches =
      getAllMusicProfile().filter { rec ->
        rec.content.lowercase().contains(q)
      }
    matches.forEach { repository.deleteMemory(it.id) }
    return matches.size
  }

  suspend fun tryHandleMusicCommand(rawCommand: String): FavoriteMusicCommandResult? {
    val clean = rawCommand.trim()
    val lower = clean.lowercase().trimEnd('.', '!', '?')

    // 1. Show my favorite music / songs
    if (lower == "show my favorite music" ||
      lower == "show my favourite music" ||
      lower == "show my favorite songs" ||
      lower == "show my favourite songs" ||
      lower == "what is my favorite song" ||
      lower == "what's my favorite song" ||
      lower == "what is my favourite song" ||
      lower == "what is my favorite singer" ||
      lower == "who is my favorite singer" ||
      lower == "who is my favorite artist"
    ) {
      val profile = getAllMusicProfile()
      if (profile.isEmpty()) {
        return FavoriteMusicCommandResult.ShowFavorites(
          summaryText = "You haven't saved any favorite songs, singers, or music genres yet. Tell me your favorite song and I'll ask to save it to My Memory.",
          records = emptyList(),
        )
      }
      val summary =
        buildString {
          append("Here is your saved music profile from My Memory:\n")
          profile.forEach { rec ->
            append("• ${rec.category.label}: ${rec.content}\n")
          }
        }.trimEnd()
      return FavoriteMusicCommandResult.ShowFavorites(summary, profile)
    }

    // 2. Remove favorite song ("remove <song> from favorites")
    Regex(
      "^(?:please\\s+)?(?:remove|delete)\\s+(.+?)\\s+from\\s+(?:my\\s+)?favou?rites?$",
      RegexOption.IGNORE_CASE,
    ).find(clean)?.let { m ->
      val target = m.groupValues[1].trim()
      val removedCount = removeFavoriteByName(target)
      return FavoriteMusicCommandResult.RemovedFavorite(
        if (removedCount > 0) "Removed \"$target\" from your favorite music memory."
        else "Could not find \"$target\" in your saved favorites."
      )
    }

    // 3. Play my favorite song
    if ("play my favorite song" in lower || "play my favourite song" in lower ||
      "play my fav song" in lower || lower == "play favorite song"
    ) {
      val songs = getFavoriteSongs()
      val preferredApp = getPreferredMusicApp()
      val topSong = songs.firstOrNull()
      if (topSong == null) {
        return FavoriteMusicCommandResult.AskFavoriteToSave(
          message = "You haven't saved a favorite song yet. Tell me what song you love and I'll save it and play it for you!",
          targetCategory = MemoryCategory.FAVORITE_SONG,
        )
      }
      return FavoriteMusicCommandResult.LaunchMusicPlayback(
        query = topSong.content,
        preferredAppHint = preferredApp,
        label = "Favorite Song: ${topSong.content}",
      )
    }

    // 4. Play a song by my favorite singer / artist
    if ("play a song by my favorite singer" in lower ||
      "play my favorite singer" in lower ||
      "play my favorite artist" in lower ||
      "play a song by my favourite singer" in lower
    ) {
      val artists = getFavoriteArtists()
      val preferredApp = getPreferredMusicApp()
      val topArtist = artists.firstOrNull()
      if (topArtist == null) {
        return FavoriteMusicCommandResult.AskFavoriteToSave(
          message = "You haven't saved a favorite singer or artist yet. Who is your favorite singer?",
          targetCategory = MemoryCategory.FAVORITE_ARTIST,
        )
      }
      return FavoriteMusicCommandResult.LaunchMusicPlayback(
        query = "${topArtist.content} song",
        preferredAppHint = preferredApp,
        label = "Songs by ${topArtist.content}",
      )
    }

    // 5. Generic "Play <song/artist> on Spotify / YouTube Music"
    Regex(
      "^(?:please\\s+)?play\\s+(.+?)(?:\\s+on\\s+(spotify|youtube\\s+music|youtube))?$",
      RegexOption.IGNORE_CASE,
    ).find(clean)?.let { m ->
      val target = m.groupValues[1].trim()
      val appHint = m.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() } ?: getPreferredMusicApp()
      if (target.isNotBlank() && "screen" !in target.lowercase() && "video" !in target.lowercase()) {
        return FavoriteMusicCommandResult.LaunchMusicPlayback(
          query = target,
          preferredAppHint = appHint,
          label = target,
        )
      }
    }

    return null
  }
}
