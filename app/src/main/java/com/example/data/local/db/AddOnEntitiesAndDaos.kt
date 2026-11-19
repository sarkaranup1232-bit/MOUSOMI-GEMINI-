package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
  tableName = "reminders",
  indices = [Index("scheduledAtMs"), Index("type"), Index("status")],
)
data class ReminderEntity(
  @PrimaryKey val id: String,
  val type: String,
  val title: String,
  val hour: Int?,
  val minute: Int?,
  val durationSeconds: Int?,
  val scheduledAtMs: Long,
  val recurrence: String,
  val daysOfWeekCsv: String,
  val enabled: Boolean,
  val status: String,
  val lastTriggeredAtMs: Long?,
  val createdAtMs: Long,
  val verificationNote: String,
)

@Entity(
  tableName = "managed_tasks",
  indices = [Index("batchId"), Index("createdAtMs"), Index("state")],
)
data class ManagedTaskEntity(
  @PrimaryKey val id: String,
  val batchId: String,
  val orderIndex: Int,
  val description: String,
  val kind: String,
  val targetArg: String,
  val requiredPermissionsJson: String,
  val dependsOnIdsJson: String,
  val priority: Int,
  val state: String,
  val resultMessage: String,
  val errorDetails: String,
  val retryCount: Int,
  val maxRetries: Int,
  val requiresApproval: Boolean,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Entity(
  tableName = "device_action_logs",
  indices = [Index("timestampMs"), Index("category")],
)
data class DeviceActionLogEntity(
  @PrimaryKey val id: String,
  val category: String,
  val command: String,
  val targetLabel: String,
  val packageName: String?,
  val success: Boolean,
  val statusLabel: String,
  val verifiedDetail: String,
  val timestampMs: Long,
)

@Dao
interface AddOnDao {
  @Query("SELECT * FROM reminders ORDER BY scheduledAtMs ASC")
  fun observeReminders(): Flow<List<ReminderEntity>>

  @Query("SELECT * FROM reminders ORDER BY scheduledAtMs ASC")
  suspend fun getRemindersSnapshot(): List<ReminderEntity>

  @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
  suspend fun getReminderById(id: String): ReminderEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertReminder(entity: ReminderEntity)

  @Query("DELETE FROM reminders WHERE id = :id")
  suspend fun deleteReminder(id: String)

  @Query("SELECT * FROM managed_tasks ORDER BY createdAtMs DESC, orderIndex ASC LIMIT 120")
  fun observeManagedTasks(): Flow<List<ManagedTaskEntity>>

  @Query("SELECT * FROM managed_tasks WHERE id = :id LIMIT 1")
  suspend fun getManagedTaskById(id: String): ManagedTaskEntity?

  @Query("SELECT * FROM managed_tasks WHERE batchId = :batchId ORDER BY orderIndex ASC")
  suspend fun getTasksForBatch(batchId: String): List<ManagedTaskEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertManagedTask(entity: ManagedTaskEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertManagedTasks(entities: List<ManagedTaskEntity>)

  @Query("DELETE FROM managed_tasks WHERE state IN ('COMPLETED', 'CANCELLED', 'FAILED')")
  suspend fun clearFinishedManagedTasks()

  @Query("SELECT * FROM device_action_logs ORDER BY timestampMs DESC LIMIT 100")
  fun observeDeviceActionLogs(): Flow<List<DeviceActionLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDeviceActionLog(entity: DeviceActionLogEntity)

  @Query("DELETE FROM device_action_logs")
  suspend fun clearDeviceActionLogs()
}
