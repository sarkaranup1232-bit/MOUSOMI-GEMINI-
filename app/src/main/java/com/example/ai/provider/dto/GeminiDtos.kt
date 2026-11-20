package com.example.ai.provider.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GeminiGenerateRequest(
  val contents: List<GeminiContent>,
  val systemInstruction: GeminiContent? = null,
  val generationConfig: GeminiGenerationConfig? = null,
  val tools: List<GeminiTool>? = null,
)

@Serializable
data class GeminiTool(
  val google_search: JsonObject? = null,
  val google_maps: JsonObject? = null,
)

@Serializable
data class GeminiContent(
  val role: String? = null,
  val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
  val text: String? = null,
  val inlineData: GeminiInlineData? = null,
)

@Serializable
data class GeminiInlineData(
  val mimeType: String,
  val data: String,
)

@Serializable
data class GeminiGenerationConfig(
  val temperature: Double = 0.7,
  val responseModalities: List<String>? = null,
)

@Serializable
data class GeminiGenerateResponse(
  val candidates: List<GeminiCandidate> = emptyList(),
  val usageMetadata: GeminiUsageMetadata? = null,
)

@Serializable
data class GeminiCandidate(
  val content: GeminiContent? = null,
  val finishReason: String? = null,
  val groundingMetadata: GeminiGroundingMetadata? = null,
)

@Serializable
data class GeminiGroundingMetadata(
  val webSearchQueries: List<String> = emptyList(),
  val groundingChunks: List<GeminiGroundingChunk> = emptyList(),
  val groundingSupports: List<GeminiGroundingSupport> = emptyList(),
)

@Serializable
data class GeminiGroundingChunk(
  val web: GeminiGroundingWeb? = null,
  val maps: GeminiGroundingMaps? = null,
)

@Serializable
data class GeminiGroundingWeb(
  val uri: String? = null,
  val title: String? = null,
)

@Serializable
data class GeminiGroundingMaps(
  val uri: String? = null,
  val title: String? = null,
  val placeId: String? = null,
)

@Serializable
data class GeminiGroundingSupport(
  val segment: GeminiGroundingSegment? = null,
  val groundingChunkIndices: List<Int> = emptyList(),
)

@Serializable
data class GeminiGroundingSegment(
  val text: String? = null,
)

@Serializable
data class GeminiUsageMetadata(
  val promptTokenCount: Int = 0,
  val candidatesTokenCount: Int = 0,
  val totalTokenCount: Int = 0,
)

@Serializable
data class GeminiModelsListResponse(
  val models: List<GeminiModelInfoDto> = emptyList(),
)

@Serializable
data class GeminiModelInfoDto(
  val name: String = "",
  val displayName: String = "",
  val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable
data class GeminiLongRunningRequest(
  val instances: List<GeminiLongRunningInstance>,
  val parameters: GeminiLongRunningParameters? = null,
)

@Serializable
data class GeminiLongRunningInstance(
  val prompt: String,
  val image: GeminiLongRunningImage? = null,
)

@Serializable
data class GeminiLongRunningImage(
  val bytesBase64Encoded: String,
  val mimeType: String,
)

@Serializable
data class GeminiLongRunningParameters(
  val aspectRatio: String? = null,
  val durationSeconds: Int? = null,
  val sampleCount: Int? = 1,
)

@Serializable
data class GeminiOperationResponse(
  val name: String = "",
  val done: Boolean = false,
  val error: GeminiOperationErrorDto? = null,
  val response: JsonObject? = null,
)

@Serializable
data class GeminiOperationErrorDto(
  val code: Int? = null,
  val message: String? = null,
)
