package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AgentTaskLifecycleState
import com.example.domain.model.AlarmRecurrence
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.ReminderEntry
import com.example.domain.model.ReminderItemType
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarmAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantHubScreen(
  initialTab: Int = 0,
  managedTasks: List<ManagedTaskItem>,
  reminders: List<ReminderEntry>,
  installedApps: List<InstalledAppInfo>,
  deviceActionLogs: List<DeviceActionResult>,
  appSearchQuery: String,
  onAppSearchQueryChange: (String) -> Unit,
  onRunCompoundInstruction: (String) -> Unit,
  onApproveOrResumeTask: (ManagedTaskItem) -> Unit,
  onPauseTask: (ManagedTaskItem) -> Unit,
  onRetryTask: (ManagedTaskItem) -> Unit,
  onCancelTask: (ManagedTaskItem) -> Unit,
  onStopAllTasks: () -> Unit,
  onClearFinishedTasks: () -> Unit,
  onStartQuickTimer: (Int, String) -> Unit,
  onCreateReminder: (ReminderItemType, String, Int, Int, AlarmRecurrence, Boolean) -> Unit,
  onCancelReminder: (String) -> Unit,
  onDeleteReminder: (String) -> Unit,
  onCheckMissedReminders: () -> Unit,
  onExecuteDeviceCommand: (String) -> Unit,
  onLaunchInstalledApp: (String) -> Unit,
  onRefreshInstalledApps: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = Modifier.fillMaxSize().testTag("assistant_hub_screen"),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("hub_back_button")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
          Text("Assistant Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text(
            "Task Center • Alarms & Timers • Device Apps",
            style = MaterialTheme.typography.labelSmall,
            color = LocalMousomiColors.current.mutedText,
          )
        }
        Button(
          onClick = onStopAllTasks,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
          modifier = Modifier.testTag("hub_stop_all_button"),
        ) {
          Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
          Text(" Stop All", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
      }

      TabRow(
        selectedTabIndex = selectedTab,
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Tasks (${managedTasks.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
          modifier = Modifier.testTag("hub_tab_tasks"),
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Alarms (${reminders.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
          modifier = Modifier.testTag("hub_tab_alarms"),
        )
        Tab(
          selected = selectedTab == 2,
          onClick = {
            selectedTab = 2
            onRefreshInstalledApps()
          },
          text = { Text("Device", maxLines = 1, overflow = TextOverflow.Ellipsis) },
          modifier = Modifier.testTag("hub_tab_device"),
        )
      }

      Box(modifier = Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp)) {
        when (selectedTab) {
          0 ->
            TaskCenterTab(
              managedTasks = managedTasks,
              onRunCompoundInstruction = onRunCompoundInstruction,
              onApproveOrResumeTask = onApproveOrResumeTask,
              onPauseTask = onPauseTask,
              onRetryTask = onRetryTask,
              onCancelTask = onCancelTask,
              onClearFinishedTasks = onClearFinishedTasks,
            )
          1 ->
            AlarmsAndRemindersTab(
              reminders = reminders,
              onStartQuickTimer = onStartQuickTimer,
              onCreateReminder = onCreateReminder,
              onCancelReminder = onCancelReminder,
              onDeleteReminder = onDeleteReminder,
              onCheckMissedReminders = onCheckMissedReminders,
            )
          else ->
            DeviceAssistantTab(
              installedApps = installedApps,
              deviceActionLogs = deviceActionLogs,
              searchQuery = appSearchQuery,
              onSearchQueryChange = onAppSearchQueryChange,
              onExecuteDeviceCommand = onExecuteDeviceCommand,
              onLaunchInstalledApp = onLaunchInstalledApp,
              onRefreshApps = onRefreshInstalledApps,
            )
        }
      }
    }
  }
}

