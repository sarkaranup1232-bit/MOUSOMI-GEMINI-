package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.Manifest
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.MainActivity
import com.example.R
import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.display.DisplayGeometry
import com.example.core.display.DisplayGeometryManager
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.overlay.OrbPositionHelper
import androidx.core.content.ContextCompat
import com.example.core.permissions.EmergencyStopController
import com.example.core.screen.MediaProjectionManager as AppMediaProjectionManager
import com.example.core.screen.ScreenSelectionManager
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.screen.ScreenshotCaptureManager
import com.example.core.voice.AudioSmokeAnalyzer
import com.example.core.voice.GeminiLiveVoiceEngine
import com.example.core.voice.LiveVoiceSessionStatus
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
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
import com.example.ui.components.FloatingOrbitPopup
import com.example.ui.components.GeminiFourPointSparkle
import com.example.ui.components.MousomiDesignTokens
import com.example.ui.components.OrbitDismissScrim
import com.example.ui.components.OrbitalSmokeOrb
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.MousomiTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.WarmAmber
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs
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
      screenSelectionManager.stopAndCleanup()
    }
    sessionManager.stopSession(reason)
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
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
) : FrameLayout(context) {

  private var backCallback: Any? = null

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

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var windowManager: WindowManager? = null
  private var edgeLightView: ComposeView? = null
  private var edgeLightLifecycle: OverlayLifecycleOwner? = null
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
  private val overlayAnswerText = MutableStateFlow("")
  private val overlayModelLabel = MutableStateFlow("")
  private val overlayPromptText = MutableStateFlow("")
  private val panelMaxHeightDpState = mutableIntStateOf(320)
  private val currentGeometryState = MutableStateFlow<DisplayGeometry?>(null)
  private var overlayQueryJob: Job? = null
  private var overlayVoiceCollectorJob: Job? = null
  private var edgeRevealSettlerJob: Job? = null
  private var imeHidePendingJob: Job? = null
  private var overlayChannelCreated = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
      endLiveAssistantSession(stopServiceCompletely = true)
      return START_NOT_STICKY
    }

    when (intent?.action) {
      ACTION_PAUSE -> {
        isOverlayPaused.value = true
        sessionStateManager.onPausedChanged(true)
        geminiLiveVoiceEngine.pauseSession()
        voiceController.stopListening()
        dismissPopupWindow()
        collapsePanelToBubble()
        startForegroundOverlayNotification()
        return START_STICKY
      }
      ACTION_RESUME -> {
        isOverlayPaused.value = false
        sessionStateManager.onPausedChanged(false)
        startForegroundOverlayNotification()
        attachEdgeLightWindow()
        attachSystemOrb()
        return START_STICKY
      }
      ACTION_ACTIVATE_ASSISTANT -> {
        val edgeRaw = intent.getStringExtra(EXTRA_ACTIVATION_EDGE) ?: EdgeActivationEdge.BOTTOM_CORNER.name
        val edge = runCatching { EdgeActivationEdge.valueOf(edgeRaw) }.getOrDefault(EdgeActivationEdge.BOTTOM_CORNER)
        val expandPanel = intent.getBooleanExtra(EXTRA_EXPAND_PANEL, true)
        val autoVoice = intent.getBooleanExtra(EXTRA_PANEL_VOICE_AUTO, true)
        val accepted = sessionStateManager.activateFromTrigger(edge = edge, expandPanel = expandPanel)
        isOverlayPaused.value = false
        startForegroundOverlayNotification()
        attachEdgeLightWindow()
        attachSystemOrb()
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
        sessionStateManager.activateFromTrigger(edge = EdgeActivationEdge.BOTTOM, expandPanel = true)
        scheduleRevealSettled()
        startForegroundOverlayNotification()
        attachEdgeLightWindow()
        attachSystemOrb()
        val autoVoice = intent.getBooleanExtra(EXTRA_PANEL_VOICE_AUTO, false)
        showAssistantPanel(autoStartVoice = autoVoice)
        return START_STICKY
      }
    }

    startForegroundOverlayNotification()
    attachEdgeLightWindow()
    attachSystemOrb()
    return START_STICKY
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
          else "Tap Quick Ask, drag bubble to X to dismiss, or tap Stop anytime."
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

          val effectivePhase =
            when {
              paused -> AssistantSessionPhase.PAUSED
              liveStatus == LiveVoiceSessionStatus.Active &&
                sessionPhase == AssistantSessionPhase.INACTIVE -> AssistantSessionPhase.LISTENING
              else -> sessionPhase
            }

          val effectiveConfig =
            (settings?.edgeLightConfig ?: EdgeLightConfig()).copy(
              enabled = !paused && (settings?.edgeLightConfig?.enabled ?: true),
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
              isSystemPowerSave = false,
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
    // Stop bubble-bound mic cleanly; keep independent active screen share intact unless End Live is pressed
    geminiLiveVoiceEngine.stopAndClose()
    voiceController.stopListening()
    voiceController.stopSpeaking()
    orbView?.let { v -> runCatching { windowManager?.removeView(v) } }
    orbView = null
    orbLifecycle?.onDestroy()
    orbLifecycle = null
    sessionStateManager.onBubbleDismissed(stopAssistantVoice = true)
    if (!sessionStateManager.isSessionActive.value) {
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
    collapsePanelToBubble()
    sessionStateManager.onEndLiveAssistant(keepScreenShare = false)
    if (stopServiceCompletely) {
      detachEdgeLightWindow()
      orbView?.let { v -> runCatching { windowManager?.removeView(v) } }
      orbView = null
      orbLifecycle?.onDestroy()
      orbLifecycle = null
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
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
      sessionStateManager.onPanelExpandedChanged(true)
      if (autoStartVoice) startOverlayVoiceListening()
      return
    }
    val wm = (windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
    windowManager = wm
    dismissPopupWindow()
    if (edgeLightView == null && !isOverlayPaused.value) {
      attachEdgeLightWindow()
    }
    attachSystemOrb()

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
      }
    assistantPanelParams = params

    lateinit var containerRef: BackAwareOverlayContainer
    val container =
      BackAwareOverlayContainer(this) {
        if (sessionStateManager.isKeyboardVisible.value) {
          val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
          imm?.hideSoftInputFromWindow(containerRef.windowToken, 0)
          sessionStateManager.onKeyboardVisibilityChanged(false)
          updatePanelLayoutForInsets(containerRef, params, wm)
          true
        } else {
          collapsePanelToBubble()
          true
        }
      }
    containerRef = container

    val composeView =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          val prompt by overlayPromptText.collectAsState()
          val responseState by overlayResponseState.collectAsState()
          val answer by overlayAnswerText.collectAsState()
          val modelLabel by overlayModelLabel.collectAsState()
          val voiceState by voiceController.voiceState.collectAsState()
          val liveStatus by geminiLiveVoiceEngine.status.collectAsState()
          val userLiveTranscript by geminiLiveVoiceEngine.userTranscript.collectAsState()
          val aiLiveTranscript by geminiLiveVoiceEngine.assistantTranscript.collectAsState()
          val paused by isOverlayPaused.collectAsState()
          val maxCardDp by panelMaxHeightDpState

          val effectiveAnswer =
            when {
              aiLiveTranscript.isNotBlank() &&
                (liveStatus == LiveVoiceSessionStatus.Active || liveStatus == LiveVoiceSessionStatus.Paused) ->
                aiLiveTranscript
              else -> answer
            }

          MousomiTheme {
            FloatingAssistantOverlayCard(
              promptText = prompt,
              answerText = effectiveAnswer,
              liveUserTranscript = userLiveTranscript,
              modelLabel = modelLabel,
              responseState = responseState,
              voiceState = voiceState,
              liveStatus = liveStatus,
              isPaused = paused,
              maxCardHeightDp = maxCardDp,
              onPromptChanged = { overlayPromptText.value = it },
              onSendPrompt = { submitOverlayPrompt(it) },
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
              onTogglePause = {
                val nextPaused = !isOverlayPaused.value
                isOverlayPaused.value = nextPaused
                sessionStateManager.onPausedChanged(nextPaused)
                if (nextPaused) {
                  geminiLiveVoiceEngine.pauseSession()
                  voiceController.stopListening()
                  voiceController.stopSpeaking()
                } else {
                  attachEdgeLightWindow()
                  if (liveStatus == LiveVoiceSessionStatus.Paused) {
                    geminiLiveVoiceEngine.resumeSession { startOverlayFallbackStt() }
                  }
                }
                startForegroundOverlayNotification()
              },
              onEndLive = { endLiveAssistantSession(stopServiceCompletely = false) },
              onExpandApp = {
                collapsePanelToBubble()
                val launch =
                  Intent(this@MousomiOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                  }
                startActivity(launch)
              },
              onCollapseToBubble = { collapsePanelToBubble() },
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

    // Detect if WM already positioned overlay window above IME or if we need explicit bottom offset
    val visibleRect = Rect()
    container.getWindowVisibleDisplayFrame(visibleRect)
    val alreadyShifted =
      imeVisibleNow && visibleRect.bottom in 100 until (geo.screenHeightPx - geo.imeBottomPx + (32 * geo.density).roundToInt())

    val placement = DisplayGeometryManager.computePanelPlacement(geo, windowAlreadyShiftedByIme = alreadyShifted)
    panelMaxHeightDpState.intValue = placement.maxHeightDp

    val targetY = placement.bottomMarginPx
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
      overlayAnswerText.value = "Microphone permission is needed for voice input. Open Permissions in Mousomi AI."
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
              overlayPromptText.value = clean
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
    if (clean.isBlank()) return
    overlayPromptText.value = ""
    if (geminiLiveVoiceEngine.status.value == LiveVoiceSessionStatus.Active) {
      geminiLiveVoiceEngine.sendFollowUpText(clean)
      return
    }
    overlayQueryJob?.cancel()
    overlayQueryJob =
      scope.launch {
        overlayAnswerText.value = ""
        overlayResponseState.value = AiResponseState.Loading
        aiOrchestrator
          .processMessage(
            OrchestrationRequest(
              conversationId = "overlay_quick",
              userPrompt = clean,
              historyMessages = emptyList(),
              requestedModelId = VerifiedModelCatalog.MOUSOMI_AUTO_SLOT_ID,
            )
          )
          .collect { event ->
            when (event) {
              is OrchestrationEvent.Routed -> {
                overlayModelLabel.value = event.decision.selectedModel.displayName
              }
              is OrchestrationEvent.StateChanged -> {
                overlayResponseState.value = event.state
                when (val s = event.state) {
                  is AiResponseState.Streaming -> overlayAnswerText.value = s.partialText
                  is AiResponseState.Completed -> {
                    overlayAnswerText.value = s.fullText
                    val settings = settingsRepository.getSettingsSnapshot()
                    if (settings.voicePreferences.autoSpeakResponses) {
                      voiceController.speakResponse("overlay_ans", s.fullText, settings.voicePreferences)
                    }
                  }
                  is AiResponseState.Error -> overlayAnswerText.value = s.message
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
    // If voice/screen share aren't running, transition edge glow smoothly off while keeping bubble ready
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
    edgeRevealSettlerJob?.cancel()
    imeHidePendingJob?.cancel()
    overlayQueryJob?.cancel()
    overlayVoiceCollectorJob?.cancel()
    hideDismissTargetOverlay()
    dismissPopupWindow()
    collapsePanelToBubble()
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
    const val ACTION_PAUSE = "com.example.action.PAUSE_OVERLAY_ORB"
    const val ACTION_RESUME = "com.example.action.RESUME_OVERLAY_ORB"
    const val ACTION_STOP = "com.example.action.STOP_OVERLAY_ORB"
    const val ACTION_SHOW_ASSISTANT_PANEL = "com.example.action.SHOW_OVERLAY_ASSISTANT_PANEL"
    const val ACTION_ACTIVATE_ASSISTANT = "com.example.action.ACTIVATE_FLOATING_ASSISTANT"
    const val EXTRA_PANEL_VOICE_AUTO = "extra_panel_voice_auto"
    const val EXTRA_EXPAND_PANEL = "extra_expand_panel"
    const val EXTRA_ACTIVATION_EDGE = "extra_activation_edge"

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

@Composable
private fun FloatingAssistantOverlayCard(
  promptText: String,
  answerText: String,
  liveUserTranscript: String,
  modelLabel: String,
  responseState: AiResponseState,
  voiceState: VoiceState,
  liveStatus: LiveVoiceSessionStatus,
  isPaused: Boolean,
  maxCardHeightDp: Int,
  onPromptChanged: (String) -> Unit,
  onSendPrompt: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onTogglePause: () -> Unit,
  onEndLive: () -> Unit,
  onExpandApp: () -> Unit,
  onCollapseToBubble: () -> Unit,
) {
  val isBusy =
    responseState is AiResponseState.Loading ||
      responseState is AiResponseState.Thinking ||
      responseState is AiResponseState.Generating ||
      responseState is AiResponseState.Streaming
  val isMicActive =
    liveStatus == LiveVoiceSessionStatus.Active ||
      liveStatus == LiveVoiceSessionStatus.Connecting ||
      voiceState is VoiceState.Listening

  Surface(
    shape = RoundedCornerShape(26.dp),
    color = LiquidGlassDark.copy(alpha = 0.96f),
    border =
      BorderStroke(
        width = 2.dp,
        brush = Brush.linearGradient(MousomiDesignTokens.GoogleNeonSpectrum),
      ),
    shadowElevation = 20.dp,
    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(max = maxCardHeightDp.dp),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          GeminiFourPointSparkle(size = 20.dp, animated = !isPaused)
          Column {
            Text(
              text = "Mousomi Assistant",
              color = Color.White,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            val statusSub =
              when {
                isPaused || liveStatus == LiveVoiceSessionStatus.Paused -> "Paused"
                liveStatus == LiveVoiceSessionStatus.Connecting -> "Connecting Gemini Live…"
                liveStatus == LiveVoiceSessionStatus.Active -> "Live Audio Active (Speak anytime)"
                liveStatus is LiveVoiceSessionStatus.Reconnecting -> "Reconnecting Live…"
                voiceState is VoiceState.Listening -> "Listening…"
                isBusy -> modelLabel.ifBlank { "Thinking…" }
                modelLabel.isNotBlank() -> modelLabel
                else -> "Ready over current app"
              }
            Text(
              text = statusSub,
              color = if (isPaused) WarmAmber else NeonCyan,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onTogglePause, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
              contentDescription = if (isPaused) "Resume Assistant" else "Pause Assistant",
              tint = if (isPaused) NeonCyan else WarmAmber,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(onClick = onExpandApp, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.Outlined.OpenInFull,
              contentDescription = "Open Full Mousomi App",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(onClick = onCollapseToBubble, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Collapse to Floating Bubble",
              tint = Color.White.copy(alpha = 0.85f),
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      if (liveUserTranscript.isNotBlank() && isMicActive) {
        Text(
          text = "You: $liveUserTranscript",
          color = NeonCyan.copy(alpha = 0.9f),
          fontSize = 11.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (answerText.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color.White.copy(alpha = 0.08f),
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 150.dp),
        ) {
          Text(
            text = answerText,
            color = Color.White.copy(alpha = 0.94f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
          )
        }
      }

      if (isMicActive || liveStatus == LiveVoiceSessionStatus.Paused) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          Surface(
            onClick = onEndLive,
            shape = CircleShape,
            color = Color(0xFF991B1B).copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Color(0xFFEF4444)),
          ) {
            Text(
              text = "End Live",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = promptText,
          onValueChange = onPromptChanged,
          placeholder = {
            Text("Ask Mousomi…", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
          },
          singleLine = true,
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.weight(1f),
        )
        Surface(
          onClick = onToggleVoice,
          shape = CircleShape,
          color = if (isMicActive) NeonCyan else Color.White.copy(alpha = 0.14f),
          modifier = Modifier.size(48.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isMicActive) Icons.Outlined.MicOff else Icons.Outlined.Mic,
              contentDescription = if (isMicActive) "Pause Voice Input" else "Start Live Voice",
              tint = if (isMicActive) Color.Black else Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Surface(
          onClick = { if (!isBusy && promptText.isNotBlank()) onSendPrompt(promptText) },
          shape = CircleShape,
          color = ElectricBlue,
          modifier = Modifier.size(48.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Send,
              contentDescription = "Send",
              tint = Color.White,
              modifier = Modifier.size(19.dp),
            )
          }
        }
      }
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
