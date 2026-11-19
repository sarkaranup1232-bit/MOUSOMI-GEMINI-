package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.OrbPopupAction
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.WarmAmber

/**
 * 12-action liquid-glass Orbit Quick Launcher (Module 39).
 */
@Composable
fun FloatingOrbitPopup(
  onSelectAction: (OrbPopupAction) -> Unit,
  onDismiss: () -> Unit,
  onDrawScreen: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(26.dp),
    color = LiquidGlassDark,
    border =
      BorderStroke(
        width = 1.6.dp,
        brush = Brush.linearGradient(MousomiDesignTokens.GoogleNeonSpectrum),
      ),
    shadowElevation = 18.dp,
    modifier = modifier.widthIn(max = 380.dp).fillMaxWidth().testTag("floating_orbit_popup"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          GeminiFourPointSparkle(size = 18.dp, animated = true)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Mousomi Orbit Launcher",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(34.dp).testTag("orbit_popup_close"),
        ) {
          Icon(
            Icons.Rounded.Close,
            contentDescription = "Collapse orbit popup",
            tint = Color.White.copy(alpha = 0.78f),
          )
        }
      }

      val entries = OrbPopupAction.entries
      val rows = entries.chunked(3)
      rows.forEach { rowActions ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          rowActions.forEach { action ->
            OrbitActionTile(
              action = action,
              onClick = { onSelectAction(action) },
              modifier = Modifier.weight(1f),
            )
          }
          repeat(3 - rowActions.size) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }

      if (onDrawScreen != null) {
        Surface(
          onClick = onDrawScreen,
          shape = RoundedCornerShape(14.dp),
          color = Color.White.copy(alpha = 0.08f),
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth().testTag("orb_action_DRAW_ON_SCREEN"),
        ) {
          Text(
            text = "Draw & Annotate Screen Region",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun OrbitActionTile(
  action: OrbPopupAction,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (icon, tint) = orbActionVisuals(action)
  val isStop = action == OrbPopupAction.EMERGENCY_STOP

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = if (isStop) Color(0xFF991B1B).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
    border = BorderStroke(1.dp, tint.copy(alpha = if (isStop) 0.7f else 0.35f)),
    modifier =
      modifier
        .heightIn(min = 52.dp)
        .semantics { contentDescription = action.label }
        .testTag("orbit_popup_action_${action.name}"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = action.shortLabel,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}

fun orbActionVisuals(action: OrbPopupAction): Pair<ImageVector, Color> =
  when (action) {
    OrbPopupAction.VOICE_ASSISTANT -> Icons.Outlined.Mic to NeonCyan
    OrbPopupAction.OPEN_CHAT -> Icons.AutoMirrored.Outlined.Chat to ElectricBlue
    OrbPopupAction.QUICK_ASK -> Icons.Outlined.Bolt to WarmAmber
    OrbPopupAction.OPEN_CAMERA -> Icons.Outlined.CameraAlt to NeonGreen
    OrbPopupAction.ANALYZE_IMAGE -> Icons.Outlined.ImageSearch to NeonCyan
    OrbPopupAction.GENERATE_IMAGE -> Icons.Outlined.AutoAwesome to NeonMagenta
    OrbPopupAction.SCREEN_ASSISTANT -> Icons.AutoMirrored.Outlined.ScreenShare to ElectricBlue
    OrbPopupAction.DEEP_RESEARCH -> Icons.Outlined.Science to NeonGreen
    OrbPopupAction.DEVICE_ACTIONS -> Icons.Outlined.Apps to NeonPurple
    OrbPopupAction.MULTI_TASK_AGENT -> Icons.Outlined.Checklist to NeonViolet
    OrbPopupAction.ALARM_AND_REMINDER -> Icons.Outlined.Alarm to WarmAmber
    OrbPopupAction.EMERGENCY_STOP -> Icons.Rounded.StopCircle to Color(0xFFEF4444)
  }

@Composable
fun OrbitDismissScrim(
  visible: Boolean,
  onDismiss: () -> Unit,
  content: @Composable () -> Unit,
) {
  if (!visible) return
  val source = remember { MutableInteractionSource() }
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.38f))
          .clickable(
            interactionSource = source,
            indication = null,
            onClick = onDismiss,
          )
    )
    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
      content()
    }
  }
}
