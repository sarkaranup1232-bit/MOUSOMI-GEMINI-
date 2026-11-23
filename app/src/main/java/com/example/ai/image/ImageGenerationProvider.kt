package com.example.ai.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.ai.firebase.FirebaseAiLogicService
import com.example.ai.provider.api.CloudflareJsonImageEnvelope
import com.example.ai.provider.api.CloudflareWorkersAiService
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.api.ProxyApiService
import com.example.ai.provider.dto.GeminiContent
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerationConfig
import com.example.ai.provider.dto.GeminiInlineData
import com.example.ai.provider.dto.GeminiPart
import com.example.ai.provider.dto.ProxyImageDto
import com.example.ai.provider.dto.ProxyImageGenerationRequest
import com.example.core.network.AiProviderException
import com.example.core.network.RetryPolicy
import com.example.core.security.SecurityValidator
import com.example.data.local.db.ImageAssetEntity
import com.example.data.local.db.ImageStudioDao
import com.example.data.local.db.ImageTaskEntity
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageEditFeatureType
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.ImageProcessingSource
import com.example.domain.model.ImageProviderCapabilities
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.VerifiedModelCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class ImageGenerationRequest(
  val prompt: String,
  val settings: AppSettings,
  val sourceImage: ProcessedImagePayload? = null,
  val regionOverlayImage: ProcessedImagePayload? = null,
  val parentMediaId: String? = null,
  val rootMediaId: String? = null,
  val nextVersionNumber: Int = 1,
  val editInstruction: String? = null,
  val requiresMaskInpainting: Boolean = false,
  val featureType: ImageEditFeatureType = ImageEditFeatureType.TEXT_TO_IMAGE,
  val conversationId: String = "studio",
)

sealed interface ImageGenerationEvent {
  data class Stage(val status: ImageGenerationStatus) : ImageGenerationEvent
  data class Success(val result: GeneratedImageResult) : ImageGenerationEvent
  data class Failure(
    val message: String,
    val recoverable: Boolean = true,
    val terminalStatus: ImageGenerationStatus = ImageGenerationStatus.FAILED,
  ) : ImageGenerationEvent
}

interface ImageGenerationProvider {
  fun generateOrEdit(request: ImageGenerationRequest): Flow<ImageGenerationEvent>
}

object ImageProviderCatalog {
  const val CLOUDFLARE_FLUX_SCHNELL = "@cf/black-forest-labs/flux-1-schnell"
  const val CLOUDFLARE_SD_INPAINTING = "@cf/runwayml/stable-diffusion-v1-5-inpainting"
  const val CLOUDFLARE_SD_IMG2IMG = "@cf/runwayml/stable-diffusion-v1-5-img2img"

  fun getCapabilitiesCatalog(settings: AppSettings): List<ImageProviderCapabilities> =
    listOf(
      ImageProviderCapabilities(
        providerId = "cloudflare_flux",
        displayName = "Cloudflare Workers AI (Free-Tier)",
        modelId = CLOUDFLARE_FLUX_SCHNELL,
        supportsTextToImage = true,
        supportsImageToImage = true,
        supportsInpaintingMask = true,
        supportsBackgroundRemoval = false,
        supportsPortraitEnhancement = false,
        isFreeTierEligible = true,
        requiresBilling = false,
        maxInputBytes = 4L * 1024L * 1024L,
        supportedFormats = setOf("image/png", "image/jpeg"),
        billingNotice = "Free-tier Neurons daily allowance via user Cloudflare token.",
      ),
      ImageProviderCapabilities(
        providerId = "gemini_image_studio",
        displayName = "Google Gemini Image Studio",
        modelId = settings.imageModelId.ifBlank { VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID },
        supportsTextToImage = true,
        supportsImageToImage = true,
        supportsInpaintingMask = true,
        supportsBackgroundRemoval = true,
        supportsPortraitEnhancement = true,
        isFreeTierEligible = false,
        requiresBilling = true,
        maxInputBytes = 8L * 1024L * 1024L,
        supportedFormats = setOf("image/png", "image/jpeg", "image/webp"),
        billingNotice =
          if (settings.imageModelId.contains("3.1-flash-image")) {
            "gemini-3.1-flash-image is deprecated; Nano Banana / AI Studio paid quota may apply."
          } else {
            "Requires Firebase Blaze plan or AI Studio billing-enabled Gemini API key."
          },
      ),
      ImageProviderCapabilities(
        providerId = "https_proxy",
        displayName = "Configured HTTPS Proxy",
        modelId = settings.imageModelId.ifBlank { VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID },
        supportsTextToImage = true,
        supportsImageToImage = true,
        supportsInpaintingMask = true,
        supportsBackgroundRemoval = true,
        supportsPortraitEnhancement = true,
        isFreeTierEligible = true,
        requiresBilling = false,
        maxInputBytes = 8L * 1024L * 1024L,
        supportedFormats = setOf("image/png", "image/jpeg", "image/webp"),
        billingNotice = "Routed via user-configured HTTPS proxy.",
      ),
    )
}

