package com.example.core.alarms

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.AddOnRepository
import com.example.domain.model.AlarmRecurrence
import com.example.domain.model.ReminderEntry
import com.example.domain.model.ReminderItemType
import com.example.service.ReminderBroadcastReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class AlarmPermissionManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun canScheduleExactAlarms(): Boolean {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      am.canScheduleExactAlarms()
    } else {
      true
    }
  }

  fun hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
}

@Singleton
class ReminderNotificationManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = context.getSystemService(NotificationManager::class.java)
      if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
        nm?.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID,
            "Mousomi Alarms & Reminders",
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = "Scheduled reminders, timers, and missed-reminder alerts."
            enableVibration(true)
          }
        )
      }
    }
  }

  fun postReminderNotification(reminderId: String, title: String, subtitle: String) {
    ensureChannel()
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    val openAppIntent =
      PendingIntent.getActivity(
        context,
        reminderId.hashCode(),
        Intent(context, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val dismissIntent =
      PendingIntent.getBroadcast(
        context,
        (reminderId + "_dismiss").hashCode(),
        Intent(context, ReminderBroadcastReceiver::class.java).apply {
          action = ACTION_DISMISS_REMINDER
          putExtra(EXTRA_REMINDER_ID, reminderId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(subtitle)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent)
        .addAction(0, "Dismiss", dismissIntent)
        .build()

    nm.notify(reminderId.hashCode(), notification)
  }

  companion object {
    const val CHANNEL_ID = "mousomi_reminders"
    const val ACTION_TRIGGER_REMINDER = "com.example.action.TRIGGER_REMINDER"
    const val ACTION_DISMISS_REMINDER = "com.example.action.DISMISS_REMINDER"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
  }
}

@Singleton
class AlarmManagerRepository @Inject constructor(
  private val addOnRepository: AddOnRepository,
) {
  fun observeAll(): Flow<List<ReminderEntry>> = addOnRepository.observeReminders()

  suspend fun getSnapshot(): List<ReminderEntry> = addOnRepository.getRemindersSnapshot()

  suspend fun save(entry: ReminderEntry) = addOnRepository.upsertReminder(entry)

  suspend fun delete(id: String) = addOnRepository.deleteReminder(id)
}

@Singleton
class AlarmScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
  private val permissionManager: AlarmPermissionManager,
) {
  fun dispatchSystemClockAlarm(
    hour: Int,
    minute: Int,
    message: String,
    recurrence: AlarmRecurrence,
    daysOfWeek: ArrayList<Int> = arrayListOf(),
  ): Result<String> {
    val intent =
      Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
        putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
        putExtra(AlarmClock.EXTRA_MESSAGE, message.ifBlank { "Mousomi Alarm" })
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        if (recurrence == AlarmRecurrence.DAILY) {
          putExtra(
            AlarmClock.EXTRA_DAYS,
            arrayListOf(
              Calendar.SUNDAY,
              Calendar.MONDAY,
              Calendar.TUESDAY,
              Calendar.WEDNESDAY,
              Calendar.THURSDAY,
              Calendar.FRIDAY,
              Calendar.SATURDAY,
            ),
          )
        } else if (recurrence == AlarmRecurrence.WEEKLY && daysOfWeek.isNotEmpty()) {
          putExtra(AlarmClock.EXTRA_DAYS, daysOfWeek)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    val resolved = intent.resolveActivity(context.packageManager)
      ?: return Result.failure(
        IllegalStateException("No system Clock / Alarm app is available to verify this alarm.")
      )

    return runCatching {
      context.startActivity(intent)
      "Opened system Clock (${resolved.packageName}) to confirm %02d:%02d alarm.".format(hour, minute)
    }
  }

  fun scheduleLocalReminderTrigger(entry: ReminderEntry): Pair<Boolean, String> {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
      ?: return false to "Android AlarmManager service unavailable."

    val triggerIntent =
      Intent(context, ReminderBroadcastReceiver::class.java).apply {
        action = ReminderNotificationManager.ACTION_TRIGGER_REMINDER
        putExtra(ReminderNotificationManager.EXTRA_REMINDER_ID, entry.id)
        putExtra(ReminderNotificationManager.EXTRA_REMINDER_TITLE, entry.title)
      }
    val pi =
      PendingIntent.getBroadcast(
        context,
        entry.id.hashCode(),
        triggerIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val exactGranted = permissionManager.canScheduleExactAlarms()
    return runCatching {
      if (exactGranted) {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.scheduledAtMs, pi)
        true to "Exact AlarmManager trigger scheduled."
      } else {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.scheduledAtMs, pi)
        true to "Inexact AlarmManager reminder scheduled (enable Exact Alarm in Settings for second-level precision)."
      }
    }.getOrElse { err ->
      false to (err.message ?: "Failed to schedule AlarmManager trigger.")
    }
  }

  fun cancelLocalReminderTrigger(reminderId: String) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val triggerIntent =
      Intent(context, ReminderBroadcastReceiver::class.java).apply {
        action = ReminderNotificationManager.ACTION_TRIGGER_REMINDER
        putExtra(ReminderNotificationManager.EXTRA_REMINDER_ID, reminderId)
      }
    val pi =
      PendingIntent.getBroadcast(
        context,
        reminderId.hashCode(),
        triggerIntent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
      )
    if (pi != null) {
      am.cancel(pi)
      pi.cancel()
    }
  }
}

@Singleton
class TimerManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val alarmScheduler: AlarmScheduler,
  private val repository: AlarmManagerRepository,
) {
  suspend fun startTimer(durationSeconds: Int, label: String): Pair<ReminderEntry, String> {
    val safeSec = durationSeconds.coerceAtLeast(1)
    val now = System.currentTimeMillis()
    val targetMs = now + (safeSec * 1000L)
    val intent =
      Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, safeSec)
        putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Mousomi Timer" })
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    var clockOpened = false
    val note =
      if (intent.resolveActivity(context.packageManager) != null) {
        runCatching {
          context.startActivity(intent)
          clockOpened = true
          "Opened Android Clock timer for ${formatDuration(safeSec)}."
        }.getOrElse { err ->
          "Clock timer UI failed (${err.message}); scheduled local Mousomi timer notification instead."
        }
      } else {
        "System Clock unavailable; scheduled Mousomi notification timer for ${formatDuration(safeSec)}."
      }

    val entry =
      ReminderEntry(
        id = "tmr_${UUID.randomUUID()}",
        type = ReminderItemType.TIMER,
        title = label.ifBlank { "Timer (${formatDuration(safeSec)})" },
        durationSeconds = safeSec,
        scheduledAtMs = targetMs,
        recurrence = AlarmRecurrence.ONCE,
        status = if (clockOpened) "Clock Opened" else "Scheduled",
        createdAtMs = now,
        verificationNote = note,
      )
    alarmScheduler.scheduleLocalReminderTrigger(entry)
    repository.save(entry)
    return entry to note
  }

  private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return buildList {
      if (h > 0) add("${h}h")
      if (m > 0) add("${m}m")
      if (s > 0 || isEmpty()) add("${s}s")
    }.joinToString(" ")
  }
}

