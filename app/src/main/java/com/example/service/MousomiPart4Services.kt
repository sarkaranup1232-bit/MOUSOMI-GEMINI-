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
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
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
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.overlay.OrbPositionHelper
import com.example.core.permissions.EmergencyStopController
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.voice.AudioSmokeAnalyzer
import com.example.domain.model.OrbPopupAction
import com.example.domain.model.OrbQuickAction
import com.example.domain.model.OrbVisualMode
import com.example.domain.repository.SettingsRepository
import com.example.ui.components.FloatingOrbitPopup
import com.example.ui.components.OrbitDismissScrim
import com.example.ui.components.OrbitalSmokeOrb
import com.example.ui.theme.MousomiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MediaProjectionCaptureService : Service() {

  @Inject lateinit var sessionManager: ScreenShareSessionManager
  @Inject lateinit var settingsRepository: SettingsRepository

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var captureJob: Job? = null
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private val handler = Handler(Looper.getMainLooper())

  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        stopCaptureAndSelf("System or user ended screen sharing.")
      }

      override fun onCapturedContentResize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
          recreateSurfaceForSize(width.coerceAtMost(1080), height.coerceAtMost(1920))
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

    if (resultCode != Activity.RESULT_OK || data == null) {
      sessionManager.stopSession("Screen capture consent was not granted.")
      stopSelf()
      return START_NOT_STICKY
    }

    startAsForeground()
    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    val projection = mgr?.getMediaProjection(resultCode, data)
    if (projection == null) {
      sessionManager.stopSession("Unable to initialize MediaProjection session.")
      stopSelf()
      return START_NOT_STICKY
    }

    mediaProjection = projection
    projection.registerCallback(projectionCallback, handler)
    startCaptureLoop()
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

  private fun stopCaptureAndSelf(reason: String) {
    captureJob?.cancel()
    virtualDisplay?.release()
    virtualDisplay = null
    imageReader?.close()
    imageReader = null
    mediaProjection?.unregisterCallback(projectionCallback)
    mediaProjection?.stop()
    mediaProjection = null
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

    fun buildStartIntent(context: Context, resultCode: Int, data: Intent): Intent =
      Intent(context, MediaProjectionCaptureService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_RESULT_CODE, resultCode)
        putExtra(EXTRA_DATA, data)
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

@AndroidEntryPoint
class MousomiOverlayService : Service() {

  @Inject lateinit var settingsRepository: SettingsRepository
  @Inject lateinit var audioSmokeAnalyzer: AudioSmokeAnalyzer
  @Inject lateinit var emergencyStopController: EmergencyStopController

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var windowManager: WindowManager? = null
  private var orbView: ComposeView? = null
  private var popupView: ComposeView? = null
  private var orbParams: WindowManager.LayoutParams? = null
  private var orbLifecycle: OverlayLifecycleOwner? = null
  private var popupLifecycle: OverlayLifecycleOwner? = null
  private var orbSizePx: Int = 160

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
      stopSelf()
      return START_NOT_STICKY
    }
    startForegroundOverlayNotification()
    attachSystemOrb()
    return START_STICKY
  }

  private fun startForegroundOverlayNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java)
      if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
        nm?.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID,
            "Floating Assistant Orb",
            NotificationManager.IMPORTANCE_MIN,
          )
        )
      }
    }
    val stopIntent = Intent(this, MousomiOverlayService::class.java).apply { action = ACTION_STOP }
    val stopPending =
      PendingIntent.getService(
        this,
        201,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Mousomi Orbital AI Active")
        .setContentText("Tap orb for 12-action Orbit Launcher or Hide to turn off.")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .addAction(0, "Hide Orb", stopPending)
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

  private fun attachSystemOrb() {
    if (orbView != null) return
    val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
    windowManager = wm

    val metrics = resources.displayMetrics
    orbSizePx = (64 * metrics.density).toInt().coerceIn(96, 220)
    val usableW = (metrics.widthPixels - orbSizePx).coerceAtLeast(1)
    val usableH = (metrics.heightPixels - orbSizePx).coerceAtLeast(1)

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
        x = (usableW * 0.92f).toInt().coerceIn(12, usableW)
        y = (usableH * 0.62f).toInt().coerceIn((usableH * 0.08f).toInt(), (usableH * 0.88f).toInt())
      }
    orbParams = params

    val owner = OverlayLifecycleOwner().also { it.onCreate() }
    orbLifecycle = owner

    val view =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        contentDescription = "Mousomi System Floating Orbital AI"
        setContent {
          val settings by settingsRepository.observeSettings().collectAsState(initial = null)
          val audioMetrics by audioSmokeAnalyzer.metrics.collectAsState()
          val isListening by audioSmokeAnalyzer.isCapturing.collectAsState()
          val orbCfg = settings?.floatingOrbConfig
          MousomiTheme {
            OrbitalSmokeOrb(
              mode = if (isListening) OrbVisualMode.LISTENING else OrbVisualMode.IDLE,
              audioMetrics = audioMetrics,
              reducedMotion = settings?.edgeLightConfig?.reducedMotion == true,
              orbSize = (orbCfg?.sizeDp ?: 60f).dp,
              showCenterSparkle = true,
            )
          }
        }
      }

    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f

    view.setOnTouchListener { _, event ->
      val dispW = resources.displayMetrics.widthPixels
      val dispH = resources.displayMetrics.heightPixels
      val maxX = (dispW - orbSizePx).coerceAtLeast(1)
      val maxY = (dispH - orbSizePx).coerceAtLeast(1)
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialX = params.x
          initialY = params.y
          initialTouchX = event.rawX
          initialTouchY = event.rawY
          true
        }
        MotionEvent.ACTION_MOVE -> {
          params.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceIn(0, maxX)
          params.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceIn((maxY * 0.08f).toInt(), (maxY * 0.88f).toInt())
          runCatching { wm.updateViewLayout(view, params) }
          true
        }
        MotionEvent.ACTION_UP -> {
          val moved = abs(event.rawX - initialTouchX) > 14f || abs(event.rawY - initialTouchY) > 14f
          if (!moved) {
            toggleSystemOrbitPopup()
          } else {
            val rawXFrac = (params.x.toFloat() / maxX).coerceIn(0f, 1f)
            val snappedXFrac = OrbPositionHelper.computeSnappedXFraction(rawXFrac, true)
            val clampedYFrac = OrbPositionHelper.clampFraction(params.y.toFloat() / maxY, 0.08f, 0.88f)
            params.x = (snappedXFrac * maxX).toInt().coerceIn(12, (maxX - 12).coerceAtLeast(12))
            params.y = (clampedYFrac * maxY).toInt()
            runCatching { wm.updateViewLayout(view, params) }
            scope.launch {
              val cur = settingsRepository.getSettingsSnapshot()
              settingsRepository.saveSettings(
                cur.copy(
                  floatingOrbConfig =
                    cur.floatingOrbConfig.copy(
                      positionXFraction = snappedXFrac,
                      positionYFraction = clampedYFrac,
                    )
                )
              )
            }
          }
          true
        }
        else -> false
      }
    }

    runCatching {
      wm.addView(view, params)
      orbView = view
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
                  if (action == OrbPopupAction.EMERGENCY_STOP) {
                    emergencyStopController.triggerEmergencyStop("Emergency Stop from system Floating Orbit.")
                    stopSelf()
                  } else {
                    val launch =
                      Intent(this@MousomiOverlayService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_ORB_POPUP_ACTION, action.name)
                      }
                    startActivity(launch)
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

  private fun dismissPopupWindow() {
    popupView?.let { v -> runCatching { windowManager?.removeView(v) } }
    popupView = null
    popupLifecycle?.onDestroy()
    popupLifecycle = null
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val wm = windowManager ?: return
    val view = orbView ?: return
    val params = orbParams ?: return
    val maxX = (resources.displayMetrics.widthPixels - orbSizePx).coerceAtLeast(1)
    val maxY = (resources.displayMetrics.heightPixels - orbSizePx).coerceAtLeast(1)
    params.x = params.x.coerceIn(12, maxX)
    params.y = params.y.coerceIn((maxY * 0.08f).toInt(), (maxY * 0.88f).toInt())
    runCatching { wm.updateViewLayout(view, params) }
  }

  override fun onDestroy() {
    super.onDestroy()
    dismissPopupWindow()
    orbView?.let { v -> runCatching { windowManager?.removeView(v) } }
    orbView = null
    orbLifecycle?.onDestroy()
    orbLifecycle = null
    scope.cancel()
  }

  companion object {
    const val CHANNEL_ID = "mousomi_floating_orb"
    const val NOTIFICATION_ID = 4402
    const val ACTION_STOP = "com.example.action.STOP_OVERLAY_ORB"
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
    val launch =
      Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_ASSIST
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(MainActivity.EXTRA_QUICK_ACTION, OrbQuickAction.START_VOICE.name)
      }
    context.startActivity(launch)
    finish()
  }
}