@Singleton
class ImageStorageManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val outputDir: File by lazy {
    File(context.filesDir, "mousomi_generated_images").apply { mkdirs() }
  }

  suspend fun saveGeneratedBytes(
    mediaId: String,
    bytes: ByteArray,
    mimeTypeHint: String = "image/png",
  ): SavedImageInfo = withContext(Dispatchers.IO) {
    val ext = when {
      mimeTypeHint.contains("jpeg", ignoreCase = true) || mimeTypeHint.contains("jpg", ignoreCase = true) -> "jpg"
      mimeTypeHint.contains("webp", ignoreCase = true) -> "webp"
      else -> "png"
    }
    val file = File(outputDir, "$mediaId.$ext")
    file.writeBytes(bytes)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      file.delete()
      throw IOException("Provider returned invalid or corrupted image bytes.")
    }
    val hasAlpha = mimeTypeHint.contains("png", ignoreCase = true)
    SavedImageInfo(
      filePath = file.absolutePath,
      width = bounds.outWidth,
      height = bounds.outHeight,
      mimeType = mimeTypeHint,
      fileSizeBytes = file.length(),
      hasTransparency = hasAlpha,
    )
  }

  suspend fun saveGeneratedBase64(
    mediaId: String,
    base64Data: String,
    mimeType: String,
  ): SavedImageInfo = withContext(Dispatchers.IO) {
    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
    saveGeneratedBytes(mediaId, bytes, mimeType)
  }

  suspend fun saveBitmap(
    mediaId: String,
    bitmap: Bitmap,
    asJpeg: Boolean = false,
    quality: Int = 94,
  ): SavedImageInfo = withContext(Dispatchers.IO) {
    val ext = if (asJpeg && !bitmap.hasAlpha()) "jpg" else "png"
    val format = if (ext == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
    val mime = if (ext == "jpg") "image/jpeg" else "image/png"
    val file = File(outputDir, "$mediaId.$ext")
    file.outputStream().use { out ->
      bitmap.compress(format, quality, out)
    }
    SavedImageInfo(
      filePath = file.absolutePath,
      width = bitmap.width.coerceAtLeast(0),
      height = bitmap.height.coerceAtLeast(0),
      mimeType = mime,
      fileSizeBytes = file.length(),
      hasTransparency = bitmap.hasAlpha() && ext == "png",
    )
  }

  fun getShareableContentUri(filePath: String): Uri {
    val file = File(filePath)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  suspend fun saveToGallery(
    filePath: String,
    displayTitle: String,
    preserveTransparency: Boolean = false,
  ): Result<Uri> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
          throw IOException("Generated image file no longer exists on disk.")
        }
        val isJpg = !preserveTransparency &&
          (filePath.endsWith(".jpg", ignoreCase = true) || filePath.endsWith(".jpeg", ignoreCase = true))
        val mime = if (isJpg) "image/jpeg" else "image/png"
        val ext = if (isJpg) "jpg" else "png"
        val cleanName =
          displayTitle
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(36)
            .ifBlank { "Mousomi_Art" } + "_${System.currentTimeMillis()}.$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Mousomi AI")
            put(MediaStore.Images.Media.IS_PENDING, 1)
          }
          val resolver = context.contentResolver
          val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
              ?: throw IOException("Unable to create MediaStore entry.")
          resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
          } ?: throw IOException("Unable to open gallery output stream.")
          values.clear()
          values.put(MediaStore.Images.Media.IS_PENDING, 0)
          resolver.update(uri, values, null, null)
          uri
        } else {
          getShareableContentUri(filePath)
        }
      }
    }

  data class SavedImageInfo(
    val filePath: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val fileSizeBytes: Long = 0L,
    val hasTransparency: Boolean = false,
  )
}

