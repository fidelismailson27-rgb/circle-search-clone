package com.circulesearch.app.domain.repository

import com.circulesearch.app.domain.model.AiEndpointProfile
import com.circulesearch.app.domain.model.ChatMessage
import com.circulesearch.app.domain.model.ConnectionTestResult
import com.circulesearch.app.domain.model.SearchError
import kotlinx.coroutines.flow.Flow

/**
 * Owns the network orchestration for a search turn: building the OpenAI-compatible
 * request (research.md R4), applying [com.circulesearch.app.data.network.ImageAttachmentPolicy]
 * (R3), and walking the fallback chain on failure (R2, FR-014/FR-015/FR-016).
 */
interface VisualSearchRepository {
    /**
     * Sends the first turn of a new [com.circulesearch.app.domain.model.ConversationSession].
     * [imageBytes] is the already-cropped/compressed selection (or null if this turn is
     * text-fallback only, per FR-019).
     */
    fun sendInitialMessage(
        activeProfile: AiEndpointProfile,
        fallbackChain: List<AiEndpointProfile>,
        imageBytes: ByteArray?,
        userText: String,
    ): Flow<ChatTurnResult>

    /**
     * Sends a follow-up turn (FR-026/FR-027), replaying [priorMessages] in full.
     * [candidateProfiles] is ordered: the session-pinned profile first if one is set
     * (research.md R2), then the remaining fallback chain. [originalImageBytes] is the
     * session's original compressed selection — kept available (not just its prior
     * text description) so it can be re-attached under [ImageAttachmentPolicy] if a
     * fallback introduces a profile that has not seen it yet (R3); null if the
     * session's initial turn itself had no image (text-fallback-only session).
     */
    fun sendFollowUpMessage(
        priorMessages: List<ChatMessage>,
        profilesImageAlreadySentTo: Set<String>,
        originalImageBytes: ByteArray?,
        candidateProfiles: List<AiEndpointProfile>,
        userText: String,
    ): Flow<ChatTurnResult>

    /** On-demand connection test for a single profile (FR-010) — never hangs indefinitely. */
    suspend fun testConnection(profile: AiEndpointProfile): ConnectionTestResult
}

/** One turn's lifecycle, surfaced to the UI as explicit states (constitution X). */
sealed interface ChatTurnResult {
    data class Streaming(val partialText: String, val answeredByProfileId: String) : ChatTurnResult

    data class Success(val message: ChatMessage) : ChatTurnResult

    data class Failed(val error: SearchError) : ChatTurnResult
}
