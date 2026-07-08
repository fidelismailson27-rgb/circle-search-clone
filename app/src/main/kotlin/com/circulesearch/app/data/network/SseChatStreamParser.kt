package com.circulesearch.app.data.network

import com.circulesearch.app.data.network.dto.ChatCompletionChunk
import com.circulesearch.app.data.network.dto.ChatCompletionResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Branches exactly once, at the transport layer, on the response `Content-Type`
 * (research.md R4) — `text/event-stream` is parsed as incremental SSE chunks,
 * anything else as one plain JSON object. Never a per-provider branch.
 */
class SseChatStreamParser
    @Inject
    constructor(
        private val json: Json,
    ) {
        fun parse(response: Response<ResponseBody>): Flow<ParsedStreamEvent> =
            flow {
                val body = response.body() ?: throw IOException("Empty response body")
                val contentType = response.headers()["Content-Type"] ?: body.contentType()?.toString().orEmpty()

                if (contentType.contains("text/event-stream", ignoreCase = true)) {
                    emitSseEvents(body)
                } else {
                    emitSingleJsonResponse(body)
                }
            }

        private suspend fun FlowCollector<ParsedStreamEvent>.emitSseEvents(body: ResponseBody) {
            val fullText = StringBuilder()
            body.source().use { source ->
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue

                    val chunk = runCatching { json.decodeFromString(ChatCompletionChunk.serializer(), data) }.getOrNull()
                    val delta = chunk?.choices?.firstOrNull()?.delta?.content
                    if (delta.isNullOrEmpty()) continue

                    fullText.append(delta)
                    emit(ParsedStreamEvent.Delta(delta))
                }
            }
            emit(ParsedStreamEvent.Complete(fullText.toString()))
        }

        private suspend fun FlowCollector<ParsedStreamEvent>.emitSingleJsonResponse(body: ResponseBody) {
            val text = body.string()
            val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), text)
            val content = parsed.choices.firstOrNull()?.message?.content
            if (content.isNullOrEmpty()) {
                throw IOException("Response contained no message content: $text")
            }
            emit(ParsedStreamEvent.Complete(content))
        }
    }

sealed interface ParsedStreamEvent {
    data class Delta(val textFragment: String) : ParsedStreamEvent

    data class Complete(val fullText: String) : ParsedStreamEvent
}
