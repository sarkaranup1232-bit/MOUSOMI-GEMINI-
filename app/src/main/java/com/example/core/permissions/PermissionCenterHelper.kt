package com.example.core.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.core.assistant.AssistantRoleHelper
import com.example.domain.model.PermissionDashboardItem
import com.example.domain.model.ScreenShareStatus

object PermissionCenterHelper {

  fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED

  fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
      PackageManager.PERMISSION_GRANTED

  fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabled.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
  }

  fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

  fun hasNotificationListenerAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

  fun hasPostNotificationsPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  fun buildDashboardItems(
    context: Context,
    screenShareStatus: ScreenShareStatus,
  ): List<PermissionDashboardItem> =
    listOf(
      PermissionDashboardItem(
        id = "mic",
        title = "Microphone (Voice Input & Live)",
        reason = "Captures speech only when you tap the mic or enable Continuous Voice.",
        isGranted = hasAudioPermission(context),
        howToDisable = "Revoke Microphone access in Android App Info > Permissions.",
      ),
      PermissionDashboardItem(
        id = "camera",
        title = "Camera (Lens & Photo Capture)",
        reason = "Lets you capture photos in chat or inspect objects in Mousomi Lens on demand.",
        isGranted = hasCameraPermission(context),
        howToDisable = "Revoke Camera access in Android App Info > Permissions > Camera.",
      ),
      PermissionDashboardItem(
        id = "contacts",
        title = "Contacts (Voice Lookup & Picker)",
        reason = "Optional lookup for commands like 'WhatsApp Alice' or 'Call Bob'; Contact Picker needs zero permission.",
        isGranted = hasContactsPermission(context),
        howToDisable = "Revoke Contacts in App Info > Permissions, or clear to use Contact Picker only.",
      ),
      PermissionDashboardItem(
        id = "overlay",
        title = "Display Over Other Apps (Floating Orb & Edge Light)",
        reason = "Allows the optional Floating AI Orb to stay accessible across apps.",
        isGranted = canDrawOverlays(context),
        howToDisable = "Turn off System Overlay toggle or disable in Special App Access.",
      ),
      PermissionDashboardItem(
        id = "screen_share",
        title = "Screen Capture (MediaProjection)",
        reason = "Temporarily reads screen frames only after explicit system consent until Stop.",
        isGranted = screenShareStatus != ScreenShareStatus.STOPPED,
        howToDisable = "Tap Stop Screen Sharing pill or notification action anytime.",
      ),
      PermissionDashboardItem(
        id = "notif_listener",
        title = "Notification Listener (Summaries & Read-Aloud)",
        reason = "Reads user-allowlisted alerts locally; OTPs, passwords, and codes are blocked.",
        isGranted = hasNotificationListenerAccess(context),
        howToDisable = "Disable Mousomi AI in Settings > Notification Access.",
      ),
      PermissionDashboardItem(
        id = "accessibility",
        title = "Accessibility Assistant (Optional UI Control)",
        reason = "Enables labeled button taps, scrolling & text entry across apps; blocks password & payment nodes.",
        isGranted = isAccessibilityEnabled(context),
        howToDisable = "Disable in Android Settings > Accessibility > Mousomi Accessibility Assistant.",
      ),
      PermissionDashboardItem(
        id = "post_notif",
        title = "App Notifications (Foreground Status)",
        reason = "Displays ongoing indicator while Screen Sharing or Floating Orb service runs.",
        isGranted = hasPostNotificationsPermission(context),
        howToDisable = "Manage in Android App Info > Notifications.",
      ),
      PermissionDashboardItem(
        id = "assistant",
        title = "Default Digital Assistant Role",
        reason = "Lets long-press home/power gesture launch Mousomi Voice & Quick Assistant.",
        isGranted = AssistantRoleHelper.isDefaultAssistant(context),
        howToDisable = "Switch default assistant in Android Default Apps settings.",
      ),
      PermissionDashboardItem(
        id = "media_picker",
        title = "Photos, Videos & Documents (Zero-Permission Picker)",
        reason = "Uses privacy-preserving Android Photo Picker & SAF; broad storage permission is never requested.",
        isGranted = true,
        howToDisable = "Only single files you explicitly select are temporarily read.",
        isOptionalZeroPermissionNote = true,
      ),
    )

  fun createOverlaySettingsIntent(context: Context): Intent =
    Intent(
      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:${context.packageName}"),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

  fun createNotificationListenerSettingsIntent(): Intent =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

  fun createAccessibilitySettingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
