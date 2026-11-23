package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class VoiceGender(val label: String, val preferredPitch: Float) {
  FEMALE("Female (Default)", 1.0f),
  MALE("Male", 0.96f),
}

@Serializable
enum class ImageGenerationStatus(val label: String) {
  PREPARING_PROMPT("Preparing prompt…"),
  CHECKING_FREE_PROVIDER("Checking free provider…"),
  UPLOADING_REQUEST("Uploading request…"),
  GENERATING("Generating image…"),
  DOWNLOADING_IMAGE("Downloading image…"),
  VALIDATING_RESULT("Validating result…"),
  COMPLETED("Completed"),
  FAILED("Failed"),
  QUOTA_EXHAUSTED("Quota Exhausted"),
  CANCELLED("Cancelled");

  companion object {
    val PREPARING: ImageGenerationStatus get() = PREPARING_PROMPT
    val PROCESSING: ImageGenerationStatus get() = DOWNLOADING_IMAGE
    val FINALIZING: ImageGenerationStatus get() = VALIDATING_RESULT
  }
}

@Serializable
enum class ImageProcessingSource(val badgeLabel: String) {
  CLOUD_AI_GENERATION("Cloud AI Generation"),
  CLOUD_AI_EDITING("Cloud AI Editing"),
  LOCAL_EDITING("Local Editing"),
  LOCAL_TEXT_COMPOSITING("Local Text Compositing"),
}

@Serializable
data class ImageVersionRecord(
  val id: String,
  val rootMediaId: String,
  val parentMediaId: String? = null,
  val conversationId: String,
  val messageId: String,
  val versionNumber: Int,
  val prompt: String,
  val editInstruction: String? = null,
  val filePath: String,
  val mimeType: String = "image/png",
  val width: Int = 0,
  val height: Int = 0,
  val providerModel: String,
  val createdAtMs: Long,
)

@Serializable
data class GeneratedImageResult(
  val mediaId: String,
  val rootMediaId: String,
  val parentMediaId: String? = null,
  val versionNumber: Int = 1,
  val prompt: String,
  val editInstruction: String? = null,
  val filePath: String,
  val mimeType: String = "image/png",
  val width: Int = 0,
  val height: Int = 0,
  val providerModel: String,
  val captionText: String = "",
  val status: ImageGenerationStatus = ImageGenerationStatus.COMPLETED,
  val errorMessage: String? = null,
  val createdAtMs: Long = System.currentTimeMillis(),
  val sourceBadge: String = ImageProcessingSource.CLOUD_AI_GENERATION.badgeLabel,
  val executionSource: ImageProcessingSource = ImageProcessingSource.CLOUD_AI_GENERATION,
  val beforeFilePath: String? = null,
  val hasTransparency: Boolean = false,
)

@Serializable
enum class RichLinkKind(val label: String) {
  PLAY_STORE("Google Play"),
  YOUTUBE("YouTube"),
  INSTAGRAM("Instagram"),
  WHATSAPP("WhatsApp"),
  ANDROID_APP("Android App"),
  WEBSITE("Website"),
}

@Serializable
data class RichLinkMetadata(
  val id: String,
  val kind: RichLinkKind,
  val title: String,
  val subtitle: String,
  val url: String,
  val packageName: String? = null,
  val domain: String? = null,
  val isInstalledApp: Boolean = false,
  val actionLabel: String = "Open",
)

enum class NeonComposerState(val statusLabel: String) {
  IDLE("Ready"),
  FOCUSED("Ask Mousomi"),
  TYPING("Composing"),
  AI_THINKING("Thinking…"),
  IMAGE_GENERATING("Creating image…"),
  VOICE_ACTIVE("Listening…"),
  AI_SPEAKING("Speaking…"),
  ERROR("Attention needed"),
  COMPLETED("Done"),
}

data class ActiveEditImageTarget(
  val mediaId: String,
  val rootMediaId: String,
  val versionNumber: Int,
  val filePath: String? = null,
  val uriString: String? = null,
  val promptHistory: String = "",
)

enum class PermissionState(val label: String) {
  GRANTED("Allowed"),
  NOT_REQUESTED("Not Requested"),
  DENIED("Denied"),
  RESTRICTED("Restricted / Settings"),
  REQUIRES_SETTINGS("Open Settings"),
  NOT_AVAILABLE("Not Available"),
  NOT_APPLICABLE("Not Supported"),
  ZERO_PERMISSION_PICKER("Zero-Permission Picker"),
}

enum class PermissionCategory(val title: String, val group: String) {
  MICROPHONE("Microphone & Voice Capture", "Voice & Live"),
  CAMERA("Camera Capture", "Visual Input"),
  PHOTO_PICKER("Photo & Video Picker", "Media & Files"),
  DOCUMENT_PICKER("Document & File Picker (SAF)", "Media & Files"),
  LOCATION_COARSE("Approximate Location (Optional)", "Context"),
  LOCATION_FINE("Precise Location (Optional)", "Context"),
  NOTIFICATIONS("App Notifications", "Alerts"),
  NOTIFICATION_LISTENER("Notification Reader (Smart Summary)", "Alerts"),
  EXACT_ALARM("Exact Alarms & Clock Scheduling", "Alarms & Timers"),
  SCREEN_CAPTURE("Screen Capture (MediaProjection)", "Screen Intelligence"),
  OVERLAY("Display Over Other Apps (Floating Orb)", "Screen Intelligence"),
  ACCESSIBILITY("Accessibility Workflow Status", "Automation"),
  CONTACTS_PICKER("Contact Picker (Zero-Permission)", "Communication"),
  PHONE_DIAL("Phone Dialer (ACTION_DIAL)", "Communication"),
  BLUETOOTH_AUDIO("Bluetooth Audio & Devices", "Voice & Live"),
  INSTALLED_APPS("Installed Apps Discovery", "Device Control"),
  INTERNET_STATE("Internet, Wi-Fi & Network State", "Core Connectivity"),
  BATTERY_OPTIMIZATION("Battery Optimization & Background", "Performance"),
  NFC_HARDWARE("NFC Hardware Status", "Other Hardware"),
  USB_DEVICES("USB Device Connection", "Other Hardware"),
  DEFAULT_ASSISTANT("Default Digital Assistant Role", "System Assistant"),
}

data class PermissionCategoryItem(
  val category: PermissionCategory,
  val state: PermissionState,
  val manifestPermission: String? = null,
  val whyNeeded: String,
  val dataUsageDisclosure: String,
  val howToManage: String,
  val isRuntimeRequestable: Boolean = false,
  val requiresSpecialSettings: Boolean = false,
  val isOptionalFeature: Boolean = true,
  val isEnabledByDefaultSafe: Boolean = false,
  val relatedFeature: String = category.group,
  val lastCheckedAtMs: Long = System.currentTimeMillis(),
)
