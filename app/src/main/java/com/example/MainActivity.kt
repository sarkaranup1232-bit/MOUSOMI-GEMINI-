package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.assistant.AssistantRoleHelper
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.permissions.PermissionCenterHelper
import com.example.core.security.configureAppCheck
import com.example.domain.auth.OptionalAuthGateway
import com.example.domain.model.OrbPopupAction
import com.example.domain.model.OrbQuickAction
import com.example.service.LiveVoiceSessionService
import com.example.service.MediaProjectionCaptureService
import com.example.service.MousomiOverlayService
import com.example.ui.components.EdgeLightPerimeterOverlay
import com.example.ui.components.MousomiDesignTokens
import com.example.ui.screens.AssistantHubScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.MousomiLensScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import com.example.ui.viewmodel.LensViewModel
import com.example.ui.viewmodel.MousomiViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject

object MousomiRoutes {
  const val CHAT = "chat"
  const val SETTINGS = "settings"
  const val CONTROL_CENTER = "control_center"
  const val PERMISSIONS = "permissions"
  const val ASSISTANT_HUB = "assistant_hub"
  const val LENS = "lens"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var authGateway: OptionalAuthGateway

  private var pendingQuickActionState = mutableStateOf<OrbQuickAction?>(null)
  private var pendingPopupActionState = mutableStateOf<OrbPopupAction?>(null)
  private var pendingAttachScreenSelectionState = mutableStateOf(false)
  private var pendingRouteActionState = mutableStateOf<String?>(null)

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractQuickAction(intent)?.let { pendingQuickActionState.value = it }
    extractPopupAction(intent)?.let { pendingPopupActionState.value = it }
    if (intent.action == ACTION_ATTACH_SCREEN_SELECTION) {
      pendingAttachScreenSelectionState.value = true
    }
    if (intent.action == ACTION_OPEN_CONTROL_CENTER || intent.action == ACTION_OPEN_SETTINGS) {
      pendingRouteActionState.value = intent.action
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    enableEdgeToEdge()

    pendingQuickActionState.value = extractQuickAction(intent)
    pendingPopupActionState.value = extractPopupAction(intent)
    pendingAttachScreenSelectionState.value = intent?.action == ACTION_ATTACH_SCREEN_SELECTION
    if (intent?.action == ACTION_OPEN_CONTROL_CENTER || intent?.action == ACTION_OPEN_SETTINGS) {
      pendingRouteActionState.value = intent.action
    }

    setContent {
      val authSession by
        authGateway.observeSession().collectAsStateWithLifecycle(
          initialValue = authGateway.getCurrentSession()
        )
      val canEnterApp =
        (authSession.isSignedIn && authSession.uid.isNotBlank()) || authSession.isGuest
      val sessionKey = if (authSession.isGuest) "guest_local" else authSession.uid

      if (!canEnterApp) {
        MousomiTheme(themeMode = AppThemeMode.DARK) {
          AuthScreen(
            authGateway = authGateway,
            onAuthSuccess = {},
          )
        }
      } else {
        key(sessionKey) {
          val viewModel: MousomiViewModel = hiltViewModel(key = sessionKey)
          val uiState by viewModel.uiState.collectAsStateWithLifecycle()
          val navController = rememberNavController()

      val screenCaptureLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
          val data = result.data
          if (result.resultCode == Activity.RESULT_OK && data != null) {
            val svcIntent =
              MediaProjectionCaptureService.buildStartIntent(
                context = this@MainActivity,
                resultCode = result.resultCode,
                data = data,
              )
            ContextCompat.startForegroundService(this@MainActivity, svcIntent)
          } else {
            viewModel.onStopScreenShare()
          }
        }

      val micPermissionLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
          viewModel.onAudioPermissionResult(granted)
        }

      val assistantRoleLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult()
        ) { _ -> }

      val startScreenShareFlow: () -> Unit = {
        viewModel.onMarkScreenShareRequestingConsent()
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mgr != null) {
          screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
        } else {
          viewModel.onStopScreenShare()
        }
      }

      val stopScreenShareFlow: () -> Unit = {
        startService(MediaProjectionCaptureService.buildStopIntent(this@MainActivity))
        viewModel.onStopScreenShare()
      }

