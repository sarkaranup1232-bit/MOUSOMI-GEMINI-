package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.screen.CropHandle
import com.example.core.screen.ScreenSelectionUiState
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionStage
import com.example.core.screen.SelectionToolMode
import com.example.domain.model.NormPoint
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import java.io.File

/**
 * Full-screen interactive Live Screen Selection host reproducing the reference video's
 * perimeter glow -> smooth finger stroke -> smart snap neon highlight -> auto-crop flow.
 */
@Composable
fun ScreenSelectionOverlay(
  state: ScreenSelectionUiState,
  geometryEngine: SelectionGeometryEngine,
  onSelectTool: (SelectionToolMode) -> Unit,
  onStrokeStart: (NormPoint) -> Unit,
  onStrokeMove: (NormPoint) -> Unit,
  onStrokeEnd: () -> Unit,
  onHandleDrag: (CropHandle, Float, Float) -> Unit,
  onUndo: () -> Unit,
  onClear: () -> Unit,
  onCancel: () -> Unit,
  onConfirmSelection: (overlayWidthPx: Int, overlayHeightPx: Int) -> Unit,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
) {
  if (!state.isActive) return

  BackHandler(onBack = onCancel)

  var overlayWidthPx by remember { mutableIntStateOf(0) }
  var overlayHeightPx by remember { mutableIntStateOf(0) }

  // Dynamically place toolbar at top if snapped box sits in lower half, otherwise bottom
  val snappedCenterY =
    state.snappedResult?.boundingBox?.let { (it.top + it.bottom) * 0.5f } ?: 0.5f
  val placeToolbarAtTop = state.snappedResult != null && snappedCenterY > 0.62f

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          if (state.sourceFrameFilePath != null) Color.Black
          else Color.Transparent
        )
        .onSizeChanged { sz ->
          overlayWidthPx = sz.width
          overlayHeightPx = sz.height
        }
        .testTag("screen_selection_overlay")
  ) {
    // 1. Authorized captured screen snapshot (in-app selection mode)
    val framePath = state.sourceFrameFilePath
    if (!framePath.isNullOrBlank() && File(framePath).exists()) {
      AsyncImage(
        model = File(framePath),
        contentDescription = "Captured screen for region selection",
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize(),
      )
    }

    // 2. Interactive stroke & neon snapped highlight cutout canvas
    SelectionHighlightRenderer(
      toolMode = state.toolMode,
      activeStrokePoints = state.activeStrokePoints,
      snappedResult = state.snappedResult,
      geometryEngine = geometryEngine,
      onStrokeStart = onStrokeStart,
      onStrokeMove = onStrokeMove,
      onStrokeEnd = onStrokeEnd,
      onHandleDrag = onHandleDrag,
      reducedMotion = reducedMotion,
    )

    // 3. Subtle continuous multicolor perimeter neon glow around screen edges
    SelectionScreenEdgeGlow(
      visible = true,
      reducedMotion = reducedMotion,
    )

    // 4. Floating "Ask Mousomi" header hint pill (reference video top/bottom indicator)
    AnimatedVisibility(
      visible = state.stage != SelectionStage.DRAWING && !placeToolbarAtTop,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier =
        Modifier
          .align(Alignment.TopCenter)
          .windowInsetsPadding(WindowInsets.statusBars)
          .padding(top = 12.dp, start = 16.dp, end = 16.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = LiquidGlassDark.copy(alpha = 0.88f),
        border =
          BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.65f), NeonPurple.copy(alpha = 0.5f))),
          ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GeminiFourPointSparkle(size = 16.dp, animated = true)
          Text(
            text = state.statusHint,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }

    // 5. Compact non-obstructive Selection Toolbar
    val toolbarModifier =
      Modifier
        .align(if (placeToolbarAtTop) Alignment.TopCenter else Alignment.BottomCenter)
        .windowInsetsPadding(if (placeToolbarAtTop) WindowInsets.statusBars else WindowInsets.navigationBars)
        .padding(horizontal = 12.dp, vertical = 14.dp)

    SelectionToolbar(
      activeTool = state.toolMode,
      canUndo = state.undoHistory.isNotEmpty() || state.snappedResult != null,
      hasSnappedSelection = state.snappedResult != null,
      isBusyCropping = state.stage == SelectionStage.CAPTURING_AND_CROPPING,
      onSelectTool = onSelectTool,
      onUndo = onUndo,
      onClear = onClear,
      onCancel = onCancel,
      onConfirm = { onConfirmSelection(overlayWidthPx, overlayHeightPx) },
      modifier = toolbarModifier,
    )

    // 6. Cropping progress badge
    if (state.stage == SelectionStage.CAPTURING_AND_CROPPING) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = LiquidGlassDark.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
        modifier = Modifier.align(Alignment.Center),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(
            color = NeonCyan,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Capturing selection…",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }

    // 7. Honest error / FLAG_SECURE notice banner if capture was blocked or failed
    val err = state.errorMessage
    if (!err.isNullOrBlank()) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1E1320).copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f)),
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = err,
            color = Color.White,
            fontSize = 13.sp,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) {
              Text("Close", color = Color.White.copy(alpha = 0.8f))
            }
            Button(
              onClick = onClear,
              colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
            ) {
              Text("Try Again", color = Color.White)
            }
          }
        }
      }
    }
  }
}
