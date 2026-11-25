package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.BackgroundReplaceStyle
import com.example.domain.model.BatchImageItemState
import com.example.domain.model.ImageAdjustmentParams
import com.example.domain.model.ImageEditFeatureType
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.NormPoint
import com.example.domain.model.PosterAspectRatio
import com.example.domain.model.PosterDesignSpec
import com.example.domain.model.PosterFontChoice
import com.example.domain.model.PosterTemplatePreset
import com.example.domain.model.PosterTextLayer
import com.example.ui.model.ImageStudioUiState
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PhotoEnhancePanel(
  state: ImageStudioUiState,
  onUpdateAdjustments: (ImageAdjustmentParams) -> Unit,
  onRotate90: () -> Unit,
  onFlipHorizontal: () -> Unit,
  onApply: () -> Unit,
) {
  val params = state.adjustments
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onRotate90,
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_rotate_button"),
      ) {
        Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Rotate ${state.rotateDegrees.roundToInt()}°")
      }
      OutlinedButton(
        onClick = onFlipHorizontal,
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_flip_button"),
      ) {
        Icon(Icons.Outlined.Flip, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (state.flipHorizontal) "Flipped" else "Flip H")
      }
      TextButton(
        onClick = { onUpdateAdjustments(ImageAdjustmentParams()) },
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
      ) {
        Text("Reset")
      }
    }

    StudioLabeledSlider(
      label = "Brightness",
      valueText = String.format("%+.2f", params.brightness),
      value = params.brightness,
      range = -1f..1f,
      onValueChange = { onUpdateAdjustments(params.copy(brightness = it)) },
    )
    StudioLabeledSlider(
      label = "Contrast",
      valueText = String.format("%.2fx", params.contrast),
      value = params.contrast,
      range = 0.5f..1.8f,
      onValueChange = { onUpdateAdjustments(params.copy(contrast = it)) },
    )
    StudioLabeledSlider(
      label = "Saturation",
      valueText = String.format("%.2fx", params.saturation),
      value = params.saturation,
      range = 0f..2f,
      onValueChange = { onUpdateAdjustments(params.copy(saturation = it)) },
    )
    StudioLabeledSlider(
      label = "Warmth",
      valueText = String.format("%+.2f", params.warmth),
      value = params.warmth,
      range = -1f..1f,
      onValueChange = { onUpdateAdjustments(params.copy(warmth = it)) },
    )
    StudioLabeledSlider(
      label = "Sharpen",
      valueText = "${(params.sharpen * 100).roundToInt()}%",
      value = params.sharpen,
      range = 0f..1f,
      onValueChange = { onUpdateAdjustments(params.copy(sharpen = it)) },
    )
    StudioLabeledSlider(
      label = "Studio Frame Border",
      valueText = "${params.borderThicknessPx}px",
      value = params.borderThicknessPx.toFloat(),
      range = 0f..48f,
      onValueChange = { onUpdateAdjustments(params.copy(borderThicknessPx = it.roundToInt())) },
    )

    Button(
      onClick = onApply,
      enabled = !state.isBusy && state.sourceUriOrPath != null,
      colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("studio_apply_enhance_button"),
    ) {
      Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Apply Realistic Enhancement")
    }
  }
}

@Composable
fun PortraitAndBgPanel(
  state: ImageStudioUiState,
  isPortraitMode: Boolean,
  onUpdateSettings: (bokeh: Float, feather: Float, style: BackgroundReplaceStyle) -> Unit,
  onPickCustomBackdrop: () -> Unit,
  onApply: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (isPortraitMode) {
      StudioLabeledSlider(
        label = "Studio Portrait Bokeh Blur",
        valueText = "${state.portraitBokehAmount.roundToInt()}px",
        value = state.portraitBokehAmount,
        range = 0f..24f,
        onValueChange = { onUpdateSettings(it, state.edgeFeatherPx, state.backgroundStyle) },
      )
    }

    StudioLabeledSlider(
      label = "Subject Edge Feather",
      valueText = String.format("%.1fpx", state.edgeFeatherPx),
      value = state.edgeFeatherPx,
      range = 0f..12f,
      onValueChange = { onUpdateSettings(state.portraitBokehAmount, it, state.backgroundStyle) },
    )

    Text("Backdrop Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BackgroundReplaceStyle.entries.forEach { style ->
        val selected = state.backgroundStyle == style
        FilterChip(
          selected = selected,
          onClick = { onUpdateSettings(state.portraitBokehAmount, state.edgeFeatherPx, style) },
          label = { Text(style.label) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
            selectedLabelColor = NeonCyan,
          ),
          modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_bg_style_${style.name}"),
        )
      }
    }

    if (state.backgroundStyle == BackgroundReplaceStyle.CUSTOM_PHOTO) {
      OutlinedButton(
        onClick = onPickCustomBackdrop,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("studio_pick_backdrop_button"),
      ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          if (state.customBackdropUri == null) "Pick Custom Backdrop Photo" else "Change Backdrop Photo ✓"
        )
      }
    }

    Button(
      onClick = onApply,
      enabled = !state.isBusy && state.sourceUriOrPath != null,
      colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("studio_apply_cutout_button"),
    ) {
      Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(if (isPortraitMode) "Render AI Portrait & Bokeh" else "Isolate Subject & Replace Background")
    }
  }
}

