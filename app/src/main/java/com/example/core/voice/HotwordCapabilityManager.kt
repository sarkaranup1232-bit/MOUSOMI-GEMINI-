package com.example.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.core.assistant.AssistantRoleHelper
import com.example.core.assistant.NavigationGestureDiagnostic
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exact runtime hotword / continuous-listening capability states.
 * Never claims hardware DSP wake-word readiness when privileged OEM permissions or enrollment
 * are unavailable on user-installed apps (e.g., Samsung Galaxy M51 / Android 12 / One UI 4.1).
 */
enum class HotwordCapabilityState(val label: String) {
  DSP_HARDWARE_AVAILABLE("DSP Hotword Hardware Available"),
  DSP_HARDWARE_UNSUPPORTED("DSP Hotword Privileged-Only / Unavailable"),
  KEYPHRASE_UNENROLLED("Keyphrase Not Enrolled"),
  KEYPHRASE_UNAVAILABLE("Keyphrase Enrollment Unavailable"),
  SOFTWARE_HOTWORD_RESTRICTED("Background Software Listening Restricted"),
  DEFAULT_ASSISTANT_NOT_SELECTED("Not Selected as Default Assistant"),
  PERMISSION_REQUIRED("Microphone Permission Required"),
  DETECTOR_START_FAILED("Detector Start Failed"),
  HOTWORD_READY("Hotword Ready"),
}

data class DeviceRuntimeProfile(
  val manufacturer: String,
  val brand: String,
  val model: String,
  val sdkInt: Int,
  val androidRelease: String,
  val oneUiVersion: String?,
  val isSamsungOneUi: Boolean,
) {
  val summaryLabel: String
    get() =
      buildString {
        append("$manufacturer $model • Android $androidRelease (API $sdkInt)")
        if (!oneUiVersion.isNullOrBlank()) {
          append(" • One UI $oneUiVersion")
        }
      }
}

data class HotwordCapabilityReport(
  val state: HotwordCapabilityState,
  val runtimeProfile: DeviceRuntimeProfile,
  val isDefaultAssistant: Boolean,
  val isVoiceInteractionActiveService: Boolean,
  val hasRecordAudioPermission: Boolean,
  val hasCaptureAudioHotwordPermission: Boolean,
  val hasManageVoiceKeyphrasesPermission: Boolean,
  val hasMicrophoneHardware: Boolean,
  val supportsSpeechRecognition: Boolean,
  val supportsOnDeviceRecognition: Boolean,
  val supportsDspHotwordForThirdParty: Boolean,
  val supportsForegroundLiveMicInSession: Boolean,
  val reasonSummary: String,
  val recommendedTriggers: List<String>,
  val checkedAtMs: Long = System.currentTimeMillis(),
  val isRoleHeld: Boolean = isDefaultAssistant,
  val isAssistActivityResolved: Boolean = false,
  val navigationDiagnostic: NavigationGestureDiagnostic? = null,
)

/**
 * Detects real Android OS, OEM (Samsung One UI), RoleManager, VoiceInteractionService, and
 * DSP/Software hotword capabilities without hidden API hacks or fabricated wake-word status.
 */
