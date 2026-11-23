package com.example.core.screen

import android.net.Uri
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.NormBoundingBox
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages cropped visual attachments staged for the chat composer:
 * - Automatically tracks cropped image URI + preserved full-resolution source frame for lossless re-editing.
 * - Cleans up superseded or removed temporary crop files cleanly.
 */
@Singleton
class ImageAttachmentManager @Inject constructor(
  private val tempMediaCacheManager: TempMediaCacheManager,
) {
  private val _attachedVisuals = MutableStateFlow<List<AttachedVisualItem>>(emptyList())
  val attachedVisuals: StateFlow<List<AttachedVisualItem>> = _attachedVisuals.asStateFlow()

  fun attachCroppedVisual(
    croppedFile: File,
    sourceFrameFilePath: String?,
    captureMetadata: ScreenCaptureMetadata?,
    normCropRect: NormBoundingBox,
    detectedShape: DetectedRegionShape,
    isSettling: Boolean = false,
  ): AttachedVisualItem {
    val item =
      AttachedVisualItem(
        id = "vis_${UUID.randomUUID()}",
        croppedFilePath = croppedFile.absolutePath,
        croppedUriString = Uri.fromFile(croppedFile).toString(),
        sourceFrameFilePath = sourceFrameFilePath,
        captureMetadata = captureMetadata,
        normCropRect = normCropRect,
        rotationDegrees = 0,
        detectedShape = detectedShape,
        aspectPreset = CropAspectPreset.FREE,
        isSettling = isSettling,
      )
    _attachedVisuals.update { list -> (list + item).takeLast(4) }
    return item
  }

  fun markSettled(attachmentId: String) {
    _attachedVisuals.update { list ->
      list.map { if (it.id == attachmentId) it.copy(isSettling = false) else it }
    }
  }

  fun replaceAfterEdit(
    attachmentId: String,
    newCroppedFile: File,
    newRect: NormBoundingBox,
    newRotation: Int,
    newPreset: CropAspectPreset,
  ): AttachedVisualItem? {
    var updatedItem: AttachedVisualItem? = null
    _attachedVisuals.update { list ->
      list.map { item ->
        if (item.id == attachmentId) {
          val oldFile = File(item.croppedFilePath)
          if (oldFile.absolutePath != newCroppedFile.absolutePath) {
            tempMediaCacheManager.cleanupFile(oldFile)
          }
          item.copy(
            croppedFilePath = newCroppedFile.absolutePath,
            croppedUriString = Uri.fromFile(newCroppedFile).toString(),
            normCropRect = newRect,
            rotationDegrees = newRotation,
            aspectPreset = newPreset,
            isSettling = false,
          ).also { updatedItem = it }
        } else {
          item
        }
      }
    }
    return updatedItem
  }

  fun findByUri(uriString: String): AttachedVisualItem? =
    _attachedVisuals.value.firstOrNull { it.croppedUriString == uriString }

  fun latestEditableVisual(): AttachedVisualItem? =
    _attachedVisuals.value.lastOrNull()

  fun removeByUri(uriString: String) {
    val toRemove = _attachedVisuals.value.filter { it.croppedUriString == uriString }
    if (toRemove.isEmpty()) return
    _attachedVisuals.update { list -> list.filterNot { it.croppedUriString == uriString } }
    toRemove.forEach { cleanupTempIfOrphaned(it) }
  }

  fun removeById(id: String) {
    val toRemove = _attachedVisuals.value.filter { it.id == id }
    if (toRemove.isEmpty()) return
    _attachedVisuals.update { list -> list.filterNot { it.id == id } }
    toRemove.forEach { cleanupTempIfOrphaned(it) }
  }

  fun clearAll(deleteSourceFrames: Boolean = false) {
    val current = _attachedVisuals.value
    _attachedVisuals.value = emptyList()
    current.forEach { cleanupTempIfOrphaned(it, deleteSourceFrame = deleteSourceFrames) }
  }

  fun clearStagingKeepFiles() {
    _attachedVisuals.value = emptyList()
  }

  private fun cleanupTempIfOrphaned(
    item: AttachedVisualItem,
    deleteSourceFrame: Boolean = true,
  ) {
    val cropped = File(item.croppedFilePath)
    if (cropped.name.startsWith("sel_")) {
      tempMediaCacheManager.cleanupFile(cropped)
    }
    if (deleteSourceFrame) {
      val src = item.sourceFrameFilePath?.let(::File)
      if (src != null &&
        src.name.startsWith("sel_full_") &&
        _attachedVisuals.value.none { it.sourceFrameFilePath == src.absolutePath }
      ) {
        tempMediaCacheManager.cleanupFile(src)
      }
    }
  }
}
