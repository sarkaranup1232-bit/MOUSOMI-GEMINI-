package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.vision.LensIntelligenceService
import com.example.core.lens.LensAutoScanTracker
import com.example.core.lens.LensFrameTelemetry
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.AppSettings
import com.example.domain.model.LensAnalysisResult
import com.example.domain.model.LensAutoScanState
import com.example.domain.model.LensComparisonResult
import com.example.domain.model.LensDetectedBox
import com.example.domain.model.LensFollowUpTurn
import com.example.domain.model.LensMode
import com.example.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LensUiState(
  val selectedMode: LensMode = LensMode.SEARCH,
  val isFrontCamera: Boolean = false,
  val isFlashEnabled: Boolean = false,
  val autoScanEnabled: Boolean = true,
  val autoScanState: LensAutoScanState = LensAutoScanState.Scanning,
  val detectedBoxes: List<LensDetectedBox> = emptyList(),
  val isAnalyzing: Boolean = false,
  val activeStageLabel: String = "",
  val capturedImagePath: String? = null,
  val analysisResult: LensAnalysisResult? = null,
  val selectedComponentId: String? = null,
  val showAnnotations: Boolean = true,
  val followUpTurns: List<LensFollowUpTurn> = emptyList(),
  val isAnsweringFollowUp: Boolean = false,
  val isPickingCompareSecondImage: Boolean = false,
  val isComparing: Boolean = false,
  val comparisonResult: LensComparisonResult? = null,
  val errorMessage: String? = null,
  val autoCaptureRequestNonce: Long = 0L,
) {
  val isShowingResults: Boolean
    get() = isAnalyzing || analysisResult != null || comparisonResult != null || errorMessage != null && capturedImagePath != null
}

