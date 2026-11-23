package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.screen.CircleSelectionProcessor
import com.example.core.screen.CropAspectPreset
import com.example.core.screen.CropHandle
import com.example.core.screen.DetectedRegionShape
import com.example.core.screen.FreehandPathProcessor
import com.example.core.screen.ImageEditState
import com.example.core.screen.RectangleSelectionProcessor
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionGeometryResult
import com.example.core.screen.SelectionToolMode
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import java.io.File

/**
 * Compact dark liquid-glass Image Editing Studio opened from "Edit images" above the composer.
 * Supports Adjust Crop (4 corner & 4 edge handles), Reposition Selection (drag inside box),
 * Resize Crop aspect presets, Rotate 90deg, Retake Selection, Cancel, and Confirm.
 */
@Composable
fun CompactImageEditorSheet(
  editState: ImageEditState,
  onAdjustHandle: (CropHandle, Float, Float) -> Unit,
  onSelectAspectPreset: (CropAspectPreset) -> Unit,
  onRotate90: () -> Unit,
  onRetakeSelection: () -> Unit,
  onResetCrop: () -> Unit,
  onCancel: () -> Unit,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onCancel)

  val geometryEngine =
    remember {
      SelectionGeometryEngine(
        freehandProcessor = FreehandPathProcessor(),
        circleProcessor = CircleSelectionProcessor(),
        rectangleProcessor = RectangleSelectionProcessor(),
      )
    }

  val previewSnapped =
    SelectionGeometryResult(
      boundingBox = editState.currentNormCropRect,
      detectedShape = DetectedRegionShape.RECTANGLE,
      cornerRadiusFraction = 0.08f,
      confidence = 1.0f,
    )

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Color(0xF2060912))
        .testTag("compact_image_editor_sheet")
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.statusBars)
          .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
      // Top studio bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onCancel, modifier = Modifier.testTag("editor_cancel_icon")) {
          Icon(Icons.Rounded.Close, contentDescription = "Cancel editing", tint = Color.White)
        }
        Text(
          text = "Edit Selection",
          color = Color.White,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onResetCrop, modifier = Modifier.testTag("editor_reset_button")) {
          Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset crop", tint = NeonCyan)
        }
      }

      // Interactive crop canvas over preserved full-res screen frame
      Box(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
      ) {
        val frameFile = File(editState.sourceFrameFilePath)
        AsyncImage(
          model = if (frameFile.exists()) frameFile else editState.sourceFrameFilePath,
          contentDescription = "Original captured frame for crop adjustment",
          contentScale = ContentScale.FillBounds,
          modifier = Modifier.fillMaxSize().rotate(editState.rotationDegrees.toFloat()),
        )

        SelectionHighlightRenderer(
          toolMode = SelectionToolMode.RECTANGLE,
          activeStrokePoints = emptyList(),
          snappedResult = previewSnapped,
          geometryEngine = geometryEngine,
          onStrokeStart = {},
          onStrokeMove = {},
          onStrokeEnd = {},
          onHandleDrag = onAdjustHandle,
          dimBackgroundAlpha = 0.62f,
        )
      }

      // Compact glass bottom controls
      Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = LiquidGlassDark.copy(alpha = 0.96f),
        border =
          BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.45f), NeonPurple.copy(alpha = 0.45f))),
          ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Aspect ratio & Rotate controls
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CropAspectPreset.entries.forEach { preset ->
              val selected = preset == editState.aspectPreset
              Surface(
                onClick = { onSelectAspectPreset(preset) },
                shape = CircleShape,
                color =
                  if (selected) ElectricBlue.copy(alpha = 0.34f)
                  else Color.White.copy(alpha = 0.08f),
                border =
                  if (selected) BorderStroke(1.dp, NeonCyan)
                  else BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier.testTag("editor_aspect_${preset.name}"),
              ) {
                Text(
                  text = preset.label,
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
              }
            }

            Surface(
              onClick = onRotate90,
              shape = CircleShape,
              color = Color.White.copy(alpha = 0.08f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
              modifier = Modifier.testTag("editor_rotate_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.RotateRight,
                  contentDescription = "Rotate 90 degrees",
                  tint = NeonCyan,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Rotate ${editState.rotationDegrees}°",
                  color = Color.White,
                  fontSize = 12.sp,
                )
              }
            }
          }

          // Retake / Cancel / Confirm actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(
              onClick = onRetakeSelection,
              border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f)),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.testTag("editor_retake_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.CenterFocusStrong,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Retake", color = Color.White, fontSize = 13.sp)
            }

            OutlinedButton(
              onClick = onCancel,
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).testTag("editor_cancel_button"),
            ) {
              Text("Cancel", color = Color.White, fontSize = 13.sp)
            }

            Button(
              onClick = onConfirm,
              colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).testTag("editor_confirm_button"),
            ) {
              Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
              Spacer(modifier = Modifier.width(5.dp))
              Text("Confirm", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}
