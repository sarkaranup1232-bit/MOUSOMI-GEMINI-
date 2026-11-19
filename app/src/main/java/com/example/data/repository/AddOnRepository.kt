package com.example.data.repository

import com.example.data.local.db.AddOnDao
import com.example.data.local.db.DeviceActionLogEntity
import com.example.data.local.db.ManagedTaskEntity
import com.example.data.local.db.ReminderEntity
import com.example.domain.model.AgentTaskLifecycleState
import com.example.domain.model.AlarmRecurrence
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.ReminderEntry
import com.example.domain.model.ReminderItemType
import com.example.domain.model.SubTaskKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class AddOnRepository @Inject constructor(
  private val addOnDao: AddOnDao,
  private val json: Json,
) {
  fun observeReminders(): Flow<List<ReminderEntry>> =
    addOnDao.observeReminders().map { list -> list.map(::toReminderEntry) }

  suspend fun getRemindersSnapshot(): List<ReminderEntry> =
    addOnDao.getRemindersSnapshot().map(::toReminderEntry)

  suspend fun upsertReminder(entry: ReminderEntry) {
    addOnDao.upsertReminder(
      ReminderEntity(
        id = entry.id,
        type = entry.type.name,
        title = entry.title,
        hour = entry.hour,
        minute = entry.minute,
        durationSeconds = entry.durationSeconds,
        scheduledAtMs = entry.scheduledAtMs,
        recurrence = entry.recurrence.name,
        daysOfWeekCsv = entry.daysOfWeekCsv,
        enabled = entry.enabled,
        status = entry.status,
        lastTriggeredAtMs = entry.lastTriggeredAtMs,
        createdAtMs = entry.createdAtMs,
        verificationNote = entry.verificationNote,
      )
    )
  }

  suspend fun deleteReminder(id: String) = addOnDao.deleteReminder(id)

  fun observeManagedTasks(): Flow<List<ManagedTaskItem>> =
    addOnDao.observeManagedTasks().map { list -> list.map(::toManagedTask) }

  suspend fun getManagedTaskById(id: String): ManagedTaskItem? =
    addOnDao.getManagedTaskById(id)?.let(::toManagedTask)

  suspend fun upsertManagedTask(task: ManagedTaskItem) {
    addOnDao.upsertManagedTask(toEntity(task))
  }

  suspend fun upsertManagedTasks(tasks: List<ManagedTaskItem>) {
    addOnDao.upsertManagedTasks(tasks.map(::toEntity))
  }

  suspend fun clearFinishedManagedTasks() = addOnDao.clearFinishedManagedTasks()

  fun observeDeviceActionLogs(): Flow<List<DeviceActionResult>> =
    addOnDao.observeDeviceActionLogs().map { list -> list.map(::toDeviceActionResult) }

  suspend fun recordDeviceAction(result: DeviceActionResult) {
    addOnDao.insertDeviceActionLog(
      DeviceActionLogEntity(
        id = result.id,
        category = result.category.name,
        command = result.command,
        targetLabel = result.targetLabel,
        packageName = result.packageName,
        success = result.success,
        statusLabel = result.statusLabel,
        verifiedDetail = result.verifiedDetail,
        timestampMs = result.timestampMs,
      )
    )
  }

  suspend fun clearDeviceActionLogs() = addOnDao.clearDeviceActionLogs()

  private fun toReminderEntry(entity: ReminderEntity): ReminderEntry =
    ReminderEntry(
      id = entity.id,
      type = runCatching { ReminderItemType.valueOf(entity.type) }.getOrDefault(ReminderItemType.REMINDER),
      title = entity.title,
      hour = entity.hour,
      minute = entity.minute,
      durationSeconds = entity.durationSeconds,
      scheduledAtMs = entity.scheduledAtMs,
      recurrence = runCatching { AlarmRecurrence.valueOf(entity.recurrence) }.getOrDefault(AlarmRecurrence.ONCE),
      daysOfWeekCsv = entity.daysOfWeekCsv,
      enabled = entity.enabled,
      status = entity.status,
      lastTriggeredAtMs = entity.lastTriggeredAtMs,
      createdAtMs = entity.createdAtMs,
      verificationNote = entity.verificationNote,
    )

  private fun toEntity(task: ManagedTaskItem): ManagedTaskEntity =
    ManagedTaskEntity(
      id = task.id,
      batchId = task.batchId,
      orderIndex = task.orderIndex,
      description = task.description,
      kind = task.kind.name,
      targetArg = task.targetArg,
      requiredPermissionsJson = runCatching { json.encodeToString(task.requiredPermissions) }.getOrDefault("[]"),
      dependsOnIdsJson = runCatching { json.encodeToString(task.dependsOnIds) }.getOrDefault("[]"),
      priority = task.priority,
      state = task.state.name,
      resultMessage = task.resultMessage,
      errorDetails = task.errorDetails,
      retryCount = task.retryCount,
      maxRetries = task.maxRetries,
      requiresApproval = task.requiresApproval,
      createdAtMs = task.createdAtMs,
      updatedAtMs = task.updatedAtMs,
    )

  private fun toManagedTask(entity: ManagedTaskEntity): ManagedTaskItem =
    ManagedTaskItem(
      id = entity.id,
      batchId = entity.batchId,
      orderIndex = entity.orderIndex,
      description = entity.description,
      kind = runCatching { SubTaskKind.valueOf(entity.kind) }.getOrDefault(SubTaskKind.AI_REASONING),
      targetArg = entity.targetArg,
      requiredPermissions =
        runCatching { json.decodeFromString<List<String>>(entity.requiredPermissionsJson) }.getOrDefault(emptyList()),
      dependsOnIds =
        runCatching { json.decodeFromString<List<String>>(entity.dependsOnIdsJson) }.getOrDefault(emptyList()),
      priority = entity.priority,
      state = runCatching { AgentTaskLifecycleState.valueOf(entity.state) }.getOrDefault(AgentTaskLifecycleState.PENDING),
      resultMessage = entity.resultMessage,
      errorDetails = entity.errorDetails,
      retryCount = entity.retryCount,
      maxRetries = entity.maxRetries,
      requiresApproval = entity.requiresApproval,
      createdAtMs = entity.createdAtMs,
      updatedAtMs = entity.updatedAtMs,
    )

  private fun toDeviceActionResult(entity: DeviceActionLogEntity): DeviceActionResult =
    DeviceActionResult(
      id = entity.id,
      category = runCatching { DeviceActionCategory.valueOf(entity.category) }.getOrDefault(DeviceActionCategory.LAUNCH_APP),
      command = entity.command,
      targetLabel = entity.targetLabel,
      packageName = entity.packageName,
      success = entity.success,
      statusLabel = entity.statusLabel,
      verifiedDetail = entity.verifiedDetail,
      timestampMs = entity.timestampMs,
    )
}
