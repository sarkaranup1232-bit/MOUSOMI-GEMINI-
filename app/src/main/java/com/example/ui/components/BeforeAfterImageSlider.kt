package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.NeonCyan
import java.io.File
import kotlin.math.roundToInt

/**
 * Interactive draggable split Before / After image comparator for Image Studio & Chat results.
 */
@Composable
fun BeforeAfterImageSlider(
  beforePath: String,
  afterPath: String,
  contentDescription: String = "Before and After image comparison",
  modifier: Modifier = Modifier,
) {
  var splitFraction by remember(beforePath, afterPath) { mutableFloatStateOf(0.5f) }
  val density = LocalDensity.current

  BoxWithConstraints(
    modifier =
      modifier
        .height(220.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFF0A0F1E))
        .testTag("before_after_slider"),
  ) {
    val totalWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
    val splitX = (splitFraction * totalWidthPx).roundToInt()

    // Base (After image full width)
    AsyncImage(
      model = if (afterPath.startsWith("content://") || afterPath.startsWith("file://")) afterPath else File(afterPath),
      contentDescription = "$contentDescription (After)",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    // Left clipped overlay (Before image)
    Box(
      modifier =
        Modifier
          .width(with(density) { splitX.toDp() })
          .fillMaxHeight()
          .clipToBounds(),
    ) {
      AsyncImage(
        model = if (beforePath.startsWith("content://") || beforePath.startsWith("file://")) beforePath else File(beforePath),
        contentDescription = "$contentDescription (Before)",
        contentScale = ContentScale.Crop,
        modifier = Modifier.width(this@BoxWithConstraints.maxWidth).fillMaxHeight(),
      )
    }

    // Labels
    Surface(
      shape = CircleShape,
      color = Color.Black.copy(alpha = 0.65f),
      modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
    ) {
      Text(
        text = "Before",
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      )
    }

    Surface(
      shape = CircleShape,
      color = Color.Black.copy(alpha = 0.65f),
      modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
    ) {
      Text(
        text = "After",
        color = NeonCyan,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      )
    }

    // Draggable divider line + knob
    Box(
      modifier =
        Modifier
          .offset { IntOffset((splitX - 18).coerceIn(0, (totalWidthPx - 36).roundToInt()), 0) }
          .width(36.dp)
          .fillMaxHeight()
          .pointerInput(totalWidthPx) {
            detectHorizontalDragGestures { change, dragAmount ->
              change.consume()
              splitFraction = (splitFraction + dragAmount / totalWidthPx).coerceIn(0.06f, 0.94f)
            }
          },
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier
            .width(2.dp)
            .fillMaxHeight()
            .background(NeonCyan.copy(alpha = 0.9f)),
      )
      Surface(
        shape = CircleShape,
        color = Color(0xFF0E172A),
        border = BorderStroke(1.5.dp, NeonCyan),
        modifier = Modifier.size(30.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.CompareArrows,
            contentDescription = "Drag to compare before and after",
            tint = NeonCyan,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}
