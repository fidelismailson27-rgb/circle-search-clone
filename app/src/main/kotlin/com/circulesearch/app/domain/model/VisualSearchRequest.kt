package com.circulesearch.app.domain.model

import android.graphics.Rect
import java.time.Instant

/**
 * One invocation of the core flow (FR-001..FR-007). In-memory only for the lifetime
 * of its [ConversationSession] — never persisted (spec.md Assumptions).
 */
data class VisualSearchRequest(
    val id: String,
    val capturedAt: Instant,
    val selectionRegion: Rect,
    val status: Status,
    val captureSource: CaptureSource,
) {
    enum class Status { Pending, Succeeded, Failed }

    enum class CaptureSource { Image, TextFallback }
}
