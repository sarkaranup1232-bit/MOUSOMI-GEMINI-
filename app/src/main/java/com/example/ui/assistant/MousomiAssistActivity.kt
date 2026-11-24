package com.example.ui.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.core.accessibility.AccessibilityActionResult
import com.example.core.accessibility.AccessibilityAgentController
import com.example.core.agent.AgentTaskPlanner
import com.example.core.agent.PlannedAgentOutcome
import com.example.core.alarms.TimerManager
import com.example.core.assistant.AssistantRoleHelper
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.AssistantSurfaceState
import com.example.core.assistant.AssistantVisualState
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.device.AgentCommandIntent
import com.example.core.device.DeviceActionExecutor
import com.example.core.device.PendingPhoneAction
import com.example.core.device.PendingPhoneActionType
import com.example.core.device.PendingSocialAction
import com.example.core.device.PhoneActionController
import com.example.core.device.ResolvedContact
import com.example.core.device.SocialIntegrationManager
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.voice.DictationTranscriptMerger
import com.example.core.voice.GeminiLiveVoiceEngine
import com.example.core.voice.LiveVoiceSessionManager
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.domain.memory.PreferenceConfirmationManager
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightIntensity
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.SettingsRepository
import com.example.service.MousomiVoiceInteractionService
import com.example.ui.components.AssistantSessionOverlayUi
import com.example.ui.overlay.ScreenCaptureConsentActivity
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Official Android [Intent.ACTION_ASSIST] and [Intent.ACTION_VOICE_COMMAND] entry Activity.
 *
 * When Android / Samsung One UI invokes an Activity Assist intent (for example right after app
 * installation before VoiceInteractionService warms up, or on OEM paths dispatching ACTION_ASSIST),
 * this Activity first delegates to [MousomiVoiceInteractionService.requestSystemAssistSession] if
 * the system VoiceInteractionService is bound; otherwise it presents the translucent Assistant
 * overlay directly in-window without requiring SYSTEM_ALERT_WINDOW or AccessibilityService.
 */
@AndroidEntryPoint
class MousomiAssistActivity : ComponentActivity() {

  @Inject lateinit var sessionStateManager: AssistantSessionStateManager
  @Inject lateinit var settingsRepository: SettingsRepository
  @Inject lateinit var aiOrchestrator: AiOrchestrator
  @Inject lateinit var agentTaskPlanner: AgentTaskPlanner
  @Inject lateinit var voiceController: VoiceController
  @Inject lateinit var geminiLiveVoiceEngine: GeminiLiveVoiceEngine
  @Inject lateinit var liveVoiceSessionManager: LiveVoiceSessionManager
  @Inject lateinit var screenShareSessionManager: ScreenShareSessionManager
  @Inject lateinit var deviceActionExecutor: DeviceActionExecutor
  @Inject lateinit var phoneActionController: PhoneActionController
  @Inject lateinit var socialIntegrationManager: SocialIntegrationManager
  @Inject lateinit var timerManager: TimerManager
  @Inject lateinit var accessibilityAgentController: AccessibilityAgentController
  @Inject lateinit var preferenceConfirmationManager: PreferenceConfirmationManager

  private var queryJob: Job? = null
  private var ownerToken: Long = 0L

  private var inputTextState by mutableStateOf("")
  private var dictationCommittedPrefixState by mutableStateOf("")
  private var isDictationSessionActiveState by mutableStateOf(false)
  private var isDictationPausedState by mutableStateOf(false)
  private var dictationErrorState by mutableStateOf<String?>(null)
  private var assistantResponseState by mutableStateOf("")
  private var userTranscriptState by mutableStateOf("")
  private var statusBannerState by mutableStateOf<String?>(null)
  private var isGeneratingState by mutableStateOf(false)
  private var responsePhaseState by mutableStateOf<AiResponseState>(AiResponseState.Idle)
  private var pendingPhoneActionState by mutableStateOf<PendingPhoneAction?>(null)
  private var pendingSocialActionState by mutableStateOf<PendingSocialAction?>(null)
  private var pendingAccessibilityActionState by mutableStateOf<AccessibilityActionResult?>(null)
  private var usingLiveSocketState by mutableStateOf(false)

