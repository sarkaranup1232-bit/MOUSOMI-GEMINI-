package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.screen.SelectionToolMode
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple

/**
 * Compact, non-obstructive liquid-glass selection toolbar providing:
 * Selection Mode indicator, Smart / Freehand / Circle / Rectangle tools, Undo, Clear, Cancel, and Confirm.
 */
@Composable
fun SelectionToolbar(
  activeTool: SelectionToolMode,
  canUndo: Boolean,
  hasSnappedSelection: Boolean,
  isBusyCropping: Boolean,
  onSelectTool: (SelectionToolMode) -> Unit,
  onUndo: () -> Unit,
  onClear: () -> Unit,
  onCancel: () -> Unit,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(26.dp),
    color = LiquidGlassDark.copy(alpha = 0.94f),
    border =
      BorderStroke(
        width = 1.2.dp,
        brush = Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.7f), NeonPurple.copy(alpha = 0.65f))),
      ),
    shadowElevation = 14.dp,
    modifier = modifier.testTag("selection_toolbar"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        SelectionToolMode.entries.forEach { mode ->
          val isSelected = mode == activeTool
          val icon = toolIcon(mode)
          Surface(
            onClick = { onSelectTool(mode) },
            shape = CircleShape,
            color =
              if (isSelected) ElectricBlue.copy(alpha = 0.34f)
              else Color.White.copy(alpha = 0.06f),
            border =
              if (isSelected) BorderStroke(1.2.dp, NeonCyan)
              else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.testTag("selection_tool_${mode.name}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = icon,
                contentDescription = mode.label,
                tint = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = mode.label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
              )
            }
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(
          onClick = onUndo,
          enabled = canUndo && !isBusyCropping,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("selection_undo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Undo,
            contentDescription = "Undo selection",
            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.32f),
            modifier = Modifier.size(20.dp),
          )
        }

        IconButton(
          onClick = onClear,
          enabled = hasSnappedSelection && !isBusyCropping,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("selection_clear_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Clear selection",
            tint = if (hasSnappedSelection) Color.White else Color.White.copy(alpha = 0.32f),
            modifier = Modifier.size(20.dp),
          )
        }

        IconButton(
          onClick = onCancel,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("selection_cancel_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Cancel screen selection",
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp),
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Surface(
          onClick = onConfirm,
          enabled = hasSnappedSelection && !isBusyCropping,
          shape = CircleShape,
          color =
            if (hasSnappedSelection) ElectricBlue
            else Color.White.copy(alpha = 0.1f),
          border =
            if (hasSnappedSelection)
              BorderStroke(1.dp, Brush.linearGradient(listOf(NeonCyan, NeonMagenta)))
            else null,
          modifier = Modifier.testTag("selection_confirm_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Check,
              contentDescription = "Confirm selection",
              tint = if (hasSnappedSelection) Color.White else Color.White.copy(alpha = 0.4f),
              modifier = Modifier.size(17.dp),
            )
            Text(
              text = if (isBusyCropping) "Cropping…" else "Ask Mousomi",
              color = if (hasSnappedSelection) Color.White else Color.White.copy(alpha = 0.45f),
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

private fun toolIcon(mode: SelectionToolMode): ImageVector =
  when (mode) {
    SelectionToolMode.SMART_LOOP -> Icons.Outlined.AutoAwesome
    SelectionToolMode.FREEHAND -> Icons.Outlined.Gesture
    SelectionToolMode.CIRCLE -> Icons.Outlined.RadioButtonUnchecked
    SelectionToolMode.RECTANGLE -> Icons.Outlined.CropSquare
  }
