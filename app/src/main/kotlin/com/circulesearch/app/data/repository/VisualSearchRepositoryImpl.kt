package com.circulesearch.app.data.repository

import com.circulesearch.app.data.network.ImageAttachmentPolicy
import com.circulesearch.app.data.network.OpenAiCompatibleApi
import com.circulesearch.app.data.network.ParsedStreamEvent
import com.circulesearch.app.data.network.ProfileAuthTag
import com.circulesearch.app.data.network.SseChatStreamParser
import com.circulesearch.app.data.network.dto.ChatCompletionRequest
import com.circulesearch.app.data.network.dto.ChatContent
import com.circulesearch.app.data.network.dto.ChatMessageDto
import com.circulesearch.app.data.network.dto.ContentPart
import com.circulesearch.app.data.network.dto.ImageUrlValue
import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ChatMessage
import com.circulesearch.app.domain.model.ConnectionTestResult
import com.circulesearch.app.domain.model.ProfileAttempt
import com.circulesearch.app.domain.model.SearchError
import com.circulesearch.app.domain.repository.ChatTurnResult
import com.circulesearch.app.domain.repository.VisualSearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

/**
 * Single OpenAI-compatible network path (research.md R4) — one code path builds and
 * sends every request regardless of which BYOK profile it targets. [attemptChain]
 * walks [profiles] in order, retrying the next one on failure (FR-014), stopping at
 * the first profile that produces any output, and emitting an aggregated error
 * (FR-016) if every candidate fails.
 */