@Singleton
class HotwordCapabilityManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val _latestReport = MutableStateFlow<HotwordCapabilityReport?>(null)
  val latestReport: StateFlow<HotwordCapabilityReport?> = _latestReport.asStateFlow()

  fun inspectCapabilities(
    lastDetectorFailureReason: String? = null,
    keyphraseEnrolled: Boolean = false,
  ): HotwordCapabilityReport {
    val profile = detectDeviceProfile()
    val roleStatus = AssistantRoleHelper.getStatus(context)
    val isActiveVoiceService =
      runCatching {
        VoiceInteractionService.isActiveService(
          context,
          android.content.ComponentName(context, "com.example.service.MousomiVoiceInteractionService"),
        )
      }.getOrDefault(false)

    val hasRecordAudio =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val hasHotwordPerm =
      ContextCompat.checkSelfPermission(context, PERM_CAPTURE_AUDIO_HOTWORD) ==
        PackageManager.PERMISSION_GRANTED
    val hasKeyphrasePerm =
      ContextCompat.checkSelfPermission(context, PERM_MANAGE_VOICE_KEYPHRASES) ==
        PackageManager.PERMISSION_GRANTED
    val hasMicFeature =
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    val supportsRecognition = SpeechRecognizer.isRecognitionAvailable(context)
    val supportsOnDevice =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
      } else {
        false
      }

    val report =
      evaluateFromInputs(
        profile = profile,
        isDefaultAssistant = roleStatus.isDefaultAssistant || isActiveVoiceService,
        isVoiceInteractionActiveService = isActiveVoiceService,
        hasRecordAudioPermission = hasRecordAudio,
        hasCaptureAudioHotwordPermission = hasHotwordPerm,
        hasManageVoiceKeyphrasesPermission = hasKeyphrasePerm,
        hasMicrophoneHardware = hasMicFeature,
        supportsSpeechRecognition = supportsRecognition,
        supportsOnDeviceRecognition = supportsOnDevice,
        keyphraseEnrolled = keyphraseEnrolled,
        lastDetectorFailureReason = lastDetectorFailureReason,
      ).copy(
        isRoleHeld = roleStatus.isRoleHeld,
        isAssistActivityResolved = roleStatus.isAssistActivityResolved,
        navigationDiagnostic = roleStatus.navigationDiagnostic,
      )
    _latestReport.value = report
    return report
  }

  fun detectDeviceProfile(): DeviceRuntimeProfile {
    val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Android" }
    val brand = Build.BRAND.orEmpty().ifBlank { manufacturer }
    val model = Build.MODEL.orEmpty().ifBlank { "Device" }
    val sdkInt = Build.VERSION.SDK_INT
    val release = Build.VERSION.RELEASE.orEmpty().ifBlank { sdkInt.toString() }
    val isSamsung =
      manufacturer.equals("samsung", ignoreCase = true) || brand.equals("samsung", ignoreCase = true)
    val oneUiVersion = if (isSamsung) readOneUiVersionSafely() else null
    return DeviceRuntimeProfile(
      manufacturer = manufacturer,
      brand = brand,
      model = model,
      sdkInt = sdkInt,
      androidRelease = release,
      oneUiVersion = oneUiVersion,
      isSamsungOneUi = isSamsung,
    )
  }

  fun createBatteryOptimizationIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

  private fun readOneUiVersionSafely(): String? {
    // Public official reflection check against Build.VERSION.SEM_PLATFORM_INT if present on Samsung ROMs;
    // never accesses private internal frameworks or crashes on AOSP/emulators.
    return runCatching {
      val field = Build.VERSION::class.java.getField("SEM_PLATFORM_INT")
      val semPlatformInt = field.getInt(null)
      if (semPlatformInt >= 90_000) {
        val normalized = semPlatformInt - 90_000
        val major = normalized / 10_000
        val minor = (normalized % 10_000) / 100
        "$major.$minor"
      } else {
        null
      }
    }.getOrNull()
  }

  companion object {
    const val PERM_CAPTURE_AUDIO_HOTWORD = "android.permission.CAPTURE_AUDIO_HOTWORD"
    const val PERM_MANAGE_VOICE_KEYPHRASES = "android.permission.MANAGE_VOICE_KEYPHRASES"

    /**
     * Pure deterministic evaluator suitable for unit tests across Galaxy M51 (Android 12 / One UI 4.1)
     * and privileged vs user-installed APK runtime states.
     */
    fun evaluateFromInputs(
      profile: DeviceRuntimeProfile,
      isDefaultAssistant: Boolean,
      isVoiceInteractionActiveService: Boolean,
      hasRecordAudioPermission: Boolean,
      hasCaptureAudioHotwordPermission: Boolean,
      hasManageVoiceKeyphrasesPermission: Boolean,
      hasMicrophoneHardware: Boolean,
      supportsSpeechRecognition: Boolean,
      supportsOnDeviceRecognition: Boolean,
      keyphraseEnrolled: Boolean = false,
      lastDetectorFailureReason: String? = null,
      nowMs: Long = System.currentTimeMillis(),
    ): HotwordCapabilityReport {
      val supportsDspForThirdParty =
        hasMicrophoneHardware &&
          isDefaultAssistant &&
          hasCaptureAudioHotwordPermission &&
          hasManageVoiceKeyphrasesPermission

      val state =
        when {
          !hasMicrophoneHardware -> HotwordCapabilityState.DSP_HARDWARE_UNSUPPORTED
          !isDefaultAssistant -> HotwordCapabilityState.DEFAULT_ASSISTANT_NOT_SELECTED
          !hasRecordAudioPermission -> HotwordCapabilityState.PERMISSION_REQUIRED
          !lastDetectorFailureReason.isNullOrBlank() -> HotwordCapabilityState.DETECTOR_START_FAILED
          !hasCaptureAudioHotwordPermission || !hasManageVoiceKeyphrasesPermission ->
            if (profile.isSamsungOneUi && profile.sdkInt >= Build.VERSION_CODES.S) {
              HotwordCapabilityState.DSP_HARDWARE_UNSUPPORTED
            } else {
              HotwordCapabilityState.SOFTWARE_HOTWORD_RESTRICTED
            }
          !supportsSpeechRecognition -> HotwordCapabilityState.KEYPHRASE_UNAVAILABLE
          !keyphraseEnrolled -> HotwordCapabilityState.KEYPHRASE_UNENROLLED
          else -> HotwordCapabilityState.HOTWORD_READY
        }

      val reasonSummary =
        when (state) {
          HotwordCapabilityState.HOTWORD_READY ->
            "Privileged hotword permissions and keyphrase model verified; hardware AlwaysOnHotwordDetector can be started."
          HotwordCapabilityState.DSP_HARDWARE_AVAILABLE ->
            "DSP hotword capability detected with required voice-interaction privileges."
          HotwordCapabilityState.DEFAULT_ASSISTANT_NOT_SELECTED ->
            "Mousomi AI is not selected as Default Digital Assistant. System corner-swipe/Power-hold triggers and VoiceInteractionService remain inactive until selected."
          HotwordCapabilityState.PERMISSION_REQUIRED ->
            "RECORD_AUDIO permission has not been granted yet. Grant Microphone permission to use push-to-talk or foreground Live Voice."
          HotwordCapabilityState.DSP_HARDWARE_UNSUPPORTED ->
            buildString {
              append("On stock Android ${profile.androidRelease}")
              if (!profile.oneUiVersion.isNullOrBlank()) {
                append(" (Samsung One UI ${profile.oneUiVersion})")
              } else if (profile.isSamsungOneUi) {
                append(" (Samsung One UI)")
              }
              append(
                ", always-on hardware DSP wake-word APIs (CAPTURE_AUDIO_HOTWORD / MANAGE_VOICE_KEYPHRASES) are signature|privileged system permissions unavailable to user-installed apps. Screen-off / closed-app wake-word listening is not supported; use Corner Swipe, Power/Home hold, Quick Tile, or Live Voice."
              )
            }
          HotwordCapabilityState.SOFTWARE_HOTWORD_RESTRICTED ->
            "Continuous background software microphone listening is restricted on Android 12+ and requires a visible foreground microphone indicator. Use foreground Live Voice inside an open Assistant session."
          HotwordCapabilityState.KEYPHRASE_UNENROLLED ->
            "Privileged hotword permissions present, but no wake-word keyphrase model is enrolled for this user."
          HotwordCapabilityState.KEYPHRASE_UNAVAILABLE ->
            "System SpeechRecognizer / SoundTrigger keyphrase service is unavailable on this build."
          HotwordCapabilityState.DETECTOR_START_FAILED ->
            "AlwaysOnHotwordDetector failed to initialize: $lastDetectorFailureReason"
        }

      val recommendedTriggers =
        buildList {
          if (isDefaultAssistant) {
            add("Bottom-corner swipe or Power/Home long-press (instant VoiceInteractionSession, no Draw Over Other Apps permission needed)")
          } else {
            add("Set Mousomi AI as Default Digital Assistant in Android Settings for instant corner-swipe / Power-hold launch")
          }
          add("Quick Settings Tile ('Mousomi AI') for one-tap launch from anywhere")
          if (hasRecordAudioPermission) {
            add("Tap the Rainbow Live orb inside any open session for hands-free foreground voice conversation")
          } else {
            add("Grant Microphone permission for Live Voice conversation and speech dictation")
          }
        }

      return HotwordCapabilityReport(
        state = state,
        runtimeProfile = profile,
        isDefaultAssistant = isDefaultAssistant,
        isVoiceInteractionActiveService = isVoiceInteractionActiveService,
        hasRecordAudioPermission = hasRecordAudioPermission,
        hasCaptureAudioHotwordPermission = hasCaptureAudioHotwordPermission,
        hasManageVoiceKeyphrasesPermission = hasManageVoiceKeyphrasesPermission,
        hasMicrophoneHardware = hasMicrophoneHardware,
        supportsSpeechRecognition = supportsSpeechRecognition,
        supportsOnDeviceRecognition = supportsOnDeviceRecognition,
        supportsDspHotwordForThirdParty = supportsDspForThirdParty,
        supportsForegroundLiveMicInSession = hasMicrophoneHardware && hasRecordAudioPermission,
        reasonSummary = reasonSummary,
        recommendedTriggers = recommendedTriggers,
        checkedAtMs = nowMs,
      )
    }
  }
}
