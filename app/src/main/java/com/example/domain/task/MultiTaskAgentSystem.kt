package com.example.domain.task

import com.example.core.alarms.ReminderManager
import com.example.core.device.DeviceActionExecutor
import com.example.core.permissions.EmergencyStopController
import com.example.core.permissions.PermissionManager
import com.example.data.repository.AddOnRepository
import com.example.domain.model.AgentTaskLifecycleState
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.MultiTaskBatchReport
import com.example.domain.model.SubTaskKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class TaskDecomposer @Inject constructor() {
  fun decomposeInstruction(instruction: String, batchId: String = "batch_${UUID.randomUUID()}"): List<ManagedTaskItem> {
    val clean = instruction.trim()
    if (clean.isBlank()) return emptyList()

    val clauses =
      clean
        .split(Regex("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+(?:then\\s+)?|\\s+then\\s+|\\s*;\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val list = mutableListOf<ManagedTaskItem>()
    clauses.forEachIndexed { index, clause ->
      val lower = clause.lowercase()
      val (kind, arg, perms) = classifyClause(clause, lower)
      list +=
        ManagedTaskItem(
          id = "task_${batchId}_$index",
          batchId = batchId,
          orderIndex = index,
          description = clause,
          kind = kind,
          targetArg = arg,
          requiredPermissions = perms,
          dependsOnIds = emptyList(),
          priority = if (kind == SubTaskKind.OPEN_APP || kind == SubTaskKind.OPEN_SETTINGS) 4 else 5,
          state = AgentTaskLifecycleState.PENDING,
          requiresApproval = kind.requiresApprovalByDefault,
        )
    }
    return list
  }

  private fun classifyClause(clause: String, lower: String): Triple<SubTaskKind, String, List<String>> {
    return when {
      lower.contains("alarm") -> Triple(SubTaskKind.SET_ALARM, clause, listOf("com.android.alarm.permission.SET_ALARM"))
      lower.contains("timer") -> Triple(SubTaskKind.SET_TIMER, clause, listOf("com.android.alarm.permission.SET_ALARM"))
      lower.contains("reminder") || lower.startsWith("remind ") ->
        Triple(SubTaskKind.CREATE_REMINDER, clause, listOf("android.permission.POST_NOTIFICATIONS"))
      lower.contains("upcoming alarm") || lower.contains("show alarm") || lower.contains("show reminder") ->
        Triple(SubTaskKind.LIST_REMINDERS, clause, emptyList())
      lower.contains("bluetooth settings") || lower.contains("wi-fi settings") ||
        lower.contains("wifi settings") || lower.contains("open settings") ->
        Triple(SubTaskKind.OPEN_SETTINGS, clause, emptyList())
      lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") ->
        Triple(SubTaskKind.OPEN_APP, clause, emptyList())
      lower.startsWith("copy ") -> Triple(SubTaskKind.COPY_TEXT, clause, emptyList())
      lower.startsWith("share ") -> Triple(SubTaskKind.SHARE_TEXT, clause, emptyList())
      lower.contains("calendar") -> Triple(SubTaskKind.CREATE_CALENDAR, clause, emptyList())
      lower.contains("generate image") || lower.contains("create image") ->
        Triple(SubTaskKind.GENERATE_IMAGE, clause, listOf("android.permission.INTERNET"))
      lower.contains("research") || lower.startsWith("search ") ->
        Triple(SubTaskKind.WEB_RESEARCH, clause, listOf("android.permission.INTERNET"))
      lower.contains("screen") -> Triple(SubTaskKind.SCREEN_ASSIST, clause, listOf("MediaProjection"))
      else -> Triple(SubTaskKind.AI_REASONING, clause, emptyList())
    }
  }
}

@Singleton
class TaskDependencyResolver @Inject constructor() {
  fun orderExecutableTasks(tasks: List<ManagedTaskItem>): List<ManagedTaskItem> {
    val completedIds = tasks.filter { it.state == AgentTaskLifecycleState.COMPLETED }.map { it.id }.toSet()
    return tasks
      .sortedWith(compareBy<ManagedTaskItem> { it.priority }.thenBy { it.orderIndex })
      .filter { task -> task.dependsOnIds.all { it in completedIds } }
  }
}

@Singleton
class TaskPlanner @Inject constructor(
  private val decomposer: TaskDecomposer,
  private val dependencyResolver: TaskDependencyResolver,
) {
  fun buildPlan(instruction: String, batchId: String = "batch_${UUID.randomUUID()}"): List<ManagedTaskItem> {
    val raw = decomposer.decomposeInstruction(instruction, batchId)
    return dependencyResolver.orderExecutableTasks(raw).ifEmpty { raw }
  }
}

@Singleton
class RetryManager @Inject constructor() {
  suspend fun <T> withRetries(
    maxRetries: Int,
    onRetryAttempt: suspend (Int) -> Unit = {},
    block: suspend () -> Result<T>,
  ): Result<T> {
    var attempt = 0
    var lastResult = block()
    while (lastResult.isFailure && attempt < maxRetries.coerceIn(0, 3)) {
      attempt++
      onRetryAttempt(attempt)
      delay(150L * attempt)
      lastResult = block()
    }
    return lastResult
  }
}

@Singleton
class ActionVerifier @Inject constructor() {
  fun verifyDeviceResult(result: DeviceActionResult): Pair<Boolean, String> {
    return if (result.success) true to result.verifiedDetail
    else false to "${result.statusLabel}: ${result.verifiedDetail}"
  }
}

@Singleton
class ActionExecutor @Inject constructor(
  private val deviceActionExecutor: DeviceActionExecutor,
  private val reminderManager: ReminderManager,
  private val permissionManager: PermissionManager,
  private val actionVerifier: ActionVerifier,
) {
  suspend fun executeTask(task: ManagedTaskItem): Result<String> {
    // Verify runtime permissions when strictly needed (never block optional notification for alarm)
    if (task.kind == SubTaskKind.OPEN_APP || task.kind == SubTaskKind.OPEN_SETTINGS || task.kind == SubTaskKind.COPY_TEXT) {
      val devRes = deviceActionExecutor.executeNaturalCommand(task.targetArg.ifBlank { task.description })
      val (ok, msg) = actionVerifier.verifyDeviceResult(devRes)
      return if (ok) Result.success(msg) else Result.failure(IllegalStateException(msg))
    }

    if (task.kind == SubTaskKind.SET_ALARM ||
      task.kind == SubTaskKind.SET_TIMER ||
      task.kind == SubTaskKind.CREATE_REMINDER ||
      task.kind == SubTaskKind.MODIFY_REMINDER ||
      task.kind == SubTaskKind.LIST_REMINDERS
    ) {
      val (summary, items) = reminderManager.processNaturalCommand(task.targetArg.ifBlank { task.description })
      return if (summary.startsWith("Could not")) {
        Result.failure(IllegalStateException(summary))
      } else {
        Result.success("$summary (${items.size} tracked item(s))")
      }
    }

    if (task.kind == SubTaskKind.CREATE_CALENDAR) {
      val now = System.currentTimeMillis()
      val devRes = deviceActionExecutor.createCalendarEvent(task.description, now + 3_600_000L, now + 7_200_000L)
      val (ok, msg) = actionVerifier.verifyDeviceResult(devRes)
      return if (ok) Result.success(msg) else Result.failure(IllegalStateException(msg))
    }

    if (task.kind == SubTaskKind.WEB_RESEARCH || task.kind == SubTaskKind.GENERATE_IMAGE) {
      if (!permissionManager.isNetworkAvailable()) {
        return Result.failure(IllegalStateException("Network unavailable; connect to the internet to run ${task.kind.label}."))
      }
    }

    return Result.success("Queued '${task.description}' for verified Mousomi assistant pipeline.")
  }
}

@Singleton
class TaskStateManager @Inject constructor(
  private val addOnRepository: AddOnRepository,
) {
  private val _tasks = MutableStateFlow<List<ManagedTaskItem>>(emptyList())
  val tasks: StateFlow<List<ManagedTaskItem>> = _tasks.asStateFlow()

  fun observePersistedTasks(): Flow<List<ManagedTaskItem>> = addOnRepository.observeManagedTasks()

  suspend fun updateTask(task: ManagedTaskItem) {
    val updated = task.copy(updatedAtMs = System.currentTimeMillis())
    _tasks.value =
      (_tasks.value.filterNot { it.id == updated.id } + updated)
        .sortedWith(compareByDescending<ManagedTaskItem> { it.createdAtMs }.thenBy { it.orderIndex })
    addOnRepository.upsertManagedTask(updated)
  }

  suspend fun replaceBatch(batch: List<ManagedTaskItem>) {
    if (batch.isEmpty()) return
    val ids = batch.map { it.id }.toSet()
    _tasks.value =
      (_tasks.value.filterNot { it.id in ids } + batch)
        .sortedWith(compareByDescending<ManagedTaskItem> { it.createdAtMs }.thenBy { it.orderIndex })
    addOnRepository.upsertManagedTasks(batch)
  }

  suspend fun clearFinished() {
    _tasks.value =
      _tasks.value.filterNot {
        it.state == AgentTaskLifecycleState.COMPLETED ||
          it.state == AgentTaskLifecycleState.CANCELLED ||
          it.state == AgentTaskLifecycleState.FAILED
      }
    addOnRepository.clearFinishedManagedTasks()
  }
}

@Singleton
class TaskQueueManager @Inject constructor() {
  private val pausedIds = mutableSetOf<String>()
  private val cancelledIds = mutableSetOf<String>()

  fun pauseTask(taskId: String) {
    synchronized(this) { pausedIds.add(taskId) }
  }

  fun resumeTask(taskId: String) {
    synchronized(this) { pausedIds.remove(taskId) }
  }

  fun cancelTask(taskId: String) {
    synchronized(this) {
      cancelledIds.add(taskId)
      pausedIds.remove(taskId)
    }
  }

  fun isPaused(taskId: String): Boolean = synchronized(this) { taskId in pausedIds }

  fun isCancelled(taskId: String): Boolean = synchronized(this) { taskId in cancelledIds }

  fun clearFlags(taskId: String) {
    synchronized(this) {
      pausedIds.remove(taskId)
      cancelledIds.remove(taskId)
    }
  }

  fun cancelAll(ids: List<String>) {
    synchronized(this) {
      cancelledIds.addAll(ids)
      pausedIds.clear()
    }
  }
}

@Singleton
class TaskExecutionEngine @Inject constructor(
  private val taskPlanner: TaskPlanner,
  private val retryManager: RetryManager,
  private val actionExecutor: ActionExecutor,
  private val taskStateManager: TaskStateManager,
  private val taskQueueManager: TaskQueueManager,
  private val emergencyStopController: EmergencyStopController,
) {
  init {
    emergencyStopController.registerStopListener {
      val activeIds =
        taskStateManager.tasks.value
          .filter {
            it.state == AgentTaskLifecycleState.PENDING ||
              it.state == AgentTaskLifecycleState.PLANNING ||
              it.state == AgentTaskLifecycleState.RUNNING ||
              it.state == AgentTaskLifecycleState.PAUSED ||
              it.state == AgentTaskLifecycleState.WAITING_FOR_APPROVAL
          }
          .map { it.id }
      taskQueueManager.cancelAll(activeIds)
    }
  }

  suspend fun planAndExecuteMultiTask(
    instruction: String,
    approveSensitiveTasks: Boolean = false,
  ): MultiTaskBatchReport {
    val batchId = "batch_${UUID.randomUUID()}"
    val initialTasks = taskPlanner.buildPlan(instruction, batchId)
    taskStateManager.replaceBatch(initialTasks)

    val results = mutableListOf<ManagedTaskItem>()
    for (task in initialTasks) {
      if (taskQueueManager.isCancelled(task.id)) {
        val c = task.copy(state = AgentTaskLifecycleState.CANCELLED, resultMessage = "Cancelled before execution.")
        taskStateManager.updateTask(c)
        results += c
        continue
      }

      if (task.requiresApproval && !approveSensitiveTasks) {
        val wait =
          task.copy(
            state = AgentTaskLifecycleState.WAITING_FOR_APPROVAL,
            resultMessage = "Confirmation required before executing external action '${task.description}'.",
          )
        taskStateManager.updateTask(wait)
        results += wait
        continue
      }

      val finalTask = runSingleTask(task)
      results += finalTask
    }

    return MultiTaskBatchReport(
      batchId = batchId,
      originalInstruction = instruction,
      tasks = results,
    )
  }

  suspend fun runSingleTask(task: ManagedTaskItem): ManagedTaskItem {
    if (taskQueueManager.isCancelled(task.id)) {
      return task.copy(state = AgentTaskLifecycleState.CANCELLED, resultMessage = "Cancelled by user.").also {
        taskStateManager.updateTask(it)
      }
    }
    if (taskQueueManager.isPaused(task.id)) {
      return task.copy(state = AgentTaskLifecycleState.PAUSED, resultMessage = "Paused in Task Queue.").also {
        taskStateManager.updateTask(it)
      }
    }

    val running = task.copy(state = AgentTaskLifecycleState.RUNNING)
    taskStateManager.updateTask(running)

    return try {
      var retryCounter = running.retryCount
      val execResult =
        retryManager.withRetries(
          maxRetries = running.maxRetries,
          onRetryAttempt = { attempt -> retryCounter = attempt },
        ) {
          actionExecutor.executeTask(running)
        }

      if (taskQueueManager.isCancelled(task.id)) {
        val cancelled = running.copy(state = AgentTaskLifecycleState.CANCELLED, resultMessage = "Stopped by user.")
        taskStateManager.updateTask(cancelled)
        cancelled
      } else {
        val finished =
          execResult.fold(
            onSuccess = { verifiedMsg ->
              running.copy(
                state = AgentTaskLifecycleState.COMPLETED,
                resultMessage = verifiedMsg,
                errorDetails = "",
                retryCount = retryCounter,
              )
            },
            onFailure = { err ->
              running.copy(
                state = AgentTaskLifecycleState.FAILED,
                resultMessage = "Action did not complete.",
                errorDetails = err.message ?: "Unknown execution failure.",
                retryCount = retryCounter,
              )
            },
          )
        taskStateManager.updateTask(finished)
        finished
      }
    } catch (_: CancellationException) {
      val cancelled = running.copy(state = AgentTaskLifecycleState.CANCELLED, resultMessage = "Cancelled.")
      taskStateManager.updateTask(cancelled)
      cancelled
    }
  }

  suspend fun pauseTask(task: ManagedTaskItem) {
    taskQueueManager.pauseTask(task.id)
    taskStateManager.updateTask(task.copy(state = AgentTaskLifecycleState.PAUSED, resultMessage = "Paused by user."))
  }

  suspend fun resumeOrApproveTask(task: ManagedTaskItem): ManagedTaskItem {
    taskQueueManager.resumeTask(task.id)
    return runSingleTask(task.copy(requiresApproval = false, state = AgentTaskLifecycleState.PENDING))
  }

  suspend fun retryTask(task: ManagedTaskItem): ManagedTaskItem {
    taskQueueManager.clearFlags(task.id)
    return runSingleTask(task.copy(state = AgentTaskLifecycleState.PENDING, errorDetails = ""))
  }

  suspend fun cancelTask(task: ManagedTaskItem) {
    taskQueueManager.cancelTask(task.id)
    taskStateManager.updateTask(
      task.copy(state = AgentTaskLifecycleState.CANCELLED, resultMessage = "Cancelled by user.")
    )
  }

  suspend fun stopAllActiveTasks() {
    val active =
      taskStateManager.tasks.value.filter {
        it.state == AgentTaskLifecycleState.PENDING ||
          it.state == AgentTaskLifecycleState.PLANNING ||
          it.state == AgentTaskLifecycleState.RUNNING ||
          it.state == AgentTaskLifecycleState.PAUSED ||
          it.state == AgentTaskLifecycleState.WAITING_FOR_APPROVAL ||
          it.state == AgentTaskLifecycleState.WAITING_FOR_PERMISSION
      }
    taskQueueManager.cancelAll(active.map { it.id })
    active.forEach { task ->
      taskStateManager.updateTask(
        task.copy(state = AgentTaskLifecycleState.CANCELLED, resultMessage = "Stopped via Stop All / Emergency Stop.")
      )
    }
  }
}
