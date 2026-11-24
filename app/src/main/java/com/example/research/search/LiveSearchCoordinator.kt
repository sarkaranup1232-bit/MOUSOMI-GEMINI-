package com.example.research.search

import com.example.domain.model.AppSettings
import com.example.domain.model.OperatingMode
import com.example.domain.model.ProviderType
import com.example.domain.model.ResearchActivitySnapshot
import com.example.domain.model.ResearchStageId
import com.example.domain.model.ResearchStageItem
import com.example.domain.model.ResearchStageStatus
import com.example.domain.model.RoutingDecision
import com.example.domain.model.SearchResultItem
import com.example.research.verification.FreshnessVerificationEngine
import com.example.research.verification.SourceVerifier
import javax.inject.Inject
import javax.inject.Singleton

data class CoordinatedSearchOutcome(
  val verifiedSources: List<SearchResultItem>,
  val rawReturnedCount: Int,
  val searchProvider: String,
  val answerModelLabel: String,
  val promptInstructionContext: String,
  val telemetrySnapshot: ResearchActivitySnapshot,
  val isLatestNews: Boolean,
)

@Singleton
class LiveSearchCoordinator @Inject constructor(
  private val searchService: SearchService,
  private val sourceVerifier: SourceVerifier,
  private val freshnessVerificationEngine: FreshnessVerificationEngine,
) {

  suspend fun coordinateSearch(
    query: String,
    decision: RoutingDecision,
    operatingMode: OperatingMode,
    settings: AppSettings,
  ): Result<CoordinatedSearchOutcome> {
    val cleanQuery = query.trim()
    val isTimeSensitive = freshnessVerificationEngine.isTimeSensitive(cleanQuery, operatingMode)
    val isLatestNews = freshnessVerificationEngine.isLatestNewsQuery(cleanQuery, operatingMode)
    val topic = freshnessVerificationEngine.detectTopic(cleanQuery, operatingMode)
    val answerModelLabel =
      "${decision.selectedModel.displayName} (${decision.selectedModel.id})"

    val searchRespResult =
      searchService.search(
        SearchQueryRequest(
          query = cleanQuery,
          topic = topic,
          page = 1,
          pageSize = 8,
          includePageContent = true,
          bypassCache = isTimeSensitive,
        ),
        settings,
      )

    if (searchRespResult.isFailure) {
      return Result.failure(
        searchRespResult.exceptionOrNull()
          ?: IllegalStateException("Live search failed.")
      )
    }

    val rawResponse = searchRespResult.getOrThrow()
    val verifiedBundle = sourceVerifier.verifyAndCompare(rawResponse.results)
    val numberedSources =
      verifiedBundle.uniqueVerifiedSources.mapIndexed { idx, item ->
        item.copy(
          sourceNumber = idx + 1,
          isLatestNews = isLatestNews,
          faviconUrl =
            item.faviconUrl
              ?: "https://www.google.com/s2/favicons?domain=${item.sourceDomain}&sz=64",
        )
      }

    val searchProviderLabel =
      if (decision.selectedModel.provider == ProviderType.GROQ &&
        !decision.selectedModel.capabilities.supportsGoogleSearchGrounding
      ) {
        "${rawResponse.providerUsed} → ${decision.selectedModel.displayName}"
      } else {
        rawResponse.providerUsed
      }

    val promptContext =
      freshnessVerificationEngine.buildVerificationInstruction(
        query = cleanQuery,
        isTimeSensitive = isTimeSensitive,
        isLatestNews = isLatestNews,
        searchProvider = rawResponse.providerUsed,
        answerModelLabel = answerModelLabel,
        sources = numberedSources,
        conflictingClaims = verifiedBundle.conflictingClaims,
      )

    val telemetry =
      ResearchActivitySnapshot(
        query = cleanQuery,
        isActive = false,
        currentHeadline =
          if (numberedSources.isEmpty()) "Search returned 0 verified sources"
          else "Retrieved ${numberedSources.size} verified web sources",
        plannedQueries = listOf(cleanQuery),
        stages =
          listOf(
            ResearchStageItem(ResearchStageId.UNDERSTAND, ResearchStageStatus.COMPLETED, topic.label),
            ResearchStageItem(
              ResearchStageId.LIVE_SEARCH,
              if (numberedSources.isEmpty()) ResearchStageStatus.FAILED else ResearchStageStatus.COMPLETED,
              "${rawResponse.results.size} returned by ${rawResponse.providerUsed}",
            ),
            ResearchStageItem(
              ResearchStageId.CROSS_VERIFICATION,
              if (numberedSources.isEmpty()) ResearchStageStatus.FAILED else ResearchStageStatus.COMPLETED,
              "${numberedSources.size} verified HTTPS sources",
            ),
          ),
        retrievedSources = numberedSources,
        searchProvider = searchProviderLabel,
        answerModel = answerModelLabel,
        sourcesReturnedCount = rawResponse.results.size,
        sourcesUsedCount = 0,
        isFailed = numberedSources.isEmpty(),
        errorMessage =
          if (numberedSources.isEmpty()) "No verified web sources were returned for this query." else null,
      )

    return Result.success(
      CoordinatedSearchOutcome(
        verifiedSources = numberedSources,
        rawReturnedCount = rawResponse.results.size,
        searchProvider = searchProviderLabel,
        answerModelLabel = answerModelLabel,
        promptInstructionContext = promptContext,
        telemetrySnapshot = telemetry,
        isLatestNews = isLatestNews,
      )
    )
  }

  fun markUsedCitations(
    answerText: String,
    sources: List<SearchResultItem>,
  ): List<SearchResultItem> {
    if (sources.isEmpty()) return emptyList()
    val citedNums =
      Regex("\\[(?:#)?(\\d+)\\]")
        .findAll(answerText)
        .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
        .toSet()

    return sources.mapIndexed { idx, src ->
      val num = if (src.sourceNumber > 0) src.sourceNumber else idx + 1
      val domainMentioned =
        src.sourceDomain.isNotBlank() && answerText.contains(src.sourceDomain, ignoreCase = true)
      val urlMentioned = answerText.contains(src.url, ignoreCase = true)
      src.copy(
        sourceNumber = num,
        usedInAnswer = (num in citedNums) || domainMentioned || urlMentioned,
      )
    }
  }
}
