package com.example.service

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.display.DisplayGeometry
import com.example.core.display.DisplayGeometryManager
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.overlay.FloatingAssistantSessionStore
import com.example.core.overlay.FloatingControlBarGeometry
import com.example.core.overlay.OrbPositionHelper
import com.example.core.permissions.EmergencyStopController
import com.example.core.screen.MediaProjectionManager as AppMediaProjectionManager
import com.example.core.screen.ScreenSelectionManager
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.screen.ScreenshotCaptureManager
import com.example.core.screen.SelectionStage
import com.example.core.screen.VisualQueryProcessor
import com.example.core.voice.AudioSmokeAnalyzer
import com.example.core.voice.GeminiLiveVoiceEngine
import com.example.core.voice.LiveVoiceSessionStatus
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.DomainMessage
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.OrbPopupAction
import com.example.domain.model.OrbQuickAction
import com.example.domain.model.OrbVisualMode
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.SettingsRepository
import com.example.ui.components.EdgeLightPerimeterOverlay
import com.example.ui.components.FloatingChatPanelCard
import com.example.ui.components.FloatingControlBar
import com.example.ui.components.FloatingOrbitPopup
import com.example.ui.components.FloatingProfileQuickMenu
import com.example.ui.components.OrbitDismissScrim
import com.example.ui.components.OrbitalSmokeOrb
import com.example.ui.model.AttachmentPreview
import com.example.ui.overlay.OverlayMediaPickerActivity
import com.example.ui.overlay.ScreenCaptureConsentActivity
import com.example.ui.theme.MousomiTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MediaProjectionCaptureService : Service() {

  @Inject lateinit var sessionManager: ScreenShareSessionManager
  @Inject lateinit var settingsRepository: SettingsRepository
  @Inject lateinit var appMediaProjectionManager: AppMediaProjectionManager
  @Inject lateinit var screenshotCaptureManager: ScreenshotCaptureManager
  @Inject lateinit var screenSelectionManager: ScreenSelectionManager

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var captureJob: Job? = null
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var isSelectionModeSession: Boolean = false
  private val handler = Handler(Looper.getMainLooper())

  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        stopCaptureAndSelf("System or user ended screen sharing.")
      }

      override fun onCapturedContentResize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
          recreateSurfaceForSize(width.coerceAtMost(1440), height.coerceAtMost(2880))
        }
      }
    }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopCaptureAndSelf("Screen sharing stopped from notification.")
      return START_NOT_STICKY
    }

    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
      ?: Activity.RESULT_CANCELED
    val data: Intent? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent?.getParcelableExtra(EXTRA_DATA)
      }
    isSelectionModeSession = intent?.getBooleanExtra(EXTRA_FOR_SCREEN_SELECTION, false) == true

    if (resultCode != Activity.RESULT_OK || data == null) {
      if (isSelectionModeSession) {
        screenSelectionManager.cancelSelection()
      }
      sessionManager.stopSession("Screen capture consent was not granted.")
      stopSelf()
      return START_NOT_STICKY
    }

    startAsForeground()
    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    val projection = mgr?.getMediaProjection(resultCode, data)
    if (projection == null) {
      if (isSelectionModeSession) {
        screenSelectionManager.reportError("Unable to initialize MediaProjection session.")
      }
      sessionManager.stopSession("Unable to initialize MediaProjection session.")
      stopSelf()
      return START_NOT_STICKY
    }

    mediaProjection = projection
    projection.registerCallback(projectionCallback, handler)
    appMediaProjectionManager.attachActiveProjection(projection, alreadyHasCallback = true)
    if (isSelectionModeSession) {
      launchScreenSelectionWorkflow()
    } else {
      startCaptureLoop()
    }
    return START_NOT_STICKY
  }

  private fun startAsForeground() {
    ensureChannel()
    val stopIntent =
      Intent(this, MediaProjectionCaptureService::class.java).apply { action = ACTION_STOP }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        101,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Mousomi Screen Intelligence Active")
        .setContentText("Periodic frame capture is active. Tap Stop to end anytime.")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .addAction(0, "Stop Sharing", stopPendingIntent)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun startCaptureLoop() {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val (width, height) =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = wm.maximumWindowMetrics.bounds
        Pair((b.width() / 2).coerceIn(360, 1080), (b.height() / 2).coerceIn(640, 1920))
      } else {
        Pair(720, 1280)
      }

    recreateSurfaceForSize(width, height)
    sessionManager.markSessionActive()

    captureJob?.cancel()
    captureJob =
      serviceScope.launch {
        while (isActive) {
          val cfg = settingsRepository.getSettingsSnapshot().screenIntelligenceConfig
          sessionManager.updateConfig(cfg)
          captureLatestFrame()
          delay((cfg.captureIntervalSeconds.coerceIn(2, 30)) * 1000L)
        }
      }
  }

  private fun recreateSurfaceForSize(width: Int, height: Int) {
    val projection = mediaProjection ?: return
    virtualDisplay?.release()
    imageReader?.close()

    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    imageReader = reader
    virtualDisplay =
      projection.createVirtualDisplay(
        "MousomiScreenCapture",
        width,
        height,
        resources.displayMetrics.densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        handler,
      )
  }

  private suspend fun captureLatestFrame() {
    val reader = imageReader ?: return
    val image = reader.acquireLatestImage() ?: return
    try {
      val plane = image.planes.firstOrNull() ?: return
      val buffer = plane.buffer
      val pixelStride = plane.pixelStride
      val rowStride = plane.rowStride
      val rowPadding = rowStride - pixelStride * image.width
      val rawBitmap =
        Bitmap.createBitmap(
          image.width + rowPadding / pixelStride,
          image.height,
          Bitmap.Config.ARGB_8888,
        )
      rawBitmap.copyPixelsFromBuffer(buffer)
      val cropped =
        if (rawBitmap.width > image.width) {
          Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height).also {
            rawBitmap.recycle()
          }
        } else {
          rawBitmap
        }
      withContext(Dispatchers.IO) {
        sessionManager.ingestCapturedBitmap(cropped)
        cropped.recycle()
      }
    } catch (t: Throwable) {
      sessionManager.markCaptureError("Screen frame capture skipped: ${t.message ?: "error"}")
    } finally {
      image.close()
    }
  }

  private fun launchScreenSelectionWorkflow() {
    val canSystemOverlay = Settings.canDrawOverlays(this)
    if (canSystemOverlay) {
      screenSelectionManager.onAuthorizedFrameReady(
        sourceFramePath = null,
        metadata = null,
        useSystemOverlay = true,
      )
      ContextCompat.startForegroundService(
        this,
        SelectionOverlayService.buildStartIntent(this),
      )
      return
    }

    serviceScope.launch {
      delay(220L)
      when (val cap = screenshotCaptureManager.captureAuthorizedScreenFrame()) {
        is ScreenshotCaptureManager.CaptureOutcome.Success -> {
          screenSelectionManager.onAuthorizedFrameReady(
            sourceFramePath = cap.filePath,
            metadata = cap.metadata,
            useSystemOverlay = false,
          )
        }
        is ScreenshotCaptureManager.CaptureOutcome.BlockedOrFailed -> {
          screenSelectionManager.reportError(cap.reason)
        }
        ScreenshotCaptureManager.CaptureOutcome.ConsentRequired -> {
          screenSelectionManager.reportError("Screen capture consent was revoked.")
        }
      }
    }
  }

  private fun stopCaptureAndSelf(reason: String) {
    captureJob?.cancel()
    virtualDisplay?.release()
    virtualDisplay = null
    imageReader?.close()
    imageReader = null
    mediaProjection?.unregisterCallback(projectionCallback)
    mediaProjection?.stop()
    mediaProjection = null
    appMediaProjectionManager.release(reason)
    if (isSelectionModeSession) {
      if (screenSelectionManager.state.value.stage != SelectionStage.ATTACHED) {
        screenSelectionManager.stopAndCleanup()
      }
    } else if (sessionManager.status.value != ScreenShareStatus.STOPPED) {
      sessionManager.stopSession(reason)
    }
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (mediaProjection == null) return
    if (isSelectionModeSession) {
      val (w, h, _) = appMediaProjectionManager.queryFullDisplayMetrics()
      appMediaProjectionManager.recreateSurface(w, h)
    } else if (captureJob?.isActive == true) {
      val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val (width, height) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          val b = wm.maximumWindowMetrics.bounds
          Pair((b.width() / 2).coerceIn(360, 1080), (b.height() / 2).coerceIn(640, 1920))
        } else {
          Pair(720, 1280)
        }
      recreateSurfaceForSize(width, height)
    }
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java)
      if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
        nm?.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID,
            "Screen Intelligence Capture",
            NotificationManager.IMPORTANCE_LOW,
          )
        )
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    captureJob?.cancel()
    virtualDisplay?.release()
    imageReader?.close()
    mediaProjection?.unregisterCallback(projectionCallback)
    mediaProjection?.stop()
    serviceScope.cancel()
  }

  companion object {
    const val CHANNEL_ID = "mousomi_screen_projection"
    const val NOTIFICATION_ID = 4401
    const val ACTION_START = "com.example.action.START_PROJECTION"
    const val ACTION_STOP = "com.example.action.STOP_PROJECTION"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_DATA = "extra_data"
    const val EXTRA_FOR_SCREEN_SELECTION = "extra_for_screen_selection"

    fun buildStartIntent(
      context: Context,
      resultCode: Int,
      data: Intent,
      forScreenSelection: Boolean = false,
    ): Intent =
      Intent(context, MediaProjectionCaptureService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_RESULT_CODE, resultCode)
        putExtra(EXTRA_DATA, data)
        putExtra(EXTRA_FOR_SCREEN_SELECTION, forScreenSelection)
      }

    fun buildStopIntent(context: Context): Intent =
      Intent(context, MediaProjectionCaptureService::class.java).apply {
        action = ACTION_STOP
      }
  }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
  private val lifecycleRegistry = LifecycleRegistry(this)
  private val savedStateController = SavedStateRegistryController.create(this)

  override val lifecycle: Lifecycle get() = lifecycleRegistry
  override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

  fun onCreate() {
    savedStateController.performRestore(null)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun onDestroy() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  }
}

