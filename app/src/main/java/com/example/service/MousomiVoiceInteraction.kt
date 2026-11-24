package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Rect as AndroidRect
import android.graphics.Region
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.core.accessibility.AccessibilityActionResult
import com.example.core.accessibility.AccessibilityAgentController
import com.example.core.agent.AgentTaskPlanner
import com.example.core.agent.PlannedAgentOutcome
import com.example.core.alarms.TimerManager
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.AssistantSurfaceState
import com.example.core.assistant.AssistantVisualState
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.device.AgentCommandIntent
import com.example.core.device.AssistantAgentCommandRouter
import com.example.core.device.ContactResolver
import com.example.core.device.DeviceActionExecutor
import com.example.core.device.PendingPhoneAction
import com.example.core.device.PendingPhoneActionType
import com.example.core.device.PendingSocialAction
import com.example.core.device.PhoneActionController
import com.example.core.device.ResolvedContact
import com.example.core.device.SocialIntegrationManager
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.overlay.FloatingAttachmentItem
import com.example.core.screen.AttachedVisualItem
import com.example.core.screen.ImageAttachmentManager
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.screen.ScreenshotCaptureManager
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionGeometryResult
import com.example.core.screen.SelectionToolMode
import com.example.core.screen.SmartCropEngine
import com.example.core.screen.VisualQueryProcessor
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.DictationTranscriptMerger
import com.example.core.voice.GeminiLiveVoiceEngine
import com.example.core.voice.LiveVoiceSessionManager
import com.example.core.voice.LiveVoiceSessionStatus
import com.example.core.voice.SpeechRecognizerDiagnostics
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightIntensity
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import com.example.domain.model.OrbQuickAction
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.SettingsRepository
import com.example.ui.components.AssistantSessionOverlayUi
import com.example.ui.model.AttachmentPreview
import com.example.ui.overlay.OverlayMediaPickerActivity
import com.example.ui.overlay.ScreenCaptureConsentActivity
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoiceInteractionEntryPoint {
  fun assistantSessionStateManager(): AssistantSessionStateManager
  fun settingsRepository(): SettingsRepository
  fun geminiLiveVoiceEngine(): GeminiLiveVoiceEngine
  fun liveVoiceSessionManager(): LiveVoiceSessionManager
  fun voiceController(): VoiceController
  fun aiOrchestrator(): AiOrchestrator
  fun screenShareSessionManager(): ScreenShareSessionManager
  fun screenRecordingManager(): ScreenRecordingManager
  fun selectionGeometryEngine(): SelectionGeometryEngine
  fun smartCropEngine(): SmartCropEngine
  fun visualQueryProcessor(): VisualQueryProcessor
  fun screenshotCaptureManager(): ScreenshotCaptureManager
  fun imageAttachmentManager(): ImageAttachmentManager
  fun tempMediaCacheManager(): TempMediaCacheManager
  fun assistantAgentCommandRouter(): AssistantAgentCommandRouter
  fun deviceActionExecutor(): DeviceActionExecutor
  fun phoneActionController(): PhoneActionController
  fun socialIntegrationManager(): SocialIntegrationManager
  fun agentTaskPlanner(): AgentTaskPlanner
  fun timerManager(): TimerManager
  fun contactResolver(): ContactResolver
  fun accessibilityAgentController(): AccessibilityAgentController
}

/**
 * Official Android Default Assistant Service ([VoiceInteractionService]).
 * Registered via [android.service.voice.VoiceInteractionService] & [res/xml/voice_interaction_service.xml].
 */
class MousomiVoiceInteractionService : VoiceInteractionService() {
  override fun onReady() {
    super.onReady()
  }

  override fun onShutdown() {
    super.onShutdown()
  }
}

/**
 * Official Android [VoiceInteractionSessionService] creating full two-state Compose [MousomiAssistantSession].
 */
class MousomiVoiceInteractionSessionService : VoiceInteractionSessionService() {
  override fun onNewSession(args: Bundle?): VoiceInteractionSession =
    MousomiAssistantSession(this)
}

/**
 * Android [RecognitionService] paired with [MousomiVoiceInteractionService] via [res/xml/voice_interaction_service.xml].
 * Required by AOSP VoiceInteractionServiceInfo validation so Android 12 / Samsung One UI binds Mousomi AI
 * directly on bottom-corner assistant swipes instead of falling back to MainActivity.
 */
class MousomiRecognitionService : RecognitionService() {
  private val mainHandler = Handler(Looper.getMainLooper())
  private var delegateRecognizer: SpeechRecognizer? = null

