package com.example.core.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.NormBoundingBox
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Produces crisp, aspect-preserved cropped image outputs from authorized captured screen frames
 * with rotation support, zero black padding, and deterministic memory cleanup.
 */
@Singleton
class SmartCropEngine @Inject constructor(
  private val coordinateMapper: CropCoordinateMapper,
  private val tempMediaCacheManager: TempMediaCacheManager,
) {

  data class CroppedImageOutput(
    val file: File,
    val width: Int,
    val height: Int,
    val normBoxUsed: NormBoundingBox,
    val rotationDegrees: Int,
  )

  suspend fun cropFromFile(
    sourceFilePath: String,
    normBox: NormBoundingBox,
    metadata: ScreenCaptureMetadata? = null,
    rotationDegrees: Int = 0,
    prefix: String = "sel_crop_",
  ): Result<CroppedImageOutput> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sourceFile = File(sourceFilePath)
        require(sourceFile.exists() && sourceFile.length() > 0L) {
          "Source capture frame is unavailable for cropping."
        }
        val decoded =
          BitmapFactory.decodeFile(sourceFilePath)
            ?: error("Unable to decode captured screen frame.")
        try {
          cropFromBitmapInternal(
            source = decoded,
            normBox = normBox,
            metadata = metadata,
            rotationDegrees = rotationDegrees,
            prefix = prefix,
          )
        } finally {
          if (!decoded.isRecycled) decoded.recycle()
        }
      }
    }

  fun cropBitmap(
    source: Bitmap,
    normBox: NormBoundingBox,
    metadata: ScreenCaptureMetadata? = null,
    rotationDegrees: Int = 0,
  ): Bitmap? {
    if (source.width < 2 || source.height < 2 || !normBox.isValid()) return null
    val effectiveMeta =
      metadata?.copy(bitmapWidth = source.width, bitmapHeight = source.height)
        ?: ScreenCaptureMetadata(
          bitmapWidth = source.width,
          bitmapHeight = source.height,
          overlayWidthPx = source.width,
          overlayHeightPx = source.height,
        )
    val cropRect = coordinateMapper.mapToBitmapRect(normBox, effectiveMeta)
    val cropW = (cropRect.width()).coerceIn(2, source.width - cropRect.left)
    val cropH = (cropRect.height()).coerceIn(2, source.height - cropRect.top)
    val cropped = Bitmap.createBitmap(source, cropRect.left, cropRect.top, cropW, cropH)

    val normRot = ((rotationDegrees % 360) + 360) % 360
    if (normRot == 0) return cropped

    val matrix = Matrix().apply { postRotate(normRot.toFloat()) }
    val rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
    if (rotated != cropped && !cropped.isRecycled) {
      cropped.recycle()
    }
    return rotated
  }

  private fun cropFromBitmapInternal(
    source: Bitmap,
    normBox: NormBoundingBox,
    metadata: ScreenCaptureMetadata?,
    rotationDegrees: Int,
    prefix: String,
  ): CroppedImageOutput {
    val resultBmp =
      cropBitmap(
        source = source,
        normBox = normBox,
        metadata = metadata,
        rotationDegrees = rotationDegrees,
      ) ?: error("Selected region is too small or invalid to crop.")

    try {
      require(resultBmp.width >= 2 && resultBmp.height >= 2) {
        "Cropped region dimensions are invalid (${resultBmp.width}x${resultBmp.height})."
      }
      val baos = ByteArrayOutputStream()
      // Use high-quality JPEG or lossless PNG for crisp small text/codes
      val compressed = resultBmp.compress(Bitmap.CompressFormat.JPEG, 94, baos)
      require(compressed && baos.size() > 0) { "Failed to encode cropped selection image." }
      val file = tempMediaCacheManager.saveTempJpeg(baos.toByteArray(), prefix = prefix)
      return CroppedImageOutput(
        file = file,
        width = resultBmp.width,
        height = resultBmp.height,
        normBoxUsed = normBox,
        rotationDegrees = ((rotationDegrees % 360) + 360) % 360,
      )
    } finally {
      if (!resultBmp.isRecycled) resultBmp.recycle()
    }
  }
}