@HiltViewModel
class LensViewModel @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val lensIntelligenceService: LensIntelligenceService,
  private val settingsRepository: SettingsRepository,
  private val tempMediaCacheManager: TempMediaCacheManager,
) : ViewModel() {

  private val autoScanTracker = LensAutoScanTracker()
  private val _uiState = MutableStateFlow(LensUiState())
  val uiState: StateFlow<LensUiState> = _uiState.asStateFlow()

  private var latestSettings: AppSettings = AppSettings()
  private var activeAnalysisJob: Job? = null

  init {
    viewModelScope.launch {
      settingsRepository.observeSettings().collectLatest { s ->
        latestSettings = s
      }
    }
  }

  fun onSelectMode(mode: LensMode) {
    _uiState.update { it.copy(selectedMode = mode) }
    val existingPath = _uiState.value.capturedImagePath
    if (existingPath != null && _uiState.value.analysisResult != null && !_uiState.value.isAnalyzing) {
      // Allow re-analyzing the current captured image immediately under the newly chosen mode
      analyzeCapturedFile(File(existingPath), modeOverride = mode)
    }
  }

  fun onToggleCameraFacing() {
    _uiState.update {
      val nextFront = !it.isFrontCamera
      it.copy(
        isFrontCamera = nextFront,
        isFlashEnabled = if (nextFront) false else it.isFlashEnabled,
        detectedBoxes = emptyList(),
      )
    }
    autoScanTracker.resetForNewCapture()
  }

  fun onToggleFlash() {
    _uiState.update {
      if (it.isFrontCamera) it else it.copy(isFlashEnabled = !it.isFlashEnabled)
    }
  }

  fun onToggleAutoScan(enabled: Boolean) {
    val nextScanState = autoScanTracker.setEnabled(enabled)
    _uiState.update { it.copy(autoScanEnabled = enabled, autoScanState = nextScanState) }
  }

  fun onCancelCountdown() {
    val nextState = autoScanTracker.cancelActiveCountdown()
    _uiState.update { it.copy(autoScanState = nextState) }
  }

  fun onFrameTelemetry(telemetry: LensFrameTelemetry) {
    val state = _uiState.value
    if (state.isShowingResults && !state.isPickingCompareSecondImage) {
      return
    }
    val nextAutoState =
      if (state.isPickingCompareSecondImage) {
        LensAutoScanState.Scanning
      } else {
        autoScanTracker.onFrame(telemetry)
      }

    if (nextAutoState is LensAutoScanState.Triggered) {
      _uiState.update {
        it.copy(
          detectedBoxes = telemetry.detectedBoxes,
          autoScanState = nextAutoState,
          autoCaptureRequestNonce = it.autoCaptureRequestNonce + 1L,
        )
      }
    } else {
      _uiState.update {
        it.copy(
          detectedBoxes = telemetry.detectedBoxes,
          autoScanState = nextAutoState,
        )
      }
    }
  }

  fun createCaptureOutputFile(): File {
    val dir = File(appContext.cacheDir, "mousomi_lens_captures").apply { mkdirs() }
    return File(dir, "lens_cap_${System.currentTimeMillis()}.jpg")
  }

  fun analyzeCapturedFile(file: File, modeOverride: LensMode? = null) {
    if (_uiState.value.isPickingCompareSecondImage) {
      compareWithSecondFile(file)
      return
    }

    autoScanTracker.markManualOrCompletedCapture()
    val mode = modeOverride ?: _uiState.value.selectedMode
    activeAnalysisJob?.cancel()
    activeAnalysisJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(
            capturedImagePath = file.absolutePath,
            isAnalyzing = true,
            activeStageLabel = "Analyzing visual details with Mousomi AI…",
            analysisResult = null,
            selectedComponentId = null,
            followUpTurns = emptyList(),
            comparisonResult = null,
            errorMessage = null,
          )
        }

        val settings = runCatching { settingsRepository.getSettingsSnapshot() }.getOrDefault(latestSettings)
        val result =
          lensIntelligenceService.analyzeCapturedImage(
            imageFile = file,
            mode = mode,
            settings = settings,
          )

        result.fold(
          onSuccess = { analysis ->
            _uiState.update {
              it.copy(
                isAnalyzing = false,
                activeStageLabel = "",
                analysisResult = analysis,
                selectedComponentId = analysis.components.firstOrNull()?.id,
                errorMessage = null,
              )
            }
          },
          onFailure = { err ->
            _uiState.update {
              it.copy(
                isAnalyzing = false,
                activeStageLabel = "",
                errorMessage = err.localizedMessage ?: "Visual analysis failed. Please try again.",
              )
            }
          },
        )
      }
  }

  fun analyzePickedUri(uri: Uri) {
    viewModelScope.launch {
      val copied =
        tempMediaCacheManager.copyUriToTempFile(
          uriString = uri.toString(),
          extensionHint = "jpg",
          maxAllowedBytes = 25L * 1024L * 1024L,
        )
      copied.fold(
        onSuccess = { temp ->
          if (_uiState.value.isPickingCompareSecondImage) {
            compareWithSecondFile(temp.file)
          } else {
            analyzeCapturedFile(temp.file)
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(
              isPickingCompareSecondImage = false,
              errorMessage = err.localizedMessage ?: "Could not load selected gallery image.",
            )
          }
        },
      )
    }
  }

  fun onSelectComponent(componentId: String?) {
    _uiState.update { it.copy(selectedComponentId = componentId) }
  }

  fun onToggleAnnotations() {
    _uiState.update { it.copy(showAnnotations = !it.showAnnotations) }
  }

  fun askFollowUp(question: String) {
    val state = _uiState.value
    val analysis = state.analysisResult ?: return
    val path = state.capturedImagePath ?: return
    val clean = question.trim()
    if (clean.isEmpty() || state.isAnsweringFollowUp) return

    viewModelScope.launch {
      _uiState.update { it.copy(isAnsweringFollowUp = true) }
      val settings = runCatching { settingsRepository.getSettingsSnapshot() }.getOrDefault(latestSettings)
      val answerRes =
        lensIntelligenceService.askFollowUp(
          imageFile = File(path),
          previousAnalysis = analysis,
          question = clean,
          settings = settings,
        )
      answerRes.fold(
        onSuccess = { answer ->
          val turn =
            LensFollowUpTurn(
              id = "lfu_${UUID.randomUUID()}",
              question = clean,
              answerMarkdown = answer,
            )
          _uiState.update {
            it.copy(
              isAnsweringFollowUp = false,
              followUpTurns = it.followUpTurns + turn,
            )
          }
        },
        onFailure = { err ->
          val turn =
            LensFollowUpTurn(
              id = "lfu_${UUID.randomUUID()}",
              question = clean,
              answerMarkdown = "Unable to answer follow-up: ${err.localizedMessage ?: "Unknown error"}",
            )
          _uiState.update {
            it.copy(
              isAnsweringFollowUp = false,
              followUpTurns = it.followUpTurns + turn,
            )
          }
        },
      )
    }
  }

  fun onStartCompareCaptureMode() {
    _uiState.update {
      it.copy(
        isPickingCompareSecondImage = true,
        comparisonResult = null,
        errorMessage = null,
      )
    }
  }

  fun onCancelCompareCaptureMode() {
    _uiState.update { it.copy(isPickingCompareSecondImage = false) }
  }

  fun compareWithSecondFile(secondFile: File) {
    val firstPath = _uiState.value.capturedImagePath ?: return
    val firstAnalysis = _uiState.value.analysisResult
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isPickingCompareSecondImage = false,
          isComparing = true,
          errorMessage = null,
        )
      }
      val settings = runCatching { settingsRepository.getSettingsSnapshot() }.getOrDefault(latestSettings)
      val res =
        lensIntelligenceService.compareObjects(
          firstImageFile = File(firstPath),
          secondImageFile = secondFile,
          firstAnalysis = firstAnalysis,
          settings = settings,
        )
      res.fold(
        onSuccess = { comparison ->
          _uiState.update {
            it.copy(
              isComparing = false,
              comparisonResult = comparison,
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(
              isComparing = false,
              errorMessage = err.localizedMessage ?: "Comparison failed.",
            )
          }
        },
      )
    }
  }

  fun onDismissComparison() {
    _uiState.update { it.copy(comparisonResult = null, isPickingCompareSecondImage = false) }
  }

  fun resetToLiveCamera() {
    activeAnalysisJob?.cancel()
    autoScanTracker.resetForNewCapture()
    _uiState.update {
      it.copy(
        isAnalyzing = false,
        activeStageLabel = "",
        capturedImagePath = null,
        analysisResult = null,
        selectedComponentId = null,
        followUpTurns = emptyList(),
        isAnsweringFollowUp = false,
        isPickingCompareSecondImage = false,
        isComparing = false,
        comparisonResult = null,
        errorMessage = null,
        autoScanState =
          if (it.autoScanEnabled) LensAutoScanState.Scanning else LensAutoScanState.Disabled,
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    activeAnalysisJob?.cancel()
  }
}
