package com.example.ai.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiStreamEvent
import com.example.core.network.AiProviderException
import com.example.core.security.SecurityValidator
import com.example.domain.model.SearchResponse
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SourceVerificationStatus
import com.example.domain.model.TokenUsage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

@Singleton
class FirebaseAiLogicService @Inject constructor(
  @ApplicationContext private val context: Context,
  private val securityValidator: SecurityValidator,
) {

  data class GeneratedBitmapResult(
    val bitmap: Bitmap,
    val captionText: String,
    val modelUsed: String,
  )

  fun isAvailable(): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

  fun resolveTextModel(requestedId: String): String {
    val clean = requestedId.trim().lowercase()
    return when {
      clean.contains("argon") -> "gemini-4-argon"
      clean.contains("pro") && !clean.contains("1.5") && !clean.contains("2.0") && !clean.contains("2.5") ->
        "gemini-4-argon"
      clean.contains("lite") && clean.contains("3.") -> "gemini-3.5-flash-lite"
      clean.startsWith("gemini-3.") || clean.startsWith("gemini-4-") -> requestedId.trim()
      // Map legacy/deprecated IDs (1.5, 2.0, 2.5) and virtual slots cleanly to supported default
      else -> DEFAULT_TEXT_MODEL
    }
  }

  fun resolveImageModel(requestedId: String): String {
    val clean = requestedId.trim().lowercase()
    return when {
      clean == "gemini-3-pro-image" -> "gemini-3-pro-image"
      clean == "gemini-3.1-flash-lite-image" -> "gemini-3.1-flash-lite-image"
      clean == "gemini-nano-banana-2.1" -> "gemini-nano-banana-2.1"
      else -> DEFAULT_IMAGE_MODEL
    }
  }

  fun streamMultimodalChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
    if (!isAvailable()) {
      throw AiProviderException("Firebase is not initialized yet. Check google-services.json and App Check.")
    }

    val modelName = resolveTextModel(request.model.id)
    val sysContent: Content? =
      request.systemPrompt.takeIf { it.isNotBlank() }?.let { sys ->
        content { text(sys) }
      }

    val generativeModel =
      Firebase.ai.generativeModel(
        modelName = modelName,
        generationConfig = generationConfig { temperature = request.temperature.toFloat() },
        systemInstruction = sysContent,
      )

    val allTurns = request.turns
    val historyContents =
      allTurns.dropLast(1).map { turn ->
        content(role = if (turn.isUser) "user" else "model") {
          turn.images.forEach { img ->
            decodeBase64Bitmap(img.base64Data)?.let { bmp -> image(bmp) }
          }
          if (turn.text.isNotBlank()) {
            text(turn.text)
          }
        }
      }

    val finalTurn = allTurns.lastOrNull()
    val promptContent =
      content(role = "user") {
        finalTurn?.images?.forEach { img ->
          decodeBase64Bitmap(img.base64Data)?.let { bmp -> image(bmp) }
        }
        val promptText = finalTurn?.text.orEmpty().ifBlank { "Analyze the attached content." }
        text(promptText)
      }

    var latestUsage: TokenUsage? = null
    try {
      withTimeout(60_000L) {
        val stream =
          if (historyContents.isNotEmpty()) {
            generativeModel.startChat(history = historyContents).sendMessageStream(promptContent)
          } else {
            generativeModel.generateContentStream(promptContent)
          }

        stream.collect { chunk ->
          val delta = chunk.text.orEmpty()
          if (delta.isNotEmpty()) {
            emit(AiStreamEvent.Delta(delta))
          }
          chunk.usageMetadata?.let { meta ->
            val usage =
              TokenUsage(
                promptTokens = meta.promptTokenCount,
                completionTokens = meta.candidatesTokenCount ?: 0,
                totalTokens = meta.totalTokenCount,
              )
            latestUsage = usage
            emit(AiStreamEvent.Usage(usage))
          }
        }
      }
      latestUsage?.let { emit(AiStreamEvent.Usage(it)) }
      emit(AiStreamEvent.Completed)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Firebase AI chat timed out", e)
      throw AiProviderException("Firebase AI Logic request timed out after 60 seconds. Please retry.", cause = e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI chat failed", e)
      throw AiProviderException(formatFirebaseAiError(e), cause = e)
    }
  }

  suspend fun searchGrounded(query: String, isMapsQuery: Boolean = false): Result<SearchResponse> {
    if (!isAvailable()) {
      return Result.failure(IllegalStateException("Firebase is not initialized."))
    }
    return try {
      val searchModel =
        Firebase.ai.generativeModel(
          modelName = DEFAULT_TEXT_MODEL,
          generationConfig = generationConfig { temperature = 0.2f },
          tools = listOf(Tool.googleSearch()),
        )
      val prompt =
        if (isMapsQuery) {
          "Find real verified places, addresses, hours, and Google Maps links for: \"$query\". Include citations."
        } else {
          "Search for the latest verified information about: \"$query\". Cite real web sources."
        }

      val response = withTimeout(60_000L) { searchModel.generateContent(prompt) }
      val candidate = response.candidates.firstOrNull()
      val grounding = candidate?.groundingMetadata
      val chunks = grounding?.groundingChunks.orEmpty()
      val now = System.currentTimeMillis()
      val responseSummary = response.text.orEmpty().take(240)

      val results =
        chunks.mapIndexedNotNull { idx, chunk ->
          val web = chunk.web ?: return@mapIndexedNotNull null
          val uri = web.uri?.trim().orEmpty()
          if (uri.isBlank()) return@mapIndexedNotNull null
          val domain = web.domain?.takeIf { it.isNotBlank() } ?: extractDomain(uri)
          SearchResultItem(
            id = "fai_grd_$idx",
            title = web.title?.ifBlank { domain } ?: domain,
            url = uri,
            publisher = web.title ?: domain,
            snippet = responseSummary,
            publishedTimestamp = null,
            retrievedTimestampMs = now,
            verificationStatus =
              if (uri.contains("vertexaisearch.cloud.google.com/grounding-api-redirect")) {
                SourceVerificationStatus.GROUNDED_REDIRECT
              } else if (securityValidator.validateHttpsResourceUrl(uri)) {
                SourceVerificationStatus.VERIFIED_HTTPS
              } else {
                SourceVerificationStatus.UNVERIFIED_FORMAT
              },
            sourceDomain = domain,
            relevanceScore = (1.0 - idx * 0.04).coerceAtLeast(0.3),
          )
        }

      if (results.isEmpty()) {
        Result.failure(
          IllegalStateException(
            "Google Search Grounding via Firebase AI Logic returned no web citations for \"$query\"."
          )
        )
      } else {
        Result.success(
          SearchResponse(
            query = query,
            results = results,
            page = 1,
            hasMorePages = false,
            providerUsed = if (isMapsQuery) "Firebase AI Logic Search (Maps)" else "Firebase AI Logic Google Search",
            retrievedAtMs = now,
          )
        )
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Firebase AI grounded search timed out", e)
      Result.failure(IllegalStateException("Search timed out after 60 seconds."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI grounded search failed", e)
      Result.failure(IllegalStateException(formatFirebaseAiError(e), e))
    }
  }

  suspend fun generateOrEditImage(
    prompt: String,
    modelId: String,
    sourceBitmap: Bitmap? = null,
    maskBitmap: Bitmap? = null,
  ): Result<GeneratedBitmapResult> {
    if (!isAvailable()) {
      return Result.failure(IllegalStateException("Firebase is not initialized."))
    }
    val resolvedModel = resolveImageModel(modelId)
    return try {
      val imageModel =
        Firebase.ai.generativeModel(
          modelName = resolvedModel,
          generationConfig =
            generationConfig {
              responseModalities = listOf(ResponseModality.IMAGE)
            },
        )
      val response =
        withTimeout(60_000L) {
          if (sourceBitmap != null || maskBitmap != null) {
            imageModel.generateContent(
              content {
                sourceBitmap?.let { image(it) }
                maskBitmap?.let {
                  text("Reference annotated focus region:")
                  image(it)
                }
                text(prompt)
              }
            )
          } else {
            imageModel.generateContent(prompt)
          }
        }

      val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
      val imagePart = parts.filterIsInstance<ImagePart>().firstOrNull()
      val bitmap = imagePart?.image
      val caption = response.text.orEmpty().trim()
      if (bitmap == null) {
        Result.failure(
          IllegalStateException(
            caption.ifBlank {
              "No image was returned by $resolvedModel. Ensure Firebase Blaze billing is active for Nano Banana image generation."
            }
          )
        )
      } else {
        Result.success(
          GeneratedBitmapResult(
            bitmap = bitmap,
            captionText = caption,
            modelUsed = resolvedModel,
          )
        )
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Firebase AI image generation timed out", e)
      Result.failure(IllegalStateException("Image generation timed out after 60 seconds."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI image generation failed", e)
      Result.failure(IllegalStateException(formatFirebaseAiError(e), e))
    }
  }

  suspend fun analyzeAudioBytes(
    bytes: ByteArray,
    mimeType: String,
    prompt: String,
  ): Result<String> {
    if (!isAvailable()) {
      return Result.failure(IllegalStateException("Firebase is not initialized."))
    }
    return try {
      val audioModel =
        Firebase.ai.generativeModel(
          modelName = DEFAULT_TEXT_MODEL,
          generationConfig = generationConfig { temperature = 0.2f },
        )
      val response =
        withTimeout(60_000L) {
          audioModel.generateContent(
            content {
              inlineData(bytes, mimeType)
              text(prompt)
            }
          )
        }
      val text = response.text?.trim().orEmpty()
      if (text.isBlank()) {
        Result.failure(IllegalStateException("Model returned an empty audio transcription."))
      } else {
        Result.success(text)
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Firebase AI audio analysis timed out", e)
      Result.failure(IllegalStateException("Audio transcription timed out after 60 seconds."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI audio analysis failed", e)
      Result.failure(IllegalStateException(formatFirebaseAiError(e), e))
    }
  }

  suspend fun generateMultimodalContent(
    prompt: String,
    bitmaps: List<Bitmap>,
    temperature: Float = 0.25f,
    systemInstructionText: String? = null,
    modelId: String = DEFAULT_TEXT_MODEL,
  ): Result<String> {
    if (!isAvailable()) {
      return Result.failure(IllegalStateException("Firebase is not initialized."))
    }
    return try {
      val sysContent =
        systemInstructionText?.takeIf { it.isNotBlank() }?.let { sys -> content { text(sys) } }
      val generativeModel =
        Firebase.ai.generativeModel(
          modelName = resolveTextModel(modelId),
          generationConfig = generationConfig { this.temperature = temperature },
          systemInstruction = sysContent,
        )
      val response =
        withTimeout(60_000L) {
          generativeModel.generateContent(
            content(role = "user") {
              bitmaps.forEach { bmp -> image(bmp) }
              text(prompt)
            }
          )
        }
      val text = response.text?.trim().orEmpty()
      if (text.isBlank()) {
        Result.failure(IllegalStateException("Vision model returned an empty response."))
      } else {
        Result.success(text)
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Firebase AI multimodal vision timed out", e)
      Result.failure(IllegalStateException("Visual analysis timed out after 60 seconds."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI multimodal vision failed", e)
      Result.failure(IllegalStateException(formatFirebaseAiError(e), e))
    }
  }

  private fun decodeBase64Bitmap(base64Data: String): Bitmap? {
    if (base64Data.isBlank()) return null
    return runCatching {
      val decoded = Base64.decode(base64Data, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
    }.getOrNull()
  }

  private fun extractDomain(url: String): String =
    runCatching { URI(url).host?.removePrefix("www.").orEmpty() }.getOrDefault("").ifBlank { "google.com" }

  private fun formatFirebaseAiError(error: Exception): String {
    val msg = error.localizedMessage ?: error.message ?: "Firebase AI Logic request failed."
    return when {
      msg.contains("403") || msg.contains("app check", ignoreCase = true) ->
        "App Check attestation or Firebase AI Logic access denied ($msg)."
      msg.contains("quota", ignoreCase = true) || msg.contains("429") ->
        "Quota or rate limit exceeded ($msg). Check Firebase Blaze plan or try again shortly."
      else -> msg
    }
  }

  companion object {
    private const val TAG = "FirebaseAiLogicService"
    const val DEFAULT_TEXT_MODEL = "gemini-3.8-flash"
    const val DEFAULT_PRO_MODEL = "gemini-4-argon"
    const val DEFAULT_LITE_MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_IMAGE_MODEL = "gemini-nano-banana-2.1"
  }
}
