package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.LiveSourceLoadStatus
import com.example.domain.model.ResearchActivitySnapshot
import com.example.domain.model.ResearchStageStatus
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.WarmAmber

/**
 * 7-Stage Collapsible Live Deep Research Activity Panel + Live Source Favicon Strip.
 * Displays real stage progression (Understand -> Structured Final Report), live site reading
 * telemetry (Connecting -> Opened -> Read / Failed), and a Stop button that preserves
 * fetched partial sources without exposing raw <think> internal reasoning.
 */
@Composable
fun ResearchActivityPanel(
  snapshot: ResearchActivitySnapshot,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onStopResearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val uriHandler = LocalUriHandler.current

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = if (colors.isDark) LiquidGlassDark else MaterialTheme.colorScheme.surfaceVariant,
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (snapshot.isActive) NeonCyan.copy(alpha = 0.55f)
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
      ),
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .animateContentSize()
        .testTag("research_activity_panel"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier =
            Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .clickable(onClick = onToggleExpanded)
              .padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (snapshot.isActive) {
            CircularProgressIndicator(
              color = NeonCyan,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Icon(
              imageVector = if (snapshot.isFailed) Icons.Outlined.Close else Icons.Outlined.Science,
              contentDescription = null,
              tint =
                when {
                  snapshot.isFailed -> Color(0xFFEF4444)
                  snapshot.isCancelled -> WarmAmber
                  else -> NeonGreen
                },
              modifier = Modifier.size(18.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = snapshot.currentHeadline,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color =
                if (snapshot.isFailed) Color(0xFFEF4444)
                else MaterialTheme.colorScheme.onBackground,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("research_headline_pill"),
            )
            val totalStages = snapshot.stages.size.coerceAtLeast(1)
            val returnedLabel =
              if (snapshot.sourcesReturnedCount > 0) "${snapshot.sourcesReturnedCount} returned"
              else "${snapshot.liveSources.size} live"
            val usedLabel =
              if (snapshot.sourcesUsedCount > 0) " • ${snapshot.sourcesUsedCount} used" else ""
            Text(
              text = "${snapshot.stages.count { it.status == ResearchStageStatus.COMPLETED }}/$totalStages stages • $returnedLabel$usedLabel",
              style = MaterialTheme.typography.labelSmall,
              color = colors.mutedText,
            )
            if (snapshot.searchProvider.isNotBlank() || snapshot.answerModel.isNotBlank()) {
              Text(
                text =
                  buildString {
                    if (snapshot.searchProvider.isNotBlank()) append("Search: ${snapshot.searchProvider}")
                    if (snapshot.searchProvider.isNotBlank() && snapshot.answerModel.isNotBlank()) append(" • ")
                    if (snapshot.answerModel.isNotBlank()) append("Model: ${snapshot.answerModel}")
                  },
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Collapse research panel" else "Expand research panel",
            tint = colors.mutedText,
          )
        }

        if (snapshot.isActive) {
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            onClick = onStopResearch,
            shape = CircleShape,
            color = Color(0xFFDC2626),
            modifier = Modifier.testTag("stop_research_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = "Stop research",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Stop",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }

      // Live Source Favicon Strip (always visible when sources are connecting/read)
      if (snapshot.liveSources.isNotEmpty()) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .testTag("live_source_favicon_strip"),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          snapshot.liveSources.forEach { src ->
            val statusColor =
              when (src.loadStatus) {
                LiveSourceLoadStatus.CONNECTING -> WarmAmber
                LiveSourceLoadStatus.OPENED -> ElectricBlue
                LiveSourceLoadStatus.READ -> NeonGreen
                LiveSourceLoadStatus.FAILED -> Color(0xFFEF4444)
              }
            val statusSymbol =
              when (src.loadStatus) {
                LiveSourceLoadStatus.CONNECTING -> "…"
                LiveSourceLoadStatus.OPENED -> "Open"
                LiveSourceLoadStatus.READ -> "Read ✓"
                LiveSourceLoadStatus.FAILED -> "Failed ✕"
              }
            Surface(
              onClick = { runCatching { uriHandler.openUri(src.url) } },
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
              border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
              modifier = Modifier.widthIn(min = 128.dp, max = 185.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (!src.faviconUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = src.faviconUrl,
                    contentDescription = "${src.domain} favicon",
                    contentScale = ContentScale.Crop,
                    modifier =
                      Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                  )
                } else {
                  Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(15.dp),
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = src.domain,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = statusSymbol,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                  )
                }
              }
            }
          }
        }
      }

      if (snapshot.isFailed && !snapshot.errorMessage.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF7F1D1D).copy(alpha = 0.28f),
          border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth().testTag("research_error_banner"),
        ) {
          Text(
            text = snapshot.errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFCA5A5),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          )
        }
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
          if (snapshot.plannedQueries.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              snapshot.plannedQueries.forEach { q ->
                Surface(
                  shape = CircleShape,
                  color = NeonPurple.copy(alpha = 0.14f),
                  border = BorderStroke(0.8.dp, NeonPurple.copy(alpha = 0.38f)),
                ) {
                  Text(
                    text = q,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
          snapshot.stages.forEach { stage ->
            val tint =
              when (stage.status) {
                ResearchStageStatus.COMPLETED -> NeonGreen
                ResearchStageStatus.ACTIVE -> NeonCyan
                ResearchStageStatus.FAILED -> Color(0xFFEF4444)
                ResearchStageStatus.CANCELLED -> WarmAmber
                ResearchStageStatus.SKIPPED, ResearchStageStatus.WAITING -> colors.mutedText
              }
            Row(
              modifier = Modifier.fillMaxWidth().testTag("research_stage_${stage.stage.order}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                when (stage.status) {
                  ResearchStageStatus.ACTIVE ->
                    CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                  ResearchStageStatus.COMPLETED ->
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                  ResearchStageStatus.FAILED ->
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                  else ->
                    Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                }
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${stage.stage.order}. ${stage.stage.label}",
                  style = MaterialTheme.typography.labelMedium,
                  color =
                    if (stage.status == ResearchStageStatus.WAITING) colors.mutedText
                    else MaterialTheme.colorScheme.onBackground,
                  fontWeight =
                    if (stage.status == ResearchStageStatus.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (stage.detail.isNotBlank()) {
                  Text(
                    text = stage.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              Text(
                text = stage.status.label,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }
      }
    }
  }
}
