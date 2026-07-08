package com.circulesearch.app.domain.repository

import com.circulesearch.app.domain.model.SearchError

/**
 * Orchestrates one on-demand capture (constitution III): requesting `MediaProjection`
 * consent, capturing a single frame, checking for a black/blocked frame, and
 * cropping+compressing the result — all Android `Bitmap` handling stays inside the
 * `data.capture` implementation; this interface only ever exposes the finished bytes
 * or an outcome, never a `Bitmap` (constitution VII, constitution II).
 */
interface ScreenCaptureRepository {
    suspend fun captureAndProcessSelection(
        selection: OverlaySelectionRegion,
        compressionQuality: Int,
    ): ScreenCaptureOutcome

    /**
     * On-demand text extraction (US5, FR-019) — called only after [captureAndProcessSelection]
     * returns [ScreenCaptureOutcome.Blocked]. Returns null if extraction itself yields
     * nothing usable (FR-020's "unavailable" case).
     */
    suspend fun extractFallbackText(): String?
}

/** Selection bounds in the overlay window's own pixel space, plus that window's pixel size for mapping. */
data class OverlaySelectionRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val overlayWindowWidthPx: Int,
    val overlayWindowHeightPx: Int,
)

sealed interface ScreenCaptureOutcome {
    data class Success(val compressedImageBytes: ByteArray) : ScreenCaptureOutcome

    /** Black/blank frame detected (typically `FLAG_SECURE`) — US5 routes this to the text fallback. */
    data object Blocked : ScreenCaptureOutcome

    data class Failed(val error: SearchError) : ScreenCaptureOutcome
}
