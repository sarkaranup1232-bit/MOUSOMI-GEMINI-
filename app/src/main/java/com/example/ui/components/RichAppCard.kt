package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Shop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.SubcomposeAsyncImage
import com.example.domain.model.RichLinkKind
import com.example.domain.model.RichLinkMetadata
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta

@Composable
fun RichAppCardsGroup(
  cards: List<RichLinkMetadata>,
  modifier: Modifier = Modifier,
) {
  if (cards.isEmpty()) return
  Column(
    modifier = modifier.fillMaxWidth().testTag("rich_app_cards_group"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    cards.forEach { card ->
      RichAppCard(card = card)
    }
  }
}

@Composable
fun RichAppCard(
  card: RichLinkMetadata,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val colors = LocalMousomiColors.current
  var confirmOpen by remember { mutableStateOf(false) }

  val installedIconBitmap =
    remember(card.packageName) {
      card.packageName?.let { pkg ->
        runCatching {
          context.packageManager.getApplicationIcon(pkg).toBitmap(width = 96, height = 96).asImageBitmap()
        }.getOrNull()
      }
    }

  val accentColor =
    when (card.kind) {
      RichLinkKind.PLAY_STORE -> NeonGreen
      RichLinkKind.YOUTUBE -> Color(0xFFFF3B30)
      RichLinkKind.INSTAGRAM -> NeonMagenta
      RichLinkKind.WHATSAPP -> Color(0xFF25D366)
      RichLinkKind.ANDROID_APP -> NeonCyan
      RichLinkKind.WEBSITE -> ElectricBlue
    }

  val fallbackIcon =
    when (card.kind) {
      RichLinkKind.PLAY_STORE -> Icons.Outlined.Shop
      RichLinkKind.YOUTUBE -> Icons.Outlined.PlayCircleOutline
      RichLinkKind.INSTAGRAM -> Icons.Outlined.Android
      RichLinkKind.WHATSAPP -> Icons.Outlined.Forum
      RichLinkKind.ANDROID_APP -> Icons.Outlined.Android
      RichLinkKind.WEBSITE -> Icons.Outlined.Language
    }

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = if (colors.isDark) LiquidGlassDark else MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
    modifier = modifier.fillMaxWidth().testTag("rich_app_card_${card.kind.name}"),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        if (installedIconBitmap != null) {
          Image(
            bitmap = installedIconBitmap,
            contentDescription = card.title,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
          )
        } else if (!card.domain.isNullOrBlank()) {
          SubcomposeAsyncImage(
            model = "https://www.google.com/s2/favicons?sz=64&domain=${card.domain}",
            contentDescription = card.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)),
            error = {
              Icon(
                imageVector = fallbackIcon,
                contentDescription = card.title,
                tint = accentColor,
                modifier = Modifier.size(24.dp),
              )
            },
            loading = {
              Icon(
                imageVector = fallbackIcon,
                contentDescription = card.title,
                tint = accentColor,
                modifier = Modifier.size(24.dp),
              )
            },
          )
        } else {
          Icon(
            imageVector = fallbackIcon,
            contentDescription = card.title,
            tint = accentColor,
            modifier = Modifier.size(24.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = card.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = card.subtitle,
          color = colors.mutedText,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = card.kind.label,
          color = accentColor,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Medium,
        )
      }

      Surface(
        onClick = { confirmOpen = true },
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
        modifier = Modifier.testTag("rich_card_open_${card.kind.name}"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = card.actionLabel,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }
  }

  if (confirmOpen) {
    AlertDialog(
      onDismissRequest = { confirmOpen = false },
      title = { Text("Open ${card.title}?") },
      text = {
        Text("Mousomi AI will open:\n${card.url}")
      },
      confirmButton = {
        Button(
          onClick = {
            confirmOpen = false
            runCatching {
              val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(card.url)).apply {
                  if (!card.packageName.isNullOrBlank() && card.isInstalledApp) {
                    setPackage(card.packageName)
                  }
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
              context.startActivity(intent)
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
        ) {
          Text("Open", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmOpen = false }) { Text("Cancel") }
      },
    )
  }
}
