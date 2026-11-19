package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.screen.RegionCropper
import com.example.domain.model.AnnotationStroke
import com.example.domain.model.DrawingTool
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import java.io.File
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val DRAWING_PALETTE =
  listOf(
    0xFF38BDF8L, // Sky Cyan
    0xFF3B82F6L, // Gemini Blue
    0xFFA855F7L, // Purple
    0xFFEC4899L, // Pink
    0xFF10B981L, // Emerald
    0xFFF59E0BL, // Amber
  )

/**
 * Dedicated isolated On-Screen Drawing & Annotation Studio.
 * - Blocks underlying touch targets cleanly.
 * - Supports Pen, Highlighter, Selection Box, Arrow, Circle, Box, Pointer, Text, Eraser.
 * - Supports Undo, Redo, Clear, Save Drawing, Submit to AI, and unsaved-stroke Discard confirmation.
 */
@Composable
fun DrawingAnnotationOverlay(
  onAnalyzeMarkedRegion: (NormBoundingBox, List<AnnotationStroke>) -> Unit,
  onClose: () -> Unit,
  backgroundImagePath: String? = null,
  backgroundImageUri: String? = null,
  onSaveDrawing: ((NormBoundingBox, List<AnnotationStroke>) -> Unit)? = null,
) {
  val strokes = remember { mutableStateListOf<AnnotationStroke>() }
  val redoStack = remember { mutableStateListOf<AnnotationStroke>() }
  var selectedTool by remember { mutableStateOf(DrawingTool.FREEHAND) }
  var selectedColorArgb by remember { mutableLongStateOf(0xFF38BDF8L) }
  var strokeWidthPx by remember { mutableFloatStateOf(9f) }
  var strokeAlpha by remember { mutableFloatStateOf(0.92f) }
  var isMinimized by remember { mutableStateOf(false) }

  var activePoints by remember { mutableStateOf<List<NormPoint>>(emptyList()) }
  var pendingTextLocation by remember { mutableStateOf<NormPoint?>(null) }
  var textInput by remember { mutableStateOf("") }
  var hasUnsavedTemporaryStrokes by remember { mutableStateOf(false) }
  var showDiscardConfirmDialog by remember { mutableStateOf(false) }
  val textMeasurer = rememberTextMeasurer()

  val clearTemporarySessionState: () -> Unit = {
    strokes.clear()
    redoStack.clear()
    activePoints = emptyList()
    pendingTextLocation = null
    textInput = ""
    hasUnsavedTemporaryStrokes = false
  }

  val handleRequestClose: () -> Unit = {
    if (hasUnsavedTemporaryStrokes && strokes.isNotEmpty()) {
      showDiscardConfirmDialog = true
    } else {
      clearTemporarySessionState()
      onClose()
    }
  }

  BackHandler(onBack = handleRequestClose)

  val mergedBottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(
          Color.Black.copy(alpha = if (backgroundImagePath != null || backgroundImageUri != null) 0.94f else 0.34f)
        )
        // Consume any stray background pointer events so chat/welcome items behind never fire
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              event.changes.forEach { it.consume() }
            }
          }
        }
        .testTag("drawing_annotation_overlay")
  ) {
    val bgModel: Any? = backgroundImagePath?.takeIf { it.isNotBlank() }?.let { File(it) } ?: backgroundImageUri
    if (bgModel != null) {
      AsyncImage(
        model = bgModel,
        contentDescription = "Target image for region selection",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(bottom = 175.dp),
      )
    }

    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .pointerInput(selectedTool, selectedColorArgb, strokeWidthPx, strokeAlpha) {
            detectTapGestures { offset ->
              val norm = NormPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))
              if (selectedTool == DrawingTool.POINTER) {
                redoStack.clear()
                strokes.add(
                  AnnotationStroke(
                    id = "str_${UUID.randomUUID()}",
                    tool = DrawingTool.POINTER,
                    colorArgb = selectedColorArgb,
                    strokeWidthPx = strokeWidthPx,
                    alpha = strokeAlpha,
                    points = listOf(norm),
                  )
                )
                hasUnsavedTemporaryStrokes = true
              } else if (selectedTool == DrawingTool.TEXT) {
                pendingTextLocation = norm
                textInput = ""
              } else if (selectedTool == DrawingTool.ERASER && strokes.isNotEmpty()) {
                val removed = strokes.removeAt(strokes.lastIndex)
                redoStack.add(removed)
                hasUnsavedTemporaryStrokes = strokes.isNotEmpty()
              }
            }
          }
          .pointerInput(selectedTool, selectedColorArgb, strokeWidthPx, strokeAlpha) {
            detectDragGestures(
              onDragStart = { start ->
                val norm = NormPoint((start.x / size.width).coerceIn(0f, 1f), (start.y / size.height).coerceIn(0f, 1f))
                activePoints = listOf(norm)
              },
              onDrag = { change, _ ->
                change.consume()
                val pos = change.position
                val norm = NormPoint((pos.x / size.width).coerceIn(0f, 1f), (pos.y / size.height).coerceIn(0f, 1f))
                if (selectedTool == DrawingTool.ERASER) {
                  val idx =
                    strokes.indexOfLast { s ->
                      s.points.any { p -> hypot(p.x - norm.x, p.y - norm.y) < 0.04f }
                    }
                  if (idx >= 0) {
                    redoStack.add(strokes.removeAt(idx))
                    hasUnsavedTemporaryStrokes = strokes.isNotEmpty()
                  }
                } else {
                  activePoints = activePoints + norm
                }
              },
              onDragEnd = {
                if (selectedTool != DrawingTool.ERASER && activePoints.size >= 2) {
                  redoStack.clear()
                  strokes.add(
                    AnnotationStroke(
                      id = "str_${UUID.randomUUID()}",
                      tool = selectedTool,
                      colorArgb = selectedColorArgb,
                      strokeWidthPx =
                        if (selectedTool == DrawingTool.HIGHLIGHTER) strokeWidthPx * 2.4f else strokeWidthPx,
                      alpha =
                        if (selectedTool == DrawingTool.HIGHLIGHTER) (strokeAlpha * 0.42f).coerceIn(0.15f, 0.6f)
                        else strokeAlpha,
                      points = activePoints,
                    )
                  )
                  hasUnsavedTemporaryStrokes = true
                }
                activePoints = emptyList()
              },
              onDragCancel = { activePoints = emptyList() },
            )
          }
    ) {
      val allToRender =
        if (activePoints.isNotEmpty() && selectedTool != DrawingTool.ERASER) {
          strokes +
            AnnotationStroke(
              id = "active",
              tool = selectedTool,
              colorArgb = selectedColorArgb,
              strokeWidthPx =
                if (selectedTool == DrawingTool.HIGHLIGHTER) strokeWidthPx * 2.4f else strokeWidthPx,
              alpha =
                if (selectedTool == DrawingTool.HIGHLIGHTER) (strokeAlpha * 0.42f).coerceIn(0.15f, 0.6f)
                else strokeAlpha,
              points = activePoints,
            )
        } else {
          strokes
        }

      allToRender.forEach { stroke ->
        drawAnnotationStroke(stroke = stroke, canvasSize = size, textMeasurer = textMeasurer)
      }
    }

    if (isMinimized) {
      Surface(
        onClick = { isMinimized = false },
        shape = CircleShape,
        color = Color(0xFF10182E),
        border = BorderStroke(1.dp, NeonCyan),
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(16.dp)
            .testTag("drawing_expand_pill"),
      ) {
        Text("Drawing Controls", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
      }
    } else {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0C1222).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(mergedBottomInsets)
            .padding(12.dp)
            .fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Drawing & Annotation Studio",
              color = Color.White,
              style = MaterialTheme.typography.labelLarge,
            )
            Row {
              IconButton(
                onClick = {
                  if (strokes.isNotEmpty()) {
                    redoStack.add(strokes.removeAt(strokes.lastIndex))
                    hasUnsavedTemporaryStrokes = strokes.isNotEmpty()
                  }
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.testTag("drawing_undo_button"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo", tint = Color.White)
              }
              IconButton(
                onClick = {
                  if (redoStack.isNotEmpty()) {
                    strokes.add(redoStack.removeAt(redoStack.lastIndex))
                    hasUnsavedTemporaryStrokes = true
                  }
                },
                enabled = redoStack.isNotEmpty(),
                modifier = Modifier.testTag("drawing_redo_button"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo", tint = Color.White)
              }
              IconButton(
                onClick = { clearTemporarySessionState() },
                modifier = Modifier.testTag("drawing_clear_button"),
              ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear All", tint = Color.White)
              }
              IconButton(onClick = { isMinimized = true }) {
                Icon(Icons.Rounded.Minimize, contentDescription = "Minimize toolbar", tint = Color.White)
              }
              IconButton(onClick = handleRequestClose, modifier = Modifier.testTag("drawing_close_button")) {
                Icon(Icons.Rounded.Close, contentDescription = "Close drawing mode", tint = Color.White)
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            DrawingTool.entries.forEach { tool ->
              FilterChip(
                selected = selectedTool == tool,
                onClick = { selectedTool = tool },
                label = { Text(tool.label, fontSize = 12.sp) },
                modifier = Modifier.testTag("drawing_tool_${tool.name}"),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            DRAWING_PALETTE.forEach { colorHex ->
              Surface(
                onClick = { selectedColorArgb = colorHex },
                shape = CircleShape,
                color = Color(colorHex),
                border =
                  if (selectedColorArgb == colorHex) BorderStroke(2.5.dp, Color.White)
                  else null,
                modifier = Modifier.size(26.dp),
              ) {}
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text("Width", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Slider(
              value = strokeWidthPx,
              onValueChange = { strokeWidthPx = it },
              valueRange = 4f..24f,
              modifier = Modifier.weight(1f),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("Alpha", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Slider(
              value = strokeAlpha,
              onValueChange = { strokeAlpha = it },
              valueRange = 0.25f..1.0f,
              modifier = Modifier.weight(1f),
            )
            if (onSaveDrawing != null) {
              OutlinedButton(
                onClick = {
                  val box =
                    RegionCropper.computeBoundingBox(strokes.toList())
                      ?: NormBoundingBox(0.05f, 0.1f, 0.95f, 0.9f)
                  onSaveDrawing(box, strokes.toList())
                  hasUnsavedTemporaryStrokes = false
                },
                enabled = strokes.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(44.dp).testTag("drawing_save_button"),
              ) {
                Icon(Icons.Outlined.BookmarkAdded, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save", color = Color.White, fontSize = 12.sp)
              }
            }
            Button(
              onClick = {
                val box =
                  RegionCropper.computeBoundingBox(strokes.toList())
                    ?: NormBoundingBox(0.05f, 0.1f, 0.95f, 0.9f)
                val finalStrokes = strokes.toList()
                hasUnsavedTemporaryStrokes = false
                onAnalyzeMarkedRegion(box, finalStrokes)
              },
              colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.height(44.dp).testTag("drawing_analyze_button"),
            ) {
              Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
              Spacer(modifier = Modifier.width(5.dp))
              Text("Submit", color = Color.White, fontSize = 12.sp)
            }
          }
        }
      }
    }

    pendingTextLocation?.let { loc ->
      AlertDialog(
        onDismissRequest = { pendingTextLocation = null },
        title = { Text("Add Text Label") },
        text = {
          OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Annotation text") },
            singleLine = true,
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              if (textInput.isNotBlank()) {
                redoStack.clear()
                strokes.add(
                  AnnotationStroke(
                    id = "txt_${UUID.randomUUID()}",
                    tool = DrawingTool.TEXT,
                    colorArgb = selectedColorArgb,
                    strokeWidthPx = strokeWidthPx,
                    alpha = strokeAlpha,
                    points = listOf(loc),
                    textLabel = textInput.trim(),
                  )
                )
                hasUnsavedTemporaryStrokes = true
              }
              pendingTextLocation = null
            }
          ) { Text("Add") }
        },
        dismissButton = {
          TextButton(onClick = { pendingTextLocation = null }) { Text("Cancel") }
        },
      )
    }

    if (showDiscardConfirmDialog) {
      AlertDialog(
        onDismissRequest = { showDiscardConfirmDialog = false },
        title = { Text("Unsaved Drawing") },
        text = { Text("Save your temporary sketch before closing, or discard it? Saved drawings in history will remain safe.") },
        confirmButton = {
          Button(
            onClick = {
              val box =
                RegionCropper.computeBoundingBox(strokes.toList())
                  ?: NormBoundingBox(0.05f, 0.1f, 0.95f, 0.9f)
              onSaveDrawing?.invoke(box, strokes.toList())
              showDiscardConfirmDialog = false
              clearTemporarySessionState()
              onClose()
            },
            modifier = Modifier.testTag("drawing_confirm_save_close"),
          ) { Text("Save & Close") }
        },
        dismissButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
              onClick = { showDiscardConfirmDialog = false },
              modifier = Modifier.testTag("drawing_keep_drawing"),
            ) { Text("Keep Editing") }
            TextButton(
              onClick = {
                showDiscardConfirmDialog = false
                clearTemporarySessionState()
                onClose()
              },
              modifier = Modifier.testTag("drawing_confirm_discard"),
            ) { Text("Discard", color = Color(0xFFEF4444)) }
          }
        },
      )
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotationStroke(
  stroke: AnnotationStroke,
  canvasSize: Size,
  textMeasurer: TextMeasurer,
) {
  if (stroke.points.isEmpty()) return
  val color = Color(stroke.colorArgb).copy(alpha = stroke.alpha.coerceIn(0.1f, 1f))
  val pts = stroke.points.map { Offset(it.x * canvasSize.width, it.y * canvasSize.height) }
  val first = pts.first()
  val last = pts.last()

  when (stroke.tool) {
    DrawingTool.FREEHAND,
    DrawingTool.HIGHLIGHTER -> {
      val path = Path().apply {
        moveTo(first.x, first.y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
      }
      drawPath(
        path = path,
        color = color,
        style = Stroke(width = stroke.strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (stroke.tool == DrawingTool.HIGHLIGHTER) BlendMode.Plus else BlendMode.SrcOver,
      )
    }
    DrawingTool.SELECTION -> {
      val left = min(first.x, last.x)
      val top = min(first.y, last.y)
      val w = kotlin.math.abs(last.x - first.x).coerceAtLeast(16f)
      val h = kotlin.math.abs(last.y - first.y).coerceAtLeast(16f)
      drawRect(
        color = color.copy(alpha = 0.14f),
        topLeft = Offset(left, top),
        size = Size(w, h),
      )
      drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(w, h),
        style =
          Stroke(
            width = (stroke.strokeWidthPx * 0.6f).coerceIn(3f, 8f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f),
          ),
      )
    }
    DrawingTool.ARROW -> {
      drawLine(color = color, start = first, end = last, strokeWidth = stroke.strokeWidthPx, cap = StrokeCap.Round)
      val angle = atan2((last.y - first.y).toDouble(), (last.x - first.x).toDouble())
      val headLen = (stroke.strokeWidthPx * 2.8f).coerceAtLeast(22f)
      val wing1 =
        Offset(
          last.x - (headLen * cos(angle - 0.5)).toFloat(),
          last.y - (headLen * sin(angle - 0.5)).toFloat(),
        )
      val wing2 =
        Offset(
          last.x - (headLen * cos(angle + 0.5)).toFloat(),
          last.y - (headLen * sin(angle + 0.5)).toFloat(),
        )
      drawLine(color = color, start = last, end = wing1, strokeWidth = stroke.strokeWidthPx, cap = StrokeCap.Round)
      drawLine(color = color, start = last, end = wing2, strokeWidth = stroke.strokeWidthPx, cap = StrokeCap.Round)
    }
    DrawingTool.RECTANGLE -> {
      val left = min(first.x, last.x)
      val top = min(first.y, last.y)
      val w = kotlin.math.abs(last.x - first.x).coerceAtLeast(8f)
      val h = kotlin.math.abs(last.y - first.y).coerceAtLeast(8f)
      drawRect(color = color, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = stroke.strokeWidthPx))
    }
    DrawingTool.CIRCLE -> {
      val radius = hypot(last.x - first.x, last.y - first.y).coerceAtLeast(12f)
      drawCircle(color = color, radius = radius, center = first, style = Stroke(width = stroke.strokeWidthPx))
    }
    DrawingTool.POINTER -> {
      drawCircle(color = color.copy(alpha = 0.28f), radius = 34f, center = first)
      drawCircle(color = color, radius = 14f, center = first)
    }
    DrawingTool.TEXT -> {
      if (stroke.textLabel.isNotBlank()) {
        val result =
          textMeasurer.measure(
            text = stroke.textLabel,
            style = TextStyle(color = color, fontSize = 18.sp),
          )
        drawText(result, topLeft = first)
      }
    }
    DrawingTool.ERASER -> Unit
  }
}