private class BackAwareOverlayContainer(
  context: Context,
  private val onBackAction: () -> Boolean,
  private val onOutsideTouch: () -> Unit = {},
  private val onInsideTouchDown: () -> Unit = {},
) : FrameLayout(context) {

  private var backCallback: Any? = null

  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    when (ev.actionMasked) {
      MotionEvent.ACTION_OUTSIDE -> {
        onOutsideTouch()
        return false
      }
      MotionEvent.ACTION_DOWN -> {
        onInsideTouchDown()
      }
    }
    return super.dispatchTouchEvent(ev)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
      if (onBackAction()) return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val cb = OnBackInvokedCallback { onBackAction() }
      backCallback = cb
      runCatching {
        findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
          OnBackInvokedDispatcher.PRIORITY_OVERLAY,
          cb,
        )
      }
    }
  }

  override fun onDetachedFromWindow() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      (backCallback as? OnBackInvokedCallback)?.let { cb ->
        runCatching { findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(cb) }
      }
      backCallback = null
    }
    super.onDetachedFromWindow()
  }
}

private class DraggableOverlayFrameLayout(
  context: Context,
  private val onDragMove: (dxPx: Int, dyPx: Int) -> Unit,
  private val onDragEnd: () -> Unit,
) : FrameLayout(context) {

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
  private var downRawX = 0f
  private var downRawY = 0f
  private var lastRawX = 0f
  private var lastRawY = 0f
  private var isDragging = false

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downRawX = ev.rawX
        downRawY = ev.rawY
        lastRawX = ev.rawX
        lastRawY = ev.rawY
        isDragging = false
        return false
      }
      MotionEvent.ACTION_MOVE -> {
        val dist = hypot(ev.rawX - downRawX, ev.rawY - downRawY)
        if (dist > touchSlop) {
          isDragging = true
          return true
        }
        return false
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        isDragging = false
        return false
      }
    }
    return false
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_MOVE -> {
        if (isDragging) {
          val dx = (event.rawX - lastRawX).roundToInt()
          val dy = (event.rawY - lastRawY).roundToInt()
          if (dx != 0 || dy != 0) {
            lastRawX = event.rawX
            lastRawY = event.rawY
            onDragMove(dx, dy)
          }
          return true
        }
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        if (isDragging) {
          isDragging = false
          onDragEnd()
          return true
        }
      }
    }
    return super.onTouchEvent(event)
  }
}

@AndroidEntryPoint
class MousomiOverlayService : Service() {

  @Inject lateinit var settingsRepository: SettingsRepository
  @Inject lateinit var audioSmokeAnalyzer: AudioSmokeAnalyzer
  @Inject lateinit var emergencyStopController: EmergencyStopController
  @Inject lateinit var aiOrchestrator: AiOrchestrator
  @Inject lateinit var voiceController: VoiceController
  @Inject lateinit var geminiLiveVoiceEngine: GeminiLiveVoiceEngine
  @Inject lateinit var sessionStateManager: AssistantSessionStateManager
  @Inject lateinit var displayGeometryManager: DisplayGeometryManager
  @Inject lateinit var screenShareSessionManager: ScreenShareSessionManager
  @Inject lateinit var floatingSessionStore: FloatingAssistantSessionStore
  @Inject lateinit var visualQueryProcessor: VisualQueryProcessor

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var windowManager: WindowManager? = null
  private var edgeLightView: ComposeView? = null
  private var edgeLightLifecycle: OverlayLifecycleOwner? = null
  private var controlBarContainer: DraggableOverlayFrameLayout? = null
  private var controlBarParams: WindowManager.LayoutParams? = null
  private var controlBarLifecycle: OverlayLifecycleOwner? = null
  private var profileMenuView: ComposeView? = null
  private var profileMenuParams: WindowManager.LayoutParams? = null
  private var profileMenuLifecycle: OverlayLifecycleOwner? = null
  private var orbView: ComposeView? = null
  private var dismissTargetView: ComposeView? = null
  private var dismissTargetLifecycle: OverlayLifecycleOwner? = null
  private var popupView: ComposeView? = null
  private var assistantPanelContainer: BackAwareOverlayContainer? = null
  private var assistantPanelParams: WindowManager.LayoutParams? = null
  private var orbParams: WindowManager.LayoutParams? = null
  private var orbLifecycle: OverlayLifecycleOwner? = null
  private var popupLifecycle: OverlayLifecycleOwner? = null
  private var assistantPanelLifecycle: OverlayLifecycleOwner? = null
  private var orbSizePx: Int = 152

  private val isOverlayPaused = MutableStateFlow(false)
  private val isHoveringDismissTarget = MutableStateFlow(false)
  private val overlayResponseState = MutableStateFlow<AiResponseState>(AiResponseState.Idle)
  private val panelMaxHeightDpState = mutableIntStateOf(360)
  private val currentGeometryState = MutableStateFlow<DisplayGeometry?>(null)
  private val isScreenOn = MutableStateFlow(true)
  private val isSystemPowerSave = MutableStateFlow(false)
  private var overlayQueryJob: Job? = null
  private var overlayVoiceCollectorJob: Job? = null
  private var edgeRevealSettlerJob: Job? = null
  private var edgeStopFadeJob: Job? = null
  private var imeHidePendingJob: Job? = null
  private var overlayChannelCreated = false
  private var isPowerReceiverRegistered = false