@Composable
fun ObjectMaskCanvasPanel(
  state: ImageStudioUiState,
  onPromptChanged: (String) -> Unit,
  onBrushRadiusChanged: (Float) -> Unit,
  onAddStroke: (List<NormPoint>) -> Unit,
  onUndoStroke: () -> Unit,
  onClearMask: () -> Unit,
  onApply: () -> Unit,
) {
  val activeStroke = remember { mutableStateListOf<NormPoint>() }
  var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = "Paint directly over any unwanted object, then tap Remove / Replace.",
      style = MaterialTheme.typography.bodySmall,
      color = LocalMousomiColors.current.mutedText,
    )

    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(220.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFF0A1020))
          .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
          .onSizeChanged { canvasSize = it }
          .pointerInput(state.sourceUriOrPath) {
            detectDragGestures(
              onDragStart = { offset ->
                activeStroke.clear()
                val w = canvasSize.width.coerceAtLeast(1).toFloat()
                val h = canvasSize.height.coerceAtLeast(1).toFloat()
                activeStroke.add(NormPoint((offset.x / w).coerceIn(0f, 1f), (offset.y / h).coerceIn(0f, 1f)))
              },
              onDrag = { change, _ ->
                change.consume()
                val w = canvasSize.width.coerceAtLeast(1).toFloat()
                val h = canvasSize.height.coerceAtLeast(1).toFloat()
                activeStroke.add(
                  NormPoint(
                    (change.position.x / w).coerceIn(0f, 1f),
                    (change.position.y / h).coerceIn(0f, 1f),
                  )
                )
              },
              onDragEnd = {
                if (activeStroke.isNotEmpty()) {
                  onAddStroke(activeStroke.toList())
                  activeStroke.clear()
                }
              },
              onDragCancel = { activeStroke.clear() },
            )
          }
          .testTag("studio_mask_canvas"),
      contentAlignment = Alignment.Center,
    ) {
      val src = state.sourceUriOrPath
      if (src != null) {
        AsyncImage(
          model = if (src.startsWith("content://") || src.startsWith("file://")) src else File(src),
          contentDescription = "Source photo for object masking",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Text("Pick a photo above to paint an object mask", color = LocalMousomiColors.current.mutedText)
      }

      Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidthPx = (state.maskBrushRadiusNorm * 2f * size.minDimension).coerceAtLeast(10f)
        val drawSingleStroke: (List<NormPoint>) -> Unit = { pts ->
          if (pts.size == 1) {
            drawCircle(
              color = Color(0xAAFF3B7F),
              radius = strokeWidthPx / 2f,
              center = Offset(pts[0].x * size.width, pts[0].y * size.height),
            )
          } else if (pts.size > 1) {
            val path = Path().apply {
              moveTo(pts[0].x * size.width, pts[0].y * size.height)
              for (i in 1 until pts.size) {
                lineTo(pts[i].x * size.width, pts[i].y * size.height)
              }
            }
            drawPath(
              path = path,
              color = Color(0xAAFF3B7F),
              style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
          }
        }
        state.maskPaths.forEach(drawSingleStroke)
        if (activeStroke.isNotEmpty()) drawSingleStroke(activeStroke)
      }
    }

    StudioLabeledSlider(
      label = "Mask Brush Size",
      valueText = "${(state.maskBrushRadiusNorm * 100).roundToInt()}%",
      value = state.maskBrushRadiusNorm,
      range = 0.02f..0.12f,
      onValueChange = onBrushRadiusChanged,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(
        onClick = onUndoStroke,
        enabled = state.maskPaths.isNotEmpty(),
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_mask_undo_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Undo")
      }
      OutlinedButton(
        onClick = onClearMask,
        enabled = state.maskPaths.isNotEmpty(),
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_mask_clear_button"),
      ) {
        Text("Clear (${state.maskPaths.size})")
      }
    }

    OutlinedTextField(
      value = state.promptInput,
      onValueChange = onPromptChanged,
      label = { Text("Remove or replacement instruction") },
      placeholder = { Text("e.g., Remove person on the left and fill natural beach") },
      modifier = Modifier.fillMaxWidth().testTag("studio_mask_prompt_input"),
      maxLines = 3,
    )

    Button(
      onClick = onApply,
      enabled = !state.isBusy && state.sourceUriOrPath != null && state.maskPaths.isNotEmpty(),
      colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("studio_apply_inpaint_button"),
    ) {
      Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Remove / Replace Painted Region")
    }
  }
}

