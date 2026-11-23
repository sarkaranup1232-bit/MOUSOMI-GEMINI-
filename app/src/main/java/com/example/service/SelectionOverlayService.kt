package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.example.core.screen.ScreenSelectionManager
import com.example.core.screen.SelectionGeometryEngine
import com.example.domain.repository.SettingsRepository
import com.example.ui.components.ScreenSelectionOverlay
import com.example.ui.theme.MousomiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class SelectionOverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
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

/**
 * System-level overlay service enabling Live Screen Selection across any visible app when both
 * TYPE_APPLICATION_OVERLAY permission and user-consented MediaProjection are granted.
 * Displays an ongoing foreground notification with a one-tap Stop control.
 */
@AndroidEntryPoint
class SelectionOverlayService : Service() {

  @Inject lateinit var selectionManager: ScreenSelectionManager
  @Inject lateinit var geometryEngine: SelectionGeometryEngine
  @Inject lateinit var settingsRepository: SettingsRepository

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var windowManager: WindowManager? = null
  private var overlayView: ComposeView? = null
  private var lifecycleOwner: SelectionOverlayLifecycleOwner? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
      selectionManager.cancelSelection()
      detachAndStop()
      return START_NOT_STICKY
    }

    startForegroundOverlayNotification()
    attachSelectionOverlayWindow()
    return START_NOT_STICKY
  }

  private fun startForegroundOverlayNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java)
      if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
        nm?.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID,
            "Live Screen Selection Overlay",
            NotificationManager.IMPORTANCE_LOW,
          )
        )
      }
    }
    val stopIntent =
      Intent(this, SelectionOverlayService::class.java).apply { action = ACTION_STOP }
    val stopPending =
      PendingIntent.getService(
        this,
        305,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Mousomi Screen Selection Active")
        .setContentText("Circle or highlight any area on screen. Tap Stop to exit.")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .addAction(0, "Stop Selection", stopPending)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
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

  private fun attachSelectionOverlayWindow() {
    if (overlayView != null) return
    val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
    windowManager = wm

    val owner = SelectionOverlayLifecycleOwner().also { it.onCreate() }
    lifecycleOwner = owner

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayLayoutType(),
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
      }

    val view =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          val selectionState by selectionManager.state.collectAsState()
          val settings by settingsRepository.observeSettings().collectAsState(initial = null)
          MousomiTheme {
            ScreenSelectionOverlay(
              state = selectionState,
              geometryEngine = geometryEngine,
              onSelectTool = selectionManager::selectToolMode,
              onStrokeStart = selectionManager::onStrokeStart,
              onStrokeMove = selectionManager::onStrokeMove,
              onStrokeEnd = { selectionManager.onStrokeEnd() },
              onHandleDrag = selectionManager::onAdjustHandle,
              onUndo = selectionManager::undo,
              onClear = selectionManager::clearSelection,
              onCancel = {
                selectionManager.cancelSelection()
                detachAndStop()
              },
              onConfirmSelection = { ovW, ovH ->
                scope.launch {
                  // Briefly hide overlay window before fresh live frame capture so neon UI isn't baked into crop
                  if (selectionState.sourceFrameFilePath == null) {
                    visibility = View.INVISIBLE
                    delay(85L)
                  }
                  val outcome =
                    selectionManager.confirmAndAutoCrop(
                      overlayWidthPx = ovW,
                      overlayHeightPx = ovH,
                    )
                  visibility = View.VISIBLE
                  if (outcome.isSuccess) {
                    val launchChat =
                      Intent(this@SelectionOverlayService, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_ATTACH_SCREEN_SELECTION
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                      }
                    startActivity(launchChat)
                    detachAndStop()
                  }
                }
              },
              reducedMotion = settings?.edgeLightConfig?.reducedMotion == true,
            )
          }
        }
      }

    runCatching {
      wm.addView(view, params)
      overlayView = view
    }
  }

  private fun detachAndStop() {
    overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
    overlayView = null
    lifecycleOwner?.onDestroy()
    lifecycleOwner = null
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    super.onDestroy()
    overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
    overlayView = null
    lifecycleOwner?.onDestroy()
    lifecycleOwner = null
    scope.cancel()
  }

  companion object {
    const val CHANNEL_ID = "mousomi_screen_selection"
    const val NOTIFICATION_ID = 4409
    const val ACTION_START = "com.example.action.START_SELECTION_OVERLAY"
    const val ACTION_STOP = "com.example.action.STOP_SELECTION_OVERLAY"

    fun buildStartIntent(context: Context): Intent =
      Intent(context, SelectionOverlayService::class.java).apply { action = ACTION_START }

    fun buildStopIntent(context: Context): Intent =
      Intent(context, SelectionOverlayService::class.java).apply { action = ACTION_STOP }
  }
}
