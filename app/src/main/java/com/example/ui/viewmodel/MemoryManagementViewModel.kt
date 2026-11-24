package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.memory.FavoriteMusicManager
import com.example.domain.memory.MemoryWriteOutcome
import com.example.domain.memory.PersonalMemoryRepository
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryRecord
import com.example.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryManagementUiState(
  val allMemories: List<MemoryRecord> = emptyList(),
  val filteredMemories: List<MemoryRecord> = emptyList(),
  val searchQuery: String = "",
  val selectedCategory: MemoryCategory? = null,
  val showArchived: Boolean = false,
  val memoryEnabled: Boolean = true,
  val proactiveQuestionsEnabled: Boolean = true,
  val encryptAtRest: Boolean = true,
  val exportedJson: String? = null,
  val statusMessage: String? = null,
)

@HiltViewModel
class MemoryManagementViewModel @Inject constructor(
  private val memoryRepository: PersonalMemoryRepository,
  private val settingsRepository: SettingsRepository,
  private val favoriteMusicManager: FavoriteMusicManager,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MemoryManagementUiState())
  val uiState: StateFlow<MemoryManagementUiState> = _uiState.asStateFlow()

  private val searchFlow = MutableStateFlow("")
  private val categoryFlow = MutableStateFlow<MemoryCategory?>(null)
  private val archivedFlow = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      combine(
        memoryRepository.observeAllMemories(),
        searchFlow,
        categoryFlow,
        archivedFlow,
      ) { items, query, cat, showArch ->
        val filtered =
          items.filter { rec ->
            val archMatch = if (showArch) !rec.isActive else rec.isActive
            val catMatch = cat == null || rec.category == cat
            val q = query.trim().lowercase()
            val textMatch =
              q.isBlank() ||
                rec.content.lowercase().contains(q) ||
                rec.originalStatement.lowercase().contains(q) ||
                rec.category.label.lowercase().contains(q) ||
                rec.tags.any { it.lowercase().contains(q) }
            archMatch && catMatch && textMatch
          }
        Triple(items, filtered, Triple(query, cat, showArch))
      }.collectLatest { (items, filtered, filters) ->
        _uiState.update {
          it.copy(
            allMemories = items,
            filteredMemories = filtered,
            searchQuery = filters.first,
            selectedCategory = filters.second,
            showArchived = filters.third,
          )
        }
      }
    }

    viewModelScope.launch {
      settingsRepository.observeSettings().collectLatest { settings ->
        _uiState.update {
          it.copy(
            memoryEnabled = settings.privacyPerformanceConfig.memoryEnabled,
            proactiveQuestionsEnabled = settings.privacyPerformanceConfig.proactiveMemoryQuestionsEnabled,
            encryptAtRest = settings.privacyPerformanceConfig.encryptSensitiveMemory,
          )
        }
      }
    }
  }

  fun onSearchQueryChanged(query: String) {
    searchFlow.value = query
  }

  fun onSelectCategory(category: MemoryCategory?) {
    categoryFlow.value = category
  }

  fun onToggleShowArchived(showArchived: Boolean) {
    archivedFlow.value = showArchived
  }

  fun onToggleMemoryEnabled(enabled: Boolean) {
    viewModelScope.launch {
      val current = settingsRepository.getSettingsSnapshot()
      settingsRepository.saveSettings(
        current.copy(
          privacyPerformanceConfig = current.privacyPerformanceConfig.copy(memoryEnabled = enabled)
        )
      )
      _uiState.update {
        it.copy(
          statusMessage =
            if (enabled) "Personal Memory learning resumed."
            else "Personal Memory learning paused. Existing saved memories remain protected."
        )
      }
    }
  }

  fun onToggleProactiveQuestions(enabled: Boolean) {
    viewModelScope.launch {
      val current = settingsRepository.getSettingsSnapshot()
      settingsRepository.saveSettings(
        current.copy(
          privacyPerformanceConfig = current.privacyPerformanceConfig.copy(
            proactiveMemoryQuestionsEnabled = enabled
          )
        )
      )
    }
  }

  fun onAddMemory(category: MemoryCategory, content: String) {
    viewModelScope.launch {
      val outcome =
        if (category == MemoryCategory.FAVORITE_SONG) {
          favoriteMusicManager.addFavoriteSong(content, _uiState.value.encryptAtRest)
        } else {
          memoryRepository.saveConfirmedMemory(
            category = category,
            content = content,
            originalStatement = content,
            encryptAtRest = _uiState.value.encryptAtRest,
          )
        }
      val msg =
        when (outcome) {
          is MemoryWriteOutcome.Saved ->
            if (outcome.mergedDuplicate) "Updated existing similar memory."
            else "Saved to My Memory."
          is MemoryWriteOutcome.BlockedSensitive -> outcome.reason
        }
      _uiState.update { it.copy(statusMessage = msg) }
    }
  }

  fun onEditMemory(id: String, newContent: String, newCategory: MemoryCategory) {
    viewModelScope.launch {
      val outcome =
        memoryRepository.updateMemory(
          id = id,
          category = newCategory,
          content = newContent,
          encryptAtRest = _uiState.value.encryptAtRest,
        )
      val msg =
        when (outcome) {
          is MemoryWriteOutcome.Saved -> "Memory updated."
          is MemoryWriteOutcome.BlockedSensitive -> outcome.reason
          null -> "Could not update memory item."
        }
      _uiState.update { it.copy(statusMessage = msg) }
    }
  }

  fun onToggleArchiveMemory(record: MemoryRecord) {
    viewModelScope.launch {
      memoryRepository.archiveOrRestore(record.id, !record.isActive)
      _uiState.update {
        it.copy(statusMessage = if (record.isActive) "Archived memory." else "Restored memory to active profile.")
      }
    }
  }

  fun onDeleteMemory(id: String) {
    viewModelScope.launch {
      memoryRepository.deleteMemory(id)
      _uiState.update { it.copy(statusMessage = "Memory deleted.") }
    }
  }

  fun onClearCategory(category: MemoryCategory) {
    viewModelScope.launch {
      memoryRepository.deleteCategory(category)
      _uiState.update { it.copy(statusMessage = "Cleared all ${category.label} memories.") }
    }
  }

  fun onClearAllMemories() {
    viewModelScope.launch {
      memoryRepository.clearAll()
      _uiState.update { it.copy(statusMessage = "All saved personal memories cleared.") }
    }
  }

  fun onExportMemories() {
    viewModelScope.launch {
      val json = memoryRepository.exportAsJson()
      _uiState.update { it.copy(exportedJson = json) }
    }
  }

  fun onDismissExport() {
    _uiState.update { it.copy(exportedJson = null) }
  }

  fun onClearStatus() {
    _uiState.update { it.copy(statusMessage = null) }
  }
}