data class ParsedTimeSpec(
  val hour24: Int,
  val minute: Int,
  val tomorrow: Boolean,
  val recurrence: AlarmRecurrence,
)

@Singleton
class ReminderManager @Inject constructor(
  private val repository: AlarmManagerRepository,
  private val alarmScheduler: AlarmScheduler,
  private val timerManager: TimerManager,
) {
  fun observeReminders(): Flow<List<ReminderEntry>> = repository.observeAll()

  suspend fun processNaturalCommand(command: String): Pair<String, List<ReminderEntry>> {
    val clean = command.trim()
    val lower = clean.lowercase()

    // 1. Show upcoming alarms/reminders
    if (lower.contains("show") || lower.contains("list") || lower.contains("upcoming")) {
      checkMissedReminders()
      val all = repository.getSnapshot().filter { it.enabled && it.status != "Cancelled" }
      val summary =
        if (all.isEmpty()) "You have no scheduled alarms, timers, or reminders yet."
        else "Showing ${all.size} active alarm/reminder item(s)."
      return summary to all
    }

    // 2. Cancel command
    if (lower.startsWith("cancel ") || lower.startsWith("delete ") || lower.startsWith("remove ")) {
      val parsed = extractTimeSpec(clean)
      val all = repository.getSnapshot().filter { it.status != "Cancelled" }
      val match =
        if (parsed != null) {
          all.firstOrNull { it.hour == parsed.hour24 && it.minute == parsed.minute }
        } else {
          val keyword =
            clean
              .replace(Regex("^(cancel|delete|remove)\\s+(my\\s+)?", RegexOption.IGNORE_CASE), "")
              .replace(Regex("\\s*(alarm|reminder|timer).*$", RegexOption.IGNORE_CASE), "")
              .trim()
              .lowercase()
          all.firstOrNull { keyword.isNotBlank() && it.title.lowercase().contains(keyword) } ?: all.firstOrNull()
        }
      if (match == null) {
        return "No matching scheduled alarm or reminder found to cancel." to all
      }
      cancelReminder(match.id)
      val updated = repository.getSnapshot()
      return "Cancelled '${match.title}' cleanly." to updated
    }

    // 3. Edit/Reschedule command ("Change tomorrow's reminder to 9 AM")
    if (lower.startsWith("change ") || lower.startsWith("move ") || lower.startsWith("reschedule ")) {
      val parsed = extractTimeSpec(clean)
      val active = repository.getSnapshot().filter { it.status != "Cancelled" }
      val target =
        if (lower.contains("tomorrow")) {
          active.firstOrNull { isTomorrow(it.scheduledAtMs) } ?: active.firstOrNull()
        } else {
          active.firstOrNull()
        }
      if (target != null && parsed != null) {
        val newCal = computeNextCalendar(parsed.hour24, parsed.minute, lower.contains("tomorrow"))
        val updatedEntry =
          target.copy(
            hour = parsed.hour24,
            minute = parsed.minute,
            scheduledAtMs = newCal.timeInMillis,
            status = "Scheduled",
            verificationNote = "Rescheduled to ${formatTime(newCal.timeInMillis)}.",
          )
        alarmScheduler.cancelLocalReminderTrigger(target.id)
        alarmScheduler.scheduleLocalReminderTrigger(updatedEntry)
        repository.save(updatedEntry)
        return "Updated '${updatedEntry.title}' to ${formatTime(updatedEntry.scheduledAtMs)}." to
          repository.getSnapshot()
      }
    }

    // 4. Timer command ("Start a 15-minute timer")
    if (lower.contains("timer")) {
      val seconds = extractDurationSeconds(lower)
      if (seconds != null) {
        val (_, note) = timerManager.startTimer(seconds, "Timer")
        return note to repository.getSnapshot()
      }
    }

    // 5. Alarm or Reminder creation
    val timeSpec = extractTimeSpec(clean) ?: ParsedTimeSpec(9, 0, tomorrow = lower.contains("tomorrow"), AlarmRecurrence.ONCE)
    val cal = computeNextCalendar(timeSpec.hour24, timeSpec.minute, timeSpec.tomorrow)
    val isAlarm = lower.contains("alarm") || lower.startsWith("wake me")
    val title = extractReminderTitle(clean, isAlarm)

    if (isAlarm) {
      val clockResult =
        alarmScheduler.dispatchSystemClockAlarm(
          hour = timeSpec.hour24,
          minute = timeSpec.minute,
          message = title,
          recurrence = timeSpec.recurrence,
        )
      val entry =
        ReminderEntry(
          id = "alm_${UUID.randomUUID()}",
          type = ReminderItemType.ALARM,
          title = title,
          hour = timeSpec.hour24,
          minute = timeSpec.minute,
          scheduledAtMs = cal.timeInMillis,
          recurrence = timeSpec.recurrence,
          enabled = clockResult.isSuccess,
          status = if (clockResult.isSuccess) "Clock Opened" else "Unavailable",
          verificationNote =
            clockResult.getOrElse {
              it.message ?: "System Clock unavailable to verify alarm creation."
            },
        )
      repository.save(entry)
      val summary =
        clockResult.getOrElse {
          "Could not set system alarm: ${it.message ?: "Clock app unavailable."}"
        }
      return summary to repository.getSnapshot()
    } else {
      val entryId = "rem_${UUID.randomUUID()}"
      val baseEntry =
        ReminderEntry(
          id = entryId,
          type = ReminderItemType.REMINDER,
          title = title,
          hour = timeSpec.hour24,
          minute = timeSpec.minute,
          scheduledAtMs = cal.timeInMillis,
          recurrence = timeSpec.recurrence,
          enabled = true,
          status = "Scheduled",
        )
      val (scheduledOk, schedNote) = alarmScheduler.scheduleLocalReminderTrigger(baseEntry)
      val saved =
        baseEntry.copy(
          enabled = scheduledOk,
          status = if (scheduledOk) "Scheduled" else "Failed",
          verificationNote = schedNote,
        )
      repository.save(saved)
      val summary =
        if (scheduledOk) "Reminder '$title' scheduled for ${formatTime(saved.scheduledAtMs)}. $schedNote"
        else "Could not schedule reminder: $schedNote"
      return summary to repository.getSnapshot()
    }
  }

  suspend fun createExplicitReminder(
    type: ReminderItemType,
    title: String,
    hour: Int,
    minute: Int,
    recurrence: AlarmRecurrence,
    tomorrow: Boolean,
  ): ReminderEntry {
    val cal = computeNextCalendar(hour, minute, tomorrow)
    val entry =
      ReminderEntry(
        id = "${type.name.take(3).lowercase()}_${UUID.randomUUID()}",
        type = type,
        title = title.ifBlank { type.label },
        hour = hour,
        minute = minute,
        scheduledAtMs = cal.timeInMillis,
        recurrence = recurrence,
        enabled = true,
      )
    return if (type == ReminderItemType.ALARM) {
      val clockRes = alarmScheduler.dispatchSystemClockAlarm(hour, minute, entry.title, recurrence)
      val updated =
        entry.copy(
          enabled = clockRes.isSuccess,
          status = if (clockRes.isSuccess) "Clock Opened" else "Unavailable",
          verificationNote = clockRes.getOrElse { it.message ?: "Clock unavailable." },
        )
      repository.save(updated)
      updated
    } else {
      val (ok, note) = alarmScheduler.scheduleLocalReminderTrigger(entry)
      val updated = entry.copy(enabled = ok, status = if (ok) "Scheduled" else "Failed", verificationNote = note)
      repository.save(updated)
      updated
    }
  }

  suspend fun cancelReminder(id: String) {
    val current = repository.getSnapshot().firstOrNull { it.id == id } ?: return
    alarmScheduler.cancelLocalReminderTrigger(id)
    repository.save(
      current.copy(
        enabled = false,
        status = "Cancelled",
        verificationNote = "Cancelled by user.",
      )
    )
  }

  suspend fun deleteReminder(id: String) {
    alarmScheduler.cancelLocalReminderTrigger(id)
    repository.delete(id)
  }

  suspend fun checkMissedReminders(): List<ReminderEntry> {
    val now = System.currentTimeMillis()
    val all = repository.getSnapshot()
    val updated = mutableListOf<ReminderEntry>()
    all.forEach { item ->
      if (item.enabled && item.status == "Scheduled" && item.scheduledAtMs < now - 60_000L) {
        val missed =
          item.copy(
            status = "Missed",
            verificationNote = "Scheduled time passed while device/notification was inactive.",
          )
        repository.save(missed)
        updated += missed
      }
    }
    return updated
  }

  suspend fun rescheduleAllOnBoot() {
    val now = System.currentTimeMillis()
    repository.getSnapshot().forEach { item ->
      if (item.enabled && item.type == ReminderItemType.REMINDER && item.scheduledAtMs > now) {
        alarmScheduler.scheduleLocalReminderTrigger(item)
      }
    }
  }

  fun extractTimeSpec(command: String): ParsedTimeSpec? {
    val lower = command.lowercase()
    val recurrence =
      when {
        "every morning" in lower || "every day" in lower || "daily" in lower -> AlarmRecurrence.DAILY
        "every week" in lower || "weekly" in lower -> AlarmRecurrence.WEEKLY
        else -> AlarmRecurrence.ONCE
      }
    val tomorrow = "tomorrow" in lower

    val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).findAll(lower)
      .firstOrNull { m ->
        val raw = m.value
        val beforeIdx = (m.range.first - 1).coerceAtLeast(0)
        // Skip numbers followed by minute/second/hour for timers unless am/pm/colon present
        val hasMeridiemOrColon = m.groupValues[3].isNotBlank() || m.groupValues[2].isNotBlank()
        val nearAtFor = lower.substring(0, m.range.first).trimEnd().let { it.endsWith("at") || it.endsWith("for") || it.endsWith("to") }
        (hasMeridiemOrColon || nearAtFor) && !raw.endsWith("m") || raw.endsWith("am") || raw.endsWith("pm")
      } ?: return null

    var hour = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return null
    val minute = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
    val meridiem = match.groupValues[3].lowercase()
    if (meridiem == "pm" && hour in 1..11) hour += 12
    if (meridiem == "am" && hour == 12) hour = 0
    return ParsedTimeSpec(hour24 = hour, minute = minute, tomorrow = tomorrow, recurrence = recurrence)
  }

  fun extractDurationSeconds(lower: String): Int? {
    val match = Regex("(\\d+)\\s*(hour|hr|hrs|hours|minute|min|mins|minutes|second|sec|secs|seconds)").find(lower)
      ?: return null
    val value = match.groupValues[1].toIntOrNull() ?: return null
    val unit = match.groupValues[2]
    return when {
      unit.startsWith("hour") || unit.startsWith("hr") -> value * 3600
      unit.startsWith("min") -> value * 60
      else -> value
    }
  }

  private fun extractReminderTitle(command: String, isAlarm: Boolean): String {
    val toIdx = command.lowercase().indexOf(" to ")
    if (toIdx in 1 until command.length - 4) {
      val tail = command.substring(toIdx + 4).trim().trimEnd('.', '!')
      if (tail.isNotBlank() && !tail.matches(Regex("\\d+.*"))) {
        return tail.replaceFirstChar { it.uppercase() }
      }
    }
    return if (isAlarm) "Mousomi Alarm" else "Mousomi Reminder"
  }

  private fun computeNextCalendar(hour: Int, minute: Int, forceTomorrow: Boolean): Calendar {
    val now = Calendar.getInstance()
    return Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (forceTomorrow || timeInMillis <= now.timeInMillis) {
        add(Calendar.DAY_OF_YEAR, 1)
      }
    }
  }

  private fun isTomorrow(timeMs: Long): Boolean {
    val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val targetCal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return tomorrowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
      tomorrowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
  }

  private fun formatTime(ms: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))
}
