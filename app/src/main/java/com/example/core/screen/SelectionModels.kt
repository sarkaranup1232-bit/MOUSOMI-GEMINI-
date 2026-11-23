package com.example.core.screen

import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import kotlinx.serialization.Serializable

@Serializable
enum class SelectionToolMode(val label: String) {
  SMART_LOOP("Smart"),
  FREEHAND("Draw"),
  CIRCLE("Circle"),
  RECTANGLE("Box"),
}

enum class SelectionStage(val label: String) {
  IDLE("Idle"),
  AWAITING_CONSENT("Waiting for Consent"),
  READY_TO_DRAW("Draw or circle anything on screen"),
  DRAWING("Selecting region…"),
  SNAPPED_HIGHLIGHT("Region selected"),
  CAPTURING_AND_CROPPING("Capturing & cropping…"),
  ATTACHED("Attached to prompt"),
  EDITING("Editing crop"),
  ERROR("Attention needed"),
}

enum class DetectedRegionShape {
  FREEHAND_LOOP,
  CIRCLE,
  OVAL,
  RECTANGLE,
}

enum class CropHandle {
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  LEFT,
  TOP,
  RIGHT,
  BOTTOM,
  MOVE,
}

enum class CropAspectPreset(val label: String, val ratio: Float?) {
  FREE("Free", null),
  SQUARE("1:1", 1f),
  FOUR_THREE("4:3", 4f / 3f),
  SIXTEEN_NINE("16:9", 16f / 9f),
}

data class SelectionGeometryResult(
  val boundingBox: NormBoundingBox,
  val detectedShape: DetectedRegionShape,
  val simplifiedContour: List<NormPoint> = emptyList(),
  val cornerRadiusFraction: Float = 0.08f,
  val confidence: Float = 1.0f,
  val isClosedLoop: Boolean = true,
)

@Serializable
data class ScreenCaptureMetadata(
  val bitmapWidth: Int,
  val bitmapHeight: Int,
  val overlayWidthPx: Int,
  val overlayHeightPx: Int,
  val overlayOffsetX: Int = 0,
  val overlayOffsetY: Int = 0,
  val statusBarInsetPx: Int = 0,
  val navBarInsetPx: Int = 0,
  val isPartialAppProjection: Boolean = false,
  val density: Float = 1f,
)

data class AttachedVisualItem(
  val id: String,
  val croppedFilePath: String,
  val croppedUriString: String,
  val sourceFrameFilePath: String? = null,
  val captureMetadata: ScreenCaptureMetadata? = null,
  val normCropRect: NormBoundingBox = NormBoundingBox(0f, 0f, 1f, 1f),
  val rotationDegrees: Int = 0,
  val detectedShape: DetectedRegionShape = DetectedRegionShape.RECTANGLE,
  val aspectPreset: CropAspectPreset = CropAspectPreset.FREE,
  val isSettling: Boolean = false,
  val createdAtMs: Long = System.currentTimeMillis(),
)

data class ImageEditState(
  val attachmentId: String,
  val sourceFrameFilePath: String,
  val originalNormCropRect: NormBoundingBox,
  val currentNormCropRect: NormBoundingBox,
  val rotationDegrees: Int = 0,
  val aspectPreset: CropAspectPreset = CropAspectPreset.FREE,
  val captureMetadata: ScreenCaptureMetadata? = null,
)
