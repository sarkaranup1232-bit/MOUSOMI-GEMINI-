package com.example.ui.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.core.overlay.FloatingAssistantSessionStore
import com.example.core.overlay.FloatingAttachmentItem
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenSelectionManager
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.storage.TempMediaCacheManager
import com.example.service.MediaProjectionCaptureService
import com.example.service.MousomiOverlayService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Translucent trampoline Activity launched from the Floating Control Bar / Floating Live Popup /
 * Assistant Session to request explicit Android OS [MediaProjection] consent before Screen Selection,
 * Screen Sharing, or Screen Recording. Never bypasses Android consent and keeps recents clean.
 */
@AndroidEntryPoint
class ScreenCaptureConsentActivity : ComponentActivity() {

  enum class ConsentMode {
    SCREEN_SELECTION,
    SCREEN_SHARE,
    SCREEN_RECORDING,
  }

  @Inject lateinit var screenSelectionManager: ScreenSelectionManager
  @Inject lateinit var screenShareSessionManager: ScreenShareSessionManager
  @Inject lateinit var screenRecordingManager: ScreenRecordingManager
  @Inject lateinit var floatingSessionStore: FloatingAssistantSessionStore

  private var triggeredFromOverlay: Boolean = true
  private var consentMode: ConsentMode = ConsentMode.SCREEN_SELECTION
  private var launchedConsent: Boolean = false

  private val projectionConsentLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val data = result.data
      if (result.resultCode == Activity.RESULT_OK && data != null) {
        val captureMode =
          when (consentMode) {
            ConsentMode.SCREEN_SELECTION -> {
              floatingSessionStore.setSelectionTriggeredFromOverlay(triggeredFromOverlay)
              MediaProjectionCaptureService.CAPTURE_MODE_SELECTION
            }
            ConsentMode.SCREEN_SHARE -> MediaProjectionCaptureService.CAPTURE_MODE_SCREEN_SHARE
            ConsentMode.SCREEN_RECORDING -> MediaProjectionCaptureService.CAPTURE_MODE_SCREEN_RECORDING
          }
        val svcIntent =
          MediaProjectionCaptureService.buildStartIntent(
            context = this,
            resultCode = result.resultCode,
            data = data,
            forScreenSelection = consentMode == ConsentMode.SCREEN_SELECTION,
            captureMode = captureMode,
          )
        ContextCompat.startForegroundService(this, svcIntent)
      } else {
        when (consentMode) {
          ConsentMode.SCREEN_SELECTION -> {
            screenSelectionManager.cancelSelection()
            floatingSessionStore.setSelectionTriggeredFromOverlay(false)
            floatingSessionStore.setStatusBanner("Screen capture permission was cancelled.")
            if (triggeredFromOverlay) {
              val restoreAction =
                if (floatingSessionStore.state.value.isPanelOpen) {
                  MousomiOverlayService.ACTION_SHOW_ASSISTANT_PANEL
                } else {
                  MousomiOverlayService.ACTION_SHOW_CONTROL_BAR
                }
              ContextCompat.startForegroundService(
                this,
                Intent(this, MousomiOverlayService::class.java).apply { action = restoreAction },
              )
            }
          }
          ConsentMode.SCREEN_SHARE -> {
            screenShareSessionManager.stopSession("Screen share permission was cancelled.")
            floatingSessionStore.setStatusBanner("Screen share permission was cancelled.")
          }
          ConsentMode.SCREEN_RECORDING -> {
            screenRecordingManager.markCancelled("Screen recording permission was cancelled.")
            floatingSessionStore.setStatusBanner("Screen recording permission was cancelled.")
          }
        }
      }
      finishQuietly()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    triggeredFromOverlay = intent?.getBooleanExtra(EXTRA_FROM_OVERLAY, true) ?: true
    consentMode =
      intent?.getStringExtra(EXTRA_CONSENT_MODE)?.let { raw ->
        runCatching { ConsentMode.valueOf(raw) }.getOrNull()
      } ?: ConsentMode.SCREEN_SELECTION
    launchedConsent = savedInstanceState?.getBoolean(KEY_LAUNCHED_CONSENT) == true
    if (!launchedConsent) {
      launchedConsent = true
      val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
      if (mgr != null) {
        when (consentMode) {
          ConsentMode.SCREEN_SELECTION -> {
            screenSelectionManager.markAwaitingConsent(useSystemOverlay = true)
            floatingSessionStore.setSelectionTriggeredFromOverlay(triggeredFromOverlay)
          }
          ConsentMode.SCREEN_SHARE -> {
            screenShareSessionManager.markRequestingConsent()
          }
          ConsentMode.SCREEN_RECORDING -> {
            screenRecordingManager.markAwaitingConsent()
          }
        }
        projectionConsentLauncher.launch(mgr.createScreenCaptureIntent())
      } else {
        when (consentMode) {
          ConsentMode.SCREEN_SELECTION -> {
            screenSelectionManager.reportError("MediaProjection service is unavailable on this device.")
            floatingSessionStore.setSelectionTriggeredFromOverlay(false)
          }
          ConsentMode.SCREEN_SHARE -> {
            screenShareSessionManager.stopSession("MediaProjection service is unavailable on this device.")
          }
          ConsentMode.SCREEN_RECORDING -> {
            screenRecordingManager.markFailed("MediaProjection service is unavailable on this device.")
          }
        }
        finishQuietly()
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(KEY_LAUNCHED_CONSENT, launchedConsent)
  }

  private fun finishQuietly() {
    finish()
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
  }

  companion object {
    const val EXTRA_FROM_OVERLAY = "extra_from_overlay"
    const val EXTRA_CONSENT_MODE = "extra_consent_mode"
    private const val KEY_LAUNCHED_CONSENT = "key_launched_consent"

    fun buildIntent(context: Context, fromOverlay: Boolean = true): Intent =
      Intent(context, ScreenCaptureConsentActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        putExtra(EXTRA_FROM_OVERLAY, fromOverlay)
        putExtra(EXTRA_CONSENT_MODE, ConsentMode.SCREEN_SELECTION.name)
      }

    fun buildScreenShareIntent(context: Context, fromOverlay: Boolean = true): Intent =
      Intent(context, ScreenCaptureConsentActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        putExtra(EXTRA_FROM_OVERLAY, fromOverlay)
        putExtra(EXTRA_CONSENT_MODE, ConsentMode.SCREEN_SHARE.name)
      }

    fun buildScreenRecordingIntent(context: Context, fromOverlay: Boolean = false): Intent =
      Intent(context, ScreenCaptureConsentActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        putExtra(EXTRA_FROM_OVERLAY, fromOverlay)
        putExtra(EXTRA_CONSENT_MODE, ConsentMode.SCREEN_RECORDING.name)
      }
  }
}

/**
 * Zero-permission Android Photo Picker trampoline launched from the Floating AI Chat Panel.
 * Copies the picked photo into temp app cache while URI grant is live, attaches it to
 * [FloatingAssistantSessionStore], and restores the floating panel cleanly.
 */
@AndroidEntryPoint
class OverlayMediaPickerActivity : ComponentActivity() {

