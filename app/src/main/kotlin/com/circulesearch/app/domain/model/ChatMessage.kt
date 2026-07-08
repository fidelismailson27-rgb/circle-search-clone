package com.circulesearch.app.domain.model

import java.time.Instant

/**
 * One turn within a [ConversationSession]. An `Assistant`-role message doubles as the
 * spec's "Search Result" concept via [producedByProfileId]/[usedTextFallback] — there
 * is no separate persisted Search Result type (data-model.md).
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val textContent: String,
    val includesImage: Boolean,
    val producedByProfileId: String?,
    val usedTextFallback: Boolean,
    val createdAt: Instant,
) {
    enum class Role { User, Assistant, System }

    init {
        require(role == Role.Assistant || (producedByProfileId == null && !usedTextFallback)) {
            "producedByProfileId/usedTextFallback only apply to Assistant messages"
        }
    }
}
