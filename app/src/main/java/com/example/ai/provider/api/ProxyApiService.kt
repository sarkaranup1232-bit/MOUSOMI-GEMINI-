package com.example.ai.provider.api

import com.example.ai.provider.dto.ExternalSearchRequest
import com.example.ai.provider.dto.ExternalSearchResponse
import com.example.ai.provider.dto.ProxyAnalyticsRequest
import com.example.ai.provider.dto.ProxyAnalyticsResponse
import com.example.ai.provider.dto.ProxyAudioRequest
import com.example.ai.provider.dto.ProxyAudioResponse
import com.example.ai.provider.dto.ProxyChatRequest
import com.example.ai.provider.dto.ProxyImageGenerationRequest
import com.example.ai.provider.dto.ProxyImageGenerationResponse
import com.example.ai.provider.dto.ProxySearchRequest
import com.example.ai.provider.dto.ProxySearchResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ProxyApiService {

  @GET
  suspend fun probeHealth(
    @Url fullHttpsUrl: String,
    @Header("Authorization") authorization: String? = null,
  ): Response<ResponseBody>

  @Streaming
  @POST
  suspend fun streamProxyChat(
    @Url fullHttpsUrl: String,
    @Body request: ProxyChatRequest,
  ): Response<ResponseBody>

  @POST
  suspend fun searchViaProxy(
    @Url fullHttpsUrl: String,
    @Body request: ProxySearchRequest,
  ): Response<ProxySearchResponse>

  @POST
  suspend fun searchExternalApi(
    @Url fullHttpsUrl: String,
    @Header("X-API-KEY") apiKey: String,
    @Body request: ExternalSearchRequest,
  ): Response<ExternalSearchResponse>

  @POST
  suspend fun transcribeAudioViaProxy(
    @Url fullHttpsUrl: String,
    @Body request: ProxyAudioRequest,
  ): Response<ProxyAudioResponse>

  @POST
  suspend fun executeAnalyticsViaProxy(
    @Url fullHttpsUrl: String,
    @Body request: ProxyAnalyticsRequest,
  ): Response<ProxyAnalyticsResponse>

  @POST
  suspend fun generateImageViaProxy(
    @Url fullHttpsUrl: String,
    @Body request: ProxyImageGenerationRequest,
  ): Response<ProxyImageGenerationResponse>

  @POST
  suspend fun editImageViaProxy(
    @Url fullHttpsUrl: String,
    @Body request: ProxyImageGenerationRequest,
  ): Response<ProxyImageGenerationResponse>
}
