package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.domain.model.AgentTaskLifecycleState
import com.example.domain.model.DeviceActionResult
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.GeneratedMusicResult
import com.example.domain.model.GeneratedVideoResult
import com.example.domain.model.ImageVersionRecord
import com.example.domain.model.ManagedTaskItem
import com.example.domain.model.ReminderEntry
import com.example.domain.model.ResponseBlock
import com.example.domain.model.SearchResultItem
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarmAmber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResponseBlockRenderer(
  block: ResponseBlock,
  modifier: Modifier = Modifier,
  imageVersionsMap: Map<String, List<ImageVersionRecord>> = emptyMap(),
  onSaveImage: (GeneratedImageResult) -> Unit = {},
  onShareImage: (GeneratedImageResult) -> Unit = {},
  onEditImage: (GeneratedImageResult) -> Unit = {},
  onSelectRegion: (GeneratedImageResult) -> Unit = {},
  onAskAboutImage: (GeneratedImageResult) -> Unit = {},
  onRegenerateImage: (GeneratedImageResult) -> Unit = {},
  onOpenSettings: () -> Unit = {},
  onApproveOrResumeTask: (ManagedTaskItem) -> Unit = {},
  onPauseTask: (ManagedTaskItem) -> Unit = {},
  onRetryTask: (ManagedTaskItem) -> Unit = {},
  onCancelTask: (ManagedTaskItem) -> Unit = {},
  onOpenAssistantHub: () -> Unit = {},
  onSaveVideo: (GeneratedVideoResult) -> Unit = {},
  onShareVideo: (GeneratedVideoResult) -> Unit = {},
  onSaveMusic: (GeneratedMusicResult) -> Unit = {},
  onShareMusic: (GeneratedMusicResult) -> Unit = {},
) {
  when (block) {
    is ResponseBlock.MarkdownText ->
      MarkdownMessageContent(
        markdown = block.content,
        textColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
      )
    is ResponseBlock.ChartBlock ->
      InteractiveChartCard(chart = block.chart, modifier = modifier)
    is ResponseBlock.VideoAnalysisBlock ->
      VideoAnalysisBlockCard(block = block, modifier = modifier)
    is ResponseBlock.AudioTranscriptBlock ->
      AudioTranscriptBlockCard(block = block, modifier = modifier)
    is ResponseBlock.DocumentSummaryBlock ->
      DocumentSummaryBlockCard(block = block, modifier = modifier)
    is ResponseBlock.DataAnalyticsBlock ->
      DataAnalyticsBlockCard(block = block, modifier = modifier)
    is ResponseBlock.ResearchReportBlock ->
      ResearchReportBlockCard(block = block, modifier = modifier)
    is ResponseBlock.SourceCitations ->
      SourceCitationsBlockCard(sources = block.sources, modifier = modifier)
    is ResponseBlock.GeneratedImageBlock -> {
      val versions = imageVersionsMap[block.image.rootMediaId].orEmpty()
      ImageResultCard(
        image = block.image,
        versions = versions,
        onSaveImage = onSaveImage,
        onShareImage = onShareImage,
        onEditImage = onEditImage,
        onSelectRegion = onSelectRegion,
        onAskAboutImage = onAskAboutImage,
        onRegenerateImage = onRegenerateImage,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
      )
    }
    is ResponseBlock.GeneratedVideoBlock ->
      GeneratedVideoBlockCard(
        video = block.video,
        onSaveVideo = { onSaveVideo(block.video) },
        onShareVideo = { onShareVideo(block.video) },
        modifier = modifier,
      )
    is ResponseBlock.GeneratedMusicBlock ->
      GeneratedMusicBlockCard(
        music = block.music,
        onSaveMusic = { onSaveMusic(block.music) },
        onShareMusic = { onShareMusic(block.music) },
        modifier = modifier,
      )
    is ResponseBlock.RichLinkCardBlock ->
      RichAppCardsGroup(cards = block.cards, modifier = modifier)
    is ResponseBlock.TaskPlanBlock ->
      TaskProgressCard(goal = block.goal, steps = block.steps, modifier = modifier)
    is ResponseBlock.DeviceActionsBlock ->
      DeviceActionsBlockCard(
        actions = block.actions,
        onOpenAssistantHub = onOpenAssistantHub,
        modifier = modifier,
      )
    is ResponseBlock.ReminderResultBlock ->
      ReminderResultBlockCard(
        summary = block.summary,
        reminders = block.reminders,
        onOpenAssistantHub = onOpenAssistantHub,
        modifier = modifier,
      )
    is ResponseBlock.MultiTaskBatchBlock ->
      MultiTaskBatchBlockCard(
        report = block.report,
        onApproveOrResumeTask = onApproveOrResumeTask,
        onPauseTask = onPauseTask,
        onRetryTask = onRetryTask,
        onCancelTask = onCancelTask,
        onOpenAssistantHub = onOpenAssistantHub,
        modifier = modifier,
      )
    is ResponseBlock.ProgressNote ->
      Text(
        text = block.label,
        color = LocalMousomiColors.current.mutedText,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
      )
  }
}

