package com.circulesearch.app.data.capture

import android.graphics.Bitmap
import javax.inject.Inject

/**
 * Sparse pixel-grid sampling heuristic to detect a black/blank captured frame —
 * typically caused by `FLAG_SECURE` on the foreground app (constitution V,
 * research.md).
 *
 * Hardened (T067) against two ambiguous cases found while writing
 * `BlackFrameDetectorTest` (T066):
 * - **False positive risk**: a legitimately dark, uniform color (e.g. Material dark
 *   theme's `#121212` surface) must not trip this — gated on the sampled color being
 *   *near-black* specifically, not merely flat/uniform.
 * - **False negative risk**: a real `FLAG_SECURE` capture is rarely *perfectly*
 *   uniform across the whole frame — the status/navigation bar chrome commonly
 *   remains visible around the blacked-out app content. Requiring every single
 *   sampled pixel to be identically black (the original v1 check) would miss this
 *   entirely. Hardened to a proportion threshold instead: blocked if the large
 *   majority of samples are near-black, tolerating a minority (system bars) that
 *   aren't.
 */
class BlackFrameDetector
    @Inject
    constructor() {
        fun isBlankOrBlack(bitmap: Bitmap): Boolean {
            val width = bitmap.width
            val height = bitmap.height
            if (width == 0 || height == 0) return true

            var nearBlackSamples = 0
            var totalSamples = 0
            for (row in 0 until SAMPLE_GRID_SIZE) {
                for (col in 0 until SAMPLE_GRID_SIZE) {
                    val x = (width - 1) * col / (SAMPLE_GRID_SIZE - 1)
                    val y = (height - 1) * row / (SAMPLE_GRID_SIZE - 1)
                    totalSamples++
                    if (isNearBlack(bitmap.getPixel(x, y))) nearBlackSamples++
                }
            }

            return totalSamples > 0 && nearBlackSamples.toFloat() / totalSamples >= BLOCKED_PROPORTION_THRESHOLD
        }

        private fun isNearBlack(color: Int): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return r < NEAR_BLACK_THRESHOLD && g < NEAR_BLACK_THRESHOLD && b < NEAR_BLACK_THRESHOLD
        }

        private companion object {
            const val SAMPLE_GRID_SIZE = 9
            const val NEAR_BLACK_THRESHOLD = 12

            // Tolerates a minority of non-black samples (status/navigation bar chrome)
            // while still requiring the large majority of the frame to be black.
            const val BLOCKED_PROPORTION_THRESHOLD = 0.85f
        }
    }
