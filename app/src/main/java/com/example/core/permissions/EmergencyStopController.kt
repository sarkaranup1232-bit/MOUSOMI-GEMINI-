package com.example.core.permissions

import android.content.Context
import com.example.core.accessibility.AccessibilityAgentController
import com.example.core.assistant.AssistantSessionStateManager
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.GeminiLiveVoiceEngine
import com.example.core.voice.LiveVoiceSessionManager
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
 * - Active voice microphone listening, Gemini Live WebSocket audio session & TTS playback
 * - Active MediaProjection screen sharing, screen recording & temporary screen frames
 * - Central AssistantSessionStateManager active states (so Border Light fades cleanly)
 * - LiveVoiceSessionManager timer, mic lock, and output amplitude analyzer
 * - Active Accessibility automation workflows
 * - Any ongoing AI generation / pending automation tasks (via listeners)
 * Does NOT revoke OS permissions, keeping the user cleanly in control.
 */
@Singleton
class EmergencyStopController @Inject constructor(
  @ApplicationContext private val context: Context,
  private val voiceController: VoiceController,
  private val screenShareSessionManager: ScreenShareSessionManager,
  private val tempMediaCacheManager: TempMediaCacheManager,
  private val geminiLiveVoiceEngine: GeminiLiveVoiceEngine? = null,
  private val assistantSessionStateManager: AssistantSessionStateManager? = null,
  private val liveVoiceSessionManager: LiveVoiceSessionManager? = null,
  private val screenRecordingManager: ScreenRecordingManager? = null,
  private val accessibilityAgentController: AccessibilityAgentController? = null,
) {
  constructor(
    context: Context,
    voiceController: VoiceController,
    screenShareSessionManager: ScreenShareSessionManager,
    tempMediaCacheManager: TempMediaCacheManager,
  ) : this(context, voiceController, screenShareSessionManager, tempMediaCacheManager, null, null, null, null, null)

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
    assistantSessionStateManager?.onBeginStopping()
    accessibilityAgentController?.stopAllAutomation(reason)
    geminiLiveVoiceEngine?.stopAndClose(keepScreenShare = false)
    voiceController.stopListening()
    voiceController.stopSpeaking()
    voiceController.resetState()
    liveVoiceSessionManager?.onEnded(keepScreenShare = false)

    runCatching {
      context.stopService(MediaProjectionCaptureService.buildStopIntent(context))
    }
    runCatching {
      context.stopService(com.example.service.SelectionOverlayService.buildStopIntent(context))
    }
    if (screenRecordingManager?.state?.value?.isActive == true) {
      screenRecordingManager.markCancelled(reason)
    }
    screenShareSessionManager.stopSession(reason)
    tempMediaCacheManager.cleanupAllTempFiles()
    assistantSessionStateManager?.onEndLiveAssistant(keepScreenShare = false)

    val snapshotListeners = synchronized(listeners) { listeners.toList() }
    snapshotListeners.forEach { runCatching { it.invoke() } }

    _lastEmergencyStopAtMs.value = System.currentTimeMillis()
  }
}