  private val micPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        startOrResumeDictation()
      } else {
        dictationErrorState = "Microphone permission is required for voice input."
        isDictationSessionActiveState = true
        isDictationPausedState = true
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Prefer native system VoiceInteractionSession overlay whenever the bound service is ready.
    if (MousomiVoiceInteractionService.requestSystemAssistSession(intent?.extras)) {
      finishQuietly()
      return
    }

    WindowCompat.setDecorFitsSystemWindows(window, false)
    enableEdgeToEdge()
    window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setDimAmount(0.22f)
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    ownerToken = sessionStateManager.claimActiveSessionToken()
    sessionStateManager.openStandardAssistant(EdgeActivationEdge.BOTTOM_CORNER)
    lifecycleScope.launch {
      delay(560L)
      if (sessionStateManager.isActiveSessionToken(ownerToken)) {
        sessionStateManager.onActivationAnimationSettled()
      }
    }

    val roleStatus = AssistantRoleHelper.getStatus(this)
    if (!roleStatus.isDefaultAssistant) {
      statusBannerState =
        "Opened via ACTION_ASSIST. Set Mousomi AI as Default Digital Assistant in Settings for system corner-swipe anywhere."
    }

    observeVoiceState()

    if (intent?.action == Intent.ACTION_VOICE_COMMAND && hasMicPermission()) {
      startOrResumeDictation()
    }

    setContent {
      val visualState by sessionStateManager.visualState.collectAsState()
      val sessionPhase by sessionStateManager.currentPhase.collectAsState()
      val edgeSignal by sessionStateManager.edgeActivation.collectAsState()
      val reactiveFrame by liveVoiceSessionManager.orbFrame.collectAsState()
      val livePaused by liveVoiceSessionManager.isPaused.collectAsState()
      val liveMuted by liveVoiceSessionManager.isMuted.collectAsState()
      val voiceState by voiceController.voiceState.collectAsState()
      val voiceRms by voiceController.audioRmsNormalized.collectAsState()
      val screenShareStatus by screenShareSessionManager.status.collectAsState()
      val liveAssistantTranscript by geminiLiveVoiceEngine.assistantTranscript.collectAsState()
      val liveUserTranscript by geminiLiveVoiceEngine.userTranscript.collectAsState()

      LaunchedEffect(liveAssistantTranscript) {
        if (liveAssistantTranscript.isNotBlank()) assistantResponseState = liveAssistantTranscript
      }
      LaunchedEffect(liveUserTranscript) {
        if (liveUserTranscript.isNotBlank()) userTranscriptState = liveUserTranscript
      }

      val edgeParams =
        EdgeLightStateMapper.resolve(
          config =
            EdgeLightConfig(
              enabled = true,
              assistantEdgeGlowEnabled = true,
              preset = EdgeLightPreset.GEMINI_FLOW,
              intensity = EdgeLightIntensity.BRIGHT,
              brightness = 0.96f,
              borderThicknessDp = 5.5f,
              cornerRadiusDp = 34f,
            ),
          responseState = responsePhaseState,
          voiceState = voiceState,
          voiceRmsNormalized = maxOf(voiceRms, reactiveFrame.reactiveAmplitude),
          screenShareStatus = screenShareStatus,
          notificationAlertActive = false,
          isSystemPowerSave = false,
          currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
          forcePreview = true,
          assistantSessionPhase = sessionPhase,
          edgeActivationSignal = edgeSignal,
        )

      MousomiTheme(themeMode = AppThemeMode.DARK) {
        AssistantSessionOverlayUi(
          visualState =
            if (visualState == AssistantVisualState.HIDDEN) AssistantVisualState.STANDARD_ASSISTANT
            else visualState,
          surfaceState = AssistantSurfaceState.EXPANDED,
          edgeLightParams = edgeParams,
          reactiveFrame = reactiveFrame,
          inputText = inputTextState,
          onInputChanged = { updated ->
            inputTextState = updated
            if (isDictationPausedState || voiceState !is VoiceState.Listening) {
              dictationCommittedPrefixState = updated
            }
          },
          onSendQuery = { prompt ->
            stopDictation()
            submitQuery(prompt)
          },
          onMicClick = { toggleMic() },
          onStartDictation = { requestMicOrStartDictation() },
          onPauseDictation = { pauseDictation() },
          onStopDictation = { stopDictation() },
          onRetryDictation = { requestMicOrStartDictation() },
          onLiveButtonClick = {
            if (visualState == AssistantVisualState.LIVE_ASSISTANT) {
              stopLiveToStandard()
            } else {
              startLiveAssistantMode()
            }
          },
          onDismissLiveToStandard = { stopLiveToStandard() },
          onShareScreenWithLive = {
            startActivity(ScreenCaptureConsentActivity.buildScreenShareIntent(this, fromOverlay = false))
          },
          onStopGeneration = {
            queryJob?.cancel()
            queryJob = null
            isGeneratingState = false
            responsePhaseState = AiResponseState.Cancelled
          },
          onOpenFullApp = { openMainApp() },
          onOpenScreenSelection = { openMainApp() },
          onOpenDrawingStudio = { openMainApp() },
          onOpenPhotoPicker = { openMainApp() },
          onCollapseToOrb = { dismissFallbackActivity() },
          onCloseSession = { dismissFallbackActivity() },
          isGenerating = isGeneratingState,
          isVoiceListening =
            voiceState is VoiceState.Listening ||
              (visualState == AssistantVisualState.LIVE_ASSISTANT && !livePaused && !liveMuted),
          voiceRmsNormalized = maxOf(voiceRms, reactiveFrame.reactiveAmplitude),
          isDictationSessionActive = isDictationSessionActiveState,
          isDictationPaused = isDictationPausedState,
          voiceErrorText = dictationErrorState,
          isLivePaused = livePaused,
          isLiveMuted = liveMuted,
          isScreenShareActive = screenShareStatus != ScreenShareStatus.STOPPED,
          userTranscript = userTranscriptState,
          assistantResponseText = assistantResponseState,
          statusBannerText = statusBannerState,
          pendingPhoneAction = pendingPhoneActionState,
          onConfirmPhoneAction = { action -> confirmPhoneAction(action) },
          onDismissPhoneAction = { pendingPhoneActionState = null },
          onSelectPhoneCandidate = { candidate -> selectPhoneCandidate(candidate) },
          pendingSocialAction = pendingSocialActionState,
          onConfirmSocialAction = { action -> confirmSocialAction(action) },
          onDismissSocialAction = { pendingSocialActionState = null },
          onSelectSocialCandidate = { candidate -> selectSocialCandidate(candidate) },
          pendingAccessibilityAction = pendingAccessibilityActionState,
          onConfirmAccessibilityAction = { action -> confirmAccessibilityAction(action) },
          onDismissAccessibilityAction = { pendingAccessibilityActionState = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (MousomiVoiceInteractionService.requestSystemAssistSession(intent.extras)) {
      finishQuietly()
      return
    }
    sessionStateManager.openStandardAssistant(EdgeActivationEdge.BOTTOM_CORNER)
    if (intent.action == Intent.ACTION_VOICE_COMMAND && hasMicPermission()) {
      startOrResumeDictation()
    }
  }

  override fun onDestroy() {
    cleanupVoiceAndQueries()
    sessionStateManager.dismissAssistantSessionIfOwner(
      ownerToken = ownerToken,
      keepScreenShare = screenShareSessionManager.status.value != ScreenShareStatus.STOPPED,
    )
    super.onDestroy()
  }

  private fun hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  private fun requestMicOrStartDictation() {
    if (hasMicPermission()) {
      startOrResumeDictation()
    } else {
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  private fun toggleMic() {
    if (!hasMicPermission()) {
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return
    }
    if (sessionStateManager.visualState.value == AssistantVisualState.LIVE_ASSISTANT) {
      val nextMuted = !liveVoiceSessionManager.isMuted.value
      if (usingLiveSocketState) {
        geminiLiveVoiceEngine.setMuted(nextMuted)
      } else {
        liveVoiceSessionManager.setMuted(nextMuted)
        if (nextMuted) voiceController.stopListening() else startFallbackDictation()
      }
      return
    }
    if (voiceController.voiceState.value is VoiceState.Listening && !isDictationPausedState) {
      pauseDictation()
    } else {
      startOrResumeDictation()
    }
  }

  private fun startOrResumeDictation() {
    dictationCommittedPrefixState = inputTextState
    isDictationSessionActiveState = true
    isDictationPausedState = false
    dictationErrorState = null
    startFallbackDictation()
  }

  private fun pauseDictation() {
    voiceController.stopListening()
    dictationCommittedPrefixState = inputTextState
    isDictationSessionActiveState = true
    isDictationPausedState = true
  }

  private fun stopDictation() {
    voiceController.stopListening()
    isDictationSessionActiveState = false
    isDictationPausedState = false
    dictationErrorState = null
    dictationCommittedPrefixState = inputTextState
  }

  private fun startFallbackDictation() {
    lifecycleScope.launch {
      val settings = settingsRepository.getSettingsSnapshot()
      voiceController.playActivationChime(settings.voicePreferences)
      voiceController.interruptSpeakingAndListen(settings.voicePreferences.preferredLanguage)
    }
  }

  private fun startLiveAssistantMode() {
    if (!hasMicPermission()) {
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return
    }
    statusBannerState = null
    sessionStateManager.enterLiveAssistant(EdgeActivationEdge.BOTTOM)
    lifecycleScope.launch {
      val settings = settingsRepository.getSettingsSnapshot()
      voiceController.playActivationChime(settings.voicePreferences)
      if (geminiLiveVoiceEngine.isFirebaseAvailable()) {
        usingLiveSocketState = true
        geminiLiveVoiceEngine.startSession(
          preferences = settings.voicePreferences,
          allowInterruptions = settings.privacyPerformanceConfig.allowVoiceInterruption,
          onFallbackRequired = { reason ->
            lifecycleScope.launch {
              usingLiveSocketState = false
              statusBannerState = "Switched to speech fallback: $reason"
              voiceController.interruptSpeakingAndListen(settings.voicePreferences.preferredLanguage)
            }
          },
        )
      } else {
        usingLiveSocketState = false
        liveVoiceSessionManager.onBeginSession(
          settings.voicePreferences,
          settings.privacyPerformanceConfig.allowVoiceInterruption,
        )
        voiceController.interruptSpeakingAndListen(settings.voicePreferences.preferredLanguage)
      }
    }
  }

  private fun stopLiveToStandard() {
    usingLiveSocketState = false
    geminiLiveVoiceEngine.stopAndClose(keepScreenShare = screenShareSessionManager.status.value != ScreenShareStatus.STOPPED)
    liveVoiceSessionManager.onEnded(keepScreenShare = screenShareSessionManager.status.value != ScreenShareStatus.STOPPED)
    voiceController.stopListening()
    voiceController.stopSpeaking()
    sessionStateManager.dismissLiveToStandard()
  }

  private fun observeVoiceState() {
    lifecycleScope.launch {
      voiceController.voiceState.collectLatest { st ->
        val isLive = sessionStateManager.visualState.value == AssistantVisualState.LIVE_ASSISTANT
        when (st) {
          is VoiceState.Listening -> {
            dictationErrorState = null
            if (isLive && st.partialTranscript.isNotBlank()) {
              userTranscriptState = st.partialTranscript
            } else if (isDictationSessionActiveState && !isDictationPausedState && st.partialTranscript.isNotBlank()) {
              inputTextState = DictationTranscriptMerger.mergePartial(dictationCommittedPrefixState, st.partialTranscript)
            }
          }
          is VoiceState.TranscriptReady -> {
            val transcript = st.transcript.trim()
            dictationErrorState = null
            if (isLive) {
              if (transcript.isNotBlank()) {
                userTranscriptState = transcript
                submitQuery(transcript)
              }
            } else {
              if (transcript.isNotBlank()) {
                val merged = DictationTranscriptMerger.appendFinalTurn(dictationCommittedPrefixState, transcript)
                dictationCommittedPrefixState = merged
                inputTextState = merged
              }
              voiceController.resetState()
              isDictationPausedState = true
            }
          }
          is VoiceState.Error -> {
            isDictationPausedState = true
            if (isLive) {
              if (!st.isRecoverableSilence) statusBannerState = st.message
            } else if (isDictationSessionActiveState) {
              dictationErrorState = st.message
            }
          }
          else -> Unit
        }
      }
    }
  }

  private fun submitQuery(rawPrompt: String) {
    val prompt = rawPrompt.trim()
    if (prompt.isEmpty()) return
    inputTextState = ""
    statusBannerState = null
    queryJob?.cancel()
    queryJob = lifecycleScope.launch {
      when (val planned = agentTaskPlanner.planAndRoute(prompt)) {
        is PlannedAgentOutcome.ConfirmPhoneAction -> {
          pendingPhoneActionState = planned.action
          assistantResponseState = planned.action.summary
          return@launch
        }
        is PlannedAgentOutcome.ConfirmSocialAction -> {
          pendingSocialActionState = planned.action
          assistantResponseState = planned.action.summary
          return@launch
        }
        is PlannedAgentOutcome.TimerRequested -> {
          val res = timerManager.startTimer(planned.durationSeconds, planned.label)
          assistantResponseState = res.second
          return@launch
        }
        is PlannedAgentOutcome.NotificationControlHandled -> {
          assistantResponseState = planned.message
          return@launch
        }
        is PlannedAgentOutcome.AccessibilityHandled -> {
          val acc = planned.result
          if (acc.requiresConfirmation) {
            pendingAccessibilityActionState = acc
            assistantResponseState = acc.pendingActionSummary.ifBlank { acc.detail }
          } else {
            assistantResponseState = "${acc.statusLabel}: ${acc.detail}"
          }
          return@launch
        }
        is PlannedAgentOutcome.AgentTaskExecuted -> {
          assistantResponseState = planned.report.summaryText
          return@launch
        }
        is PlannedAgentOutcome.LaunchCommand -> {
          if (planned.intent is AgentCommandIntent.OpenTargetOrSetting) {
            val res = deviceActionExecutor.executeNaturalCommand(planned.intent.command)
            assistantResponseState = "${res.statusLabel}: ${res.verifiedDetail}"
            return@launch
          }
        }
        else -> Unit
      }

      isGeneratingState = true
      responsePhaseState = AiResponseState.Thinking()
      assistantResponseState = ""
      val settings = settingsRepository.getSettingsSnapshot()
      val pendingPref =
        preferenceConfirmationManager.inspectUserStatement(
          rawInput = prompt,
          memoryEnabled = settings.privacyPerformanceConfig.memoryEnabled,
        )
      val req =
        OrchestrationRequest(
          conversationId = "assist_activity_session",
          userPrompt = prompt,
          historyMessages = emptyList(),
          requestedModelId = VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID,
        )
      aiOrchestrator.processMessage(req).collectLatest { event ->
        when (event) {
          is OrchestrationEvent.StateChanged -> {
            responsePhaseState = event.state
            when (val s = event.state) {
              is AiResponseState.Streaming -> assistantResponseState = s.partialText
              is AiResponseState.Completed -> {
                isGeneratingState = false
                assistantResponseState =
                  if (pendingPref != null && pendingPref.isExplicitSaveRequest) {
                    preferenceConfirmationManager.confirmAndSave(
                      pendingPref,
                      settings.privacyPerformanceConfig.encryptSensitiveMemory,
                    )
                    "${s.fullText}\n\nSaved to My Memory: \"${pendingPref.extractedContent}\""
                  } else {
                    s.fullText
                  }
              }
              is AiResponseState.Error -> {
                isGeneratingState = false
                statusBannerState = s.message
              }
              else -> Unit
            }
          }
          is OrchestrationEvent.BlocksUpdated -> {
            val text = event.blocks.filterIsInstance<ResponseBlock.MarkdownText>().joinToString("\n") { it.content }
            if (text.isNotBlank()) assistantResponseState = text
          }
          else -> Unit
        }
      }
    }
  }

  private fun selectPhoneCandidate(candidate: ResolvedContact) {
    val cur = pendingPhoneActionState ?: return
    pendingPhoneActionState =
      if (cur.type == PendingPhoneActionType.DIAL_PHONE) {
        phoneActionController.prepareDialNumber(candidate.phoneNumber, candidate.displayName)
      } else {
        phoneActionController.prepareSms(candidate.phoneNumber, cur.messageBody, candidate.displayName)
      }
  }

  private fun selectSocialCandidate(candidate: ResolvedContact) {
    val cur = pendingSocialActionState ?: return
    pendingSocialActionState =
      socialIntegrationManager.prepareWhatsAppMessage(
        phoneOrName = candidate.phoneNumber,
        messageText = cur.captionOrMessage,
        resolvedDisplayLabel = "${candidate.displayName} (${candidate.phoneNumber})",
      )
  }

  private fun confirmPhoneAction(action: PendingPhoneAction) {
    pendingPhoneActionState = null
    lifecycleScope.launch {
      val res = phoneActionController.executeConfirmedAction(action)
      statusBannerState = "${res.statusLabel}: ${res.verifiedDetail}"
    }
  }

  private fun confirmSocialAction(action: PendingSocialAction) {
    pendingSocialActionState = null
    lifecycleScope.launch {
      val res = socialIntegrationManager.executeConfirmedSocialAction(action)
      statusBannerState = "${res.statusLabel}: ${res.verifiedDetail}"
    }
  }

  private fun confirmAccessibilityAction(action: AccessibilityActionResult) {
    pendingAccessibilityActionState = null
    val target = action.targetLabelToConfirm
    if (target.isBlank()) return
    val res = accessibilityAgentController.performNodeClick(targetText = target, confirmedSensitiveAction = true)
    assistantResponseState = "${res.statusLabel}: ${res.detail}"
  }

  private fun openMainApp() {
    cleanupVoiceAndQueries()
    startActivity(
      Intent(this, MainActivity::class.java).apply {
        action = Intent.ACTION_ASSIST
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      }
    )
    finishQuietly()
  }

  private fun dismissFallbackActivity() {
    cleanupVoiceAndQueries()
    sessionStateManager.dismissAssistantSessionIfOwner(
      ownerToken = ownerToken,
      keepScreenShare = screenShareSessionManager.status.value != ScreenShareStatus.STOPPED,
    )
    finishQuietly()
  }

  private fun cleanupVoiceAndQueries() {
    queryJob?.cancel()
    queryJob = null
    isGeneratingState = false
    usingLiveSocketState = false
    isDictationSessionActiveState = false
    isDictationPausedState = false
    geminiLiveVoiceEngine.stopAndClose(keepScreenShare = screenShareSessionManager.status.value != ScreenShareStatus.STOPPED)
    liveVoiceSessionManager.onEnded(keepScreenShare = screenShareSessionManager.status.value != ScreenShareStatus.STOPPED)
    voiceController.stopListening()
    voiceController.stopSpeaking()
  }

  private fun finishQuietly() {
    finish()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
      @Suppress("DEPRECATION")
      overridePendingTransition(0, 0)
    }
  }
}
