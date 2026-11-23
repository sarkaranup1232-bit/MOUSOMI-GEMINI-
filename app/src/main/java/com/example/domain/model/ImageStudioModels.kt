package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ImageEditFeatureType(val code: String, val title: String, val subtitle: String) {
  TEXT_TO_IMAGE("G0", "Text to Image", "Cloud AI Free-First Generation"),
  PHOTO_ENHANCE("A", "Realistic Photo Enhancement", "Exposure, contrast, warmth & sharpen"),
  PORTRAIT_EDIT("B", "AI Portrait Editing", "Subject cutout + studio bokeh & tone"),
  BACKGROUND_REMOVE_REPLACE("C", "Background Remove & Replace", "Transparent PNG + edge feather + backdrop"),
  OBJECT_REMOVE_REPLACE("D", "Object Remove & Replace", "Mask brush + real cloud inpainting"),
  CREATIVE_AI_EDIT("E", "Creative AI Photo Editing", "Vision-planned cloud image transformation"),
  BENGALI_ENGLISH_POSTER("F", "Bengali & English Poster", "Offline vector-crisp StaticLayout compositor"),
  BATCH_MULTI_PHOTO("G", "Multiple Photo Batch", "Sequential M51-safe multi-photo queue"),
}

@Serializable
data class ImageProviderCapabilities(
  val providerId: String,
  val displayName: String,
  val modelId: String,
  val supportsTextToImage: Boolean = false,
  val supportsImageToImage: Boolean = false,
  val supportsInpaintingMask: Boolean = false,
  val supportsBackgroundRemoval: Boolean = false,
  val supportsPortraitEnhancement: Boolean = false,
  val isFreeTierEligible: Boolean = false,
  val requiresBilling: Boolean = true,
  val maxInputBytes: Long = 8L * 1024L * 1024L,
  val supportedFormats: Set<String> = setOf("image/png", "image/jpeg"),
  val billingNotice: String = "",
)

@Serializable
data class ImageAdjustmentParams(
  val brightness: Float = 0f,     // -1f..1f
  val contrast: Float = 1f,       // 0.5f..1.8f
  val saturation: Float = 1f,     // 0f..2f
  val warmth: Float = 0f,         // -1f..1f
  val sharpen: Float = 0f,        // 0f..1f
  val portraitBokehRadius: Float = 0f, // 0f..24f
  val borderThicknessPx: Int = 0, // 0..48
  val borderColorHex: Long = 0xFFFFFFFFL,
)

@Serializable
enum class BackgroundReplaceStyle(val label: String) {
  TRANSPARENT_PNG("Transparent PNG"),
  PURE_WHITE("Studio White"),
  OBSIDIAN_DARK("Obsidian Studio"),
  SUNSET_GRADIENT("Warm Sunset"),
  ROYAL_TEAL("Royal Teal"),
  CUSTOM_PHOTO("Custom Photo"),
}

@Serializable
enum class PosterAspectRatio(val label: String, val widthPx: Int, val heightPx: Int) {
  SQUARE_1_1("1:1 Square", 1080, 1080),
  PORTRAIT_4_5("4:5 Social", 1080, 1350),
  STORY_9_16("9:16 Story", 1080, 1920),
  LANDSCAPE_16_9("16:9 Wide", 1920, 1080),
}

@Serializable
enum class PosterFontChoice(val label: String, val isBengaliPrimary: Boolean) {
  NOTO_SANS_BENGALI("Noto Sans Bengali", true),
  HIND_SILIGURI("Hind Siliguri Display", true),
  PLAYFAIR_DISPLAY("Playfair Editorial", false),
  SPACE_GROTESK("Space Grotesk Tech", false),
  OUTFIT_CLEAN("Outfit Modern", false),
}

@Serializable
enum class PosterTemplatePreset(val label: String, val startColor: Long, val endColor: Long, val accentColor: Long) {
  EID_FESTIVE("Festive Emerald & Gold", 0xFF063B2B, 0xFF0E694D, 0xFFF5C451),
  POETICS_MIDNIGHT("Midnight Poetic", 0xFF0D132B, 0xFF28194A, 0xFF60EFFF),
  SUNSET_QUOTE("Warm Terracotta Quote", 0xFF4A181D, 0xFF8C3B2B, 0xFFFFD194),
  MINIMAL_PAPER("Clean Cream Editorial", 0xFFF7F2EA, 0xFFEAE0D0, 0xFF1F2937),
}

@Serializable
data class PosterTextLayer(
  val id: String,
  val text: String,
  val fontChoice: PosterFontChoice = PosterFontChoice.NOTO_SANS_BENGALI,
  val fontSizeSp: Float = 44f,
  val textColorArgb: Long = 0xFFFFFFFFL,
  val strokeColorArgb: Long = 0xFF000000L,
  val strokeWidthPx: Float = 0f,
  val shadowRadius: Float = 6f,
  val backdropOpacity: Float = 0.28f,
  val yPositionFraction: Float = 0.45f,
  val isCentered: Boolean = true,
)

@Serializable
data class PosterDesignSpec(
  val aspectRatio: PosterAspectRatio = PosterAspectRatio.SQUARE_1_1,
  val template: PosterTemplatePreset = PosterTemplatePreset.POETICS_MIDNIGHT,
  val backgroundPhotoPath: String? = null,
  val layers: List<PosterTextLayer> = emptyList(),
)

@Serializable
enum class EditExecutionTarget {
  LOCAL_BITMAP,
  LOCAL_PORTRAIT_OR_CUTOUT,
  LOCAL_POSTER_COMPOSITOR,
  CLOUD_AI_EDIT,
  CLOUD_AI_INPAINT_MASK,
  CLOUD_AI_GENERATE,
}

@Serializable
data class EditPlan(
  val executionTarget: EditExecutionTarget,
  val featureType: ImageEditFeatureType,
  val summary: String,
  val suggestedPrompt: String = "",
  val requiresMask: Boolean = false,
  val suggestedAdjustments: ImageAdjustmentParams = ImageAdjustmentParams(),
  val extractedPosterText: String = "",
)

@Serializable
data class BatchImageItemState(
  val itemId: String,
  val sourceUri: String,
  val displayName: String,
  val status: ImageGenerationStatus = ImageGenerationStatus.PREPARING_PROMPT,
  val outputFilePath: String? = null,
  val sourceBadge: String = ImageProcessingSource.LOCAL_EDITING.badgeLabel,
  val errorMessage: String? = null,
  val isSelected: Boolean = true,
)

data class ModelSubstitutionPrompt(
  val title: String,
  val message: String,
  val requestedModelId: String,
  val suggestedModelId: String,
  val suggestedModelDisplayName: String,
  val pendingPrompt: String,
  val pendingMode: OperatingMode,
)
