package com.example.ai.image

import com.example.ai.firebase.FirebaseAiLogicService
import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiStreamEvent
import com.example.ai.provider.AiTurn
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.dto.GeminiContent
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerationConfig
import com.example.ai.provider.dto.GeminiPart
import com.example.domain.model.AppSettings
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.ImageProcessingSource
import com.example.domain.model.VerifiedModelCatalog
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

enum class PollinationsAspectRatio(
  val label: String,
  val widthPx: Int,
  val heightPx: Int,
) {
  SQUARE_1_1("1:1 Square", 1024, 1024),
  PORTRAIT_3_4("3:4 Portrait", 768, 1024),
  LANDSCAPE_16_9("16:9 Wide", 1024, 768),
  STORY_9_16("9:16 Story", 768, 1024),
}

/**
 * Zero-key free-tier Creative Image Generator backed by Pollinations.ai (Flux).
 * Performs real HTTPS requests via OkHttp, validates returned image bytes, and stores crisp PNG/JPEG outputs locally.
 */
@Singleton
class PollinationsImageService @Inject constructor(
  private val okHttpClient: OkHttpClient,
  private val geminiApiService: GeminiApiService,
  private val storageManager: ImageStorageManager,
  private val firebaseAiLogicService: FirebaseAiLogicService? = null,
) {

  fun buildImageUrl(
    prompt: String,
    width: Int = 1024,
    height: Int = 1024,
    seed: Long = 42L,
    model: String = "flux",
  ): String {
    val safeW = width.coerceIn(256, 1440)
    val safeH = height.coerceIn(256, 1440)
    val safeSeed = abs(seed % 1_000_000_000L).coerceAtLeast(1L)
    val encodedPrompt =
      URLEncoder.encode(prompt.trim().ifBlank { "cinematic digital art" }, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
    val encodedModel = URLEncoder.encode(model.trim().ifBlank { "flux" }, StandardCharsets.UTF_8.name())
    return "https://image.pollinations.ai/prompt/$encodedPrompt?width=$safeW&height=$safeH&model=$encodedModel&nologo=true&seed=$safeSeed"
  }

  /**
   * Optionally expands a short user prompt into a vivid image prompt using Gemini 3.8 Flash (if available),
   * or falls back cleanly to a deterministic creative boost when offline/unconfigured.
   */
  suspend fun enhancePrompt(
    rawPrompt: String,
    settings: AppSettings,
  ): String = withContext(Dispatchers.IO) {
    val clean = rawPrompt.trim()
    if (clean.isBlank()) return@withContext ""

    val instruction =
      "Rewrite this image prompt into one concise, vivid visual description under 60 words with lighting, composition, and mood details. Return ONLY the improved prompt text, no quotes:\n$clean"

    val apiKey = settings.geminiApiKey.trim()
    if (apiKey.isNotBlank()) {
      val direct =
        runCatching {
          val resp =
            geminiApiService.generateContent(
              model = VerifiedModelCatalog.FLAGSHIP_FLASH_38_ID,
              apiKey = apiKey,
              request =
                GeminiGenerateRequest(
                  contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = instruction)))),
                  generationConfig = GeminiGenerationConfig(temperature = 0.7),
                ),
            )
          resp.body()
            ?.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.mapNotNull { it.text }
            ?.joinToString(" ")
            ?.trim()
        }.getOrNull()
      if (!direct.isNullOrBlank()) return@withContext direct
    }

    if (firebaseAiLogicService?.isAvailable() == true) {
      val viaFirebase =
        runCatching {
          val sb = StringBuilder()
          firebaseAiLogicService.streamMultimodalChat(
            AiChatRequest(
              model = VerifiedModelCatalog.createFlash38Spec(),
              systemPrompt = "",
              turns = listOf(AiTurn(isUser = true, text = instruction)),
              settings = settings,
              temperature = 0.7,
            )
          ).collect { event ->
            if (event is AiStreamEvent.Delta) sb.append(event.text)
          }
          sb.toString().trim()
        }.getOrNull()
      if (!viaFirebase.isNullOrBlank()) return@withContext viaFirebase
    }

    if (clean.length < 48) {
      "$clean, ultra-detailed editorial lighting, sharp focus, rich textures, harmonious color grading"
    } else {
      clean
    }
  }

  suspend fun generateImage(
    prompt: String,
    width: Int = 1024,
    height: Int = 1024,
    seed: Long? = null,
    enhancePromptEnabled: Boolean = false,
    settings: AppSettings = AppSettings(),
    parentMediaId: String? = null,
    rootMediaId: String? = null,
    nextVersionNumber: Int = 1,
    editInstruction: String? = null,
    onStage: suspend (ImageGenerationStatus) -> Unit = {},
  ): Result<GeneratedImageResult> = withContext(Dispatchers.IO) {
    runCatching {
      val cleanInput = prompt.trim()
      require(cleanInput.isNotBlank()) { "Enter a visual prompt to generate an image." }

      onStage(ImageGenerationStatus.PREPARING_PROMPT)
      val effectivePrompt =
        if (enhancePromptEnabled) enhancePrompt(cleanInput, settings) else cleanInput
      val actualSeed = seed ?: ((System.currentTimeMillis() and 0x7FFFFFFFL).coerceAtLeast(1L))
      val url = buildImageUrl(
        prompt = effectivePrompt,
        width = width,
        height = height,
        seed = actualSeed,
        model = "flux",
      )

      onStage(ImageGenerationStatus.GENERATING)
      val req =
        Request.Builder()
          .url(url)
          .header("Accept", "image/*")
          .get()
          .build()

      val (bytes, contentType) = executeGetBytes(req)

      onStage(ImageGenerationStatus.DOWNLOADING_IMAGE)
      if (bytes.isEmpty()) {
        throw IOException("Pollinations.ai returned an empty image payload.")
      }
      val headText = bytes.take(128).toByteArray().decodeToString().lowercase()
      if (headText.contains("<!doctype html") || headText.contains("<html")) {
        throw IOException("Pollinations.ai returned an unexpected HTML response instead of image data.")
      }
      val mimeHint =
        when {
          contentType.contains("jpeg", ignoreCase = true) || contentType.contains("jpg", ignoreCase = true) -> "image/jpeg"
          contentType.contains("webp", ignoreCase = true) -> "image/webp"
          else -> "image/png"
        }

      onStage(ImageGenerationStatus.VALIDATING_RESULT)
      val mediaId = "img_${UUID.randomUUID()}"
      val resolvedRootId = rootMediaId ?: mediaId
      val saved = storageManager.saveGeneratedBytes(mediaId, bytes, mimeHint)

      GeneratedImageResult(
        mediaId = mediaId,
        rootMediaId = resolvedRootId,
        parentMediaId = parentMediaId,
        versionNumber = nextVersionNumber,
        prompt = effectivePrompt,
        editInstruction = editInstruction,
        filePath = saved.filePath,
        mimeType = saved.mimeType,
        width = saved.width,
        height = saved.height,
        providerModel = "pollinations:flux",
        captionText = "Generated with Pollinations.ai Flux (${saved.width}x${saved.height} · seed $actualSeed)",
        status = ImageGenerationStatus.COMPLETED,
        sourceBadge = "Pollinations.ai • Flux Studio",
        executionSource = ImageProcessingSource.CLOUD_AI_GENERATION,
        hasTransparency = saved.hasTransparency,
      )
    }
  }

  private suspend fun executeGetBytes(request: Request): Pair<ByteArray, String> =
    suspendCancellableCoroutine { cont ->
      val call = okHttpClient.newCall(request)
      cont.invokeOnCancellation { call.cancel() }
      call.enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
          }

          override fun onResponse(call: Call, response: Response) {
            response.use { resp ->
              if (!resp.isSuccessful) {
                if (cont.isActive) {
                  cont.resumeWithException(
                    IOException("Pollinations.ai request failed (HTTP ${resp.code})")
                  )
                }
                return
              }
              val contentType = resp.header("Content-Type").orEmpty()
              val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
              if (cont.isActive) {
                cont.resume(bodyBytes to contentType)
              }
            }
          }
        }
      )
    }
}
