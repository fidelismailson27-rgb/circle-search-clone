package com.circulesearch.app.data.network

import com.circulesearch.app.data.network.dto.ChatCompletionRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Tag
import retrofit2.http.Url

/**
 * The single OpenAI-compatible network path (research.md R4, constitution IX) — one
 * interface, no per-provider adapters. [url] is resolved per-call from whichever
 * [com.circulesearch.app.domain.model.AiEndpointProfile] is currently being attempted
 * (active profile, or a fallback candidate), since Retrofit needs one client instance
 * shared across every profile's distinct Base URL.
 *
 * Returns the raw [ResponseBody] rather than an auto-deserialized DTO: whether the
 * response is a single JSON object or an SSE stream is only known from the response's
 * `Content-Type`, decided once by [SseChatStreamParser] — not by Retrofit's converter.
 */
interface OpenAiCompatibleApi {
    @Streaming
    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Body request: ChatCompletionRequest,
        @Tag auth: ProfileAuthTag,
    ): Response<ResponseBody>
}

/**
 * Carries the target profile's API key as a per-request tag (not a static header),
 * read by [ProfileAuthInterceptor] — required because a single conversation can
 * target different profiles across its turns (research.md R2).
 */
data class ProfileAuthTag(val apiKey: String)
