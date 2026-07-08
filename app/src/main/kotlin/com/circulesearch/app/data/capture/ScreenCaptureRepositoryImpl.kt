package com.circulesearch.app.data.capture

import android.content.Context
import android.content.Intent
import com.circulesearch.app.data.accessibility.TextExtractionFallback
import com.circulesearch.app.domain.model.SearchError
import com.circulesearch.app.domain.repository.OverlaySelectionRegion
import com.circulesearch.app.domain.repository.ScreenCaptureOutcome
import com.circulesearch.app.domain.repository.ScreenCaptureRepository
import com.circulesearch.app.ui.overlay.CoordinateMapper
import com.circulesearch.app.ui.overlay.FloatBounds
import com.circulesearch.app.ui.overlay.PixelSize
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Implements the full on-demand capture cycle (research.md R1): launches
 * [MediaProjectionConsentActivity], awaits the result via [CaptureCoordinator],
 * checks for a black/blocked frame, then crops+compresses. The `Bitmap` obtained from
 * [CaptureResult.Success] never leaves this class.
 */
class ScreenCaptureRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val captureCoordinator: CaptureCoordinator,
        private val blackFrameDetector: BlackFrameDetector,
        private val imageProcessor: SelectionImageProcessor,
        private val textExtractionFallback: TextExtractionFallback,
    ) : ScreenCaptureRepository {
        override suspend fun captureAndProcessSelection(
            selection: OverlaySelectionRegion,
            compressionQuality: Int,
        ): ScreenCaptureOutcome {
            launchConsentFlow()

            return when (val result = captureCoordinator.awaitResult()) {
                is CaptureResult.Failed -> ScreenCaptureOutcome.Failed(result.reason.toSearchError())
                is CaptureResult.Success -> processCapturedFrame(result, selection, compressionQuality)
            }
        }

        override suspend fun extractFallbackText(): String? = textExtractionFallback.extractVisibleText()

        private fun launchConsentFlow() {
            val consentIntent =
                Intent(context, MediaProjectionConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(consentIntent)
        }

        private suspend fun processCapturedFrame(
            result: CaptureResult.Success,
            selection: OverlaySelectionRegion,
            compressionQuality: Int,
        ): ScreenCaptureOutcome {
            if (blackFrameDetector.isBlankOrBlack(result.bitmap)) {
                result.bitmap.recycle()
                return ScreenCaptureOutcome.Blocked
            }

            val pixelRegion =
                CoordinateMapper.mapToBitmapSpace(
                    selectionInOverlaySpace = FloatBounds(selection.left, selection.top, selection.right, selection.bottom),
                    overlayWindowPixelSize = PixelSize(selection.overlayWindowWidthPx, selection.overlayWindowHeightPx),
                    capturedBitmapPixelSize = PixelSize(result.displayWidthPx, result.displayHeightPx),
                )
            val bytes = imageProcessor.process(result.bitmap, pixelRegion, compressionQuality)
            return ScreenCaptureOutcome.Success(bytes)
        }
    }

private fun CaptureFailureReason.toSearchError(): SearchError =
    when (this) {
        CaptureFailureReason.ConsentDenied -> SearchError.CaptureBlocked
        CaptureFailureReason.Timeout -> SearchError.Timeout
        CaptureFailureReason.CaptureError -> SearchError.CaptureBlocked
    }