  override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
    val hasMic =
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasMic) {
      runCatching { listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
      return
    }
    mainHandler.post {
      val externalComponent = SpeechRecognizerDiagnostics.resolveExternalRecognitionComponent(this)
      val canOnDevice =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
      if (externalComponent == null && !canOnDevice) {
        runCatching { listener.error(SpeechRecognizer.ERROR_CLIENT) }
        return@post
      }
      delegateRecognizer?.cancel()
      delegateRecognizer?.destroy()
      delegateRecognizer = null

      val recognizer =
        when {
          externalComponent != null -> SpeechRecognizer.createSpeechRecognizer(this, externalComponent)
          canOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
          else -> null
        }
      if (recognizer == null) {
        runCatching { listener.error(SpeechRecognizer.ERROR_CLIENT) }
        return@post
      }
      delegateRecognizer = recognizer
      recognizer.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            runCatching { listener.readyForSpeech(params ?: Bundle.EMPTY) }
          }

          override fun onBeginningOfSpeech() {
            runCatching { listener.beginningOfSpeech() }
          }

          override fun onRmsChanged(rmsdB: Float) {
            runCatching { listener.rmsChanged(rmsdB) }
          }

          override fun onBufferReceived(buffer: ByteArray?) {
            if (buffer != null) runCatching { listener.bufferReceived(buffer) }
          }

          override fun onEndOfSpeech() {
            runCatching { listener.endOfSpeech() }
          }

          override fun onError(error: Int) {
            delegateRecognizer?.destroy()
            delegateRecognizer = null
            runCatching { listener.error(error) }
          }

          override fun onResults(results: Bundle?) {
            delegateRecognizer?.destroy()
            delegateRecognizer = null
            runCatching { listener.results(results ?: Bundle.EMPTY) }
          }

          override fun onPartialResults(partialResults: Bundle?) {
            runCatching { listener.partialResults(partialResults ?: Bundle.EMPTY) }
          }

          override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
      )
      val forwardIntent = Intent(recognizerIntent).apply {
        if (action.isNullOrBlank()) action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
      }
      recognizer.startListening(forwardIntent)
    }
  }

  override fun onCancel(listener: Callback) {
    mainHandler.post {
      delegateRecognizer?.cancel()
      delegateRecognizer?.destroy()
      delegateRecognizer = null
    }
  }

  override fun onStopListening(listener: Callback) {
    mainHandler.post {
      delegateRecognizer?.stopListening()
    }
  }

  override fun onDestroy() {
    mainHandler.post {
      delegateRecognizer?.cancel()
      delegateRecognizer?.destroy()
      delegateRecognizer = null
    }
    super.onDestroy()
  }
}

/**
 * Real System-Level Android Default Assistant Session:
 * - Renders directly inside [VoiceInteractionSession.onCreateContentView] (no SYSTEM_ALERT_WINDOW needed).
 * - Implements Reference Image 1 ([AssistantVisualState.STANDARD_ASSISTANT]) & Reference Image 2
 *   ([AssistantVisualState.LIVE_ASSISTANT]) with IME adjustResize, real Gemini Live Voice / STT+TTS fallback,
 *   Screen Share consent handoff, and confirmation-gated Phone/Social actions.
 */
