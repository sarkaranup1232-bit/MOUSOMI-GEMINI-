package com.example.domain.orchestrator

import com.example.ai.image.ImageGenerationEvent
import com.example.ai.image.ImageGenerationProvider
import com.example.ai.image.ImageGenerationRequest
import com.example.ai.media.VideoAndMusicGenerationService
import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.ai.provider.AiTurn
import com.example.analytics.charts.ChartDataBuilder
import com.example.analytics.parser.TabularDataParser
import com.example.analytics.statistics.StatisticalAnalyzer
import com.example.core.alarms.ReminderManager
import com.example.core.device.DeviceActionExecutor
import com.example.data.repository.Part5Repository
import com.example.domain.model.AgentTaskLifecycleState
import com.example.domain.model.AiModelSpec
import com.example.domain.model.AiResponseState
import com.example.domain.model.AppSettings
import com.example.domain.model.DeepResearchProgress
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageGenerationStatus
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.ProviderType
import com.example.domain.model.ResponseBlock
import com.example.domain.model.RoutingDecision
import com.example.domain.model.SearchResultItem
import com.example.domain.model.TokenUsage
import com.example.domain.task.TaskExecutionEngine
import com.example.multimodal.audio.AudioIntelligenceEngine
import com.example.multimodal.document.DocumentIntelligenceEngine
import com.example.multimodal.image.PhotoIntelligenceEngine
import com.example.multimodal.video.VideoIntelligenceEngine
import com.example.research.deepresearch.DeepResearchEngine
import com.example.research.search.SearchQueryRequest
import com.example.research.search.SearchService
import com.example.research.search.SearchTopic
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.FlowCollector

