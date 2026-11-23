package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import com.example.domain.model.BackgroundReplaceStyle
import com.example.domain.model.ImageAdjustmentParams
import com.example.domain.model.NormPoint
import com.example.domain.model.ProcessedImagePayload
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.FloatBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class LocalCutoutResult(
  val outputFilePath: String,
  val width: Int,
  val height: Int,
  val hasTransparency: Boolean,
  val usedMlKitSegmentation: Boolean,
)

@Singleton
class LocalPhotoEditorEngine @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val tempDir: File by lazy {
    File(context.cacheDir, "image_studio_temp").apply { mkdirs() }
  }

  /**
   * Galaxy M51 memory-safe subsampled decode capped at maxDimensionPx (default 1440px).
   */
  suspend fun decodeSafeBitmap(uriOrPath: String, maxDimensionPx: Int = 1440): Bitmap? =
    withContext(Dispatchers.IO) {
      runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val isFile = !uriOrPath.startsWith("content://") && !uriOrPath.startsWith("file://")
        if (isFile) {
          BitmapFactory.decodeFile(uriOrPath, bounds)
        } else {
          val uri = Uri.parse(uriOrPath)
          context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
        val opts = BitmapFactory.Options().apply {
          inSampleSize = sample
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded =
          if (isFile) {
            BitmapFactory.decodeFile(uriOrPath, opts)
          } else {
            val uri = Uri.parse(uriOrPath)
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
          } ?: return@runCatching null

        scaleDownIfNeeded(decoded, maxDimensionPx)
      }.getOrNull()
    }

  /**
   * Feature A: Local non-destructive ColorMatrix enhancements + fast sharpening + border framing.
   */
  suspend fun applyAdjustments(
    sourceUriOrPath: String,
    params: ImageAdjustmentParams,
    rotateDegrees: Float = 0f,
    flipHorizontal: Boolean = false,
    maxDimensionPx: Int = 1600,
  ): Result<File> =
    withContext(Dispatchers.Default) {
      runCatching {
        val base = decodeSafeBitmap(sourceUriOrPath, maxDimensionPx)
          ?: error("Unable to load source photo for editing.")
        val transformed = transformBitmap(base, rotateDegrees, flipHorizontal)
        if (transformed !== base) base.recycle()

        val colorAdjusted = applyColorAdjustmentsInternal(transformed, params)
        if (colorAdjusted !== transformed) transformed.recycle()

        val sharpened =
          if (params.sharpen > 0.05f) {
            applyFastSharpen(colorAdjusted, params.sharpen).also {
              if (it !== colorAdjusted) colorAdjusted.recycle()
            }
          } else {
            colorAdjusted
          }

        val finalBmp =
          if (params.borderThicknessPx > 0) {
            applyBorder(sharpened, params.borderThicknessPx, params.borderColorHex.toInt()).also {
              if (it !== sharpened) sharpened.recycle()
            }
          } else {
            sharpened
          }

        val outFile = File(tempDir, "edit_${UUID.randomUUID()}.png")
        outFile.outputStream().use { out -> finalBmp.compress(Bitmap.CompressFormat.PNG, 95, out) }
        finalBmp.recycle()
        outFile
      }
    }

  /**
   * Feature B & C: Subject isolation via Play Services ML Kit Subject Segmentation (with deterministic fallback).
   * Supports transparent PNG cutout (with adjustable edge feathering), bokeh portrait blur, or backdrop replacement.
   */
  suspend fun processSubjectIsolation(
    sourceUriOrPath: String,
    style: BackgroundReplaceStyle,
    edgeFeatherPx: Float = 2f,
    portraitBokehAmount: Float = 0f,
    customBackgroundUriOrPath: String? = null,
    maxDimensionPx: Int = 1440,
  ): Result<LocalCutoutResult> =
    withContext(Dispatchers.Default) {
      runCatching {
        val fgBitmap = decodeSafeBitmap(sourceUriOrPath, maxDimensionPx)
          ?: error("Unable to decode portrait/subject image.")
        val w = fgBitmap.width
        val h = fgBitmap.height

        val (confidenceMask, usedMlKit) = extractSubjectConfidenceMask(fgBitmap)
        val featheredAlpha = applyMaskFeathering(confidenceMask, w, h, edgeFeatherPx.coerceIn(0f, 12f))

        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // Draw background layer depending on mode
        if (portraitBokehAmount > 0.5f && style != BackgroundReplaceStyle.TRANSPARENT_PNG) {
          val blurredBg = fastDownscaleBlur(fgBitmap, (portraitBokehAmount / 3f).roundToInt().coerceIn(2, 12))
          val dst = RectF(0f, 0f, w.toFloat(), h.toFloat())
          canvas.drawBitmap(blurredBg, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
          blurredBg.recycle()
        } else if (style != BackgroundReplaceStyle.TRANSPARENT_PNG) {
          drawBackdrop(canvas, w, h, style, customBackgroundUriOrPath)
        }

        // Composite masked foreground subject cleanly onto canvas
        val subjectLayer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        fgBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
          val a = (featheredAlpha[i].coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
          pixels[i] = (a shl 24) or (pixels[i] and 0x00FFFFFF)
        }
        subjectLayer.setPixels(pixels, 0, w, 0, 0, w, h)
        canvas.drawBitmap(subjectLayer, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        subjectLayer.recycle()
        fgBitmap.recycle()

        val hasTransparency = style == BackgroundReplaceStyle.TRANSPARENT_PNG
        val outFile = File(tempDir, "cutout_${UUID.randomUUID()}.png")
        outFile.outputStream().use { out ->
          resultBitmap.compress(Bitmap.CompressFormat.PNG, 96, out)
        }
        resultBitmap.recycle()

        LocalCutoutResult(
          outputFilePath = outFile.absolutePath,
          width = w,
          height = h,
          hasTransparency = hasTransparency,
          usedMlKitSegmentation = usedMlKit,
        )
      }
    }

  /**
   * Feature D: Renders user brush strokes into a crisp binary mask PNG (white painted strokes on black background)
   * matching the target image aspect & Galaxy M51 upload budget (<= 1024px).
   */
  suspend fun renderMaskPngPayload(
    brushPaths: List<List<NormPoint>>,
    brushRadiusNorm: Float,
    targetWidth: Int,
    targetHeight: Int,
  ): Result<ProcessedImagePayload> =
    withContext(Dispatchers.Default) {
      runCatching {
        require(brushPaths.any { it.isNotEmpty() }) { "Paint at least one mask area first." }
        val maxSide = max(targetWidth, targetHeight).coerceAtLeast(1)
        val scale = if (maxSide > 1024) 1024f / maxSide.toFloat() else 1f
        val maskW = (targetWidth * scale).roundToInt().coerceIn(128, 1024)
        val maskH = (targetHeight * scale).roundToInt().coerceIn(128, 1024)

        val maskBmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBmp)
        canvas.drawColor(Color.BLACK)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          style = Paint.Style.STROKE
          strokeCap = Paint.Cap.ROUND
          strokeJoin = Paint.Join.ROUND
          strokeWidth = (brushRadiusNorm * min(maskW, maskH)).coerceAtLeast(10f)
        }

        brushPaths.forEach { pts ->
          if (pts.size == 1) {
            canvas.drawCircle(
              pts.first().x * maskW,
              pts.first().y * maskH,
              strokePaint.strokeWidth / 2f,
              Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
            )
          } else if (pts.size > 1) {
            val path = Path().apply {
              moveTo(pts.first().x * maskW, pts.first().y * maskH)
              for (i in 1 until pts.size) {
                lineTo(pts[i].x * maskW, pts[i].y * maskH)
              }
            }
            canvas.drawPath(path, strokePaint)
          }
        }

        val outStream = ByteArrayOutputStream()
        maskBmp.compress(Bitmap.CompressFormat.PNG, 95, outStream)
        val bytes = outStream.toByteArray()
        maskBmp.recycle()

        val maskFile = File(tempDir, "mask_${UUID.randomUUID()}.png")
        maskFile.writeBytes(bytes)

        ProcessedImagePayload(
          uriString = Uri.fromFile(maskFile).toString(),
          mimeType = "image/png",
          base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
          width = maskW,
          height = maskH,
          byteSize = bytes.size,
          exifSummary = "User inpainting mask",
        )
      }
    }

  /**
   * Feature G / Collage helper: Clean 2x1 or 2x2 grid collage off main thread.
   */
  suspend fun createGridCollage(urisOrPaths: List<String>, outputSizePx: Int = 1440): Result<File> =
    withContext(Dispatchers.Default) {
      runCatching {
        val inputs = urisOrPaths.take(4)
        require(inputs.size >= 2) { "Select 2 to 4 photos for a collage." }
        val cols = if (inputs.size >= 2) 2 else 1
        val rows = if (inputs.size > 2) 2 else 1
        val cellW = outputSizePx / cols
        val cellH = outputSizePx / rows

        val outBmp = Bitmap.createBitmap(outputSizePx, rows * cellH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)
        canvas.drawColor(Color.parseColor("#0F131D"))

        val gap = 12f
        inputs.forEachIndexed { idx, src ->
          val bmp = decodeSafeBitmap(src, cellW)
          if (bmp != null) {
            val col = idx % cols
            val row = idx / cols
            val dst = RectF(
              col * cellW + gap,
              row * cellH + gap,
              (col + 1) * cellW - gap,
              (row + 1) * cellH - gap,
            )
            drawCenterCrop(canvas, bmp, dst)
            bmp.recycle()
          }
        }

        val file = File(tempDir, "collage_${UUID.randomUUID()}.png")
        file.outputStream().use { outBmp.compress(Bitmap.CompressFormat.PNG, 94, it) }
        outBmp.recycle()
        file
      }
    }

  private suspend fun extractSubjectConfidenceMask(bitmap: Bitmap): Pair<FloatArray, Boolean> {
    val w = bitmap.width
    val h = bitmap.height
    val mlKitResult: FloatBuffer? =
      suspendCancellableCoroutine { cont ->
        runCatching {
          val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
          val client = SubjectSegmentation.getClient(options)
          val input = InputImage.fromBitmap(bitmap, 0)
          client.process(input)
            .addOnSuccessListener { result ->
              if (cont.isActive) cont.resume(result.foregroundConfidenceMask)
              runCatching { client.close() }
            }
            .addOnFailureListener {
              if (cont.isActive) cont.resume(null)
              runCatching { client.close() }
            }
        }.onFailure {
          if (cont.isActive) cont.resume(null)
        }
      }

    if (mlKitResult != null && mlKitResult.remaining() >= w * h) {
      val arr = FloatArray(w * h)
      mlKitResult.rewind()
      mlKitResult.get(arr, 0, w * h)
      return arr to true
    }

    // Deterministic offline portrait-centered saliency fallback when GMS segmentation isn't installed
    val arr = FloatArray(w * h)
    val cx = w * 0.5f
    val cy = h * 0.45f
    val rx = w * 0.36f
    val ry = h * 0.42f
    for (y in 0 until h) {
      for (x in 0 until w) {
        val dx = (x - cx) / max(rx, 1f)
        val dy = (y - cy) / max(ry, 1f)
        val dist = hypot(dx, dy)
        val alpha = when {
          dist <= 0.72f -> 1f
          dist >= 1.15f -> 0f
          else -> 1f - ((dist - 0.72f) / 0.43f)
        }
        arr[y * w + x] = alpha.coerceIn(0f, 1f)
      }
    }
    return arr to false
  }

  private fun applyMaskFeathering(mask: FloatArray, w: Int, h: Int, radiusPx: Float): FloatArray {
    val r = radiusPx.roundToInt()
    if (r <= 0) return mask
    val out = FloatArray(w * h)
    val kernelRadius = r.coerceIn(1, 6)
    for (y in 0 until h) {
      for (x in 0 until w) {
        var sum = 0f
        var count = 0
        val minY = (y - kernelRadius).coerceAtLeast(0)
        val maxY = (y + kernelRadius).coerceAtMost(h - 1)
        val minX = (x - kernelRadius).coerceAtLeast(0)
        val maxX = (x + kernelRadius).coerceAtMost(w - 1)
        for (ny in minY..maxY step 2) {
          for (nx in minX..maxX step 2) {
            sum += mask[ny * w + nx]
            count++
          }
        }
        out[y * w + x] = if (count > 0) sum / count else mask[y * w + x]
      }
    }
    return out
  }

  private suspend fun drawBackdrop(
    canvas: Canvas,
    w: Int,
    h: Int,
    style: BackgroundReplaceStyle,
    customBackgroundUriOrPath: String?,
  ) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    when (style) {
      BackgroundReplaceStyle.TRANSPARENT_PNG -> Unit
      BackgroundReplaceStyle.PURE_WHITE -> canvas.drawColor(Color.WHITE)
      BackgroundReplaceStyle.OBSIDIAN_DARK -> canvas.drawColor(Color.parseColor("#0B101E"))
      BackgroundReplaceStyle.SUNSET_GRADIENT -> {
        paint.shader = LinearGradient(
          0f, 0f, w.toFloat(), h.toFloat(),
          intArrayOf(Color.parseColor("#3B1C32"), Color.parseColor("#A64D79"), Color.parseColor("#F59E0B")),
          null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
      }
      BackgroundReplaceStyle.ROYAL_TEAL -> {
        paint.shader = LinearGradient(
          0f, 0f, w.toFloat(), h.toFloat(),
          intArrayOf(Color.parseColor("#042F2E"), Color.parseColor("#0F766E"), Color.parseColor("#14B8A6")),
          null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
      }
      BackgroundReplaceStyle.CUSTOM_PHOTO -> {
        val bg = customBackgroundUriOrPath?.let { decodeSafeBitmap(it, max(w, h)) }
        if (bg != null) {
          drawCenterCrop(canvas, bg, RectF(0f, 0f, w.toFloat(), h.toFloat()))
          bg.recycle()
        } else {
          canvas.drawColor(Color.parseColor("#131A2C"))
        }
      }
    }
  }

  private fun applyColorAdjustmentsInternal(src: Bitmap, params: ImageAdjustmentParams): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    val cm = ColorMatrix()
    val sat = ColorMatrix().apply { setSaturation(params.saturation.coerceIn(0f, 2f)) }
    cm.postConcat(sat)

    val c = params.contrast.coerceIn(0.5f, 1.8f)
    val b = params.brightness.coerceIn(-1f, 1f) * 85f
    val translate = (-0.5f * c + 0.5f) * 255f + b
    val wShift = params.warmth.coerceIn(-1f, 1f) * 28f
    val contrastWarmth = ColorMatrix(
      floatArrayOf(
        c, 0f, 0f, 0f, translate + wShift,
        0f, c, 0f, 0f, translate + (wShift * 0.25f),
        0f, 0f, c, 0f, translate - wShift,
        0f, 0f, 0f, 1f, 0f,
      )
    )
    cm.postConcat(contrastWarmth)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      colorFilter = ColorMatrixColorFilter(cm)
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return out
  }

  private fun applyFastSharpen(src: Bitmap, strength: Float): Bitmap {
    val w = src.width
    val h = src.height
    if (w < 3 || h < 3) return src
    val amount = strength.coerceIn(0f, 1f) * 0.85f
    val inPx = IntArray(w * h)
    val outPx = IntArray(w * h)
    src.getPixels(inPx, 0, w, 0, 0, w, h)
    System.arraycopy(inPx, 0, outPx, 0, inPx.size)
    for (y in 1 until h - 1) {
      val row = y * w
      for (x in 1 until w - 1) {
        val idx = row + x
        val center = inPx[idx]
        val up = inPx[idx - w]
        val down = inPx[idx + w]
        val left = inPx[idx - 1]
        val right = inPx[idx + 1]

        val cr = (center ushr 16) and 0xFF
        val cg = (center ushr 8) and 0xFF
        val cb = center and 0xFF

        val avgR = (((up ushr 16) and 0xFF) + ((down ushr 16) and 0xFF) + ((left ushr 16) and 0xFF) + ((right ushr 16) and 0xFF)) shr 2
        val avgG = (((up ushr 8) and 0xFF) + ((down ushr 8) and 0xFF) + ((left ushr 8) and 0xFF) + ((right ushr 8) and 0xFF)) shr 2
        val avgB = ((up and 0xFF) + (down and 0xFF) + (left and 0xFF) + (right and 0xFF)) shr 2

        val nr = (cr + amount * (cr - avgR)).roundToInt().coerceIn(0, 255)
        val ng = (cg + amount * (cg - avgG)).roundToInt().coerceIn(0, 255)
        val nb = (cb + amount * (cb - avgB)).roundToInt().coerceIn(0, 255)
        outPx[idx] = (center and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
      }
    }
    return Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
  }

  private fun applyBorder(src: Bitmap, borderPx: Int, colorArgb: Int): Bitmap {
    val b = borderPx.coerceIn(1, 64)
    val out = Bitmap.createBitmap(src.width + b * 2, src.height + b * 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(colorArgb)
    canvas.drawBitmap(src, b.toFloat(), b.toFloat(), null)
    return out
  }

  private fun fastDownscaleBlur(src: Bitmap, radiusDiv: Int): Bitmap {
    val factor = radiusDiv.coerceIn(2, 12)
    val smallW = (src.width / factor).coerceAtLeast(16)
    val smallH = (src.height / factor).coerceAtLeast(16)
    val down = Bitmap.createScaledBitmap(src, smallW, smallH, true)
    val up = Bitmap.createScaledBitmap(down, src.width, src.height, true)
    down.recycle()
    return up
  }

  private fun transformBitmap(src: Bitmap, rotateDegrees: Float, flipHorizontal: Boolean): Bitmap {
    if (abs(rotateDegrees) < 0.5f && !flipHorizontal) return src
    val matrix = Matrix().apply {
      if (flipHorizontal) postScale(-1f, 1f)
      if (abs(rotateDegrees) >= 0.5f) postRotate(rotateDegrees)
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
  }

  private fun drawCenterCrop(canvas: Canvas, bmp: Bitmap, dst: RectF) {
    val scale = max(dst.width() / bmp.width.toFloat(), dst.height() / bmp.height.toFloat())
    val drawW = bmp.width * scale
    val drawH = bmp.height * scale
    val left = dst.left + (dst.width() - drawW) / 2f
    val top = dst.top + (dst.height() - drawH) / 2f
    canvas.save()
    canvas.clipRect(dst)
    canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
  }

  private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
    val maxSide = max(bitmap.width, bitmap.height)
    if (maxSide <= maxDimensionPx) return bitmap
    val ratio = maxDimensionPx.toFloat() / maxSide.toFloat()
    val w = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
    val h = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
  }

  private fun calculateInSampleSize(rawW: Int, rawH: Int, maxDim: Int): Int {
    var sample = 1
    while ((rawW / sample) > maxDim * 2 || (rawH / sample) > maxDim * 2) {
      sample *= 2
    }
    return sample
  }
}
