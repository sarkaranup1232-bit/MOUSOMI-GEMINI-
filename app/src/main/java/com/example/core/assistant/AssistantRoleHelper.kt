package com.example.core.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

data class AssistantRoleStatus(
  val isDefaultAssistant: Boolean,
  val isRoleSupported: Boolean,
  val setupSummary: String,
)

object AssistantRoleHelper {

  fun isDefaultAssistant(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
        return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
      }
    }
    val current =
      runCatching {
        Settings.Secure.getString(context.contentResolver, "assistant")
      }.getOrNull().orEmpty()
    return current.contains(context.packageName)
  }

  fun getStatus(context: Context): AssistantRoleStatus {
    val isHeld = isDefaultAssistant(context)
    val isSupported =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
      } else {
        true
      }
    val summary =
      when {
        isHeld ->
          "Mousomi AI is your Default Digital Assistant. Hold Power/Home or swipe from a bottom corner to summon the Neon Edge Line & Floating Assistant over any app."
        isSupported ->
          "Set Mousomi AI as Default Digital Assistant to use Android corner-swipe or Home/Power hold triggers. You can also launch anytime via Quick Settings Tile or Floating Bubble."
        else ->
          "System Default Assistant role is restricted on this build. Use the Quick Settings Tile or Floating Bubble to trigger Mousomi over any app."
      }
    return AssistantRoleStatus(
      isDefaultAssistant = isHeld,
      isRoleSupported = isSupported,
      setupSummary = summary,
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
}
