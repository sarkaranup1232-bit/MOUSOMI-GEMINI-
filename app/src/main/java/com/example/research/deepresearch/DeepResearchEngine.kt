package com.example.research.deepresearch

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.ai.provider.AiTurn
import com.example.domain.model.AiModelSpec
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DeepResearchProgress
import com.example.domain.model.LiveSourceLoadStatus
import com.example.domain.model.LiveSourceTelemetry
import com.example.domain.model.ProviderType
import com.example.domain.model.ResearchActivitySnapshot
import com.example.domain.model.ResearchReport
import com.example.domain.model.ResearchStageId
import com.example.domain.model.ResearchStageItem
import com.example.domain.model.ResearchStageStatus
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SourceVerificationStatus
import com.example.research.search.SearchQueryRequest
import com.example.research.search.SearchService
import com.example.research.search.SearchTopic
import com.example.research.verification.SourceVerifier
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

@Serializable
private data class LlmResearchSynthesis(
  val executiveSummary: String = "",
  val objectives: List<String> = emptyList(),
  val methodology: String = "",
  val keyFindings: List<String> = emptyList(),
  val detailedAnalysis: String = "",
  val limitations: List<String> = emptyList(),
  val conclusion: String = "",
)

@Singleton
class DeepResearchEngine @Inject constructor(
  private val searchService: SearchService,
  private val sourceVerifier: SourceVerifier,
  private val providers: Map<ProviderType, @JvmSuppressWildcards AiProvider>,
  private val json: Json,
) {

  fun executeResearch(
    question: String,
    model: AiModelSpec,
    settings: AppSettings,
  ): Flow<DeepResearchProgress> = flow {
    val stageMap = ResearchStageId.entries.associateWith {
      ResearchStageItem(stage = it, status = ResearchStageStatus.WAITING)
    }.toMutableMap()
    val liveSources = mutableListOf<LiveSourceTelemetry>()
    val retrievedRaw = mutableListOf<SearchResultItem>()
    var verifiedSourcesSnapshot = emptyList<SearchResultItem>()
    var plannedQueries = emptyList<String>()

    fun buildSnapshot(
      headline: String,
      isActive: Boolean = true,
      isCancelled: Boolean = false,
    ): ResearchActivitySnapshot =
      ResearchActivitySnapshot(
        query = question,
        isActive = isActive,
        isCancelled = isCancelled,
        currentHeadline = headline,
        plannedQueries = plannedQueries,
        stages = ResearchStageId.entries.mapNotNull { stageMap[it] },
        liveSources = liveSources.toList(),
        retrievedSources = if (verifiedSourcesSnapshot.isNotEmpty()) verifiedSourcesSnapshot else retrievedRaw.toList(),
      )

    fun updateStage(stage: ResearchStageId, status: ResearchStageStatus, detail: String = "") {
      stageMap[stage] = ResearchStageItem(stage = stage, status = status, detail = detail)
    }

    try {
      // Stage 1: Understand
      updateStage(ResearchStageId.UNDERSTAND, ResearchStageStatus.ACTIVE, "Normalizing intent & language")
      emit(DeepResearchProgress.UnderstandingQuestion)
      emit(DeepResearchProgress.StageUpdate(buildSnapshot("Understanding your research question…")))
      val complexity = detectComplexity(question)
      updateStage(ResearchStageId.UNDERSTAND, ResearchStageStatus.COMPLETED, complexity)
      emit(DeepResearchProgress.DetectingComplexity(complexity))

      // Stage 2: Planning
      coroutineContext.ensureActive()
      updateStage(ResearchStageId.PLANNING, ResearchStageStatus.ACTIVE, "Decomposing multi-angle queries")
      plannedQueries = buildResearchQueries(question, complexity)
      updateStage(ResearchStageId.PLANNING, ResearchStageStatus.COMPLETED, "${plannedQueries.size} targeted queries")
      emit(DeepResearchProgress.PlanningQueries(plannedQueries))
      emit(DeepResearchProgress.StageUpdate(buildSnapshot("Planned ${plannedQueries.size} search angles…")))

      val topic =
        when {
          "news" in question.lowercase() || "today" in question.lowercase() || "latest" in question.lowercase() ->
            SearchTopic.NEWS
          "ai" in question.lowercase() || "kotlin" in question.lowercase() || "software" in question.lowercase() ->
            SearchTopic.TECHNOLOGY
          else -> SearchTopic.RESEARCH
        }

      // Stage 3: Live Search
      updateStage(ResearchStageId.LIVE_SEARCH, ResearchStageStatus.ACTIVE, "Querying live search providers")
      emit(DeepResearchProgress.StageUpdate(buildSnapshot("Searching live web sources…")))
      var lastError: Throwable? = null
      plannedQueries.forEachIndexed { idx, q ->
        coroutineContext.ensureActive()
        emit(
          DeepResearchProgress.SearchingWeb(
            currentQuery = q,
            queryIndex = idx + 1,
            totalQueries = plannedQueries.size,
            sourcesCollected = retrievedRaw.size,
          )
        )
        val res =
          searchService.search(
            SearchQueryRequest(
              query = q,
              topic = topic,
              page = 1,
              pageSize = 6,
              includePageContent = idx <= 1,
            ),
            settings,
          )
        if (res.isSuccess) {
          val found = res.getOrThrow().results
          retrievedRaw += found
          found.forEach { item ->
            if (liveSources.none { it.url == item.url }) {
              liveSources +=
                LiveSourceTelemetry(
                  id = item.id,
                  domain = item.sourceDomain,
                  title = item.title,
                  url = item.url,
                  faviconUrl =
                    item.faviconUrl ?: "https://www.google.com/s2/favicons?domain=${item.sourceDomain}&sz=64",
                  loadStatus = LiveSourceLoadStatus.CONNECTING,
                )
            }
          }
          emit(
            DeepResearchProgress.StageUpdate(
              buildSnapshot("Found ${retrievedRaw.size} candidate sources (${idx + 1}/${plannedQueries.size})…")
            )
          )
        } else {
          lastError = res.exceptionOrNull()
        }
      }

      if (retrievedRaw.isEmpty()) {
        val msg =
          lastError?.message
            ?: "Unable to retrieve live web sources for deep research. Check your API key or Search configuration in Settings."
        updateStage(ResearchStageId.LIVE_SEARCH, ResearchStageStatus.FAILED, msg)
        emit(DeepResearchProgress.StageUpdate(buildSnapshot("Live Search failed", isActive = false)))
        emit(DeepResearchProgress.Failed(msg))
        return@flow
      }

      updateStage(ResearchStageId.LIVE_SEARCH, ResearchStageStatus.COMPLETED, "${retrievedRaw.size} hits retrieved")

      // Stage 4: Source Reading
      coroutineContext.ensureActive()
      updateStage(ResearchStageId.SOURCE_READING, ResearchStageStatus.ACTIVE, "Inspecting page titles, dates & excerpts")
      liveSources.forEachIndexed { idx, src ->
        val matching = retrievedRaw.firstOrNull { it.url == src.url }
        val status =
          when {
            matching == null -> LiveSourceLoadStatus.FAILED
            matching.verificationStatus == SourceVerificationStatus.FETCH_FAILED -> LiveSourceLoadStatus.FAILED
            !matching.pageExcerpt.isNullOrBlank() -> LiveSourceLoadStatus.READ
            else -> LiveSourceLoadStatus.OPENED
          }
        liveSources[idx] = src.copy(loadStatus = status)
      }
      val analyzedPagesCount = retrievedRaw.count { !it.pageExcerpt.isNullOrBlank() }
      updateStage(
        ResearchStageId.SOURCE_READING,
        ResearchStageStatus.COMPLETED,
        "$analyzedPagesCount full excerpts read, ${retrievedRaw.size} metadata inspected",
      )
      emit(DeepResearchProgress.AnalyzingPages(analyzedCount = analyzedPagesCount, totalCount = retrievedRaw.size))
      emit(DeepResearchProgress.StageUpdate(buildSnapshot("Reading & extracting verified pages…")))

      // Stage 5: Cross-Verification
      coroutineContext.ensureActive()
      updateStage(ResearchStageId.CROSS_VERIFICATION, ResearchStageStatus.ACTIVE, "Comparing claims & HTTPS domains")
      val verifiedBundle = sourceVerifier.verifyAndCompare(retrievedRaw)
      val verifiedSources = verifiedBundle.uniqueVerifiedSources
      verifiedSourcesSnapshot = verifiedSources

      emit(
        DeepResearchProgress.DeduplicatingAndVerifying(
          rawCount = retrievedRaw.size,
          verifiedCount = verifiedSources.size,
        )
      )

      if (verifiedSources.isEmpty()) {
        val err =
          "Retrieved links failed HTTPS source verification (${verifiedBundle.rejectedCount} rejected). Refusing to fabricate research citations."
        updateStage(ResearchStageId.CROSS_VERIFICATION, ResearchStageStatus.FAILED, err)
        emit(DeepResearchProgress.StageUpdate(buildSnapshot("Verification failed", isActive = false)))
        emit(DeepResearchProgress.Failed(err))
        return@flow
      }

      updateStage(
        ResearchStageId.CROSS_VERIFICATION,
        ResearchStageStatus.COMPLETED,
        "${verifiedSources.size} unique HTTPS sources verified",
      )
      emit(DeepResearchProgress.StageUpdate(buildSnapshot("Cross-verified ${verifiedSources.size} unique sources…")))

      // Stage 6: Analysis (strictly structured synthesis — never prints internal chain-of-thought)
      coroutineContext.ensureActive()
      updateStage(ResearchStageId.ANALYSIS, ResearchStageStatus.ACTIVE, "Synthesizing grounded evidence")
      emit(DeepResearchProgress.SynthesizingReport(verifiedSources.size))
      emit(DeepResearchProgress.StageUpdate(buildSnapshot("Synthesizing evidence & reconciling conflicts…")))

      val synthesis = synthesizeWithModel(question, plannedQueries, verifiedSources, model, settings)
      updateStage(ResearchStageId.ANALYSIS, ResearchStageStatus.COMPLETED, "Analysis ready")

      // Stage 7: Structured Final Report
      coroutineContext.ensureActive()
      updateStage(ResearchStageId.FINAL_REPORT, ResearchStageStatus.ACTIVE, "Formatting cited report")
      val report =
        ResearchReport(
          query = question,
          complexity = complexity,
          executiveSummary = synthesis.executiveSummary,
          researchObjectives = synthesis.objectives.ifEmpty {
            listOf(
              "Investigate verified current facts regarding: $question",
              "Cross-check claims across ${verifiedSources.size} retrieved HTTPS sources",
            )
          },
          methodology = synthesis.methodology.ifBlank {
            "Executed ${plannedQueries.size} live search queries, removed ${verifiedBundle.duplicatesRemoved} duplicate entries, verified HTTPS links, and compared claims across ${verifiedSources.size} distinct sources."
          },
          keyFindings = synthesis.keyFindings.ifEmpty {
            verifiedSources.take(4).map { "[${it.publisher}] ${it.snippet.take(180)}" }
          },
          detailedAnalysis = synthesis.detailedAnalysis,
          sourceComparison = verifiedBundle.sourceComparisonNotes,
          conflictingInformation = verifiedBundle.conflictingClaims,
          limitations = synthesis.limitations.ifEmpty {
            listOf(
              "Findings reflect snippets and retrieved excerpts available at query time.",
              "Paywalled or script-rendered pages beyond retrieved excerpts were not fully crawled.",
            )
          },
          conclusion = synthesis.conclusion,
          references = verifiedSources,
          generatedAtMs = System.currentTimeMillis(),
        )

      updateStage(ResearchStageId.FINAL_REPORT, ResearchStageStatus.COMPLETED, "${verifiedSources.size} citations attached")
      emit(DeepResearchProgress.StageUpdate(buildSnapshot("Research complete", isActive = false)))
      emit(DeepResearchProgress.Completed(report))
    } catch (e: CancellationException) {
      ResearchStageId.entries.forEach { st ->
        val current = stageMap[st]
        if (current != null && (current.status == ResearchStageStatus.WAITING || current.status == ResearchStageStatus.ACTIVE)) {
          stageMap[st] = current.copy(status = ResearchStageStatus.CANCELLED)
        }
      }
      throw e
    } catch (e: Exception) {
      ResearchStageId.entries.forEach { st ->
        val current = stageMap[st]
        if (current != null && current.status == ResearchStageStatus.ACTIVE) {
          stageMap[st] = current.copy(status = ResearchStageStatus.FAILED, detail = e.localizedMessage ?: "Error")
        }
      }
      emit(DeepResearchProgress.StageUpdate(buildSnapshot("Research interrupted", isActive = false)))
      emit(DeepResearchProgress.Failed(e.localizedMessage ?: "Deep research encountered an error."))
    }
  }

  fun detectComplexity(question: String): String {
    val words = question.trim().split(Regex("\\s+")).size
    val lower = question.lowercase()
    return when {
      words > 18 || "compare" in lower || "architecture" in lower || "detailed" in lower ->
        "High (Multi-Angle Investigation)"
      words > 8 || "why" in lower || "how" in lower ->
        "Moderate (Structured Analysis)"
      else -> "Focused (Fact & Source Verification)"
    }
  }

  fun buildResearchQueries(question: String, complexity: String): List<String> {
    val base = question.trim()
    val queries = mutableListOf(base)
    if (!complexity.startsWith("Focused")) {
      queries += "$base latest data benchmarks research"
      queries += "$base limitations criticism comparison"
    } else {
      queries += "$base latest verified facts"
    }
    return queries.distinct().take(3)
  }

  private suspend fun synthesizeWithModel(
    question: String,
    queries: List<String>,
    sources: List<SearchResultItem>,
    model: AiModelSpec,
    settings: AppSettings,
  ): LlmResearchSynthesis {
    val targetProvider =
      if (settings.connectionMode == ConnectionMode.BACKEND_PROXY) ProviderType.PROXY
      else model.provider
    val provider = providers[targetProvider] ?: return fallbackSynthesis(question, sources)

    val sourceDigest =
      sources.take(10).mapIndexed { i, s ->
        "[${i + 1}] Title: ${s.title} | Publisher: ${s.publisher} | Date: ${s.publishedTimestamp ?: "unknown"} | URL: ${s.url}\nSnippet: ${s.snippet}\n${s.pageExcerpt?.let { "Excerpt: ${it.take(600)}\n" }.orEmpty()}"
      }.joinToString("\n")

    val prompt =
      """
      Synthesize a rigorous research report answering: "$question"
      Queries run: ${queries.joinToString("; ")}
      Verified retrieved sources:
      $sourceDigest

      Return ONLY valid JSON matching this schema (do not include chain-of-thought or <think> tags):
      {
        "executiveSummary": "...",
        "objectives": ["..."],
        "methodology": "...",
        "keyFindings": ["..."],
        "detailedAnalysis": "...",
        "limitations": ["..."],
        "conclusion": "..."
      }
      Strictly base every claim on the retrieved sources above. Do not invent URLs or facts.
      """.trimIndent()

    val rawBuilder = StringBuilder()
    runCatching {
      provider
        .streamChat(
          AiChatRequest(
            model = model,
            systemPrompt = "You are a rigorous Research Synthesis Engine. Output clean valid JSON strictly grounded in provided sources. Never output internal reasoning tags.",
            turns = listOf(AiTurn(isUser = true, text = prompt)),
            settings = settings,
            temperature = 0.25,
          )
        )
        .collect { evt ->
          if (evt is AiStreamEvent.Delta) rawBuilder.append(evt.text)
        }
    }

    val cleanedRaw =
      rawBuilder
        .toString()
        .replace(Regex("<think>[\\s\\S]*?</think>"), "")
        .trim()
    val jsonSlice =
      cleanedRaw
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
        .let { s ->
          val start = s.indexOf('{')
          val end = s.lastIndexOf('}')
          if (start >= 0 && end > start) s.substring(start, end + 1) else s
        }

    return runCatching { json.decodeFromString<LlmResearchSynthesis>(jsonSlice) }
      .getOrElse {
        if (cleanedRaw.length > 80) {
          LlmResearchSynthesis(
            executiveSummary = cleanedRaw.take(450),
            detailedAnalysis = cleanedRaw,
            conclusion = "Synthesized from ${sources.size} verified web sources.",
          )
        } else {
          fallbackSynthesis(question, sources)
        }
      }
  }

  private fun fallbackSynthesis(question: String, sources: List<SearchResultItem>): LlmResearchSynthesis {
    return LlmResearchSynthesis(
      executiveSummary = "Collected ${sources.size} verified sources addressing \"$question\".",
      detailedAnalysis =
        sources.take(5).joinToString("\n\n") { "**${it.title}** (${it.publisher}): ${it.snippet}" },
      conclusion = "Review the verified references below for primary source details.",
    )
  }
}
