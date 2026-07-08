package com.circulesearch.app.domain.model

/**
 * The ephemeral, in-memory chat tied to exactly one [VisualSearchRequest] (research.md
 * R2/R3). Owned by the result panel's ViewModel; MUST NOT be written to disk at any
 * point (FR-028) and is discarded in full the instant the panel closes (FR-029).
 */
@Suppress("ArrayInDataClass") // originalImageBytes identity/content equality is never relied upon
data class ConversationSession(
    val id: String,
    val visualSearchRequestId: String,
    val messages: List<ChatMessage>,
    val profilesImageSentTo: Set<String>,
    val sessionPinnedProfileId: String?,
    val state: State,
    /**
     * The session's original compressed selection bytes, kept available (not just
     * its first-turn text description) so a mid-session fallback can re-attach it to
     * a profile that has not seen it yet (R3/ImageAttachmentPolicy). Null for a
     * text-fallback-only session (no image was ever captured, FR-019/020). This is
     * already-compressed WebP output, not a raw frame — retaining it does not
     * conflict with constitution VII's full-resolution-Bitmap discipline.
     */
    val originalImageBytes: ByteArray?,
) {
    enum class State { AwaitingFirstResult, Active, Discarded }
}
