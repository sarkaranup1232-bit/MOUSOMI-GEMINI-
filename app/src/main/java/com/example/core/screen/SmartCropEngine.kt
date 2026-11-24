package com.example.core.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Produces crisp, aspect-preserved cropped image outputs from authorized captured screen frames:
 * - Supports rectangular and circular/freehand-polygon selections with clean alpha masking
 * - Compensates for overlay coordinates, display scale, and rotation
 * - Clamps output dimensions & byte size cleanly within multimodal model limits
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
    val mimeType: String = "image/jpeg",
    val hasTransparencyMask: Boolean = false,
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
            simplifiedContour = emptyList(),
            detectedShape = DetectedRegionShape.RECTANGLE,
            metadata = metadata,
            rotationDegrees = rotationDegrees,
            prefix = prefix,
            maxDimensionPx = DEFAULT_MAX_DIMENSION_PX,
            maxByteSize = DEFAULT_MAX_BYTES,
          )
        } finally {
          if (!decoded.isRecycled) decoded.recycle()
        }
      }
    }

  suspend fun cropSelectionToFile(
    sourceFilePath: String,
    snappedResult: SelectionGeometryResult,
    metadata: ScreenCaptureMetadata? = null,
    rotationDegrees: Int = 0,
    prefix: String = "sel_crop_",
    maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX,
    maxByteSize: Int = DEFAULT_MAX_BYTES,
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
            normBox = snappedResult.boundingBox,
            simplifiedContour = snappedResult.simplifiedContour,
            detectedShape = snappedResult.detectedShape,
            metadata = metadata,
            rotationDegrees = rotationDegrees,
            prefix = prefix,
            maxDimensionPx = maxDimensionPx,
            maxByteSize = maxByteSize,
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

  fun cropBitmapWithMask(
    source: Bitmap,
    normBox: NormBoundingBox,
    simplifiedContour: List<NormPoint>,
    detectedShape: DetectedRegionShape,
    metadata: ScreenCaptureMetadata? = null,
    rotationDegrees: Int = 0,
  ): Bitmap? {
    val rectCropped = cropBitmap(source, normBox, metadata, rotationDegrees = 0) ?: return null
    val shouldMaskFreehand =
      (detectedShape == DetectedRegionShape.FREEHAND_LOOP ||
        detectedShape == DetectedRegionShape.CIRCLE ||
        detectedShape == DetectedRegionShape.OVAL) &&
        simplifiedContour.size >= 3

    val maskedOrRect =
      if (!shouldMaskFreehand) {
        rectCropped
      } else {
        val masked = Bitmap.createBitmap(rectCropped.width, rectCropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(masked)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val maskPaint =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
          }
        val path = Path()
        val boxW = (normBox.right - normBox.left).coerceAtLeast(0.005f)
        val boxH = (normBox.bottom - normBox.top).coerceAtLeast(0.005f)

        if (detectedShape == DetectedRegionShape.CIRCLE || detectedShape == DetectedRegionShape.OVAL) {
          path.addOval(0f, 0f, rectCropped.width.toFloat(), rectCropped.height.toFloat(), Path.Direction.CW)
        } else {
          simplifiedContour.forEachIndexed { idx, pt ->
            val localX = (((pt.x - normBox.left) / boxW).coerceIn(0f, 1f)) * rectCropped.width
            val localY = (((pt.y - normBox.top) / boxH).coerceIn(0f, 1f)) * rectCropped.height
            if (idx == 0) path.moveTo(localX, localY) else path.lineTo(localX, localY)
          }
          path.close()
        }

        canvas.drawPath(path, maskPaint)
        val xferPaint =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
          }
        canvas.drawBitmap(rectCropped, 0f, 0f, xferPaint)
        if (!rectCropped.isRecycled) rectCropped.recycle()
        masked
      }

    val normRot = ((rotationDegrees % 360) + 360) % 360
    if (normRot == 0) return maskedOrRect

    val matrix = Matrix().apply { postRotate(normRot.toFloat()) }
    val rotated = Bitmap.createBitmap(maskedOrRect, 0, 0, maskedOrRect.width, maskedOrRect.height, matrix, true)
    if (rotated != maskedOrRect && !maskedOrRect.isRecycled) {
      maskedOrRect.recycle()
    }
    return rotated
  }

  private fun cropFromBitmapInternal(
    source: Bitmap,
    normBox: NormBoundingBox,
    simplifiedContour: List<NormPoint>,
    detectedShape: DetectedRegionShape,
    metadata: ScreenCaptureMetadata?,
    rotationDegrees: Int,
    prefix: String,
    maxDimensionPx: Int,
    maxByteSize: Int,
  ): CroppedImageOutput {
    val rawCrop =
      cropBitmapWithMask(
        source = source,
        normBox = normBox,
        simplifiedContour = simplifiedContour,
        detectedShape = detectedShape,
        metadata = metadata,
        rotationDegrees = rotationDegrees,
      ) ?: error("Selected region is too small or invalid to crop.")

    var working = downscaleIfNeeded(rawCrop, maxDimensionPx)
    if (working !== rawCrop && !rawCrop.isRecycled) {
      rawCrop.recycle()
    }

    val hasMask =
      (detectedShape == DetectedRegionShape.FREEHAND_LOOP ||
        detectedShape == DetectedRegionShape.CIRCLE ||
        detectedShape == DetectedRegionShape.OVAL) &&
        simplifiedContour.size >= 3

    try {
      require(working.width >= 2 && working.height >= 2) {
        "Cropped region dimensions are invalid (${working.width}x${working.height})."
      }
      val (file, mime) = encodeWithinLimit(working, hasMask, prefix, maxByteSize)
      return CroppedImageOutput(
        file = file,
        width = working.width,
        height = working.height,
        normBoxUsed = normBox,
        rotationDegrees = ((rotationDegrees % 360) + 360) % 360,
        mimeType = mime,
        hasTransparencyMask = hasMask,
      )
    } finally {
      if (!working.isRecycled) working.recycle()
    }
  }

  private fun encodeWithinLimit(
    initialBitmap: Bitmap,
    preferTransparencyPng: Boolean,
    prefix: String,
    maxByteSize: Int,
  ): Pair<File, String> {
    var current = initialBitmap
    val ownedBitmaps = mutableListOf<Bitmap>()
    try {
      if (preferTransparencyPng) {
        for (attempt in 0..3) {
          val baos = ByteArrayOutputStream()
          val ok = current.compress(Bitmap.CompressFormat.PNG, 100, baos)
          require(ok && baos.size() > 0) { "Failed to encode masked selection image." }
          if (baos.size() <= maxByteSize || attempt == 3 || max(current.width, current.height) <= 480) {
            return tempMediaCacheManager.saveTempPng(baos.toByteArray(), prefix = prefix) to "image/png"
          }
          val nextW = (current.width * 0.78f).roundToInt().coerceAtLeast(32)
          val nextH = (current.height * 0.78f).roundToInt().coerceAtLeast(32)
          current = Bitmap.createScaledBitmap(current, nextW, nextH, true).also { ownedBitmaps.add(it) }
        }
      }

      var quality = 94
      while (quality >= 72) {
        val baos = ByteArrayOutputStream()
        val ok = current.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        require(ok && baos.size() > 0) { "Failed to encode cropped selection image." }
        if (baos.size() <= maxByteSize || quality <= 72) {
          return tempMediaCacheManager.saveTempJpeg(baos.toByteArray(), prefix = prefix) to "image/jpeg"
        }
        quality -= 8
      }

      val finalBaos = ByteArrayOutputStream()
      current.compress(Bitmap.CompressFormat.JPEG, 78, finalBaos)
      return tempMediaCacheManager.saveTempJpeg(finalBaos.toByteArray(), prefix = prefix) to "image/jpeg"
    } finally {
      ownedBitmaps.forEach { bmp ->
        if (bmp !== initialBitmap && !bmp.isRecycled) bmp.recycle()
      }
    }
  }

  private fun downscaleIfNeeded(bitmap: Bitmap, maxDimPx: Int): Bitmap {
    val maxSide = max(bitmap.width, bitmap.height)
    if (maxSide <= maxDimPx) return bitmap
    val scale = maxDimPx.toFloat() / maxSide.toFloat()
    val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(2)
    val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(2)
    return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
  }

  companion object {
    const val DEFAULT_MAX_DIMENSION_PX = 1536
    const val DEFAULT_MAX_BYTES = 1_500_000
  }
}
