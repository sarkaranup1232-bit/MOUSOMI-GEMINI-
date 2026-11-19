package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.analytics.parser.TabularDataParser
import com.example.core.alarms.AlarmManagerRepository
import com.example.core.alarms.AlarmPermissionManager
import com.example.core.alarms.AlarmScheduler
import com.example.core.alarms.ReminderManager
import com.example.core.alarms.TimerManager
import com.example.core.device.AndroidIntentManager
import com.example.core.device.AppLauncherManager
import com.example.core.device.DeviceActionExecutor
import com.example.core.device.DeviceActionVerifier
import com.example.core.device.InstalledAppRepository
import com.example.core.device.PackageVisibilityManager
import com.example.core.permissions.EmergencyStopController
import com.example.core.permissions.PermissionManager
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.AudioSmokeAnalyzer
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.core.voice.VoiceController
import com.example.data.local.db.MousomiDatabase
import com.example.data.repository.AddOnRepository
import com.example.domain.model.AgentTaskLifecycleState
import com.example.domain.model.AlarmRecurrence
import com.example.domain.model.DeviceActionCategory
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.OrbPopupAction
import com.example.domain.model.OrbVisualMode
import com.example.domain.model.PermissionCategory
import com.example.domain.model.ReminderEntry
import com.example.domain.model.ReminderItemType
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.SubTaskKind
import com.example.domain.model.VoiceAudioMetrics
import com.example.domain.model.VoicePreferences
import com.example.domain.router.RealtimeIntelligenceRouter
import com.example.domain.task.ActionExecutor
import com.example.domain.task.ActionVerifier
import com.example.domain.task.RetryManager
import com.example.domain.task.TaskDecomposer
import com.example.domain.task.TaskDependencyResolver
import com.example.domain.task.TaskExecutionEngine
import com.example.domain.task.TaskPlanner
import com.example.domain.task.TaskStateManager
import com.example.ui.components.FloatingOrbitPopup
import com.example.ui.components.OrbitalSmokeOrb
import com.example.ui.screens.AssistantHubScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddOnModulesTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `room v5 AddOnRepository persists reminders, managed tasks, and device action logs`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room.inMemoryDatabaseBuilder(context, MousomiDatabase::class.java)
        .addMigrations(
          MousomiDatabase.MIGRATION_1_2,
          MousomiDatabase.MIGRATION_2_3,
          MousomiDatabase.MIGRATION_3_4,
          MousomiDatabase.MIGRATION_4_5,
        )
        .allowMainThreadQueries()
        .build()

    val repo = AddOnRepository(db.addOnDao(), Json { ignoreUnknownKeys = true })

    val rem =
      ReminderEntry(
        id = "rem_1",
        type = ReminderItemType.REMINDER,
        title = "Study Compose",
        hour = 22,
        minute = 0,
        scheduledAtMs = System.currentTimeMillis() + 3600_000L,
        recurrence = AlarmRecurrence.DAILY,
        status = "Scheduled",
        verificationNote = "Scheduled cleanly",
      )
    repo.upsertReminder(rem)
    assertEquals(1, repo.getRemindersSnapshot().size)
    assertEquals("Study Compose", repo.observeReminders().first().first().title)

    val task =
      ManagedTaskItem(
        id = "t_1",
        batchId = "b_1",
        orderIndex = 0,
        description = "Open Chrome",
        kind = SubTaskKind.OPEN_APP,
        targetArg = "Open Chrome",
        state = AgentTaskLifecycleState.COMPLETED,
        resultMessage = "Opened Chrome",
      )
    repo.upsertManagedTask(task)
    assertEquals("Open Chrome", repo.getManagedTaskById("t_1")?.description)

    val log =
      DeviceActionResult(
        id = "log_1",
        category = DeviceActionCategory.CLIPBOARD_COPY,
        command = "Copy hello",
        targetLabel = "hello",
        success = true,
        statusLabel = "Copied",
        verifiedDetail = "Verified clipboard write",
      )
    repo.recordDeviceAction(log)
    assertEquals(1, repo.observeDeviceActionLogs().first().size)

    db.close()
  }

  @Test
  fun `AudioSmokeAnalyzer analyzePcmFrame emits strict SILENT for zeros and genuine DSP pitch for 220Hz wave`() {
    val silentBuffer = ShortArray(AudioSmokeAnalyzer.FRAME_SIZE_SHORTS) { 0 }
    val silentOutcome = AudioSmokeAnalyzer.analyzePcmFrame(silentBuffer)
    assertEquals(0f, silentOutcome.metrics.rmsNormalized, 0.0001f)
    assertEquals(0f, silentOutcome.metrics.peakNormalized, 0.0001f)
    assertFalse(silentOutcome.metrics.isVoiceActive)
    assertEquals(0f, silentOutcome.metrics.estimatedPitchHz, 0.01f)

    // Synthesize 220 Hz tone at 16 kHz sample rate (~0.5 amplitude)
    val n = AudioSmokeAnalyzer.FRAME_SIZE_SHORTS
    val sineBuffer =
      ShortArray(n) { i ->
        val angle = 2.0 * PI * 220.0 * i / AudioSmokeAnalyzer.SAMPLE_RATE_HZ
        (sin(angle) * 16000.0).toInt().toShort()
      }
    val voiceOutcome = AudioSmokeAnalyzer.analyzePcmFrame(sineBuffer)
    assertTrue(voiceOutcome.metrics.rmsNormalized > 0.25f)
    assertTrue(voiceOutcome.metrics.isVoiceActive)
    assertTrue(voiceOutcome.metrics.pitchConfidence > 0.7f)
    assertEquals(220f, voiceOutcome.metrics.estimatedPitchHz, 12f)
    assertTrue(voiceOutcome.metrics.lowBandEnergy > 0f || voiceOutcome.metrics.midBandEnergy > 0f)
  }

  @Test
  fun `MultiTaskAgentSystem decomposes compound command, supports pause resume cancel, and Stop All`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, MousomiDatabase::class.java).allowMainThreadQueries().build()
    val addOnRepo = AddOnRepository(db.addOnDao(), Json { ignoreUnknownKeys = true })

    val decomposer = TaskDecomposer()
    val items = decomposer.decomposeInstruction("Open Chrome, open YouTube, and set a reminder for 10 PM.")
    assertEquals(3, items.size)
    assertEquals(SubTaskKind.OPEN_APP, items[0].kind)
    assertEquals(SubTaskKind.OPEN_APP, items[1].kind)
    assertEquals(SubTaskKind.CREATE_REMINDER, items[2].kind)

    val visibility = PackageVisibilityManager(context)
    val installedRepo = InstalledAppRepository(visibility)
    val verifier = DeviceActionVerifier(context)
    val intentMgr = AndroidIntentManager(context)
    val appLauncher = AppLauncherManager(context, installedRepo, verifier)
    val devExecutor = DeviceActionExecutor(context, appLauncher, installedRepo, intentMgr, verifier, addOnRepo)

    val alarmPerm = AlarmPermissionManager(context)
    val alarmRepo = AlarmManagerRepository(addOnRepo)
    val alarmScheduler = AlarmScheduler(context, alarmPerm)
    val timerMgr = TimerManager(context, alarmScheduler, alarmRepo)
    val reminderMgr = ReminderManager(alarmRepo, alarmScheduler, timerMgr)

    val permMgr = PermissionManager(context)
    val dummyStt = object : SpeechToTextGateway {
      override fun startListening(languageTag: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) = Unit
      override fun stopListening() = Unit
      override fun release() = Unit
    }
    val dummyTts = object : TextToSpeechGateway {
      override fun speak(messageId: String, text: String, preferences: VoicePreferences, onStart: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) = Unit
      override fun stop() = Unit
      override fun release() = Unit
    }
    val voiceCtrl = VoiceController(dummyStt, dummyTts)
    val cache = TempMediaCacheManager(context)
    val emergency = EmergencyStopController(context, voiceCtrl, ScreenShareSessionManager(cache), cache)

    val taskStateMgr = TaskStateManager(addOnRepo)
    val queueMgr = com.example.domain.task.TaskQueueManager()
    val engine =
      TaskExecutionEngine(
        taskPlanner = TaskPlanner(decomposer, TaskDependencyResolver()),
        retryManager = RetryManager(),
        actionExecutor = ActionExecutor(devExecutor, reminderMgr, permMgr, ActionVerifier()),
        taskStateManager = taskStateMgr,
        taskQueueManager = queueMgr,
        emergencyStopController = emergency,
      )

    val report = engine.planAndExecuteMultiTask("Copy hello world, and set a reminder at 10 PM to study")
    assertEquals(2, report.tasks.size)
    assertEquals(AgentTaskLifecycleState.COMPLETED, report.tasks[0].state)
    assertEquals(AgentTaskLifecycleState.COMPLETED, report.tasks[1].state)
    assertTrue(verifier.verifyClipboardContains("hello world"))

    // Verify Pause & Cancel controls
    val shareTask =
      ManagedTaskItem(
        id = "pause_test",
        batchId = "b_p",
        orderIndex = 0,
        description = "Share summary",
        kind = SubTaskKind.SHARE_TEXT,
        requiresApproval = true,
      )
    taskStateMgr.updateTask(shareTask)
    engine.pauseTask(shareTask)
    assertEquals(AgentTaskLifecycleState.PAUSED, taskStateMgr.tasks.value.first { it.id == "pause_test" }.state)
    engine.stopAllActiveTasks()
    assertEquals(AgentTaskLifecycleState.CANCELLED, taskStateMgr.tasks.value.first { it.id == "pause_test" }.state)

    db.close()
  }

  @Test
  fun `ReminderManager parses times, detects missed reminders, and cancels cleanly`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, MousomiDatabase::class.java).allowMainThreadQueries().build()
    val repo = AlarmManagerRepository(AddOnRepository(db.addOnDao(), Json { ignoreUnknownKeys = true }))
    val scheduler = AlarmScheduler(context, AlarmPermissionManager(context))
    val timerMgr = TimerManager(context, scheduler, repo)
    val mgr = ReminderManager(repo, scheduler, timerMgr)

    val spec = mgr.extractTimeSpec("Remind me every morning at 7:30 am")
    assertEquals(7, spec?.hour24)
    assertEquals(30, spec?.minute)
    assertEquals(AlarmRecurrence.DAILY, spec?.recurrence)

    val overdue =
      ReminderEntry(
        id = "old_1",
        type = ReminderItemType.REMINDER,
        title = "Overdue Study",
        scheduledAtMs = System.currentTimeMillis() - 120_000L,
        status = "Scheduled",
        enabled = true,
      )
    repo.save(overdue)
    val missed = mgr.checkMissedReminders()
    assertEquals(1, missed.size)
    assertEquals("Missed", missed.first().status)

    val (createSummary, listAfterCreate) = mgr.processNaturalCommand("Remind me at 10 PM to read Android docs")
    assertTrue(createSummary.contains("scheduled", ignoreCase = true))
    assertTrue(listAfterCreate.any { it.title.contains("Read Android docs", ignoreCase = true) })

    val (cancelSummary, listAfterCancel) = mgr.processNaturalCommand("Cancel my 10 PM reminder")
    assertTrue(cancelSummary.contains("Cancelled", ignoreCase = true))
    assertTrue(listAfterCancel.any { it.status == "Cancelled" })

    db.close()
  }

  @Test
  fun `RealtimeIntelligenceRouter classifies add-on intents cleanly`() {
    val router = RealtimeIntelligenceRouter(TabularDataParser(Json { ignoreUnknownKeys = true }))

    assertEquals(
      IntelligenceIntent.MULTI_TASK,
      router.classifyIntent("Open Chrome, open YouTube, and set a reminder for 10 PM.", false, emptyList()),
    )
    assertEquals(
      IntelligenceIntent.ALARM_REMINDER,
      router.classifyIntent("Set an alarm for 6 AM", false, emptyList()),
    )
    assertEquals(
      IntelligenceIntent.DEVICE_ACTION,
      router.classifyIntent("Open Bluetooth settings", false, emptyList()),
    )
  }

  @Test
  fun `OrbitalSmokeOrb and FloatingOrbitPopup render all 12 actions and collapse on dismiss`() {
    var selected: OrbPopupAction? = null
    var dismissed = false

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        OrbitalSmokeOrb(
          mode = OrbVisualMode.LISTENING,
          audioMetrics =
            VoiceAudioMetrics(
              rmsNormalized = 0.65f,
              isVoiceActive = true,
              estimatedPitchHz = 210f,
              pitchConfidence = 0.85f,
              lowBandEnergy = 0.7f,
              midBandEnergy = 0.5f,
              highBandEnergy = 0.3f,
            ),
          orbSize = 96.dp,
        )
        FloatingOrbitPopup(
          onSelectAction = { selected = it },
          onDismiss = { dismissed = true },
        )
      }
    }

    composeRule.onNodeWithTag("orbital_smoke_orb").assertIsDisplayed()
    composeRule.onNodeWithTag("floating_orbit_popup").assertIsDisplayed()
    OrbPopupAction.entries.forEach { action ->
      composeRule.onNodeWithTag("orbit_popup_action_${action.name}").performScrollTo().assertIsDisplayed()
    }
    composeRule.onNodeWithTag("orbit_popup_action_MULTI_TASK_AGENT").performScrollTo().performClick()
    assertEquals(OrbPopupAction.MULTI_TASK_AGENT, selected)

    composeRule.onNodeWithTag("orbit_popup_close").performClick()
    assertTrue(dismissed)
  }

  @Test
  fun `AssistantHubScreen displays 3 tabs and runs compound command`() {
    var ranCommand = ""
    var timerSec = 0

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        AssistantHubScreen(
          initialTab = 0,
          managedTasks =
            listOf(
              ManagedTaskItem(
                id = "t1",
                batchId = "b1",
                orderIndex = 0,
                description = "Open Chrome",
                kind = SubTaskKind.OPEN_APP,
                state = AgentTaskLifecycleState.COMPLETED,
                resultMessage = "Verified launch",
              )
            ),
          reminders = emptyList(),
          installedApps = listOf(InstalledAppInfo("com.android.chrome", "Chrome")),
          deviceActionLogs = emptyList(),
          appSearchQuery = "",
          onAppSearchQueryChange = {},
          onRunCompoundInstruction = { ranCommand = it },
          onApproveOrResumeTask = {},
          onPauseTask = {},
          onRetryTask = {},
          onCancelTask = {},
          onStopAllTasks = {},
          onClearFinishedTasks = {},
          onStartQuickTimer = { s, _ -> timerSec = s },
          onCreateReminder = { _, _, _, _, _, _ -> },
          onCancelReminder = {},
          onDeleteReminder = {},
          onCheckMissedReminders = {},
          onExecuteDeviceCommand = {},
          onLaunchInstalledApp = {},
          onRefreshInstalledApps = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("hub_multitask_input").performTextInput("Open Chrome and start 5m timer")
    composeRule.onNodeWithTag("hub_run_multitask_button").performClick()
    assertEquals("Open Chrome and start 5m timer", ranCommand)

    composeRule.onNodeWithTag("hub_tab_alarms").performClick()
    composeRule.onNodeWithTag("quick_timer_5m").performClick()
    assertEquals(300, timerSec)

    composeRule.onNodeWithTag("hub_tab_device").performClick()
    composeRule.onNodeWithText("Chrome").assertIsDisplayed()
  }

  @Test
  fun `PermissionCenterScreen displays all 20 categories including Exact Alarm, Location, NFC, and USB`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val categories = PermissionManager(context).refreshAll(ScreenShareStatus.STOPPED)
    assertEquals(20, PermissionCategory.entries.size)
    assertEquals(20, categories.size)

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        PermissionCenterScreen(
          categories = categories,
          isEmergencyActive = false,
          onRefreshPermissions = {},
          onTriggerEmergencyStop = {},
          onOpenScreenShare = {},
          onStopScreenShare = {},
          onOpenOverlaySettings = {},
          onOpenNotificationAccess = {},
          onOpenAssistantSettings = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("permission_card_EXACT_ALARM").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("permission_card_LOCATION_FINE").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("permission_card_NFC_HARDWARE").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("permission_card_USB_DEVICES").performScrollTo().assertIsDisplayed()
  }
}
