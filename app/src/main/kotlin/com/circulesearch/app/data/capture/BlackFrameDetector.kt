package com.circulesearch.app.data.capture

import android.graphics.Bitmap
import javax.inject.Inject

/**
 * Sparse pixel-grid sampling heuristic to detect a black/blank captured frame —
 * typically caused by `FLAG_SECURE` on the foreground app (constitution V,
 * research.md). v1: gates on the sampled color being *near-black*, not merely flat,
 * so a legitimately flat but brightly-colored screen doesn't trip this heuristic.
 * Wired here to a baseline "couldn't capture this screen" failure for the MVP;
 * hardened against ambiguous cases and routed to the text-extraction fallback by
 * US5 (T066-T068).
 */
class BlackFrameDetector
    @Inject
    constructor() {
        fun isBlankOrBlack(bitmap: Bitmap): Boolean {
            val width = bitmap.width
            val height = bitmap.height
            if (width == 0 || height == 0) return true

            var referenceColor: Int? = null
            for (row in 0 until SAMPLE_GRID_SIZE) {
                for (col in 0 until SAMPLE_GRID_SIZE) {
                    val x = (width - 1) * col / (SAMPLE_GRID_SIZE - 1)
                    val y = (height - 1) * row / (SAMPLE_GRID_SIZE - 1)
                    val pixel = bitmap.getPixel(x, y)
                    val previous = referenceColor
                    if (previous == null) {
                        referenceColor = pixel
                    } else if (pixel != previous) {
                        return false
                    }
                }
            }
            return referenceColor?.let(::isNearBlack) ?: true
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
        }
    }
