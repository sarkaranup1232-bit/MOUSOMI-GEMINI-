package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.ImageVersionRecord
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageResultCard(
  image: GeneratedImageResult,
  versions: List<ImageVersionRecord> = emptyList(),
  onSaveImage: (GeneratedImageResult) -> Unit = {},
  onShareImage: (GeneratedImageResult) -> Unit = {},
  onEditImage: (GeneratedImageResult) -> Unit = {},
  onSelectRegion: (GeneratedImageResult) -> Unit = {},
  onAskAboutImage: (GeneratedImageResult) -> Unit = {},
  onRegenerateImage: (GeneratedImageResult) -> Unit = {},
  onOpenSettings: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  var selectedVersionFilePath by remember(image.mediaId) { mutableStateOf(image.filePath) }
  var selectedVersionNumber by remember(image.mediaId) { mutableStateOf(image.versionNumber) }
  var saveConfirmed by remember(image.mediaId) { mutableStateOf(false) }

  val displayedPath = selectedVersionFilePath.ifBlank { image.filePath }
  val displayedResult =
    if (displayedPath == image.filePath) {
      image
    } else {
      val ver = versions.firstOrNull { it.filePath == displayedPath }
      if (ver != null) {
        image.copy(
          mediaId = ver.id,
          parentMediaId = ver.parentMediaId,
          versionNumber = ver.versionNumber,
          filePath = ver.filePath,
          prompt = ver.prompt,
        )
      } else image
    }

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = if (colors.isDark) LiquidGlassDark else MaterialTheme.colorScheme.surfaceVariant,
    border =
      BorderStroke(
        width = 1.dp,
        brush =
          Brush.linearGradient(
            listOf(NeonCyan.copy(alpha = 0.55f), NeonMagenta.copy(alpha = 0.45f))
          ),
      ),
    modifier = modifier.fillMaxWidth().testTag("image_result_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            Text(
              text = if (image.editInstruction != null) "Edited Image" else "Generated Image",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
          if (image.sourceBadge.isNotBlank()) {
            Text(
              text = image.sourceBadge,
              style = MaterialTheme.typography.labelSmall,
              color = NeonCyan,
              modifier = Modifier.padding(start = 26.dp).testTag("image_source_badge"),
            )
          }
        }
        Surface(
          shape = CircleShape,
          color = ElectricBlue.copy(alpha = 0.22f),
          border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
        ) {
          Text(
            text = "v$selectedVersionNumber • ${image.providerModel.take(20)}",
            color = NeonCyan,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      when (image.status) {
        ImageGenerationStatus.PREPARING_PROMPT,
        ImageGenerationStatus.CHECKING_FREE_PROVIDER,
        ImageGenerationStatus.UPLOADING_REQUEST,
        ImageGenerationStatus.GENERATING,
        ImageGenerationStatus.DOWNLOADING_IMAGE,
        ImageGenerationStatus.VALIDATING_RESULT -> {
          val shimmerTransition = rememberInfiniteTransition(label = "img_gen_shimmer")
          val offset by
            shimmerTransition.animateFloat(
              initialValue = -300f,
              targetValue = 900f,
              animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
              label = "shimmer_x",
            )
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                  Brush.linearGradient(
                    colors =
                      listOf(
                        Color(0xFF10182E),
                        NeonPurple.copy(alpha = 0.28f),
                        NeonCyan.copy(alpha = 0.25f),
                        Color(0xFF10182E),
                      ),
                    start = Offset(offset, 0f),
                    end = Offset(offset + 320f, 320f),
                  )
                ),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
              Text(
                text = image.status.label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }

        ImageGenerationStatus.FAILED,
        ImageGenerationStatus.QUOTA_EXHAUSTED,
        ImageGenerationStatus.CANCELLED -> {
          val isCancelled = image.status == ImageGenerationStatus.CANCELLED
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(if (isCancelled) 0xFF334155 else 0xFF7F1D1D).copy(alpha = 0.35f),
            border = BorderStroke(1.dp, Color(if (isCancelled) 0xFF94A3B8 else 0xFFEF4444).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5))
                Text(
                  text = image.errorMessage ?: image.status.label,
                  color = Color(0xFFFCA5A5),
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = { onRegenerateImage(image) },
                  colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                  modifier = Modifier.testTag("image_retry_button"),
                ) {
                  Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Retry", color = Color.White)
                }
                OutlinedButton(onClick = onOpenSettings) {
                  Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Settings")
                }
              }
            }
          }
        }

        ImageGenerationStatus.COMPLETED -> {
          if (!image.beforeFilePath.isNullOrBlank() && displayedPath.isNotBlank()) {
            BeforeAfterImageSlider(
              beforePath = image.beforeFilePath,
              afterPath = displayedPath,
              modifier = Modifier.fillMaxWidth(),
            )
          } else if (displayedPath.isNotBlank()) {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(200.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .background(Color(0xFF0A0F1E)),
            ) {
              AsyncImage(
                model = File(displayedPath),
                contentDescription = "Generated image v$selectedVersionNumber: ${image.prompt}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag("generated_image_preview"),
              )
              if (image.width > 0 && image.height > 0) {
                Surface(
                  shape = CircleShape,
                  color = Color.Black.copy(alpha = 0.6f),
                  modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) {
                  Text(
                    text = "${image.width}x${image.height}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }
            }
          }

          if (versions.size > 1) {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              versions.forEach { ver ->
                FilterChip(
                  selected = ver.filePath == displayedPath,
                  onClick = {
                    selectedVersionFilePath = ver.filePath
                    selectedVersionNumber = ver.versionNumber
                  },
                  label = { Text("v${ver.versionNumber}") },
                  modifier = Modifier.testTag("image_version_chip_${ver.versionNumber}"),
                )
              }
            }
          }

          Text(
            text = image.prompt,
            color = colors.mutedText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ImageActionPill(
              icon = Icons.Outlined.Edit,
              label = "Edit Image",
              accent = NeonCyan,
              onClick = { onEditImage(displayedResult) },
              testTag = "image_action_edit",
            )
            ImageActionPill(
              icon = Icons.Outlined.Brush,
              label = "Select Region",
              accent = NeonPurple,
              onClick = { onSelectRegion(displayedResult) },
              testTag = "image_action_select_region",
            )
            ImageActionPill(
              icon = Icons.Outlined.ImageSearch,
              label = "Ask About Image",
              accent = ElectricBlue,
              onClick = { onAskAboutImage(displayedResult) },
              testTag = "image_action_ask",
            )
            ImageActionPill(
              icon = Icons.Outlined.Download,
              label = if (saveConfirmed) "Saved" else "Save",
              accent = NeonGreen,
              onClick = {
                saveConfirmed = true
                onSaveImage(displayedResult)
              },
              testTag = "image_action_save",
            )
            ImageActionPill(
              icon = Icons.Outlined.Share,
              label = "Share",
              accent = Color.White,
              onClick = { onShareImage(displayedResult) },
              testTag = "image_action_share",
            )
            ImageActionPill(
              icon = Icons.Outlined.Refresh,
              label = "Regenerate",
              accent = Color.White,
              onClick = { onRegenerateImage(displayedResult) },
              testTag = "image_action_regenerate",
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ImageActionPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  accent: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = LocalMousomiColors.current.activeChipContainer,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    modifier = Modifier.testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
      Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
  }
}