@Composable
fun CreativeAiEditPanel(
  state: ImageStudioUiState,
  onPromptChanged: (String) -> Unit,
  onApply: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(
      value = state.promptInput,
      onValueChange = onPromptChanged,
      label = { Text("Describe your creative transformation") },
      placeholder = { Text("e.g., Golden hour cinematic rim light with cyberpunk reflections") },
      modifier = Modifier.fillMaxWidth().testTag("studio_creative_prompt_input"),
      minLines = 2,
      maxLines = 4,
    )

    state.lastEditPlan?.let { plan ->
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Vision Edit Plan · ${plan.executionTarget.name}",
            style = MaterialTheme.typography.labelMedium,
            color = NeonCyan,
            fontWeight = FontWeight.SemiBold,
          )
          Text(text = plan.summary, style = MaterialTheme.typography.bodySmall)
        }
      }
    }

    Button(
      onClick = onApply,
      enabled = !state.isBusy && state.promptInput.isNotBlank(),
      colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("studio_apply_creative_edit_button"),
    ) {
      Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Execute Creative AI Edit")
    }
  }
}

@Composable
fun BengaliPosterPanel(
  state: ImageStudioUiState,
  onUpdatePosterSpec: (PosterDesignSpec) -> Unit,
  onPickPosterBg: () -> Unit,
  onRenderPoster: () -> Unit,
) {
  val spec = state.posterSpec
  val headline = spec.layers.getOrNull(0) ?: PosterTextLayer(id = "headline", text = "")
  val subtitle = spec.layers.getOrNull(1) ?: PosterTextLayer(id = "subtitle", text = "", fontSizeSp = 24f, yPositionFraction = 0.62f)

  fun updateLayer(index: Int, updated: PosterTextLayer) {
    val mutable = spec.layers.toMutableList()
    while (mutable.size <= index) {
      mutable.add(PosterTextLayer(id = "layer_${mutable.size}", text = ""))
    }
    mutable[index] = updated
    onUpdatePosterSpec(spec.copy(layers = mutable))
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Poster Template", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      PosterTemplatePreset.entries.forEach { preset ->
        FilterChip(
          selected = spec.template == preset,
          onClick = { onUpdatePosterSpec(spec.copy(template = preset)) },
          label = { Text(preset.label) },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_poster_template_${preset.name}"),
        )
      }
    }

    Text("Poster Aspect Ratio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      PosterAspectRatio.entries.forEach { ratio ->
        FilterChip(
          selected = spec.aspectRatio == ratio,
          onClick = { onUpdatePosterSpec(spec.copy(aspectRatio = ratio)) },
          label = { Text(ratio.label) },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_poster_ratio_${ratio.name}"),
        )
      }
    }

    OutlinedTextField(
      value = headline.text,
      onValueChange = { updateLayer(0, headline.copy(text = it)) },
      label = { Text("Headline (Bengali or English)") },
      placeholder = { Text("শিরোনাম লিখুন…") },
      modifier = Modifier.fillMaxWidth().testTag("studio_poster_headline_input"),
      maxLines = 3,
    )

    OutlinedTextField(
      value = subtitle.text,
      onValueChange = { updateLayer(1, subtitle.copy(text = it)) },
      label = { Text("Subtitle / Quote") },
      modifier = Modifier.fillMaxWidth().testTag("studio_poster_subtitle_input"),
      maxLines = 2,
    )

    Text("Bundled Display Font (Zero Garbling)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      PosterFontChoice.entries.forEach { font ->
        FilterChip(
          selected = headline.fontChoice == font,
          onClick = { updateLayer(0, headline.copy(fontChoice = font)) },
          label = { Text(font.label) },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_poster_font_${font.name}"),
        )
      }
    }

    StudioLabeledSlider(
      label = "Headline Font Size",
      valueText = "${headline.fontSizeSp.roundToInt()}sp",
      value = headline.fontSizeSp,
      range = 24f..76f,
      onValueChange = { updateLayer(0, headline.copy(fontSizeSp = it)) },
    )

    StudioLabeledSlider(
      label = "Text Backdrop Pill Opacity",
      valueText = "${(headline.backdropOpacity * 100).roundToInt()}%",
      value = headline.backdropOpacity,
      range = 0f..0.75f,
      onValueChange = { updateLayer(0, headline.copy(backdropOpacity = it)) },
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(
        onClick = onPickPosterBg,
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_poster_bg_pick_button"),
      ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (spec.backgroundPhotoPath == null) "Optional BG Photo" else "Change BG Photo")
      }
      if (spec.backgroundPhotoPath != null) {
        TextButton(
          onClick = { onUpdatePosterSpec(spec.copy(backgroundPhotoPath = null)) },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        ) {
          Text("Use Gradient")
        }
      }
    }

    Button(
      onClick = onRenderPoster,
      enabled = !state.isBusy && (headline.text.isNotBlank() || subtitle.text.isNotBlank()),
      colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("studio_render_poster_button"),
    ) {
      Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Render Crisp Bengali & English Poster")
    }
  }
}