@Singleton
class Part3IntelligencePipeline @Inject constructor(
  private val searchService: SearchService,
  private val deepResearchEngine: DeepResearchEngine,
  private val photoIntelligenceEngine: PhotoIntelligenceEngine,
  private val videoIntelligenceEngine: VideoIntelligenceEngine,
  private val audioIntelligenceEngine: AudioIntelligenceEngine,
  private val documentIntelligenceEngine: DocumentIntelligenceEngine,
  private val tabularDataParser: TabularDataParser,
  private val statisticalAnalyzer: StatisticalAnalyzer,
  private val chartDataBuilder: ChartDataBuilder,
  private val providers: Map<ProviderType, @JvmSuppressWildcards AiProvider>,
  private val imageGenerationProvider: ImageGenerationProvider? = null,
  private val part5Repository: Part5Repository? = null,
  private val deviceActionExecutor: DeviceActionExecutor? = null,
  private val reminderManager: ReminderManager? = null,
  private val taskExecutionEngine: TaskExecutionEngine? = null,
  private val videoAndMusicService: VideoAndMusicGenerationService? = null,
) {

  suspend fun handleSpecialIntent(
    intent: IntelligenceIntent,
    cleanPrompt: String,
    request: OrchestrationRequest,
    decision: RoutingDecision,
    settings: AppSettings,
    collector: FlowCollector<OrchestrationEvent>,
  ): PipelinePreparation {
    return when (intent) {
      IntelligenceIntent.VIDEO_INTELLIGENCE -> {
        val videoItem =
          request.pendingMediaItems.firstOrNull { it.kind == MediaAttachmentKind.VIDEO }
            ?: return PipelinePreparation.Aborted("No video attachment found.")
        val result =
          videoIntelligenceEngine.analyzeVideo(
            uriString = videoItem.uriString,
            userPrompt = cleanPrompt.ifBlank { "Summarize the video and generate a scene-by-scene timeline." },
            model = decision.selectedModel,
            settings = settings,
            onProgress = { msg ->
              collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Searching(msg)))
            },
          )
        if (result.isFailure) {
          return PipelinePreparation.Aborted(
            result.exceptionOrNull()?.message ?: "Video processing failed."
          )
        }
        val videoAnalysis = result.getOrThrow()
        val blocks = listOf(ResponseBlock.VideoAnalysisBlock(videoAnalysis))
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        val narrative =
          buildString {
            appendLine("## Video Analysis: ${videoAnalysis.topic}")
            appendLine()
            appendLine(videoAnalysis.summary)
            appendLine()
            appendLine("> ${videoAnalysis.analysisMethodNote}")
          }.trim()
        PipelinePreparation.AlreadyCompleted(
          fullText = narrative,
          blocks = blocks,
          modelName = decision.selectedModel.displayName,
        )
      }

      IntelligenceIntent.AUDIO_INTELLIGENCE -> {
        val audioItem =
          request.pendingMediaItems.firstOrNull { it.kind == MediaAttachmentKind.AUDIO }
            ?: return PipelinePreparation.Aborted("No audio attachment found.")
        val result =
          audioIntelligenceEngine.analyzeAudio(
            uriString = audioItem.uriString,
            userPrompt = cleanPrompt.ifBlank { "Transcribe and summarize this audio." },
            model = decision.selectedModel,
            settings = settings,
            onProgress = { msg ->
              collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Searching(msg)))
            },
          )
        if (result.isFailure) {
          return PipelinePreparation.Aborted(
            result.exceptionOrNull()?.message ?: "Audio intelligence failed."
          )
        }
        val audioRes = result.getOrThrow()
        val blocks = listOf(ResponseBlock.AudioTranscriptBlock(audioRes))
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        val narrative =
          buildString {
            appendLine("## Audio Summary (${audioRes.detectedLanguage})")
            appendLine()
            appendLine(audioRes.summary)
          }.trim()
        PipelinePreparation.AlreadyCompleted(
          fullText = narrative,
          blocks = blocks,
          modelName = audioRes.transcriptionEngine,
        )
      }

      IntelligenceIntent.DEEP_RESEARCH -> {
        var finalBlocks: List<ResponseBlock> = emptyList()
        var finalText = ""
        var failureMsg: String? = null

        deepResearchEngine
          .executeResearch(
            question = cleanPrompt,
            model = decision.selectedModel,
            settings = settings,
          )
          .collect { progress ->
            when (progress) {
              DeepResearchProgress.UnderstandingQuestion ->
                collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Thinking("Understanding research question…")))
              is DeepResearchProgress.DetectingComplexity ->
                collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Thinking("Research depth: ${progress.complexityLabel}")))
              is DeepResearchProgress.PlanningQueries ->
                collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Searching("Planned ${progress.queries.size} live search queries…")))
              is DeepResearchProgress.SearchingWeb ->
                collector.emit(
                  OrchestrationEvent.StateChanged(
                    AiResponseState.Searching(
                      "Searching (${progress.queryIndex}/${progress.totalQueries}): ${progress.currentQuery} • ${progress.sourcesCollected} sources"
                    )
                  )
                )
              is DeepResearchProgress.DeduplicatingAndVerifying ->
                collector.emit(
                  OrchestrationEvent.StateChanged(
                    AiResponseState.Searching(
                      "Verified ${progress.verifiedCount} HTTPS sources (${progress.rawCount - progress.verifiedCount} duplicates/invalid filtered)"
                    )
                  )
                )
              is DeepResearchProgress.SynthesizingReport ->
                collector.emit(
                  OrchestrationEvent.StateChanged(
                    AiResponseState.Thinking("Synthesizing 10-section report from ${progress.verifiedSourcesCount} verified sources…")
                  )
                )
              is DeepResearchProgress.Completed -> {
                finalBlocks =
                  listOf(
                    ResponseBlock.ResearchReportBlock(progress.report),
                    ResponseBlock.SourceCitations(progress.report.references),
                  )
                finalText = progress.report.executiveSummary
              }
              DeepResearchProgress.Cancelled ->
                collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Cancelled))
              is DeepResearchProgress.Failed ->
                failureMsg = progress.reason
            }
          }

        val err = failureMsg
        if (err != null) {
          return PipelinePreparation.Aborted(err)
        }
        collector.emit(OrchestrationEvent.BlocksUpdated(finalBlocks))
        PipelinePreparation.AlreadyCompleted(
          fullText = finalText,
          blocks = finalBlocks,
          modelName = decision.selectedModel.displayName,
        )
      }

      IntelligenceIntent.LIVE_SEARCH -> {
        collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Searching("Running real-time web search…")))
        val topic =
          if ("news" in cleanPrompt.lowercase() || "today" in cleanPrompt.lowercase()) SearchTopic.NEWS
          else SearchTopic.GENERAL
        val searchRes =
          searchService.search(
            SearchQueryRequest(
              query = cleanPrompt,
              topic = topic,
              page = 1,
              pageSize = 6,
              includePageContent = true,
            ),
            settings,
          )
        if (searchRes.isFailure) {
          val reason = searchRes.exceptionOrNull()?.message ?: "Live search failed."
          return PipelinePreparation.Aborted(
            "Live Search Unavailable: $reason. Never claiming live search results without a configured search provider."
          )
        }
        val sources = searchRes.getOrThrow().results
        val blocks =
          if (sources.isNotEmpty()) listOf(ResponseBlock.SourceCitations(sources)) else emptyList()
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        val sourceContext = formatSourcesForPrompt(sources)
        PipelinePreparation.ContinueWithLlm(
          extraSystemContext = sourceContext,
          preEmittedBlocks = blocks,
        )
      }

      IntelligenceIntent.DOCUMENT_INTELLIGENCE,
      IntelligenceIntent.DATA_ANALYSIS,
      IntelligenceIntent.CHART_VISUALIZATION -> {
        handleDocumentsAndAnalytics(intent, cleanPrompt, request, settings, collector)
      }

      IntelligenceIntent.PHOTO_INTELLIGENCE -> {
        val visionGuide =
          photoIntelligenceEngine.buildVisionInstruction(cleanPrompt, request.currentTurnImages)
        PipelinePreparation.ContinueWithLlm(extraSystemContext = visionGuide)
      }

      IntelligenceIntent.IMAGE_GENERATION,
      IntelligenceIntent.IMAGE_EDITING -> {
        handleImageGenerationOrEdit(intent, cleanPrompt, request, settings, collector)
      }

      IntelligenceIntent.DEVICE_ACTION -> {
        val executor =
          deviceActionExecutor
            ?: return PipelinePreparation.Aborted("Device Assistant module is not available.")
        collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Thinking("Executing verified Android device action…")))
        val result = executor.executeNaturalCommand(cleanPrompt)
        val blocks = listOf(ResponseBlock.DeviceActionsBlock(listOf(result)))
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        val narrative =
          buildString {
            appendLine("## Device Assistant • ${result.statusLabel}")
            appendLine()
            appendLine("**Action:** ${result.targetLabel}")
            appendLine("**Result:** ${result.verifiedDetail}")
          }.trim()
        PipelinePreparation.AlreadyCompleted(
          fullText = narrative,
          blocks = blocks,
          modelName = "Mousomi Device Assistant",
        )
      }

      IntelligenceIntent.ALARM_REMINDER -> {
        val mgr =
          reminderManager
            ?: return PipelinePreparation.Aborted("Smart Alarm & Reminder manager is not available.")
        collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Thinking("Scheduling & verifying alarm/reminder…")))
        val (summary, entries) = mgr.processNaturalCommand(cleanPrompt)
        val blocks = listOf(ResponseBlock.ReminderResultBlock(summary = summary, reminders = entries))
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        val narrative =
          buildString {
            appendLine("## Alarms, Timers & Reminders")
            appendLine()
            appendLine(summary)
          }.trim()
        PipelinePreparation.AlreadyCompleted(
          fullText = narrative,
          blocks = blocks,
          modelName = "Mousomi Alarm & Reminder Agent",
        )
      }

      IntelligenceIntent.MULTI_TASK -> {
        val engine =
          taskExecutionEngine
            ?: return PipelinePreparation.Aborted("Multi-Task Agent engine is not available.")
        collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Thinking("Planning & executing multi-step tasks…")))
        val report = engine.planAndExecuteMultiTask(cleanPrompt, approveSensitiveTasks = false)
        val blocks = listOf(ResponseBlock.MultiTaskBatchBlock(report))
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        val narrative =
          buildString {
            appendLine("## Multi-Task Execution Report")
            appendLine()
            appendLine("Planned **${report.tasks.size}** step(s): **${report.completedCount}** completed, **${report.failedCount}** unavailable/failed, **${report.awaitingCount}** awaiting approval.")
            appendLine()
            report.tasks.forEach { t ->
              val detail = t.errorDetails.ifBlank { t.resultMessage }
              appendLine("- **${t.description}** — *${t.state.label}*: $detail")
            }
          }.trim()
        PipelinePreparation.AlreadyCompleted(
          fullText = narrative,
          blocks = blocks,
          modelName = "Mousomi Multi-Task Agent",
        )
      }

      IntelligenceIntent.MAPS_SEARCH -> {
        collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Searching("Searching verified Google Maps data…")))
        val mapsRes = searchService.searchMaps(cleanPrompt, settings)
        if (mapsRes.isFailure) {
          val reason = mapsRes.exceptionOrNull()?.message ?: "Maps search failed."
          return PipelinePreparation.Aborted("Maps Grounding Unavailable: $reason")
        }
        val sources = mapsRes.getOrThrow().results
        val blocks =
          if (sources.isNotEmpty()) listOf(ResponseBlock.SourceCitations(sources)) else emptyList()
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        val sourceContext = formatSourcesForPrompt(sources)
        PipelinePreparation.ContinueWithLlm(
          extraSystemContext = sourceContext,
          preEmittedBlocks = blocks,
        )
      }

      IntelligenceIntent.IMAGE_TO_VIDEO,
      IntelligenceIntent.TEXT_TO_VIDEO -> {
        val svc =
          videoAndMusicService
            ?: return PipelinePreparation.Aborted("Video generation engine is not initialized.")
        val sourceImage = request.currentTurnImages.firstOrNull()
        if (intent == IntelligenceIntent.IMAGE_TO_VIDEO && sourceImage == null) {
          return PipelinePreparation.Aborted(
            "Attach an image first to animate it into a video, or ask to generate a video from text."
          )
        }
        collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Generating("Generating video…")))
        val videoResult =
          svc.generateVideo(
            prompt = cleanPrompt.ifBlank { "Animate this scene smoothly with natural camera motion." },
            sourceImage = sourceImage,
            settings = settings,
            onProgress = { msg ->
              collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Generating(msg)))
            },
          )
        if (videoResult.isFailure) {
          return PipelinePreparation.Aborted(
            videoResult.exceptionOrNull()?.message ?: "Video generation failed."
          )
        }
        val generated = videoResult.getOrThrow()
        val blocks = listOf(ResponseBlock.GeneratedVideoBlock(generated))
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        PipelinePreparation.AlreadyCompleted(
          fullText = "Generated ${if (generated.isImageToVideo) "image-to-video animation" else "video"} using **${generated.providerModel}**.",
          blocks = blocks,
          modelName = generated.providerModel,
        )
      }

      IntelligenceIntent.MUSIC_GENERATION -> {
        val svc =
          videoAndMusicService
            ?: return PipelinePreparation.Aborted("Music generation engine is not initialized.")
        collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Generating("Generating AI music…")))
        val musicResult =
          svc.generateMusic(
            prompt = cleanPrompt.ifBlank { "Compose an uplifting ambient instrumental track." },
            settings = settings,
            onProgress = { msg ->
              collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Generating(msg)))
            },
          )
        if (musicResult.isFailure) {
          return PipelinePreparation.Aborted(
            musicResult.exceptionOrNull()?.message ?: "Music generation failed."
          )
        }
        val generated = musicResult.getOrThrow()
        val blocks = listOf(ResponseBlock.GeneratedMusicBlock(generated))
        collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
        val narrative =
          buildString {
            appendLine("Generated AI music track using **${generated.providerModel}**.")
            if (generated.lyricsOrNotes.isNotBlank()) {
              appendLine()
              appendLine(generated.lyricsOrNotes)
            }
          }.trim()
        PipelinePreparation.AlreadyCompleted(
          fullText = narrative,
          blocks = blocks,
          modelName = generated.providerModel,
        )
      }

      IntelligenceIntent.DIRECT_CHAT,
      IntelligenceIntent.NORMAL_AI -> PipelinePreparation.ContinueWithLlm()
    }
  }

  private suspend fun handleImageGenerationOrEdit(
    intent: IntelligenceIntent,
    cleanPrompt: String,
    request: OrchestrationRequest,
    settings: AppSettings,
    collector: FlowCollector<OrchestrationEvent>,
  ): PipelinePreparation {
    val provider =
      imageGenerationProvider
        ?: return PipelinePreparation.Aborted("Image generation engine is not initialized.")

    val target = request.activeEditTarget
    val sourceImage = request.currentTurnImages.firstOrNull()
    if (intent == IntelligenceIntent.IMAGE_EDITING && sourceImage == null) {
      return PipelinePreparation.Aborted(
        "Attach or select an image to edit first, then describe the visual change."
      )
    }

    val nextVersion =
      if (target != null && part5Repository != null) {
        part5Repository.getNextVersionNumber(target.rootMediaId)
      } else if (target != null) {
        target.versionNumber + 1
      } else {
        1
      }

    val placeholderId = "pending_${UUID.randomUUID()}"
    val modelDisplay = settings.imageModelId.ifBlank { "Gemini Image Engine" }
    var completedResult: GeneratedImageResult? = null
    var failureMsg: String? = null

    provider
      .generateOrEdit(
        ImageGenerationRequest(
          prompt = cleanPrompt.ifBlank { "Enhance visual clarity and lighting" },
          settings = settings,
          sourceImage = sourceImage,
          regionOverlayImage = request.regionOverlayImage,
          parentMediaId = target?.mediaId,
          rootMediaId = target?.rootMediaId,
          nextVersionNumber = nextVersion,
          editInstruction = if (sourceImage != null) cleanPrompt else null,
        )
      )
      .collect { event ->
        when (event) {
          is ImageGenerationEvent.Stage -> {
            collector.emit(OrchestrationEvent.StateChanged(AiResponseState.Generating(event.status.label)))
            val stageBlock =
              ResponseBlock.GeneratedImageBlock(
                GeneratedImageResult(
                  mediaId = placeholderId,
                  rootMediaId = target?.rootMediaId ?: placeholderId,
                  parentMediaId = target?.mediaId,
                  versionNumber = nextVersion,
                  prompt = cleanPrompt,
                  editInstruction = if (sourceImage != null) cleanPrompt else null,
                  filePath = "",
                  providerModel = modelDisplay,
                  status = event.status,
                )
              )
            collector.emit(OrchestrationEvent.BlocksUpdated(listOf(stageBlock)))
          }
          is ImageGenerationEvent.Success -> {
            completedResult = event.result
          }
          is ImageGenerationEvent.Failure -> {
            failureMsg = event.message
          }
        }
      }

    val err = failureMsg
    if (err != null) {
      val failedBlock =
        ResponseBlock.GeneratedImageBlock(
          GeneratedImageResult(
            mediaId = placeholderId,
            rootMediaId = target?.rootMediaId ?: placeholderId,
            parentMediaId = target?.mediaId,
            versionNumber = nextVersion,
            prompt = cleanPrompt,
            filePath = "",
            providerModel = modelDisplay,
            status = ImageGenerationStatus.FAILED,
            errorMessage = err,
          )
        )
      collector.emit(OrchestrationEvent.BlocksUpdated(listOf(failedBlock)))
      return PipelinePreparation.Aborted(err)
    }

    val finalResult = completedResult ?: return PipelinePreparation.Aborted("No image result returned.")
    val finalBlocks = listOf(ResponseBlock.GeneratedImageBlock(finalResult))
    collector.emit(OrchestrationEvent.BlocksUpdated(finalBlocks))
    val summaryText =
      buildString {
        if (sourceImage != null) {
          appendLine("Edited image **v${finalResult.versionNumber}** with **${finalResult.providerModel}**.")
        } else {
          appendLine("Generated image **v${finalResult.versionNumber}** with **${finalResult.providerModel}**.")
        }
        if (finalResult.captionText.isNotBlank()) {
          appendLine()
          appendLine(finalResult.captionText)
        }
      }.trim()

    return PipelinePreparation.AlreadyCompleted(
      fullText = summaryText,
      blocks = finalBlocks,
      modelName = finalResult.providerModel,
    )
  }

  private suspend fun handleDocumentsAndAnalytics(
    intent: IntelligenceIntent,
    cleanPrompt: String,
    request: OrchestrationRequest,
    settings: AppSettings,
    collector: FlowCollector<OrchestrationEvent>,
  ): PipelinePreparation {
    val docAttachments = request.pendingMediaItems.filter { it.kind == MediaAttachmentKind.DOCUMENT }
    val extractedDocs =
      if (docAttachments.isNotEmpty()) {
        collector.emit(
          OrchestrationEvent.StateChanged(
            AiResponseState.Searching("Extracting text & tables from ${docAttachments.size} document(s)…")
          )
        )
        documentIntelligenceEngine
          .extractDocuments(docAttachments.map { it.uriString })
          .getOrElse { err ->
            return PipelinePreparation.Aborted(err.message ?: "Unable to read uploaded document.")
          }
      } else {
        emptyList()
      }

    val blocks = mutableListOf<ResponseBlock>()
    val scannedImages = mutableListOf<ProcessedImagePayload>()

    extractedDocs.forEach { doc ->
      blocks +=
        ResponseBlock.DocumentSummaryBlock(
          documentName = doc.displayName,
          formatLabel = doc.formatLabel,
          pageCount = doc.pageCount,
          extractedPreview = doc.extractedText.take(550),
          tables = doc.tables.take(2),
          warnings = doc.warnings,
        )
      scannedImages += doc.scannedPageImages
    }

    val tables = mutableListOf<com.example.domain.model.ParsedTable>()
    tables += tabularDataParser.extractTablesFromDocuments(extractedDocs)
    tabularDataParser.parseInlineTabularText("Prompt Dataset", cleanPrompt)?.let { tables += it }

    val analyticsNotes = StringBuilder()
    if (tables.isNotEmpty() && (intent == IntelligenceIntent.DATA_ANALYSIS || intent == IntelligenceIntent.CHART_VISUALIZATION || extractedDocs.any { it.formatLabel in setOf("CSV", "XLSX") })) {
      collector.emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Thinking("Cleaning dataset & computing verified statistics…")
        )
      )
      val targetTable = tables.first()
      val analytics = statisticalAnalyzer.analyze(targetTable, settings)
      blocks += ResponseBlock.DataAnalyticsBlock(analytics)

      analyticsNotes.appendLine("COMPUTED DATA ANALYTICS (use these exact computed numbers; never fabricate values):")
      analytics.numericStatistics.forEach { st ->
        analyticsNotes.appendLine(
          "- ${st.columnName}: count=${st.count}, sum=${"%.2f".format(st.sum)}, mean=${"%.2f".format(st.mean)}, median=${"%.2f".format(st.median)}, min=${"%.2f".format(st.min)}, max=${"%.2f".format(st.max)}, stdDev=${"%.2f".format(st.stdDev)}"
        )
      }
      if (analytics.detectedTrends.isNotEmpty()) {
        analyticsNotes.appendLine("Trends: ${analytics.detectedTrends.joinToString("; ")}")
      }
      if (analytics.correlationNotes.isNotEmpty()) {
        analyticsNotes.appendLine("Correlations: ${analytics.correlationNotes.joinToString("; ")}")
      }

      val chartTable = analytics.previewTable ?: targetTable
      chartDataBuilder
        .buildChartFromVerifiedTable(
          table = chartTable,
          prompt = cleanPrompt,
          sourceAttribution = "Verified rows from ${targetTable.name}",
        )
        .onSuccess { spec ->
          blocks += ResponseBlock.ChartBlock(spec)
        }
    } else if (intent == IntelligenceIntent.CHART_VISUALIZATION && tables.isEmpty()) {
      return PipelinePreparation.Aborted(
        "To build a verified chart without fabricating numbers, please upload a CSV/XLSX file or include a dataset/table in your prompt."
      )
    }

    if (blocks.isNotEmpty()) {
      collector.emit(OrchestrationEvent.BlocksUpdated(blocks))
    }

    val docContext =
      if (extractedDocs.isNotEmpty()) {
        documentIntelligenceEngine.buildDocumentPromptContext(extractedDocs, cleanPrompt)
      } else ""

    return PipelinePreparation.ContinueWithLlm(
      extraSystemContext = listOf(docContext, analyticsNotes.toString()).filter { it.isNotBlank() }.joinToString("\n\n"),
      extraImages = scannedImages,
      preEmittedBlocks = blocks,
    )
  }

  private fun formatSourcesForPrompt(sources: List<SearchResultItem>): String {
    if (sources.isEmpty()) return ""
    return buildString {
      appendLine("VERIFIED LIVE WEB SEARCH SOURCES (Cite real URLs only from this list):")
      sources.take(6).forEachIndexed { idx, s ->
        appendLine("[${idx + 1}] ${s.title} (${s.publisher}, ${s.publishedTimestamp ?: "recent"}) - ${s.url}")
        appendLine("Snippet: ${s.snippet}")
        s.pageExcerpt?.let { appendLine("Excerpt: ${it.take(500)}") }
      }
    }
  }
}

sealed interface PipelinePreparation {
  data class ContinueWithLlm(
    val extraSystemContext: String = "",
    val extraImages: List<ProcessedImagePayload> = emptyList(),
    val preEmittedBlocks: List<ResponseBlock> = emptyList(),
  ) : PipelinePreparation

  data class AlreadyCompleted(
    val fullText: String,
    val blocks: List<ResponseBlock>,
    val modelName: String,
    val tokenUsage: TokenUsage? = null,
  ) : PipelinePreparation

  data class Aborted(val errorMessage: String) : PipelinePreparation
}
