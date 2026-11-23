package com.example.ai.image

import com.example.core.image.LocalPhotoEditorEngine
import com.example.domain.model.BackgroundReplaceStyle
import com.example.domain.model.BatchImageItemState
import com.example.domain.model.ImageAdjustmentParams
import com.example.domain.model.ImageEditFeatureType
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.ImageProcessingSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feature G: Bounded Sequential Multi-Photo Batch Queue.
 * Processes selected photos sequentially (concurrency = 1 by default) so Samsung Galaxy M51 RAM/CPU
 * stays well within limits while exposing real per-item status, retry, cancel, and MediaStore export.
 */
@Singleton
class BatchImageProcessor @Inject constructor(
  private val localEditor: LocalPhotoEditorEngine,
  private val storageManager: ImageStorageManager,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var activeJob: Job? = null

  private val _items = MutableStateFlow<List<BatchImageItemState>>(emptyList())
  val items: StateFlow<List<BatchImageItemState>> = _items.asStateFlow()

  private val _isRunning = MutableStateFlow(false)
  val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

  fun setPhotos(urisWithNames: List<Pair<String, String>>) {
    activeJob?.cancel()
    _isRunning.value = false
    _items.value =
      urisWithNames.mapIndexed { idx, (uri, name) ->
        BatchImageItemState(
          itemId = "batch_${idx}_${uri.hashCode()}",
          sourceUri = uri,
          displayName = name.ifBlank { "Photo ${idx + 1}" },
          status = ImageGenerationStatus.PREPARING_PROMPT,
        )
      }
  }

  fun startBatch(
    featureType: ImageEditFeatureType,
    adjustments: ImageAdjustmentParams,
    backgroundStyle: BackgroundReplaceStyle,
  ) {
    val current = _items.value
    if (current.isEmpty()) return
    activeJob?.cancel()
    activeJob =
      scope.launch {
        _isRunning.value = true
        try {
          for (item in _items.value) {
            if (!item.isSelected || item.status == ImageGenerationStatus.COMPLETED || item.status == ImageGenerationStatus.CANCELLED) {
              continue
            }
            processSingleItem(item.itemId, item.sourceUri, featureType, adjustments, backgroundStyle)
          }
        } finally {
          _isRunning.value = false
        }
      }
  }

  fun retryItem(
    itemId: String,
    featureType: ImageEditFeatureType,
    adjustments: ImageAdjustmentParams,
    backgroundStyle: BackgroundReplaceStyle,
  ) {
    val target = _items.value.firstOrNull { it.itemId == itemId } ?: return
    updateItem(itemId) { it.copy(status = ImageGenerationStatus.PREPARING_PROMPT, errorMessage = null) }
    scope.launch {
      processSingleItem(target.itemId, target.sourceUri, featureType, adjustments, backgroundStyle)
    }
  }

  fun cancelItem(itemId: String) {
    updateItem(itemId) { it.copy(status = ImageGenerationStatus.CANCELLED, errorMessage = "Cancelled by user") }
  }

  fun cancelAll() {
    activeJob?.cancel()
    activeJob = null
    _isRunning.value = false
    _items.update { list ->
      list.map { item ->
        if (item.status != ImageGenerationStatus.COMPLETED && item.status != ImageGenerationStatus.FAILED) {
          item.copy(status = ImageGenerationStatus.CANCELLED)
        } else {
          item
        }
      }
    }
  }

  suspend fun exportItemToGallery(itemId: String): Result<String> {
    val item = _items.value.firstOrNull { it.itemId == itemId }
      ?: return Result.failure(IllegalStateException("Batch item not found"))
    val path = item.outputFilePath
      ?: return Result.failure(IllegalStateException("Processed file is not ready yet"))
    return storageManager.saveToGallery(
      filePath = path,
      displayTitle = item.displayName,
      preserveTransparency = path.endsWith(".png", true),
    ).map { "Saved ${item.displayName} to Pictures/Mousomi AI" }
  }

  private suspend fun processSingleItem(
    itemId: String,
    sourceUri: String,
    featureType: ImageEditFeatureType,
    adjustments: ImageAdjustmentParams,
    backgroundStyle: BackgroundReplaceStyle,
  ) {
    try {
      updateItem(itemId) { it.copy(status = ImageGenerationStatus.GENERATING, errorMessage = null) }
      val resultPath =
        when (featureType) {
          ImageEditFeatureType.BACKGROUND_REMOVE_REPLACE,
          ImageEditFeatureType.PORTRAIT_EDIT -> {
            val cutout =
              localEditor.processSubjectIsolation(
                sourceUriOrPath = sourceUri,
                style = backgroundStyle,
                edgeFeatherPx = 2.5f,
                portraitBokehAmount =
                  if (featureType == ImageEditFeatureType.PORTRAIT_EDIT) 14f else 0f,
                maxDimensionPx = 1280,
              ).getOrThrow()
            cutout.outputFilePath
          }
          else -> {
            localEditor.applyAdjustments(
              sourceUriOrPath = sourceUri,
              params = adjustments,
              maxDimensionPx = 1280,
            ).getOrThrow().absolutePath
          }
        }
      updateItem(itemId) { it.copy(status = ImageGenerationStatus.VALIDATING_RESULT) }
      updateItem(itemId) {
        it.copy(
          status = ImageGenerationStatus.COMPLETED,
          outputFilePath = resultPath,
          sourceBadge = ImageProcessingSource.LOCAL_EDITING.badgeLabel,
        )
      }
    } catch (e: CancellationException) {
      updateItem(itemId) { it.copy(status = ImageGenerationStatus.CANCELLED) }
      throw e
    } catch (e: Exception) {
      updateItem(itemId) {
        it.copy(
          status = ImageGenerationStatus.FAILED,
          errorMessage = e.localizedMessage ?: "Failed to process image",
        )
      }
    }
  }

  private fun updateItem(itemId: String, transform: (BatchImageItemState) -> BatchImageItemState) {
    _items.update { list -> list.map { if (it.itemId == itemId) transform(it) else it } }
  }
}
