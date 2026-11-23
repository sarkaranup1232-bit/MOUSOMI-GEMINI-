package com.example.core.image

import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.dto.GeminiContent
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerationConfig
import com.example.ai.provider.dto.GeminiInlineData
import com.example.ai.provider.dto.GeminiPart
import com.example.domain.model.AppSettings
import com.example.domain.model.EditExecutionTarget
import com.example.domain.model.EditPlan
import com.example.domain.model.ImageAdjustmentParams
import com.example.domain.model.ImageEditFeatureType
import com.example.domain.model.ProcessedImagePayload
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Layer C: Vision Edit Planner.
 * Uses Gemini Vision (when a Gemini API key is present) or fast deterministic local intent analysis
 * to classify user edit requests into structured JSON EditPlan directives targeting Layer A (Cloud)
 * vs Layer B (On-Device Bitmap / Cutout / Bengali Poster Compositor).
 */
@Singleton
class VisionEditPlanner @Inject constructor(
  private val geminiApiService: GeminiApiService,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  suspend fun planEdit(
    instruction: String,
    sourceImage: ProcessedImagePayload?,
    settings: AppSettings,
  ): EditPlan {
    val cleanInstruction = instruction.trim()
    val apiKey = settings.geminiApiKey.trim()

    if (apiKey.isNotBlank() && cleanInstruction.isNotBlank()) {
      val visionResult =
        runCatching {
          val parts = mutableListOf<GeminiPart>()
          parts +=
            GeminiPart(
              text =
                """
                Classify this image editing instruction into strict JSON matching EditPlan schema.
                Instruction: "$cleanInstruction"
                Rules:
                - Use LOCAL_POSTER_COMPOSITOR for adding Bengali or English text, quotes, banners, or posters.
                - Use LOCAL_PORTRAIT_OR_CUTOUT for background removal, cutout PNG, or portrait blur.
                - Use LOCAL_BITMAP for brightness, contrast, saturation, warmth, sharpen, or framing.
                - Use CLOUD_AI_INPAINT_MASK for removing or replacing specific painted objects.
                - Use CLOUD_AI_EDIT or CLOUD_AI_GENERATE for artistic style changes or new scene synthesis.
                Return ONLY raw JSON without markdown fences:
                {"executionTarget":"...","featureType":"...","summary":"...","suggestedPrompt":"...","requiresMask":false,"extractedPosterText":""}
                """.trimIndent()
            )
          if (sourceImage != null) {
            parts +=
              GeminiPart(
                inlineData = GeminiInlineData(mimeType = sourceImage.mimeType, data = sourceImage.base64Data)
              )
          }
          val resp =
            geminiApiService.generateContent(
              model = settings.hydrogenBackingModelId.ifBlank { "gemini-2.5-flash" },
              apiKey = apiKey,
              request =
                GeminiGenerateRequest(
                  contents = listOf(GeminiContent(role = "user", parts = parts)),
                  generationConfig =
                    GeminiGenerationConfig(
                      temperature = 0.15,
                      responseMimeType = "application/json",
                    ),
                ),
            )
          val rawText =
            resp.body()
              ?.candidates
              ?.firstOrNull()
              ?.content
              ?.parts
              ?.mapNotNull { it.text }
              ?.joinToString("")
              ?.trim()
              .orEmpty()
              .removePrefix("```json")
              .removePrefix("```")
              .removeSuffix("```")
              .trim()
          if (rawText.isNotBlank()) json.decodeFromString<EditPlan>(rawText) else null
        }.getOrNull()

      if (visionResult != null) return visionResult
    }

    return planDeterministically(cleanInstruction, hasSourceImage = sourceImage != null)
  }

  fun planDeterministically(instruction: String, hasSourceImage: Boolean): EditPlan {
    val q = instruction.lowercase()
    val hasBengaliScript = instruction.any { it in '\u0980'..'\u09FF' }

    if (hasBengaliScript || "poster" in q || "add text" in q || "quote" in q || "typography" in q || "পোস্টার" in q || "লিখ" in q) {
      return EditPlan(
        executionTarget = EditExecutionTarget.LOCAL_POSTER_COMPOSITOR,
        featureType = ImageEditFeatureType.BENGALI_ENGLISH_POSTER,
        summary = "Offline Bengali & English StaticLayout poster compositor selected.",
        extractedPosterText = instruction,
      )
    }

    if ("remove object" in q || "erase" in q || "inpaint" in q || "replace the" in q || "object remove" in q) {
      return EditPlan(
        executionTarget = EditExecutionTarget.CLOUD_AI_INPAINT_MASK,
        featureType = ImageEditFeatureType.OBJECT_REMOVE_REPLACE,
        summary = "Mask brush + real cloud inpainting required for object removal/replacement.",
        suggestedPrompt = instruction,
        requiresMask = true,
      )
    }

    if ("remove background" in q || "cutout" in q || "transparent" in q || "change background" in q || "portrait" in q || "bokeh" in q || "blur background" in q) {
      val isPortrait = "portrait" in q || "bokeh" in q || "blur background" in q
      return EditPlan(
        executionTarget = EditExecutionTarget.LOCAL_PORTRAIT_OR_CUTOUT,
        featureType = if (isPortrait) ImageEditFeatureType.PORTRAIT_EDIT else ImageEditFeatureType.BACKGROUND_REMOVE_REPLACE,
        summary = "On-device ML Kit Subject Segmentation & feathering selected.",
      )
    }

    if (hasSourceImage && ("enhance" in q || "bright" in q || "contrast" in q || "sharp" in q || "warm" in q || "saturation" in q)) {
      return EditPlan(
        executionTarget = EditExecutionTarget.LOCAL_BITMAP,
        featureType = ImageEditFeatureType.PHOTO_ENHANCE,
        summary = "Local non-destructive enhancement preset computed for Galaxy M51.",
        suggestedAdjustments =
          ImageAdjustmentParams(
            brightness = if ("bright" in q) 0.14f else 0.04f,
            contrast = 1.14f,
            saturation = 1.12f,
            warmth = if ("warm" in q) 0.22f else 0.05f,
            sharpen = 0.35f,
          ),
      )
    }

    return if (hasSourceImage) {
      EditPlan(
        executionTarget = EditExecutionTarget.CLOUD_AI_EDIT,
        featureType = ImageEditFeatureType.CREATIVE_AI_EDIT,
        summary = "Cloud AI creative photo editing selected.",
        suggestedPrompt = instruction,
      )
    } else {
      EditPlan(
        executionTarget = EditExecutionTarget.CLOUD_AI_GENERATE,
        featureType = ImageEditFeatureType.TEXT_TO_IMAGE,
        summary = "Cloud AI text-to-image generation selected.",
        suggestedPrompt = instruction,
      )
    }
  }
}
