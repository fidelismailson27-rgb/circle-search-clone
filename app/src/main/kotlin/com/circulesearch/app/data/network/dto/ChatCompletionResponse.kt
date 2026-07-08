package com.circulesearch.app.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Non-streaming response shape (used when the endpoint ignores `stream: true`). */
@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ChoiceDto> = emptyList(),
)

@Serializable
data class ChoiceDto(
    val index: Int = 0,
    val message: ResponseMessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ResponseMessageDto(
    val role: String? = null,
    val content: String? = null,
)
