package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.permissions.EmergencyStopController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Explicit user-initiated foreground service for Mousomi Live Voice sessions.
 * Displays a persistent status notification with an immediate Stop action
 * that triggers EmergencyStopController. Never runs invisibly in background.
 */
@AndroidEntryPoint
class LiveVoiceSessionService : Service() {

  @Inject lateinit var emergencyStopController: EmergencyStopController

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      emergencyStopController.triggerEmergencyStop("Live Voice stopped from notification.")
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    val hasMic =
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasMic) {
      stopSelf()
      return START_NOT_STICKY
    }

    startAsForeground()
    return START_NOT_STICKY
  }

  private fun startAsForeground() {
    ensureChannel()
    val stopIntent = Intent(this, LiveVoiceSessionService::class.java).apply { action = ACTION_STOP }
    val stopPending =
      PendingIntent.getService(
        this,
        301,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      }
    val openPending =
      PendingIntent.getActivity(
        this,
        302,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Mousomi Live Voice Active")
        .setContentText("Hands-free voice conversation active. Tap Stop anytime.")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setContentIntent(openPending)
        .addAction(0, "Stop Live", stopPending)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java)
      if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
        nm?.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID,
            "Mousomi Live Voice",
            NotificationManager.IMPORTANCE_LOW,
          )
        )
      }
    }
  }

  companion object {
    const val CHANNEL_ID = "mousomi_live_voice"
    const val NOTIFICATION_ID = 4403
    const val ACTION_START = "com.example.action.START_LIVE_VOICE"
    const val ACTION_STOP = "com.example.action.STOP_LIVE_VOICE"

    fun buildStartIntent(context: Context): Intent =
      Intent(context, LiveVoiceSessionService::class.java).apply { action = ACTION_START }

    fun buildStopIntent(context: Context): Intent =
      Intent(context, LiveVoiceSessionService::class.java).apply { action = ACTION_STOP }
  }
}
