package com.example.core.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService

enum class SystemNavigationMode(val displayName: String) {
  GESTURE_SIDES_AND_BOTTOM("Gesture navigation (Swipe from sides and bottom)"),
  SAMSUNG_SWIPE_FROM_BOTTOM("Samsung Swipe from bottom (3-zone)"),
  TWO_BUTTON("2-button navigation"),
  THREE_BUTTON("Navigation buttons (3-button)"),
  UNKNOWN("System navigation mode"),
}

data class NavigationGestureDiagnostic(
  val navigationMode: SystemNavigationMode,
  val isCornerAssistGestureSupported: Boolean,
  val isCornerAssistEnabledInOs: Boolean?,
  val primaryTriggerSummary: String,
  val userActionHint: String?,
)

data class AssistantRoleStatus(
  val isDefaultAssistant: Boolean,
  val isRoleSupported: Boolean,
  val setupSummary: String,
  val isRoleHeld: Boolean = isDefaultAssistant,
  val isVoiceInteractionActiveService: Boolean = false,
  val isAssistActivityResolved: Boolean = false,
  val navigationDiagnostic: NavigationGestureDiagnostic? = null,
)

object AssistantRoleHelper {

  const val VOICE_INTERACTION_SERVICE_CLASS = "com.example.service.MousomiVoiceInteractionService"
  const val VOICE_INTERACTION_SESSION_SERVICE_CLASS = "com.example.service.MousomiVoiceInteractionSessionService"

  fun voiceInteractionComponent(context: Context): ComponentName =
    ComponentName(context, VOICE_INTERACTION_SERVICE_CLASS)

  fun isVoiceInteractionActive(context: Context): Boolean =
    runCatching {
      VoiceInteractionService.isActiveService(context, voiceInteractionComponent(context))
    }.getOrDefault(false)

  fun isAssistIntentResolvedToMousomi(context: Context): Boolean {
    val pm = context.packageManager
    val assistIntent = Intent(Intent.ACTION_ASSIST).addCategory(Intent.CATEGORY_DEFAULT)
    val candidates =
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.queryIntentActivities(
            assistIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
          )
        } else {
          @Suppress("DEPRECATION")
          pm.queryIntentActivities(assistIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
      }.getOrElse { emptyList() }
    return candidates.any { it.activityInfo?.packageName == context.packageName }
  }

