package com.circulesearch.app.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One `data: {...}` SSE chunk, parsed incrementally by [com.circulesearch.app.data.network.SseChatStreamParser]. */
@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    val choices: List<ChunkChoiceDto> = emptyList(),
)

@Serializable
data class ChunkChoiceDto(
    val index: Int = 0,
    val delta: DeltaDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class DeltaDto(
    val role: String? = null,
    val content: String? = null,
)
