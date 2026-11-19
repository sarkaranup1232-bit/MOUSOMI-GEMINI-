package com.example.core.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.domain.model.PermissionCategory
import com.example.domain.model.PermissionCategoryItem
import com.example.domain.model.PermissionState
import com.example.domain.model.ScreenShareStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live Android Permission Manager & Repository for Mousomi AI (Module 33).
 * Inspects real Android OS & hardware states across all 20 categories without stale caching.
 */
@Singleton
class PermissionManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val _categories = MutableStateFlow<List<PermissionCategoryItem>>(emptyList())
  val categories: StateFlow<List<PermissionCategoryItem>> = _categories.asStateFlow()

  fun refreshAll(screenShareStatus: ScreenShareStatus = ScreenShareStatus.STOPPED): List<PermissionCategoryItem> {
    val items = buildAllCategories(screenShareStatus)
    _categories.value = items
    return items
  }

  fun isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

  fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

  fun hasNotificationListenerAccess(): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

  fun canScheduleExactAlarms(): Boolean {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
  }

  fun isIgnoringBatteryOptimizations(): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true
  }

  fun isBackgroundRestricted(): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) am.isBackgroundRestricted else false
  }

  fun isAccessibilityEnabledForPackage(): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabled.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
  }

  fun isNetworkAvailable(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  fun buildAllCategories(screenShareStatus: ScreenShareStatus = ScreenShareStatus.STOPPED): List<PermissionCategoryItem> {
    val checkedAt = System.currentTimeMillis()
    val pm = context.packageManager

    val micGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
    val camGranted = isPermissionGranted(Manifest.permission.CAMERA)
    val hasCameraHw = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    val coarseLocationGranted = isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    val fineLocationGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
    val postNotificationsGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
      } else {
        true
      }
    val bluetoothState =
      if (!pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
        PermissionState.NOT_AVAILABLE
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT) &&
          isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
        ) {
          PermissionState.GRANTED
        } else {
          PermissionState.NOT_REQUESTED
        }
      } else {
        PermissionState.GRANTED
      }

    val hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
    val nfcAdapter = if (hasNfc) runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull() else null
    val nfcState =
      when {
        !hasNfc || nfcAdapter == null -> PermissionState.NOT_AVAILABLE
        nfcAdapter.isEnabled -> PermissionState.GRANTED
        else -> PermissionState.REQUIRES_SETTINGS
      }

    val hasUsbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    val usbMgr = if (hasUsbHost) runCatching { context.getSystemService(Context.USB_SERVICE) as? UsbManager }.getOrNull() else null
    val usbState =
      when {
        !hasUsbHost || usbMgr == null -> PermissionState.NOT_AVAILABLE
        else -> PermissionState.ZERO_PERMISSION_PICKER
      }

    val batteryState =
      when {
        isBackgroundRestricted() -> PermissionState.RESTRICTED
        isIgnoringBatteryOptimizations() -> PermissionState.GRANTED
        else -> PermissionState.REQUIRES_SETTINGS
      }

    return listOf(
      PermissionCategoryItem(
        category = PermissionCategory.MICROPHONE,
        state = if (micGranted) PermissionState.GRANTED else PermissionState.NOT_REQUESTED,
        manifestPermission = Manifest.permission.RECORD_AUDIO,
        whyNeeded = "Required for voice dictation, Live Voice mode, and AudioRecord DSP smoke visualizer.",
        dataUsageDisclosure = "Audio is captured strictly while Voice or Live mode runs, with immediate mic release on stop.",
        howToManage = "Tap Enable to allow, or manage anytime in Android App Info > Permissions > Microphone.",
        isRuntimeRequestable = true,
        relatedFeature = "Voice Assistant & Smoke Orb",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.CAMERA,
        state = if (!hasCameraHw) PermissionState.NOT_AVAILABLE else if (camGranted) PermissionState.GRANTED else PermissionState.NOT_REQUESTED,
        manifestPermission = Manifest.permission.CAMERA,
        whyNeeded = "Lets you snap a fresh photo directly into chat for visual analysis or editing.",
        dataUsageDisclosure = "Camera opens only when you tap Take Photo; background camera capture is never performed.",
        howToManage = "Tap Enable to allow Camera, or manage in Android App Info > Permissions > Camera.",
        isRuntimeRequestable = hasCameraHw,
        relatedFeature = "Camera Capture & Vision",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.PHOTO_PICKER,
        state = PermissionState.ZERO_PERMISSION_PICKER,
        whyNeeded = "Attach photos or videos into chat for vision analysis and image editing.",
        dataUsageDisclosure = "Uses Android's privacy-preserving Photo Picker. Broad gallery storage permissions are never requested.",
        howToManage = "Zero broad permissions held; Mousomi only sees individual items you pick.",
        isOptionalFeature = true,
        relatedFeature = "Image Analysis & Editing",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.DOCUMENT_PICKER,
        state = PermissionState.ZERO_PERMISSION_PICKER,
        whyNeeded = "Read PDF, DOCX, XLSX, CSV, JSON, and audio files you select.",
        dataUsageDisclosure = "Uses Android Storage Access Framework (SAF). Only files you explicitly select are temporarily cached and cleaned.",
        howToManage = "Managed per-selection; no broad file access permission is held.",
        isOptionalFeature = true,
        relatedFeature = "Documents & Data Analytics",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.NOTIFICATIONS,
        state = if (postNotificationsGranted) PermissionState.GRANTED else PermissionState.NOT_REQUESTED,
        manifestPermission =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
        whyNeeded = "Displays mandatory foreground controls, local Reminders/Timers, and Emergency Stop.",
        dataUsageDisclosure = "Used strictly for active session status notifications and scheduled reminders.",
        howToManage = "Tap Enable (Android 13+) or manage in App Notification Settings.",
        isRuntimeRequestable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        relatedFeature = "Reminders, Timers & Live Controls",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.EXACT_ALARM,
        state = if (canScheduleExactAlarms()) PermissionState.GRANTED else PermissionState.REQUIRES_SETTINGS,
        whyNeeded = "Allows local reminders and countdown notifications to trigger precisely on time.",
        dataUsageDisclosure = "Alarms are also verified against the system Clock app via AlarmClock intents.",
        howToManage = "Open Alarms & Reminders settings on Android 12+ to grant or revoke exact alarm scheduling.",
        requiresSpecialSettings = true,
        relatedFeature = "Smart Alarm & Reminder Agent",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.SCREEN_CAPTURE,
        state = if (screenShareStatus != ScreenShareStatus.STOPPED) PermissionState.GRANTED else PermissionState.REQUIRES_SETTINGS,
        whyNeeded = "Reads frames only during an active user-consented Screen Intelligence session.",
        dataUsageDisclosure = "Requires explicit Android system MediaProjection consent every session; wiped automatically on Stop.",
        howToManage = "Start/stop per session from Screen Assistant or Emergency Stop.",
        requiresSpecialSettings = true,
        relatedFeature = "Screen Intelligence & Region Crop",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.OVERLAY,
        state = if (canDrawOverlays()) PermissionState.GRANTED else PermissionState.REQUIRES_SETTINGS,
        whyNeeded = "Shows the optional system-wide Floating Orbital AI Orb & Edge Light across other apps.",
        dataUsageDisclosure = "Displays visual controls only when you explicitly toggle System Overlay; Hide anytime from notification.",
        howToManage = "Open Display Over Other Apps system settings to grant or revoke.",
        requiresSpecialSettings = true,
        relatedFeature = "Floating Orbit Overlay & Edge Light",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.NOTIFICATION_LISTENER,
        state = if (hasNotificationListenerAccess()) PermissionState.GRANTED else PermissionState.REQUIRES_SETTINGS,
        whyNeeded = "Optional Smart Notification summaries for allowlisted apps.",
        dataUsageDisclosure = "Runs locally with OTP, bank, and password redaction enabled.",
        howToManage = "Enable or revoke Mousomi AI inside Android Notification Access settings.",
        requiresSpecialSettings = true,
        relatedFeature = "Notification Intelligence",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.BLUETOOTH_AUDIO,
        state = bluetoothState,
        manifestPermission =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null,
        whyNeeded = "Routes Live Voice audio cleanly to connected Bluetooth headsets and scans nearby BT devices (neverForLocation).",
        dataUsageDisclosure = "Used solely for Bluetooth headset routing and Bluetooth quick settings verification.",
        howToManage = "Grant Nearby Devices on Android 12+ or manage in App Info > Permissions.",
        isRuntimeRequestable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && bluetoothState != PermissionState.NOT_AVAILABLE,
        relatedFeature = "Bluetooth Audio & Settings Control",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.LOCATION_COARSE,
        state = if (coarseLocationGranted) PermissionState.GRANTED else PermissionState.NOT_REQUESTED,
        manifestPermission = Manifest.permission.ACCESS_COARSE_LOCATION,
        whyNeeded = "Optional approximate city/region context for local weather or nearby recommendations.",
        dataUsageDisclosure = "Only checked on demand if granted; background location is never requested.",
        howToManage = "Optional: grant/revoke in App Info > Permissions > Location.",
        isRuntimeRequestable = true,
        relatedFeature = "Local Context (Optional)",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.LOCATION_FINE,
        state = if (fineLocationGranted) PermissionState.GRANTED else PermissionState.NOT_REQUESTED,
        manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION,
        whyNeeded = "Strictly optional precise location only if you request precise local assistance.",
        dataUsageDisclosure = "Foreground only if granted; never polled in the background.",
        howToManage = "Optional: grant/revoke anytime in App Info > Permissions > Location.",
        isRuntimeRequestable = true,
        relatedFeature = "Precise Local Assistance (Optional)",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.CONTACTS_PICKER,
        state = PermissionState.ZERO_PERMISSION_PICKER,
        whyNeeded = "Optionally pick a single contact card using Android's system Contact Picker.",
        dataUsageDisclosure = "Never requests READ_CONTACTS; uses zero-permission ContactsContract picker when invoked.",
        howToManage = "Zero standing permission held.",
        relatedFeature = "Contact Lookup",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.PHONE_DIAL,
        state = PermissionState.ZERO_PERMISSION_PICKER,
        whyNeeded = "Prepares the system phone dialer with a number for you to confirm.",
        dataUsageDisclosure = "Uses ACTION_DIAL only; never places direct calls or requests CALL_PHONE.",
        howToManage = "Zero permission required; you always tap Call yourself.",
        relatedFeature = "Phone Dialer Helper",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.INSTALLED_APPS,
        state = PermissionState.GRANTED,
        whyNeeded = "Resolves app icons, app search, and safe launch actions for Device Assistant.",
        dataUsageDisclosure = "Queries launcher-visible packages locally via Android <queries>; never uses QUERY_ALL_PACKAGES.",
        howToManage = "Always scoped by OS package visibility.",
        isEnabledByDefaultSafe = true,
        relatedFeature = "Device App Launcher",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.INTERNET_STATE,
        state = if (isNetworkAvailable()) PermissionState.GRANTED else PermissionState.RESTRICTED,
        whyNeeded = "Connects to Gemini, Groq, HTTPS Backend Proxy, and verified HTTPS search endpoints.",
        dataUsageDisclosure = "Install-time normal network & Wi-Fi state access; HTTPS enforced for all remote traffic.",
        howToManage = "Controlled by device Wi-Fi / Mobile Data settings.",
        isEnabledByDefaultSafe = true,
        relatedFeature = "AI Cloud, Research & Web Actions",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.ACCESSIBILITY,
        state = if (isAccessibilityEnabledForPackage()) PermissionState.GRANTED else PermissionState.NOT_APPLICABLE,
        whyNeeded = "Guided system shortcuts only; Mousomi avoids intrusive Accessibility automation to stay Play compliant.",
        dataUsageDisclosure = "No automated arbitrary screen-tapping or hidden Accessibility service bundled.",
        howToManage = "Optional OS Accessibility preferences can be opened in System Settings.",
        requiresSpecialSettings = true,
        relatedFeature = "Guided Device Control",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.BATTERY_OPTIMIZATION,
        state = batteryState,
        whyNeeded = "Prevents Android from pausing long Deep Research, Alarms, or Live Voice foreground sessions.",
        dataUsageDisclosure = "Checks PowerManager & background restriction honestly; never forces unrestricted battery use.",
        howToManage = "Open Android Battery settings for Mousomi AI to review power optimization.",
        requiresSpecialSettings = true,
        relatedFeature = "Background & Alarm Reliability",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.NFC_HARDWARE,
        state = nfcState,
        whyNeeded = "Reports device NFC hardware readiness for optional tag intents.",
        dataUsageDisclosure = "Read-only hardware availability status check; no background NFC polling.",
        howToManage = "Toggle NFC in Android Connected Devices settings if supported.",
        requiresSpecialSettings = nfcState != PermissionState.NOT_AVAILABLE,
        relatedFeature = "Hardware Telemetry",
        lastCheckedAtMs = checkedAt,
      ),
      PermissionCategoryItem(
        category = PermissionCategory.USB_DEVICES,
        state = usbState,
        whyNeeded = "Reports Android USB Host availability for user-confirmed external accessories.",
        dataUsageDisclosure = "Only enumerates USB devices upon explicit user prompt; each accessory requires OS dialog approval.",
        howToManage = "Managed automatically by Android per connected USB device.",
        relatedFeature = "Hardware Telemetry",
        lastCheckedAtMs = checkedAt,
      ),
    )
  }

  fun createAppInfoSettingsIntent(): Intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${context.packageName}"),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

  fun createExactAlarmSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"),
      ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    } else {
      createAppInfoSettingsIntent()
    }

  fun createAppNotificationSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      createAppInfoSettingsIntent()
    }

  fun createBatteryOptimizationInfoIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

  fun createAccessibilitySettingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