@Composable
fun BatchMultiPhotoPanel(
  state: ImageStudioUiState,
  batchItems: List<BatchImageItemState>,
  isBatchRunning: Boolean,
  selectedBatchOperation: ImageEditFeatureType,
  onSelectBatchOperation: (ImageEditFeatureType) -> Unit,
  onPickMultiplePhotos: () -> Unit,
  onStartBatch: () -> Unit,
  onCancelAll: () -> Unit,
  onRetryItem: (String) -> Unit,
  onSaveItem: (String) -> Unit,
  onCreateCollage: () -> Unit,
) {
  val batchOps = remember {
    listOf(
      ImageEditFeatureType.PHOTO_ENHANCE,
      ImageEditFeatureType.BACKGROUND_REMOVE_REPLACE,
      ImageEditFeatureType.PORTRAIT_EDIT,
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(
        onClick = onPickMultiplePhotos,
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_batch_pick_button"),
      ) {
        Icon(Icons.Outlined.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Pick Photos (${batchItems.size}/6)")
      }
      OutlinedButton(
        onClick = onCreateCollage,
        enabled = !state.isBusy && (batchItems.size >= 2 || state.collageUris.size >= 2),
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_batch_collage_button"),
      ) {
        Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("2x2 Collage")
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      batchOps.forEach { op ->
        FilterChip(
          selected = selectedBatchOperation == op,
          onClick = { onSelectBatchOperation(op) },
          label = { Text(op.title) },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_batch_op_${op.name}"),
        )
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = onStartBatch,
        enabled = !isBatchRunning && batchItems.isNotEmpty(),
        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_batch_start_button"),
      ) {
        Text(if (isBatchRunning) "Processing Queue…" else "Run Batch Queue")
      }
      if (isBatchRunning) {
        OutlinedButton(
          onClick = onCancelAll,
          modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_batch_cancel_button"),
        ) {
          Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Stop")
        }
      }
    }

    batchItems.forEach { item ->
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag("studio_batch_item_${item.itemId}"),
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = item.displayName,
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = item.status.label,
              style = MaterialTheme.typography.labelSmall,
              color = if (item.status == ImageGenerationStatus.COMPLETED) NeonGreen else NeonCyan,
            )
          }
          if (item.status == ImageGenerationStatus.GENERATING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
          item.errorMessage?.let { err ->
            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.status == ImageGenerationStatus.FAILED || item.status == ImageGenerationStatus.CANCELLED) {
              TextButton(
                onClick = { onRetryItem(item.itemId) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_batch_retry_${item.itemId}"),
              ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry")
              }
            }
            if (item.status == ImageGenerationStatus.COMPLETED && !item.outputFilePath.isNullOrBlank()) {
              TextButton(
                onClick = { onSaveItem(item.itemId) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_batch_save_${item.itemId}"),
              ) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save to Gallery")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StudioLabeledSlider(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = label, style = MaterialTheme.typography.labelMedium)
      Text(text = valueText, style = MaterialTheme.typography.labelMedium, color = NeonCyan)
    }
    Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
  }
}
