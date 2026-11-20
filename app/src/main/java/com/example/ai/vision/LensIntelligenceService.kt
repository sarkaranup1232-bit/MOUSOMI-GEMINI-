package com.example.ai.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.ai.firebase.FirebaseAiLogicService
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.dto.GeminiContent
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerationConfig
import com.example.ai.provider.dto.GeminiInlineData
import com.example.ai.provider.dto.GeminiPart
import com.example.core.image.ImageProcessor
import com.example.core.network.RetryPolicy
import com.example.core.security.SecurityValidator
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.LensAnalysisResult
import com.example.domain.model.LensAnnotatedComponent
import com.example.domain.model.LensCalloutColor
import com.example.domain.model.LensComparisonResult
import com.example.domain.model.LensMode
import com.example.domain.model.LensReferenceImage
import com.example.domain.model.SearchResultItem
import com.example.domain.model.VerifiedModelCatalog
import com.example.research.search.SearchQueryRequest
import com.example.research.search.SearchService
import com.example.research.search.SearchTopic
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class LensIntelligenceService @Inject constructor(
  private val firebaseAiLogicService: FirebaseAiLogicService,
  private val geminiApiService: GeminiApiService,
  private val searchService: SearchService,
  private val imageProcessor: ImageProcessor,
  private val okHttpClient: OkHttpClient,
  private val securityValidator: SecurityValidator,
  private val json: Json,
) {

  suspend fun analyzeCapturedImage(
    imageFile: File,
    mode: LensMode,
    settings: AppSettings,
    userHint: String = "",
  ): Result<LensAnalysisResult> = coroutineScope {
    val processedResult =
      imageProcessor.processUri(
        uriString = android.net.Uri.fromFile(imageFile).toString(),
        maxDimensionPx = 1400,
        maxBytes = 1_200_000,
      )

    val payload = processedResult.getOrElse {
      val rawBitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(imageFile.absolutePath) }
        ?: return@coroutineScope Result.failure(IllegalStateException("Unable to decode captured image."))
      imageProcessor.compressBitmapPublic(imageFile.absolutePath, rawBitmap, maxDimensionPx = 1400, maxBytes = 1_200_000)
    }

    val prompt = buildAnalysisPrompt(mode, userHint)
    val modelCall =
      invokeVisionModel(
        prompt = prompt,
        imagePayloads = listOf(payload),
        settings = settings,
      )

    val (rawText, modelUsed) = modelCall.getOrElse { err ->
      return@coroutineScope Result.failure(err)
    }

    val captureId = "lens_${UUID.randomUUID()}"
    val baseResult =
      parseLensAnalysisResponse(
        rawText = rawText,
        captureId = captureId,
        capturedFilePath = imageFile.absolutePath,
        mode = mode,
        modelUsed = modelUsed,
      )

    // Discover real external HTTPS web reference images & verified Google search citations
    val queries =
      baseResult.referenceSearchQueries
        .ifEmpty {
          listOfNotNull(
            baseResult.identifiedName.takeIf { it.isNotBlank() && !baseResult.isUncertain },
            baseResult.category.takeIf { it.isNotBlank() && !baseResult.isUncertain }?.let { "${baseResult.identifiedName} $it" },
          )
        }
        .take(2)

    if (queries.isEmpty()) {
      return@coroutineScope Result.success(baseResult)
    }

    val refImagesDeferred = async { fetchWikimediaReferenceImages(queries) }
    val searchDeferred = async {
      runCatching {
        searchService.searchMultipleQueries(
          queries = queries,
          topic = if (mode == LensMode.IDENTIFY) SearchTopic.RESEARCH else SearchTopic.GENERAL,
          settings = settings,
        ).getOrDefault(emptyList())
      }.getOrDefault(emptyList())
    }

    val refImages = refImagesDeferred.await()
    val verifiedSources: List<SearchResultItem> = searchDeferred.await().take(6)

    Result.success(
      baseResult.copy(
        referenceImages = refImages,
        verifiedSources = verifiedSources,
      )
    )
  }

  suspend fun askFollowUp(
    imageFile: File,
    previousAnalysis: LensAnalysisResult,
    question: String,
    settings: AppSettings,
  ): Result<String> {
    val cleanQuestion = question.trim()
    if (cleanQuestion.isEmpty()) {
      return Result.failure(IllegalArgumentException("Follow-up question cannot be empty."))
    }
    val payload =
      imageProcessor.processUri(android.net.Uri.fromFile(imageFile).toString()).getOrElse {
        val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(imageFile.absolutePath) }
          ?: return Result.failure(IllegalStateException("Captured image is no longer available."))
        imageProcessor.compressBitmapPublic(imageFile.absolutePath, bmp)
      }

    val prompt =
      """
      You are Mousomi Lens Visual Assistant answering a user follow-up about the attached captured image.
      Previous identification: ${previousAnalysis.identifiedName} (${previousAnalysis.confidenceLabel})
      Category: ${previousAnalysis.category}
      Previous summary: ${previousAnalysis.purposeAndFunctionality}
      
      User question: "$cleanQuestion"
      
      Respond clearly in well-structured Markdown grounded directly in what is visible in the image. If the image does not show enough detail to answer a detail reliably, state that honestly.
      """.trimIndent()

    return invokeVisionModel(prompt = prompt, imagePayloads = listOf(payload), settings = settings).map { it.first }
  }

  suspend fun compareObjects(
    firstImageFile: File,
    secondImageFile: File,
    firstAnalysis: LensAnalysisResult?,
    settings: AppSettings,
  ): Result<LensComparisonResult> {
    val p1 =
      imageProcessor.processUri(android.net.Uri.fromFile(firstImageFile).toString()).getOrElse {
        val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(firstImageFile.absolutePath) }
          ?: return Result.failure(IllegalStateException("First image could not be decoded."))
        imageProcessor.compressBitmapPublic(firstImageFile.absolutePath, bmp)
      }
    val p2 =
      imageProcessor.processUri(android.net.Uri.fromFile(secondImageFile).toString()).getOrElse {
        val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(secondImageFile.absolutePath) }
          ?: return Result.failure(IllegalStateException("Second image could not be decoded."))
        imageProcessor.compressBitmapPublic(secondImageFile.absolutePath, bmp)
      }

    val prompt =
      """
      Compare the two attached images side by side. Image 1 was previously observed as "${firstAnalysis?.identifiedName ?: "First Object"}".
      Return ONLY strict JSON with no markdown backticks surrounding the JSON:
      {
        "firstObjectName": "Name of object in image 1",
        "secondObjectName": "Name of object in image 2",
        "similarities": ["point 1", "point 2"],
        "differences": ["difference 1", "difference 2"],
        "keySpecifications": ["Visual spec/material comparison 1", "Usage/ergonomic comparison 2"],
        "verdictSummary": "Concise 2-3 sentence summary comparing the two items."
      }
      If either image is unclear, explicitly mention uncertainty.
      """.trimIndent()

    val result = invokeVisionModel(prompt = prompt, imagePayloads = listOf(p1, p2), settings = settings)
    return result.map { (raw, modelUsed) ->
      parseComparisonResponse(
        rawText = raw,
        firstImagePath = firstImageFile.absolutePath,
        secondImagePath = secondImageFile.absolutePath,
        fallbackFirstName = firstAnalysis?.identifiedName ?: "First Capture",
        modelUsed = modelUsed,
      )
    }
  }

  private suspend fun invokeVisionModel(
    prompt: String,
    imagePayloads: List<com.example.domain.model.ProcessedImagePayload>,
    settings: AppSettings,
  ): Result<Pair<String, String>> {
    // 1. Prefer Direct Gemini API key when explicitly configured in DIRECT_DEV_KEYS mode
    if (settings.connectionMode == ConnectionMode.DIRECT_DEV_KEYS && settings.geminiApiKey.isNotBlank()) {
      val modelId =
        settings.hydrogenBackingModelId.ifBlank { VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID }
      val parts =
        buildList {
          imagePayloads.forEach { img ->
            add(GeminiPart(inlineData = GeminiInlineData(mimeType = img.mimeType, data = img.base64Data)))
          }
          add(GeminiPart(text = prompt))
        }
      val apiResult =
        runCatching {
          val resp =
            RetryPolicy.withExponentialBackoff {
              geminiApiService.generateContent(
                model = modelId,
                apiKey = settings.geminiApiKey.trim(),
                request =
                  GeminiGenerateRequest(
                    contents = listOf(GeminiContent(role = "user", parts = parts)),
                    generationConfig = GeminiGenerationConfig(temperature = 0.2),
                  ),
              )
            }
          if (!resp.isSuccessful) error("Gemini HTTP ${resp.code()}")
          val text =
            resp.body()
              ?.candidates
              ?.firstOrNull()
              ?.content
              ?.parts
              ?.mapNotNull { it.text }
              ?.joinToString("\n")
              ?.trim()
              .orEmpty()
          if (text.isBlank()) error("Gemini returned empty vision analysis.")
          text to modelId
        }
      if (apiResult.isSuccess) return apiResult
    }

    // 2. Firebase AI Logic (zero-key production Gemini Vision via Firebase App Check)
    if (firebaseAiLogicService.isAvailable()) {
      val bitmaps =
        imagePayloads.mapNotNull { img ->
          runCatching {
            val bytes = Base64.decode(img.base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          }.getOrNull()
        }
      if (bitmaps.isNotEmpty()) {
        val modelId =
          settings.hydrogenBackingModelId.ifBlank { VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID }
        val fbResult =
          firebaseAiLogicService.generateMultimodalContent(
            prompt = prompt,
            bitmaps = bitmaps,
            temperature = 0.25f,
            modelId = modelId,
          )
        bitmaps.forEach { it.recycle() }
        if (fbResult.isSuccess) {
          return Result.success(fbResult.getOrThrow() to "Firebase AI (${firebaseAiLogicService.resolveTextModel(modelId)})")
        }
        return Result.failure(fbResult.exceptionOrNull() ?: IllegalStateException("Firebase AI Vision failed."))
      }
    }

    return Result.failure(
      IllegalStateException("Vision analysis requires Firebase AI Logic initialization or a configured Gemini API key in Settings.")
    )
  }

  /**
   * Queries Wikipedia / Wikimedia Commons public HTTPS API to obtain real, curated,
   * verifiable reference thumbnails and source URLs matching the identified subject.
   */
  suspend fun fetchWikimediaReferenceImages(queries: List<String>): List<LensReferenceImage> =
    withContext(Dispatchers.IO) {
      val collected = mutableListOf<LensReferenceImage>()
      val seenUrls = HashSet<String>()

      for (rawQuery in queries.take(2)) {
        val clean = rawQuery.trim()
        if (clean.isBlank()) continue
        runCatching {
          val encoded = URLEncoder.encode(clean, "UTF-8")
          val url =
            "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=4&prop=pageimages|info|extracts&inprop=url&exintro=1&explaintext=1&exchars=160&pithumbsize=640&format=json&origin=*"
          val req =
            Request.Builder()
              .url(url)
              .header("User-Agent", "MousomiAI-Lens/1.0 (+Android)")
              .get()
              .build()
          okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use
            val body = resp.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject ?: return@use
            pages.values.forEach { pageEl ->
              val obj = pageEl.jsonObject
              val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
              val thumbUrl = obj["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
              val fullUrl = obj["fullurl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
              val extract = obj["extract"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
              if (
                title.isNotBlank() &&
                thumbUrl.isNotBlank() &&
                securityValidator.validateHttpsResourceUrl(thumbUrl) &&
                securityValidator.validateHttpsResourceUrl(fullUrl) &&
                seenUrls.add(thumbUrl)
              ) {
                val host = runCatching { URI(fullUrl).host?.removePrefix("www.") }.getOrNull().orEmpty().ifBlank { "en.wikipedia.org" }
                collected +=
                  LensReferenceImage(
                    id = "wiki_${collected.size}_${title.hashCode()}",
                    title = title,
                    imageUrl = thumbUrl,
                    sourceWebsite = "Wikipedia ($host)",
                    sourcePageUrl = fullUrl,
                    caption = "Web Reference • $title",
                    snippet = extract,
                  )
              }
            }
          }
        }
      }
      collected.take(5)
    }

  private fun buildAnalysisPrompt(mode: LensMode, userHint: String): String {
    val hintBlock = if (userHint.isNotBlank()) "User note: \"$userHint\"." else ""
    return """
      Analyze the attached image for Mousomi Lens (Active Mode: ${mode.label}).
      Mode Focus: ${mode.instructionFocus}
      $hintBlock
      
      Return ONLY strict valid JSON (do not wrap in prose) matching this exact schema:
      {
        "identifiedName": "Exact concise object, plant, landmark, document, or product name",
        "confidenceLabel": "High | Medium | Low",
        "isUncertain": false,
        "uncertaintyReason": "If blurry, dark, cropped, or ambiguous, explain clearly what is uncertain and set isUncertain=true; never fabricate certainty.",
        "category": "e.g., Botanical Flora, Consumer Hardware, Architecture, Document / Label, Everyday Object",
        "visualCharacteristics": "Colors, shapes, textures, materials, proportions visible in the photo.",
        "purposeAndFunctionality": "What this object is, its primary purpose, and why it is designed this way.",
        "howItWorks": "Clear explanation of internal mechanism, biological structure, or operating principle.",
        "practicalApplications": ["Use case 1", "Use case 2", "Use case 3"],
        "interestingFacts": ["Verified fact 1", "Verified fact 2"],
        "safetyInformation": "Important safety warnings, handling care, electrical/chemical/allergen notes, or 'No specific hazards detected for standard inspection.'",
        "relatedDiscoveries": ["Related topic 1", "Related topic 2", "Related topic 3"],
        "extractedOrTranslatedText": "Only if text is present (especially for Translate/Text mode): verbatim OCR text and translation.",
        "shoppingGuidance": "Only if Shopping mode or product inspection: model traits, quality cues, buying checklist.",
        "referenceSearchQueries": ["Canonical search term 1", "Canonical search term 2"],
        "components": [
          {
            "number": 1,
            "title": "Short part or feature title",
            "shortExplanation": "One-line callout summary",
            "detailedExplanation": "2-3 sentence explanation of this visible component.",
            "colorCategory": "RED | GREEN | BLUE | PURPLE | CYAN",
            "normX": 0.50,
            "normY": 0.45,
            "normLeft": 0.35,
            "normTop": 0.30,
            "normRight": 0.65,
            "normBottom": 0.60
          }
        ]
      }
      
      Component Color Category rules (use diverse relevant colors across 3 to 5 visible parts):
      - RED: Important warnings, delicate/hot/electrical/hazard points & critical components
      - GREEN: Positive identification marks, healthy/confirmed key features
      - BLUE: Technical mechanisms, structural or engineering explanations
      - PURPLE: Additional interesting insights, origin, or secondary details
      - CYAN: Primary highlighted object or focal pointers
      
      Coordinates normX, normY, normLeft, normTop, normRight, normBottom MUST be normalized floats between 0.05 and 0.95 relative to the captured image.
    """.trimIndent()
  }

  fun parseLensAnalysisResponse(
    rawText: String,
    captureId: String,
    capturedFilePath: String,
    mode: LensMode,
    modelUsed: String,
  ): LensAnalysisResult {
    val cleanJson = extractJsonObjectString(rawText)
    if (cleanJson != null) {
      val parsed =
        runCatching {
          val obj = json.parseToJsonElement(cleanJson).jsonObject
          val identifiedName = obj.str("identifiedName").ifBlank { "Visual Subject" }
          val isUncertain = obj["isUncertain"]?.jsonPrimitive?.booleanOrNull ?: false
          val confidence = obj.str("confidenceLabel").ifBlank { if (isUncertain) "Low" else "Medium" }
          val components =
            obj["components"]?.jsonArray?.mapIndexedNotNull { idx, elem ->
              parseComponent(idx + 1, elem.jsonObject)
            }.orEmpty()

          LensAnalysisResult(
            captureId = captureId,
            capturedFilePath = capturedFilePath,
            mode = mode,
            identifiedName = identifiedName,
            confidenceLabel = confidence,
            isUncertain = isUncertain,
            uncertaintyReason = obj.str("uncertaintyReason"),
            category = obj.str("category"),
            visualCharacteristics = obj.str("visualCharacteristics"),
            purposeAndFunctionality = obj.str("purposeAndFunctionality"),
            howItWorks = obj.str("howItWorks"),
            practicalApplications = obj.strList("practicalApplications"),
            interestingFacts = obj.strList("interestingFacts"),
            safetyInformation = obj.str("safetyInformation"),
            relatedDiscoveries = obj.strList("relatedDiscoveries"),
            extractedOrTranslatedText = obj.str("extractedOrTranslatedText"),
            shoppingGuidance = obj.str("shoppingGuidance"),
            components = components,
            referenceSearchQueries = obj.strList("referenceSearchQueries"),
            modelUsed = modelUsed,
          )
        }.getOrNull()
      if (parsed != null) return parsed
    }

    // Honest unstructured fallback if model returned prose instead of strict JSON
    return LensAnalysisResult(
      captureId = captureId,
      capturedFilePath = capturedFilePath,
      mode = mode,
      identifiedName = "Visual Analysis",
      confidenceLabel = "Medium",
      isUncertain = rawText.contains("uncertain", ignoreCase = true) || rawText.contains("blurry", ignoreCase = true),
      purposeAndFunctionality = rawText.trim(),
      components =
        listOf(
          LensAnnotatedComponent(
            id = "comp_1",
            number = 1,
            title = "Primary Subject",
            shortExplanation = "Main area analyzed in frame",
            detailedExplanation = rawText.trim().take(280),
            colorCategory = LensCalloutColor.CYAN,
            normX = 0.5f,
            normY = 0.5f,
            normLeft = 0.25f,
            normTop = 0.25f,
            normRight = 0.75f,
            normBottom = 0.75f,
          )
        ),
      modelUsed = modelUsed,
    )
  }

  fun parseComparisonResponse(
    rawText: String,
    firstImagePath: String,
    secondImagePath: String,
    fallbackFirstName: String,
    modelUsed: String,
  ): LensComparisonResult {
    val cleanJson = extractJsonObjectString(rawText)
    if (cleanJson != null) {
      val parsed =
        runCatching {
          val obj = json.parseToJsonElement(cleanJson).jsonObject
          LensComparisonResult(
            firstImagePath = firstImagePath,
            secondImagePath = secondImagePath,
            firstObjectName = obj.str("firstObjectName").ifBlank { fallbackFirstName },
            secondObjectName = obj.str("secondObjectName").ifBlank { "Second Object" },
            similarities = obj.strList("similarities"),
            differences = obj.strList("differences"),
            keySpecifications = obj.strList("keySpecifications"),
            verdictSummary = obj.str("verdictSummary"),
            modelUsed = modelUsed,
          )
        }.getOrNull()
      if (parsed != null) return parsed
    }
    return LensComparisonResult(
      firstImagePath = firstImagePath,
      secondImagePath = secondImagePath,
      firstObjectName = fallbackFirstName,
      secondObjectName = "Second Object",
      similarities = emptyList(),
      differences = emptyList(),
      keySpecifications = emptyList(),
      verdictSummary = rawText.trim(),
      modelUsed = modelUsed,
    )
  }

  private fun parseComponent(defaultNum: Int, obj: JsonObject): LensAnnotatedComponent? {
    val title = obj.str("title").ifBlank { return null }
    val num = obj["number"]?.jsonPrimitive?.intOrNull ?: defaultNum
    val colorStr = obj.str("colorCategory").uppercase()
    val color =
      LensCalloutColor.entries.firstOrNull { it.name == colorStr } ?: when (defaultNum % 5) {
        1 -> LensCalloutColor.CYAN
        2 -> LensCalloutColor.GREEN
        3 -> LensCalloutColor.BLUE
        4 -> LensCalloutColor.PURPLE
        else -> LensCalloutColor.RED
      }
    val nx = (obj["normX"]?.jsonPrimitive?.floatOrNull ?: 0.5f).coerceIn(0.06f, 0.94f)
    val ny = (obj["normY"]?.jsonPrimitive?.floatOrNull ?: 0.5f).coerceIn(0.06f, 0.94f)
    val left = obj["normLeft"]?.jsonPrimitive?.floatOrNull?.coerceIn(0.02f, 0.95f)
    val top = obj["normTop"]?.jsonPrimitive?.floatOrNull?.coerceIn(0.02f, 0.95f)
    val right = obj["normRight"]?.jsonPrimitive?.floatOrNull?.coerceIn(0.05f, 0.98f)
    val bottom = obj["normBottom"]?.jsonPrimitive?.floatOrNull?.coerceIn(0.05f, 0.98f)

    return LensAnnotatedComponent(
      id = "comp_$num",
      number = num,
      title = title,
      shortExplanation = obj.str("shortExplanation"),
      detailedExplanation = obj.str("detailedExplanation"),
      colorCategory = color,
      normX = nx,
      normY = ny,
      normLeft = if (left != null && right != null && right > left) left else null,
      normTop = if (top != null && bottom != null && bottom > top) top else null,
      normRight = if (left != null && right != null && right > left) right else null,
      normBottom = if (top != null && bottom != null && bottom > top) bottom else null,
    )
  }

  private fun extractJsonObjectString(raw: String): String? {
    val trimmed = raw.trim()
    val noFence =
      if (trimmed.startsWith("```")) {
        trimmed
          .removePrefix("```json")
          .removePrefix("```JSON")
          .removePrefix("```")
          .substringBeforeLast("```")
          .trim()
      } else {
        trimmed
      }
    val start = noFence.indexOf('{')
    val end = noFence.lastIndexOf('}')
    return if (start >= 0 && end > start) noFence.substring(start, end + 1) else null
  }

  private fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

  private fun JsonObject.strList(key: String): List<String> {
    val el: JsonElement = this[key] ?: return emptyList()
    val arr: JsonArray = el as? JsonArray ?: return emptyList()
    return arr.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
  }
}
