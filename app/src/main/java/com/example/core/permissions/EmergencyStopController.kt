package com.example.core.permissions

import android.content.Context
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.VoiceController
import com.example.service.MediaProjectionCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global Emergency Stop Controller.
 * Immediately terminates:
 * - Active voice microphone listening & Live Voice mode
 * - Active TTS playback & audio focus
 * - Active MediaProjection screen sharing & temporary screen frames
 * - Any ongoing AI generation / pending automation tasks (via listeners)
 * Does NOT revoke OS permissions, keeping the user cleanly in control.
 */
@Singleton
class EmergencyStopController @Inject constructor(
  @ApplicationContext private val context: Context,
  private val voiceController: VoiceController,
  private val screenShareSessionManager: ScreenShareSessionManager,
  private val tempMediaCacheManager: TempMediaCacheManager,
) {
  private val _lastEmergencyStopAtMs = MutableStateFlow<Long?>(null)
  val lastEmergencyStopAtMs: StateFlow<Long?> = _lastEmergencyStopAtMs.asStateFlow()

  private val listeners = mutableListOf<() -> Unit>()

  fun registerStopListener(listener: () -> Unit) {
    synchronized(listeners) {
      if (!listeners.contains(listener)) {
        listeners.add(listener)
      }
    }
  }

  fun triggerEmergencyStop(reason: String = "Emergency Stop activated by user.") {
    voiceController.stopListening()
    voiceController.stopSpeaking()
    voiceController.resetState()

    runCatching {
      context.stopService(MediaProjectionCaptureService.buildStopIntent(context))
    }
    screenShareSessionManager.stopSession(reason)
    tempMediaCacheManager.cleanupAllTempFiles()

    val snapshotListeners = synchronized(listeners) { listeners.toList() }
    snapshotListeners.forEach { runCatching { it.invoke() } }

    _lastEmergencyStopAtMs.value = System.currentTimeMillis()
  }
}
