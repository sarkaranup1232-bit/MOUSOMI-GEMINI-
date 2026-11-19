package com.example.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.alarms.ReminderManager
import com.example.core.alarms.ReminderNotificationManager
import com.example.data.repository.AddOnRepository
import com.example.domain.model.AlarmRecurrence
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderBroadcastReceiver : BroadcastReceiver() {

  @Inject lateinit var notificationManager: ReminderNotificationManager
  @Inject lateinit var addOnRepository: AddOnRepository
  @Inject lateinit var reminderManager: ReminderManager

  override fun onReceive(context: Context, intent: Intent?) {
    if (intent == null) return
    val reminderId = intent.getStringExtra(ReminderNotificationManager.EXTRA_REMINDER_ID) ?: return
    val title = intent.getStringExtra(ReminderNotificationManager.EXTRA_REMINDER_TITLE) ?: "Mousomi Reminder"

    if (intent.action == ReminderNotificationManager.ACTION_DISMISS_REMINDER) {
      val nm = context.getSystemService(NotificationManager::class.java)
      nm?.cancel(reminderId.hashCode())
      return
    }

    if (intent.action == ReminderNotificationManager.ACTION_TRIGGER_REMINDER) {
      notificationManager.postReminderNotification(
        reminderId = reminderId,
        title = title,
        subtitle = "Mousomi AI Reminder • Tap to open assistant",
      )
      val pending = goAsync()
      CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
          val entry = addOnRepository.getRemindersSnapshot().firstOrNull { it.id == reminderId }
          if (entry != null) {
            val now = System.currentTimeMillis()
            if (entry.recurrence == AlarmRecurrence.ONCE) {
              addOnRepository.upsertReminder(
                entry.copy(
                  enabled = false,
                  status = "Triggered",
                  lastTriggeredAtMs = now,
                  verificationNote = "Notification delivered.",
                )
              )
            } else {
              val nextMs =
                entry.scheduledAtMs +
                  if (entry.recurrence == AlarmRecurrence.DAILY) 86_400_000L else 7 * 86_400_000L
              addOnRepository.upsertReminder(
                entry.copy(
                  scheduledAtMs = nextMs,
                  lastTriggeredAtMs = now,
                  status = "Scheduled",
                  verificationNote = "Delivered and rescheduled next occurrence.",
                )
              )
              reminderManager.rescheduleAllOnBoot()
            }
          }
        } finally {
          pending.finish()
        }
      }
    }
  }
}

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
  @Inject lateinit var reminderManager: ReminderManager

  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        reminderManager.checkMissedReminders()
        reminderManager.rescheduleAllOnBoot()
      } finally {
        pending.finish()
      }
    }
  }
}
