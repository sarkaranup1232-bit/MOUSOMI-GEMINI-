package com.example.core.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.R
import com.example.data.repository.AddOnRepository
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageVisibilityManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun queryLauncherPackages(): List<InstalledAppInfo> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcherIntent, 0)
      }
    return resolved
      .mapNotNull { info ->
        val actInfo = info.activityInfo ?: return@mapNotNull null
        val pkg = actInfo.packageName ?: return@mapNotNull null
        val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
        val isSys = (actInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        InstalledAppInfo(packageName = pkg, appName = label, isSystemApp = isSys)
      }
      .distinctBy { it.packageName }
      .sortedBy { it.appName.lowercase() }
  }

  fun isPackageInstalled(packageName: String): Boolean =
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
      }
      true
    }.getOrDefault(false)
}

@Singleton
class InstalledAppRepository @Inject constructor(
  private val visibilityManager: PackageVisibilityManager,
) {
  fun getAllInstalledApps(): List<InstalledAppInfo> = visibilityManager.queryLauncherPackages()

  fun searchApps(query: String): List<InstalledAppInfo> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return getAllInstalledApps()
    val knownAliasPackage = knownAppPackageForAlias(q)
    return getAllInstalledApps().filter { app ->
      app.appName.lowercase().contains(q) ||
        app.packageName.lowercase().contains(q) ||
        (knownAliasPackage != null && app.packageName == knownAliasPackage)
    }
  }

  fun resolveTargetPackage(appNameOrAlias: String): InstalledAppInfo? {
    val clean = appNameOrAlias.trim().lowercase()
    if (clean.isBlank()) return null
    val all = getAllInstalledApps()
    all.firstOrNull { it.appName.lowercase() == clean }?.let { return it }
    knownAppPackageForAlias(clean)?.let { aliasPkg ->
      all.firstOrNull { it.packageName == aliasPkg }?.let { return it }
      if (visibilityManager.isPackageInstalled(aliasPkg)) {
        return InstalledAppInfo(packageName = aliasPkg, appName = appNameOrAlias.trim())
      }
    }
    return all.firstOrNull { it.appName.lowercase().contains(clean) || clean.contains(it.appName.lowercase()) }
  }

  companion object {
    fun knownAppPackageForAlias(alias: String): String? {
      val q = alias.lowercase().trim()
      return when {
        "youtube" in q -> "com.google.android.youtube"
        "chrome" in q -> "com.android.chrome"
        "whatsapp" in q -> "com.whatsapp"
        "play store" in q || "playstore" in q -> "com.android.vending"
        "calculator" in q -> "com.google.android.calculator"
        "clock" in q || "alarm" in q -> "com.google.android.deskclock"
        "gmail" in q -> "com.google.android.gm"
        "maps" in q -> "com.google.android.apps.maps"
        "instagram" in q -> "com.instagram.android"
        "spotify" in q -> "com.spotify.music"
        "gallery" in q || "photos" in q -> "com.google.android.apps.photos"
        else -> null
      }
    }
  }
}

@Singleton
class DeviceActionVerifier @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun verifyIntentResolvable(intent: Intent): Boolean =
    intent.resolveActivity(context.packageManager) != null

  fun verifyClipboardContains(expected: String): Boolean {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    val current = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    return current == expected
  }

  fun isBluetoothEnabled(): Boolean? {
    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter: BluetoothAdapter? = mgr?.adapter
    return adapter?.isEnabled
  }
}

@Singleton
class AndroidIntentManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun buildSystemSettingsIntent(targetQuery: String): Pair<String, Intent>? {
    val q = targetQuery.lowercase()
    return when {
      "bluetooth" in q -> "Bluetooth Settings" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
      "wifi" in q || "wi-fi" in q -> "Wi-Fi Settings" to Intent(Settings.ACTION_WIFI_SETTINGS)
      "camera" in q -> "Camera" to Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
      "gallery" in q || "photos" in q ->
        "Gallery" to Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
      "display" in q || "brightness" in q ->
        "Display Settings" to Intent(Settings.ACTION_DISPLAY_SETTINGS)
      "sound" in q || "volume" in q ->
        "Sound Settings" to Intent(Settings.ACTION_SOUND_SETTINGS)
      "battery" in q -> "Battery Saver Settings" to Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
      "location" in q -> "Location Settings" to Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
      "contact" in q || "contacts" in q ->
        "Contacts" to Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
      "accessibility" in q ->
        "Accessibility Settings" to Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      "notification listener" in q || "notification access" in q ->
        "Notification Access Settings" to Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
      "default assistant" in q || "assistant settings" in q ->
        "Default Assistant Settings" to Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
      "timer" in q && "open" in q ->
        "Timers" to Intent(AlarmClock.ACTION_SHOW_ALARMS)
      "notification" in q ->
        "App Notification Settings" to
          Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
          }
      "app info" in q || "permissions" in q ->
        "Mousomi App Settings" to
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
      "settings" in q -> "Android Settings" to Intent(Settings.ACTION_SETTINGS)
      else -> null
    }
  }

  fun buildShareTextIntent(text: String, title: String = "Share from Mousomi AI"): Intent =
    Intent.createChooser(
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
      },
      title,
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

  fun buildCalendarEventIntent(
    title: String,
    startTimeMs: Long,
    endTimeMs: Long,
    description: String = "",
  ): Intent =
    Intent(Intent.ACTION_INSERT).apply {
      data = CalendarContract.Events.CONTENT_URI
      putExtra(CalendarContract.Events.TITLE, title)
      putExtra(CalendarContract.Events.DESCRIPTION, description)
      putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMs)
      putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMs)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Singleton
class AppLauncherManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val appRepository: InstalledAppRepository,
  private val verifier: DeviceActionVerifier,
) {
  fun launchAppByName(appName: String): DeviceActionResult {
    val target = appRepository.resolveTargetPackage(appName)
    if (target == null) {
      return DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.LAUNCH_APP,
        command = "Open $appName",
        targetLabel = appName,
        success = false,
        statusLabel = "Not Installed",
        verifiedDetail = "Application '$appName' is not installed or has no launcher activity visible to Mousomi AI.",
      )
    }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
    if (launchIntent == null) {
      return DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.LAUNCH_APP,
        command = "Open $appName",
        targetLabel = target.appName,
        packageName = target.packageName,
        success = false,
        statusLabel = "No Launcher Intent",
        verifiedDetail = "${target.appName} (${target.packageName}) does not expose a launchable main activity.",
      )
    }

    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!verifier.verifyIntentResolvable(launchIntent)) {
      return DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.LAUNCH_APP,
        command = "Open $appName",
        targetLabel = target.appName,
        packageName = target.packageName,
        success = false,
        statusLabel = "Unavailable",
        verifiedDetail = "Android could not resolve a launcher target for ${target.packageName}.",
      )
    }

    return runCatching {
      context.startActivity(launchIntent)
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.LAUNCH_APP,
        command = "Open $appName",
        targetLabel = target.appName,
        packageName = target.packageName,
        success = true,
        statusLabel = "Launched",
        verifiedDetail = "Dispatched verified Android launcher intent for ${target.appName} (${target.packageName}).",
      )
    }.getOrElse { err ->
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.LAUNCH_APP,
        command = "Open $appName",
        targetLabel = target.appName,
        packageName = target.packageName,
        success = false,
        statusLabel = "Launch Blocked",
        verifiedDetail = err.message ?: "Android rejected launching ${target.appName}.",
      )
    }
  }
}

@Singleton
class AppShortcutManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun publishQuickAssistantShortcuts(mainActivityClassName: String): Boolean {
    return runCatching {
      val voiceShortcut =
        ShortcutInfoCompat.Builder(context, "mousomi_voice_shortcut")
          .setShortLabel("Voice Chat")
          .setLongLabel("Ask Mousomi by Voice")
          .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
          .setIntent(
            Intent(Intent.ACTION_ASSIST).apply {
              setClassName(context.packageName, mainActivityClassName)
            }
          )
          .build()
      ShortcutManagerCompat.pushDynamicShortcut(context, voiceShortcut)
      true
    }.getOrDefault(false)
  }
}

