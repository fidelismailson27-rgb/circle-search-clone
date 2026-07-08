package com.circulesearch.app.data.network

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/** research.md R4: branch once on Content-Type, never per-provider. */
class SseChatStreamParserTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val parser = SseChatStreamParser(json)

    @Test
    fun `parses an SSE stream into deltas then a final complete event`() =
        runTest {
            val sse =
                buildString {
                    append("""data: {"choices":[{"delta":{"content":"Hel"}}]}""" + "\n\n")
                    append("""data: {"choices":[{"delta":{"content":"lo"}}]}""" + "\n\n")
                    append("data: [DONE]\n\n")
                }
            val response = jsonResponse(sse, "text/event-stream")

            val events = parser.parse(response).toList()

            assertEquals(3, events.size)
            assertEquals("Hel", (events[0] as ParsedStreamEvent.Delta).textFragment)
            assertEquals("lo", (events[1] as ParsedStreamEvent.Delta).textFragment)
            assertEquals("Hello", (events[2] as ParsedStreamEvent.Complete).fullText)
        }

    @Test
    fun `parses a plain JSON response as a single complete event`() =
        runTest {
            val body = """{"choices":[{"message":{"content":"Hi there"}}]}"""
            val response = jsonResponse(body, "application/json")

            val events = parser.parse(response).toList()

            assertEquals(1, events.size)
            assertEquals("Hi there", (events.single() as ParsedStreamEvent.Complete).fullText)
        }

    private fun jsonResponse(
        body: String,
        contentType: String,
    ) = Response.success(
        body.toResponseBody(contentType.toMediaType()),
        Headers.Builder().add("Content-Type", contentType).build(),
    )
}
