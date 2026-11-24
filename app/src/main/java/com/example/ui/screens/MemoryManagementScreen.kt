package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryRecord
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.viewmodel.MemoryManagementViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PRIMARY_PROFILE_CATEGORIES =
  listOf(
    MemoryCategory.FAVORITE_SONG,
    MemoryCategory.FAVORITE_ARTIST,
    MemoryCategory.FAVORITE_MUSIC_GENRE,
    MemoryCategory.FAVORITE_APP,
    MemoryCategory.FAVORITE_VIDEO_ENTERTAINMENT,
    MemoryCategory.LANGUAGE_PREFERENCE,
    MemoryCategory.CONVERSATION_PREFERENCE,
    MemoryCategory.PREFERENCE,
    MemoryCategory.HOBBY_INTEREST,
    MemoryCategory.DAILY_ROUTINE,
    MemoryCategory.FREQUENT_CONTACT,
    MemoryCategory.USER_REMINDER,
    MemoryCategory.IMPORTANT_INFO,
    MemoryCategory.CUSTOM_PREFERENCE,
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryManagementScreen(
  viewModel: MemoryManagementViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = LocalMousomiColors.current
  val snackbarHostState = remember { SnackbarHostState() }
  val clipboard = LocalClipboardManager.current
  val mergedBottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

  var showAddDialog by remember { mutableStateOf(false) }
  var editingRecord by remember { mutableStateOf<MemoryRecord?>(null) }
  var confirmClearAll by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.statusMessage) {
    val msg = uiState.statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.onClearStatus()
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.windowInsetsPadding(mergedBottomInsets),
      )
    },
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = stringResource(R.string.my_memory_title),
              fontFamily = DisplayFontFamily,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = "${uiState.allMemories.count { it.isActive }} active memories • Keystore AES-GCM",
              style = MaterialTheme.typography.labelSmall,
              color = colors.mutedText,
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("my_memory_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        actions = {
          IconButton(
            onClick = viewModel::onExportMemories,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("my_memory_export_button"),
          ) {
            Icon(Icons.Outlined.IosShare, contentDescription = "Export memories JSON")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
          ),
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).testTag("my_memory_add_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add personal memory")
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
            .windowInsetsPadding(mergedBottomInsets)
            .padding(horizontal = 16.dp),
      ) {
        // Privacy & Discovery Controls Card
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Learn Personal Preferences", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Remember approved songs, style & language across models. Secrets & OTPs stay blocked.",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.mutedText,
                )
              }
              Switch(
                checked = uiState.memoryEnabled,
                onCheckedChange = viewModel::onToggleMemoryEnabled,
                modifier = Modifier.testTag("my_memory_enabled_switch"),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Gentle Preference Discovery", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Ask one unasked question occasionally to personalize music or replies",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.mutedText,
                )
              }
              Switch(
                checked = uiState.proactiveQuestionsEnabled,
                onCheckedChange = viewModel::onToggleProactiveQuestions,
                modifier = Modifier.testTag("my_memory_discovery_switch"),
              )
            }
          }
        }

        Spacer(modifier = Modifier.size(10.dp))

        // Search input
        OutlinedTextField(
          value = uiState.searchQuery,
          onValueChange = viewModel::onSearchQueryChanged,
          placeholder = { Text("Search songs, artists, preferences, or notes…") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("my_memory_search_input"),
        )

        Spacer(modifier = Modifier.size(8.dp))

        // Category filter row
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          FilterChip(
            selected = uiState.selectedCategory == null && !uiState.showArchived,
            onClick = {
              viewModel.onToggleShowArchived(false)
              viewModel.onSelectCategory(null)
            },
            label = { Text("All Active") },
            modifier = Modifier.testTag("memory_filter_all"),
          )
          FilterChip(
            selected = uiState.showArchived,
            onClick = { viewModel.onToggleShowArchived(!uiState.showArchived) },
            label = { Text("Archived") },
            modifier = Modifier.testTag("memory_filter_archived"),
          )
          PRIMARY_PROFILE_CATEGORIES.forEach { cat ->
            FilterChip(
              selected = uiState.selectedCategory == cat && !uiState.showArchived,
              onClick = {
                viewModel.onToggleShowArchived(false)
                viewModel.onSelectCategory(if (uiState.selectedCategory == cat) null else cat)
              },
              label = { Text(cat.label) },
              modifier = Modifier.testTag("memory_filter_${cat.name}"),
            )
          }
        }

        // Quick Category / Clear Bar
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val activeCategory = uiState.selectedCategory
          if (activeCategory != null && uiState.filteredMemories.isNotEmpty()) {
            TextButton(onClick = { viewModel.onClearCategory(activeCategory) }) {
              Text("Clear ${activeCategory.label}")
            }
          } else {
            Text(
              text = "${uiState.filteredMemories.size} item(s)",
              style = MaterialTheme.typography.labelMedium,
              color = colors.mutedText,
            )
          }
          if (uiState.allMemories.isNotEmpty()) {
            TextButton(
              onClick = { confirmClearAll = true },
              colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171)),
              modifier = Modifier.testTag("my_memory_clear_all_button"),
            ) {
              Text("Clear All Memories")
            }
          }
        }

        if (uiState.filteredMemories.isEmpty()) {
          Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(24.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = NeonCyan.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp),
              )
              Text(
                text =
                  if (uiState.searchQuery.isNotBlank()) "No saved memories match \"${uiState.searchQuery}\""
                  else if (uiState.showArchived) "No archived memories"
                  else "Your personal profile is empty",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Tap + to add your favorite songs, artists, or reply preferences—or simply tell Mousomi in chat.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(uiState.filteredMemories, key = { it.id }) { mem ->
              MemoryRecordCard(
                record = mem,
                onEdit = { editingRecord = mem },
                onToggleArchive = { viewModel.onToggleArchiveMemory(mem) },
                onDelete = { viewModel.onDeleteMemory(mem.id) },
              )
            }
            item { Spacer(modifier = Modifier.size(72.dp)) }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    MemoryEditDialog(
      title = "Add to My Memory",
      initialCategory = uiState.selectedCategory ?: MemoryCategory.FAVORITE_SONG,
      initialContent = "",
      onConfirm = { cat, content ->
        viewModel.onAddMemory(cat, content)
        showAddDialog = false
      },
      onDismiss = { showAddDialog = false },
    )
  }

  editingRecord?.let { target ->
    MemoryEditDialog(
      title = "Edit Memory",
      initialCategory = target.category,
      initialContent = target.content,
      onConfirm = { cat, content ->
        viewModel.onEditMemory(target.id, content, cat)
        editingRecord = null
      },
      onDismiss = { editingRecord = null },
    )
  }

  if (confirmClearAll) {
    AlertDialog(
      onDismissRequest = { confirmClearAll = false },
      title = { Text("Clear All Memories?") },
      text = {
        Text("This permanently deletes all saved preferences, favorite songs, artists, and notes from Mousomi's local database.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.onClearAllMemories()
            confirmClearAll = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
        ) {
          Text("Delete All", color = Color.White)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
      },
    )
  }

  uiState.exportedJson?.let { json ->
    AlertDialog(
      onDismissRequest = viewModel::onDismissExport,
      title = { Text("Exported Personal Memory JSON") },
      text = {
        OutlinedTextField(
          value = json,
          onValueChange = {},
          readOnly = true,
          maxLines = 10,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            clipboard.setText(AnnotatedString(json))
            viewModel.onDismissExport()
          }
        ) {
          Text("Copy JSON")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = viewModel::onDismissExport) { Text("Close") }
      },
    )
  }
}

