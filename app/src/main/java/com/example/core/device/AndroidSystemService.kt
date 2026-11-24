package com.example.core.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.core.voice.HotwordCapabilityManager
import com.example.data.repository.AddOnRepository
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

enum class VolumeTargetStream(val label: String, val androidStream: Int) {
  MEDIA("Media", AudioManager.STREAM_MUSIC),
  RING("Ringtone", AudioManager.STREAM_RING),
  ALARM("Alarm", AudioManager.STREAM_ALARM),
  NOTIFICATION("Notification", AudioManager.STREAM_NOTIFICATION),
  CALL("Call", AudioManager.STREAM_VOICE_CALL),
}

enum class MediaPlaybackCommand(val label: String, val keyCode: Int) {
  PLAY_PAUSE("Play / Pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
  PLAY("Play", KeyEvent.KEYCODE_MEDIA_PLAY),
  PAUSE("Pause", KeyEvent.KEYCODE_MEDIA_PAUSE),
  NEXT("Next Track", KeyEvent.KEYCODE_MEDIA_NEXT),
  PREVIOUS("Previous Track", KeyEvent.KEYCODE_MEDIA_PREVIOUS),
  STOP("Stop Media", KeyEvent.KEYCODE_MEDIA_STOP),
}

/**
 * Verified Android System Control Service:
 * - Audio volume inspection and safe adjustment via [AudioManager]
 * - Media playback controls via [AudioManager.dispatchMediaKeyEvent]
 * - Battery & device OS/One UI capability reporting
 * - Foreground-only on-demand Location lookup gated strictly on granted location permission
 */
@Singleton
class AndroidSystemService @Inject constructor(
  @ApplicationContext private val context: Context,
  private val hotwordCapabilityManager: HotwordCapabilityManager,
  private val addOnRepository: AddOnRepository? = null,
) {

  suspend fun adjustVolume(
    stream: VolumeTargetStream,
    targetPercent: Int? = null,
    deltaDirection: Int = 0, // +1 up, -1 down, 0 read-only / set exact
    mute: Boolean? = null,
  ): DeviceActionResult {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      ?: return recordFailure(
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Adjust ${stream.label} volume",
        targetLabel = "${stream.label} Volume",
        detail = "Android AudioManager is unavailable on this device.",
      )

    val maxVol = am.getStreamMaxVolume(stream.androidStream).coerceAtLeast(1)
    val beforeVol = am.getStreamVolume(stream.androidStream)

    val detail =
      runCatching {
        when {
          mute == true -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              am.adjustStreamVolume(stream.androidStream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            } else {
              am.setStreamVolume(stream.androidStream, 0, AudioManager.FLAG_SHOW_UI)
            }
          }
          mute == false -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              am.adjustStreamVolume(stream.androidStream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            }
          }
          targetPercent != null -> {
            val pct = targetPercent.coerceIn(0, 100)
            val targetIdx = ((pct / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
            am.setStreamVolume(stream.androidStream, targetIdx, AudioManager.FLAG_SHOW_UI)
          }
          deltaDirection > 0 -> {
            am.adjustStreamVolume(stream.androidStream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
          }
          deltaDirection < 0 -> {
            am.adjustStreamVolume(stream.androidStream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
          }
        }
        val afterVol = am.getStreamVolume(stream.androidStream)
        val afterPct = ((afterVol * 100f) / maxVol).roundToInt().coerceIn(0, 100)
        if (targetPercent == null && deltaDirection == 0 && mute == null) {
          "${stream.label} volume is currently $afterVol / $maxVol ($afterPct%)."
        } else if (beforeVol == afterVol && mute == null && deltaDirection != 0) {
          "${stream.label} volume stayed at $afterVol / $maxVol ($afterPct%) — device may be at limit or locked by Do Not Disturb."
        } else {
          "Updated ${stream.label} volume: $beforeVol → $afterVol / $maxVol ($afterPct%)."
        }
      }.getOrElse { err ->
        return recordFailure(
          category = DeviceActionCategory.SYSTEM_SETTINGS,
          command = "Adjust ${stream.label} volume",
          targetLabel = "${stream.label} Volume",
          detail = err.message ?: "Android rejected volume adjustment (check Do Not Disturb access).",
        )
      }

    return recordSuccess(
      category = DeviceActionCategory.SYSTEM_SETTINGS,
      command = "Adjust ${stream.label} volume",
      targetLabel = "${stream.label} Volume",
      status = "Volume Updated",
      detail = detail,
    )
  }

  suspend fun controlMediaPlayback(command: MediaPlaybackCommand): DeviceActionResult {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      ?: return recordFailure(
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Media: ${command.label}",
        targetLabel = "Media Playback",
        detail = "AudioManager service unavailable.",
      )

    return runCatching {
      val now = SystemClock.uptimeMillis()
      am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, command.keyCode, 0))
      am.dispatchMediaKeyEvent(KeyEvent(now, now + 15L, KeyEvent.ACTION_UP, command.keyCode, 0))
      recordSuccess(
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Media: ${command.label}",
        targetLabel = command.label,
        status = "Media Key Dispatched",
        detail = "Sent ${command.label} media key event via AudioManager to active audio session.",
      )
    }.getOrElse { err ->
      recordFailure(
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Media: ${command.label}",
        targetLabel = command.label,
        detail = err.message ?: "Failed to dispatch media playback event.",
      )
    }
  }

  suspend fun getBatteryAndDeviceSummary(): DeviceActionResult {
    val batteryStatusIntent =
      runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
    val plugged = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val pct =
      if (level >= 0) ((level * 100f) / scale).roundToInt()
      else {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
      }
    val chargingText =
      when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Charging (AC)"
        BatteryManager.BATTERY_PLUGGED_USB -> "Charging (USB)"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging (Wireless)"
        else -> "On battery"
      }
    val profile = hotwordCapabilityManager.detectDeviceProfile()
    val oneUiNote = profile.oneUiVersion?.let { ", One UI $it" }.orEmpty()
    val summary =
      buildString {
        if (pct >= 0) append("Battery: $pct% ($chargingText). ") else append("Battery level unavailable. ")
        append("Device: ${profile.manufacturer} ${profile.model}, Android ${profile.androidRelease} (API ${profile.sdkInt}$oneUiNote).")
      }
    return recordSuccess(
      category = DeviceActionCategory.SYSTEM_SETTINGS,
      command = "Battery & Device Info",
      targetLabel = "Device Status",
      status = "Status Read",
      detail = summary,
    )
  }

