package com.circulesearch.app.data.capture

import android.graphics.Bitmap
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process bridge between [ScreenCaptureForegroundService] (which owns the actual
 * `MediaProjection`/`VirtualDisplay` lifecycle) and whoever requested a capture
 * (`StartVisualSearchUseCase`, T034) — everything here runs in one process, so a
 * simple conflated channel is sufficient; no cross-process IPC is needed.
 */
@Singleton
class CaptureCoordinator
    @Inject
    constructor() {
        private val resultChannel = Channel<CaptureResult>(capacity = Channel.CONFLATED)

        suspend fun awaitResult(): CaptureResult = resultChannel.receive()

        suspend fun publish(result: CaptureResult) {
            resultChannel.send(result)
        }
    }

sealed interface CaptureResult {
    data class Success(val bitmap: Bitmap, val displayWidthPx: Int, val displayHeightPx: Int) : CaptureResult

    data class Failed(val reason: CaptureFailureReason) : CaptureResult
}

enum class CaptureFailureReason { ConsentDenied, Timeout, CaptureError }
