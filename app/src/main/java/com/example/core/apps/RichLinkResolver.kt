package com.example.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import com.example.domain.model.RichLinkKind
import com.example.domain.model.RichLinkMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RichLinkResolver @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val urlRegex = Regex("""https://[^\s)\]>"']+""", RegexOption.IGNORE_CASE)

  fun extractRichCards(text: String): List<RichLinkMetadata> {
    if (text.isBlank()) return emptyList()
    val foundUrls = urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ';', ':') }.distinct().take(4).toList()
    val cards = mutableListOf<RichLinkMetadata>()

    foundUrls.forEach { rawUrl ->
      val parsed = runCatching { URI(rawUrl) }.getOrNull()
      val host = parsed?.host?.lowercase()?.removePrefix("www.") ?: return@forEach
      val kind =
        when {
          host == "play.google.com" -> RichLinkKind.PLAY_STORE
          host == "youtube.com" || host == "youtu.be" || host == "m.youtube.com" -> RichLinkKind.YOUTUBE
          host == "instagram.com" -> RichLinkKind.INSTAGRAM
          host == "wa.me" || host == "whatsapp.com" || host == "chat.whatsapp.com" -> RichLinkKind.WHATSAPP
          else -> RichLinkKind.WEBSITE
        }
      val knownPkg = packageForKind(kind)
      val installed = knownPkg != null && isAppInstalled(knownPkg)
      val title =
        when (kind) {
          RichLinkKind.PLAY_STORE -> "Google Play Store"
          RichLinkKind.YOUTUBE -> "YouTube"
          RichLinkKind.INSTAGRAM -> "Instagram"
          RichLinkKind.WHATSAPP -> "WhatsApp"
          RichLinkKind.WEBSITE, RichLinkKind.ANDROID_APP -> host
        }
      cards +=
        RichLinkMetadata(
          id = "rich_${UUID.randomUUID()}",
          kind = kind,
          title = title,
          subtitle = rawUrl.removePrefix("https://").take(64),
          url = rawUrl,
          packageName = if (installed) knownPkg else null,
          domain = host,
          isInstalledApp = installed,
          actionLabel = if (installed) "Open in App" else "Open Website",
        )
    }

    // Also match explicit known app launch references if prompt/reply mentions opening an installed app
    val lower = text.lowercase()
    val appCandidates =
      listOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "play store" to "com.android.vending",
      )
    appCandidates.forEach { (keyword, pkg) ->
      if (keyword in lower && cards.none { it.packageName == pkg }) {
        val label = resolveAppLabel(pkg)
        if (label != null) {
          val kind =
            when (pkg) {
              "com.android.vending" -> RichLinkKind.PLAY_STORE
              "com.google.android.youtube" -> RichLinkKind.YOUTUBE
              "com.instagram.android" -> RichLinkKind.INSTAGRAM
              "com.whatsapp" -> RichLinkKind.WHATSAPP
              else -> RichLinkKind.ANDROID_APP
            }
          cards +=
            RichLinkMetadata(
              id = "rich_${UUID.randomUUID()}",
              kind = kind,
              title = label,
              subtitle = "Installed Android app ($pkg)",
              url = fallbackHttpsForKind(kind),
              packageName = pkg,
              domain = null,
              isInstalledApp = true,
              actionLabel = "Launch $label",
            )
        }
      }
    }

    return cards.take(4)
  }

  fun loadAppIconDrawable(packageName: String?): Drawable? {
    if (packageName.isNullOrBlank()) return null
    return runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
  }

  fun launchRichCard(card: RichLinkMetadata): Result<Unit> = runCatching {
    val pm = context.packageManager
    val launchIntent =
      if (!card.packageName.isNullOrBlank() && card.url.isBlank()) {
        pm.getLaunchIntentForPackage(card.packageName)
      } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(card.url)).apply {
          if (!card.packageName.isNullOrBlank() && card.isInstalledApp) {
            setPackage(card.packageName)
          }
        }
      } ?: Intent(Intent.ACTION_VIEW, Uri.parse(card.url))

    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (launchIntent.resolveActivity(pm) != null) {
      context.startActivity(launchIntent)
    } else {
      val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(card.url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(fallback)
    }
  }

  private fun isAppInstalled(packageName: String): Boolean =
    runCatching {
      context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      true
    }.getOrElse {
      runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
      }.getOrDefault(false)
    }

  private fun resolveAppLabel(packageName: String): String? =
    runCatching {
      val info = context.packageManager.getApplicationInfo(packageName, 0)
      context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

  private fun packageForKind(kind: RichLinkKind): String? =
    when (kind) {
      RichLinkKind.PLAY_STORE -> "com.android.vending"
      RichLinkKind.YOUTUBE -> "com.google.android.youtube"
      RichLinkKind.INSTAGRAM -> "com.instagram.android"
      RichLinkKind.WHATSAPP -> "com.whatsapp"
      RichLinkKind.ANDROID_APP, RichLinkKind.WEBSITE -> null
    }

  private fun fallbackHttpsForKind(kind: RichLinkKind): String =
    when (kind) {
      RichLinkKind.PLAY_STORE -> "https://play.google.com/store"
      RichLinkKind.YOUTUBE -> "https://www.youtube.com"
      RichLinkKind.INSTAGRAM -> "https://www.instagram.com"
      RichLinkKind.WHATSAPP -> "https://www.whatsapp.com"
      RichLinkKind.ANDROID_APP, RichLinkKind.WEBSITE -> "https://example.com"
    }
}
