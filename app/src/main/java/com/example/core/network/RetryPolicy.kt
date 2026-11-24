package com.example.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException

class AiProviderException(
  override val message: String,
  val httpStatusCode: Int? = null,
  val isRetryable: Boolean = false,
  cause: Throwable? = null,
) : Exception(message, cause)

object RetryPolicy {
  private val retryableStatusCodes = setOf(408, 429, 500, 502, 503, 504)
  private val geminiKeyRegex = Regex("AIza[0-9A-Za-z_-]{20,}")
  private val groqKeyRegex = Regex("gsk_[0-9A-Za-z_-]{16,}")
  private val bearerRegex = Regex("Bearer\\s+[A-Za-z0-9._-]+", RegexOption.IGNORE_CASE)

  fun isRetryableStatus(code: Int): Boolean = code in retryableStatusCodes

  fun redactSecrets(raw: String): String =
    raw
      .replace(geminiKeyRegex, "AIza••••REDACTED")
      .replace(groqKeyRegex, "gsk_••••REDACTED")
      .replace(bearerRegex, "Bearer ••••REDACTED")

  fun mapHttpError(
    code: Int,
    rawBody: String? = null,
    retryAfterHeader: String? = null,
  ): AiProviderException {
    val detail =
      rawBody
        ?.let(::redactSecrets)
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(220)
        ?.takeIf { it.isNotBlank() }
    val msg =
      when (code) {
        400 ->
          if (detail?.contains("model", ignoreCase = true) == true) {
            "Selected model rejected this request (HTTP 400): $detail"
          } else {
            "Invalid request payload (HTTP 400)${detail?.let { ": $it" } ?: "."}"
          }
        401 -> "Invalid or expired API key (HTTP 401). Verify your key in Settings."
        403 -> "Access denied or insufficient permission for this API/model (HTTP 403)${detail?.let { ": $it" } ?: "."}"
        404 ->
          "Selected model or endpoint is unavailable on this provider (HTTP 404). Open Settings and pick another verified model."
        408 -> "Request timed out at provider (HTTP 408). Retry in a moment."
        429 -> {
          val retryHint = retryAfterHeader?.trim()?.takeIf { it.isNotBlank() }?.let { " Retry after ${it}s." } ?: ""
          val isQuota = detail?.contains("quota", ignoreCase = true) == true ||
            detail?.contains("billing", ignoreCase = true) == true ||
            detail?.contains("exceeded", ignoreCase = true) == true
          if (isQuota) {
            "Provider quota exceeded (HTTP 429).$retryHint Switch model or check billing/quota."
          } else {
            "Provider rate limit reached (HTTP 429).$retryHint Try again shortly."
          }
        }
        in 500..599 -> "AI provider temporarily unavailable (HTTP $code). Please retry shortly."
        else -> "Request failed with HTTP $code${detail?.let { ": $it" } ?: "."}"
      }
    return AiProviderException(
      message = msg,
      httpStatusCode = code,
      isRetryable = isRetryableStatus(code),
    )
  }

  fun mapThrowable(error: Throwable): AiProviderException {
    if (error is AiProviderException) return error
    return when (error) {
      is SocketTimeoutException ->
        AiProviderException(
          message = "Request timed out while waiting for the provider. Check network speed and retry.",
          httpStatusCode = 408,
          isRetryable = true,
          cause = error,
        )
      is UnknownHostException, is ConnectException ->
        AiProviderException(
          message = "Unable to reach provider (offline or DNS failure). Check your internet connection.",
          isRetryable = true,
          cause = error,
        )
      is SerializationException ->
        AiProviderException(
          message = "Provider returned an unexpected or incomplete JSON response.",
          isRetryable = false,
          cause = error,
        )
      is IOException ->
        AiProviderException(
          message = "Network communication failed: ${redactSecrets(error.localizedMessage ?: "connection interrupted")}",
          isRetryable = true,
          cause = error,
        )
      else ->
        AiProviderException(
          message = redactSecrets(error.localizedMessage ?: "Unexpected provider error."),
          isRetryable = false,
          cause = error,
        )
    }
  }

  suspend fun <T> withExponentialBackoff(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 800L,
    maxDelayMs: Long = 6_000L,
    block: suspend (attempt: Int) -> T,
  ): T {
    var currentDelay = initialDelayMs
    var lastError: Throwable? = null

    for (attempt in 1..maxAttempts) {
      try {
        return block(attempt)
      } catch (e: CancellationException) {
        throw e
      } catch (e: AiProviderException) {
        lastError = e
        if (!e.isRetryable || attempt == maxAttempts) throw e
      } catch (e: IOException) {
        lastError = mapThrowable(e)
        if (attempt == maxAttempts) throw lastError
      }

      val jitter = Random.nextLong(100L, 350L)
      delay((currentDelay + jitter).coerceAtMost(maxDelayMs))
      currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
    }

    throw (lastError ?: AiProviderException("Unknown network failure"))
  }
}
