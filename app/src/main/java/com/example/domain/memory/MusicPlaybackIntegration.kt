package com.example.domain.memory

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.example.core.device.DeviceActionVerifier
import com.example.core.device.PackageVisibilityManager
import com.example.data.repository.AddOnRepository
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Android Music Playback & Search Integration using:
 * - Official [MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH] targeted at Spotify / YouTube Music when installed
 * - Spotify search URI (`spotify:search:<query>`) or YouTube / YouTube Music verified search URL fallback
 * Never fabricates playback when no compatible media/browser handler exists.
 */
@Singleton
class MusicPlaybackIntegration @Inject constructor(
  @ApplicationContext private val context: Context,
  private val visibilityManager: PackageVisibilityManager,
  private val verifier: DeviceActionVerifier,
  private val addOnRepository: AddOnRepository? = null,
) {
  fun buildPlayFromSearchIntent(
    query: String,
    preferredAppHint: String? = null,
  ): Pair<String, Intent> {
    val cleanQuery = query.trim()
    val hint = preferredAppHint?.lowercase().orEmpty()

    val preferredPackage =
      when {
        "spotify" in hint && visibilityManager.isPackageInstalled(PKG_SPOTIFY) -> PKG_SPOTIFY
        "youtube music" in hint && visibilityManager.isPackageInstalled(PKG_YT_MUSIC) -> PKG_YT_MUSIC
        "youtube" in hint && visibilityManager.isPackageInstalled(PKG_YOUTUBE) -> PKG_YOUTUBE
        visibilityManager.isPackageInstalled(PKG_SPOTIFY) -> PKG_SPOTIFY
        visibilityManager.isPackageInstalled(PKG_YT_MUSIC) -> PKG_YT_MUSIC
        visibilityManager.isPackageInstalled(PKG_YOUTUBE) -> PKG_YOUTUBE
        else -> null
      }

    // 1. Official Android Media Play-From-Search intent
    val playSearchIntent =
      Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        putExtra(SearchManager.QUERY, cleanQuery)
        if (preferredPackage != null) {
          setPackage(preferredPackage)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    if (verifier.verifyIntentResolvable(playSearchIntent)) {
      val label =
        when (preferredPackage) {
          PKG_SPOTIFY -> "Spotify"
          PKG_YT_MUSIC -> "YouTube Music"
          PKG_YOUTUBE -> "YouTube"
          else -> "Music Player"
        }
      return label to playSearchIntent
    }

    // 2. Spotify search deep link fallback
    if (preferredPackage == PKG_SPOTIFY || ("spotify" in hint && visibilityManager.isPackageInstalled(PKG_SPOTIFY))) {
      val spotifyIntent =
        Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(cleanQuery)}")).apply {
          setPackage(PKG_SPOTIFY)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      if (verifier.verifyIntentResolvable(spotifyIntent)) {
        return "Spotify" to spotifyIntent
      }
    }

    // 3. YouTube / YouTube Music search URL fallback
    val ytMusicWeb =
      Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://music.youtube.com/search?q=${Uri.encode(cleanQuery)}"),
      ).apply {
        if (visibilityManager.isPackageInstalled(PKG_YT_MUSIC)) {
          setPackage(PKG_YT_MUSIC)
        } else if (visibilityManager.isPackageInstalled(PKG_YOUTUBE)) {
          setPackage(PKG_YOUTUBE)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return "YouTube Music / Web Search" to ytMusicWeb
  }

  suspend fun launchMusicPlayback(
    query: String,
    preferredAppHint: String? = null,
    displayLabel: String = query,
  ): DeviceActionResult {
    val (appLabel, intent) = buildPlayFromSearchIntent(query = query, preferredAppHint = preferredAppHint)
    if (!verifier.verifyIntentResolvable(intent)) {
      val fail =
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Play $displayLabel",
          targetLabel = displayLabel,
          success = false,
          statusLabel = "No Music App",
          verifiedDetail = "No compatible music player or browser could handle playback search for \"$query\".",
        )
      addOnRepository?.recordDeviceAction(fail)
      return fail
    }

    val res =
      runCatching {
        context.startActivity(intent)
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Play $displayLabel",
          targetLabel = displayLabel,
          packageName = intent.`package`,
          success = true,
          statusLabel = "Opened in $appLabel",
          verifiedDetail = "Dispatched verified music search/playback request for \"$query\" via $appLabel.",
        )
      }.getOrElse { err ->
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Play $displayLabel",
          targetLabel = displayLabel,
          success = false,
          statusLabel = "Playback Launch Failed",
          verifiedDetail = err.message ?: "Failed to launch music playback.",
        )
      }
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  companion object {
    const val PKG_SPOTIFY = "com.spotify.music"
    const val PKG_YT_MUSIC = "com.google.android.apps.youtube.music"
    const val PKG_YOUTUBE = "com.google.android.youtube"
  }
}
