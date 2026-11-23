package com.example.domain.router

import com.example.analytics.parser.TabularDataParser
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.PendingMediaItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeIntelligenceRouter @Inject constructor(
  private val tabularDataParser: TabularDataParser,
) {
  fun classifyIntent(
    prompt: String,
    hasImages: Boolean,
    attachments: List<PendingMediaItem>,
    deepResearchRequested: Boolean = false,
    explicitImageEditRequested: Boolean = false,
  ): IntelligenceIntent {
    val q = prompt.lowercase().trim()

    if (attachments.any { it.kind == MediaAttachmentKind.VIDEO }) {
      return IntelligenceIntent.VIDEO_INTELLIGENCE
    }
    if (attachments.any { it.kind == MediaAttachmentKind.AUDIO }) {
      return IntelligenceIntent.AUDIO_INTELLIGENCE
    }

    val docs = attachments.filter { it.kind == MediaAttachmentKind.DOCUMENT }
    if (docs.isNotEmpty()) {
      val hasSpreadsheet =
        docs.any {
          it.displayName.endsWith(".csv", ignoreCase = true) ||
            it.displayName.endsWith(".xlsx", ignoreCase = true)
        }
      return when {
        chartKeywords.any { it in q } -> IntelligenceIntent.CHART_VISUALIZATION
        hasSpreadsheet || dataAnalyticsKeywords.any { it in q } -> IntelligenceIntent.DATA_ANALYSIS
        else -> IntelligenceIntent.DOCUMENT_INTELLIGENCE
      }
    }

    if (hasImages || attachments.any { it.kind == MediaAttachmentKind.PHOTO }) {
      if (imageToVideoKeywords.any { it in q }) {
        return IntelligenceIntent.IMAGE_TO_VIDEO
      }
      if (explicitImageEditRequested || imageEditKeywords.any { it in q }) {
        return IntelligenceIntent.IMAGE_EDITING
      }
      return IntelligenceIntent.PHOTO_INTELLIGENCE
    }

    if (isMultiTaskCommand(q)) {
      return IntelligenceIntent.MULTI_TASK
    }

    if (isAlarmOrReminderCommand(q)) {
      return IntelligenceIntent.ALARM_REMINDER
    }

    if (isDeviceActionCommand(q)) {
      return IntelligenceIntent.DEVICE_ACTION
    }

    if (explicitImageEditRequested) {
      return IntelligenceIntent.IMAGE_EDITING
    }

    if (textToVideoPhrases.any { it in q }) {
      return IntelligenceIntent.TEXT_TO_VIDEO
    }

    if (isMusicGenerationRequest(q)) {
      return IntelligenceIntent.MUSIC_GENERATION
    }

    if (imageGenerationPhrases.any { it in q }) {
      return IntelligenceIntent.IMAGE_GENERATION
    }

    if (deepResearchRequested || deepResearchPhrases.any { it in q }) {
      return IntelligenceIntent.DEEP_RESEARCH
    }

    val hasInlineTable = tabularDataParser.parseInlineTabularText("Prompt Dataset", prompt) != null
    if (hasInlineTable && chartKeywords.any { it in q }) {
      return IntelligenceIntent.CHART_VISUALIZATION
    }
    if (hasInlineTable || (dataAnalyticsKeywords.any { it in q } && ("dataset" in q || "csv" in q || "excel" in q || "data" in q))) {
      return IntelligenceIntent.DATA_ANALYSIS
    }
    if (chartKeywords.any { it in q } && ("chart" in q || "plot" in q || "graph" in q)) {
      return IntelligenceIntent.CHART_VISUALIZATION
    }

    if (mapsKeywords.any { it in q }) {
      return IntelligenceIntent.MAPS_SEARCH
    }

    if (liveSearchKeywords.any { it in q }) {
      return IntelligenceIntent.LIVE_SEARCH
    }

    if (isCasualGreeting(q)) {
      return IntelligenceIntent.DIRECT_CHAT
    }

    return IntelligenceIntent.NORMAL_AI
  }

  private fun isMultiTaskCommand(q: String): Boolean {
    val clauses =
      q.split(Regex("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+(?:then\\s+)?|\\s+then\\s+|\\s*;\\s*"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (clauses.size < 2) return false
    val deviceOrScheduleClauses = clauses.count { isDeviceActionCommand(it) || isAlarmOrReminderCommand(it) }
    val allActionClauses =
      clauses.count { clause ->
        isDeviceActionCommand(clause) ||
          isAlarmOrReminderCommand(clause) ||
          imageGenerationPhrases.any { it in clause } ||
          deepResearchPhrases.any { it in clause }
      }
    return deviceOrScheduleClauses >= 1 && allActionClauses >= 2
  }

  private fun isAlarmOrReminderCommand(q: String): Boolean {
    val alarmTriggers =
      listOf(
        "set an alarm",
        "set alarm",
        "wake me",
        "set a reminder",
        "set reminder",
        "remind me",
        "start a ",
        "start timer",
        "set a timer",
        "set timer",
        "upcoming alarms",
        "upcoming reminders",
        "show my upcoming alarms",
        "show my alarms",
        "show my reminders",
        "cancel my ",
        "cancel alarm",
        "cancel reminder",
        "change tomorrow",
      )
    return alarmTriggers.any { it in q } &&
      ("alarm" in q || "reminder" in q || "remind " in q || "timer" in q || "wake me" in q)
  }

  private fun isDeviceActionCommand(q: String): Boolean {
    val directPrefixes = listOf("open ", "launch ", "search app", "find app", "copy ", "show installed apps")
    if (directPrefixes.any { q.startsWith(it) }) {
      return true
    }
    val deviceKeywords =
      listOf(
        "open youtube",
        "open chrome",
        "open whatsapp",
        "open gallery",
        "open camera",
        "open settings",
        "bluetooth settings",
        "wi-fi settings",
        "wifi settings",
        "open play store",
        "open calculator",
        "installed apps",
        "navigate to settings",
      )
    return deviceKeywords.any { it in q }
  }

  private fun isMusicGenerationRequest(q: String): Boolean {
    if (musicGenerationPhrases.any { it in q }) return true
    val composeVerb = listOf("compose ", "generate ", "create ", "make ", "produce ")
    val audioNoun = listOf("music", "song", "beat", "melody", "track", "instrumental", "soundtrack", "jingle")
    return composeVerb.any { it in q } && audioNoun.any { it in q }
  }

  private fun isCasualGreeting(q: String): Boolean {
    if (q.length > 48) return false
    val cleaned = q.replace(Regex("[^a-zA-Z0-9\\s\\u0980-\\u09FF\\u0900-\\u097F]"), "").trim()
    return greetingPhrases.any { cleaned == it || cleaned.startsWith("$it ") }
  }

  companion object {
    private val greetingPhrases =
      listOf(
        "hi", "hello", "hey", "good morning", "good evening", "good night",
        "how are you", "thanks", "thank you", "হ্যালো", "হাই", "ধন্যবাদ", "नमस्ते",
        "nomoshkar", "kemon acho",
      )

    private val imageGenerationPhrases =
      listOf(
        "generate image", "generate an image", "create image", "create an image",
        "make an image", "draw an image", "draw a picture", "generate a picture",
        "create a picture", "generate artwork", "create artwork", "generate a photo",
        "create a logo", "generate a logo", "design a poster", "generate wallpaper",
        "create wallpaper", "chobi banao", "chobi toiri", "ছবি তৈরি কর", "ছবি বানাও", "चित्र बनाओ",
      )

    private val imageEditKeywords =
      listOf(
        "edit this image", "edit image", "change background", "change the background",
        "remove background", "remove object", "remove the", "add text to",
        "enhance this photo", "enhance image", "replace the", "recolor",
        "retouch", "inpaint", "erase ", "make it ", "convert style",
      )

    private val deepResearchPhrases =
      listOf(
        "deep research", "deep reasearch", "research in detail", "comprehensive report", "in-depth research",
        "in detail research", "detailed research", "literature review", "deep dive",
        "research report", "বিস্তারিত গবেষণা", "গভীর গবেষণা", "gobhir gobeshona",
      )

    private val liveSearchKeywords =
      listOf(
        "today's", "today ", "latest ", "current ", "news", "breaking", "recent ",
        "this week", "this month", "search ", "serch ", "look up", "find today", "up to date",
        "live price", "weather", "release date", "2026", "আজকের", "সর্বশেষ", "খবর", "khobor",
        "ताज़ा", "समाचार",
      )

    private val chartKeywords =
      listOf(
        "chart", "plot", "graph", "bar chart", "line chart", "pie chart", "area chart",
        "scatter plot", "time series", "visualize",
      )

    private val dataAnalyticsKeywords =
      listOf(
        "analyze my excel", "analyze this csv", "analyze data", "data analysis",
        "statistics", "mean", "median", "correlation", "trend", "duplicate", "missing value",
      )

    private val imageToVideoKeywords =
      listOf(
        "animate this", "animate image", "animate photo", "animate the photo",
        "turn this image into a video", "turn image into video", "create a video from this",
        "make a video from this", "video from this image", "image to video", "image-to-video",
      )

    private val textToVideoPhrases =
      listOf(
        "generate video", "generate a video", "create video", "create a video",
        "make a video", "text to video", "text-to-video", "veo video",
      )

    private val musicGenerationPhrases =
      listOf(
        "generate music", "generate a song", "create music", "create a song",
        "compose music", "compose a song", "make music", "generate a beat",
        "create a beat", "compose a beat", "ai music", "lyria", "gaan banao", "গান বানাও",
      )

    private val mapsKeywords =
      listOf(
        "nearby ", "near me", "directions to", "route to", "google maps",
        "restaurants near", "restaurants in ", "hotels near", "hotels in ",
        "cafes near", "hospital near", "places to visit in", "how far is",
        "distance from ", "map of ", "kothay ache", "কাছাকাছি ",
      )
  }
}
