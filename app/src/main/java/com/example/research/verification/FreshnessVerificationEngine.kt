package com.example.research.verification

import com.example.domain.model.ConflictingClaim
import com.example.domain.model.OperatingMode
import com.example.domain.model.SearchResultItem
import com.example.research.search.SearchTopic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreshnessVerificationEngine @Inject constructor() {

  fun isTimeSensitive(
    query: String,
    operatingMode: OperatingMode = OperatingMode.NORMAL_CHAT,
  ): Boolean {
    if (operatingMode == OperatingMode.LATEST_NEWS ||
      operatingMode == OperatingMode.GOOGLE_AI_SEARCH ||
      operatingMode == OperatingMode.DEEP_RESEARCH
    ) {
      return true
    }
    val q = query.lowercase(Locale.US)
    return timeSensitiveMarkers.any { it in q }
  }

  fun isLatestNewsQuery(
    query: String,
    operatingMode: OperatingMode = OperatingMode.NORMAL_CHAT,
  ): Boolean {
    if (operatingMode == OperatingMode.LATEST_NEWS) return true
    val q = query.lowercase(Locale.US)
    return newsMarkers.any { it in q }
  }

  fun detectTopic(
    query: String,
    operatingMode: OperatingMode = OperatingMode.NORMAL_CHAT,
  ): SearchTopic {
    val q = query.lowercase(Locale.US)
    return when {
      isLatestNewsQuery(query, operatingMode) -> SearchTopic.NEWS
      "research" in q || "paper" in q || "study" in q -> SearchTopic.RESEARCH
      "android" in q || "kotlin" in q || "ai" in q || "release" in q || "version" in q -> SearchTopic.TECHNOLOGY
      else -> SearchTopic.GENERAL
    }
  }

  fun buildVerificationInstruction(
    query: String,
    isTimeSensitive: Boolean,
    isLatestNews: Boolean,
    searchProvider: String,
    answerModelLabel: String,
    sources: List<SearchResultItem>,
    conflictingClaims: List<ConflictingClaim>,
  ): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return buildString {
      appendLine("FRESHNESS & SOURCE VERIFICATION RULES (Today's device date: $today):")
      appendLine("- Live Search executed by: $searchProvider")
      appendLine("- Answer synthesis model: $answerModelLabel")
      if (isTimeSensitive || isLatestNews) {
        appendLine("- This query is TIME-SENSITIVE. Treat ungrounded model memory as unverified; rely ONLY on the retrieved web sources below for current officeholders, news, prices, schedules, trends, or recent releases.")
        appendLine("- Explicitly distinguish each source's publish date (if present) from the actual event date mentioned in the article. If a source lacks a publish date, state that its publish date could not be verified rather than guessing.")
      }
      if (conflictingClaims.isNotEmpty()) {
        appendLine("- CONFLICTS DETECTED ACROSS SOURCES (must be disclosed explicitly in your answer):")
        conflictingClaims.forEach { c ->
          appendLine("  * ${c.topic}: ${c.sourceA} reports '${c.claimA.take(140)}' vs ${c.sourceB} reports '${c.claimB.take(140)}'")
        }
      }
      if (sources.isEmpty()) {
        appendLine("- WARNING: 0 verified web sources were returned for \"$query\". Clearly tell the user no live verified sources were retrieved and do NOT fabricate URLs, dates, or current claims.")
      } else {
        appendLine("- Cite claims inline using source numbers like [1], [2] matching ONLY the verified sources below:")
        sources.forEachIndexed { idx, s ->
          val num = if (s.sourceNumber > 0) s.sourceNumber else idx + 1
          val dateLabel = s.publishedTimestamp?.takeIf { it.isNotBlank() } ?: "publish date unverified"
          appendLine("[$num] ${s.title} | Publisher: ${s.publisher} (${s.sourceDomain}) | Published: $dateLabel | URL: ${s.url}")
          if (s.snippet.isNotBlank()) {
            appendLine("    Snippet: ${s.snippet.take(420)}")
          }
          if (!s.pageExcerpt.isNullOrBlank()) {
            appendLine("    Page Excerpt: ${s.pageExcerpt.take(550)}")
          }
        }
      }
    }
  }

  companion object {
    private val timeSensitiveMarkers =
      listOf(
        "today", "today's", "latest", "breaking", "current ", "currently",
        "right now", "this week", "this month", "recent ", "2026", "2025",
        "chief minister", "cm of ", "prime minister", "president of ", "governor of ",
        "ceo of ", "who is the current", "live score", "match score", "weather",
        "youtube trends", "trending on youtube", "trending videos", "viral today",
        "stock price", "share price", "exchange rate", "usd to", "bitcoin price",
        "gold price", "petrol price", "release date", "latest version", "current version",
        "election", "update ", "news",
        "আজকের", "সর্বশেষ", "খবর", "মুখ্যমন্ত্রী", "প্রধানমন্ত্রী",
        "ताज़ा", "समाचार", "मुख्यमंत्री", "प्रधानमंत्री",
      )

    private val newsMarkers =
      listOf(
        "news", "breaking", "headline", "headlines", "youtube trends", "trending on youtube",
        "trending today", "top stories", "খবর", "সর্বশেষ সংবাদ", "ताज़ा समाचार", "समाचार",
      )
  }
}