  private val powerStateReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          Intent.ACTION_SCREEN_OFF -> isScreenOn.value = false
          Intent.ACTION_SCREEN_ON -> isScreenOn.value = true
          PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> refreshPowerState()
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    refreshPowerState()
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
      }
    ContextCompat.registerReceiver(
      this,
      powerStateReceiver,
      filter,
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    isPowerReceiverRegistered = true
  }

  private fun refreshPowerState() {
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    isScreenOn.value = pm.isInteractive
    isSystemPowerSave.value = pm.isPowerSaveMode
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
      endLiveAssistantSession(stopServiceCompletely = true)
      return START_NOT_STICKY
    }

    edgeStopFadeJob?.cancel()

    when (intent?.action) {
      ACTION_HIDE_FOR_SELECTION -> {
        detachControlBar()
        detachProfileQuickMenu()
        collapsePanelToBubble()
        dismissPopupWindow()
        startForegroundOverlayNotification()
        return START_STICKY
      }
      ACTION_PAUSE -> {
        isOverlayPaused.value = true
        sessionStateManager.onPausedChanged(true)
        geminiLiveVoiceEngine.pauseSession()
        voiceController.stopListening()
        dismissPopupWindow()
        detachProfileQuickMenu()
        collapsePanelToBubble()
        startForegroundOverlayNotification()
        return START_STICKY
      }
      ACTION_RESUME -> {
        isOverlayPaused.value = false
        sessionStateManager.onPausedChanged(false)
        startForegroundOverlayNotification()
        attachEdgeLightWindow()
        attachControlBar()
        maybeAttachSystemOrbFromSettings()
        return START_STICKY
      }
      ACTION_SHOW_CONTROL_BAR -> {
        isOverlayPaused.value = false
        sessionStateManager.onPausedChanged(false)
        floatingSessionStore.setControlBarVisible(true)
        startForegroundOverlayNotification()
        attachEdgeLightWindow()
        attachControlBar()
        maybeAttachSystemOrbFromSettings()
        return START_STICKY
      }
      ACTION_ACTIVATE_ASSISTANT -> {
        val edgeRaw = intent.getStringExtra(EXTRA_ACTIVATION_EDGE) ?: EdgeActivationEdge.BOTTOM_CORNER.name
        val edge = runCatching { EdgeActivationEdge.valueOf(edgeRaw) }.getOrDefault(EdgeActivationEdge.BOTTOM_CORNER)
        val expandPanel = intent.getBooleanExtra(EXTRA_EXPAND_PANEL, true)
        val autoVoice = intent.getBooleanExtra(EXTRA_PANEL_VOICE_AUTO, true)
        val accepted = sessionStateManager.activateFromTrigger(edge = edge, expandPanel = expandPanel)
        isOverlayPaused.value = false
        floatingSessionStore.setControlBarVisible(true)
        startForegroundOverlayNotification()
        attachEdgeLightWindow()
        attachControlBar()
        maybeAttachSystemOrbFromSettings()
        if (accepted) {
          scheduleRevealSettled()
          if (expandPanel) {
            showAssistantPanel(autoStartVoice = autoVoice)
          } else if (autoVoice) {
            startOverlayVoiceListening()
          }
        }
        return START_STICKY
      }
      ACTION_SHOW_ASSISTANT_PANEL -> {
        isOverlayPaused.value = false
        floatingSessionStore.setControlBarVisible(true)
        sessionStateManager.activateFromTrigger(edge = EdgeActivationEdge.BOTTOM, expandPanel = true)
        scheduleRevealSettled()
        startForegroundOverlayNotification()
        attachEdgeLightWindow()
        attachControlBar()
        maybeAttachSystemOrbFromSettings()
        val autoVoice = intent.getBooleanExtra(EXTRA_PANEL_VOICE_AUTO, false)
        showAssistantPanel(autoStartVoice = autoVoice)
        return START_STICKY
      }
    }

    floatingSessionStore.setControlBarVisible(true)
    startForegroundOverlayNotification()
    attachEdgeLightWindow()
    attachControlBar()
    maybeAttachSystemOrbFromSettings()
    return START_STICKY
  }

  private fun maybeAttachSystemOrbFromSettings() {
    scope.launch {
      val settings = settingsRepository.getSettingsSnapshot()
      if (settings.floatingOrbConfig.enabled) {
        attachSystemOrb()
      }
    }
  }

  private fun scheduleRevealSettled() {
    edgeRevealSettlerJob?.cancel()
    edgeRevealSettlerJob =
      scope.launch {
        delay(560L)
        sessionStateManager.onActivationAnimationSettled()
      }
  }

  private fun startForegroundOverlayNotification() {
    if (!overlayChannelCreated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java)
      if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
        nm?.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID,
            "Floating Assistant & Edge Light",
            NotificationManager.IMPORTANCE_MIN,
          )
        )
      }
      overlayChannelCreated = true
    }
    val paused = isOverlayPaused.value
    val stopIntent = Intent(this, MousomiOverlayService::class.java).apply { action = ACTION_STOP }
    val stopPending =
      PendingIntent.getService(
        this,
        201,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val askIntent =
      Intent(this, MousomiOverlayService::class.java).apply {
        action = ACTION_SHOW_ASSISTANT_PANEL
      }
    val askPending =
      PendingIntent.getService(
        this,
        202,
        askIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val toggleIntent =
      Intent(this, MousomiOverlayService::class.java).apply {
        action = if (paused) ACTION_RESUME else ACTION_PAUSE
      }
    val togglePending =
      PendingIntent.getService(
        this,
        203,
        toggleIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(if (paused) "Mousomi Overlay Paused" else "Mousomi Floating Assistant Ready")
        .setContentText(
          if (paused) "Tap Resume to re-enable floating assistant & edge glow."
          else "Use the floating bar to ask, select screen, or tap Stop anytime."
        )
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setContentIntent(askPending)
        .addAction(0, "Quick Ask", askPending)
        .addAction(0, if (paused) "Resume" else "Pause", togglePending)
        .addAction(0, "Stop", stopPending)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun overlayLayoutType(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }

  private fun attachEdgeLightWindow() {
    if (edgeLightView != null) return
    val wm = (windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
    windowManager = wm

    val geometry = displayGeometryManager.measure(this)
    currentGeometryState.value = geometry

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayLayoutType(),
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
      }

    val owner = OverlayLifecycleOwner().also { it.onCreate() }
    edgeLightLifecycle = owner

    val view =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          val settings by settingsRepository.observeSettings().collectAsState(initial = null)
          val voiceState by voiceController.voiceState.collectAsState()
          val voiceRms by voiceController.audioRmsNormalized.collectAsState()
          val panelResponse by overlayResponseState.collectAsState()
          val paused by isOverlayPaused.collectAsState()
          val sessionPhase by sessionStateManager.currentPhase.collectAsState()
          val activationSignal by sessionStateManager.edgeActivation.collectAsState()
          val screenShareStatus by screenShareSessionManager.status.collectAsState()
          val liveStatus by geminiLiveVoiceEngine.status.collectAsState()
          val latestGeo by currentGeometryState.collectAsState()
          val screenOn by isScreenOn.collectAsState()
          val sysPowerSave by isSystemPowerSave.collectAsState()
          val floatingState by floatingSessionStore.state.collectAsState()

          val effectivePhase =
            when {
              !screenOn -> AssistantSessionPhase.PAUSED
              paused -> AssistantSessionPhase.PAUSED
              liveStatus == LiveVoiceSessionStatus.Active &&
                sessionPhase == AssistantSessionPhase.INACTIVE -> AssistantSessionPhase.LISTENING
              floatingState.isPanelOpen &&
                sessionPhase == AssistantSessionPhase.INACTIVE -> AssistantSessionPhase.PANEL_EXPANDED
              else -> sessionPhase
            }

          val baseCfg = settings?.edgeLightConfig ?: EdgeLightConfig()
          val effectiveConfig =
            baseCfg.copy(
              enabled = screenOn && !paused && baseCfg.enabled,
              reducedMotion = baseCfg.reducedMotion || !screenOn,
              scheduleEnabled = false,
            )
          val edgeParams =
            EdgeLightStateMapper.resolve(
              config = effectiveConfig,
              responseState = panelResponse,
              voiceState = voiceState,
              voiceRmsNormalized = voiceRms,
              screenShareStatus = screenShareStatus,
              notificationAlertActive = false,
              isSystemPowerSave = sysPowerSave,
              currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
              forcePreview = false,
              assistantSessionPhase = effectivePhase,
              edgeActivationSignal = activationSignal,
              isOverlayHost = true,
            )
          val radii4 =
            latestGeo?.let {
              floatArrayOf(
                it.cornerTopLeftPx,
                it.cornerTopRightPx,
                it.cornerBottomRightPx,
                it.cornerBottomLeftPx,
              )
            }
          EdgeLightPerimeterOverlay(params = edgeParams, perCornerRadiiPx = radii4)
        }
      }

    runCatching {
      wm.addView(view, params)
      edgeLightView = view
    }
  }

  private fun detachEdgeLightWindow() {
    edgeLightView?.let { v -> runCatching { windowManager?.removeView(v) } }
    edgeLightView = null
    edgeLightLifecycle?.onDestroy()
    edgeLightLifecycle = null
  }

  private fun attachControlBar() {
    if (controlBarContainer != null) {
      floatingSessionStore.setControlBarVisible(true)
      return
    }
    val wm = (windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
    windowManager = wm

    val geometry = displayGeometryManager.measure(this)
    currentGeometryState.value = geometry
    val estBarW = (288f * geometry.density).roundToInt().coerceAtMost(geometry.usableWidthPx)
    val estBarH = (56f * geometry.density).roundToInt()
    val overlayState = floatingSessionStore.state.value
    val (initX, initY) =
      FloatingControlBarGeometry.coordinatesFromFraction(
        xFraction = overlayState.controlBarXFraction,
        yFraction = overlayState.controlBarYFraction,
        barWidthPx = estBarW,
        barHeightPx = estBarH,
        geometry = geometry,
      )

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayLayoutType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = initX
        y = initY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
      }
    controlBarParams = params

    val owner = OverlayLifecycleOwner().also { it.onCreate() }
    controlBarLifecycle = owner

    lateinit var containerRef: DraggableOverlayFrameLayout
    val container =
      DraggableOverlayFrameLayout(
        context = this,
        onDragMove = { dx, dy ->
          val geo = displayGeometryManager.measure(this@MousomiOverlayService)
          val w = containerRef.width.takeIf { it > 16 } ?: estBarW
          val h = containerRef.height.takeIf { it > 16 } ?: estBarH
          val (cx, cy) =
            FloatingControlBarGeometry.clampControlBarCoordinates(
              xPx = params.x + dx,
              yPx = params.y + dy,
              barWidthPx = w,
              barHeightPx = h,
              geometry = geo,
            )
          if (params.x != cx || params.y != cy) {
            params.x = cx
            params.y = cy
            runCatching { wm.updateViewLayout(containerRef, params) }
          }
        },
        onDragEnd = {
          val geo = displayGeometryManager.measure(this@MousomiOverlayService)
          val w = containerRef.width.takeIf { it > 16 } ?: estBarW
          val h = containerRef.height.takeIf { it > 16 } ?: estBarH
          val (xf, yf) =
            FloatingControlBarGeometry.fractionFromCoordinates(
              xPx = params.x,
              yPx = params.y,
              barWidthPx = w,
              barHeightPx = h,
              geometry = geo,
            )
          floatingSessionStore.updateControlBarFraction(xf, yf)
          repositionProfileMenuIfOpen(geo)
        },
      )
    containerRef = container

    val composeView =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          val overlayUi by floatingSessionStore.state.collectAsState()
          val paused by isOverlayPaused.collectAsState()
          val responseState by overlayResponseState.collectAsState()
          val voiceState by voiceController.voiceState.collectAsState()
          val liveStatus by geminiLiveVoiceEngine.status.collectAsState()
          val isBusyOrListening =
            responseState is AiResponseState.Loading ||
              responseState is AiResponseState.Thinking ||
              responseState is AiResponseState.Streaming ||
              voiceState is VoiceState.Listening ||
              liveStatus == LiveVoiceSessionStatus.Active

          MousomiTheme {
            FloatingControlBar(
              isPanelOpen = overlayUi.isPanelOpen,
              isProfileMenuOpen = overlayUi.isProfileQuickMenuOpen,
              isPaused = paused,
              isBusyOrListening = isBusyOrListening,
              onExpandClick = {
                if (floatingSessionStore.state.value.isPanelOpen) {
                  collapsePanelToBubble()
                  floatingSessionStore.minimizePanel()
                } else {
                  floatingSessionStore.setProfileQuickMenuOpen(false)
                  detachProfileQuickMenu()
                  showAssistantPanel(autoStartVoice = false)
                }
              },
              onScreenSelectionClick = {
                launchScreenSelectionFromOverlay()
              },
              onProfileClick = {
                val nextOpen = !floatingSessionStore.state.value.isProfileQuickMenuOpen
                floatingSessionStore.setProfileQuickMenuOpen(nextOpen)
                if (nextOpen) {
                  attachProfileQuickMenu()
                } else {
                  detachProfileQuickMenu()
                }
              },
              onPauseToggleClick = {
                toggleOverlayPause()
              },
              onCloseClick = {
                endLiveAssistantSession(stopServiceCompletely = true)
              },
            )
          }
        }
      }

    container.setViewTreeLifecycleOwner(owner)
    container.setViewTreeSavedStateRegistryOwner(owner)
    container.addView(
      composeView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
      ),
    )

    container.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
      if (controlBarContainer === v && v.width > 16 && v.height > 16) {
        val geo = displayGeometryManager.measure(this@MousomiOverlayService)
        val (cx, cy) =
          FloatingControlBarGeometry.clampControlBarCoordinates(
            xPx = params.x,
            yPx = params.y,
            barWidthPx = v.width,
            barHeightPx = v.height,
            geometry = geo,
          )
        if (params.x != cx || params.y != cy) {
          params.x = cx
          params.y = cy
          runCatching { wm.updateViewLayout(v, params) }
        }
      }
    }

    runCatching {
      wm.addView(container, params)
      controlBarContainer = container
      floatingSessionStore.setControlBarVisible(true)
    }
  }

  private fun detachControlBar() {
    controlBarContainer?.let { v -> runCatching { windowManager?.removeView(v) } }
    controlBarContainer = null
    controlBarParams = null
    controlBarLifecycle?.onDestroy()
    controlBarLifecycle = null
    floatingSessionStore.setControlBarVisible(false)
  }

  private fun attachProfileQuickMenu() {
    if (profileMenuView != null) return
    val wm = (windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
    val geo = displayGeometryManager.measure(this)
    val barParams = controlBarParams
    val barHeight = controlBarContainer?.height?.takeIf { it > 16 } ?: (56 * geo.density).roundToInt()
    val widthPx = (360f * geo.density).roundToInt().coerceAtMost(geo.usableWidthPx)
    val menuY =
      if (barParams != null) {
        val belowBar = barParams.y + barHeight + (8f * geo.density).roundToInt()
        val maxMenuTop = (geo.screenHeightPx - geo.safeBottomWithoutImePx - (260f * geo.density).roundToInt()).coerceAtLeast(geo.safeTopPx)
        belowBar.coerceIn(geo.safeTopPx, maxMenuTop)
      } else {
        geo.safeTopPx + (72f * geo.density).roundToInt()
      }

    val params =
      WindowManager.LayoutParams(
        widthPx,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayLayoutType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = menuY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
      }
    profileMenuParams = params

    val owner = OverlayLifecycleOwner().also { it.onCreate() }
    profileMenuLifecycle = owner

    val view =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setOnTouchListener { _, event ->
          if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            floatingSessionStore.setProfileQuickMenuOpen(false)
            detachProfileQuickMenu()
          }
          false
        }
        setContent {
          val settings by settingsRepository.observeSettings().collectAsState(initial = null)
          val overlayUi by floatingSessionStore.state.collectAsState()
          MousomiTheme {
            FloatingProfileQuickMenu(
              activeModelLabel = overlayUi.activeModelLabel,
              edgeLightConfig = settings?.edgeLightConfig ?: EdgeLightConfig(),
              onUpdateEdgeLight = { nextCfg ->
                scope.launch {
                  val cur = settingsRepository.getSettingsSnapshot()
                  settingsRepository.saveSettings(cur.copy(edgeLightConfig = nextCfg))
                }
              },
              onOpenControlCenter = {
                floatingSessionStore.setProfileQuickMenuOpen(false)
                detachProfileQuickMenu()
                openMainAppRoute(MainActivity.ACTION_OPEN_CONTROL_CENTER)
              },
              onOpenSettings = {
                floatingSessionStore.setProfileQuickMenuOpen(false)
                detachProfileQuickMenu()
                openMainAppRoute(MainActivity.ACTION_OPEN_SETTINGS)
              },
              onClearHistory = {
                floatingSessionStore.clearConversationHistory()
                floatingSessionStore.setStatusBanner("Floating chat history cleared.")
              },
              onDismiss = {
                floatingSessionStore.setProfileQuickMenuOpen(false)
                detachProfileQuickMenu()
              },
            )
          }
        }
      }

    runCatching {
      wm.addView(view, params)
      profileMenuView = view
    }
  }

  private fun detachProfileQuickMenu() {
    profileMenuView?.let { v -> runCatching { windowManager?.removeView(v) } }
    profileMenuView = null
    profileMenuParams = null
    profileMenuLifecycle?.onDestroy()
    profileMenuLifecycle = null
  }

  private fun repositionProfileMenuIfOpen(geo: DisplayGeometry) {
    val menu = profileMenuView ?: return
    val params = profileMenuParams ?: return
    val wm = windowManager ?: return
    val barParams = controlBarParams ?: return
    val barH = controlBarContainer?.height?.takeIf { it > 16 } ?: (56 * geo.density).roundToInt()
    val maxMenuTop = (geo.screenHeightPx - geo.safeBottomWithoutImePx - (260f * geo.density).roundToInt()).coerceAtLeast(geo.safeTopPx)
    params.y = (barParams.y + barH + (8f * geo.density).roundToInt()).coerceIn(geo.safeTopPx, maxMenuTop)
    params.width = (360f * geo.density).roundToInt().coerceAtMost(geo.usableWidthPx)
    runCatching { wm.updateViewLayout(menu, params) }
  }

  private fun toggleOverlayPause() {
    val nextPaused = !isOverlayPaused.value
    isOverlayPaused.value = nextPaused
    sessionStateManager.onPausedChanged(nextPaused)
    if (nextPaused) {
      geminiLiveVoiceEngine.pauseSession()
      voiceController.stopListening()
      voiceController.stopSpeaking()
    } else {
      attachEdgeLightWindow()
      if (geminiLiveVoiceEngine.status.value == LiveVoiceSessionStatus.Paused) {
        geminiLiveVoiceEngine.resumeSession { startOverlayFallbackStt() }
      }
    }
    startForegroundOverlayNotification()
  }

  private fun launchScreenSelectionFromOverlay() {
    detachControlBar()
    detachProfileQuickMenu()
    collapsePanelToBubble()
    dismissPopupWindow()
    floatingSessionStore.setSelectionTriggeredFromOverlay(true)
    startActivity(ScreenCaptureConsentActivity.buildIntent(this, fromOverlay = true))
  }

  private fun openMainAppRoute(customAction: String? = null) {
    collapsePanelToBubble()
    floatingSessionStore.minimizePanel()
    val launch =
      Intent(this@MousomiOverlayService, MainActivity::class.java).apply {
        if (!customAction.isNullOrBlank()) {
          action = customAction
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      }
    startActivity(launch)
  }

  private fun attachSystemOrb() {
    if (orbView != null) {
      sessionStateManager.onBubbleShown(true)
      return
    }
    val wm = (windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
    windowManager = wm

    val geometry = displayGeometryManager.measure(this)
    currentGeometryState.value = geometry
    orbSizePx = (56 * geometry.density).roundToInt().coerceIn(96, 196)
    val initialX = (geometry.screenWidthPx - geometry.safeRightPx - orbSizePx - (12 * geometry.density).roundToInt())
    val initialY = (geometry.screenHeightPx * 0.56f).roundToInt()
    val (clampedX, clampedY) =
      DisplayGeometryManager.clampBubbleCoordinates(initialX, initialY, orbSizePx, geometry)

    val params =
      WindowManager.LayoutParams(
        orbSizePx,
        orbSizePx,
        overlayLayoutType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = clampedX
        y = clampedY
      }
    orbParams = params

    val owner = OverlayLifecycleOwner().also { it.onCreate() }
    orbLifecycle = owner

    val view =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        contentDescription = "Mousomi System Floating Assistant Bubble"
        setContent {
          val settings by settingsRepository.observeSettings().collectAsState(initial = null)
          val audioMetrics by audioSmokeAnalyzer.metrics.collectAsState()
          val paused by isOverlayPaused.collectAsState()
          val phase by sessionStateManager.currentPhase.collectAsState()
          val orbCfg = settings?.floatingOrbConfig
          MousomiTheme {
            OrbitalSmokeOrb(
              mode =
                when {
                  paused -> OrbVisualMode.IDLE
                  phase == AssistantSessionPhase.USER_SPEAKING ||
                    phase == AssistantSessionPhase.LISTENING -> OrbVisualMode.LISTENING
                  phase == AssistantSessionPhase.AI_RESPONDING -> OrbVisualMode.SPEAKING
                  phase == AssistantSessionPhase.PANEL_EXPANDED ||
                    phase == AssistantSessionPhase.KEYBOARD_VISIBLE -> OrbVisualMode.FOCUSED
                  else -> OrbVisualMode.IDLE
                },
              audioMetrics = audioMetrics,
              reducedMotion = paused || settings?.edgeLightConfig?.reducedMotion == true,
              orbSize = (orbCfg?.sizeDp ?: 56f).coerceIn(48f, 64f).dp,
              showCenterSparkle = true,
            )
          }
        }
      }

    var initialPxX = 0
    var initialPxY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f
    var isDragging = false
    var wasInsideDismiss = false

    view.setOnTouchListener { v, event ->
      val geo = displayGeometryManager.measure(this@MousomiOverlayService)
      currentGeometryState.value = geo
      val rawMaxX = (geo.screenWidthPx - orbSizePx).coerceAtLeast(1)
      val rawMaxY = (geo.screenHeightPx - orbSizePx).coerceAtLeast(1)
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialPxX = params.x
          initialPxY = params.y
          initialTouchX = event.rawX
          initialTouchY = event.rawY
          isDragging = false
          wasInsideDismiss = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = event.rawX - initialTouchX
          val dy = event.rawY - initialTouchY
          if (!isDragging && (abs(dx) > 10f || abs(dy) > 10f)) {
            isDragging = true
            showDismissTargetOverlay(geo)
          }
          if (isDragging) {
            val nextX = (initialPxX + dx.roundToInt()).coerceIn(geo.safeLeftPx, rawMaxX - geo.safeRightPx)
            val nextY =
              (initialPxY + dy.roundToInt()).coerceIn(
                geo.safeTopPx + 8,
                (geo.screenHeightPx - orbSizePx - 8).coerceAtLeast(geo.safeTopPx + 16),
              )
            params.x = nextX
            params.y = nextY
            runCatching { wm.updateViewLayout(view, params) }

            val insideDismiss = OrbPositionHelper.isInsideBottomDismissZone(nextX, nextY, orbSizePx, geo)
            if (insideDismiss != wasInsideDismiss) {
              wasInsideDismiss = insideDismiss
              isHoveringDismissTarget.value = insideDismiss
              if (insideDismiss) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
              }
            }
          }
          true
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          hideDismissTargetOverlay()
          if (!isDragging && event.action == MotionEvent.ACTION_UP) {
            if (assistantPanelContainer != null) {
              collapsePanelToBubble()
              floatingSessionStore.minimizePanel()
            } else {
              showAssistantPanel(autoStartVoice = false)
            }
          } else if (isDragging) {
            if (wasInsideDismiss && event.action == MotionEvent.ACTION_UP) {
              dismissBubbleCompletely()
            } else {
              val snappedX = OrbPositionHelper.computeSnappedXPixel(params.x, orbSizePx, geo, snapToEdge = true)
              val (_, safeY) = DisplayGeometryManager.clampBubbleCoordinates(snappedX, params.y, orbSizePx, geo)
              params.x = snappedX
              params.y = safeY
              runCatching { wm.updateViewLayout(view, params) }

              val xFrac = (snappedX.toFloat() / rawMaxX).coerceIn(0f, 1f)
              val yFrac = (safeY.toFloat() / rawMaxY).coerceIn(0.08f, 0.88f)
              scope.launch {
                val cur = settingsRepository.getSettingsSnapshot()
                settingsRepository.saveSettings(
                  cur.copy(
                    floatingOrbConfig =
                      cur.floatingOrbConfig.copy(
                        positionXFraction = xFrac,
                        positionYFraction = yFrac,
                      )
                  )
                )
              }
            }
          }
          isDragging = false
          wasInsideDismiss = false
          isHoveringDismissTarget.value = false
          true
        }
        else -> false
      }
    }

    runCatching {
      wm.addView(view, params)
      orbView = view
      sessionStateManager.onBubbleShown(true)
    }
  }

  private fun showDismissTargetOverlay(geometry: DisplayGeometry) {
    if (dismissTargetView != null) return
    val wm = windowManager ?: return
    val owner = OverlayLifecycleOwner().also { it.onCreate() }
    dismissTargetLifecycle = owner

    val targetSizePx = (84 * geometry.density).roundToInt()
    val spec = DisplayGeometryManager.computeDismissTargetSpec(geometry)
    val params =
      WindowManager.LayoutParams(
        targetSizePx,
        targetSizePx,
        overlayLayoutType(),
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (spec.centerX - targetSizePx / 2f).roundToInt()
        y = (spec.centerY - targetSizePx / 2f).roundToInt()
      }

    val view =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          val hovering by isHoveringDismissTarget.collectAsState()
          val scale by animateFloatAsState(if (hovering) 1.18f else 0.96f, label = "dismiss_target_scale")
          Box(
            modifier = Modifier.size(84.dp),
            contentAlignment = Alignment.Center,
          ) {
            Surface(
              shape = CircleShape,
              color =
                if (hovering) Color(0xFFDC2626).copy(alpha = 0.94f)
                else Color(0xFF0F172A).copy(alpha = 0.88f),
              border =
                BorderStroke(
                  width = if (hovering) 2.2.dp else 1.5.dp,
                  color = if (hovering) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.45f),
                ),
              shadowElevation = 12.dp,
              modifier = Modifier.size(60.dp).scale(scale),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Drop here to dismiss floating bubble",
                  tint = Color.White,
                  modifier = Modifier.size(28.dp),
                )
              }
            }
          }
        }
      }

    runCatching {
      wm.addView(view, params)
      dismissTargetView = view
    }
  }

  private fun hideDismissTargetOverlay() {
    dismissTargetView?.let { v -> runCatching { windowManager?.removeView(v) } }
    dismissTargetView = null
    dismissTargetLifecycle?.onDestroy()
    dismissTargetLifecycle = null
  }

  private fun dismissBubbleCompletely() {
    hideDismissTargetOverlay()
    collapsePanelToBubble()
    dismissPopupWindow()
    geminiLiveVoiceEngine.stopAndClose()
    voiceController.stopListening()
    voiceController.stopSpeaking()
    orbView?.let { v -> runCatching { windowManager?.removeView(v) } }
    orbView = null
    orbLifecycle?.onDestroy()
    orbLifecycle = null
    sessionStateManager.onBubbleDismissed(stopAssistantVoice = true)
    if (!sessionStateManager.isSessionActive.value && controlBarContainer == null) {
      scope.launch {
        val sysEnabled = settingsRepository.getSettingsSnapshot().edgeLightConfig.systemOverlayEnabled
        if (!sysEnabled) {
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        }
      }
    }
  }

  private fun endLiveAssistantSession(stopServiceCompletely: Boolean) {
    sessionStateManager.onBeginStopping()
    hideDismissTargetOverlay()
    overlayQueryJob?.cancel()
    overlayVoiceCollectorJob?.cancel()
    geminiLiveVoiceEngine.stopAndClose()
    voiceController.stopListening()
    voiceController.stopSpeaking()
    voiceController.resetState()
    if (screenShareSessionManager.status.value != ScreenShareStatus.STOPPED) {
      runCatching { startService(MediaProjectionCaptureService.buildStopIntent(this)) }
      screenShareSessionManager.stopSession("Ended via Live Assistant stop.")
    }
    dismissPopupWindow()
    detachProfileQuickMenu()
    collapsePanelToBubble()
    sessionStateManager.onEndLiveAssistant(keepScreenShare = false)
    if (stopServiceCompletely) {
      detachControlBar()
      floatingSessionStore.resetTransientOverlayVisibilityOnly()
      orbView?.let { v -> runCatching { windowManager?.removeView(v) } }
      orbView = null
      orbLifecycle?.onDestroy()
      orbLifecycle = null
      edgeStopFadeJob?.cancel()
      if (edgeLightView != null) {
        // Allow EdgeLightPerimeterOverlay's 360ms visibilityAlpha fade-out tween to finish smoothly
        edgeStopFadeJob =
          scope.launch {
            delay(380L)
            detachEdgeLightWindow()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
          }
      } else {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    } else {
      startForegroundOverlayNotification()
    }
  }

  private fun toggleSystemOrbitPopup() {
    if (popupView != null) {
      dismissPopupWindow()
      return
    }
    val wm = windowManager ?: return
    val owner = OverlayLifecycleOwner().also { it.onCreate() }
    popupLifecycle = owner

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayLayoutType(),
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.CENTER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
      }

    val view =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          MousomiTheme {
            OrbitDismissScrim(
              visible = true,
              onDismiss = { dismissPopupWindow() },
            ) {
              FloatingOrbitPopup(
                onSelectAction = { action ->
                  dismissPopupWindow()
                  when (action) {
                    OrbPopupAction.EMERGENCY_STOP -> {
                      emergencyStopController.triggerEmergencyStop("Emergency Stop from Floating Orbit.")
                      endLiveAssistantSession(stopServiceCompletely = true)
                    }
                    OrbPopupAction.QUICK_ASK,
                    OrbPopupAction.OPEN_CHAT -> showAssistantPanel(autoStartVoice = false)
                    OrbPopupAction.VOICE_ASSISTANT -> showAssistantPanel(autoStartVoice = true)
                    else -> {
                      val launch =
                        Intent(this@MousomiOverlayService, MainActivity::class.java).apply {
                          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                          putExtra(MainActivity.EXTRA_ORB_POPUP_ACTION, action.name)
                        }
                      startActivity(launch)
                    }
                  }
                },
                onDismiss = { dismissPopupWindow() },
              )
            }
          }
        }
      }

    runCatching {
      wm.addView(view, params)
      popupView = view
    }
  }

  private fun showAssistantPanel(autoStartVoice: Boolean) {
    if (assistantPanelContainer != null) {
      floatingSessionStore.setPanelState(open = true)
      sessionStateManager.onPanelExpandedChanged(true)
      assistantPanelParams?.let { params ->
        val wm = windowManager
        if (wm != null && (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
          params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
          runCatching { wm.updateViewLayout(assistantPanelContainer, params) }
        }
      }
      if (autoStartVoice) startOverlayVoiceListening()
      return
    }
    val wm = (windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
    windowManager = wm
    dismissPopupWindow()
    detachProfileQuickMenu()
    floatingSessionStore.setProfileQuickMenuOpen(false)
    if (edgeLightView == null && !isOverlayPaused.value) {
      attachEdgeLightWindow()
    }
    attachControlBar()

    val initialGeo = displayGeometryManager.measure(this)
    currentGeometryState.value = initialGeo
    val placement = DisplayGeometryManager.computePanelPlacement(initialGeo)
    panelMaxHeightDpState.intValue = placement.maxHeightDp

    val owner = OverlayLifecycleOwner().also { it.onCreate() }
    assistantPanelLifecycle = owner

    @Suppress("DEPRECATION")
    val params =
      WindowManager.LayoutParams(
        placement.widthPx,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayLayoutType(),
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = placement.bottomMarginPx
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
      }
    assistantPanelParams = params

    lateinit var containerRef: BackAwareOverlayContainer
    val container =
      BackAwareOverlayContainer(
        context = this,
        onBackAction = {
          if (sessionStateManager.isKeyboardVisible.value) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(containerRef.windowToken, 0)
            sessionStateManager.onKeyboardVisibilityChanged(false)
            updatePanelLayoutForInsets(containerRef, params, wm)
            true
          } else {
            collapsePanelToBubble()
            floatingSessionStore.minimizePanel()
            true
          }
        },
        onOutsideTouch = {
          val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
          imm?.hideSoftInputFromWindow(containerRef.windowToken, 0)
          sessionStateManager.onKeyboardVisibilityChanged(false)
          if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { wm.updateViewLayout(containerRef, params) }
          }
        },
        onInsideTouchDown = {
          if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            runCatching { wm.updateViewLayout(containerRef, params) }
          }
        },
      )
    containerRef = container

    val composeView =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          val overlayUi by floatingSessionStore.state.collectAsState()
          val responseState by overlayResponseState.collectAsState()
          val voiceState by voiceController.voiceState.collectAsState()
          val liveStatus by geminiLiveVoiceEngine.status.collectAsState()
          val userLiveTranscript by geminiLiveVoiceEngine.userTranscript.collectAsState()
          val aiLiveTranscript by geminiLiveVoiceEngine.assistantTranscript.collectAsState()
          val paused by isOverlayPaused.collectAsState()
          val maxCardDp by panelMaxHeightDpState

          LaunchedEffect(aiLiveTranscript, liveStatus) {
            if (aiLiveTranscript.isNotBlank() &&
              (liveStatus == LiveVoiceSessionStatus.Active || liveStatus == LiveVoiceSessionStatus.Paused)
            ) {
              floatingSessionStore.appendLiveTranscriptTurn(
                isUser = false,
                text = aiLiveTranscript,
              )
            }
          }
          LaunchedEffect(userLiveTranscript, liveStatus) {
            if (userLiveTranscript.isNotBlank() && liveStatus == LiveVoiceSessionStatus.Active) {
              floatingSessionStore.appendLiveTranscriptTurn(
                isUser = true,
                text = userLiveTranscript,
              )
            }
          }

          MousomiTheme {
            FloatingChatPanelCard(
              promptText = overlayUi.promptText,
              turns = overlayUi.turns,
              pendingAttachments = overlayUi.pendingAttachments,
              previewingAttachment = overlayUi.previewingAttachment,
              liveUserTranscript = userLiveTranscript,
              activeModelLabel = overlayUi.activeModelLabel,
              statusBanner = overlayUi.statusBanner,
              responseState = responseState,
              voiceState = voiceState,
              liveStatus = liveStatus,
              isPaused = paused,
              maxCardHeightDp = maxCardDp,
              onPromptChanged = { floatingSessionStore.updatePromptText(it) },
              onSendPrompt = { submitOverlayPrompt(it) },
              onAttachImage = {
                startActivity(OverlayMediaPickerActivity.buildIntent(this@MousomiOverlayService))
              },
              onStartScreenSelection = {
                launchScreenSelectionFromOverlay()
              },
              onRemoveAttachment = { uriStr ->
                floatingSessionStore.removePendingAttachment(uriStr)
              },
              onOpenAttachmentPreview = { item ->
                floatingSessionStore.openAttachmentPreview(item)
              },
              onToggleVoice = {
                if (liveStatus == LiveVoiceSessionStatus.Active || liveStatus == LiveVoiceSessionStatus.Connecting) {
                  geminiLiveVoiceEngine.pauseSession()
                } else if (liveStatus == LiveVoiceSessionStatus.Paused) {
                  isOverlayPaused.value = false
                  geminiLiveVoiceEngine.resumeSession { startOverlayFallbackStt() }
                } else if (voiceState is VoiceState.Listening) {
                  voiceController.stopListening()
                } else {
                  startOverlayVoiceListening()
                }
              },
              onTogglePause = { toggleOverlayPause() },
              onEndLive = { endLiveAssistantSession(stopServiceCompletely = false) },
              onClearHistory = { floatingSessionStore.clearConversationHistory() },
              onExpandApp = { openMainAppRoute() },
              onMinimizePanel = {
                collapsePanelToBubble()
                floatingSessionStore.minimizePanel()
              },
            )
          }
        }
      }

    container.setViewTreeLifecycleOwner(owner)
    container.setViewTreeSavedStateRegistryOwner(owner)
    container.addView(
      composeView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
      ),
    )

    ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
      updatePanelLayoutForInsets(v, params, wm)
      insets
    }
    container.viewTreeObserver.addOnGlobalLayoutListener {
      if (assistantPanelContainer === container) {
        updatePanelLayoutForInsets(container, params, wm)
      }
    }

    runCatching {
      wm.addView(container, params)
      assistantPanelContainer = container
      floatingSessionStore.setPanelState(open = true)
      sessionStateManager.onPanelExpandedChanged(true)
      if (autoStartVoice) {
        startOverlayVoiceListening()
      }
    }
  }

  private fun updatePanelLayoutForInsets(
    container: View,
    params: WindowManager.LayoutParams,
    wm: WindowManager,
  ) {
    val geo = displayGeometryManager.measure(this, container)
    currentGeometryState.value = geo
    val imeVisibleNow = geo.isImeVisible

    if (imeVisibleNow) {
      imeHidePendingJob?.cancel()
      sessionStateManager.onKeyboardVisibilityChanged(true)
    } else if (sessionStateManager.isKeyboardVisible.value) {
      imeHidePendingJob?.cancel()
      imeHidePendingJob =
        scope.launch {
          delay(90L)
          val refreshed = displayGeometryManager.measure(this@MousomiOverlayService, container)
          if (!refreshed.isImeVisible) {
            sessionStateManager.onKeyboardVisibilityChanged(false)
          }
        }
    }

    val visibleRect = Rect()
    container.getWindowVisibleDisplayFrame(visibleRect)
    val tolerancePx = (24f * geo.density).roundToInt()
    val imeTopY = geo.screenHeightPx - geo.imeBottomPx
    val alreadyShiftedByWm =
      imeVisibleNow && visibleRect.bottom in 100..(imeTopY + tolerancePx)

    val placement =
      DisplayGeometryManager.computePanelPlacement(
        geometry = geo,
        windowAlreadyShiftedByIme = alreadyShiftedByWm,
      )
    panelMaxHeightDpState.intValue = placement.maxHeightDp

    var targetY = placement.bottomMarginPx
    if (imeVisibleNow && container.height > 0) {
      val loc = IntArray(2)
      container.getLocationOnScreen(loc)
      val actualBottom = loc[1] + container.height
      val clearancePx = (10f * geo.density).roundToInt()
      val desiredMaxBottom = imeTopY - clearancePx
      if (actualBottom > desiredMaxBottom + (6f * geo.density).roundToInt()) {
        val shortfall = actualBottom - desiredMaxBottom
        targetY = (params.y + shortfall).coerceAtLeast(placement.bottomMarginPx)
      }
    }

    if (params.y != targetY || params.width != placement.widthPx) {
      params.y = targetY
      params.width = placement.widthPx
      runCatching { wm.updateViewLayout(container, params) }
    }
  }

  private fun startOverlayVoiceListening() {
    val hasMic =
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasMic) {
      floatingSessionStore.setStatusBanner(
        "Microphone permission is needed for voice input. Open Permissions in Mousomi AI."
      )
      return
    }
    scope.launch {
      val settings = settingsRepository.getSettingsSnapshot()
      if (geminiLiveVoiceEngine.isFirebaseAvailable()) {
        voiceController.playActivationChime(settings.voicePreferences)
        geminiLiveVoiceEngine.startSession(
          preferences = settings.voicePreferences,
          allowInterruptions = settings.privacyPerformanceConfig.allowVoiceInterruption,
          onFallbackRequired = { startOverlayFallbackStt() },
        )
      } else {
        voiceController.playActivationChime(settings.voicePreferences)
        startOverlayFallbackStt()
      }
    }
  }

  private fun startOverlayFallbackStt() {
    overlayVoiceCollectorJob?.cancel()
    overlayVoiceCollectorJob =
      scope.launch {
        voiceController.voiceState.collectLatest { vState ->
          if (vState is VoiceState.TranscriptReady) {
            val clean = vState.transcript.trim()
            voiceController.resetState()
            if (clean.isNotBlank()) {
              floatingSessionStore.updatePromptText(clean)
              submitOverlayPrompt(clean)
            }
          }
        }
      }
    scope.launch {
      val settings = settingsRepository.getSettingsSnapshot()
      voiceController.interruptSpeakingAndListen(settings.voicePreferences.preferredLanguage)
    }
  }

  private fun submitOverlayPrompt(rawPrompt: String) {
    val clean = rawPrompt.trim()
    val pendingAttachments = floatingSessionStore.state.value.pendingAttachments
    if (clean.isBlank() && pendingAttachments.isEmpty()) return

    if (pendingAttachments.isEmpty() && geminiLiveVoiceEngine.status.value == LiveVoiceSessionStatus.Active) {
      floatingSessionStore.appendLiveTranscriptTurn(isUser = true, text = clean)
      floatingSessionStore.updatePromptText("")
      geminiLiveVoiceEngine.sendFollowUpText(clean)
      return
    }

    val snapshotTurns = floatingSessionStore.state.value.turns
    val attachmentPreviews =
      pendingAttachments.map { AttachmentPreview.PickedPhotoUri(it.uriString) }
    val attachmentUris = pendingAttachments.map { it.uriString }

    overlayQueryJob?.cancel()
    overlayQueryJob =
      scope.launch {
        overlayResponseState.value = AiResponseState.Loading
        val prepared =
          if (attachmentPreviews.isNotEmpty()) {
            visualQueryProcessor.prepareQuery(clean, attachmentPreviews)
          } else {
            VisualQueryProcessor.PreparedVisualQuery(
              userDisplayPrompt = clean,
              modelPrompt = clean,
              payloads = emptyList(),
            )
          }

        val assistantTurnId =
          floatingSessionStore.appendUserTurnAndPrepareAssistantPlaceholder(
            userDisplayText = prepared.userDisplayPrompt,
            attachmentUris = attachmentUris,
          )

        val historyMessages =
          snapshotTurns
            .filter { it.text.isNotBlank() }
            .takeLast(12)
            .map { turn ->
              DomainMessage(
                id = turn.id,
                conversationId = "overlay_quick",
                isUser = turn.isUser,
                text = turn.text,
                timestampMs = turn.timestampMs,
                imageUris = turn.attachmentUris,
                modelUsed = turn.modelLabel.takeIf { it.isNotBlank() },
              )
            }

        var latestModelLabel = floatingSessionStore.state.value.activeModelLabel
        aiOrchestrator
          .processMessage(
            OrchestrationRequest(
              conversationId = "overlay_quick",
              userPrompt = prepared.modelPrompt,
              historyMessages = historyMessages,
              currentTurnImages = prepared.payloads,
              requestedModelId = VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID,
            )
          )
          .collect { event ->
            when (event) {
              is OrchestrationEvent.Routed -> {
                latestModelLabel = event.decision.selectedModel.displayName
                floatingSessionStore.setActiveModelLabel(latestModelLabel)
              }
              is OrchestrationEvent.StateChanged -> {
                overlayResponseState.value = event.state
                when (val s = event.state) {
                  is AiResponseState.Streaming -> {
                    floatingSessionStore.updateAssistantTurn(
                      assistantTurnId = assistantTurnId,
                      text = s.partialText,
                      modelLabel = latestModelLabel,
                    )
                  }
                  is AiResponseState.Completed -> {
                    floatingSessionStore.updateAssistantTurn(
                      assistantTurnId = assistantTurnId,
                      text = s.fullText,
                      modelLabel = latestModelLabel,
                    )
                    val settings = settingsRepository.getSettingsSnapshot()
                    if (settings.voicePreferences.autoSpeakResponses) {
                      voiceController.speakResponse("overlay_ans", s.fullText, settings.voicePreferences)
                    }
                  }
                  is AiResponseState.Error -> {
                    floatingSessionStore.updateAssistantTurn(
                      assistantTurnId = assistantTurnId,
                      text = s.message,
                      modelLabel = latestModelLabel,
                    )
                  }
                  else -> Unit
                }
              }
              else -> Unit
            }
          }
      }
  }

  private fun collapsePanelToBubble() {
    imeHidePendingJob?.cancel()
    overlayVoiceCollectorJob?.cancel()
    overlayVoiceCollectorJob = null
    assistantPanelContainer?.let { container ->
      val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
      imm?.hideSoftInputFromWindow(container.windowToken, 0)
      runCatching { windowManager?.removeView(container) }
    }
    assistantPanelContainer = null
    assistantPanelParams = null
    assistantPanelLifecycle?.onDestroy()
    assistantPanelLifecycle = null
    sessionStateManager.onPanelExpandedChanged(false)
    if (geminiLiveVoiceEngine.status.value != LiveVoiceSessionStatus.Active &&
      screenShareSessionManager.status.value == ScreenShareStatus.STOPPED
    ) {
      sessionStateManager.onActivationAnimationSettled()
    }
  }

  private fun dismissPopupWindow() {
    popupView?.let { v -> runCatching { windowManager?.removeView(v) } }
    popupView = null
    popupLifecycle?.onDestroy()
    popupLifecycle = null
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val wm = windowManager ?: return
    val geo = displayGeometryManager.measure(this, assistantPanelContainer)
    currentGeometryState.value = geo

    controlBarContainer?.let { bar ->
      controlBarParams?.let { params ->
        val overlayUi = floatingSessionStore.state.value
        val w = bar.width.takeIf { it > 16 } ?: (288f * geo.density).roundToInt()
        val h = bar.height.takeIf { it > 16 } ?: (56f * geo.density).roundToInt()
        val (rx, ry) =
          FloatingControlBarGeometry.coordinatesFromFraction(
            xFraction = overlayUi.controlBarXFraction,
            yFraction = overlayUi.controlBarYFraction,
            barWidthPx = w,
            barHeightPx = h,
            geometry = geo,
          )
        params.x = rx
        params.y = ry
        runCatching { wm.updateViewLayout(bar, params) }
      }
    }

    repositionProfileMenuIfOpen(geo)

    orbView?.let { view ->
      orbParams?.let { params ->
        val (clampedX, clampedY) =
          DisplayGeometryManager.clampBubbleCoordinates(params.x, params.y, orbSizePx, geo)
        val snappedX = OrbPositionHelper.computeSnappedXPixel(clampedX, orbSizePx, geo, snapToEdge = true)
        params.x = snappedX
        params.y = clampedY
        runCatching { wm.updateViewLayout(view, params) }
      }
    }

    assistantPanelContainer?.let { container ->
      assistantPanelParams?.let { params ->
        updatePanelLayoutForInsets(container, params, wm)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isPowerReceiverRegistered) {
      runCatching { unregisterReceiver(powerStateReceiver) }
      isPowerReceiverRegistered = false
    }
    edgeRevealSettlerJob?.cancel()
    edgeStopFadeJob?.cancel()
    imeHidePendingJob?.cancel()
    overlayQueryJob?.cancel()
    overlayVoiceCollectorJob?.cancel()
    hideDismissTargetOverlay()
    dismissPopupWindow()
    detachProfileQuickMenu()
    collapsePanelToBubble()
    detachControlBar()
    detachEdgeLightWindow()
    orbView?.let { v -> runCatching { windowManager?.removeView(v) } }
    orbView = null
    orbLifecycle?.onDestroy()
    orbLifecycle = null
    scope.cancel()
  }

  companion object {
    const val CHANNEL_ID = "mousomi_floating_orb"
    const val NOTIFICATION_ID = 4402
    const val ACTION_START = "com.example.action.START_OVERLAY_ORB"
    const val ACTION_SHOW_CONTROL_BAR = "com.example.action.SHOW_FLOATING_CONTROL_BAR"
    const val ACTION_HIDE_FOR_SELECTION = "com.example.action.HIDE_FOR_SCREEN_SELECTION"
    const val ACTION_PAUSE = "com.example.action.PAUSE_OVERLAY_ORB"
    const val ACTION_RESUME = "com.example.action.RESUME_OVERLAY_ORB"
    const val ACTION_STOP = "com.example.action.STOP_OVERLAY_ORB"
    const val ACTION_SHOW_ASSISTANT_PANEL = "com.example.action.SHOW_OVERLAY_ASSISTANT_PANEL"
    const val ACTION_ACTIVATE_ASSISTANT = "com.example.action.ACTIVATE_FLOATING_ASSISTANT"
    const val EXTRA_PANEL_VOICE_AUTO = "extra_panel_voice_auto"
    const val EXTRA_EXPAND_PANEL = "extra_expand_panel"
    const val EXTRA_ACTIVATION_EDGE = "extra_activation_edge"

    fun buildShowControlBarIntent(context: Context): Intent =
      Intent(context, MousomiOverlayService::class.java).apply { action = ACTION_SHOW_CONTROL_BAR }

    fun buildActivateAssistantIntent(
      context: Context,
      edge: EdgeActivationEdge = EdgeActivationEdge.BOTTOM_CORNER,
      expandPanel: Boolean = true,
      autoVoice: Boolean = true,
    ): Intent =
      Intent(context, MousomiOverlayService::class.java).apply {
        action = ACTION_ACTIVATE_ASSISTANT
        putExtra(EXTRA_ACTIVATION_EDGE, edge.name)
        putExtra(EXTRA_EXPAND_PANEL, expandPanel)
        putExtra(EXTRA_PANEL_VOICE_AUTO, autoVoice)
      }
  }
}

