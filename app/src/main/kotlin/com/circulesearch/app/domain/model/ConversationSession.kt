package com.circulesearch.app.domain.model

/**
 * The ephemeral, in-memory chat tied to exactly one [VisualSearchRequest] (research.md
 * R2/R3). Owned by the result panel's ViewModel; MUST NOT be written to disk at any
 * point (FR-028) and is discarded in full the instant the panel closes (FR-029).
 */
data class ConversationSession(
    val id: String,
    val visualSearchRequestId: String,
    val messages: List<ChatMessage>,
    val profilesImageSentTo: Set<String>,
    val sessionPinnedProfileId: String?,
    val state: State,
) {
    enum class State { AwaitingFirstResult, Active, Discarded }
}
