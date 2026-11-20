package com.example.ai.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.dto.GeminiContent
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerationConfig
import com.example.ai.provider.dto.GeminiLongRunningImage
import com.example.ai.provider.dto.GeminiLongRunningInstance
import com.example.ai.provider.dto.GeminiLongRunningParameters
import com.example.ai.provider.dto.GeminiLongRunningRequest
import com.example.ai.provider.dto.GeminiPart
import com.example.core.security.SecurityValidator
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.GeneratedMusicResult
import com.example.domain.model.GeneratedVideoResult
import com.example.domain.model.ProcessedImagePayload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class VideoAndMusicGenerationService @Inject constructor(
  @ApplicationContext private val context: Context,
  private val geminiApiService: GeminiApiService,
  private val okHttpClient: OkHttpClient,
  private val securityValidator: SecurityValidator,
) {
  private val mediaDir: File by lazy {
    File(context.filesDir, "mousomi_generated_media").apply { mkdirs() }
  }

  suspend fun generateVideo(
    prompt: String,
    sourceImage: ProcessedImagePayload?,
    settings: AppSettings,
    onProgress: suspend (String) -> Unit,
  ): Result<GeneratedVideoResult> {
    if (settings.connectionMode == ConnectionMode.BACKEND_PROXY) {
      val proxyResult = securityValidator.validateHttpsUrl(settings.proxyBaseUrl)
      if (proxyResult.isFailure) {
        return Result.failure(
          IllegalStateException(
            "Configure a valid HTTPS Backend Proxy URL or a Gemini API key in Settings for Veo video generation."
          )
        )
      }
      return Result.failure(
        IllegalStateException(
          "Video generation in Backend Proxy mode requires an endpoint supporting Veo (/v1/videos/generate). Enter a Gemini API key with Veo billing enabled in Settings to generate videos directly."
        )
      )
    }

    val apiKey = settings.geminiApiKey.trim()
    if (apiKey.isEmpty()) {
      return Result.failure(
        IllegalStateException(
          "Video generation (Veo) requires a Gemini API key with Google AI Studio / Cloud Billing enabled in Settings. Firebase AI Logic Spark mode does not expose Veo predictLongRunning."
        )
      )
    }

    val modelId = DEFAULT_VEO_MODEL
    return try {
      onProgress("Submitting ${if (sourceImage != null) "Image-to-Video" else "Text-to-Video"} job to $modelId…")
      val instance =
        GeminiLongRunningInstance(
          prompt = prompt,
          image =
            sourceImage?.let {
              GeminiLongRunningImage(
                bytesBase64Encoded = it.base64Data,
                mimeType = it.mimeType,
              )
            },
        )
      val initialResp =
        geminiApiService.predictLongRunning(
          model = modelId,
          apiKey = apiKey,
          request =
            GeminiLongRunningRequest(
              instances = listOf(instance),
              parameters =
                GeminiLongRunningParameters(
                  aspectRatio = if (sourceImage != null && sourceImage.width > sourceImage.height) "16:9" else "9:16",
                  durationSeconds = 6,
                  sampleCount = 1,
                ),
            ),
        )

      if (!initialResp.isSuccessful) {
        val code = initialResp.code()
        val msg =
          when (code) {
            400, 404 ->
              "Veo model ($modelId) rejected the request or is unavailable for this API key (HTTP $code)."
            401, 403 ->
              "Gemini API key lacks billing/permissions for Veo video generation (HTTP $code). Verify Paid API tier in Google AI Studio."
            429 -> "Veo video generation quota reached (HTTP 429). Retry later."
            else -> "Veo video generation request failed (HTTP $code)."
          }
        return Result.failure(IllegalStateException(msg))
      }

      val opName =
        initialResp.body()?.name?.trim().orEmpty().removePrefix("v1beta/").removePrefix("/")
      if (opName.isBlank()) {
        return Result.failure(IllegalStateException("Veo did not return a valid long-running operation ID."))
      }

      var completedOp = initialResp.body()!!
      withTimeout(180_000L) {
        var pollAttempt = 0
        while (!completedOp.done && pollAttempt < 36) {
          pollAttempt++
          onProgress("Rendering video via Veo (step $pollAttempt)…")
          delay(5_000L)
          val pollResp = geminiApiService.getOperation(operationName = opName, apiKey = apiKey)
          if (!pollResp.isSuccessful) {
            throw IOException("Operation status check failed (HTTP ${pollResp.code()}).")
          }
          completedOp = pollResp.body() ?: throw IOException("Empty operation response.")
        }
      }

      if (!completedOp.done) {
        return Result.failure(IllegalStateException("Video rendering is still in progress on Google servers. Please try again."))
      }
      completedOp.error?.let { err ->
        return Result.failure(
          IllegalStateException(err.message ?: "Veo video generation failed (code ${err.code}).")
        )
      }

      val videoUri =
        extractVideoDownloadUri(completedOp.response)
          ?: return Result.failure(
            IllegalStateException("Veo operation completed but did not include a downloadable MP4 URI.")
          )

      onProgress("Downloading rendered MP4 video…")
      val mediaId = "vid_${UUID.randomUUID()}"
      val destFile = File(mediaDir, "$mediaId.mp4")
      downloadHttpsWithApiKey(videoUri, apiKey, destFile).getOrElse { dlErr ->
        return Result.failure(dlErr)
      }

      Result.success(
        GeneratedVideoResult(
          mediaId = mediaId,
          prompt = prompt,
          isImageToVideo = sourceImage != null,
          filePath = destFile.absolutePath,
          mimeType = "video/mp4",
          durationSeconds = 6,
          providerModel = modelId,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(IllegalStateException(e.localizedMessage ?: "Video generation failed.", e))
    }
  }

  suspend fun generateMusic(
    prompt: String,
    settings: AppSettings,
    onProgress: suspend (String) -> Unit,
  ): Result<GeneratedMusicResult> {
    val apiKey = settings.geminiApiKey.trim()
    if (apiKey.isEmpty()) {
      return Result.failure(
        IllegalStateException(
          "AI Music generation (Lyria / native audio synthesis) requires a Gemini API key with billing/audio generation access in Settings."
        )
      )
    }

    val modelId = DEFAULT_LYRIA_MODEL
    return try {
      onProgress("Composing instrumental/vocal audio with $modelId…")
      val response =
        geminiApiService.generateContent(
          model = modelId,
          apiKey = apiKey,
          request =
            GeminiGenerateRequest(
              contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt)))),
              generationConfig =
                GeminiGenerationConfig(
                  temperature = 0.9,
                  responseModalities = listOf("AUDIO", "TEXT"),
                ),
            ),
        )

      if (!response.isSuccessful) {
        val code = response.code()
        val msg =
          when (code) {
            401, 403 ->
              "Gemini API key lacks billing/permissions for AI Music model $modelId (HTTP $code)."
            404 ->
              "AI Music model ($modelId) is not enabled for your Google AI Studio tier (HTTP 404)."
            429 -> "AI Music rate limit reached (HTTP 429). Retry shortly."
            else -> "AI Music generation failed (HTTP $code)."
          }
        return Result.failure(IllegalStateException(msg))
      }

      val parts = response.body()?.candidates?.firstOrNull()?.content?.parts.orEmpty()
      val audioPart =
        parts.firstOrNull { p ->
          p.inlineData != null &&
            p.inlineData.mimeType.startsWith("audio/", ignoreCase = true) &&
            p.inlineData.data.isNotBlank()
        }?.inlineData
      val notes = parts.mapNotNull { it.text }.joinToString("\n").trim()

      if (audioPart == null) {
        return Result.failure(
          IllegalStateException(
            notes.ifBlank {
              "The model ($modelId) did not return audio bytes. Verify Lyria / audio output access on your API key."
            }
          )
        )
      }

      val ext =
        when {
          audioPart.mimeType.contains("mpeg", ignoreCase = true) || audioPart.mimeType.contains("mp3", ignoreCase = true) -> "mp3"
          audioPart.mimeType.contains("ogg", ignoreCase = true) -> "ogg"
          else -> "wav"
        }
      val mediaId = "mus_${UUID.randomUUID()}"
      val destFile = File(mediaDir, "$mediaId.$ext")
      withContext(Dispatchers.IO) {
        destFile.writeBytes(Base64.decode(audioPart.data, Base64.DEFAULT))
      }

      Result.success(
        GeneratedMusicResult(
          mediaId = mediaId,
          prompt = prompt,
          filePath = destFile.absolutePath,
          mimeType = audioPart.mimeType,
          durationSeconds = 15,
          providerModel = modelId,
          lyricsOrNotes = notes,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(IllegalStateException(e.localizedMessage ?: "AI Music generation failed.", e))
    }
  }

  fun getShareableMediaUri(filePath: String): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(filePath))

  suspend fun saveVideoToGallery(filePath: String, title: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(filePath)
        if (!file.exists()) throw IOException("Video file not found on disk.")
        val cleanName =
          title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "Mousomi_Video" } +
            "_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val values =
            ContentValues().apply {
              put(MediaStore.Video.Media.DISPLAY_NAME, cleanName)
              put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
              put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Mousomi AI")
              put(MediaStore.Video.Media.IS_PENDING, 1)
            }
          val resolver = context.contentResolver
          val uri =
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
              ?: throw IOException("Unable to create video MediaStore entry.")
          resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IOException("Unable to write video to MediaStore.")
          values.clear()
          values.put(MediaStore.Video.Media.IS_PENDING, 0)
          resolver.update(uri, values, null, null)
          uri
        } else {
          getShareableMediaUri(filePath)
        }
      }
    }

  suspend fun saveAudioToMusicFolder(filePath: String, title: String, mimeType: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(filePath)
        if (!file.exists()) throw IOException("Audio file not found on disk.")
        val ext = file.extension.ifBlank { "wav" }
        val cleanName =
          title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "Mousomi_Music" } +
            "_${System.currentTimeMillis()}.$ext"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val values =
            ContentValues().apply {
              put(MediaStore.Audio.Media.DISPLAY_NAME, cleanName)
              put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
              put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Mousomi AI")
              put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
          val resolver = context.contentResolver
          val uri =
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
              ?: throw IOException("Unable to create audio MediaStore entry.")
          resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IOException("Unable to write audio to MediaStore.")
          values.clear()
          values.put(MediaStore.Audio.Media.IS_PENDING, 0)
          resolver.update(uri, values, null, null)
          uri
        } else {
          getShareableMediaUri(filePath)
        }
      }
    }

  private fun extractVideoDownloadUri(responseObj: JsonObject?): String? {
    responseObj ?: return null
    val genResp = responseObj["generateVideoResponse"]?.let { it as? JsonObject }
    val samples: JsonArray? =
      genResp?.get("generatedSamples")?.jsonArray
        ?: responseObj["generatedVideos"]?.jsonArray
        ?: responseObj["videos"]?.jsonArray
    val first = samples?.firstOrNull()?.jsonObject ?: return null
    val videoObj = first["video"]?.jsonObject ?: first
    return (videoObj["uri"] as? JsonPrimitive)?.contentOrNull
  }

  private suspend fun downloadHttpsWithApiKey(
    url: String,
    apiKey: String,
    destFile: File,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (!securityValidator.validateHttpsResourceUrl(url)) {
          throw IOException("Blocked non-HTTPS video download URI.")
        }
        val req =
          Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            throw IOException("Failed to download generated video (HTTP ${resp.code}).")
          }
          val body = resp.body ?: throw IOException("Empty video response body.")
          destFile.outputStream().use { out -> body.byteStream().copyTo(out) }
          Unit
        }
      }
    }

  companion object {
    const val DEFAULT_VEO_MODEL = "veo-3.0-generate-preview"
    const val DEFAULT_LYRIA_MODEL = "lyria-3-clip-preview"
  }
}
