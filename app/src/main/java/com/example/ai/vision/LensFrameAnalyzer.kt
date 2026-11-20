package com.example.ai.vision

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.core.lens.LensAutoScanTracker
import com.example.core.lens.LensFrameTelemetry
import com.example.domain.model.LensDetectedBox
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time CameraX ImageAnalysis.Analyzer combining:
 * 1. ML Kit On-Device Object Detection (bounding boxes, tracking IDs, category labels)
 * 2. Y-plane luminance grid sampling for exposure & camera-motion stability estimation
 */
class LensFrameAnalyzer(
  private val isFrontCameraProvider: () -> Boolean,
  private val onTelemetry: (LensFrameTelemetry) -> Unit,
) : ImageAnalysis.Analyzer {

  private val options =
    ObjectDetectorOptions.Builder()
      .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
      .enableMultipleObjects()
      .enableClassification()
      .build()

  private val detector: ObjectDetector = ObjectDetection.getClient(options)
  private val isProcessing = AtomicBoolean(false)
  private var lastAnalyzedMs = 0L
  private var previousLumaGrid: FloatArray? = null

  @SuppressLint("UnsafeOptInUsageError")
  override fun analyze(imageProxy: ImageProxy) {
    val nowMs = System.currentTimeMillis()
    val mediaImage = imageProxy.image
    if (mediaImage == null || nowMs - lastAnalyzedMs < 240L || !isProcessing.compareAndSet(false, true)) {
      imageProxy.close()
      return
    }
    lastAnalyzedMs = nowMs

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val rawWidth = imageProxy.width
    val rawHeight = imageProxy.height
    val (orientedW, orientedH) =
      if (rotationDegrees == 90 || rotationDegrees == 270) {
        rawHeight.toFloat() to rawWidth.toFloat()
      } else {
        rawWidth.toFloat() to rawHeight.toFloat()
      }

    val (avgLuma, currentGrid) = sampleYPlaneGrid(imageProxy)
    val motionDelta = LensAutoScanTracker.estimateMotionDelta(previousLumaGrid, currentGrid)
    previousLumaGrid = currentGrid

    val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
    val isFront = isFrontCameraProvider()

    detector
      .process(inputImage)
      .addOnSuccessListener { objects ->
        val mapped =
          objects.mapNotNull { obj ->
            mapDetectedObject(obj, orientedW, orientedH, isFront)
          }
        onTelemetry(
          LensFrameTelemetry(
            detectedBoxes = mapped,
            avgLuma = avgLuma,
            motionDelta = motionDelta,
            timestampMs = nowMs,
          )
        )
      }
      .addOnFailureListener {
        onTelemetry(
          LensFrameTelemetry(
            detectedBoxes = emptyList(),
            avgLuma = avgLuma,
            motionDelta = motionDelta,
            timestampMs = nowMs,
          )
        )
      }
      .addOnCompleteListener {
        isProcessing.set(false)
        imageProxy.close()
      }
  }

  fun close() {
    runCatching { detector.close() }
  }

  private fun mapDetectedObject(
    obj: DetectedObject,
    imgW: Float,
    imgH: Float,
    mirrorHorizontal: Boolean,
  ): LensDetectedBox? {
    if (imgW <= 1f || imgH <= 1f) return null
    val r: Rect = obj.boundingBox
    val rawLeft = (r.left.toFloat() / imgW).coerceIn(0f, 1f)
    val rawRight = (r.right.toFloat() / imgW).coerceIn(0f, 1f)
    val top = (r.top.toFloat() / imgH).coerceIn(0f, 1f)
    val bottom = (r.bottom.toFloat() / imgH).coerceIn(0f, 1f)

    val (left, right) =
      if (mirrorHorizontal) {
        (1f - rawRight) to (1f - rawLeft)
      } else {
        rawLeft to rawRight
      }

    if (right - left < 0.03f || bottom - top < 0.03f) return null
    val bestLabel =
      obj.labels
        .maxByOrNull { it.confidence }
        ?.text
        ?.takeIf { it.isNotBlank() }
        ?: "Object"

    return LensDetectedBox(
      trackingId = obj.trackingId,
      label = bestLabel,
      normLeft = left,
      normTop = top,
      normRight = right,
      normBottom = bottom,
    )
  }

  private fun sampleYPlaneGrid(imageProxy: ImageProxy): Pair<Float, FloatArray> {
    val plane = imageProxy.planes.firstOrNull() ?: return 128f to FloatArray(0)
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val w = imageProxy.width
    val h = imageProxy.height
    if (w <= 0 || h <= 0) return 128f to FloatArray(0)

    val cols = 12
    val rows = 9
    val grid = FloatArray(cols * rows)
    var sum = 0f
    var count = 0

    for (gy in 0 until rows) {
      val y = ((gy + 0.5f) / rows * h).toInt().coerceIn(0, h - 1)
      for (gx in 0 until cols) {
        val x = ((gx + 0.5f) / cols * w).toInt().coerceIn(0, w - 1)
        val idx = y * rowStride + x * pixelStride
        val luma =
          if (idx in 0 until buffer.limit()) {
            (buffer.get(idx).toInt() and 0xFF).toFloat()
          } else {
            128f
          }
        grid[gy * cols + gx] = luma
        sum += luma
        count++
      }
    }

    val avg = if (count > 0) sum / count else 128f
    return avg to grid
  }
}