class MousomiAssistantSession(
  context: Context,
) : VoiceInteractionSession(context), LifecycleOwner, SavedStateRegistryOwner {

  private val entryPoint: VoiceInteractionEntryPoint by lazy {
    EntryPointAccessors.fromApplication(context.applicationContext, VoiceInteractionEntryPoint::class.java)
  }

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val savedStateRegistryController = SavedStateRegistryController.create(this)
  private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var queryJob: Job? = null
  private var voiceCollectorJob: Job? = null
  private var rootContainer: FrameLayout? = null
  private var hasShownOnce = false

  // Session UI state
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
  private var isDrawingModeState by mutableStateOf(false)
  private var drawingToolModeState by mutableStateOf(SelectionToolMode.SMART_LOOP)
  private var drawingStrokePointsState by mutableStateOf<List<NormPoint>>(emptyList())
  private var snappedDrawingResultState by mutableStateOf<SelectionGeometryResult?>(null)
  private var confirmedSelectionBoxState by mutableStateOf<NormBoundingBox?>(null)
  private var pendingAttachmentsState by mutableStateOf<List<FloatingAttachmentItem>>(emptyList())
  private var isOrbDraggingState by mutableStateOf(false)
  private val orbTouchableRect = AndroidRect()
  private val orbCloseTouchableRect = AndroidRect()

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateRegistryController.savedStateRegistry

  override fun onCreate() {
    super.onCreate()
    savedStateRegistryController.performRestore(null)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
  }

  override fun onCreateContentView(): View {
    configureSessionWindow()

    val ep = entryPoint
    val sessionState = ep.assistantSessionStateManager()
    val settingsRepo = ep.settingsRepository()
    val liveEngine = ep.geminiLiveVoiceEngine()
    val liveVoiceMgr = ep.liveVoiceSessionManager()
    val voiceCtrl = ep.voiceController()
    val screenShareMgr = ep.screenShareSessionManager()

    val container = FrameLayout(context).apply {
      setBackgroundColor(AndroidColor.TRANSPARENT)
      setViewTreeLifecycleOwner(this@MousomiAssistantSession)
      setViewTreeSavedStateRegistryOwner(this@MousomiAssistantSession)
    }

    val composeView = ComposeView(context).apply {
      setBackgroundColor(AndroidColor.TRANSPARENT)
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setViewTreeLifecycleOwner(this@MousomiAssistantSession)
      setViewTreeSavedStateRegistryOwner(this@MousomiAssistantSession)
      setContent {
        val surfaceState by sessionState.surfaceState.collectAsState()
        val orbXFraction by sessionState.orbXFraction.collectAsState()
        val orbYFraction by sessionState.orbYFraction.collectAsState()
        val visualState by sessionState.visualState.collectAsState()
        val sessionPhase by sessionState.currentPhase.collectAsState()
        val edgeSignal by sessionState.edgeActivation.collectAsState()
        val reactiveFrame by liveVoiceMgr.orbFrame.collectAsState()
        val livePaused by liveVoiceMgr.isPaused.collectAsState()
        val liveMuted by liveVoiceMgr.isMuted.collectAsState()
        val voiceState by voiceCtrl.voiceState.collectAsState()
        val voiceRms by voiceCtrl.audioRmsNormalized.collectAsState()
        val screenShareStatus by screenShareMgr.status.collectAsState()
        val screenRecordingState by ep.screenRecordingManager().state.collectAsState()
        val liveAssistantTranscript by liveEngine.assistantTranscript.collectAsState()
        val liveUserTranscript by liveEngine.userTranscript.collectAsState()

        LaunchedEffect(surfaceState, isOrbDraggingState) {
          syncWindowForSurfaceState(surfaceState)
          requestInsetsRefresh()
        }

        LaunchedEffect(liveAssistantTranscript) {
          if (liveAssistantTranscript.isNotBlank()) {
            assistantResponseState = liveAssistantTranscript
          }
        }
        LaunchedEffect(liveUserTranscript) {
          if (liveUserTranscript.isNotBlank()) {
            userTranscriptState = liveUserTranscript
          }
        }

        val edgeConfig =
          EdgeLightConfig(
            enabled = true,
            assistantEdgeGlowEnabled = true,
            preset = EdgeLightPreset.GEMINI_FLOW,
            intensity = EdgeLightIntensity.BRIGHT,
            brightness = 0.96f,
            borderThicknessDp = 5.5f,
            cornerRadiusDp = 34f,
          )
        val edgeParams =
          EdgeLightStateMapper.resolve(
            config = edgeConfig,
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

        val effectiveSurfaceState =
          if (surfaceState == AssistantSurfaceState.CLOSED && hasShownOnce) {
            if (isDrawingModeState) AssistantSurfaceState.DRAWING else AssistantSurfaceState.EXPANDED
          } else {
            surfaceState
          }

        MousomiTheme(themeMode = AppThemeMode.DARK) {
          AssistantSessionOverlayUi(
            visualState =
              if (visualState == AssistantVisualState.HIDDEN) AssistantVisualState.STANDARD_ASSISTANT
              else visualState,
            surfaceState = effectiveSurfaceState,
            orbXFraction = orbXFraction,
            orbYFraction = orbYFraction,
            onOrbPositionChanged = { xf, yf -> sessionState.updateOrbPosition(xf, yf) },
            onCollapseToOrb = { collapseSessionToOrb() },
            onExpandFromOrb = { expandSessionFromOrb() },
            onOrbTouchableRegionsChanged = { orbRect, closeRect ->
              if (orbTouchableRect != orbRect || orbCloseTouchableRect != closeRect) {
                orbTouchableRect.set(orbRect)
                orbCloseTouchableRect.set(closeRect)
                requestInsetsRefresh()
              }
            },
            onOrbDragStateChanged = { dragging ->
              if (isOrbDraggingState != dragging) {
                isOrbDraggingState = dragging
                requestInsetsRefresh()
              }
            },
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
              stopSessionDictation()
              submitQueryFromSession(prompt)
            },
            onMicClick = { toggleMicOrDictation() },
            onStartDictation = { startOrResumeSessionDictation() },
            onPauseDictation = { pauseSessionDictation() },
            onStopDictation = { stopSessionDictation() },
            onRetryDictation = { startOrResumeSessionDictation() },
            onLiveButtonClick = {
              if (Settings.canDrawOverlays(context)) {
                launchFloatingLiveModeFromSession()
              } else if (visualState == AssistantVisualState.LIVE_ASSISTANT) {
                stopLiveToStandard()
              } else {
                startLiveAssistantMode()
              }
            },
            onDismissLiveToStandard = { stopLiveToStandard() },
            onShareScreenWithLive = { handleShareScreenWithLive() },
            onStopGeneration = {
              queryJob?.cancel()
              queryJob = null
              isGeneratingState = false
              responsePhaseState = AiResponseState.Cancelled
            },
            onOpenFullApp = { openMainAppFromSession(null) },
            onOpenScreenSelection = { enterInlineDrawingMode() },
            onOpenDrawingStudio = { enterInlineDrawingMode() },
            onOpenPhotoPicker = { launchPhotoPickerFromSession() },
            onCloseSession = { explicitCloseSession() },
            isGenerating = isGeneratingState,
            isVoiceListening = voiceState is VoiceState.Listening ||
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
            isDrawingMode = isDrawingModeState,
            drawingToolMode = drawingToolModeState,
            activeDrawingPoints = drawingStrokePointsState,
            snappedDrawingResult = snappedDrawingResultState,
            confirmedSelectionBox = confirmedSelectionBoxState,
            selectionGeometryEngine = ep.selectionGeometryEngine(),
            onToggleDrawingMode = {
              if (isDrawingModeState) {
                cancelInlineDrawing()
              } else {
                enterInlineDrawingMode()
              }
            },
            onSelectDrawingTool = { mode ->
              drawingToolModeState = mode
              snappedDrawingResultState = null
              drawingStrokePointsState = emptyList()
            },
            onDrawingStrokeStart = { pt ->
              drawingStrokePointsState = listOf(pt)
              snappedDrawingResultState = null
            },
            onDrawingStrokeMove = { pt ->
              drawingStrokePointsState = (drawingStrokePointsState + pt).takeLast(240)
            },
            onDrawingStrokeEnd = {
              if (drawingStrokePointsState.size >= 2) {
                snappedDrawingResultState =
                  ep.selectionGeometryEngine().processStroke(drawingStrokePointsState, drawingToolModeState)
              }
              drawingStrokePointsState = emptyList()
            },
            onDrawingHandleDrag = { handle, dx, dy ->
              snappedDrawingResultState?.let { cur ->
                snappedDrawingResultState =
                  ep.selectionGeometryEngine().adjustSnappedResult(cur, handle, dx, dy)
              }
            },
            onConfirmDrawingSelection = { confirmInlineDrawing() },
            onRedrawDrawingSelection = { redrawInlineDrawing() },
            onCancelDrawingSelection = { cancelInlineDrawing() },
            pendingAttachments = pendingAttachmentsState,
            onRemoveAttachment = { uriString -> removeSessionAttachment(uriString) },
            onReplaceAttachment = { enterInlineDrawingMode() },
            screenRecordingState = screenRecordingState,
            onConfirmScreenRecording = {
              context.startActivity(ScreenCaptureConsentActivity.buildScreenRecordingIntent(context, fromOverlay = false))
            },
            onStopScreenRecording = {
              ContextCompat.startForegroundService(context, MediaProjectionCaptureService.buildStopRecordingIntent(context))
            },
            onCancelScreenRecording = {
              ContextCompat.startForegroundService(context, MediaProjectionCaptureService.buildCancelRecordingIntent(context))
            },
            onDismissScreenRecordingCard = {
              ep.screenRecordingManager().resetToIdle()
            },
          )
        }
      }
    }

    container.addView(
      composeView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      ),
    )
    rootContainer = container
    return container
  }

  override fun onPrepareShow(args: Bundle?, showFlags: Int) {
    super.onPrepareShow(args, showFlags)
    configureSessionWindow()
  }

  override fun onShow(args: Bundle?, showFlags: Int) {
    super.onShow(args, showFlags)
    configureSessionWindow()
    if (!hasShownOnce) {
      hasShownOnce = true
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    } else if (lifecycleRegistry.currentState == Lifecycle.State.CREATED) {
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }
    if (lifecycleRegistry.currentState != Lifecycle.State.RESUMED) {
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    inputTextState = ""
    dictationCommittedPrefixState = ""
    isDictationSessionActiveState = false
    isDictationPausedState = false
    dictationErrorState = null
    statusBannerState = null
    pendingPhoneActionState = null
    pendingSocialActionState = null
    pendingAccessibilityActionState = null
    entryPoint.assistantSessionStateManager().openStandardAssistant(EdgeActivationEdge.BOTTOM_CORNER)
    startVoiceCollector()

    // If invoked with explicit Live quick action, transition straight into Live State
    val requestedLive =
      args?.getBoolean("start_live", false) == true ||
        args?.getString("action") == "live"
    if (requestedLive) {
      startLiveAssistantMode()
    }
  }

  override fun onHide() {
    super.onHide()
    isDrawingModeState = false
    drawingStrokePointsState = emptyList()
    snappedDrawingResultState = null
    isOrbDraggingState = false
    cleanupVoiceAndQueries()
    entryPoint.assistantSessionStateManager().dismissAssistantSession(
      keepScreenShare = entryPoint.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }
  }

  override fun onCloseSystemDialogs() {
    isDrawingModeState = false
    drawingStrokePointsState = emptyList()
    snappedDrawingResultState = null
    isOrbDraggingState = false
    cleanupVoiceAndQueries()
    entryPoint.assistantSessionStateManager().dismissAssistantSession(
      keepScreenShare = entryPoint.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    super.onCloseSystemDialogs()
  }

  override fun onBackPressed() {
    val sessionState = entryPoint.assistantSessionStateManager()
    if (isDrawingModeState || sessionState.surfaceState.value == AssistantSurfaceState.DRAWING) {
      cancelInlineDrawing()
      return
    }
    if (sessionState.surfaceState.value == AssistantSurfaceState.EXPANDED) {
      if (sessionState.visualState.value == AssistantVisualState.LIVE_ASSISTANT) {
        stopLiveToStandard()
        return
      }
      collapseSessionToOrb()
      return
    }
    cleanupVoiceAndQueries()
    sessionState.dismissAssistantSession(
      keepScreenShare = entryPoint.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    super.onBackPressed()
  }

  override fun onDestroy() {
    isDrawingModeState = false
    isOrbDraggingState = false
    cleanupVoiceAndQueries()
    entryPoint.assistantSessionStateManager().dismissAssistantSession(keepScreenShare = false)
    val state = lifecycleRegistry.currentState
    if (state != Lifecycle.State.INITIALIZED && state != Lifecycle.State.DESTROYED) {
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
    rootContainer = null
    sessionScope.cancel()
    super.onDestroy()
  }

  override fun onComputeInsets(outInsets: Insets) {
    super.onComputeInsets(outInsets)
    val surface = entryPoint.assistantSessionStateManager().surfaceState.value
    if (surface == AssistantSurfaceState.COLLAPSED_ORB && !isOrbDraggingState) {
      val activeOrbRect =
        if (!orbTouchableRect.isEmpty) {
          orbTouchableRect
        } else {
          val dm = context.resources.displayMetrics
          val density = dm.density
          val orbBoxPx = (96f * density).toInt()
          val right = (dm.widthPixels - (12f * density).toInt()).coerceAtLeast(orbBoxPx)
          val bottom = (dm.heightPixels * 0.78f).toInt().coerceAtLeast(orbBoxPx)
          AndroidRect(right - orbBoxPx, bottom - orbBoxPx, right, bottom)
        }
      outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
      outInsets.touchableRegion.set(activeOrbRect)
      if (!orbCloseTouchableRect.isEmpty) {
        outInsets.touchableRegion.op(orbCloseTouchableRect, Region.Op.UNION)
      }
    } else {
      outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
    }
  }

  private fun configureSessionWindow() {
    val win = window?.window ?: return
    win.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
    syncWindowForSurfaceState(entryPoint.assistantSessionStateManager().surfaceState.value)
    win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val lp = win.attributes
      lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      win.attributes = lp
    }
    WindowCompat.setDecorFitsSystemWindows(win, false)
  }

  private fun syncWindowForSurfaceState(surfaceState: AssistantSurfaceState) {
    val win = window?.window ?: return
    when (surfaceState) {
      AssistantSurfaceState.COLLAPSED_ORB -> {
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.setDimAmount(0f)
        if (!isOrbDraggingState) {
          win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
          win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
      }
      AssistantSurfaceState.DRAWING -> {
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.setDimAmount(0f)
        win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
      }
      AssistantSurfaceState.EXPANDED,
      AssistantSurfaceState.CLOSED -> {
        win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.setDimAmount(0.22f)
        win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
      }
    }
  }

  private fun requestInsetsRefresh() {
    val root = rootContainer ?: return
    root.post {
      window?.window?.decorView?.requestLayout()
      root.requestLayout()
    }
  }

  private fun collapseSessionToOrb() {
    val sessionState = entryPoint.assistantSessionStateManager()
    if (isDrawingModeState || sessionState.surfaceState.value == AssistantSurfaceState.DRAWING) {
      return
    }
    sessionState.collapseToOrb()
    syncWindowForSurfaceState(AssistantSurfaceState.COLLAPSED_ORB)
    requestInsetsRefresh()
  }

  private fun expandSessionFromOrb() {
    val sessionState = entryPoint.assistantSessionStateManager()
    sessionState.expandFromOrb()
    syncWindowForSurfaceState(AssistantSurfaceState.EXPANDED)
    requestInsetsRefresh()
  }

  private fun explicitCloseSession() {
    val sessionState = entryPoint.assistantSessionStateManager()
    if (isDrawingModeState || sessionState.surfaceState.value == AssistantSurfaceState.DRAWING) {
      return
    }
    cleanupVoiceAndQueries()
    sessionState.closeAssistant(
      keepScreenShare = entryPoint.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    hide()
  }

  private fun hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  private fun startLiveAssistantMode() {
    if (!hasMicPermission()) {
      statusBannerState = "Microphone permission is required for Live Voice. Open Mousomi AI to grant mic access."
      return
    }
    statusBannerState = null
    val ep = entryPoint
    ep.assistantSessionStateManager().enterLiveAssistant(EdgeActivationEdge.BOTTOM)
    sessionScope.launch {
      val settings = ep.settingsRepository().getSettingsSnapshot()
      ep.voiceController().playActivationChime(settings.voicePreferences)
      val engine = ep.geminiLiveVoiceEngine()
      if (engine.isFirebaseAvailable()) {
        usingLiveSocketState = true
        engine.startSession(
          preferences = settings.voicePreferences,
          allowInterruptions = settings.privacyPerformanceConfig.allowVoiceInterruption,
          onFallbackRequired = { reason ->
            sessionScope.launch {
              usingLiveSocketState = false
              statusBannerState = "Switched to continuous speech fallback: $reason"
              ep.voiceController().interruptSpeakingAndListen(settings.voicePreferences.preferredLanguage)
            }
          },
        )
      } else {
        usingLiveSocketState = false
        ep.liveVoiceSessionManager().onBeginSession(
          settings.voicePreferences,
          settings.privacyPerformanceConfig.allowVoiceInterruption,
        )
        ep.voiceController().interruptSpeakingAndListen(settings.voicePreferences.preferredLanguage)
      }
    }
  }

  private fun stopLiveToStandard() {
    val ep = entryPoint
    usingLiveSocketState = false
    ep.geminiLiveVoiceEngine().stopAndClose(
      keepScreenShare = ep.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    ep.liveVoiceSessionManager().onEnded(
      keepScreenShare = ep.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    ep.voiceController().stopListening()
    ep.voiceController().stopSpeaking()
    ep.assistantSessionStateManager().dismissLiveToStandard()
  }

  private fun toggleMicOrDictation() {
    val ep = entryPoint
    if (!hasMicPermission()) {
      dictationErrorState = "Microphone permission is required for voice input."
      isDictationSessionActiveState = true
      isDictationPausedState = true
      return
    }
    if (ep.assistantSessionStateManager().visualState.value == AssistantVisualState.LIVE_ASSISTANT) {
      val nextMuted = !ep.liveVoiceSessionManager().isMuted.value
      if (usingLiveSocketState) {
        ep.geminiLiveVoiceEngine().setMuted(nextMuted)
      } else {
        ep.liveVoiceSessionManager().setMuted(nextMuted)
        if (nextMuted) ep.voiceController().stopListening()
        else startFallbackDictation()
      }
      return
    }
    if (ep.voiceController().voiceState.value is VoiceState.Listening && !isDictationPausedState) {
      pauseSessionDictation()
    } else {
      startOrResumeSessionDictation()
    }
  }

  private fun startOrResumeSessionDictation() {
    if (!hasMicPermission()) {
      dictationErrorState = "Microphone permission is required for voice input."
      isDictationSessionActiveState = true
      isDictationPausedState = true
      return
    }
    dictationCommittedPrefixState = inputTextState
    isDictationSessionActiveState = true
    isDictationPausedState = false
    dictationErrorState = null
    statusBannerState = null
    startFallbackDictation()
  }

  private fun pauseSessionDictation() {
    entryPoint.voiceController().stopListening()
    dictationCommittedPrefixState = inputTextState
    isDictationSessionActiveState = true
    isDictationPausedState = true
  }

  private fun stopSessionDictation() {
    entryPoint.voiceController().stopListening()
    isDictationSessionActiveState = false
    isDictationPausedState = false
    dictationErrorState = null
    dictationCommittedPrefixState = inputTextState
  }

  private fun startFallbackDictation() {
    val ep = entryPoint
    sessionScope.launch {
      val settings = ep.settingsRepository().getSettingsSnapshot()
      ep.voiceController().playActivationChime(settings.voicePreferences)
      ep.voiceController().interruptSpeakingAndListen(settings.voicePreferences.preferredLanguage)
    }
  }

  private fun startVoiceCollector() {
    voiceCollectorJob?.cancel()
    val ep = entryPoint
    voiceCollectorJob = sessionScope.launch {
      ep.voiceController().voiceState.collectLatest { st ->
        val isLive =
          ep.assistantSessionStateManager().visualState.value == AssistantVisualState.LIVE_ASSISTANT
        when (st) {
          is VoiceState.Listening -> {
            dictationErrorState = null
            if (isLive) {
              if (st.partialTranscript.isNotBlank()) {
                userTranscriptState = st.partialTranscript
              }
            } else if (isDictationSessionActiveState && !isDictationPausedState && st.partialTranscript.isNotBlank()) {
              inputTextState =
                DictationTranscriptMerger.mergePartial(dictationCommittedPrefixState, st.partialTranscript)
            }
          }
          is VoiceState.TranscriptReady -> {
            val transcript = st.transcript.trim()
            dictationErrorState = null
            if (isLive) {
              if (transcript.isNotBlank()) {
                userTranscriptState = transcript
                submitQueryFromSession(transcript)
              }
            } else {
              if (transcript.isNotBlank()) {
                val merged =
                  DictationTranscriptMerger.appendFinalTurn(dictationCommittedPrefixState, transcript)
                dictationCommittedPrefixState = merged
                inputTextState = merged
              }
              ep.voiceController().resetState()
              isDictationPausedState = true
            }
          }
          is VoiceState.Error -> {
            isDictationPausedState = true
            if (isLive) {
              if (!st.isRecoverableSilence) {
                statusBannerState = st.message
              }
            } else if (isDictationSessionActiveState) {
              dictationErrorState = st.message
            }
          }
          else -> Unit
        }
      }
    }
  }

  private fun submitQueryFromSession(
    rawPrompt: String,
    forceIncludeScreenFrame: Boolean = false,
  ) {
    val prompt = rawPrompt.trim()
    val currentPending = pendingAttachmentsState
    if (prompt.isEmpty() && currentPending.isEmpty()) return
    inputTextState = ""
    statusBannerState = null

    val ep = entryPoint
    queryJob?.cancel()
    queryJob = sessionScope.launch {
      // Check modular AgentTaskPlanner when there are no staged image attachments (or when social media attaches cropped visual)
      if (prompt.isNotEmpty() && currentPending.isEmpty()) {
        when (val planned = ep.agentTaskPlanner().planAndRoute(prompt)) {
          is PlannedAgentOutcome.ConfirmPhoneAction -> {
            pendingPhoneActionState = planned.action
            assistantResponseState =
              if (planned.action.isContactAmbiguous) {
                planned.action.summary
              } else {
                "Review and confirm before opening ${planned.action.type.title}."
              }
            return@launch
          }
          is PlannedAgentOutcome.ConfirmSocialAction -> {
            pendingSocialActionState = planned.action
            assistantResponseState =
              if (planned.action.isContactAmbiguous) {
                planned.action.summary
              } else {
                "Review and confirm before opening ${planned.action.platform.displayName}."
              }
            return@launch
          }
          is PlannedAgentOutcome.TimerRequested -> {
            val res = ep.timerManager().startTimer(planned.durationSeconds, planned.label)
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
          is PlannedAgentOutcome.LaunchCommand -> {
            when (val agentCmd = planned.intent) {
              is AgentCommandIntent.StartScreenRecording -> {
                ep.screenRecordingManager().requestConfirmation()
                assistantResponseState = "Please confirm below to grant Android screen-recording permission."
                return@launch
              }
              AgentCommandIntent.StopScreenRecording -> {
                ContextCompat.startForegroundService(context, MediaProjectionCaptureService.buildStopRecordingIntent(context))
                assistantResponseState = "Stopping and finalizing screen recording…"
                return@launch
              }
              AgentCommandIntent.CancelScreenRecording -> {
                ContextCompat.startForegroundService(context, MediaProjectionCaptureService.buildCancelRecordingIntent(context))
                assistantResponseState = "Cancelling screen recording…"
                return@launch
              }
              AgentCommandIntent.StartScreenShare -> {
                handleShareScreenWithLive()
                return@launch
              }
              AgentCommandIntent.StopScreenShare -> {
                if (ep.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED) {
                  ContextCompat.startForegroundService(context, MediaProjectionCaptureService.buildStopIntent(context))
                  ep.screenShareSessionManager().stopSession("Screen sharing stopped by command.")
                }
                assistantResponseState = "Screen sharing is now off."
                return@launch
              }
              AgentCommandIntent.StartScreenSelection -> {
                enterInlineDrawingMode()
                return@launch
              }
              is AgentCommandIntent.AnalyzeCurrentScreen -> {
                val shareStatus = ep.screenShareSessionManager().status.value
                if (shareStatus == ScreenShareStatus.STOPPED || shareStatus == ScreenShareStatus.REQUESTING_CONSENT) {
                  statusBannerState = "Screen sharing is off. Tap 'Share screen with Live' so Mousomi can view the screen."
                  return@launch
                }
              }
              is AgentCommandIntent.UnsupportedSilentToggle -> {
                agentCmd.fallbackSettingsQuery?.let { ep.deviceActionExecutor().executeNaturalCommand(it) }
                assistantResponseState = agentCmd.explanation
                return@launch
              }
              is AgentCommandIntent.OpenTargetOrSetting -> {
                val res = ep.deviceActionExecutor().executeNaturalCommand(agentCmd.command)
                assistantResponseState = "${res.statusLabel}: ${res.verifiedDetail}"
                return@launch
              }
            }
          }
          PlannedAgentOutcome.ForwardToMultimodalAi -> Unit
        }
      }

      pendingAttachmentsState = emptyList()
      isGeneratingState = true
      responsePhaseState = AiResponseState.Thinking()
      assistantResponseState = ""

      val settings = ep.settingsRepository().getSettingsSnapshot()
      val attachmentPreviews = currentPending.map { AttachmentPreview.PickedPhotoUri(it.uriString) }
      val preparedQuery =
        if (attachmentPreviews.isNotEmpty()) {
          ep.visualQueryProcessor().prepareQuery(prompt, attachmentPreviews)
        } else {
          null
        }
      val payloads = preparedQuery?.payloads?.toMutableList() ?: mutableListOf()
      val shareStatus = ep.screenShareSessionManager().status.value
      val shouldAttachScreen =
        payloads.isEmpty() &&
          (forceIncludeScreenFrame ||
            (shareStatus != ScreenShareStatus.STOPPED && shareStatus != ScreenShareStatus.REQUESTING_CONSENT))
      if (shouldAttachScreen) {
        val screenPayload =
          ep.visualQueryProcessor().prepareScreenSharePayload(
            screenShareSessionManager = ep.screenShareSessionManager(),
            screenshotCaptureManager = ep.screenshotCaptureManager(),
            smartCropEngine = ep.smartCropEngine(),
            cropBox = confirmedSelectionBoxState,
          )
        if (screenPayload != null) {
          payloads.add(screenPayload.payload)
        }
      }
      val effectivePrompt = preparedQuery?.modelPrompt ?: prompt
      val req =
        OrchestrationRequest(
          conversationId = "assistant_session",
          userPrompt = effectivePrompt,
          historyMessages = emptyList(),
          currentTurnImages = payloads,
          requestedModelId = VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID,
        )
      ep.aiOrchestrator().processMessage(req).collectLatest { event ->
        when (event) {
          is OrchestrationEvent.StateChanged -> {
            responsePhaseState = event.state
            when (val s = event.state) {
              is AiResponseState.Streaming -> assistantResponseState = s.partialText
              is AiResponseState.Completed -> {
                isGeneratingState = false
                assistantResponseState = s.fullText
                if (ep.assistantSessionStateManager().visualState.value == AssistantVisualState.LIVE_ASSISTANT &&
                  !usingLiveSocketState
                ) {
                  ep.voiceController().speakResponse(
                    messageId = "assist_${System.currentTimeMillis()}",
                    text = s.fullText,
                    preferences = settings.voicePreferences,
                  )
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
        entryPoint.phoneActionController().prepareDialNumber(candidate.phoneNumber, candidate.displayName)
      } else {
        entryPoint.phoneActionController().prepareSms(candidate.phoneNumber, cur.messageBody, candidate.displayName)
      }
  }

  private fun selectSocialCandidate(candidate: ResolvedContact) {
    val cur = pendingSocialActionState ?: return
    pendingSocialActionState =
      entryPoint.socialIntegrationManager().prepareWhatsAppMessage(
        phoneOrName = candidate.phoneNumber,
        messageText = cur.captionOrMessage,
        resolvedDisplayLabel = "${candidate.displayName} (${candidate.phoneNumber})",
      )
  }

  private fun confirmPhoneAction(action: PendingPhoneAction) {
    pendingPhoneActionState = null
    sessionScope.launch {
      val res = entryPoint.phoneActionController().executeConfirmedAction(action)
      statusBannerState = "${res.statusLabel}: ${res.verifiedDetail}"
    }
  }

  private fun confirmSocialAction(action: PendingSocialAction) {
    pendingSocialActionState = null
    sessionScope.launch {
      val res = entryPoint.socialIntegrationManager().executeConfirmedSocialAction(action)
      statusBannerState = "${res.statusLabel}: ${res.verifiedDetail}"
    }
  }

  private fun confirmAccessibilityAction(action: AccessibilityActionResult) {
    pendingAccessibilityActionState = null
    val target = action.targetLabelToConfirm
    if (target.isBlank()) return
    val res = entryPoint.accessibilityAgentController().performNodeClick(targetText = target, confirmedSensitiveAction = true)
    assistantResponseState = "${res.statusLabel}: ${res.detail}"
  }

  private fun enterInlineDrawingMode() {
    statusBannerState = null
    drawingStrokePointsState = emptyList()
    snappedDrawingResultState = null
    isDrawingModeState = true
    entryPoint.assistantSessionStateManager().enterDrawingMode()
    syncWindowForSurfaceState(AssistantSurfaceState.DRAWING)
    requestInsetsRefresh()
  }

  private fun redrawInlineDrawing() {
    confirmedSelectionBoxState = null
    drawingStrokePointsState = emptyList()
    snappedDrawingResultState = null
    isDrawingModeState = true
    entryPoint.assistantSessionStateManager().enterDrawingMode()
  }

  private fun confirmInlineDrawing() {
    val snapped = snappedDrawingResultState ?: return
    val ep = entryPoint
    val shareStatus = ep.screenShareSessionManager().status.value
    if (shareStatus == ScreenShareStatus.STOPPED || shareStatus == ScreenShareStatus.REQUESTING_CONSENT) {
      isDrawingModeState = false
      ep.assistantSessionStateManager().confirmDrawingMode()
      syncWindowForSurfaceState(AssistantSurfaceState.EXPANDED)
      snappedDrawingResultState = null
      drawingStrokePointsState = emptyList()
      confirmedSelectionBoxState = snapped.boundingBox
      statusBannerState =
        "Screen sharing is disabled, so external screen content cannot be captured. Tap 'Share screen with Live' to authorize capture."
      return
    }

    sessionScope.launch {
      val ovW = rootContainer?.width ?: 0
      val ovH = rootContainer?.height ?: 0
      rootContainer?.visibility = View.INVISIBLE
      delay(95L)
      val outcome =
        ep.screenshotCaptureManager().captureAuthorizedScreenFrame(
          overlayWidthPx = ovW,
          overlayHeightPx = ovH,
        )
      rootContainer?.visibility = View.VISIBLE

      when (outcome) {
        ScreenshotCaptureManager.CaptureOutcome.ConsentRequired -> {
          isDrawingModeState = false
          ep.assistantSessionStateManager().confirmDrawingMode()
          syncWindowForSurfaceState(AssistantSurfaceState.EXPANDED)
          snappedDrawingResultState = null
          drawingStrokePointsState = emptyList()
          confirmedSelectionBoxState = snapped.boundingBox
          statusBannerState = "Screen capture session expired — requesting renewed consent."
          context.startActivity(ScreenCaptureConsentActivity.buildScreenShareIntent(context, fromOverlay = false))
        }
        is ScreenshotCaptureManager.CaptureOutcome.BlockedOrFailed -> {
          statusBannerState = outcome.reason
        }
        is ScreenshotCaptureManager.CaptureOutcome.Success -> {
          val cropOutcome =
            ep.smartCropEngine().cropSelectionToFile(
              sourceFilePath = outcome.filePath,
              snappedResult = snapped,
              metadata = outcome.metadata,
              prefix = "sel_crop_",
            )
          cropOutcome.onSuccess { cropped ->
            val attached =
              ep.imageAttachmentManager().attachCroppedVisual(
                croppedFile = cropped.file,
                sourceFrameFilePath = outcome.filePath,
                captureMetadata = outcome.metadata,
                normCropRect = snapped.boundingBox,
                detectedShape = snapped.detectedShape,
                isSettling = false,
              )
            val floatingItem =
              FloatingAttachmentItem(
                id = attached.id,
                uriString = attached.croppedUriString,
                filePath = attached.croppedFilePath,
                label = if (cropped.hasTransparencyMask) "Masked region" else "Cropped area",
                isFromScreenSelection = true,
              )
            pendingAttachmentsState =
              (pendingAttachmentsState.filterNot { it.isFromScreenSelection } + floatingItem).takeLast(4)
            isDrawingModeState = false
            ep.assistantSessionStateManager().confirmDrawingMode()
            syncWindowForSurfaceState(AssistantSurfaceState.EXPANDED)
            snappedDrawingResultState = null
            drawingStrokePointsState = emptyList()
            confirmedSelectionBoxState = null
            statusBannerState = "Cropped region attached to Chat Bar. Ask your question and tap Send."
          }.onFailure { err ->
            statusBannerState = err.message ?: "Failed to crop selected screen region."
          }
        }
      }
    }
  }

  private fun removeSessionAttachment(uriString: String) {
    val target = pendingAttachmentsState.firstOrNull { it.uriString == uriString }
    pendingAttachmentsState = pendingAttachmentsState.filterNot { it.uriString == uriString }
    entryPoint.imageAttachmentManager().removeByUri(uriString)
    target?.filePath?.let { path ->
      val file = File(path)
      if (file.name.startsWith("sel_") || file.name.startsWith("annotated_")) {
        entryPoint.tempMediaCacheManager().cleanupFile(file)
      }
    }
  }

  private fun cancelInlineDrawing() {
    isDrawingModeState = false
    entryPoint.assistantSessionStateManager().cancelDrawingMode()
    syncWindowForSurfaceState(entryPoint.assistantSessionStateManager().surfaceState.value)
    requestInsetsRefresh()
    snappedDrawingResultState = null
    drawingStrokePointsState = emptyList()
    confirmedSelectionBoxState = null
  }

  private fun launchFloatingLiveModeFromSession() {
    cleanupVoiceAndQueries()
    entryPoint.assistantSessionStateManager().dismissAssistantSession(
      keepScreenShare = entryPoint.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    if (hasMicPermission()) {
      runCatching {
        ContextCompat.startForegroundService(context, LiveVoiceSessionService.buildStartIntent(context))
      }
    }
    runCatching {
      ContextCompat.startForegroundService(
        context,
        MousomiOverlayService.buildStartFloatingLiveIntent(context),
      )
    }
    hide()
  }

  private fun handleShareScreenWithLive() {
    val shareMgr = entryPoint.screenShareSessionManager()
    if (shareMgr.status.value != ScreenShareStatus.STOPPED) {
      runCatching { ContextCompat.startForegroundService(context, MediaProjectionCaptureService.buildStopIntent(context)) }
      shareMgr.stopSession("Screen sharing stopped.")
      statusBannerState = "Screen sharing turned off."
      return
    }
    statusBannerState = "Requesting Android screen-sharing consent…"
    context.startActivity(ScreenCaptureConsentActivity.buildScreenShareIntent(context, fromOverlay = false))
  }

  private fun launchPhotoPickerFromSession() {
    if (Settings.canDrawOverlays(context)) {
      context.startActivity(OverlayMediaPickerActivity.buildIntent(context))
      finish()
    } else {
      openMainAppFromSession(null)
    }
  }

  private fun openMainAppFromSession(quickAction: OrbQuickAction?) {
    cleanupVoiceAndQueries()
    val launch =
      Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_ASSIST
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (quickAction != null) {
          putExtra(MainActivity.EXTRA_QUICK_ACTION, quickAction.name)
        }
      }
    context.startActivity(launch)
    finish()
  }

  private fun cleanupVoiceAndQueries() {
    queryJob?.cancel()
    queryJob = null
    voiceCollectorJob?.cancel()
    voiceCollectorJob = null
    isGeneratingState = false
    usingLiveSocketState = false
    isDictationSessionActiveState = false
    isDictationPausedState = false
    dictationErrorState = null
    val ep = entryPoint
    ep.geminiLiveVoiceEngine().stopAndClose(
      keepScreenShare = ep.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    ep.liveVoiceSessionManager().onEnded(
      keepScreenShare = ep.screenShareSessionManager().status.value != ScreenShareStatus.STOPPED
    )
    ep.voiceController().stopListening()
    ep.voiceController().stopSpeaking()
  }
}
