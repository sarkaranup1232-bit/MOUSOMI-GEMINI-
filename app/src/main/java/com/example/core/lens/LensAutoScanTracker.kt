package com.example.core.lens

import com.example.domain.model.LensAutoScanState
import com.example.domain.model.LensDetectedBox
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

data class LensFrameTelemetry(
  val detectedBoxes: List<LensDetectedBox>,
  val avgLuma: Float,
  val motionDelta: Float,
  val timestampMs: Long,
)

/**
 * Deterministic state machine for the 10-second automatic object observation workflow.
 * Starts counting down only when an object is clearly visible and sufficiently stable,
 * pauses/resets when camera moves rapidly or view is unclear, and prevents repeat captures.
 */
class LensAutoScanTracker(
  private val countdownDurationMs: Long = 10_000L,
  private val preLockStabilizeMs: Long = 700L,
  private val missingGraceMs: Long = 1_100L,
) {
  var isEnabled: Boolean = true
    private set

  var hasCapturedCurrentSession: Boolean = false
    private set

  private var lockStartTimestampMs: Long? = null
  private var countdownStartTimestampMs: Long? = null
  private var lastObjectSeenMs: Long? = null
  private var lastCenterX: Float? = null
  private var lastCenterY: Float? = null
  private var lastTargetLabel: String = "Object"

  fun setEnabled(enabled: Boolean): LensAutoScanState {
    isEnabled = enabled
    if (!enabled) {
      resetCountdownAnchors()
      return LensAutoScanState.Disabled
    }
    return if (hasCapturedCurrentSession) LensAutoScanState.Scanning else LensAutoScanState.Scanning
  }

  fun markManualOrCompletedCapture() {
    hasCapturedCurrentSession = true
    resetCountdownAnchors()
  }

  fun resetForNewCapture() {
    hasCapturedCurrentSession = false
    resetCountdownAnchors()
  }

  fun cancelActiveCountdown(): LensAutoScanState {
    resetCountdownAnchors()
    return if (isEnabled) LensAutoScanState.Scanning else LensAutoScanState.Disabled
  }

  fun onFrame(telemetry: LensFrameTelemetry): LensAutoScanState {
    if (!isEnabled) return LensAutoScanState.Disabled
    if (hasCapturedCurrentSession) return LensAutoScanState.Scanning

    val now = telemetry.timestampMs

    // Guard against covered/too-dark or blown-out frames
    if (telemetry.avgLuma < 20f) {
      resetCountdownAnchors()
      return LensAutoScanState.Unstable("Frame too dark — point at a lit subject")
    }
    if (telemetry.avgLuma > 246f) {
      resetCountdownAnchors()
      return LensAutoScanState.Unstable("Scene overexposed — adjust lighting")
    }

    // Guard against rapid camera movement
    if (telemetry.motionDelta > 0.115f) {
      resetCountdownAnchors()
      return LensAutoScanState.Unstable("Camera moving — hold steady")
    }

    val primaryBox =
      telemetry.detectedBoxes
        .filter { it.area >= 0.015f }
        .maxByOrNull { it.area }

    if (primaryBox == null) {
      val lastSeen = lastObjectSeenMs
      if (countdownStartTimestampMs != null && lastSeen != null && (now - lastSeen) <= missingGraceMs) {
        val elapsed = (now - countdownStartTimestampMs!!).coerceAtLeast(0L)
        return buildCountdownState(elapsed)
      }
      resetCountdownAnchors()
      return LensAutoScanState.Scanning
    }

    lastObjectSeenMs = now
    val cleanLabel = primaryBox.label.ifBlank { "Detected Object" }
    lastTargetLabel = cleanLabel

    val prevX = lastCenterX
    val prevY = lastCenterY
    lastCenterX = primaryBox.centerX
    lastCenterY = primaryBox.centerY

    val boxShift =
      if (prevX != null && prevY != null) {
        hypot((primaryBox.centerX - prevX).toDouble(), (primaryBox.centerY - prevY).toDouble()).toFloat()
      } else {
        0f
      }

    if (boxShift > 0.085f || telemetry.motionDelta > 0.085f) {
      // Significant subject shift; re-anchor stability window cleanly
      lockStartTimestampMs = now
      countdownStartTimestampMs = null
      return LensAutoScanState.Unstable("Hold steady on $cleanLabel")
    }

    val lockStart = lockStartTimestampMs
    if (lockStart == null) {
      lockStartTimestampMs = now
      return LensAutoScanState.Scanning
    }

    if (countdownStartTimestampMs == null) {
      if (now - lockStart < preLockStabilizeMs) {
        return LensAutoScanState.Scanning
      }
      countdownStartTimestampMs = now
    }

    val elapsed = (now - countdownStartTimestampMs!!).coerceAtLeast(0L)
    if (elapsed >= countdownDurationMs) {
      hasCapturedCurrentSession = true
      resetCountdownAnchors()
      return LensAutoScanState.Triggered(cleanLabel)
    }

    return buildCountdownState(elapsed)
  }

  private fun buildCountdownState(elapsedMs: Long): LensAutoScanState.CountingDown {
    val remainingMs = (countdownDurationMs - elapsedMs).coerceIn(1L, countdownDurationMs)
    val secondsRemaining = ceil(remainingMs / 1000.0).toInt().coerceIn(1, 10)
    val progress = (elapsedMs.toFloat() / countdownDurationMs.toFloat()).coerceIn(0f, 1f)
    return LensAutoScanState.CountingDown(
      secondsRemaining = secondsRemaining,
      progress = progress,
      targetLabel = lastTargetLabel,
    )
  }

  private fun resetCountdownAnchors() {
    lockStartTimestampMs = null
    countdownStartTimestampMs = null
    lastObjectSeenMs = null
    lastCenterX = null
    lastCenterY = null
  }

  companion object {
    fun estimateMotionDelta(previousLumaGrid: FloatArray?, currentLumaGrid: FloatArray): Float {
      if (previousLumaGrid == null || previousLumaGrid.size != currentLumaGrid.size || currentLumaGrid.isEmpty()) {
        return 0f
      }
      var sumDiff = 0f
      for (i in currentLumaGrid.indices) {
        sumDiff += abs(currentLumaGrid[i] - previousLumaGrid[i])
      }
      return (sumDiff / (currentLumaGrid.size * 255f)).coerceIn(0f, 1f)
    }
  }
}
