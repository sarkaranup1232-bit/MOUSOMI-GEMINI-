package com.example.core.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages the Android [MediaProjection] session, crisp VirtualDisplay + ImageReader surface,
 * Android 14+ content resize callbacks, FLAG_SECURE blank-frame detection, and deterministic cleanup.
 */
@Singleton
class MediaProjectionManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val handler = Handler(Looper.getMainLooper())
  private val lock = Any()

  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null

  private var targetWidthPx: Int = 1080
  private var targetHeightPx: Int = 2400
  private var overlayWidthPx: Int = 1080
  private var overlayHeightPx: Int = 2400
  private var densityDpi: Int = context.resources.displayMetrics.densityDpi
  private var isPartialProjection: Boolean = false
  private var isContentVisible: Boolean = true

  private val _isSessionActive = MutableStateFlow(false)
  val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

  private val _lastFailureReason = MutableStateFlow<String?>(null)
  val lastFailureReason: StateFlow<String?> = _lastFailureReason.asStateFlow()

  private val isCallbackRegistered = AtomicBoolean(false)

  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        release("System or user ended screen capture.")
      }

      override fun onCapturedContentResize(width: Int, height: Int) {
        if (width > 16 && height > 16) {
          synchronized(lock) {
            isPartialProjection =
              kotlin.math.abs(width - overlayWidthPx) > 24 ||
                kotlin.math.abs(height - overlayHeightPx) > 24
            targetWidthPx = width.coerceIn(64, 2160)
            targetHeightPx = height.coerceIn(64, 4096)
          }
          recreateSurface(targetWidthPx, targetHeightPx)
        }
      }

      override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
        isContentVisible = isVisible
      }
    }

  data class CapturedBitmapResult(
    val bitmap: Bitmap,
    val metadata: ScreenCaptureMetadata,
  )

  fun attachActiveProjection(
    projection: MediaProjection,
    alreadyHasCallback: Boolean = false,
  ) {
    val (dispW, dispH, dpi) = queryFullDisplayMetrics()
    synchronized(lock) {
      if (mediaProjection !== projection) {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        if (isCallbackRegistered.getAndSet(false)) {
          runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        }
      }
      mediaProjection = projection
      targetWidthPx = dispW
      targetHeightPx = dispH
      overlayWidthPx = dispW
      overlayHeightPx = dispH
      densityDpi = dpi
      isPartialProjection = false
      isContentVisible = true
      _lastFailureReason.value = null
      _isSessionActive.value = true

      if (!alreadyHasCallback) {
        runCatching {
          projection.registerCallback(projectionCallback, handler)
          isCallbackRegistered.set(true)
        }
      }
    }
    recreateSurface(dispW, dispH)
  }

  fun attachExistingSurface(
    projection: MediaProjection,
    reader: ImageReader,
    display: VirtualDisplay,
    bitmapW: Int,
    bitmapH: Int,
  ) {
    val (dispW, dispH, dpi) = queryFullDisplayMetrics()
    synchronized(lock) {
      mediaProjection = projection
      imageReader = reader
      virtualDisplay = display
      targetWidthPx = bitmapW.coerceAtLeast(64)
      targetHeightPx = bitmapH.coerceAtLeast(64)
      overlayWidthPx = dispW
      overlayHeightPx = dispH
      densityDpi = dpi
      _lastFailureReason.value = null
      _isSessionActive.value = true
    }
  }

  fun hasActiveProjection(): Boolean = mediaProjection != null && _isSessionActive.value

  fun updateOverlayMetrics(widthPx: Int, heightPx: Int) {
    if (widthPx > 16 && heightPx > 16) {
      synchronized(lock) {
        overlayWidthPx = widthPx
        overlayHeightPx = heightPx
      }
    }
  }

  suspend fun captureFreshFrame(timeoutMs: Long = 1600L): Result<CapturedBitmapResult> =
    withContext(Dispatchers.IO) {
      val proj = mediaProjection
      if (proj == null || !_isSessionActive.value) {
        return@withContext Result.failure(
          IllegalStateException("MediaProjection session is not active. Screen capture permission is required.")
        )
      }

      if (imageReader == null || virtualDisplay == null) {
        recreateSurface(targetWidthPx, targetHeightPx)
      }

      val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(400L, 4000L)
      var latestBmp: Bitmap? = null

      while (System.currentTimeMillis() < deadline) {
        val reader = synchronized(lock) { imageReader }
        val img = runCatching { reader?.acquireLatestImage() }.getOrNull()
        if (img != null) {
          val converted = convertImageToBitmap(img)
          img.close()
          if (converted != null) {
            latestBmp = converted
            break
          }
        }
        delay(45L)
      }

      val bmp =
        latestBmp
          ?: return@withContext Result.failure(
            IllegalStateException(
              if (!isContentVisible) "Captured app window is currently hidden or minimized."
              else "No frame arrived from screen capture surface. Please try again."
            )
          )

      if (isUniformlyBlackOrBlocked(bmp)) {
        bmp.recycle()
        return@withContext Result.failure(
          SecurityException(
            "Screen content appears protected by FLAG_SECURE or unavailable to capture."
          )
        )
      }

      val (ovW, ovH, partial, dpi) =
        synchronized(lock) {
          Quad(overlayWidthPx, overlayHeightPx, isPartialProjection, densityDpi)
        }
      val metadata =
        ScreenCaptureMetadata(
          bitmapWidth = bmp.width,
          bitmapHeight = bmp.height,
          overlayWidthPx = ovW.coerceAtLeast(bmp.width),
          overlayHeightPx = ovH.coerceAtLeast(bmp.height),
          isPartialAppProjection = partial,
          density = dpi / 160f,
        )
      Result.success(CapturedBitmapResult(bitmap = bmp, metadata = metadata))
    }

  fun recreateSurface(width: Int, height: Int) {
    synchronized(lock) {
      val projection = mediaProjection ?: return
      val safeW = width.coerceIn(64, 2160)
      val safeH = height.coerceIn(64, 4096)
      virtualDisplay?.release()
      virtualDisplay = null
      imageReader?.close()
      imageReader = null

      val reader = ImageReader.newInstance(safeW, safeH, PixelFormat.RGBA_8888, 2)
      imageReader = reader
      virtualDisplay =
        runCatching {
          projection.createVirtualDisplay(
            "MousomiScreenSelect",
            safeW,
            safeH,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
          )
        }.getOrNull()
    }
  }

  fun queryFullDisplayMetrics(): Triple<Int, Int, Int> {
    val metrics = context.resources.displayMetrics
    val dpi = metrics.densityDpi.coerceAtLeast(160)
    val (w, h) =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val bounds = wm?.maximumWindowMetrics?.bounds
        if (bounds != null && bounds.width() > 64 && bounds.height() > 64) {
          bounds.width() to bounds.height()
        } else {
          metrics.widthPixels to metrics.heightPixels
        }
      } else {
        metrics.widthPixels to metrics.heightPixels
      }
    // Keep high clarity up to 1440px width to keep memory reasonable while crisp for small OCR codes
    val scale = if (w > 1440) 1440f / w else 1f
    return Triple((w * scale).toInt().coerceAtLeast(360), (h * scale).toInt().coerceAtLeast(640), dpi)
  }

  fun release(reason: String? = null) {
    synchronized(lock) {
      virtualDisplay?.release()
      virtualDisplay = null
      imageReader?.close()
      imageReader = null
      if (isCallbackRegistered.getAndSet(false)) {
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
      }
      runCatching { mediaProjection?.stop() }
      mediaProjection = null
      _isSessionActive.value = false
      if (reason != null) {
        _lastFailureReason.value = reason
      }
    }
  }

  private fun convertImageToBitmap(image: Image): Bitmap? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride.coerceAtLeast(1)
    val rowStride = plane.rowStride
    val rowPadding = (rowStride - pixelStride * image.width).coerceAtLeast(0)
    val rawBitmap =
      Bitmap.createBitmap(
        image.width + rowPadding / pixelStride,
        image.height,
        Bitmap.Config.ARGB_8888,
      )
    rawBitmap.copyPixelsFromBuffer(buffer)
    return if (rawBitmap.width > image.width) {
      Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height).also {
        rawBitmap.recycle()
      }
    } else {
      rawBitmap
    }
  }

  private fun isUniformlyBlackOrBlocked(bmp: Bitmap): Boolean {
    val cols = 6
    val rows = 8
    val stepX = (bmp.width / (cols + 1)).coerceAtLeast(1)
    val stepY = (bmp.height / (rows + 1)).coerceAtLeast(1)
    var nonBlackCount = 0
    for (r in 1..rows) {
      for (c in 1..cols) {
        val px = bmp.getPixel((c * stepX).coerceAtMost(bmp.width - 1), (r * stepY).coerceAtMost(bmp.height - 1))
        val rgb = px and 0x00FFFFFF
        if (rgb != 0) {
          nonBlackCount++
          if (nonBlackCount >= 2) return false
        }
      }
    }
    return nonBlackCount == 0
  }

  private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
