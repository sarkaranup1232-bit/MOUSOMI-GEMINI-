package com.example.service

import android.content.Context
import android.content.Intent
import com.example.core.assistant.EdgeActivationEdge
import dagger.hilt.android.AndroidEntryPoint

/**
 * Dedicated Foreground Service hosting the SYSTEM_ALERT_WINDOW Floating Control Bar,
 * Floating Chat Panel, Neon Border Light, and draggable Orbital Bubble above other apps
 * on Android 12+ (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE`).
 *
 * Delegates window lifecycle, ACTION_MOVE boundary-clamped dragging, MediaProjection launch,
 * and Live Orbit Voice coordination directly to [MousomiOverlayService].
 */
@AndroidEntryPoint
class FloatingAssistantService : MousomiOverlayService() {

  companion object {
    fun buildStartIntent(context: Context): Intent =
      Intent(context, FloatingAssistantService::class.java).apply {
        action = ACTION_SHOW_CONTROL_BAR
      }

    fun buildControlBarIntent(context: Context): Intent =
      Intent(context, FloatingAssistantService::class.java).apply {
        action = ACTION_SHOW_CONTROL_BAR
      }

    fun buildPanelIntent(context: Context, autoVoice: Boolean = false): Intent =
      Intent(context, FloatingAssistantService::class.java).apply {
        action = ACTION_SHOW_ASSISTANT_PANEL
        putExtra(EXTRA_PANEL_VOICE_AUTO, autoVoice)
      }

    fun buildActivateIntent(
      context: Context,
      edge: EdgeActivationEdge = EdgeActivationEdge.BOTTOM_CORNER,
      expandPanel: Boolean = true,
      autoVoice: Boolean = true,
    ): Intent =
      Intent(context, FloatingAssistantService::class.java).apply {
        action = ACTION_ACTIVATE_ASSISTANT
        putExtra(EXTRA_ACTIVATION_EDGE, edge.name)
        putExtra(EXTRA_EXPAND_PANEL, expandPanel)
        putExtra(EXTRA_PANEL_VOICE_AUTO, autoVoice)
      }

    fun buildStopIntent(context: Context): Intent =
      Intent(context, FloatingAssistantService::class.java).apply {
        action = ACTION_STOP
      }
  }
}
