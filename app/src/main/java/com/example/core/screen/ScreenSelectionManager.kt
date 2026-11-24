package com.example.core.screen

import com.example.domain.model.NormPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScreenSelectionUiState(
  val isActive: Boolean = false,
  val isSystemOverlayMode: Boolean = false,
  val stage: SelectionStage = SelectionStage.IDLE,
  val toolMode: SelectionToolMode = SelectionToolMode.SMART_LOOP,
  val activeStrokePoints: List<NormPoint> = emptyList(),
  val snappedResult: SelectionGeometryResult? = null,
  val undoHistory: List<SelectionGeometryResult> = emptyList(),
  val sourceFrameFilePath: String? = null,
  val captureMetadata: ScreenCaptureMetadata? = null,
  val statusHint: String = "Circle or draw a box around anything (10s)",
  val errorMessage: String? = null,
  val lastAttachedVisual: AttachedVisualItem? = null,
  val remainingSeconds: Int = ScreenSelectionManager.SELECTION_TIMEOUT_SECONDS,
  val hasTimedOut: Boolean = false,
)

/**
 * Single source of truth for Live Screen Selection Mode:
 * - Orchestrates consent -> active neon perimeter -> finger stroke -> smart snap highlight -> crop & composer attach.
 * - Handles undo, clear, boundary adjustment, retake, and Emergency Stop cleanup.
 */
