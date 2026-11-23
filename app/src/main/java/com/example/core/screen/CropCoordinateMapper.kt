package com.example.core.screen

import android.graphics.Rect
import com.example.domain.model.NormBoundingBox
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Translates normalized [0f..1f] UI overlay selection coordinates accurately into pixel [Rect]
 * coordinates in the captured [android.graphics.Bitmap], accounting for overlay offsets,
 * display-to-bitmap scale differences, and partial-projection content aspect ratios.
 */
@Singleton
class CropCoordinateMapper @Inject constructor() {

  fun mapToBitmapRect(
    normBox: NormBoundingBox,
    metadata: ScreenCaptureMetadata,
  ): Rect {
    val bmpW = metadata.bitmapWidth.coerceAtLeast(2)
    val bmpH = metadata.bitmapHeight.coerceAtLeast(2)
    val ovW = metadata.overlayWidthPx.takeIf { it > 1 } ?: bmpW
    val ovH = metadata.overlayHeightPx.takeIf { it > 1 } ?: bmpH

    val clampedLeft = normBox.left.coerceIn(0f, 0.99f)
    val clampedTop = normBox.top.coerceIn(0f, 0.99f)
    val clampedRight = normBox.right.coerceIn(clampedLeft + 0.01f, 1f)
    val clampedBottom = normBox.bottom.coerceIn(clampedTop + 0.01f, 1f)

    // Convert normalized overlay coordinates to overlay-local pixels, then global display pixels
    val globalLeftPx = metadata.overlayOffsetX + clampedLeft * ovW
    val globalTopPx = metadata.overlayOffsetY + clampedTop * ovH
    val globalRightPx = metadata.overlayOffsetX + clampedRight * ovW
    val globalBottomPx = metadata.overlayOffsetY + clampedBottom * ovH

    val mappedRect =
      if (metadata.isPartialAppProjection && ovW > 0 && ovH > 0) {
        // Fit/scale cleanly when Android 14+ single-app capture differs in aspect ratio from full screen
        val ovAspect = ovW.toFloat() / ovH.toFloat()
        val bmpAspect = bmpW.toFloat() / bmpH.toFloat()
        val scaleX: Float
        val scaleY: Float
        val padX: Float
        val padY: Float
        if (kotlin.math.abs(ovAspect - bmpAspect) > 0.05f) {
          val fitScale = minOf(ovW.toFloat() / bmpW, ovH.toFloat() / bmpH)
          val drawnW = bmpW * fitScale
          val drawnH = bmpH * fitScale
          padX = (ovW - drawnW) * 0.5f
          padY = (ovH - drawnH) * 0.5f
          scaleX = bmpW.toFloat() / drawnW.coerceAtLeast(1f)
          scaleY = bmpH.toFloat() / drawnH.coerceAtLeast(1f)
        } else {
          padX = 0f
          padY = 0f
          scaleX = bmpW.toFloat() / ovW.toFloat()
          scaleY = bmpH.toFloat() / ovH.toFloat()
        }
        Rect(
          floor((globalLeftPx - padX) * scaleX).toInt(),
          floor((globalTopPx - padY) * scaleY).toInt(),
          ceil((globalRightPx - padX) * scaleX).toInt(),
          ceil((globalBottomPx - padY) * scaleY).toInt(),
        )
      } else {
        // Full display MediaProjection: map overlay global pixels directly into bitmap coordinates
        val scaleX = bmpW.toFloat() / ovW.toFloat()
        val scaleY = bmpH.toFloat() / ovH.toFloat()
        Rect(
          floor(globalLeftPx * scaleX).toInt(),
          floor(globalTopPx * scaleY).toInt(),
          ceil(globalRightPx * scaleX).toInt(),
          ceil(globalBottomPx * scaleY).toInt(),
        )
      }

    val safeLeft = mappedRect.left.coerceIn(0, bmpW - 2)
    val safeTop = mappedRect.top.coerceIn(0, bmpH - 2)
    val safeRight = mappedRect.right.coerceIn(safeLeft + 2, bmpW)
    val safeBottom = mappedRect.bottom.coerceIn(safeTop + 2, bmpH)
    return Rect(safeLeft, safeTop, safeRight, safeBottom)
  }

  /**
   * Computes a normalized crop rect strictly from an already-cropped sub-rect inside an image view.
   */
  fun mapDisplayFitRectToNorm(
    containerWidthPx: Float,
    containerHeightPx: Float,
    bitmapWidth: Int,
    bitmapHeight: Int,
  ): NormBoundingBox {
    if (containerWidthPx <= 1f || containerHeightPx <= 1f || bitmapWidth <= 0 || bitmapHeight <= 0) {
      return NormBoundingBox(0f, 0f, 1f, 1f)
    }
    val scale = minOf(containerWidthPx / bitmapWidth, containerHeightPx / bitmapHeight)
    val drawnW = bitmapWidth * scale
    val drawnH = bitmapHeight * scale
    val padX = ((containerWidthPx - drawnW) * 0.5f) / containerWidthPx
    val padY = ((containerHeightPx - drawnH) * 0.5f) / containerHeightPx
    return NormBoundingBox(
      left = padX.coerceIn(0f, 0.45f),
      top = padY.coerceIn(0f, 0.45f),
      right = (1f - padX).coerceIn(0.55f, 1f),
      bottom = (1f - padY).coerceIn(0.55f, 1f),
    )
  }
}