@AndroidEntryPoint
class MousomiNotificationListenerService : NotificationListenerService() {

  @Inject lateinit var notificationEngine: NotificationIntelligenceEngine
  @Inject lateinit var settingsRepository: SettingsRepository

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    if (sbn.packageName == packageName || sbn.isOngoing) return
    val extras = sbn.notification?.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val body =
      extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

    scope.launch {
      val cfg = settingsRepository.getSettingsSnapshot().notificationConfig
      notificationEngine.updateConfig(cfg)
      val appLabel =
        runCatching {
          val info = packageManager.getApplicationInfo(sbn.packageName, 0)
          packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

      notificationEngine.onNotificationPosted(
        packageName = sbn.packageName,
        appLabel = appLabel,
        title = title,
        bodyText = body,
      )
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
  }
}

class MousomiVoiceInteractionService : VoiceInteractionService()

class MousomiVoiceInteractionSessionService : VoiceInteractionSessionService() {
  override fun onNewSession(args: Bundle?): VoiceInteractionSession =
    MousomiAssistantSession(this)
}

private class MousomiAssistantSession(context: Context) : VoiceInteractionSession(context) {
  override fun onShow(args: Bundle?, showFlags: Int) {
    super.onShow(args, showFlags)
    if (Settings.canDrawOverlays(context)) {
      val overlayIntent =
        MousomiOverlayService.buildActivateAssistantIntent(
          context = context,
          edge = EdgeActivationEdge.BOTTOM_CORNER,
          expandPanel = true,
          autoVoice = true,
        )
      ContextCompat.startForegroundService(context, overlayIntent)
    } else {
      val launch =
        Intent(context, MainActivity::class.java).apply {
          action = Intent.ACTION_ASSIST
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
          putExtra(MainActivity.EXTRA_QUICK_ACTION, OrbQuickAction.START_VOICE.name)
        }
      context.startActivity(launch)
    }
    finish()
  }
}

class MousomiQuickTileService : TileService() {
  override fun onStartListening() {
    super.onStartListening()
    qsTile?.apply {
      state = Tile.STATE_ACTIVE
      label = "Mousomi AI"
      updateTile()
    }
  }

  override fun onClick() {
    super.onClick()
    if (Settings.canDrawOverlays(this)) {
      val overlayIntent =
        MousomiOverlayService.buildActivateAssistantIntent(
          context = this,
          edge = EdgeActivationEdge.BOTTOM,
          expandPanel = true,
          autoVoice = false,
        )
      ContextCompat.startForegroundService(this, overlayIntent)
    } else {
      val launch =
        Intent(this, MainActivity::class.java).apply {
          action = Intent.ACTION_ASSIST
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val pending =
          PendingIntent.getActivity(
            this,
            409,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        startActivityAndCollapse(pending)
      } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(launch)
      }
    }
  }
}