@Composable
private fun MemoryRecordCard(
  record: MemoryRecord,
  onEdit: () -> Unit,
  onToggleArchive: () -> Unit,
  onDelete: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val dateLabel =
    remember(record.updatedAtMs) {
      SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(record.updatedAtMs))
    }
  val isMusic =
    record.category == MemoryCategory.FAVORITE_SONG ||
      record.category == MemoryCategory.FAVORITE_ARTIST ||
      record.category == MemoryCategory.FAVORITE_MUSIC_GENRE

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    tonalElevation = 1.dp,
    modifier = Modifier.fillMaxWidth().testTag("memory_card_${record.id}"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (isMusic) {
            Icon(
              imageVector = Icons.Outlined.MusicNote,
              contentDescription = null,
              tint = NeonCyan,
              modifier = Modifier.size(16.dp),
            )
          }
          Text(
            text = record.category.label,
            style = MaterialTheme.typography.labelMedium,
            color = NeonCyan,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Text(
          text = dateLabel,
          style = MaterialTheme.typography.labelSmall,
          color = colors.mutedText,
        )
      }

      Text(
        text = record.content,
        style = MaterialTheme.typography.bodyLarge,
        fontSize = 15.sp,
      )

      if (record.originalStatement.isNotBlank() && record.originalStatement != record.content) {
        Text(
          text = "Source: \"${record.originalStatement}\"",
          style = MaterialTheme.typography.labelSmall,
          color = colors.mutedText,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(Icons.Outlined.Edit, contentDescription = "Edit memory")
        }
        IconButton(onClick = onToggleArchive, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(
            imageVector = if (record.isActive) Icons.Outlined.Archive else Icons.Outlined.Restore,
            contentDescription = if (record.isActive) "Archive memory" else "Restore memory",
          )
        }
        IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Delete memory",
            tint = Color(0xFFF87171),
          )
        }
      }
    }
  }
}

@Composable
private fun MemoryEditDialog(
  title: String,
  initialCategory: MemoryCategory,
  initialContent: String,
  onConfirm: (MemoryCategory, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedCategory by remember { mutableStateOf(initialCategory) }
  var text by remember { mutableStateOf(initialContent) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          PRIMARY_PROFILE_CATEGORIES.forEach { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat.label) },
            )
          }
        }
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text("Detail to remember") },
          placeholder = { Text("e.g. Tum Hi Ho - Arijit Singh") },
          modifier = Modifier.fillMaxWidth().testTag("memory_dialog_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (text.isNotBlank()) onConfirm(selectedCategory, text.trim())
        },
        enabled = text.isNotBlank(),
        modifier = Modifier.testTag("memory_dialog_save_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