@Composable
private fun DeviceActionsBlockCard(
  actions: List<DeviceActionResult>,
  onOpenAssistantHub: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.58f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("device_actions_block_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Outlined.Apps, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
          Text("Device Assistant", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
          text = "Open Hub",
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.clickable(onClick = onOpenAssistantHub).padding(4.dp),
        )
      }
      actions.forEach { res ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = if (res.success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = res.statusLabel,
            tint = if (res.success) NeonGreen else WarmAmber,
            modifier = Modifier.size(16.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${res.targetLabel} • ${res.statusLabel}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = res.verifiedDetail,
              color = colors.mutedText,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ReminderResultBlockCard(
  summary: String,
  reminders: List<ReminderEntry>,
  onOpenAssistantHub: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.58f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("reminder_result_block_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Outlined.Alarm, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(18.dp))
          Text("Alarms & Reminders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
          text = "Manage",
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.clickable(onClick = onOpenAssistantHub).padding(4.dp),
        )
      }
      Text(summary, style = MaterialTheme.typography.bodySmall)
      reminders.take(4).forEach { entry ->
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${entry.title} (${entry.type.label})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
              Text(
                text = "${formatter.format(Date(entry.scheduledAtMs))} • ${entry.recurrence.label}",
                color = colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
              )
            }
            Text(
              text = entry.status,
              color = if (entry.status == "Missed" || entry.status == "Failed") Color(0xFFFCA5A5) else NeonGreen,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MultiTaskBatchBlockCard(
  report: com.example.domain.model.MultiTaskBatchReport,
  onApproveOrResumeTask: (ManagedTaskItem) -> Unit,
  onPauseTask: (ManagedTaskItem) -> Unit,
  onRetryTask: (ManagedTaskItem) -> Unit,
  onCancelTask: (ManagedTaskItem) -> Unit,
  onOpenAssistantHub: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.58f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = modifier.fillMaxWidth().testTag("multi_task_batch_block_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Outlined.Checklist, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
          Text("Multi-Task Plan (${report.tasks.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
          text = "Task Center",
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.clickable(onClick = onOpenAssistantHub).padding(4.dp),
        )
      }

      report.tasks.forEach { task ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${task.orderIndex + 1}. ${task.description}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
              )
              Text(
                text = task.state.label,
                color =
                  when (task.state) {
                    AgentTaskLifecycleState.COMPLETED -> NeonGreen
                    AgentTaskLifecycleState.FAILED -> Color(0xFFFCA5A5)
                    AgentTaskLifecycleState.WAITING_FOR_APPROVAL -> WarmAmber
                    else -> NeonCyan
                  },
                style = MaterialTheme.typography.labelSmall,
              )
            }
            val msg = task.errorDetails.ifBlank { task.resultMessage }
            if (msg.isNotBlank()) {
              Text(msg, color = colors.mutedText, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              if (task.state == AgentTaskLifecycleState.WAITING_FOR_APPROVAL || task.state == AgentTaskLifecycleState.PAUSED) {
                OutlinedButton(onClick = { onApproveOrResumeTask(task) }) {
                  Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                  Text(if (task.state == AgentTaskLifecycleState.WAITING_FOR_APPROVAL) "Approve" else "Resume", style = MaterialTheme.typography.labelSmall)
                }
              }
              if (task.state == AgentTaskLifecycleState.RUNNING || task.state == AgentTaskLifecycleState.PENDING) {
                OutlinedButton(onClick = { onPauseTask(task) }) {
                  Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(15.dp))
                  Text("Pause", style = MaterialTheme.typography.labelSmall)
                }
              }
              if (task.state == AgentTaskLifecycleState.FAILED) {
                OutlinedButton(onClick = { onRetryTask(task) }) {
                  Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                  Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
              }
              if (task.state != AgentTaskLifecycleState.COMPLETED && task.state != AgentTaskLifecycleState.CANCELLED) {
                OutlinedButton(onClick = { onCancelTask(task) }) {
                  Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                  Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun VideoAnalysisBlockCard(
  block: ResponseBlock.VideoAnalysisBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val res = block.analysis
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("video_analysis_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Video Analytics • ${res.topic}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      Text(
        text = "${res.metadata.displayName} • ${res.metadata.width}x${res.metadata.height} • Sampled ${res.sampledFrameCount} frames",
        color = colors.mutedText,
        style = MaterialTheme.typography.labelSmall,
      )
      if (res.timeline.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Timeline Breakdown", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        res.timeline.forEach { seg ->
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "${seg.startTimeFormatted} – ${seg.endTimeFormatted} • ${seg.sceneDescription}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = seg.visualEvents,
              color = colors.mutedText,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
      if (res.keyObservations.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Key Observations", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        res.keyObservations.forEach { obs ->
          Text("• $obs", style = MaterialTheme.typography.bodySmall)
        }
      }
      Text(res.analysisMethodNote, color = colors.mutedText, style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun AudioTranscriptBlockCard(
  block: ResponseBlock.AudioTranscriptBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val res = block.analysis
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("audio_transcript_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Audio Intelligence • ${res.detectedLanguage}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      Text("${res.metadata.displayName} • ${res.transcriptionEngine}", color = colors.mutedText, style = MaterialTheme.typography.labelSmall)
      if (res.keyPoints.isNotEmpty()) {
        Text("Key Points", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        res.keyPoints.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
      }
      if (res.segments.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Timestamped Transcript", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        res.segments.take(12).forEach { seg ->
          Text(
            text = "[${seg.startTimeFormatted}–${seg.endTimeFormatted}] ${seg.text}",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      } else if (res.fullTranscript.isNotBlank()) {
        Text(res.fullTranscript.take(900), style = MaterialTheme.typography.bodySmall)
      }
      res.translatedText?.let { tr ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Translation: $tr", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun DocumentSummaryBlockCard(
  block: ResponseBlock.DocumentSummaryBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.5f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("document_summary_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("${block.documentName} (${block.formatLabel})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      Text("${block.pageCount} page/sheet(s) extracted • Document-derived facts", color = colors.mutedText, style = MaterialTheme.typography.labelSmall)
      if (block.warnings.isNotEmpty()) {
        block.warnings.forEach { w -> Text("Note: $w", color = colors.mutedText, style = MaterialTheme.typography.labelSmall) }
      }
      if (block.tables.isNotEmpty()) {
        Text("Detected ${block.tables.size} table(s)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
      }
    }
  }
}

@Composable
private fun DataAnalyticsBlockCard(
  block: ResponseBlock.DataAnalyticsBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val a = block.analytics
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("data_analytics_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Data Analytics • ${a.datasetName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      Text(
        text = "Rows: ${a.cleaningSummary.originalRowCount} -> ${a.cleaningSummary.cleanedRowCount} cleaned (${a.cleaningSummary.duplicateRowsRemoved} duplicates removed)",
        color = colors.mutedText,
        style = MaterialTheme.typography.labelSmall,
      )
      a.numericStatistics.forEach { st ->
        Text(
          text = "${st.columnName}: mean=${"%.2f".format(st.mean)}, median=${"%.2f".format(st.median)}, min=${"%.2f".format(st.min)}, max=${"%.2f".format(st.max)}, σ=${"%.2f".format(st.stdDev)}",
          style = MaterialTheme.typography.bodySmall,
        )
      }
      a.detectedTrends.forEach { tr -> Text("• $tr", style = MaterialTheme.typography.bodySmall) }
      a.correlationNotes.forEach { cor -> Text("• $cor", style = MaterialTheme.typography.bodySmall) }
    }
  }
}

@Composable
private fun ResearchReportBlockCard(
  block: ResponseBlock.ResearchReportBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val r = block.report
  var expanded by remember { mutableStateOf(false) }
  @Suppress("DEPRECATION")
  val clipboard = LocalClipboardManager.current

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = modifier.fillMaxWidth().testTag("research_report_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
          Icon(Icons.Outlined.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
          Text("Deep Research Report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        IconButton(
          onClick = { clipboard.setText(AnnotatedString(r.toMarkdownExport())) },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(Icons.Outlined.ContentCopy, contentDescription = "Export Markdown Report", modifier = Modifier.size(16.dp))
        }
      }

      Text("${r.complexity} • ${r.references.size} verified sources", color = colors.mutedText, style = MaterialTheme.typography.labelSmall)
      Text("Key Findings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
      r.keyFindings.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }

      if (expanded) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Research Objectives", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        r.researchObjectives.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        Text("Methodology", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(r.methodology, style = MaterialTheme.typography.bodySmall)
        Text("Detailed Analysis", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(r.detailedAnalysis, style = MaterialTheme.typography.bodySmall)
        Text("Source Comparison", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        r.sourceComparison.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        Text("Conflicting Information", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        if (r.conflictingInformation.isEmpty()) {
          Text("No direct factual conflicts identified.", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
        } else {
          r.conflictingInformation.forEach { c ->
            Text("• ${c.topic}: ${c.sourceA} vs ${c.sourceB}", style = MaterialTheme.typography.bodySmall)
          }
        }
        Text("Limitations", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        r.limitations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        Text("Conclusion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(r.conclusion, style = MaterialTheme.typography.bodySmall)
      }

      Text(
        text = if (expanded) "Collapse full 10-section report" else "View full 10-section report",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
      )
    }
  }
}

@Composable
private fun SourceCitationsBlockCard(
  sources: List<SearchResultItem>,
  modifier: Modifier = Modifier,
) {
  val uriHandler = LocalUriHandler.current
  val colors = LocalMousomiColors.current

  if (sources.isEmpty()) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = colors.activeChipContainer.copy(alpha = 0.55f),
      border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.4f)),
      modifier = modifier.fillMaxWidth().testTag("source_citations_empty_state"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Public,
          contentDescription = null,
          tint = WarmAmber,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "No verified web sources were returned for this search.",
          color = colors.mutedText,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    return
  }

  Column(
    modifier = modifier.fillMaxWidth().testTag("source_citations_list"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val usedCount = sources.count { it.usedInAnswer }
    Text(
      text =
        if (usedCount > 0) "Verified Sources (${sources.size} returned • $usedCount cited)"
        else "Verified Sources (${sources.size})",
      color = colors.mutedText,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
    )
    sources.forEachIndexed { idx, src ->
      val sourceNum = if (src.sourceNumber > 0) src.sourceNumber else idx + 1
      val isMapLink =
        src.url.contains("maps.google", ignoreCase = true) ||
          src.url.contains("google.com/maps", ignoreCase = true)
      val publisherLabel = src.publisher.ifBlank { src.sourceDomain }
      val initialChar = publisherLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
      val faviconModel =
        src.faviconUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }
          ?: "https://www.google.com/s2/favicons?domain=${src.sourceDomain}&sz=64"
      val canOpen = src.url.startsWith("https://", ignoreCase = true)

      Surface(
        onClick = { if (canOpen) runCatching { uriHandler.openUri(src.url) } },
        shape = RoundedCornerShape(14.dp),
        color = colors.activeChipContainer.copy(alpha = 0.72f),
        border =
          BorderStroke(
            width = 1.dp,
            color =
              if (src.usedInAnswer) NeonCyan.copy(alpha = 0.55f)
              else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
          ),
        modifier = Modifier.fillMaxWidth().testTag("source_citation_card_$idx"),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(
                    if (isMapLink) NeonGreen.copy(alpha = 0.16f)
                    else NeonCyan.copy(alpha = 0.14f)
                  ),
            ) {
              if (isMapLink) {
                Icon(
                  imageVector = Icons.Outlined.Place,
                  contentDescription = "Google Maps location",
                  tint = NeonGreen,
                  modifier = Modifier.size(18.dp),
                )
              } else {
                Text(
                  text = initialChar,
                  color = NeonCyan,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
                AsyncImage(
                  model = faviconModel,
                  contentDescription = "$publisherLabel logo",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(22.dp).clip(CircleShape),
                )
              }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = NeonCyan.copy(alpha = 0.16f),
                ) {
                  Text(
                    text = "#$sourceNum",
                    color = NeonCyan,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                  )
                }
                Text(
                  text = publisherLabel,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false),
                )
                if (src.usedInAnswer) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NeonGreen.copy(alpha = 0.16f),
                  ) {
                    Text(
                      text = "Used in answer",
                      color = NeonGreen,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                  }
                }
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = if (isMapLink) Icons.Outlined.Place else Icons.Outlined.Public,
                  contentDescription = null,
                  tint = NeonCyan,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text =
                    buildString {
                      append(src.sourceDomain)
                      append(" • ")
                      append(src.publishedTimestamp?.takeIf { it.isNotBlank() } ?: "Date unverified")
                      append(" • ")
                      append(src.verificationStatus.label)
                    },
                  color = colors.mutedText,
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          Text(
            text = src.title.ifBlank { src.sourceDomain },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          if (src.snippet.isNotBlank()) {
            Text(
              text = src.snippet.take(180),
              color = colors.mutedText.copy(alpha = 0.92f),
              style = MaterialTheme.typography.bodySmall,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (canOpen) "Open Original Source" else "Unverified Source URL",
              color = if (canOpen) MaterialTheme.colorScheme.primary else colors.mutedText,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Icon(
              imageVector = Icons.Outlined.OpenInNew,
              contentDescription = "Open source ${src.url}",
              modifier = Modifier.size(15.dp),
              tint = if (canOpen) MaterialTheme.colorScheme.primary else colors.mutedText,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GeneratedVideoBlockCard(
  video: GeneratedVideoResult,
  onSaveVideo: () -> Unit,
  onShareVideo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val fileExists = remember(video.filePath) { video.filePath.isNotBlank() && File(video.filePath).exists() }
  var isPlaying by remember(video.mediaId) { mutableStateOf(false) }
  var videoViewRef by remember(video.mediaId) { mutableStateOf<VideoView?>(null) }

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = colors.activeChipContainer.copy(alpha = 0.65f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = modifier.fillMaxWidth().testTag("generated_video_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
          Text(
            text = if (video.isImageToVideo) "Image-to-Video Animation" else "Generated Video",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Text(
          text = "${video.durationSeconds}s • ${video.providerModel}",
          color = colors.mutedText,
          style = MaterialTheme.typography.labelSmall,
        )
      }

      if (fileExists) {
        AndroidView(
          factory = { ctx ->
            VideoView(ctx).apply {
              setVideoURI(Uri.fromFile(File(video.filePath)))
              setOnPreparedListener { mp -> mp.isLooping = true }
              setOnCompletionListener { isPlaying = false }
              videoViewRef = this
            }
          },
          modifier =
            Modifier
              .fillMaxWidth()
              .height(210.dp)
              .clip(RoundedCornerShape(14.dp)),
        )
      } else {
        Text(
          text = "Video file unavailable at ${video.filePath}",
          color = colors.mutedText,
          style = MaterialTheme.typography.bodySmall,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = {
            val view = videoViewRef
            if (view != null) {
              if (view.isPlaying) {
                view.pause()
                isPlaying = false
              } else {
                view.start()
                isPlaying = true
              }
            }
          },
          enabled = fileExists,
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = if (isPlaying) "Pause video" else "Play video",
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(if (isPlaying) "Pause" else "Play")
        }
        OutlinedButton(onClick = onSaveVideo, enabled = fileExists) {
          Icon(Icons.Outlined.Download, contentDescription = "Save video", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("Save")
        }
        OutlinedButton(onClick = onShareVideo, enabled = fileExists) {
          Icon(Icons.Outlined.Share, contentDescription = "Share video", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("Share")
        }
      }
    }
  }
}

@Composable
private fun GeneratedMusicBlockCard(
  music: GeneratedMusicResult,
  onSaveMusic: () -> Unit,
  onShareMusic: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val context = LocalContext.current
  val fileExists = remember(music.filePath) { music.filePath.isNotBlank() && File(music.filePath).exists() }
  var isPlaying by remember(music.mediaId) { mutableStateOf(false) }
  var player by remember(music.mediaId) { mutableStateOf<MediaPlayer?>(null) }

  DisposableEffect(music.mediaId) {
    onDispose {
      runCatching {
        player?.stop()
        player?.release()
      }
      player = null
    }
  }

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = colors.activeChipContainer.copy(alpha = 0.65f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = modifier.fillMaxWidth().testTag("generated_music_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(18.dp))
          Text(
            text = "AI Music Track",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Text(
          text = music.providerModel,
          color = colors.mutedText,
          style = MaterialTheme.typography.labelSmall,
        )
      }

      if (music.lyricsOrNotes.isNotBlank()) {
        Text(
          text = music.lyricsOrNotes.take(180),
          color = colors.mutedText,
          style = MaterialTheme.typography.bodySmall,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = {
            if (!fileExists) return@OutlinedButton
            val current = player
            if (current != null && current.isPlaying) {
              current.pause()
              isPlaying = false
            } else if (current != null) {
              current.start()
              isPlaying = true
            } else {
              runCatching {
                val created =
                  MediaPlayer.create(context, Uri.fromFile(File(music.filePath)))?.apply {
                    setOnCompletionListener { isPlaying = false }
                    start()
                  }
                player = created
                isPlaying = created != null
              }
            }
          },
          enabled = fileExists,
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = if (isPlaying) "Pause track" else "Play track",
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(if (isPlaying) "Pause" else "Play Track")
        }
        OutlinedButton(onClick = onSaveMusic, enabled = fileExists) {
          Icon(Icons.Outlined.Download, contentDescription = "Save audio", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("Save")
        }
        OutlinedButton(onClick = onShareMusic, enabled = fileExists) {
          Icon(Icons.Outlined.Share, contentDescription = "Share audio", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("Share")
        }
      }
    }
  }
}