      val screenSelectionConsentLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
          val data = result.data
          if (result.resultCode == Activity.RESULT_OK && data != null) {
            val svcIntent =
              MediaProjectionCaptureService.buildStartIntent(
                context = this@MainActivity,
                resultCode = result.resultCode,
                data = data,
                forScreenSelection = true,
              )
            ContextCompat.startForegroundService(this@MainActivity, svcIntent)
          } else {
            viewModel.onScreenSelectionConsentCancelled()
          }
        }

      val startScreenSelectionFlow: () -> Unit = {
        viewModel.onStartScreenSelectionFlow {
          val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
          if (mgr != null) {
            screenSelectionConsentLauncher.launch(mgr.createScreenCaptureIntent())
          } else {
            viewModel.onScreenSelectionConsentCancelled()
          }
        }
      }

      val activeQuickAction by pendingQuickActionState
      val activePopupAction by pendingPopupActionState
      val activeAttachScreenSelection by pendingAttachScreenSelectionState
      val activeRouteAction by pendingRouteActionState

      LaunchedEffect(activeRouteAction) {
        val routeAction = activeRouteAction ?: return@LaunchedEffect
        pendingRouteActionState.value = null
        when (routeAction) {
          ACTION_OPEN_CONTROL_CENTER ->
            navController.navigate(MousomiRoutes.CONTROL_CENTER) { launchSingleTop = true }
          ACTION_OPEN_SETTINGS ->
            navController.navigate(MousomiRoutes.SETTINGS) { launchSingleTop = true }
        }
      }

      LaunchedEffect(activeAttachScreenSelection) {
        if (activeAttachScreenSelection) {
          pendingAttachScreenSelectionState.value = false
          viewModel.onReturnedFromSystemSelection()
          navController.navigate(MousomiRoutes.CHAT) {
            launchSingleTop = true
            popUpTo(MousomiRoutes.CHAT) { inclusive = false }
          }
        }
      }

      LaunchedEffect(activeQuickAction) {
        val action = activeQuickAction ?: return@LaunchedEffect
        pendingQuickActionState.value = null
        viewModel.onHandleOrbQuickAction(
          action = action,
          onRequestScreenShare = startScreenShareFlow,
          onNavigateToControlCenter = {
            navController.navigate(MousomiRoutes.CONTROL_CENTER) { launchSingleTop = true }
          },
        )
      }

      LaunchedEffect(activePopupAction) {
        val popup = activePopupAction ?: return@LaunchedEffect
        pendingPopupActionState.value = null
        viewModel.onHandleOrbPopupAction(
          action = popup,
          onRequestScreenShare = startScreenShareFlow,
          onNavigateToLens = {
            navController.navigate(MousomiRoutes.LENS) { launchSingleTop = true }
          },
          onNavigateToAssistantHub = { tab ->
            viewModel.onSetRequestedHubTab(tab)
            navController.navigate(MousomiRoutes.ASSISTANT_HUB) { launchSingleTop = true }
          },
        )
      }

      // Sync optional system-wide floating overlay service safely when explicitly toggled in settings
      var wasSystemOverlayEnabled by remember { mutableStateOf(uiState.edgeLightConfig.systemOverlayEnabled) }
      LaunchedEffect(uiState.edgeLightConfig.systemOverlayEnabled) {
        val enabled = uiState.edgeLightConfig.systemOverlayEnabled
        if (enabled && Settings.canDrawOverlays(this@MainActivity)) {
          wasSystemOverlayEnabled = true
          val intent = Intent(this@MainActivity, MousomiOverlayService::class.java)
          ContextCompat.startForegroundService(this@MainActivity, intent)
        } else if (!enabled && wasSystemOverlayEnabled) {
          wasSystemOverlayEnabled = false
          stopService(Intent(this@MainActivity, MousomiOverlayService::class.java))
        }
      }

      // Sync foreground microphone service during active user-initiated Live Voice sessions
      LaunchedEffect(uiState.isGeminiLiveOpen, uiState.hasAudioPermission) {
        if (uiState.isGeminiLiveOpen && uiState.hasAudioPermission) {
          ContextCompat.startForegroundService(
            this@MainActivity,
            LiveVoiceSessionService.buildStartIntent(this@MainActivity),
          )
        } else {
          stopService(Intent(this@MainActivity, LiveVoiceSessionService::class.java))
        }
      }

      MousomiTheme(themeMode = uiState.themeMode) {
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(MousomiDesignTokens.ScreenCornerRadius))
                .clipToBounds()
          ) {
            NavHost(
              navController = navController,
              startDestination = MousomiRoutes.CHAT,
              enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
              exitTransition = { fadeOut() },
              popEnterTransition = { fadeIn() },
              popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
            ) {
            composable(MousomiRoutes.CHAT) {
              MainChatScreen(
                uiState = uiState,
                suggestions = viewModel.defaultSuggestions,
                viewModel = viewModel,
                onNavigateToSettings = {
                  navController.navigate(MousomiRoutes.SETTINGS) { launchSingleTop = true }
                },
                onNavigateToControlCenter = {
                  navController.navigate(MousomiRoutes.CONTROL_CENTER) { launchSingleTop = true }
                },
                onNavigateToPermissions = {
                  navController.navigate(MousomiRoutes.PERMISSIONS) { launchSingleTop = true }
                },
                onRequestStartScreenShare = startScreenShareFlow,
                onStopScreenShare = stopScreenShareFlow,
                onRequestStartScreenSelection = startScreenSelectionFlow,
                onNavigateToAssistantHub = { tab ->
                  viewModel.onSetRequestedHubTab(tab)
                  navController.navigate(MousomiRoutes.ASSISTANT_HUB) { launchSingleTop = true }
                },
                onNavigateToLens = {
                  navController.navigate(MousomiRoutes.LENS) { launchSingleTop = true }
                },
              )
            }

            composable(MousomiRoutes.LENS) {
              val lensViewModel: LensViewModel = hiltViewModel()
              MousomiLensScreen(
                viewModel = lensViewModel,
                onContinueInChat = { result ->
                  viewModel.onImportLensAnalysisToChat(
                    result = result,
                    followUpTurns = lensViewModel.uiState.value.followUpTurns,
                  )
                  navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
              )
            }

            composable(MousomiRoutes.ASSISTANT_HUB) {
              AssistantHubScreen(
                initialTab = uiState.requestedHubTab,
                managedTasks = uiState.managedTasks,
                reminders = uiState.reminders,
                installedApps = uiState.installedApps,
                deviceActionLogs = uiState.deviceActionLogs,
                appSearchQuery = uiState.appSearchQuery,
                onAppSearchQueryChange = viewModel::onAppSearchQueryChange,
                onRunCompoundInstruction = viewModel::onRunCompoundInstruction,
                onApproveOrResumeTask = viewModel::onApproveOrResumeTask,
                onPauseTask = viewModel::onPauseTask,
                onRetryTask = viewModel::onRetryTask,
                onCancelTask = viewModel::onCancelTask,
                onStopAllTasks = viewModel::onStopAllTasks,
                onClearFinishedTasks = viewModel::onClearFinishedTasks,
                onStartQuickTimer = viewModel::onStartQuickTimer,
                onCreateReminder = viewModel::onCreateReminder,
                onCancelReminder = viewModel::onCancelReminder,
                onDeleteReminder = viewModel::onDeleteReminder,
                onCheckMissedReminders = viewModel::onCheckMissedReminders,
                onExecuteDeviceCommand = viewModel::onExecuteDeviceCommand,
                onLaunchInstalledApp = viewModel::onLaunchInstalledApp,
                onRefreshInstalledApps = { viewModel.onRefreshInstalledApps() },
                onBack = { navController.popBackStack() },
              )
            }

            composable(MousomiRoutes.CONTROL_CENTER) {
              ControlCenterScreen(
                uiState = uiState,
                onUpdateEdgeLight = viewModel::onUpdateEdgeLightConfig,
                onUpdateFloatingOrb = viewModel::onUpdateFloatingOrbConfig,
                onUpdateScreenConfig = viewModel::onUpdateScreenIntelligenceConfig,
                onRequestStartScreenShare = startScreenShareFlow,
                onStopScreenShare = stopScreenShareFlow,
                onAnalyzeCurrentScreen = {
                  navController.popBackStack()
                  viewModel.analyzeLatestScreenFrameOrPrompt(startScreenShareFlow)
                },
                onOpenDrawingStudio = { viewModel.onToggleDrawingOverlay(true) },
                onUpdateAgentBudget = viewModel::onUpdateAgentBudgetConfig,
                onSaveMemory = viewModel::onSaveMemoryItem,
                onDeleteMemory = viewModel::onDeleteMemoryItem,
                onClearMemories = viewModel::onClearAllMemories,
                onUpdateNotificationConfig = viewModel::onUpdateNotificationConfig,
                onDismissNotificationSummary = viewModel::onDismissNotificationSummary,
                onRunAutomation = viewModel::onRunAutomationWorkflow,
                onConfirmAutomation = viewModel::onConfirmAutomationTask,
                onCancelAutomation = viewModel::onCancelAutomationTask,
                onUpdatePrivacyPerformance = viewModel::onUpdatePrivacyPerformanceConfig,
                onClearTempMediaCache = viewModel::onClearTempMediaCache,
                onToggleSystemOverlay = { enabled ->
                  if (enabled && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(PermissionCenterHelper.createOverlaySettingsIntent(this@MainActivity))
                  }
                },
                onLaunchFloatingControlBar = {
                  if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(PermissionCenterHelper.createOverlaySettingsIntent(this@MainActivity))
                  } else {
                    viewModel.onUpdateEdgeLightConfig(
                      uiState.edgeLightConfig.copy(systemOverlayEnabled = true)
                    )
                    ContextCompat.startForegroundService(
                      this@MainActivity,
                      MousomiOverlayService.buildShowControlBarIntent(this@MainActivity),
                    )
                  }
                },
                onOpenOverlaySettings = {
                  startActivity(PermissionCenterHelper.createOverlaySettingsIntent(this@MainActivity))
                },
                onOpenNotificationAccessSettings = {
                  startActivity(PermissionCenterHelper.createNotificationListenerSettingsIntent())
                },
                onRequestAssistantRole = {
                  assistantRoleLauncher.launch(AssistantRoleHelper.createRequestAssistantIntent(this@MainActivity))
                },
                onRequestMicPermission = {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onOpenApiAndModelSettings = {
                  navController.navigate(MousomiRoutes.SETTINGS) { launchSingleTop = true }
                },
                onOpenFullPermissionCenter = {
                  navController.navigate(MousomiRoutes.PERMISSIONS) { launchSingleTop = true }
                },
                onTriggerEmergencyStop = viewModel::onTriggerEmergencyStop,
                onBack = { navController.popBackStack() },
              )
            }

            composable(MousomiRoutes.PERMISSIONS) {
              PermissionCenterScreen(
                categories = uiState.permissionCategories,
                isEmergencyActive = uiState.isAnyLiveSessionActive,
                onRefreshPermissions = viewModel::onRefreshPermissions,
                onTriggerEmergencyStop = viewModel::onTriggerEmergencyStop,
                onOpenScreenShare = startScreenShareFlow,
                onStopScreenShare = stopScreenShareFlow,
                onOpenOverlaySettings = {
                  startActivity(PermissionCenterHelper.createOverlaySettingsIntent(this@MainActivity))
                },
                onOpenNotificationAccess = {
                  startActivity(PermissionCenterHelper.createNotificationListenerSettingsIntent())
                },
                onOpenAssistantSettings = {
                  assistantRoleLauncher.launch(AssistantRoleHelper.createRequestAssistantIntent(this@MainActivity))
                },
                onBack = { navController.popBackStack() },
              )
            }

            composable(MousomiRoutes.SETTINGS) {
              SettingsScreen(
                geminiApiKey = uiState.geminiApiKey,
                groqApiKey = uiState.groqApiKey,
                themeMode = uiState.themeMode,
                connectionMode = uiState.connectionMode,
                proxyBaseUrl = uiState.proxyBaseUrl,
                hydrogenBackingModelId = uiState.hydrogenBackingModelId,
                groqReasoningModelId = uiState.groqReasoningModelId,
                imageModelId = uiState.imageModelId,
                externalSearchUrl = uiState.externalSearchUrl,
                externalSearchApiKey = uiState.externalSearchApiKey,
                analyticsSandboxEnabled = uiState.analyticsSandboxEnabled,
                composerGlowIntensity = uiState.composerGlowIntensity,
                voicePreferences = uiState.voicePreferences,
                settingsErrorMessage = uiState.settingsErrorMessage,
                onGeminiApiKeyChange = viewModel::onUpdateGeminiApiKey,
                onGroqApiKeyChange = viewModel::onUpdateGroqApiKey,
                onThemeModeChange = viewModel::onUpdateThemeMode,
                onConnectionModeChange = viewModel::onUpdateConnectionMode,
                onProxyBaseUrlChange = viewModel::onUpdateProxyBaseUrl,
                onHydrogenBackingModelIdChange = viewModel::onUpdateHydrogenBackingModelId,
                onGroqReasoningModelIdChange = viewModel::onUpdateGroqReasoningModelId,
                onImageModelIdChange = viewModel::onUpdateImageModelId,
                onExternalSearchUrlChange = viewModel::onUpdateExternalSearchUrl,
                onExternalSearchApiKeyChange = viewModel::onUpdateExternalSearchApiKey,
                onAnalyticsSandboxEnabledChange = viewModel::onUpdateAnalyticsSandboxEnabled,
                onComposerGlowIntensityChange = viewModel::onUpdateComposerGlowIntensity,
                onClearTempMediaCache = viewModel::onClearTempMediaCache,
                onPreferredLanguageChange = viewModel::onUpdatePreferredLanguage,
                onVoiceGenderChange = viewModel::onUpdateVoiceGender,
                onActivationSoundChange = viewModel::onUpdateActivationSound,
                onAutoSpeakChange = viewModel::onUpdateAutoSpeak,
                onSpeechRateChange = viewModel::onUpdateSpeechRate,
                onOpenPermissionCenter = {
                  navController.navigate(MousomiRoutes.PERMISSIONS) { launchSingleTop = true }
                },
                onSaveSettings = viewModel::onSaveSettings,
                oneTapApiKeyInput = uiState.oneTapApiKeyInput,
                isActivatingApi = uiState.isActivatingApi,
                activationResult = uiState.activationResult,
                onOneTapApiKeyChange = viewModel::onOneTapApiKeyChanged,
                onActivateOneTap = viewModel::onActivateOneTapApiKey,
                onVerifyConnection = viewModel::onVerifyCurrentConnection,
                onDeactivateCredentials = viewModel::onDeactivateApiCredentials,
                onBack = { navController.popBackStack() },
              )
            }
            }
          }

          val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
          val edgeParams =
            EdgeLightStateMapper.resolve(
              config = uiState.edgeLightConfig,
              responseState = uiState.responseState,
              voiceState = uiState.voiceState,
              voiceRmsNormalized = uiState.voiceRmsNormalized,
              screenShareStatus = uiState.screenShareStatus,
              notificationAlertActive = uiState.notificationPulseActive,
              isSystemPowerSave = uiState.isSystemPowerSave,
              currentHour = currentHour,
              assistantSessionPhase = uiState.assistantSessionPhase,
              edgeActivationSignal = uiState.edgeActivationSignal,
            )
          EdgeLightPerimeterOverlay(params = edgeParams)
        }
      }
        }
      }
    }
  }

  private fun extractQuickAction(intent: Intent?): OrbQuickAction? {
    if (intent == null) return null
    val raw = intent.getStringExtra(EXTRA_QUICK_ACTION)
    if (!raw.isNullOrBlank()) {
      return runCatching { OrbQuickAction.valueOf(raw) }.getOrNull()
    }
    if (intent.action == Intent.ACTION_ASSIST) {
      return OrbQuickAction.START_VOICE
    }
    return null
  }

  private fun extractPopupAction(intent: Intent?): OrbPopupAction? {
    val raw = intent?.getStringExtra(EXTRA_ORB_POPUP_ACTION) ?: return null
    return runCatching { OrbPopupAction.valueOf(raw) }.getOrNull()
  }

  companion object {
    const val EXTRA_QUICK_ACTION = "extra_orb_quick_action"
    const val EXTRA_ORB_POPUP_ACTION = "extra_orb_popup_action"
    const val ACTION_ATTACH_SCREEN_SELECTION = "com.example.action.ATTACH_SCREEN_SELECTION"
    const val ACTION_OPEN_CONTROL_CENTER = "com.example.action.OPEN_CONTROL_CENTER"
    const val ACTION_OPEN_SETTINGS = "com.example.action.OPEN_SETTINGS"
  }
}
