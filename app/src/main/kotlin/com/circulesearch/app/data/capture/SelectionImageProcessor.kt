package com.circulesearch.app.data.capture

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import com.circulesearch.app.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Crop → downscale → WebP (~q80) compression pipeline (constitution VI/VII/VIII),
 * always run on [DefaultDispatcher]. The full-resolution source [Bitmap] is
 * `recycle()`'d immediately after crop — at most one full-resolution frame is ever
 * alive at a time (constitution VII).
 */
class SelectionImageProcessor
    @Inject
    constructor(
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        suspend fun process(
            source: Bitmap,
            selectionRegion: Rect,
            compressionQuality: Int,
        ): ByteArray =
            withContext(defaultDispatcher) {
                val safeRegion = selectionRegion.clampedTo(source.width, source.height)
                val cropped =
                    Bitmap.createBitmap(source, safeRegion.left, safeRegion.top, safeRegion.width(), safeRegion.height())
                source.recycle()

                val downscaled = cropped.downscaleIfNeeded(MAX_DIMENSION_PX)
                if (downscaled !== cropped) cropped.recycle()

                val output = ByteArrayOutputStream()
                downscaled.compress(webpCompressFormat(), compressionQuality, output)
                downscaled.recycle()

                output.toByteArray()
            }

        private fun Rect.clampedTo(
            width: Int,
            height: Int,
        ): Rect {
            val safeLeft = left.coerceIn(0, width)
            val safeTop = top.coerceIn(0, height)
            val safeRight = right.coerceIn(safeLeft + 1, width)
            val safeBottom = bottom.coerceIn(safeTop + 1, height)
            return Rect(safeLeft, safeTop, safeRight, safeBottom)
        }

        private fun Bitmap.downscaleIfNeeded(maxDimension: Int): Bitmap {
            if (width <= maxDimension && height <= maxDimension) return this
            val scale = maxDimension.toFloat() / maxOf(width, height)
            val targetWidth = (width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        }

        private fun webpCompressFormat(): Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

        private companion object {
            // Generous enough for on-screen text/detail to survive AI analysis, small
            // enough to keep upload size/token cost bounded (constitution VIII).
            const val MAX_DIMENSION_PX = 1536
        }
    }
