package com.example.core.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.core.storage.TempMediaCacheManager
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates authorized screenshot capture for Live Screen Selection:
 * - Captures from an active MediaProjection session without any silent/unconsented capture.
 * - Preserves full-resolution frame cleanly in temp storage so "Edit images" can re-crop without quality loss.
 */
@Singleton
class ScreenshotCaptureManager @Inject constructor(
  private val mediaProjectionManager: MediaProjectionManager,
  private val screenShareSessionManager: ScreenShareSessionManager,
  private val tempMediaCacheManager: TempMediaCacheManager,
) {

  sealed interface CaptureOutcome {
    data class Success(
      val filePath: String,
      val metadata: ScreenCaptureMetadata,
    ) : CaptureOutcome

    data object ConsentRequired : CaptureOutcome

    data class BlockedOrFailed(val reason: String) : CaptureOutcome
  }

  suspend fun captureAuthorizedScreenFrame(
    overlayWidthPx: Int = 0,
    overlayHeightPx: Int = 0,
  ): CaptureOutcome {
    if (overlayWidthPx > 16 && overlayHeightPx > 16) {
      mediaProjectionManager.updateOverlayMetrics(overlayWidthPx, overlayHeightPx)
    }

    if (mediaProjectionManager.hasActiveProjection()) {
      val result = mediaProjectionManager.captureFreshFrame()
      if (result.isSuccess) {
        val captured = result.getOrThrow()
        val saved = persistTempSourceFrame(captured.bitmap)
        captured.bitmap.recycle()
        return if (saved != null) {
          CaptureOutcome.Success(
            filePath = saved.absolutePath,
            metadata =
              captured.metadata.copy(
                overlayWidthPx =
                  overlayWidthPx.takeIf { it > 16 } ?: captured.metadata.overlayWidthPx,
                overlayHeightPx =
                  overlayHeightPx.takeIf { it > 16 } ?: captured.metadata.overlayHeightPx,
              ),
          )
        } else {
          CaptureOutcome.BlockedOrFailed("Unable to store temporary captured frame.")
        }
      } else {
        val err = result.exceptionOrNull()
        val existingFrame = screenShareSessionManager.getLatestFrame()
        if (existingFrame != null && File(existingFrame.filePath).exists()) {
          return buildOutcomeFromExistingFile(existingFrame.filePath, overlayWidthPx, overlayHeightPx)
        }
        return CaptureOutcome.BlockedOrFailed(
          err?.message ?: "Unable to capture screen frame."
        )
      }
    }

    // Fallback only if user already authorized periodic ScreenShareSessionManager for this session
    val latestShareFrame = screenShareSessionManager.getLatestFrame()
    if (latestShareFrame != null && File(latestShareFrame.filePath).exists()) {
      return buildOutcomeFromExistingFile(latestShareFrame.filePath, overlayWidthPx, overlayHeightPx)
    }

    return CaptureOutcome.ConsentRequired
  }

  private suspend fun persistTempSourceFrame(bitmap: Bitmap): File? =
    withContext(Dispatchers.IO) {
      runCatching {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
        tempMediaCacheManager.saveTempJpeg(baos.toByteArray(), prefix = "sel_full_")
      }.getOrNull()
    }

  private suspend fun buildOutcomeFromExistingFile(
    filePath: String,
    overlayWidthPx: Int,
    overlayHeightPx: Int,
  ): CaptureOutcome =
    withContext(Dispatchers.IO) {
      val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(filePath, opts)
      if (opts.outWidth < 2 || opts.outHeight < 2) {
        return@withContext CaptureOutcome.BlockedOrFailed("Captured screen frame is invalid.")
      }
      CaptureOutcome.Success(
        filePath = filePath,
        metadata =
          ScreenCaptureMetadata(
            bitmapWidth = opts.outWidth,
            bitmapHeight = opts.outHeight,
            overlayWidthPx = overlayWidthPx.takeIf { it > 16 } ?: opts.outWidth,
            overlayHeightPx = overlayHeightPx.takeIf { it > 16 } ?: opts.outHeight,
          ),
      )
    }
}
