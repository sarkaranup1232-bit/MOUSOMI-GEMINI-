package com.example.ai.provider.api

import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class CloudflareModelEntry(
  val id: String = "",
  val name: String = "",
  val description: String = "",
)

@Serializable
data class CloudflareModelsResponse(
  val success: Boolean = false,
  val result: List<CloudflareModelEntry> = emptyList(),
  val errors: List<CloudflareApiError> = emptyList(),
)

@Serializable
data class CloudflareApiError(
  val code: Int = 0,
  val message: String = "",
)

@Serializable
data class CloudflareJsonImageEnvelope(
  val success: Boolean = false,
  val result: CloudflareJsonImageResult? = null,
  val errors: List<CloudflareApiError> = emptyList(),
)

@Serializable
data class CloudflareJsonImageResult(
  val image: String? = null,
)

interface CloudflareWorkersAiService {
  @GET("accounts/{accountId}/ai/models/search")
  suspend fun listImageModels(
    @Path("accountId") accountId: String,
    @Header("Authorization") authorization: String,
    @retrofit2.http.Query("task") task: String = "Text-to-Image",
  ): Response<CloudflareModelsResponse>

  @POST("accounts/{accountId}/ai/run/{modelId}")
  suspend fun runModelRaw(
    @Path("accountId") accountId: String,
    @Path("modelId", encoded = true) modelId: String,
    @Header("Authorization") authorization: String,
    @Body body: RequestBody,
  ): Response<ResponseBody>
}
