package com.circulesearch.app.data.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Request body for the single OpenAI-compatible `/v1/chat/completions`-shaped
 * endpoint every BYOK profile targets (research.md R4) — no per-provider variant.
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: ChatContent,
)

/**
 * `content` is a plain string for text-only turns, or a multi-part array when an
 * image is attached (research.md R3 — image attached only on a profile's first turn
 * in a session). [ChatContentSerializer] chooses the wire shape based on which
 * subtype is present, matching what OpenAI/Gemini-compat/OpenRouter/Ollama all accept.
 */
@Serializable(with = ChatContentSerializer::class)
sealed interface ChatContent {
    data class Text(val text: String) : ChatContent

    data class Parts(val parts: List<ContentPart>) : ChatContent
}

@Serializable
sealed interface ContentPart {
    @Serializable
    @SerialName("text")
    data class TextPart(val text: String) : ContentPart

    @Serializable
    @SerialName("image_url")
    data class ImagePart(
        @SerialName("image_url") val imageUrl: ImageUrlValue,
    ) : ContentPart
}

@Serializable
data class ImageUrlValue(val url: String)

object ChatContentSerializer : KSerializer<ChatContent> {
    private val partsSerializer = ListSerializer(ContentPart.serializer())

    override val descriptor: SerialDescriptor = buildSerialDescriptor("ChatContent", kotlinx.serialization.descriptors.SerialKind.CONTEXTUAL)

    override fun serialize(
        encoder: Encoder,
        value: ChatContent,
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("ChatContent can only be serialized to JSON")
        val element =
            when (value) {
                is ChatContent.Text -> JsonPrimitive(value.text)
                is ChatContent.Parts -> jsonEncoder.json.encodeToJsonElement(partsSerializer, value.parts)
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): ChatContent {
        val jsonDecoder = decoder as? JsonDecoder ?: error("ChatContent can only be deserialized from JSON")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> ChatContent.Text(element.content)
            is JsonArray -> ChatContent.Parts(jsonDecoder.json.decodeFromJsonElement(partsSerializer, element))
            else -> error("Unexpected 'content' JSON shape: $element")
        }
    }
}
