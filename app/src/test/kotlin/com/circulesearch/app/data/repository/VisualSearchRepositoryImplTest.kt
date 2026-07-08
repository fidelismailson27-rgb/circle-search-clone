package com.circulesearch.app.data.repository

import com.circulesearch.app.data.network.ImageAttachmentPolicy
import com.circulesearch.app.data.network.OpenAiCompatibleApi
import com.circulesearch.app.data.network.SseChatStreamParser
import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.SearchError
import com.circulesearch.app.domain.repository.ChatTurnResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** FR-014/FR-016: automatic fallback across profiles, and the aggregated error when every profile fails. */
class VisualSearchRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: VisualSearchRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true }
        val retrofit =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        val api = retrofit.create(OpenAiCompatibleApi::class.java)

        repository = VisualSearchRepositoryImpl(api, SseChatStreamParser(json), ImageAttachmentPolicy())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `falls back to the next profile when the active profile fails`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"Hello from fallback"}}]}"""),
            )

            val results =
                repository.sendInitialMessage(
                    activeProfile = testProfile("active"),
                    fallbackChain = listOf(testProfile("fallback")),
                    imageBytes = null,
                    userText = "hi",
                ).toList()

            assertEquals(1, results.size)
            val success = results.single() as ChatTurnResult.Success
            assertEquals("fallback", success.message.producedByProfileId)
            assertEquals("Hello from fallback", success.message.textContent)
        }

    @Test
    fun `emits one aggregated error naming every profile when all fail`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(503))

            val results =
                repository.sendInitialMessage(
                    activeProfile = testProfile("active"),
                    fallbackChain = listOf(testProfile("fallback")),
                    imageBytes = null,
                    userText = "hi",
                ).toList()

            assertEquals(1, results.size)
            val failed = results.single() as ChatTurnResult.Failed
            val error = failed.error as SearchError.AllProfilesExhausted
            assertEquals(listOf("active", "fallback"), error.attempts.map { it.profileId })
        }

    @Test
    fun `succeeds directly against the active profile with no fallback needed`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"Hello"}}]}"""),
            )

            val results =
                repository.sendInitialMessage(
                    activeProfile = testProfile("active"),
                    fallbackChain = emptyList(),
                    imageBytes = null,
                    userText = "hi",
                ).toList()

            assertEquals(1, results.size)
            val success = results.single() as ChatTurnResult.Success
            assertEquals("active", success.message.producedByProfileId)
        }

    private fun testProfile(id: String) =
        AiEndpointProfile(
            id = id,
            name = "Profile $id",
            baseUrl = server.url("/").toString(),
            apiKey = "key-$id",
            modelName = "model-$id",
            isActive = id == "active",
            fallbackOrder = if (id == "active") null else 1,
            lastConnectionTest = null,
        )
}
