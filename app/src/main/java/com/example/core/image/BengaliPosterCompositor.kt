package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.model.PosterDesignSpec
import com.example.domain.model.PosterFontChoice
import com.example.domain.model.PosterTextLayer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic Layer B Typography & Poster Compositor.
 * Uses Android StaticLayout + bundled local Bengali (.ttf) & English display fonts so complex
 * Bengali Unicode conjuncts and vowel signs render crisply offline with zero character garbling.
 */
@Singleton
class BengaliPosterCompositor @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val outputDir: File by lazy {
    File(context.filesDir, "mousomi_generated_images").apply { mkdirs() }
  }

  suspend fun renderPoster(
    spec: PosterDesignSpec,
    targetScaleForPreview: Float = 1f,
  ): Result<File> =
    withContext(Dispatchers.Default) {
      runCatching {
        val scale = targetScaleForPreview.coerceIn(0.35f, 1f)
        val width = (spec.aspectRatio.widthPx * scale).roundToInt().coerceAtLeast(360)
        val height = (spec.aspectRatio.heightPx * scale).roundToInt().coerceAtLeast(360)

        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)

        // 1. Background: Custom photo (center-cropped) or template gradient
        val bgPath = spec.backgroundPhotoPath
        val customBg = bgPath?.let { loadCappedBitmap(it, max(width, height)) }
        if (customBg != null) {
          drawCenterCrop(canvas, customBg, width, height)
          customBg.recycle()
          // Subtle readability scrim
          val scrim = Paint().apply { color = Color.argb(92, 8, 12, 24) }
          canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        } else {
          val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
              0f, 0f, width.toFloat(), height.toFloat(),
              intArrayOf(
                spec.template.startColor.toInt(),
                spec.template.endColor.toInt(),
              ),
              null,
              Shader.TileMode.CLAMP,
            )
          }
          canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

          // Crisp geometric accent frame
          val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.template.accentColor.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f * scale
            alpha = 155
          }
          val inset = 38f * scale
          canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            28f * scale,
            28f * scale,
            borderPaint,
          )
        }

        // 2. Render each Bengali / English StaticLayout text layer
        spec.layers.forEach { layer ->
          if (layer.text.isNotBlank()) {
            drawTextLayer(canvas, width, height, layer, scale)
          }
        }

        val outFile = File(outputDir, "poster_${UUID.randomUUID()}.png")
        outFile.outputStream().use { stream ->
          outBitmap.compress(Bitmap.CompressFormat.PNG, 96, stream)
        }
        outBitmap.recycle()
        outFile
      }
    }

  private fun drawTextLayer(
    canvas: Canvas,
    canvasW: Int,
    canvasH: Int,
    layer: PosterTextLayer,
    scale: Float,
  ) {
    val typeface = resolveBundledTypeface(layer.fontChoice)
    val horizontalPad = canvasW * 0.10f
    val maxTextWidth = (canvasW - horizontalPad * 2f).roundToInt().coerceAtLeast(120)
    val fontSizePx = layer.fontSizeSp * 2.2f * scale

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      this.typeface = typeface
      textSize = fontSizePx
      color = layer.textColorArgb.toInt()
      if (layer.shadowRadius > 0.2f) {
        setShadowLayer(layer.shadowRadius * scale, 0f, 3f * scale, Color.argb(175, 0, 0, 0))
      }
    }

    val alignment =
      if (layer.isCentered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL

    val layout =
      StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, textPaint, maxTextWidth)
        .setAlignment(alignment)
        .setLineSpacing(0f, 1.18f)
        .setIncludePad(true)
        .build()

    val blockTop = (canvasH * layer.yPositionFraction - layout.height * 0.5f)
      .coerceIn(48f * scale, (canvasH - layout.height - 48f * scale).coerceAtLeast(48f * scale))

    // Optional rounded pill/card backdrop behind text
    if (layer.backdropOpacity > 0.02f) {
      val bgAlpha = (layer.backdropOpacity.coerceIn(0f, 1f) * 255).roundToInt()
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(bgAlpha, 8, 12, 22)
      }
      val boxPadX = 28f * scale
      val boxPadY = 18f * scale
      canvas.drawRoundRect(
        RectF(
          horizontalPad - boxPadX,
          blockTop - boxPadY,
          canvasW - horizontalPad + boxPadX,
          blockTop + layout.height + boxPadY,
        ),
        22f * scale,
        22f * scale,
        bgPaint,
      )
    }

    canvas.save()
    canvas.translate(horizontalPad, blockTop)

    // Optional outline stroke pass before fill pass
    if (layer.strokeWidthPx > 0.2f) {
      val strokePaint = TextPaint(textPaint).apply {
        style = Paint.Style.STROKE
        strokeWidth = layer.strokeWidthPx * scale
        color = layer.strokeColorArgb.toInt()
        clearShadowLayer()
      }
      val strokeLayout =
        StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, strokePaint, maxTextWidth)
          .setAlignment(alignment)
          .setLineSpacing(0f, 1.18f)
          .setIncludePad(true)
          .build()
      strokeLayout.draw(canvas)
    }

    layout.draw(canvas)
    canvas.restore()
  }

  private fun resolveBundledTypeface(choice: PosterFontChoice): Typeface {
    val resId =
      when (choice) {
        PosterFontChoice.NOTO_SANS_BENGALI -> R.font.noto_sans_bengali
        PosterFontChoice.HIND_SILIGURI -> R.font.hind_siliguri
        PosterFontChoice.PLAYFAIR_DISPLAY -> R.font.playfair_display
        PosterFontChoice.SPACE_GROTESK -> R.font.space_grotesk
        PosterFontChoice.OUTFIT_CLEAN -> R.font.outfit
      }
    return runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull() ?: Typeface.DEFAULT
  }

  private fun loadCappedBitmap(uriOrPath: String, maxDim: Int): Bitmap? =
    runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      val isFile = !uriOrPath.startsWith("content://") && !uriOrPath.startsWith("file://")
      if (isFile) {
        BitmapFactory.decodeFile(uriOrPath, bounds)
      } else {
        context.contentResolver.openInputStream(Uri.parse(uriOrPath))?.use {
          BitmapFactory.decodeStream(it, null, bounds)
        }
      }
      var sample = 1
      while ((bounds.outWidth / sample) > maxDim * 2 || (bounds.outHeight / sample) > maxDim * 2) {
        sample *= 2
      }
      val opts = BitmapFactory.Options().apply { inSampleSize = sample }
      if (isFile) {
        BitmapFactory.decodeFile(uriOrPath, opts)
      } else {
        context.contentResolver.openInputStream(Uri.parse(uriOrPath))?.use {
          BitmapFactory.decodeStream(it, null, opts)
        }
      }
    }.getOrNull()

  private fun drawCenterCrop(canvas: Canvas, bmp: Bitmap, dstW: Int, dstH: Int) {
    val scale = max(dstW.toFloat() / bmp.width.toFloat(), dstH.toFloat() / bmp.height.toFloat())
    val drawW = bmp.width * scale
    val drawH = bmp.height * scale
    val left = (dstW - drawW) / 2f
    val top = (dstH - drawH) / 2f
    canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
  }
}
