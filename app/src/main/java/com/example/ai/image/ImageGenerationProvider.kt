package com.example.ai.image

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
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
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageGenerationStatus
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

data class ImageGenerationRequest(
  val prompt: String,
  val settings: AppSettings,
  val sourceImage: ProcessedImagePayload? = null,
  val regionOverlayImage: ProcessedImagePayload? = null,
  val parentMediaId: String? = null,
  val rootMediaId: String? = null,
  val nextVersionNumber: Int = 1,
  val editInstruction: String? = null,
)

sealed interface ImageGenerationEvent {
  data class Stage(val status: ImageGenerationStatus) : ImageGenerationEvent
  data class Success(val result: GeneratedImageResult) : ImageGenerationEvent
  data class Failure(val message: String, val recoverable: Boolean = true) : ImageGenerationEvent
}

interface ImageGenerationProvider {
  fun generateOrEdit(request: ImageGenerationRequest): Flow<ImageGenerationEvent>
}

@Singleton
class ImageStorageManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val outputDir: File by lazy {
    File(context.filesDir, "mousomi_generated_images").apply { mkdirs() }
  }

  suspend fun saveGeneratedBase64(
    mediaId: String,
    base64Data: String,
    mimeType: String,
  ): SavedImageInfo = withContext(Dispatchers.IO) {
    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
    val ext = when {
      mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "jpg"
      mimeType.contains("webp", ignoreCase = true) -> "webp"
      else -> "png"
    }
    val file = File(outputDir, "$mediaId.$ext")
    file.writeBytes(bytes)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    SavedImageInfo(
      filePath = file.absolutePath,
      width = bounds.outWidth.coerceAtLeast(0),
      height = bounds.outHeight.coerceAtLeast(0),
      mimeType = mimeType,
    )
  }

  fun getShareableContentUri(filePath: String): Uri {
    val file = File(filePath)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  suspend fun saveToGallery(filePath: String, displayTitle: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
          throw IOException("Generated image file no longer exists on disk.")
        }
        val cleanName =
          displayTitle
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(36)
            .ifBlank { "Mousomi_Art" } + "_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
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
  )
}

@Singleton
class CompositeImageGenerationProvider @Inject constructor(
  private val geminiApiService: GeminiApiService,
  private val proxyApiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
  private val storageManager: ImageStorageManager,
) : ImageGenerationProvider {

  override fun generateOrEdit(request: ImageGenerationRequest): Flow<ImageGenerationEvent> = flow {
    emit(ImageGenerationEvent.Stage(ImageGenerationStatus.PREPARING))

    val settings = request.settings
    val modelId = settings.imageModelId.trim().ifBlank { VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID }
    val useProxy = settings.connectionMode == ConnectionMode.BACKEND_PROXY

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
      executeViaProxy(request, modelId, validatedProxy.getOrThrow(), this)
    } else {
      val key = settings.geminiApiKey.trim()
      if (key.isEmpty()) {
        emit(
          ImageGenerationEvent.Failure(
            "Image generation requires a Google Gemini API key or HTTPS Backend Proxy. Open Settings to configure one."
          )
        )
        return@flow
      }
      executeViaGemini(request, modelId, key, this)
    }
  }

  private suspend fun executeViaGemini(
    request: ImageGenerationRequest,
    modelId: String,
    apiKey: String,
    collector: kotlinx.coroutines.flow.FlowCollector<ImageGenerationEvent>,
  ) {
    try {
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.GENERATING))
      val parts = mutableListOf(GeminiPart(text = request.prompt))
      request.sourceImage?.let { src ->
        parts += GeminiPart(inlineData = GeminiInlineData(mimeType = src.mimeType, data = src.base64Data))
      }
      request.regionOverlayImage?.let { mask ->
        parts += GeminiPart(text = "Reference annotated region mask / crop showing user focus area:")
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
        val msg =
          when (code) {
            401, 403 -> "Gemini API key was rejected ($code). Verify your key in Settings."
            404 -> "Configured image model ($modelId) was not found. Update the Image Model ID in Settings."
            429 -> "Image generation rate limit reached (429). Please retry shortly."
            else -> "Image generation failed ($code): ${response.message()}"
          }
        collector.emit(ImageGenerationEvent.Failure(msg))
        return
      }

      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.PROCESSING))
      val body = response.body()
      val candidateParts = body?.candidates?.firstOrNull()?.content?.parts.orEmpty()
      val imagePart = candidateParts.firstOrNull { it.inlineData != null && it.inlineData.data.isNotBlank() }?.inlineData
      val captionText = candidateParts.mapNotNull { it.text }.joinToString("\n").trim()

      if (imagePart == null) {
        collector.emit(
          ImageGenerationEvent.Failure(
            captionText.ifBlank {
              "The model ($modelId) did not return an image. Check that your configured Image Model ID supports image output."
            }
          )
        )
        return
      }

      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.FINALIZING))
      val mediaId = "img_${UUID.randomUUID()}"
      val rootId = request.rootMediaId ?: mediaId
      val saved = storageManager.saveGeneratedBase64(mediaId, imagePart.data, imagePart.mimeType)
      collector.emit(
        ImageGenerationEvent.Success(
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
          )
        )
      )
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
    collector: kotlinx.coroutines.flow.FlowCollector<ImageGenerationEvent>,
  ) {
    try {
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.GENERATING))
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
      val response =
        if (isEdit) proxyApiService.editImageViaProxy(endpoint, proxyReq)
        else proxyApiService.generateImageViaProxy(endpoint, proxyReq)

      if (!response.isSuccessful) {
        collector.emit(ImageGenerationEvent.Failure("Proxy image request failed (${response.code()})."))
        return
      }
      val body = response.body()
      if (body == null || body.base64Data.isBlank()) {
        collector.emit(ImageGenerationEvent.Failure(body?.error ?: "Proxy did not return generated image bytes."))
        return
      }
      collector.emit(ImageGenerationEvent.Stage(ImageGenerationStatus.FINALIZING))
      val mediaId = "img_${UUID.randomUUID()}"
      val rootId = request.rootMediaId ?: mediaId
      val saved = storageManager.saveGeneratedBase64(mediaId, body.base64Data, body.mimeType)
      collector.emit(
        ImageGenerationEvent.Success(
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
          )
        )
      )
    } catch (e: IOException) {
      collector.emit(ImageGenerationEvent.Failure("Network error reaching image proxy: ${e.message ?: "offline"}"))
    } catch (e: Exception) {
      collector.emit(ImageGenerationEvent.Failure(e.localizedMessage ?: "Proxy image generation failed."))
    }
  }
}
