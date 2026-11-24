package com.example.core.screen

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import com.example.core.storage.TempMediaCacheManager
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Captures Mousomi AI's OWN app window or root View strictly when drawing inside Mousomi's UI
 * without MediaProjection screen sharing active. Never captures external apps' windows.
 */
object InternalViewCaptureHelper {

  data class InternalCaptureResult(
    val file: File,
    val metadata: ScreenCaptureMetadata,
  )

  suspend fun captureInternalRootView(
    rootView: View?,
    window: Window? = null,
    tempMediaCacheManager: TempMediaCacheManager,
  ): InternalCaptureResult? {
    val view = rootView ?: return null
    val w = view.width
    val h = view.height
    if (w < 8 || h < 8) return null

    val bitmap =
      withContext(Dispatchers.Main.immediate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && window != null) {
          pixelCopyWindow(window, w, h) ?: drawViewSoftware(view, w, h)
        } else {
          drawViewSoftware(view, w, h)
        }
      } ?: return null

    return withContext(Dispatchers.IO) {
      try {
        val baos = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        if (!ok || baos.size() <= 0) return@withContext null
        val saved = tempMediaCacheManager.saveTempPng(baos.toByteArray(), prefix = "sel_internal_")
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        InternalCaptureResult(
          file = saved,
          metadata =
            ScreenCaptureMetadata(
              bitmapWidth = bitmap.width,
              bitmapHeight = bitmap.height,
              overlayWidthPx = w,
              overlayHeightPx = h,
              overlayOffsetX = 0,
              overlayOffsetY = 0,
              density = view.resources.displayMetrics.density,
            ),
        )
      } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
      }
    }
  }

  fun findWindow(view: View?): Window? {
    val ctx = view?.context ?: return null
    if (ctx is Activity) return ctx.window
    return null
  }

  private fun drawViewSoftware(view: View, width: Int, height: Int): Bitmap? =
    runCatching {
      val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      view.draw(canvas)
      bmp
    }.getOrNull()

  private suspend fun pixelCopyWindow(window: Window, width: Int, height: Int): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val dest = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
    return suspendCancellableCoroutine { cont ->
      runCatching {
        PixelCopy.request(
          window,
          dest,
          { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
              cont.resume(dest)
            } else {
              if (!dest.isRecycled) dest.recycle()
              cont.resume(null)
            }
          },
          Handler(Looper.getMainLooper()),
        )
      }.onFailure {
        if (!dest.isRecycled) dest.recycle()
        cont.resume(null)
      }
    }
  }
}
