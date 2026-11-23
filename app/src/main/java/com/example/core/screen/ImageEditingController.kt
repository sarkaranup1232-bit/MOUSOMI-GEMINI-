package com.example.core.screen

import com.example.domain.model.NormBoundingBox
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Controller for the compact Image Editing Studio opened by tapping "Edit images" above the composer.
 * Supports Adjust Crop (corner/edge handles), Reposition Selection, Resize Crop (aspect presets),
 * Rotate (90-degree increments), Cancel, and Confirm (re-cropping from preserved full-res frame).
 */
@Singleton
class ImageEditingController @Inject constructor(
  private val geometryEngine: SelectionGeometryEngine,
  private val smartCropEngine: SmartCropEngine,
  private val imageAttachmentManager: ImageAttachmentManager,
) {
  private val _editState = MutableStateFlow<ImageEditState?>(null)
  val editState: StateFlow<ImageEditState?> = _editState.asStateFlow()

  fun beginEditing(item: AttachedVisualItem): Boolean {
    val sourcePath = item.sourceFrameFilePath?.takeIf { it.isNotBlank() } ?: item.croppedFilePath
    if (sourcePath.isBlank()) return false
    val initialRect =
      if (item.sourceFrameFilePath != null) item.normCropRect
      else NormBoundingBox(0.05f, 0.05f, 0.95f, 0.95f)

    _editState.value =
      ImageEditState(
        attachmentId = item.id,
        sourceFrameFilePath = sourcePath,
        originalNormCropRect = initialRect,
        currentNormCropRect = initialRect,
        rotationDegrees = item.rotationDegrees,
        aspectPreset = item.aspectPreset,
        captureMetadata = item.captureMetadata,
      )
    return true
  }

  fun adjustHandle(
    handle: CropHandle,
    deltaXNorm: Float,
    deltaYNorm: Float,
  ) {
    _editState.update { state ->
      state ?: return@update null
      val updated =
        geometryEngine.rectangleProcessor.adjustBox(
          box = state.currentNormCropRect,
          handle = handle,
          deltaXNorm = deltaXNorm,
          deltaYNorm = deltaYNorm,
          targetAspectRatio = state.aspectPreset.ratio,
        )
      state.copy(currentNormCropRect = updated)
    }
  }

  fun setAspectPreset(preset: CropAspectPreset) {
    _editState.update { state ->
      state ?: return@update null
      val updatedRect =
        geometryEngine.rectangleProcessor.enforceAspectRatio(
          box = state.currentNormCropRect,
          targetAspectRatio = preset.ratio,
        )
      state.copy(aspectPreset = preset, currentNormCropRect = updatedRect)
    }
  }

  fun rotate90() {
    _editState.update { state ->
      state ?: return@update null
      val nextRot = (state.rotationDegrees + 90) % 360
      state.copy(rotationDegrees = nextRot)
    }
  }

  fun resetToOriginal() {
    _editState.update { state ->
      state ?: return@update null
      state.copy(
        currentNormCropRect = state.originalNormCropRect,
        rotationDegrees = 0,
        aspectPreset = CropAspectPreset.FREE,
      )
    }
  }

  suspend fun confirmEdit(): Result<AttachedVisualItem> {
    val state =
      _editState.value
        ?: return Result.failure(IllegalStateException("No active image edit session."))

    val cropResult =
      smartCropEngine.cropFromFile(
        sourceFilePath = state.sourceFrameFilePath,
        normBox = state.currentNormCropRect,
        metadata = state.captureMetadata,
        rotationDegrees = state.rotationDegrees,
        prefix = "sel_edit_",
      )

    return cropResult.fold(
      onSuccess = { output ->
        val updated =
          imageAttachmentManager.replaceAfterEdit(
            attachmentId = state.attachmentId,
            newCroppedFile = output.file,
            newRect = state.currentNormCropRect,
            newRotation = output.rotationDegrees,
            newPreset = state.aspectPreset,
          )
        _editState.value = null
        if (updated != null) {
          Result.success(updated)
        } else {
          Result.failure(IllegalStateException("Edited attachment could not be updated."))
        }
      },
      onFailure = { err -> Result.failure(err) },
    )
  }

  fun cancelEdit() {
    _editState.value = null
  }
}