@Singleton
class DeviceActionExecutor @Inject constructor(
  @ApplicationContext private val context: Context,
  private val appLauncherManager: AppLauncherManager,
  private val installedAppRepository: InstalledAppRepository,
  private val intentManager: AndroidIntentManager,
  private val verifier: DeviceActionVerifier,
  private val addOnRepository: AddOnRepository,
) {
  fun parseTargetFromCommand(command: String): String {
    return command
      .replace(Regex("^(please\\s+)?(can you\\s+)?(open|launch|start|search apps? for|find app)\\s+", RegexOption.IGNORE_CASE), "")
      .trim()
      .trimEnd('.', '!', '?')
  }

  suspend fun executeNaturalCommand(command: String): DeviceActionResult {
    val cleanCmd = command.trim()
    val lower = cleanCmd.lowercase()

    // 1. Copy text
    if (lower.startsWith("copy ")) {
      val toCopy = cleanCmd.removePrefix("copy ").removePrefix("Copy ").trim()
      val res = copyToClipboard(toCopy)
      addOnRepository.recordDeviceAction(res)
      return res
    }

    // 2. Search installed apps
    if (lower.startsWith("search app") || lower.startsWith("find app") || lower.contains("installed apps")) {
      val query = parseTargetFromCommand(cleanCmd)
      val matches = installedAppRepository.searchApps(query)
      val res =
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = cleanCmd,
          targetLabel = query.ifBlank { "Installed Apps" },
          success = true,
          statusLabel = "Found ${matches.size} App(s)",
          verifiedDetail =
            if (matches.isEmpty()) "No installed launcher apps matched '$query'."
            else matches.take(8).joinToString(", ") { it.appName },
        )
      addOnRepository.recordDeviceAction(res)
      return res
    }

    // 3. System Settings / Camera / Gallery intents
    val settingsIntentPair = intentManager.buildSystemSettingsIntent(cleanCmd)
    if (settingsIntentPair != null) {
      val (label, intent) = settingsIntentPair
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      val result =
        if (!verifier.verifyIntentResolvable(intent)) {
          // Try launching Play Store / Photos / Calculator by app name fallback for Camera or Gallery
          if (label == "Gallery") {
            appLauncherManager.launchAppByName("Gallery")
          } else {
            DeviceActionResult(
              id = "dev_${UUID.randomUUID()}",
              category = DeviceActionCategory.SYSTEM_SETTINGS,
              command = cleanCmd,
              targetLabel = label,
              success = false,
              statusLabel = "Unavailable",
              verifiedDetail = "No Android activity resolved for $label on this device.",
            )
          }
        } else {
          runCatching {
            context.startActivity(intent)
            DeviceActionResult(
              id = "dev_${UUID.randomUUID()}",
              category = DeviceActionCategory.SYSTEM_SETTINGS,
              command = cleanCmd,
              targetLabel = label,
              success = true,
              statusLabel = "Opened",
              verifiedDetail = "Verified and launched Android $label.",
            )
          }.getOrElse { err ->
            DeviceActionResult(
              id = "dev_${UUID.randomUUID()}",
              category = DeviceActionCategory.SYSTEM_SETTINGS,
              command = cleanCmd,
              targetLabel = label,
              success = false,
              statusLabel = "Failed",
              verifiedDetail = err.message ?: "Failed to open $label.",
            )
          }
        }
      addOnRepository.recordDeviceAction(result)
      return result
    }

    // 4. Installed app launch
    val appTarget = parseTargetFromCommand(cleanCmd)
    val appRes = appLauncherManager.launchAppByName(appTarget)
    addOnRepository.recordDeviceAction(appRes)
    return appRes
  }

  fun copyToClipboard(text: String): DeviceActionResult {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      ?: return DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.CLIPBOARD_COPY,
        command = "Copy text",
        targetLabel = "Clipboard",
        success = false,
        statusLabel = "Unavailable",
        verifiedDetail = "System ClipboardManager service unavailable.",
      )
    cm.setPrimaryClip(ClipData.newPlainText("Mousomi AI", text))
    val verified = verifier.verifyClipboardContains(text)
    return DeviceActionResult(
      id = "dev_${UUID.randomUUID()}",
      category = DeviceActionCategory.CLIPBOARD_COPY,
      command = "Copy text",
      targetLabel = text.take(28),
      success = verified,
      statusLabel = if (verified) "Copied" else "Unverified",
      verifiedDetail = if (verified) "Verified text written to Android clipboard." else "Clipboard write could not be verified.",
    )
  }

  fun createCalendarEvent(title: String, startTimeMs: Long, endTimeMs: Long): DeviceActionResult {
    val intent = intentManager.buildCalendarEventIntent(title, startTimeMs, endTimeMs)
    if (!verifier.verifyIntentResolvable(intent)) {
      return DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.CALENDAR_EVENT,
        command = "Create Calendar Event",
        targetLabel = title,
        success = false,
        statusLabel = "No Calendar App",
        verifiedDetail = "No compatible Calendar application found on device.",
      )
    }
    return runCatching {
      context.startActivity(intent)
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.CALENDAR_EVENT,
        command = "Create Calendar Event",
        targetLabel = title,
        success = true,
        statusLabel = "Calendar Composer Opened",
        verifiedDetail = "Opened system Calendar composer for '$title' for your final confirmation.",
      )
    }.getOrElse { err ->
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.CALENDAR_EVENT,
        command = "Create Calendar Event",
        targetLabel = title,
        success = false,
        statusLabel = "Failed",
        verifiedDetail = err.message ?: "Could not open Calendar.",
      )
    }
  }
}
