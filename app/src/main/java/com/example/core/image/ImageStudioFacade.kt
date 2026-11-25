package com.example.core.image

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.ai.image.BatchImageProcessor
import com.example.ai.image.CompositeImageGenerationProvider
import com.example.ai.image.ImageGenerationEvent
import com.example.ai.image.ImageGenerationRequest
import com.example.ai.image.ImageStorageManager
import com.example.ai.image.PollinationsAspectRatio
import com.example.ai.image.PollinationsImageService
import com.example.domain.model.AppSettings
import com.example.domain.model.EditExecutionTarget
import com.example.domain.model.EditPlan
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageEditFeatureType
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.ImageProcessingSource
import com.example.domain.model.ProcessedImagePayload
import com.example.ui.model.ImageStudioUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.FlowCollector

/**
 * Unified orchestration facade for Mousomi Creative Image Studio (all 8 tools):
 * G0: Text-to-Image (Pollinations.ai Flux + Cloudflare + Gemini)
 * A: Photo Enhance (ColorMatrix + Sharpen + Transform)
 * B: AI Portrait Edit (ML Kit Subject Segmentation + Studio Bokeh)
 * C: Background Remove & Replace (Transparent PNG + Edge Feather + Backdrops)
 * D: Object Remove & Replace (Mask Brush + Cloud Inpainting)
 * E: Creative AI Photo Editing (VisionEditPlanner + Layer A/B)
 * F: Bengali & English Poster (Offline StaticLayout + Bundled TTFs)
 * G: Multiple Photo Batch & 2x2 Collage (BatchImageProcessor)
 */
