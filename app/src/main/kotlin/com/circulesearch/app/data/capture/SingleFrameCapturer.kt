package com.circulesearch.app.data.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Captures exactly ONE frame per [MediaProjection] instance passed in — verified
 * against Android's real platform behavior (research.md R1): a projection token may
 * back only a single `createVirtualDisplay()` call, ever. Tears down the
 * `VirtualDisplay`/`ImageReader` immediately after that one frame (constitution III),
 * bounded by a timeout so a frame that never arrives cannot hang the search
 * indefinitely (constitution V).
 */
@Singleton
class SingleFrameCapturer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Volatile private var activeImageReader: ImageReader? = null

        @Volatile private var activeVirtualDisplay: VirtualDisplay? = null

        suspend fun captureSingleFrame(projection: MediaProjection): CaptureResult {
            val metrics = currentDisplayMetrics()
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, IMAGE_READER_MAX_IMAGES)
            activeImageReader = imageReader

            val bitmap =
                withTimeoutOrNull(CAPTURE_TIMEOUT_MILLIS) {
                    awaitSingleFrame(projection, imageReader, width, height, metrics.densityDpi)
                }

            releaseCurrentCapture()

            return if (bitmap != null) {
                CaptureResult.Success(bitmap, width, height)
            } else {
                CaptureResult.Failed(CaptureFailureReason.Timeout)
            }
        }

        private suspend fun awaitSingleFrame(
            projection: MediaProjection,
            imageReader: ImageReader,
            width: Int,
            height: Int,
            densityDpi: Int,
        ): Bitmap =
            suspendCancellableCoroutine { continuation ->
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val captured = image.toBitmap(width, height)
                        image.close()
                        if (continuation.isActive) continuation.resume(captured)
                    }
                }, null)

                activeVirtualDisplay =
                    projection.createVirtualDisplay(
                        VIRTUAL_DISPLAY_NAME,
                        width,
                        height,
                        densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.surface,
                        null,
                        null,
                    )
            }

        /** Idempotent — safe to call even if nothing is currently active. */
        fun releaseCurrentCapture() {
            activeVirtualDisplay?.release()
            activeVirtualDisplay = null
            activeImageReader?.close()
            activeImageReader = null
        }

        @Suppress("DEPRECATION")
        private fun currentDisplayMetrics(): DisplayMetrics {
            // Context.getDisplay()/WindowMetrics is the modern replacement, but requires
            // API 30+; defaultDisplay.getRealMetrics remains correct and necessary across
            // this app's full minSdk 26 - targetSdk 35 range.
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: error("WindowManager unavailable")
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            return metrics
        }

        private companion object {
            const val CAPTURE_TIMEOUT_MILLIS = 2000L
            const val IMAGE_READER_MAX_IMAGES = 1
            const val VIRTUAL_DISPLAY_NAME = "circle_search_capture"
        }
    }

/** Converts an RGBA_8888 [Image] to a [Bitmap], accounting for row-stride padding. */
private fun Image.toBitmap(
    width: Int,
    height: Int,
): Bitmap {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width

    val paddedWidth = width + rowPadding / pixelStride
    val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    paddedBitmap.copyPixelsFromBuffer(plane.buffer)

    if (rowPadding == 0) return paddedBitmap

    val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
    paddedBitmap.recycle()
    return cropped
}