@Singleton
class ScreenSelectionManager @Inject constructor(
  private val geometryEngine: SelectionGeometryEngine,
  private val smartCropEngine: SmartCropEngine,
  private val screenshotCaptureManager: ScreenshotCaptureManager,
  private val imageAttachmentManager: ImageAttachmentManager,
  private val mediaProjectionManager: MediaProjectionManager,
) {
  private val _state = MutableStateFlow(ScreenSelectionUiState())
  val state: StateFlow<ScreenSelectionUiState> = _state.asStateFlow()

  fun markAwaitingConsent(useSystemOverlay: Boolean) {
    _state.value =
      ScreenSelectionUiState(
        isActive = true,
        isSystemOverlayMode = useSystemOverlay,
        stage = SelectionStage.AWAITING_CONSENT,
        statusHint = "Waiting for screen capture consent…",
      )
  }

  fun onAuthorizedFrameReady(
    sourceFramePath: String?,
    metadata: ScreenCaptureMetadata?,
    useSystemOverlay: Boolean,
  ) {
    _state.update {
      it.copy(
        isActive = true,
        isSystemOverlayMode = useSystemOverlay,
        stage = SelectionStage.READY_TO_DRAW,
        activeStrokePoints = emptyList(),
        snappedResult = null,
        undoHistory = emptyList(),
        sourceFrameFilePath = sourceFramePath,
        captureMetadata = metadata,
        statusHint = "Draw or circle a region to select (${SELECTION_TIMEOUT_SECONDS}s)",
        errorMessage = null,
        remainingSeconds = SELECTION_TIMEOUT_SECONDS,
        hasTimedOut = false,
      )
    }
  }

  fun selectToolMode(mode: SelectionToolMode) {
    _state.update { it.copy(toolMode = mode) }
  }

  fun onStrokeStart(point: NormPoint) {
    _state.update { current ->
      val prevHistory =
        current.snappedResult?.let { current.undoHistory + it } ?: current.undoHistory
      current.copy(
        stage = SelectionStage.DRAWING,
        activeStrokePoints = listOf(point),
        undoHistory = prevHistory.takeLast(8),
        snappedResult = null,
        errorMessage = null,
      )
    }
  }

  fun onStrokeMove(point: NormPoint) {
    _state.update { current ->
      if (current.stage != SelectionStage.DRAWING) return@update current
      current.copy(activeStrokePoints = current.activeStrokePoints + point)
    }
  }

  fun onStrokeEnd(): SelectionGeometryResult? {
    val current = _state.value
    if (current.activeStrokePoints.isEmpty()) return current.snappedResult
    val result = geometryEngine.processStroke(current.activeStrokePoints, current.toolMode)
    _state.update {
      it.copy(
        stage = if (result != null) SelectionStage.SNAPPED_HIGHLIGHT else SelectionStage.READY_TO_DRAW,
        activeStrokePoints = emptyList(),
        snappedResult = result,
        statusHint =
          if (result != null) "Adjust handles, Redraw, or Confirm"
          else "Draw or circle a region to select (${it.remainingSeconds}s)",
      )
    }
    return result
  }

  fun onAdjustHandle(
    handle: CropHandle,
    deltaXNorm: Float,
    deltaYNorm: Float,
  ) {
    _state.update { current ->
      val snapped = current.snappedResult ?: return@update current
      val adjusted = geometryEngine.adjustSnappedResult(snapped, handle, deltaXNorm, deltaYNorm)
      current.copy(snappedResult = adjusted, stage = SelectionStage.SNAPPED_HIGHLIGHT)
    }
  }

  fun undo() {
    _state.update { current ->
      if (current.undoHistory.isEmpty()) {
        current.copy(
          snappedResult = null,
          activeStrokePoints = emptyList(),
          stage = SelectionStage.READY_TO_DRAW,
          statusHint = "Draw or circle a region to select (${SELECTION_TIMEOUT_SECONDS}s)",
          remainingSeconds = SELECTION_TIMEOUT_SECONDS,
        )
      } else {
        val restored = current.undoHistory.last()
        current.copy(
          snappedResult = restored,
          undoHistory = current.undoHistory.dropLast(1),
          activeStrokePoints = emptyList(),
          stage = SelectionStage.SNAPPED_HIGHLIGHT,
        )
      }
    }
  }

  fun clearSelection() {
    _state.update { current ->
      val prevHistory =
        current.snappedResult?.let { current.undoHistory + it } ?: current.undoHistory
      current.copy(
        activeStrokePoints = emptyList(),
        snappedResult = null,
        undoHistory = prevHistory.takeLast(8),
        stage = SelectionStage.READY_TO_DRAW,
        statusHint = "Draw or circle a region to select (${SELECTION_TIMEOUT_SECONDS}s)",
        errorMessage = null,
        remainingSeconds = SELECTION_TIMEOUT_SECONDS,
        hasTimedOut = false,
      )
    }
  }

  fun tickCountdown(): Boolean {
    val (nextState, timedOut) = advanceCountdown(_state.value)
    if (nextState !== _state.value) {
      _state.value = nextState
    }
    return timedOut
  }

  suspend fun confirmAndAutoCrop(
    overlayWidthPx: Int = 0,
    overlayHeightPx: Int = 0,
  ): Result<AttachedVisualItem> {
    val current = _state.value
    val snapped =
      current.snappedResult
        ?: return Result.failure(IllegalStateException("Draw or select a region first."))

    _state.update { it.copy(stage = SelectionStage.CAPTURING_AND_CROPPING, errorMessage = null) }

    // Ensure we have an authorized screen frame
    var framePath = current.sourceFrameFilePath
    var meta = current.captureMetadata

    if (framePath.isNullOrBlank()) {
      when (
        val cap =
          screenshotCaptureManager.captureAuthorizedScreenFrame(
            overlayWidthPx = overlayWidthPx,
            overlayHeightPx = overlayHeightPx,
          )
      ) {
        is ScreenshotCaptureManager.CaptureOutcome.Success -> {
          framePath = cap.filePath
          meta = cap.metadata
        }
        ScreenshotCaptureManager.CaptureOutcome.ConsentRequired -> {
          val msg = "Screen capture consent is required before cropping."
          _state.update { it.copy(stage = SelectionStage.ERROR, errorMessage = msg) }
          return Result.failure(IllegalStateException(msg))
        }
        is ScreenshotCaptureManager.CaptureOutcome.BlockedOrFailed -> {
          _state.update { it.copy(stage = SelectionStage.ERROR, errorMessage = cap.reason) }
          return Result.failure(IllegalStateException(cap.reason))
        }
      }
    } else if (meta != null && overlayWidthPx > 16 && overlayHeightPx > 16) {
      meta = meta.copy(overlayWidthPx = overlayWidthPx, overlayHeightPx = overlayHeightPx)
    }

    val cropRes =
      smartCropEngine.cropSelectionToFile(
        sourceFilePath = framePath,
        snappedResult = snapped,
        metadata = meta,
        prefix = "sel_crop_",
      )

    return cropRes.fold(
      onSuccess = { cropped ->
        val attached =
          imageAttachmentManager.attachCroppedVisual(
            croppedFile = cropped.file,
            sourceFrameFilePath = framePath,
            captureMetadata = meta,
            normCropRect = snapped.boundingBox,
            detectedShape = snapped.detectedShape,
            isSettling = true,
          )
        _state.update {
          it.copy(
            isActive = false,
            stage = SelectionStage.ATTACHED,
            activeStrokePoints = emptyList(),
            snappedResult = null,
            lastAttachedVisual = attached,
            errorMessage = null,
          )
        }
        Result.success(attached)
      },
      onFailure = { err ->
        val msg = err.message ?: "Unable to crop selected region."
        _state.update { it.copy(stage = SelectionStage.ERROR, errorMessage = msg) }
        Result.failure(err)
      },
    )
  }

  fun reportError(message: String) {
    _state.update {
      it.copy(
        stage = SelectionStage.ERROR,
        errorMessage = message,
      )
    }
  }

  fun cancelSelection() {
    _state.value = ScreenSelectionUiState()
  }

  fun stopAndCleanup() {
    _state.value = ScreenSelectionUiState()
    mediaProjectionManager.release("Selection session stopped.")
  }

  companion object {
    const val SELECTION_TIMEOUT_SECONDS = 10

    fun advanceCountdown(current: ScreenSelectionUiState): Pair<ScreenSelectionUiState, Boolean> {
      if (!current.isActive ||
        current.stage != SelectionStage.READY_TO_DRAW ||
        current.snappedResult != null
      ) {
        return current to false
      }
      val next = (current.remainingSeconds - 1).coerceAtLeast(0)
      if (next <= 0) {
        return ScreenSelectionUiState(hasTimedOut = true) to true
      }
      return current.copy(
        remainingSeconds = next,
        statusHint = "Draw or circle a region to select (${next}s)",
      ) to false
    }
  }
}