@Singleton
class CompositeImageGenerationProvider @Inject constructor(
  private val geminiApiService: GeminiApiService,
  private val proxyApiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
  private val storageManager: ImageStorageManager,
  private val firebaseAiLogicService: FirebaseAiLogicService? = null,
  private val cloudflareWorkersAiService: CloudflareWorkersAiService? = null,
  private val imageStudioDao: ImageStudioDao? = null,
) : ImageGenerationProvider {

  private val json = Json { ignoreUnknownKeys = true }

  override fun generateOrEdit(request: ImageGenerationRequest): Flow<ImageGenerationEvent> = flow {
    emit(ImageGenerationEvent.Stage(ImageGenerationStatus.PREPARING_PROMPT))

    val settings = request.settings
    val modelId = settings.imageModelId.trim().ifBlank { VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID }
    val useProxy = settings.connectionMode == ConnectionMode.BACKEND_PROXY
    val sourceKind =
      if (request.sourceImage != null) ImageProcessingSource.CLOUD_AI_EDITING
      else ImageProcessingSource.CLOUD_AI_GENERATION

    if (request.requiresMaskInpainting && (request.sourceImage == null || request.regionOverlayImage == null)) {
      emit(
        ImageGenerationEvent.Failure(
          message = "Object Remove & Replace requires both a photo and a painted brush mask.",
          terminalStatus = ImageGenerationStatus.FAILED,
        )
      )
      return@flow
    }

    // Validate payload budget (Samsung Galaxy M51 upload guard)
    val totalInputBytes =
      (request.sourceImage?.byteSize ?: 0) + (request.regionOverlayImage?.byteSize ?: 0)
    if (totalInputBytes > 8 * 1024 * 1024) {
      emit(
        ImageGenerationEvent.Failure(
          message = "Source image payload exceeds 8 MB safe limit. Resize or compress before uploading.",
          terminalStatus = ImageGenerationStatus.FAILED,
        )
      )
      return@flow
    }

    if (useProxy) {
      val validatedProxy = securityValidator.validateHttpsUrl(settings.proxyBaseUrl)
      if (validatedProxy.isFailure) {
        emit(
          ImageGenerationEvent.Failure(
            validatedProxy.exceptionOrNull()?.message
              ?: "Configure a valid HTTPS Backend Proxy URL in Settings for image generation.",
          )
        )
        return@flow
      }
      executeViaProxy(request, modelId, validatedProxy.getOrThrow(), sourceKind, this)
      return@flow
    }

    emit(ImageGenerationEvent.Stage(ImageGenerationStatus.CHECKING_FREE_PROVIDER))

    // Layer A Step 1: Free-Tier First -> Cloudflare Workers AI (when configured)
    val hasCloudflare =
      cloudflareWorkersAiService != null &&
        settings.cloudflareAccountId.isNotBlank() &&
        settings.cloudflareApiToken.isNotBlank()

    if (hasCloudflare) {
      val cfSuccess = executeViaCloudflare(request, settings, sourceKind, this)
      if (cfSuccess) return@flow
      // Do NOT silently fall back to billing-required Gemini when Strict Free-Only is enabled without approval
      if (settings.strictFreeImageOnly && !settings.allowPaidImageFallbackApproved) {
        return@flow
      }
    }

    val key = settings.geminiApiKey.trim()
    val hasFirebaseAi = firebaseAiLogicService?.isAvailable() == true

    // Strict Free-First Gate check: Firebase AI Logic Nano Banana requires Blaze billing; block silent paid invocation unless approved or user explicitly provided AI Studio key
    if (key.isEmpty() && settings.strictFreeImageOnly && !settings.allowPaidImageFallbackApproved) {
      val reason =
        if (!hasCloudflare) {
          "Strict Free-Only mode is enabled. Add free-tier Cloudflare Workers AI credentials in Settings -> Connections, enter a Gemini AI Studio API key, or allow paid fallback in Settings."
        } else {
          "Free-tier provider failed and paid fallback is disabled by Strict Free-Only policy."
        }
      emit(
        ImageGenerationEvent.Failure(
          message = reason,
          terminalStatus = ImageGenerationStatus.QUOTA_EXHAUSTED,
        )
      )
      return@flow
    }

    if (key.isNotEmpty()) {
      executeViaGemini(request, modelId, key, sourceKind, this)
    } else if (hasFirebaseAi && firebaseAiLogicService != null) {
      executeViaFirebaseAiLogic(request, modelId, firebaseAiLogicService, sourceKind, this)
    } else {
      emit(
        ImageGenerationEvent.Failure(
          "No active cloud image provider configured. Add Cloudflare Workers AI or a Gemini API key in Settings."
        )
      )
    }
  }

  private suspend fun executeViaCloudflare(
    request: ImageGenerationRequest,
    settings: AppSettings,
    sourceKind: ImageProcessingSource,
    collector: FlowCollector<ImageGenerationEvent>,
  ): Boolean {
    val cfService = cloudflareWorkersAiService ?: return false
    val cfModelId =
      when {
        request.requiresMaskInpainting -> ImageProviderCatalog.CLOUDFLARE_SD_INPAINTING
        request.sourceImage != null -> ImageProviderCatalog.CLOUDFLARE_SD_IMG2IMG
        else -> ImageProviderCatalog.CLOUDFLARE_FLUX_SCHNELL
      }

    return try {
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.UPLOADING_REQUEST))
      val payloadJson =
        buildJsonObject {
          put("prompt", request.prompt)
          if (cfModelId == ImageProviderCatalog.CLOUDFLARE_FLUX_SCHNELL) {
            put("num_steps", 4)
          }
          request.sourceImage?.let { src ->
            val srcBytes = Base64.decode(src.base64Data, Base64.DEFAULT).map { it.toInt() and 0xFF }
            put("image", JsonArray(srcBytes.map { kotlinx.serialization.json.JsonPrimitive(it) }))
          }
          request.regionOverlayImage?.let { mask ->
            val maskBytes = Base64.decode(mask.base64Data, Base64.DEFAULT).map { it.toInt() and 0xFF }
            put("mask", JsonArray(maskBytes.map { kotlinx.serialization.json.JsonPrimitive(it) }))
          }
        }.toString()

      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.GENERATING))
      val resp =
        cfService.runModelRaw(
          accountId = settings.cloudflareAccountId.trim(),
          modelId = cfModelId,
          authorization = "Bearer ${settings.cloudflareApiToken.trim()}",
          body = payloadJson.toRequestBody("application/json".toMediaType()),
        )

      if (!resp.isSuccessful) {
        val status =
          if (resp.code() == 429) ImageGenerationStatus.QUOTA_EXHAUSTED else ImageGenerationStatus.FAILED
        val msg =
          when (resp.code()) {
            401, 403 -> "Cloudflare Workers AI rejected your token (${resp.code()})."
            429 -> "Cloudflare Workers AI free daily quota/rate limit reached (429)."
            else -> "Cloudflare Workers AI failed (HTTP ${resp.code()})."
          }
        if (settings.strictFreeImageOnly && !settings.allowPaidImageFallbackApproved) {
          collector.emit(ImageGenerationEvent.Failure(msg, terminalStatus = status))
        }
        return false
      }

      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.DOWNLOADING_IMAGE))
      val body = resp.body() ?: return false
      val contentType = body.contentType()?.toString().orEmpty()
      val rawBytes = body.bytes()
      if (rawBytes.isEmpty()) return false

      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.VALIDATING_RESULT))
      val imageBytes: ByteArray
      val mime: String
      if (contentType.contains("application/json", ignoreCase = true) || rawBytes.firstOrNull() == '{'.code.toByte()) {
        val env = json.decodeFromString<CloudflareJsonImageEnvelope>(rawBytes.decodeToString())
        val b64 = env.result?.image
        if (b64.isNullOrBlank()) {
          val errMsg = env.errors.firstOrNull()?.message ?: "Cloudflare did not return image bytes."
          collector.emit(ImageGenerationEvent.Failure(errMsg))
          return false
        }
        imageBytes = Base64.decode(b64, Base64.DEFAULT)
        mime = "image/png"
      } else {
        imageBytes = rawBytes
        mime = if (contentType.contains("jpeg", true)) "image/jpeg" else "image/png"
      }

      val mediaId = "img_${UUID.randomUUID()}"
      val rootId = request.rootMediaId ?: mediaId
      val saved = storageManager.saveGeneratedBytes(mediaId, imageBytes, mime)
      val result =
        GeneratedImageResult(
          mediaId = mediaId,
          rootMediaId = rootId,
          parentMediaId = request.parentMediaId,
          versionNumber = request.nextVersionNumber,
          prompt = request.prompt,
          editInstruction = request.editInstruction,
          filePath = saved.filePath,
          mimeType = saved.mimeType,
          width = saved.width,
          height = saved.height,
          providerModel = "cloudflare:$cfModelId",
          captionText = "Generated via Cloudflare Workers AI (Free-Tier)",
          status = ImageGenerationStatus.COMPLETED,
          sourceBadge = sourceKind.badgeLabel,
          executionSource = sourceKind,
          hasTransparency = saved.hasTransparency,
        )
      persistTaskAndAssetSafe(request, result, "cloudflare_flux", cfModelId)
      collector.emit(ImageGenerationEvent.Success(result))
      true
    } catch (e: Exception) {
      if (settings.strictFreeImageOnly && !settings.allowPaidImageFallbackApproved) {
        collector.emit(
          ImageGenerationEvent.Failure(
            "Cloudflare Workers AI error: ${e.localizedMessage ?: "network failure"}",
          )
        )
      }
      false
    }
  }

  private suspend fun executeViaFirebaseAiLogic(
    request: ImageGenerationRequest,
    modelId: String,
    firebaseAi: FirebaseAiLogicService,
    sourceKind: ImageProcessingSource,
    collector: FlowCollector<ImageGenerationEvent>,
  ) {
    collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.UPLOADING_REQUEST))
    val sourceBitmap =
      request.sourceImage?.let { payload ->
        val raw = Base64.decode(payload.base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(raw, 0, raw.size)
      }
    val maskBitmap =
      request.regionOverlayImage?.let { payload ->
        val raw = Base64.decode(payload.base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(raw, 0, raw.size)
      }
    collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.GENERATING))
    val result =
      firebaseAi.generateOrEditImage(
        prompt = request.prompt,
        modelId = modelId,
        sourceBitmap = sourceBitmap,
        maskBitmap = maskBitmap,
      )
    sourceBitmap?.recycle()
    maskBitmap?.recycle()
    result.fold(
      onSuccess = { gen ->
        collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.DOWNLOADING_IMAGE))
        collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.VALIDATING_RESULT))
        val mediaId = "img_${UUID.randomUUID()}"
        val rootId = request.rootMediaId ?: mediaId
        val saved = storageManager.saveBitmap(mediaId, gen.bitmap)
        val finalRes =
          GeneratedImageResult(
            mediaId = mediaId,
            rootMediaId = rootId,
            parentMediaId = request.parentMediaId,
            versionNumber = request.nextVersionNumber,
            prompt = request.prompt,
            editInstruction = request.editInstruction,
            filePath = saved.filePath,
            mimeType = saved.mimeType,
            width = saved.width,
            height = saved.height,
            providerModel = gen.modelUsed,
            captionText = gen.captionText,
            status = ImageGenerationStatus.COMPLETED,
            sourceBadge = sourceKind.badgeLabel,
            executionSource = sourceKind,
            hasTransparency = saved.hasTransparency,
          )
        persistTaskAndAssetSafe(request, finalRes, "firebase_ai_logic", gen.modelUsed)
        collector.emit(ImageGenerationEvent.Success(finalRes))
      },
      onFailure = { err ->
        val msg = err.message ?: "Firebase AI Logic image generation failed."
        val status =
          if (msg.contains("quota", true) || msg.contains("billing", true)) {
            ImageGenerationStatus.QUOTA_EXHAUSTED
          } else {
            ImageGenerationStatus.FAILED
          }
        collector.emit(ImageGenerationEvent.Failure(msg, terminalStatus = status))
      },
    )
  }

  private suspend fun executeViaGemini(
    request: ImageGenerationRequest,
    modelId: String,
    apiKey: String,
    sourceKind: ImageProcessingSource,
    collector: FlowCollector<ImageGenerationEvent>,
  ) {
    try {
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.UPLOADING_REQUEST))
      val parts = mutableListOf(GeminiPart(text = request.prompt))
      request.sourceImage?.let { src ->
        parts += GeminiPart(inlineData = GeminiInlineData(mimeType = src.mimeType, data = src.base64Data))
      }
      request.regionOverlayImage?.let { mask ->
        parts += GeminiPart(
          text = if (request.requiresMaskInpainting) {
            "Binary inpainting mask (edit ONLY the painted white regions, preserve rest seamlessly):"
          } else {
            "Reference annotated region mask / crop showing user focus area:"
          }
        )
        parts += GeminiPart(inlineData = GeminiInlineData(mimeType = mask.mimeType, data = mask.base64Data))
      }

      val apiRequest =
        GeminiGenerateRequest(
          contents = listOf(GeminiContent(role = "user", parts = parts)),
          generationConfig =
            GeminiGenerationConfig(
              temperature = if (request.sourceImage != null) 0.55 else 0.85,
              responseModalities = listOf("TEXT", "IMAGE"),
            ),
        )

      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.GENERATING))
      val response =
        RetryPolicy.withExponentialBackoff {
          geminiApiService.generateContent(
            model = modelId,
            apiKey = apiKey,
            request = apiRequest,
          )
        }

      if (!response.isSuccessful) {
        val code = response.code()
        val status =
          if (code == 429) ImageGenerationStatus.QUOTA_EXHAUSTED else ImageGenerationStatus.FAILED
        val msg =
          when (code) {
            401, 403 -> "Gemini API key was rejected ($code). Verify your key in Settings."
            404 -> "Configured image model ($modelId) was not found. Update Image Model ID in Settings."
            429 -> "Image generation quota or rate limit exhausted (HTTP 429)."
            else -> "Image generation failed ($code): ${response.message()}"
          }
        collector.emit(ImageGenerationEvent.Failure(msg, terminalStatus = status))
        return
      }

      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.DOWNLOADING_IMAGE))
      val body = response.body()
      val candidateParts = body?.candidates?.firstOrNull()?.content?.parts.orEmpty()
      val imagePart = candidateParts.firstOrNull { it.inlineData != null && it.inlineData.data.isNotBlank() }?.inlineData
      val captionText = candidateParts.mapNotNull { it.text }.joinToString("\n").trim()

      if (imagePart == null) {
        collector.emit(
          ImageGenerationEvent.Failure(
            captionText.ifBlank {
              "The model ($modelId) did not return an image. Check that your Image Model ID supports image output."
            }
          )
        )
        return
      }

      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.VALIDATING_RESULT))
      val mediaId = "img_${UUID.randomUUID()}"
      val rootId = request.rootMediaId ?: mediaId
      val saved = storageManager.saveGeneratedBase64(mediaId, imagePart.data, imagePart.mimeType)
      val finalRes =
        GeneratedImageResult(
          mediaId = mediaId,
          rootMediaId = rootId,
          parentMediaId = request.parentMediaId,
          versionNumber = request.nextVersionNumber,
          prompt = request.prompt,
          editInstruction = request.editInstruction,
          filePath = saved.filePath,
          mimeType = saved.mimeType,
          width = saved.width,
          height = saved.height,
          providerModel = modelId,
          captionText = captionText,
          status = ImageGenerationStatus.COMPLETED,
          sourceBadge = sourceKind.badgeLabel,
          executionSource = sourceKind,
          hasTransparency = saved.hasTransparency,
        )
      persistTaskAndAssetSafe(request, finalRes, "gemini_image_studio", modelId)
      collector.emit(ImageGenerationEvent.Success(finalRes))
    } catch (e: AiProviderException) {
      collector.emit(ImageGenerationEvent.Failure(e.message))
    } catch (e: IOException) {
      collector.emit(ImageGenerationEvent.Failure("Network error during image generation: ${e.message ?: "offline"}"))
    } catch (e: Exception) {
      collector.emit(ImageGenerationEvent.Failure(e.localizedMessage ?: "Unexpected image generation error."))
    }
  }

  private suspend fun executeViaProxy(
    request: ImageGenerationRequest,
    modelId: String,
    baseUrl: String,
    sourceKind: ImageProcessingSource,
    collector: FlowCollector<ImageGenerationEvent>,
  ) {
    try {
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.UPLOADING_REQUEST))
      val normalizedBase = baseUrl.trimEnd('/')
      val isEdit = request.sourceImage != null
      val endpoint =
        if (isEdit) "$normalizedBase/v1/images/edit"
        else "$normalizedBase/v1/images/generate"
      val proxyReq =
        ProxyImageGenerationRequest(
          modelId = modelId,
          prompt = request.prompt,
          sourceImage = request.sourceImage?.let { ProxyImageDto(it.mimeType, it.base64Data) },
          maskImage = request.regionOverlayImage?.let { ProxyImageDto(it.mimeType, it.base64Data) },
        )
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.GENERATING))
      val response =
        if (isEdit) proxyApiService.editImageViaProxy(endpoint, proxyReq)
        else proxyApiService.generateImageViaProxy(endpoint, proxyReq)

      if (!response.isSuccessful) {
        collector.emit(ImageGenerationEvent.Failure("Proxy image request failed (${response.code()})."))
        return
      }
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.DOWNLOADING_IMAGE))
      val body = response.body()
      if (body == null || body.base64Data.isBlank()) {
        collector.emit(ImageGenerationEvent.Failure(body?.error ?: "Proxy did not return generated image bytes."))
        return
      }
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.VALIDATING_RESULT))
      val mediaId = "img_${UUID.randomUUID()}"
      val rootId = request.rootMediaId ?: mediaId
      val saved = storageManager.saveGeneratedBase64(mediaId, body.base64Data, body.mimeType)
      val finalRes =
        GeneratedImageResult(
          mediaId = mediaId,
          rootMediaId = rootId,
          parentMediaId = request.parentMediaId,
          versionNumber = request.nextVersionNumber,
          prompt = request.prompt,
          editInstruction = request.editInstruction,
          filePath = saved.filePath,
          mimeType = saved.mimeType,
          width = saved.width,
          height = saved.height,
          providerModel = modelId,
          captionText = body.captionText,
          status = ImageGenerationStatus.COMPLETED,
          sourceBadge = sourceKind.badgeLabel,
          executionSource = sourceKind,
          hasTransparency = saved.hasTransparency,
        )
      persistTaskAndAssetSafe(request, finalRes, "https_proxy", modelId)
      collector.emit(ImageGenerationEvent.Success(finalRes))
    } catch (e: IOException) {
      collector.emit(ImageGenerationEvent.Failure("Network error reaching image proxy: ${e.message ?: "offline"}"))
    } catch (e: Exception) {
      collector.emit(ImageGenerationEvent.Failure(e.localizedMessage ?: "Proxy image generation failed."))
    }
  }

  private suspend fun persistTaskAndAssetSafe(
    request: ImageGenerationRequest,
    result: GeneratedImageResult,
    providerId: String,
    modelId: String,
  ) {
    val dao = imageStudioDao ?: return
    runCatching {
      val now = System.currentTimeMillis()
      val assetId = "ast_${result.mediaId}"
      dao.upsertAsset(
        ImageAssetEntity(
          assetId = assetId,
          localPreviewPath = result.filePath,
          mediaStoreUri = null,
          mimeType = result.mimeType,
          width = result.width,
          height = result.height,
          fileSizeBytes = File(result.filePath).length(),
          hasTransparency = result.hasTransparency,
          sourceBadge = result.sourceBadge,
          createdAtMs = now,
        )
      )
      dao.upsertTask(
        ImageTaskEntity(
          taskId = "tsk_${result.mediaId}",
          conversationId = request.conversationId,
          featureType = request.featureType.name,
          executionSource = result.executionSource.name,
          prompt = request.prompt,
          editPlanJson = "{}",
          inputAssetId = request.parentMediaId,
          maskPath = null,
          outputAssetId = assetId,
          providerId = providerId,
          modelId = modelId,
          status = result.status.name,
          errorMessage = result.errorMessage,
          createdAtMs = now,
          updatedAtMs = now,
        )
      )
    }
  }
}