@Composable
private fun TaskCenterTab(
  managedTasks: List<ManagedTaskItem>,
  onRunCompoundInstruction: (String) -> Unit,
  onApproveOrResumeTask: (ManagedTaskItem) -> Unit,
  onPauseTask: (ManagedTaskItem) -> Unit,
  onRetryTask: (ManagedTaskItem) -> Unit,
  onCancelTask: (ManagedTaskItem) -> Unit,
  onClearFinishedTasks: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  var commandInput by remember { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(14.dp).testTag("hub_task_center_list"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.activeChipContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Checklist, contentDescription = null, tint = NeonCyan)
            Text("Multi-Task AI Agent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          }
          OutlinedTextField(
            value = commandInput,
            onValueChange = { commandInput = it },
            label = { Text("Compound command (e.g., Open Chrome and set a 15-minute timer)") },
            modifier = Modifier.fillMaxWidth().testTag("hub_multitask_input"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                val cmd = commandInput.trim()
                if (cmd.isNotEmpty()) {
                  onRunCompoundInstruction(cmd)
                  commandInput = ""
                }
              },
              modifier = Modifier.testTag("hub_run_multitask_button"),
            ) { Text("Plan & Execute") }
            OutlinedButton(
              onClick = onClearFinishedTasks,
              modifier = Modifier.testTag("hub_clear_finished_tasks_button"),
            ) { Text("Clear Finished") }
          }
        }
      }
    }

    if (managedTasks.isEmpty()) {
      item {
        Text(
          text = "No queued or recent multi-step tasks yet. Enter a compound instruction above or from chat.",
          color = colors.mutedText,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(vertical = 12.dp),
        )
      }
    }

    items(managedTasks, key = { it.id }) { task ->
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().testTag("hub_task_card_${task.id}"),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = task.description,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = task.state.label,
              color =
                when (task.state) {
                  AgentTaskLifecycleState.COMPLETED -> NeonGreen
                  AgentTaskLifecycleState.FAILED -> Color(0xFFFCA5A5)
                  AgentTaskLifecycleState.WAITING_FOR_APPROVAL -> WarmAmber
                  else -> NeonCyan
                },
              style = MaterialTheme.typography.labelMedium,
            )
          }
          Text(
            text = "${task.kind.label} • Retries: ${task.retryCount}/${task.maxRetries}",
            color = colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
          )
          val detail = task.errorDetails.ifBlank { task.resultMessage }
          if (detail.isNotBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (task.state == AgentTaskLifecycleState.WAITING_FOR_APPROVAL || task.state == AgentTaskLifecycleState.PAUSED) {
              OutlinedButton(onClick = { onApproveOrResumeTask(task) }) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(if (task.state == AgentTaskLifecycleState.WAITING_FOR_APPROVAL) " Approve" else " Resume")
              }
            }
            if (task.state == AgentTaskLifecycleState.RUNNING || task.state == AgentTaskLifecycleState.PENDING) {
              OutlinedButton(onClick = { onPauseTask(task) }) {
                Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Pause")
              }
            }
            if (task.state == AgentTaskLifecycleState.FAILED) {
              OutlinedButton(onClick = { onRetryTask(task) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Retry")
              }
            }
            if (task.state != AgentTaskLifecycleState.COMPLETED && task.state != AgentTaskLifecycleState.CANCELLED) {
              OutlinedButton(onClick = { onCancelTask(task) }) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Cancel")
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmsAndRemindersTab(
  reminders: List<ReminderEntry>,
  onStartQuickTimer: (Int, String) -> Unit,
  onCreateReminder: (ReminderItemType, String, Int, Int, AlarmRecurrence, Boolean) -> Unit,
  onCancelReminder: (String) -> Unit,
  onDeleteReminder: (String) -> Unit,
  onCheckMissedReminders: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
  var title by remember { mutableStateOf("") }
  var selectedType by remember { mutableStateOf(ReminderItemType.REMINDER) }
  var recurrence by remember { mutableStateOf(AlarmRecurrence.ONCE) }
  var hourText by remember { mutableStateOf("9") }
  var minuteText by remember { mutableStateOf("00") }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(14.dp).testTag("hub_alarms_list"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.activeChipContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = NeonCyan)
            Text("Quick Timers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onStartQuickTimer(300, "5m Focus Timer") }, modifier = Modifier.testTag("quick_timer_5m")) {
              Text("5m Timer")
            }
            OutlinedButton(onClick = { onStartQuickTimer(600, "10m Timer") }) {
              Text("10m Timer")
            }
            OutlinedButton(onClick = { onStartQuickTimer(900, "15m Study Timer") }, modifier = Modifier.testTag("quick_timer_15m")) {
              Text("15m Timer")
            }
            OutlinedButton(onClick = onCheckMissedReminders, modifier = Modifier.testTag("hub_check_missed_button")) {
              Text("Check Missed")
            }
          }
        }
      }
    }

    item {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.activeChipContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Alarm, contentDescription = null, tint = WarmAmber)
            Text("Schedule Reminder or Alarm", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = selectedType == ReminderItemType.REMINDER,
              onClick = { selectedType = ReminderItemType.REMINDER },
              label = { Text("Reminder") },
            )
            FilterChip(
              selected = selectedType == ReminderItemType.ALARM,
              onClick = { selectedType = ReminderItemType.ALARM },
              label = { Text("Clock Alarm") },
            )
          }
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Label (e.g., Study Kotlin)") },
            modifier = Modifier.fillMaxWidth().testTag("hub_reminder_title_input"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = hourText,
              onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
              label = { Text("Hour (0-23)") },
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = minuteText,
              onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
              label = { Text("Min (0-59)") },
              modifier = Modifier.weight(1f),
            )
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlarmRecurrence.entries.forEach { rec ->
              FilterChip(
                selected = recurrence == rec,
                onClick = { recurrence = rec },
                label = { Text(rec.label) },
              )
            }
          }
          Button(
            onClick = {
              val h = (hourText.toIntOrNull() ?: 9).coerceIn(0, 23)
              val m = (minuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
              onCreateReminder(selectedType, title.trim(), h, m, recurrence, false)
              title = ""
            },
            modifier = Modifier.fillMaxWidth().testTag("hub_create_reminder_button"),
          ) {
            Text("Create Verified ${selectedType.label}")
          }
        }
      }
    }

    items(reminders, key = { it.id }) { entry ->
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().testTag("hub_reminder_card_${entry.id}"),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${entry.title} (${entry.type.label})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = entry.status,
              color = if (entry.status == "Missed" || entry.status == "Failed" || entry.status == "Unavailable") Color(0xFFFCA5A5) else NeonGreen,
              style = MaterialTheme.typography.labelMedium,
            )
          }
          Text(
            text = "${formatter.format(Date(entry.scheduledAtMs))} • ${entry.recurrence.label}",
            color = colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
          )
          if (entry.verificationNote.isNotBlank()) {
            Text(entry.verificationNote, style = MaterialTheme.typography.bodySmall)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entry.status != "Cancelled") {
              OutlinedButton(onClick = { onCancelReminder(entry.id) }) { Text("Cancel") }
            }
            OutlinedButton(onClick = { onDeleteReminder(entry.id) }) {
              Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
              Text(" Remove")
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceAssistantTab(
  installedApps: List<InstalledAppInfo>,
  deviceActionLogs: List<DeviceActionResult>,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onExecuteDeviceCommand: (String) -> Unit,
  onLaunchInstalledApp: (String) -> Unit,
  onRefreshApps: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val shortcuts =
    listOf(
      "Wi-Fi" to "Open Wi-Fi Settings",
      "Bluetooth" to "Open Bluetooth Settings",
      "Display" to "Open Display Settings",
      "Battery" to "Open Battery Settings",
      "Sound" to "Open Sound Settings",
      "Camera" to "Open Camera",
      "App Info" to "Open App Info",
    )

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(14.dp).testTag("hub_device_list"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.activeChipContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Outlined.Apps, contentDescription = null, tint = NeonCyan)
              Text("System Settings & App Launcher", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onRefreshApps) { Text("Refresh") }
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shortcuts.forEach { (label, cmd) ->
              OutlinedButton(
                onClick = { onExecuteDeviceCommand(cmd) },
                modifier = Modifier.testTag("device_shortcut_$label"),
              ) { Text(label) }
            }
          }
          OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search installed applications…") },
            modifier = Modifier.fillMaxWidth().testTag("hub_app_search_input"),
          )
        }
      }
    }

    items(installedApps.take(25), key = { it.packageName }) { app ->
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(app.packageName, color = colors.mutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          OutlinedButton(onClick = { onLaunchInstalledApp(app.appName) }) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" Open")
          }
        }
      }
    }

    if (deviceActionLogs.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(6.dp))
        Text("Recent Verified Device Actions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
      }
      items(deviceActionLogs.take(10), key = { it.id }) { log ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colors.activeChipContainer.copy(alpha = 0.45f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(log.targetLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
              Text(
                log.statusLabel,
                color = if (log.success) NeonGreen else WarmAmber,
                style = MaterialTheme.typography.labelSmall,
              )
            }
            Text(log.verifiedDetail, color = colors.mutedText, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}
