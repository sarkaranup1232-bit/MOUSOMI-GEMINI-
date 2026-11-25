package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ai.image.PollinationsAspectRatio
import com.example.domain.model.ImageEditFeatureType
import com.example.ui.components.BeforeAfterImageSlider
import com.example.ui.components.geminiBackgroundGlow
import com.example.ui.model.MousomiUiState
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.viewmodel.MousomiViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageStudioScreen(
  uiState: MousomiUiState,
  viewModel: MousomiViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val colors = LocalMousomiColors.current
  val studio = uiState.imageStudioState
  var selectedBatchOp by remember { mutableStateOf(ImageEditFeatureType.PHOTO_ENHANCE) }

  val singlePhotoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        viewModel.onStudioPickSourcePhoto(uri.toString())
      }
    }

  val customBackdropLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        viewModel.onStudioPickCustomBackdrop(uri.toString())
      }
    }

  val batchPhotosLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.onStudioSetBatchPhotos(uris.map { it.toString() })
      }
    }

  Box(modifier = Modifier.fillMaxSize().geminiBackgroundGlow()) {
    Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.navigationBars,
      topBar = {
        TopAppBar(
          modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
          navigationIcon = {
            IconButton(
              onClick = onBack,
              modifier = Modifier.defaultMinSize(48.dp, 48.dp).testTag("image_studio_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
          },
          title = {
            Column {
              Text(
                text = "Creative Image Studio",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "Pollinations Flux • On-Device ML Kit • Offline Poster",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
          actions = {
            IconButton(
              onClick = {
                singlePhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
              modifier = Modifier.defaultMinSize(48.dp, 48.dp).testTag("studio_pick_photo_button"),
            ) {
              Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Pick source photo")
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .widthIn(max = 720.dp),
      ) {
        // Pinned 8-Tool Studio Selector
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ImageEditFeatureType.entries.forEach { feature ->
            val selected = studio.activeFeature == feature
            FilterChip(
              selected = selected,
              onClick = { viewModel.onSelectStudioFeature(feature) },
              label = { Text("${feature.code} · ${feature.title}") },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = ElectricBlue.copy(alpha = 0.28f),
                selectedLabelColor = NeonCyan,
              ),
              modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_tab_${feature.name}"),
            )
          }
        }

        Column(
          modifier =
            Modifier
              .weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

        // Source photo picker pill for photo-based modes
        val needsSourcePhoto = studio.activeFeature in setOf(
          ImageEditFeatureType.PHOTO_ENHANCE,
          ImageEditFeatureType.PORTRAIT_EDIT,
          ImageEditFeatureType.BACKGROUND_REMOVE_REPLACE,
          ImageEditFeatureType.OBJECT_REMOVE_REPLACE,
          ImageEditFeatureType.CREATIVE_AI_EDIT,
        )
        if (needsSourcePhoto) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (studio.sourceUriOrPath != null) "Source Photo Ready" else "No Photo Selected",
                  style = MaterialTheme.typography.labelLarge,
                )
                Text(
                  text = studio.activeFeature.subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.mutedText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              OutlinedButton(
                onClick = {
                  singlePhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_select_source_button"),
              ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (studio.sourceUriOrPath != null) "Change" else "Pick Photo")
              }
            }
          }
        }

        // Output Preview Stage
        StudioPreviewStage(
          studio = studio,
          onOpenLightbox = { path -> viewModel.onSetStudioLightboxPath(path) },
          onSaveToGallery = viewModel::onSaveStudioOutputToGallery,
          onShareOutput = { viewModel.onShareStudioOutput(context) },
          onSendToChat = {
            viewModel.onOpenStudioOutputInChat()
            onBack()
          },
          onRegenerate = { viewModel.onStudioGenerateFluxImage(remix = false) },
          onRemix = { viewModel.onStudioGenerateFluxImage(remix = true) },
        )

        studio.errorMessage?.let { err ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().testTag("studio_error_banner"),
          ) {
            Text(
              text = err,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        // Active Tool Controls
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (studio.activeFeature) {
              ImageEditFeatureType.TEXT_TO_IMAGE -> {
                TextToImageFluxPanel(
                  studio = studio,
                  onPromptChanged = viewModel::onUpdateStudioPrompt,
                  onEnhanceToggle = viewModel::onToggleStudioEnhancePrompt,
                  onEnhanceNow = viewModel::onEnhanceStudioPrompt,
                  onSelectAspectRatio = viewModel::onUpdateStudioAspectRatio,
                  onSeedChanged = viewModel::onUpdateStudioSeed,
                  onGenerate = { viewModel.onStudioGenerateFluxImage(remix = false) },
                )
              }
              ImageEditFeatureType.PHOTO_ENHANCE -> {
                PhotoEnhancePanel(
                  state = studio,
                  onUpdateAdjustments = viewModel::onUpdateStudioAdjustments,
                  onRotate90 = viewModel::onRotateStudioImage,
                  onFlipHorizontal = viewModel::onFlipStudioImage,
                  onApply = viewModel::onApplyStudioPhotoEnhance,
                )
              }
              ImageEditFeatureType.PORTRAIT_EDIT -> {
                PortraitAndBgPanel(
                  state = studio,
                  isPortraitMode = true,
                  onUpdateSettings = viewModel::onUpdateStudioPortraitSettings,
                  onPickCustomBackdrop = {
                    customBackdropLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                  },
                  onApply = viewModel::onApplyStudioPortraitOrBg,
                )
              }
              ImageEditFeatureType.BACKGROUND_REMOVE_REPLACE -> {
                PortraitAndBgPanel(
                  state = studio,
                  isPortraitMode = false,
                  onUpdateSettings = viewModel::onUpdateStudioPortraitSettings,
                  onPickCustomBackdrop = {
                    customBackdropLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                  },
                  onApply = viewModel::onApplyStudioPortraitOrBg,
                )
              }
              ImageEditFeatureType.OBJECT_REMOVE_REPLACE -> {
                ObjectMaskCanvasPanel(
                  state = studio,
                  onPromptChanged = viewModel::onUpdateStudioPrompt,
                  onBrushRadiusChanged = viewModel::onUpdateStudioBrushRadius,
                  onAddStroke = viewModel::onAddStudioMaskStroke,
                  onUndoStroke = viewModel::onUndoStudioMaskStroke,
                  onClearMask = viewModel::onClearStudioMask,
                  onApply = viewModel::onApplyStudioObjectRemove,
                )
              }
              ImageEditFeatureType.CREATIVE_AI_EDIT -> {
                CreativeAiEditPanel(
                  state = studio,
                  onPromptChanged = viewModel::onUpdateStudioPrompt,
                  onApply = viewModel::onApplyStudioCreativeEdit,
                )
              }
              ImageEditFeatureType.BENGALI_ENGLISH_POSTER -> {
                BengaliPosterPanel(
                  state = studio,
                  onUpdatePosterSpec = viewModel::onUpdatePosterSpec,
                  onPickPosterBg = {
                    customBackdropLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                  },
                  onRenderPoster = viewModel::onRenderStudioPoster,
                )
              }
              ImageEditFeatureType.BATCH_MULTI_PHOTO -> {
                BatchMultiPhotoPanel(
                  state = studio,
                  batchItems = uiState.batchImageItems,
                  isBatchRunning = uiState.isBatchRunning,
                  selectedBatchOperation = selectedBatchOp,
                  onSelectBatchOperation = { selectedBatchOp = it },
                  onPickMultiplePhotos = {
                    batchPhotosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                  },
                  onStartBatch = { viewModel.onStudioStartBatch(selectedBatchOp) },
                  onCancelAll = viewModel::onStudioCancelBatch,
                  onRetryItem = { itemId -> viewModel.onStudioRetryBatchItem(itemId, selectedBatchOp) },
                  onSaveItem = viewModel::onStudioSaveBatchItem,
                  onCreateCollage = viewModel::onStudioCreateCollage,
                )
              }
            }
          }
        }

        Spacer(Modifier.height(16.dp))
        }
      }
    }

    studio.lightboxImagePath?.let { path ->
      StudioFullscreenLightboxDialog(
        filePath = path,
        onSave = viewModel::onSaveStudioOutputToGallery,
        onShare = { viewModel.onShareStudioOutput(context) },
        onDismiss = { viewModel.onSetStudioLightboxPath(null) },
      )
    }
  }
}

@Composable
private fun TextToImageFluxPanel(
  studio: com.example.ui.model.ImageStudioUiState,
  onPromptChanged: (String) -> Unit,
  onEnhanceToggle: (Boolean) -> Unit,
  onEnhanceNow: () -> Unit,
  onSelectAspectRatio: (String) -> Unit,
  onSeedChanged: (String) -> Unit,
  onGenerate: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(
      value = studio.promptInput,
      onValueChange = onPromptChanged,
      label = { Text("Describe the image to generate") },
      placeholder = { Text("e.g., Futuristic Dhaka skyline at dusk, glowing neon rain reflections") },
      modifier = Modifier.fillMaxWidth().testTag("studio_prompt_input"),
      minLines = 2,
      maxLines = 4,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(
          checked = studio.enhancePromptEnabled,
          onCheckedChange = onEnhanceToggle,
          modifier = Modifier.testTag("studio_enhance_switch"),
        )
        Text("Auto-Enhance Prompt", style = MaterialTheme.typography.labelLarge)
      }
      OutlinedButton(
        onClick = onEnhanceNow,
        enabled = !studio.isEnhancingPrompt && studio.promptInput.isNotBlank(),
        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_enhance_prompt_button"),
      ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (studio.isEnhancingPrompt) "Enhancing…" else "Sparkle Rewrite")
      }
    }

    Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      PollinationsAspectRatio.entries.forEach { ratio ->
        FilterChip(
          selected = studio.aspectRatioLabel == ratio.label,
          onClick = { onSelectAspectRatio(ratio.label) },
          label = { Text(ratio.label) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
            selectedLabelColor = NeonCyan,
          ),
          modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_ratio_${ratio.name}"),
        )
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = studio.seedText,
        onValueChange = onSeedChanged,
        label = { Text("Seed (optional)") },
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("studio_seed_input"),
      )
      OutlinedButton(
        onClick = { onSeedChanged(((System.currentTimeMillis() % 900_000L) + 100_000L).toString()) },
        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_random_seed_button"),
      ) {
        Icon(Icons.Outlined.Casino, contentDescription = "Random seed", modifier = Modifier.size(18.dp))
      }
    }

    Button(
      onClick = onGenerate,
      enabled = !studio.isBusy && studio.promptInput.isNotBlank(),
      colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("studio_generate_button"),
    ) {
      Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Generate with Pollinations.ai Flux")
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioPreviewStage(
  studio: com.example.ui.model.ImageStudioUiState,
  onOpenLightbox: (String) -> Unit,
  onSaveToGallery: () -> Unit,
  onShareOutput: () -> Unit,
  onSendToChat: () -> Unit,
  onRegenerate: () -> Unit,
  onRemix: () -> Unit,
) {
  val outPath = studio.outputFilePath
  val beforePath = studio.beforeFilePath

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = Color(0xFF0A1020),
    border = BorderStroke(
      width = 1.dp,
      brush = Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.55f), NeonPurple.copy(alpha = 0.55f))),
    ),
    modifier = Modifier.fillMaxWidth().testTag("studio_preview_stage"),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      when {
        studio.isBusy -> {
          Box(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
              CircularProgressIndicator(color = NeonCyan)
              Text(
                text = studio.status?.label ?: "Processing image…",
                style = MaterialTheme.typography.labelLarge,
                color = NeonCyan,
              )
            }
          }
        }
        outPath != null && beforePath != null && outPath != beforePath -> {
          BeforeAfterImageSlider(beforePath = beforePath, afterPath = outPath, modifier = Modifier.fillMaxWidth())
        }
        outPath != null -> {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (studio.outputHasTransparency) Color(0xFF1E293B) else Color(0xFF050811))
                .clickable { onOpenLightbox(outPath) }
                .testTag("studio_output_image"),
            contentAlignment = Alignment.Center,
          ) {
            AsyncImage(
              model = File(outPath),
              contentDescription = "Studio output artwork",
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize(),
            )
            Surface(
              shape = CircleShape,
              color = Color.Black.copy(alpha = 0.65f),
              modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Text("Zoom", color = Color.White, style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
        else -> {
          Box(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Your studio output & Before/After comparison will appear here",
              style = MaterialTheme.typography.bodyMedium,
              color = LocalMousomiColors.current.mutedText,
            )
          }
        }
      }

      if (outPath != null && !studio.isBusy) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = studio.outputSourceBadge ?: "Studio Output",
            style = MaterialTheme.typography.labelSmall,
            color = NeonGreen,
          )
          if (studio.outputWidthPx > 0 && studio.outputHeightPx > 0) {
            Text(
              text = "${studio.outputWidthPx}x${studio.outputHeightPx}" + if (studio.outputHasTransparency) " · PNG Alpha" else "",
              style = MaterialTheme.typography.labelSmall,
              color = LocalMousomiColors.current.mutedText,
            )
          }
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onSaveToGallery,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_save_gallery_button"),
          ) {
            Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Save")
          }
          OutlinedButton(
            onClick = onShareOutput,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_share_button"),
          ) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Share")
          }
          OutlinedButton(
            onClick = onSendToChat,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_send_to_chat_button"),
          ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Send to Chat")
          }
          if (studio.activeFeature == ImageEditFeatureType.TEXT_TO_IMAGE) {
            OutlinedButton(
              onClick = onRegenerate,
              modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_regenerate_button"),
            ) {
              Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Regen")
            }
            OutlinedButton(
              onClick = onRemix,
              modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_remix_button"),
            ) {
              Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Remix")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StudioFullscreenLightboxDialog(
  filePath: String,
  onSave: () -> Unit,
  onShare: () -> Unit,
  onDismiss: () -> Unit,
) {
  var scale by remember { mutableFloatStateOf(1f) }
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.95f))
          .windowInsetsPadding(WindowInsets.statusBars)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .testTag("studio_lightbox_dialog"),
    ) {
      AsyncImage(
        model = File(filePath),
        contentDescription = "Fullscreen image zoom preview",
        contentScale = ContentScale.Fit,
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .pointerInput(Unit) {
              detectTransformGestures { _, _, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 4f)
              }
            },
      )
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).defaultMinSize(48.dp, 48.dp).testTag("studio_lightbox_close"),
      ) {
        Icon(Icons.Rounded.Close, contentDescription = "Close fullscreen preview", tint = Color.White)
      }
      Row(
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(onClick = onSave, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
          Icon(Icons.Outlined.SaveAlt, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("Save")
        }
        OutlinedButton(onClick = onShare, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
          Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White)
          Spacer(Modifier.width(6.dp))
          Text("Share", color = Color.White)
        }
        TextButton(onClick = { scale = 1f }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
          Text("Reset Zoom", color = NeonCyan)
        }
      }
    }
  }
}
