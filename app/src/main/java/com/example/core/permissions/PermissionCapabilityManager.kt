package com.example.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import com.example.core.assistant.AssistantRoleHelper
import com.example.core.voice.DeviceRuntimeProfile
import com.example.core.voice.HotwordCapabilityManager
import com.example.core.voice.HotwordCapabilityReport
import com.example.domain.model.PermissionCategory
import com.example.domain.model.PermissionCategoryItem
import com.example.domain.model.PermissionState
import com.example.domain.model.ScreenShareStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class RuntimeCapabilitySnapshot(
  val deviceProfile: DeviceRuntimeProfile,
  val hotwordReport: HotwordCapabilityReport,
  val isDefaultAssistant: Boolean,
  val hasMicPermission: Boolean,
  val hasContactsPermission: Boolean,
  val hasCameraPermission: Boolean,
  val hasLocationPermission: Boolean,
  val hasNotificationListener: Boolean,
  val hasAccessibilityEnabled: Boolean,
  val hasOverlayPermission: Boolean,
  val isDefaultDialer: Boolean,
  val hasAnswerPhoneCallsPermission: Boolean,
  val permissionCategories: List<PermissionCategoryItem>,
) {
  val canCallAnswerOrEndDirectly: Boolean
    get() =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        (isDefaultDialer || hasAnswerPhoneCallsPermission)

  val answerEndLimitationReason: String
    get() =
      if (canCallAnswerOrEndDirectly) {
        "TelecomManager call answer/end is permitted on this device."
      } else {
        "Android restricts programmatic call answer/end to the Default Phone/Dialer app or explicit ANSWER_PHONE_CALLS grant (not claimed by Mousomi AI)."
      }
}

/**
 * Centralized Runtime OS / OEM / Permission Capability Manager.
 * Combines [PermissionManager], [HotwordCapabilityManager], [AssistantRoleHelper],
 * and [TelecomManager] checks without ever re-prompting already granted permissions.
 */
@Singleton
class PermissionCapabilityManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val permissionManager: PermissionManager,
  private val hotwordCapabilityManager: HotwordCapabilityManager,
) {

  fun inspectRuntimeCapabilities(
    screenShareStatus: ScreenShareStatus = ScreenShareStatus.STOPPED,
  ): RuntimeCapabilitySnapshot {
    val profile = hotwordCapabilityManager.detectDeviceProfile()
    val hotwordReport = hotwordCapabilityManager.inspectCapabilities()
    val categories = permissionManager.refreshAll(screenShareStatus)
    val roleStatus = AssistantRoleHelper.getStatus(context)
    val telecomMgr = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    val isDefaultDialer =
      runCatching { telecomMgr?.defaultDialerPackage == context.packageName }.getOrDefault(false)
    val hasAnswerCalls =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        permissionManager.isPermissionGranted(Manifest.permission.ANSWER_PHONE_CALLS)
      } else {
        false
      }

    return RuntimeCapabilitySnapshot(
      deviceProfile = profile,
      hotwordReport = hotwordReport,
      isDefaultAssistant = roleStatus.isDefaultAssistant,
      hasMicPermission = permissionManager.isPermissionGranted(Manifest.permission.RECORD_AUDIO),
      hasContactsPermission = permissionManager.isPermissionGranted(Manifest.permission.READ_CONTACTS),
      hasCameraPermission = permissionManager.isPermissionGranted(Manifest.permission.CAMERA),
      hasLocationPermission =
        permissionManager.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
          permissionManager.isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
      hasNotificationListener = permissionManager.hasNotificationListenerAccess(),
      hasAccessibilityEnabled = permissionManager.isAccessibilityEnabledForPackage(),
      hasOverlayPermission = permissionManager.canDrawOverlays(),
      isDefaultDialer = isDefaultDialer,
      hasAnswerPhoneCallsPermission = hasAnswerCalls,
      permissionCategories = categories,
    )
  }

  fun isGranted(permission: String): Boolean = permissionManager.isPermissionGranted(permission)

  fun shouldRequestPermission(permission: String): Boolean =
    !permissionManager.isPermissionGranted(permission)

  fun statusForCategory(category: PermissionCategory): PermissionState? =
    permissionManager.categories.value.firstOrNull { it.category == category }?.state

  fun hasTelephonyFeature(): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
}