  fun isDefaultAssistant(context: Context): Boolean {
    if (isVoiceInteractionActive(context)) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
        if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) return true
      }
    }
    val current =
      runCatching {
        Settings.Secure.getString(context.contentResolver, "assistant")
      }.getOrNull().orEmpty()
    val currentVis =
      runCatching {
        Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
      }.getOrNull().orEmpty()
    return current.contains(context.packageName) || currentVis.contains(context.packageName)
  }

  fun detectSystemNavigationMode(context: Context): Pair<SystemNavigationMode, Boolean?> {
    val cr = context.contentResolver
    val isSamsung =
      Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
        Build.BRAND.equals("samsung", ignoreCase = true)

    val rawNavMode =
      runCatching { Settings.Secure.getInt(cr, "navigation_mode", -1) }.getOrDefault(-1).let {
        if (it in 0..2) it
        else {
          val resId = context.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
          if (resId != 0) runCatching { context.resources.getInteger(resId) }.getOrDefault(-1) else -1
        }
      }

    // Read-only check for Samsung One UI's gesture detail ("Swipe from bottom" vs "Swipe from sides and bottom")
    val samsungGestureType =
      if (isSamsung) {
        runCatching { Settings.Global.getInt(cr, "navigation_bar_gesture_detail_type", -1) }
          .getOrDefault(-1)
      } else {
        -1
      }

    val mode =
      when {
        isSamsung && samsungGestureType == 0 && rawNavMode == 2 -> SystemNavigationMode.SAMSUNG_SWIPE_FROM_BOTTOM
        rawNavMode == 2 -> SystemNavigationMode.GESTURE_SIDES_AND_BOTTOM
        rawNavMode == 1 -> SystemNavigationMode.TWO_BUTTON
        rawNavMode == 0 ->
          if (isSamsung && samsungGestureType == 0) SystemNavigationMode.SAMSUNG_SWIPE_FROM_BOTTOM
          else SystemNavigationMode.THREE_BUTTON
        else -> SystemNavigationMode.UNKNOWN
      }

    val cornerToggle: Boolean? =
      runCatching {
        when (Settings.Secure.getInt(cr, "assist_gesture_enabled", -1)) {
          1 -> true
          0 -> false
          else -> null
        }
      }.getOrNull()

    return mode to cornerToggle
  }

  /**
   * Pure deterministic evaluator for Android 12 / Samsung Galaxy M51 (One UI 4.1) navigation modes.
   * Never mutates user settings; strictly reports which system trigger summons VoiceInteractionSession.
   */
  fun evaluateNavigationGestureCompatibility(
    isDefaultAssistant: Boolean,
    isVoiceInteractionActiveService: Boolean,
    isSamsungOneUi: Boolean,
    navigationMode: SystemNavigationMode,
    isCornerAssistEnabledInOs: Boolean?,
  ): NavigationGestureDiagnostic {
    if (!isDefaultAssistant) {
      return NavigationGestureDiagnostic(
        navigationMode = navigationMode,
        isCornerAssistGestureSupported = navigationMode == SystemNavigationMode.GESTURE_SIDES_AND_BOTTOM,
        isCornerAssistEnabledInOs = isCornerAssistEnabledInOs,
        primaryTriggerSummary = "Default Digital Assistant role is not active for Mousomi AI yet.",
        userActionHint =
          "Open Settings > Apps > Choose default apps > Digital assistant app and select Mousomi AI so Android binds VoiceInteractionService.",
      )
    }

    val visNote =
      if (!isVoiceInteractionActiveService) {
        " Note: VoiceInteractionService is not currently reported active; ACTION_ASSIST fallback will still open Mousomi."
      } else {
        ""
      }

    return when (navigationMode) {
      SystemNavigationMode.GESTURE_SIDES_AND_BOTTOM -> {
        if (isCornerAssistEnabledInOs == false) {
          NavigationGestureDiagnostic(
            navigationMode = navigationMode,
            isCornerAssistGestureSupported = true,
            isCornerAssistEnabledInOs = false,
            primaryTriggerSummary = "Gesture navigation active, but OS corner-swipe Assistant gesture is turned off.",
            userActionHint =
              if (isSamsungOneUi) {
                "Enable corner swipe in Settings > Display > Navigation bar > Swipe gestures > Assistant app (or swipe-from-corner toggle)."
              } else {
                "Enable 'Swipe to invoke assistant' in Android System > Gestures > System navigation settings."
              },
          )
        } else {
          NavigationGestureDiagnostic(
            navigationMode = navigationMode,
            isCornerAssistGestureSupported = true,
            isCornerAssistEnabledInOs = isCornerAssistEnabledInOs,
            primaryTriggerSummary =
              "Swipe diagonally inward from either bottom corner over any screen to launch Mousomi AI's Assistant session.$visNote",
            userActionHint =
              if (isSamsungOneUi) {
                "If bottom-corner swipe does not respond on One UI 4.1, verify Settings > Display > Navigation bar > Swipe gestures > More options is set to 'Swipe from sides and bottom' and 'Assistant app' is ON."
              } else {
                null
              },
          )
        }
      }

      SystemNavigationMode.SAMSUNG_SWIPE_FROM_BOTTOM ->
        NavigationGestureDiagnostic(
          navigationMode = navigationMode,
          isCornerAssistGestureSupported = false,
          isCornerAssistEnabledInOs = false,
          primaryTriggerSummary =
            "Samsung 'Swipe from bottom' divides the bottom edge into Recents/Home/Back and disables diagonal bottom-corner Assistant swipe.",
          userActionHint =
            "To use bottom-corner swipe on Galaxy M51 (One UI 4.1): open Settings > Display > Navigation bar > Swipe gestures > More options, choose 'Swipe from sides and bottom', and turn on 'Assistant app'. Otherwise use Quick Settings Tile or Side key.",
        )

      SystemNavigationMode.THREE_BUTTON ->
        NavigationGestureDiagnostic(
          navigationMode = navigationMode,
          isCornerAssistGestureSupported = false,
          isCornerAssistEnabledInOs = false,
          primaryTriggerSummary =
            "3-button Navigation is active: long-press the Home button (not corner swipe) to summon Mousomi AI over any app.$visNote",
          userActionHint =
            if (isSamsungOneUi) {
              "Corner-swipe requires switching Settings > Display > Navigation bar to 'Swipe gestures > Swipe from sides and bottom'."
            } else {
              "Corner-swipe requires Gesture navigation in Android System > Gestures > System navigation."
            },
        )

      SystemNavigationMode.TWO_BUTTON ->
        NavigationGestureDiagnostic(
          navigationMode = navigationMode,
          isCornerAssistGestureSupported = true,
          isCornerAssistEnabledInOs = isCornerAssistEnabledInOs,
          primaryTriggerSummary =
            "2-button navigation active: swipe diagonally from a bottom corner or long-press the Home pill to launch Mousomi AI.$visNote",
          userActionHint = null,
        )

      SystemNavigationMode.UNKNOWN ->
        NavigationGestureDiagnostic(
          navigationMode = navigationMode,
          isCornerAssistGestureSupported = true,
          isCornerAssistEnabledInOs = isCornerAssistEnabledInOs,
          primaryTriggerSummary =
            "Mousomi AI is registered as Default Assistant for corner swipe (Swipe from sides & bottom) and Home/Side-key Assist triggers.$visNote",
          userActionHint =
            if (isSamsungOneUi) {
              "On Samsung One UI 4.1 (Galaxy M51), verify Settings > Display > Navigation bar > Swipe gestures > More options > 'Swipe from sides and bottom' and ensure 'Assistant app' is enabled."
            } else {
              null
            },
        )
    }
  }

  fun getStatus(context: Context): AssistantRoleStatus {
    val isRoleSupported =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
      } else {
        true
      }
    val isRoleHeld =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
      } else {
        false
      }
    val isVisActive = isVoiceInteractionActive(context)
    val isAssistActivityResolved = isAssistIntentResolvedToMousomi(context)
    val isHeld = isRoleHeld || isVisActive || isDefaultAssistant(context)
    val isSamsung =
      Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
        Build.BRAND.equals("samsung", ignoreCase = true)
    val (navMode, cornerEnabled) = detectSystemNavigationMode(context)
    val navDiagnostic =
      evaluateNavigationGestureCompatibility(
        isDefaultAssistant = isHeld,
        isVoiceInteractionActiveService = isVisActive,
        isSamsungOneUi = isSamsung,
        navigationMode = navMode,
        isCornerAssistEnabledInOs = cornerEnabled,
      )

    val summary =
      when {
        isHeld -> navDiagnostic.primaryTriggerSummary
        isRoleSupported ->
          "Set Mousomi AI as Default Digital Assistant to use Android corner-swipe or Home/Side-key triggers. You can also launch anytime via Quick Settings Tile or Floating Bubble."
        else ->
          "System Default Assistant role is restricted on this build. Use the Quick Settings Tile or Floating Bubble to trigger Mousomi over any app."
      }
    return AssistantRoleStatus(
      isDefaultAssistant = isHeld,
      isRoleSupported = isRoleSupported,
      setupSummary = summary,
      isRoleHeld = isRoleHeld,
      isVoiceInteractionActiveService = isVisActive,
      isAssistActivityResolved = isAssistActivityResolved,
      navigationDiagnostic = navDiagnostic,
    )
  }

  fun createRequestAssistantIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      if (roleManager != null &&
        roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
        !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
      ) {
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
      }
    }
    return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
  }

  fun createNavigationBarSettingsIntent(): Intent =
    Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
