package com.example.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.example.core.alarms.ReminderManager
import com.example.data.local.db.TodoNoteDao
import com.example.data.local.db.TodoNoteEntity
import com.example.data.repository.AddOnRepository
import com.example.domain.model.AlarmRecurrence
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.ReminderItemType
import com.example.domain.model.TodoNoteKind
import com.example.domain.model.TodoNoteRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Privacy-preserving Media, Camera & SAF Document Service:
 * - Zero broad storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` are never used).
 * - Uses persisted SAF URIs for searching authorized user files and system Photo Picker / SAF Document Picker intents.
 */
@Singleton
class MediaFileService @Inject constructor(
  @ApplicationContext private val context: Context,
  private val verifier: DeviceActionVerifier,
  private val addOnRepository: AddOnRepository? = null,
) {

  fun buildDocumentPickerIntent(allowMultiple: Boolean = true): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)
      type = "*/*"
      putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

  suspend fun openCamera(): DeviceActionResult {
    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    val res =
      if (!verifier.verifyIntentResolvable(intent)) {
        DeviceActionResult(
          id = "dev_${UUID.randomUUID()}",
          category = DeviceActionCategory.LAUNCH_APP,
          command = "Open Camera",
          targetLabel = "Camera",
          success = false,
          statusLabel = "Unavailable",
          verifiedDetail = "No compatible Camera activity resolved.",
        )
      } else {
        runCatching {
          context.startActivity(intent)
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.LAUNCH_APP,
            command = "Open Camera",
            targetLabel = "Camera",
            success = true,
            statusLabel = "Camera Opened",
            verifiedDetail = "Launched Android system Camera.",
          )
        }.getOrElse { err ->
          DeviceActionResult(
            id = "dev_${UUID.randomUUID()}",
            category = DeviceActionCategory.LAUNCH_APP,
            command = "Open Camera",
            targetLabel = "Camera",
            success = false,
            statusLabel = "Failed",
            verifiedDetail = err.message ?: "Camera launch failed.",
          )
        }
      }
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  suspend fun findPersistedAuthorizedFiles(query: String): DeviceActionResult {
    val cleanQuery = query.trim().lowercase()
    val persisted = context.contentResolver.persistedUriPermissions
      .filter { it.isReadPermission }
      .map { it.uri }
    val matched =
      if (cleanQuery.isBlank()) persisted
      else persisted.filter { uri -> uri.toString().lowercase().contains(cleanQuery) }

    val res =
      DeviceActionResult(
        id = "dev_${UUID.randomUUID()}",
        category = DeviceActionCategory.SYSTEM_SETTINGS,
        command = "Search files: $query",
        targetLabel = "SAF Authorized Files",
        success = true,
        statusLabel = "Found ${matched.size} File(s)",
        verifiedDetail =
          if (matched.isEmpty()) {
            "No persisted SAF documents matched '$query'. Tap '+' → Documents in chat to pick local files securely via Android Storage Access Framework."
          } else {
            matched.take(6).joinToString("\n") { "• ${it.lastPathSegment ?: it}" }
          },
      )
    addOnRepository?.recordDeviceAction(res)
    return res
  }

  fun openPersistedUri(uri: Uri, mimeType: String? = null): Boolean {
    val intent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: "*/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    if (!verifier.verifyIntentResolvable(intent)) return false
    return runCatching {
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  companion object {
    fun extractImageGenerationPrompt(rawCommand: String): String? {
      val clean = rawCommand.trim()
      val regex =
        Regex(
          "^(?:please\\s+)?(?:can you\\s+)?(?:generate|create|draw|make|paint|render)\\s+(?:an?\\s+)?(?:ai\\s+)?(?:image|picture|photo|artwork|illustration|wallpaper|poster)\\s+(?:of|about|showing|with)?\\s*(.+)$",
          RegexOption.IGNORE_CASE,
        )
      val m = regex.find(clean) ?: return null
      return m.groupValues[1].trim().takeIf { it.isNotBlank() }
    }
  }
}

/**
 * Unified Calendar, To-Do, Notes, and Daily Schedule Productivity Service backed by Room (`TodoNoteDao`),
 * Android Calendar intents (`CalendarContract.Events`), and Mousomi [ReminderManager].
 */
@Singleton
class CalendarReminderService @Inject constructor(
  private val deviceActionExecutor: DeviceActionExecutor,
  private val reminderManager: ReminderManager,
  private val todoNoteDao: TodoNoteDao? = null,
) {

  fun observeTodoNotes(): Flow<List<TodoNoteRecord>>? =
    todoNoteDao?.observeAllItems()?.map { rows -> rows.map(::toRecord) }

  suspend fun createCalendarEvent(
    title: String,
    startTimeMs: Long = System.currentTimeMillis() + 3_600_000L,
    durationMinutes: Int = 60,
  ): DeviceActionResult =
    deviceActionExecutor.createCalendarEvent(
      title = title.trim().ifBlank { "Mousomi Event" },
      startTimeMs = startTimeMs,
      endTimeMs = startTimeMs + (durationMinutes.coerceIn(5, 1440) * 60_000L),
    )

  suspend fun addTodoOrNote(
    kind: TodoNoteKind,
    title: String,
    details: String = "",
    scheduleTimeLabel: String = "",
    dueAtMs: Long? = null,
  ): TodoNoteRecord {
    val now = System.currentTimeMillis()
    val entity =
      TodoNoteEntity(
        id = "todo_${UUID.randomUUID()}",
        kind = kind.name,
        title = title.trim(),
        body = details.trim(),
        scheduleTimeLabel = scheduleTimeLabel.trim(),
        isCompleted = false,
        createdAtMs = now,
        updatedAtMs = now,
      )
    todoNoteDao?.upsertItem(entity)
    if (dueAtMs != null && dueAtMs > now) {
      val cal = Calendar.getInstance().apply { timeInMillis = dueAtMs }
      reminderManager.createExplicitReminder(
        type = ReminderItemType.REMINDER,
        title = entity.title,
        hour = cal.get(Calendar.HOUR_OF_DAY),
        minute = cal.get(Calendar.MINUTE),
        recurrence = AlarmRecurrence.ONCE,
        tomorrow = false,
      )
    }
    return toRecord(entity)
  }

  suspend fun listSummary(kindFilter: TodoNoteKind? = null): String {
    val items = todoNoteDao?.getAllSnapshot().orEmpty().map(::toRecord)
      .filter { kindFilter == null || it.kind == kindFilter }
    if (items.isEmpty()) {
      return "You have no saved ${kindFilter?.label?.lowercase() ?: "to-dos, notes, or schedule items"} yet."
    }
    return buildString {
      appendLine("Your saved items (${items.size}):")
      items.take(12).forEach { item ->
        val check = if (item.isCompleted) "[✓]" else "[ ]"
        appendLine("$check ${item.kind.label}: ${item.title}")
      }
    }.trim()
  }

  suspend fun toggleCompleted(id: String, completed: Boolean) {
    todoNoteDao?.setCompleted(id, completed, System.currentTimeMillis())
  }

  suspend fun deleteItem(id: String) {
    todoNoteDao?.deleteItem(id)
  }

  private fun toRecord(entity: TodoNoteEntity): TodoNoteRecord =
    TodoNoteRecord(
      id = entity.id,
      kind = runCatching { TodoNoteKind.valueOf(entity.kind) }.getOrDefault(TodoNoteKind.TODO),
      title = entity.title,
      body = entity.body,
      scheduleTimeLabel = entity.scheduleTimeLabel,
      isCompleted = entity.isCompleted,
      createdAtMs = entity.createdAtMs,
      updatedAtMs = entity.updatedAtMs,
    )
}
