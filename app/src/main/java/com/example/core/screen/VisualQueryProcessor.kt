package com.example.core.screen

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.core.image.ImageProcessor
import com.example.domain.model.ProcessedImagePayload
import com.example.ui.model.AttachmentPreview
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prepares cropped visual selections + optional typed/voice user instructions for multimodal vision
 * analysis, OCR/code explanation, or translation via Mousomi's vision-capable models.
 */
@Singleton
class VisualQueryProcessor @Inject constructor(
  private val imageProcessor: ImageProcessor,
  private val imageAttachmentManager: ImageAttachmentManager,
) {
  data class PreparedVisualQuery(
    val userDisplayPrompt: String,
    val modelPrompt: String,
    val payloads: List<ProcessedImagePayload>,
  )

  suspend fun prepareQuery(
    rawUserPrompt: String,
    attachments: List<AttachmentPreview>,
  ): PreparedVisualQuery =
    withContext(Dispatchers.IO) {
      val trimmed = rawUserPrompt.trim()
      val hasCroppedScreenVisual =
        attachments.any { att ->
          att is AttachmentPreview.PickedPhotoUri &&
            imageAttachmentManager.findByUri(att.uri) != null
        }

      val payloads = mutableListOf<ProcessedImagePayload>()
      attachments.forEach { attachment ->
        if (attachment is AttachmentPreview.PickedPhotoUri) {
          val visual = imageAttachmentManager.findByUri(attachment.uri)
          val directFilePayload = visual?.let { decodeLocalFilePayload(it.croppedFilePath, attachment.uri) }
          if (directFilePayload != null) {
            payloads.add(directFilePayload)
          } else {
            val path = Uri.parse(attachment.uri).path
            val localFile = path?.let(::File)?.takeIf { it.exists() }
            val fallback =
              localFile?.let { decodeLocalFilePayload(it.absolutePath, attachment.uri) }
                ?: imageProcessor.processUri(attachment.uri).getOrNull()
            if (fallback != null) {
              payloads.add(fallback)
            }
          }
        }
      }

      val effectivePrompt =
        when {
          trimmed.isNotEmpty() -> trimmed
          hasCroppedScreenVisual ->
            "Analyze this selected screen region: accurately extract any visible text, codes, prices, or UI elements and explain clearly."
          payloads.isNotEmpty() -> "Analyze the attached image clearly."
          else -> trimmed
        }

      PreparedVisualQuery(
        userDisplayPrompt = if (trimmed.isNotEmpty()) trimmed else "Analyze selected screen region",
        modelPrompt = effectivePrompt,
        payloads = payloads,
      )
    }

  private fun decodeLocalFilePayload(
    filePath: String,
    uriString: String,
  ): ProcessedImagePayload? =
    runCatching {
      val file = File(filePath)
      if (!file.exists() || file.length() <= 0L) return null
      val bytes = file.readBytes()
      val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
      ProcessedImagePayload(
        uriString = uriString,
        mimeType =
          if (filePath.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg",
        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
        width = opts.outWidth.coerceAtLeast(1),
        height = opts.outHeight.coerceAtLeast(1),
        byteSize = bytes.size,
      )
    }.getOrNull()
}
