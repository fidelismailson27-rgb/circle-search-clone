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
import com.circulesearch.app.domain.model.SearchError
import com.circulesearch.app.domain.repository.ChatTurnResult
import com.circulesearch.app.domain.repository.VisualSearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

/**
 * Single OpenAI-compatible network path (research.md R4) — one code path builds and
 * sends every request regardless of which BYOK profile it targets. T033 covers the
 * initial-turn send against a single profile; US2's T047 extends [attemptChain] with
 * the actual retry-the-next-profile-on-failure loop.
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

        private fun attemptChain(
            profiles: List<AiEndpointProfile>,
            priorMessages: List<ChatMessage>,
            profilesImageAlreadySentTo: Set<String>,
            imageBytes: ByteArray?,
            userText: String,
        ): Flow<ChatTurnResult> =
            flow {
                val profile = profiles.firstOrNull()
                if (profile == null) {
                    emit(ChatTurnResult.Failed(SearchError.NoActiveProfileConfigured))
                    return@flow
                }

                val attachImage = imageBytes != null && imageAttachmentPolicy.shouldAttachImage(profile.id, profilesImageAlreadySentTo)
                val messages = buildMessages(priorMessages, userText, imageBytes, attachImage)
                emitAll(attemptSingleProfile(profile, messages))
            }

        private fun attemptSingleProfile(
            profile: AiEndpointProfile,
            messages: List<ChatMessageDto>,
        ): Flow<ChatTurnResult> =
            flow {
                try {
                    val request = ChatCompletionRequest(model = profile.modelName, messages = messages, stream = true)
                    val url = profile.baseUrl.trimEnd('/') + CHAT_COMPLETIONS_PATH
                    val response = api.chatCompletion(url, request, ProfileAuthTag(profile.apiKey))

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

        private companion object {
            const val CHAT_COMPLETIONS_PATH = "/chat/completions"
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