@Singleton
class ImageStudioFacade @Inject constructor(
  @ApplicationContext private val context: Context,
  val pollinationsImageService: PollinationsImageService,
  val imageGenerationProvider: CompositeImageGenerationProvider,
  val localEditor: LocalPhotoEditorEngine,
  val posterCompositor: BengaliPosterCompositor,
  val visionEditPlanner: VisionEditPlanner,
  val batchProcessor: BatchImageProcessor,
  val storageManager: ImageStorageManager,
  val imageProcessor: ImageProcessor,
) {

  fun resolveAspectRatio(label: String): PollinationsAspectRatio =
    PollinationsAspectRatio.entries.firstOrNull { it.label == label }
      ?: PollinationsAspectRatio.SQUARE_1_1

  suspend fun enhancePrompt(rawPrompt: String, settings: AppSettings): String =
    pollinationsImageService.enhancePrompt(rawPrompt, settings)

  suspend fun generateTextToImage(
    state: ImageStudioUiState,
    settings: AppSettings,
    overrideSeed: Long? = null,
    onStage: suspend (ImageGenerationStatus) -> Unit = {},
  ): Result<GeneratedImageResult> {
    val ratio = resolveAspectRatio(state.aspectRatioLabel)
    val parsedSeed = overrideSeed ?: state.seedText.trim().toLongOrNull()
    var result: GeneratedImageResult? = null
    var failure: String? = null

    imageGenerationProvider.generateOrEdit(
      ImageGenerationRequest(
        prompt = state.promptInput.trim().ifBlank { "A cinematic neon-lit studio artwork" },
        settings = settings,
        featureType = ImageEditFeatureType.TEXT_TO_IMAGE,
        aspectWidth = ratio.widthPx,
        aspectHeight = ratio.heightPx,
        seed = parsedSeed,
        enhancePrompt = state.enhancePromptEnabled,
      )
    ).collect { event ->
      when (event) {
        is ImageGenerationEvent.Stage -> onStage(event.status)
        is ImageGenerationEvent.Success -> result = event.result
        is ImageGenerationEvent.Failure -> failure = event.message
      }
    }

    return result?.let { Result.success(it) }
      ?: Result.failure(IllegalStateException(failure ?: "Image generation did not complete."))
  }

  suspend fun runPhotoEnhance(state: ImageStudioUiState): Result<File> {
    val src = state.sourceUriOrPath
      ?: return Result.failure(IllegalStateException("Select a source photo first."))
    return localEditor.applyAdjustments(
      sourceUriOrPath = src,
      params = state.adjustments,
      rotateDegrees = state.rotateDegrees,
      flipHorizontal = state.flipHorizontal,
    )
  }

  suspend fun runSubjectIsolation(state: ImageStudioUiState): Result<LocalCutoutResult> {
    val src = state.sourceUriOrPath
      ?: return Result.failure(IllegalStateException("Select a portrait or subject photo first."))
    val bokeh =
      if (state.activeFeature == ImageEditFeatureType.PORTRAIT_EDIT) state.portraitBokehAmount
      else 0f
    return localEditor.processSubjectIsolation(
      sourceUriOrPath = src,
      style = state.backgroundStyle,
      edgeFeatherPx = state.edgeFeatherPx,
      portraitBokehAmount = bokeh,
      customBackgroundUriOrPath = state.customBackdropUri,
    )
  }

  suspend fun runObjectMaskInpaint(
    state: ImageStudioUiState,
    settings: AppSettings,
    onStage: suspend (ImageGenerationStatus) -> Unit = {},
  ): Result<GeneratedImageResult> {
    val srcUri = state.sourceUriOrPath
      ?: return Result.failure(IllegalStateException("Select a photo first."))
    val srcPayload = decodeSourcePayload(srcUri)
      ?: return Result.failure(IllegalStateException("Unable to read selected photo."))
    val maskPayload =
      localEditor.renderMaskPngPayload(
        brushPaths = state.maskPaths,
        brushRadiusNorm = state.maskBrushRadiusNorm,
        targetWidth = srcPayload.width,
        targetHeight = srcPayload.height,
      ).getOrElse { return Result.failure(it) }

    var finalResult: GeneratedImageResult? = null
    var failure: String? = null
    imageGenerationProvider.generateOrEdit(
      ImageGenerationRequest(
        prompt = state.promptInput.trim().ifBlank { "Remove painted object cleanly and reconstruct background" },
        settings = settings,
        sourceImage = srcPayload,
        regionOverlayImage = maskPayload,
        editInstruction = state.promptInput.trim(),
        requiresMaskInpainting = true,
        featureType = ImageEditFeatureType.OBJECT_REMOVE_REPLACE,
      )
    ).collect { event ->
      when (event) {
        is ImageGenerationEvent.Stage -> onStage(event.status)
        is ImageGenerationEvent.Success -> finalResult = event.result
        is ImageGenerationEvent.Failure -> failure = event.message
      }
    }
    return finalResult?.let { Result.success(it.copy(beforeFilePath = srcUri)) }
      ?: Result.failure(IllegalStateException(failure ?: "Object removal failed."))
  }

  suspend fun runCreativeEditOrPlan(
    state: ImageStudioUiState,
    settings: AppSettings,
    onStage: suspend (ImageGenerationStatus) -> Unit = {},
  ): Pair<EditPlan, Result<GeneratedImageResult>> {
    val srcPayload = state.sourceUriOrPath?.let { decodeSourcePayload(it) }
    val plan = visionEditPlanner.planEdit(state.promptInput, srcPayload, settings)

    return when (plan.executionTarget) {
      EditExecutionTarget.LOCAL_BITMAP -> {
        val localState = state.copy(adjustments = plan.suggestedAdjustments)
        val out = runPhotoEnhance(localState).map { file ->
          GeneratedImageResult(
            mediaId = "edit_${UUID.randomUUID()}",
            rootMediaId = "edit_${UUID.randomUUID()}",
            prompt = state.promptInput,
            filePath = file.absolutePath,
            beforeFilePath = state.sourceUriOrPath,
            providerModel = "on-device:ColorMatrix",
            sourceBadge = ImageProcessingSource.LOCAL_EDITING.badgeLabel,
            executionSource = ImageProcessingSource.LOCAL_EDITING,
          )
        }
        plan to out
      }
      EditExecutionTarget.LOCAL_PORTRAIT_OR_CUTOUT -> {
        val out = runSubjectIsolation(state).map { res ->
          GeneratedImageResult(
            mediaId = "cut_${UUID.randomUUID()}",
            rootMediaId = "cut_${UUID.randomUUID()}",
            prompt = state.promptInput,
            filePath = res.outputFilePath,
            beforeFilePath = state.sourceUriOrPath,
            width = res.width,
            height = res.height,
            hasTransparency = res.hasTransparency,
            providerModel = if (res.usedMlKitSegmentation) "mlkit:subject-segmentation" else "local:cutout",
            sourceBadge = ImageProcessingSource.LOCAL_EDITING.badgeLabel,
            executionSource = ImageProcessingSource.LOCAL_EDITING,
          )
        }
        plan to out
      }
      EditExecutionTarget.LOCAL_POSTER_COMPOSITOR -> {
        val updatedLayers =
          if (plan.extractedPosterText.isNotBlank() && state.posterSpec.layers.isNotEmpty()) {
            state.posterSpec.layers.mapIndexed { i, layer ->
              if (i == 0) layer.copy(text = plan.extractedPosterText) else layer
            }
          } else state.posterSpec.layers
        val out = posterCompositor.renderPoster(state.posterSpec.copy(layers = updatedLayers)).map { file ->
          GeneratedImageResult(
            mediaId = "poster_${UUID.randomUUID()}",
            rootMediaId = "poster_${UUID.randomUUID()}",
            prompt = state.promptInput,
            filePath = file.absolutePath,
            providerModel = "offline:StaticLayout-TTF",
            sourceBadge = ImageProcessingSource.LOCAL_TEXT_COMPOSITING.badgeLabel,
            executionSource = ImageProcessingSource.LOCAL_TEXT_COMPOSITING,
          )
        }
        plan to out
      }
      EditExecutionTarget.CLOUD_AI_GENERATE -> {
        plan to generateTextToImage(state, settings, onStage = onStage)
      }
      EditExecutionTarget.CLOUD_AI_INPAINT_MASK -> {
        if (state.maskPaths.isEmpty()) {
          plan to Result.failure(
            IllegalStateException("Paint a mask over the object on the Object Remove tab first.")
          )
        } else {
          plan to runObjectMaskInpaint(state, settings, onStage)
        }
      }
      EditExecutionTarget.CLOUD_AI_EDIT -> {
        var finalRes: GeneratedImageResult? = null
        var failure: String? = null
        imageGenerationProvider.generateOrEdit(
          ImageGenerationRequest(
            prompt = plan.suggestedPrompt.ifBlank { state.promptInput },
            settings = settings,
            sourceImage = srcPayload,
            editInstruction = state.promptInput,
            featureType = ImageEditFeatureType.CREATIVE_AI_EDIT,
          )
        ).collect { event ->
          when (event) {
            is ImageGenerationEvent.Stage -> onStage(event.status)
            is ImageGenerationEvent.Success -> finalRes = event.result
            is ImageGenerationEvent.Failure -> failure = event.message
          }
        }
        val res =
          finalRes?.let { Result.success(it.copy(beforeFilePath = state.sourceUriOrPath)) }
            ?: Result.failure(IllegalStateException(failure ?: "Creative AI edit failed."))
        plan to res
      }
    }
  }

  suspend fun renderPoster(state: ImageStudioUiState): Result<File> =
    posterCompositor.renderPoster(state.posterSpec)

  suspend fun createGridCollage(uris: List<String>): Result<File> =
    localEditor.createGridCollage(uris)

  suspend fun saveToGallery(
    filePath: String,
    title: String,
    hasTransparency: Boolean,
  ): Result<String> =
    storageManager.saveToGallery(filePath, title, preserveTransparency = hasTransparency)
      .map { "Saved to Pictures/Mousomi AI" }

  fun getShareableUri(filePath: String): Uri =
    storageManager.getShareableContentUri(filePath)

  suspend fun decodeSourcePayload(uriOrPath: String): ProcessedImagePayload? {
    val file =
      when {
        uriOrPath.startsWith("file://") -> Uri.parse(uriOrPath).path?.let(::File)
        !uriOrPath.startsWith("content://") -> File(uriOrPath)
        else -> null
      }
    if (file != null && file.exists()) {
      return runCatching {
        val bytes = file.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        ProcessedImagePayload(
          uriString = Uri.fromFile(file).toString(),
          mimeType = if (file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true)) "image/jpeg" else "image/png",
          base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
          width = bounds.outWidth.coerceAtLeast(1024),
          height = bounds.outHeight.coerceAtLeast(1024),
          byteSize = bytes.size,
        )
      }.getOrNull()
    }
    return imageProcessor.processUri(uriOrPath).getOrNull()
  }
}