  @Inject lateinit var floatingSessionStore: FloatingAssistantSessionStore
  @Inject lateinit var tempMediaCacheManager: TempMediaCacheManager

  private var launchedPicker: Boolean = false

  private val pickMediaLauncher =
    registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri == null) {
        reopenOverlayPanel()
        finishQuietly()
        return@registerForActivityResult
      }
      lifecycleScope.launch {
        val copied = tempMediaCacheManager.copyUriToTempFile(uri.toString(), extensionHint = "jpg")
        val item =
          copied.fold(
            onSuccess = { tmp ->
              FloatingAttachmentItem(
                uriString = tmp.file.toURI().toString(),
                filePath = tmp.file.absolutePath,
                label = tmp.displayName,
                isFromScreenSelection = false,
              )
            },
            onFailure = {
              FloatingAttachmentItem(
                uriString = uri.toString(),
                filePath = null,
                label = "Image",
                isFromScreenSelection = false,
              )
            },
          )
        floatingSessionStore.attachImage(item, openPreviewIfFirst = true)
        reopenOverlayPanel()
        finishQuietly()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    launchedPicker = savedInstanceState?.getBoolean(KEY_LAUNCHED_PICKER) == true
    if (!launchedPicker) {
      launchedPicker = true
      pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(KEY_LAUNCHED_PICKER, launchedPicker)
  }

  private fun reopenOverlayPanel() {
    ContextCompat.startForegroundService(
      this,
      Intent(this, MousomiOverlayService::class.java).apply {
        action = MousomiOverlayService.ACTION_SHOW_ASSISTANT_PANEL
        putExtra(MousomiOverlayService.EXTRA_PANEL_VOICE_AUTO, false)
      },
    )
  }

  private fun finishQuietly() {
    finish()
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
  }

  companion object {
    private const val KEY_LAUNCHED_PICKER = "key_launched_picker"

    fun buildIntent(context: Context): Intent =
      Intent(context, OverlayMediaPickerActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
      }
  }
}