  @SuppressLint("MissingPermission")
  suspend fun getForegroundLocationSummary(): DeviceActionResult {
    val hasFine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val hasCoarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) {
      val settingsIntent =
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { context.startActivity(settingsIntent) }
      return recordFailure(
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Check Location",
        targetLabel = "Location",
        detail =
          "Location permission is not granted. Enable Location in App Permissions or review Location Settings (foreground on-demand only).",
      )
    }
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      ?: return recordFailure(
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Check Location",
        targetLabel = "Location",
        detail = "Android LocationManager unavailable.",
      )
    val providers = lm.getProviders(true)
    val bestLocation: Location? =
      providers
        .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }

    return if (bestLocation != null) {
      val lat = String.format(Locale.US, "%.4f", bestLocation.latitude)
      val lon = String.format(Locale.US, "%.4f", bestLocation.longitude)
      val acc = bestLocation.accuracy.roundToInt()
      recordSuccess(
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Check Location",
        targetLabel = "Current Location",
        status = "Location Read",
        detail =
          "Last known device location: $lat, $lon (accuracy ~${acc}m via ${bestLocation.provider ?: "system"}). Background location is never polled.",
      )
    } else {
      recordFailure(
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Check Location",
        targetLabel = "Location",
        detail = "Location permission is granted, but no recent cached fix is available (GPS/Location toggle may be off).",
      )
    }
  }

  private suspend fun recordSuccess(
    category: DeviceActionCategory,
    command: String,
    targetLabel: String,
    status: String,
    detail: String,
  ): DeviceActionResult {
    val res =
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = category,
        command = command,
        targetLabel = targetLabel,
        success = true,
        statusLabel = status,
        verifiedDetail = detail,
      )
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  private suspend fun recordFailure(
    category: DeviceActionCategory,
    command: String,
    targetLabel: String,
    detail: String,
  ): DeviceActionResult {
    val res =
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = category,
        command = command,
        targetLabel = targetLabel,
        success = false,
        statusLabel = "Unavailable",
        verifiedDetail = detail,
      )
    addOnRepository?.recordDeviceAction(res)
    return res
  }
}
