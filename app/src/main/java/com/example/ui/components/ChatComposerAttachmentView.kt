package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.screen.AttachedVisualItem
import com.example.ui.model.AttachmentPreview
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import java.io.File

/**
 * Floating compact liquid-glass "Edit images" pill positioned directly above the top-left of the
 * expanded NeonChatComposer card whenever an image attachment is present (matching the reference video).
 */
@Composable
fun FloatingEditImagesPill(
  visible: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn() + scaleIn(initialScale = 0.9f),
    exit = fadeOut() + scaleOut(targetScale = 0.9f),
    modifier = modifier,
  ) {
    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(18.dp),
      color = LiquidGlassDark.copy(alpha = 0.92f),
      border =
        BorderStroke(
          width = 1.dp,
          brush = Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.55f), NeonPurple.copy(alpha = 0.45f))),
        ),
      shadowElevation = 6.dp,
      modifier = Modifier.testTag("composer_edit_images_pill"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = "Edit images",
          tint = Color.White,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "Edit images",
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}

/**
 * Embedded attachment thumbnail strip rendered INSIDE the expanded "Ask Mousomi" neon composer card,
 * showing rounded cropped image thumbnails with top-right circular (X) remove badges and optional
 * brief settling spinner, plus compact glass pills for documents/audio/video files.
 */
@Composable
fun ChatComposerAttachmentView(
  pendingAttachments: List<AttachmentPreview>,
  attachedVisualsByUri: Map<String, AttachedVisualItem>,
  onRemoveAttachment: (AttachmentPreview) -> Unit,
  onTapImageAttachment: (AttachmentPreview) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (pendingAttachments.isEmpty()) return
  val colors = LocalMousomiColors.current

  Column(
    modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .testTag("composer_attachment_strip"),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      pendingAttachments.forEachIndexed { idx, attachment ->
        when (attachment) {
          is AttachmentPreview.PickedPhotoUri -> {
            val visual = attachedVisualsByUri[attachment.uri]
            val imageModel: Any =
              visual?.croppedFilePath?.takeIf { File(it).exists() }?.let { File(it) }
                ?: attachment.uri
            ThumbnailAttachmentCard(
              imageModel = imageModel,
              isSettling = visual?.isSettling == true,
              index = idx,
              onTapThumbnail = { onTapImageAttachment(attachment) },
              onRemove = { onRemoveAttachment(attachment) },
            )
          }
          AttachmentPreview.SampleInlineArtwork -> {
            Box(
              modifier =
                Modifier
                  .size(66.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onTapImageAttachment(attachment) }
                  .testTag("composer_thumbnail_$idx"),
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_inline_preview_art_1795023054486),
                contentDescription = "Sample attached image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
              RemoveBadgeButton(
                index = idx,
                onClick = { onRemoveAttachment(attachment) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
              )
            }
          }
          is AttachmentPreview.MediaFile -> {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = colors.activeChipContainer,
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
              modifier = Modifier.testTag("composer_media_chip_$idx"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Description,
                  contentDescription = null,
                  tint = NeonCyan,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = attachment.item.displayName.take(20),
                  color = MaterialTheme.colorScheme.onBackground,
                  style = MaterialTheme.typography.labelLarge,
                  modifier = Modifier.widthIn(max = 140.dp),
                )
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Remove attachment",
                  tint = colors.mutedText,
                  modifier =
                    Modifier
                      .size(20.dp)
                      .clip(CircleShape)
                      .clickable { onRemoveAttachment(attachment) }
                      .testTag("attachment_remove_button_$idx"),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ThumbnailAttachmentCard(
  imageModel: Any,
  isSettling: Boolean,
  index: Int,
  onTapThumbnail: () -> Unit,
  onRemove: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = Color(0xFF151C2C),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
    modifier =
      Modifier
        .size(68.dp)
        .clickable(onClick = onTapThumbnail)
        .testTag("composer_thumbnail_$index"),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      AsyncImage(
        model = imageModel,
        contentDescription = "Attached cropped image ${index + 1}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )

      if (isSettling) {
        Box(
          modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.36f)),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            color = NeonCyan,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      RemoveBadgeButton(
        index = index,
        onClick = onRemove,
        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
      )
    }
  }
}

@Composable
private fun RemoveBadgeButton(
  index: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = Color(0xCC0A0F1A),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
    modifier =
      modifier
        .size(22.dp)
        .semantics { contentDescription = "Remove attachment" }
        .testTag("attachment_remove_button_$index"),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Rounded.Close,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(13.dp),
      )
    }
  }
}