class VisualSearchRepositoryImpl
    @Inject
    constructor(
        private val api: OpenAiCompatibleApi,
        private val streamParser: SseChatStreamParser,
        private val imageAttachmentPolicy: ImageAttachmentPolicy,
    ) : VisualSearchRepository {
        override fun sendInitialMessage(
            activeProfile: AiEndpointProfile,
            fallbackChain: List<AiEndpointProfile>,
            imageBytes: ByteArray?,
            userText: String,
        ): Flow<ChatTurnResult> {
            val candidates = listOf(activeProfile) + fallbackChain
            return attemptChain(candidates, emptyList(), emptySet(), imageBytes, userText)
        }

        override fun sendFollowUpMessage(
            priorMessages: List<ChatMessage>,
            profilesImageAlreadySentTo: Set<String>,
            originalImageBytes: ByteArray?,
            candidateProfiles: List<AiEndpointProfile>,
            userText: String,
        ): Flow<ChatTurnResult> = attemptChain(candidateProfiles, priorMessages, profilesImageAlreadySentTo, originalImageBytes, userText)

        override suspend fun testConnection(profile: AiEndpointProfile): ConnectionTestResult {
            if (profile.baseUrl.isBlank() || profile.modelName.isBlank()) {
                return ConnectionTestResult(Instant.now(), success = false, message = "Base URL and model are required.")
            }
            return try {
                val request =
                    ChatCompletionRequest(
                        model = profile.modelName,
                        messages = listOf(ChatMessageDto(role = "user", content = ChatContent.Text(TEST_PROMPT))),
                        stream = false,
                    )
                val response = api.chatCompletion(profile.chatCompletionsUrl(), request, ProfileAuthTag(profile.apiKey))
                if (response.isSuccessful) {
                    ConnectionTestResult(Instant.now(), success = true, message = "Connected successfully.")
                } else {
                    val body = response.errorBody()?.string().orEmpty()
                    ConnectionTestResult(Instant.now(), success = false, message = "HTTP ${response.code()}: $body".take(MAX_TEST_MESSAGE_LENGTH))
                }
            } catch (e: IOException) {
                ConnectionTestResult(Instant.now(), success = false, message = e.message ?: "Network error.")
            }
        }

        /** FR-014/FR-016: try each candidate profile in order; stop at the first that produces any output. */
        private fun attemptChain(
            profiles: List<AiEndpointProfile>,
            priorMessages: List<ChatMessage>,
            profilesImageAlreadySentTo: Set<String>,
            imageBytes: ByteArray?,
            userText: String,
        ): Flow<ChatTurnResult> =
            flow {
                if (profiles.isEmpty()) {
                    emit(ChatTurnResult.Failed(SearchError.NoActiveProfileConfigured))
                    return@flow
                }

                val failedAttempts = mutableListOf<ProfileAttempt>()

                for ((index, profile) in profiles.withIndex()) {
                    val attachImage = imageBytes != null && imageAttachmentPolicy.shouldAttachImage(profile.id, profilesImageAlreadySentTo)
                    val messages = buildMessages(priorMessages, userText, imageBytes, attachImage)

                    var producedOutput = false
                    var lastError: SearchError = SearchError.Network(IOException("No response"))

                    attemptSingleProfile(profile, messages).collect { result ->
                        when (result) {
                            is ChatTurnResult.Streaming, is ChatTurnResult.Success -> {
                                producedOutput = true
                                emit(result)
                            }
                            is ChatTurnResult.Failed -> lastError = result.error
                        }
                    }

                    if (producedOutput) return@flow

                    failedAttempts += ProfileAttempt(profile.id, profile.name, lastError)
                    if (index == profiles.lastIndex) {
                        emit(ChatTurnResult.Failed(SearchError.AllProfilesExhausted(failedAttempts)))
                    }
                }
            }

        private fun attemptSingleProfile(
            profile: AiEndpointProfile,
            messages: List<ChatMessageDto>,
        ): Flow<ChatTurnResult> =
            flow {
                try {
                    val request = ChatCompletionRequest(model = profile.modelName, messages = messages, stream = true)
                    val response = api.chatCompletion(profile.chatCompletionsUrl(), request, ProfileAuthTag(profile.apiKey))

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        emit(ChatTurnResult.Failed(SearchError.Http(response.code(), errorBody)))
                        return@flow
                    }

                    var fullText = ""
                    streamParser.parse(response).collect { event ->
                        when (event) {
                            is ParsedStreamEvent.Delta -> {
                                fullText += event.textFragment
                                emit(ChatTurnResult.Streaming(fullText, profile.id))
                            }
                            is ParsedStreamEvent.Complete -> fullText = event.fullText
                        }
                    }

                    emit(ChatTurnResult.Success(fullText.toAssistantMessage(profile.id)))
                } catch (e: IOException) {
                    emit(ChatTurnResult.Failed(SearchError.Network(e)))
                } catch (e: kotlinx.serialization.SerializationException) {
                    emit(ChatTurnResult.Failed(SearchError.MalformedResponse(e.message ?: "Malformed response")))
                }
            }

        private fun buildMessages(
            priorMessages: List<ChatMessage>,
            userText: String,
            imageBytes: ByteArray?,
            attachImage: Boolean,
        ): List<ChatMessageDto> {
            var imageAttached = false
            val history =
                priorMessages.map { message ->
                    if (attachImage && !imageAttached && message.includesImage && imageBytes != null) {
                        imageAttached = true
                        message.toDto(content = buildImageContent(message.textContent, imageBytes))
                    } else {
                        message.toDto(content = ChatContent.Text(message.textContent))
                    }
                }

            val newUserContent =
                if (attachImage && !imageAttached && imageBytes != null) {
                    buildImageContent(userText, imageBytes)
                } else {
                    ChatContent.Text(userText)
                }

            return history + ChatMessageDto(role = "user", content = newUserContent)
        }

        private fun buildImageContent(
            text: String,
            imageBytes: ByteArray,
        ): ChatContent {
            val base64 = Base64.getEncoder().encodeToString(imageBytes)
            return ChatContent.Parts(
                listOf(
                    ContentPart.TextPart(text),
                    ContentPart.ImagePart(ImageUrlValue("data:image/webp;base64,$base64")),
                ),
            )
        }

        private fun AiEndpointProfile.chatCompletionsUrl(): String = baseUrl.trimEnd('/') + CHAT_COMPLETIONS_PATH

        private companion object {
            const val CHAT_COMPLETIONS_PATH = "/chat/completions"
            const val TEST_PROMPT = "Reply with \"ok\"."
            const val MAX_TEST_MESSAGE_LENGTH = 200
        }
    }

private fun String.toAssistantMessage(profileId: String) =
    ChatMessage(
        id = UUID.randomUUID().toString(),
        role = ChatMessage.Role.Assistant,
        textContent = this,
        includesImage = false,
        producedByProfileId = profileId,
        usedTextFallback = false,
        createdAt = Instant.now(),
    )

private fun ChatMessage.toDto(content: ChatContent): ChatMessageDto {
    val role =
        when (this.role) {
            ChatMessage.Role.User -> "user"
            ChatMessage.Role.Assistant -> "assistant"
            ChatMessage.Role.System -> "system"
        }
    return ChatMessageDto(role = role, content = content)
}
