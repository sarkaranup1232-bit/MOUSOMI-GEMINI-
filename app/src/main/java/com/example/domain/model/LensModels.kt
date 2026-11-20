package com.example.domain.model

import kotlinx.serialization.Serializable

enum class LensMode(
  val label: String,
  val hintText: String,
  val instructionFocus: String,
) {
  SEARCH(
    label = "Search",
    hintText = "Point your camera at anything — Plants, objects, text, places",
    instructionFocus = "Identify the primary object, landmark, brand, or subject and explain it thoroughly.",
  ),
  TRANSLATE(
    label = "Translate",
    hintText = "Point at signs, menus, labels, or printed text to translate",
    instructionFocus = "Detect all foreign or printed text visible in the image, transcribe it verbatim, identify the source language, and translate it clearly into English.",
  ),
  TEXT(
    label = "Text",
    hintText = "Aim at documents, notes, equations, or labels to extract",
    instructionFocus = "Perform accurate OCR transcription of all visible text, formatting tables, lists, numbers, or equations clearly and explaining key takeaways.",
  ),
  IDENTIFY(
    label = "Identify",
    hintText = "Identify plants, animals, electronics, tools, or parts",
    instructionFocus = "Provide botanical, biological, technical, or mechanical identification with detailed parts breakdown and safety/care information.",
  ),
  SHOPPING(
    label = "Shopping",
    hintText = "Point at products, packaging, or gear to inspect details",
    instructionFocus = "Identify the product type, visible model cues, materials, key specifications, typical use cases, and what buyers should check.",
  ),
}

enum class LensCalloutColor(
  val hexArgb: Long,
  val semanticRole: String,
) {
  RED(0xFFEF4444L, "Critical / Warning"),
  GREEN(0xFF22C55EL, "Confirmed Feature"),
  BLUE(0xFF3B82F6L, "Technical Detail"),
  PURPLE(0xFFA855F7L, "Additional Insight"),
  CYAN(0xFF06B6D4L, "Highlighted Focus"),
}

@Serializable
data class LensAnnotatedComponent(
  val id: String,
  val number: Int,
  val title: String,
  val shortExplanation: String,
  val detailedExplanation: String,
  val colorCategory: LensCalloutColor,
  val normX: Float,
  val normY: Float,
  val normLeft: Float? = null,
  val normTop: Float? = null,
  val normRight: Float? = null,
  val normBottom: Float? = null,
)

data class LensDetectedBox(
  val trackingId: Int?,
  val label: String,
  val normLeft: Float,
  val normTop: Float,
  val normRight: Float,
  val normBottom: Float,
) {
  val centerX: Float get() = (normLeft + normRight) * 0.5f
  val centerY: Float get() = (normTop + normBottom) * 0.5f
  val area: Float get() = (normRight - normLeft).coerceAtLeast(0f) * (normBottom - normTop).coerceAtLeast(0f)
}

@Serializable
data class LensReferenceImage(
  val id: String,
  val title: String,
  val imageUrl: String,
  val sourceWebsite: String,
  val sourcePageUrl: String,
  val caption: String,
  val snippet: String = "",
)

@Serializable
data class LensAnalysisResult(
  val captureId: String,
  val capturedFilePath: String,
  val mode: LensMode,
  val identifiedName: String,
  val confidenceLabel: String,
  val isUncertain: Boolean = false,
  val uncertaintyReason: String = "",
  val category: String = "",
  val visualCharacteristics: String = "",
  val purposeAndFunctionality: String = "",
  val howItWorks: String = "",
  val practicalApplications: List<String> = emptyList(),
  val interestingFacts: List<String> = emptyList(),
  val safetyInformation: String = "",
  val relatedDiscoveries: List<String> = emptyList(),
  val extractedOrTranslatedText: String = "",
  val shoppingGuidance: String = "",
  val components: List<LensAnnotatedComponent> = emptyList(),
  val referenceSearchQueries: List<String> = emptyList(),
  val referenceImages: List<LensReferenceImage> = emptyList(),
  val verifiedSources: List<SearchResultItem> = emptyList(),
  val modelUsed: String = "",
  val capturedAtMs: Long = System.currentTimeMillis(),
) {
  fun toChatSummaryMarkdown(): String = buildString {
    appendLine("## Mousomi Lens Identification: $identifiedName")
    if (category.isNotBlank()) {
      appendLine("**Category:** $category • **Confidence:** $confidenceLabel")
    } else {
      appendLine("**Confidence:** $confidenceLabel")
    }
    if (isUncertain && uncertaintyReason.isNotBlank()) {
      appendLine("> **Uncertainty Note:** $uncertaintyReason")
    }
    appendLine()
    if (extractedOrTranslatedText.isNotBlank()) {
      appendLine("### Extracted / Translated Text")
      appendLine(extractedOrTranslatedText)
      appendLine()
    }
    if (visualCharacteristics.isNotBlank()) {
      appendLine("### Visual Characteristics")
      appendLine(visualCharacteristics)
      appendLine()
    }
    if (purposeAndFunctionality.isNotBlank()) {
      appendLine("### Purpose & Functionality")
      appendLine(purposeAndFunctionality)
      appendLine()
    }
    if (howItWorks.isNotBlank()) {
      appendLine("### How It Works")
      appendLine(howItWorks)
      appendLine()
    }
    if (components.isNotEmpty()) {
      appendLine("### Highlighted Components")
      components.forEach { comp ->
        appendLine("- **${comp.number}. ${comp.title}** (${comp.colorCategory.semanticRole}): ${comp.detailedExplanation.ifBlank { comp.shortExplanation }}")
      }
      appendLine()
    }
    if (practicalApplications.isNotEmpty()) {
      appendLine("### Practical Applications")
      practicalApplications.forEach { appendLine("- $it") }
      appendLine()
    }
    if (safetyInformation.isNotBlank()) {
      appendLine("### Safety Information")
      appendLine(safetyInformation)
      appendLine()
    }
    if (interestingFacts.isNotEmpty()) {
      appendLine("### Key Facts")
      interestingFacts.forEach { appendLine("- $it") }
    }
  }.trim()
}

data class LensComparisonResult(
  val firstImagePath: String,
  val secondImagePath: String,
  val firstObjectName: String,
  val secondObjectName: String,
  val similarities: List<String>,
  val differences: List<String>,
  val keySpecifications: List<String>,
  val verdictSummary: String,
  val modelUsed: String,
)

data class LensFollowUpTurn(
  val id: String,
  val question: String,
  val answerMarkdown: String,
  val timestampMs: Long = System.currentTimeMillis(),
)

sealed interface LensAutoScanState {
  data object Disabled : LensAutoScanState

  data object Scanning : LensAutoScanState

  data class Unstable(val reason: String) : LensAutoScanState

  data class CountingDown(
    val secondsRemaining: Int,
    val progress: Float,
    val targetLabel: String,
  ) : LensAutoScanState

  data class Triggered(val targetLabel: String) : LensAutoScanState
}
