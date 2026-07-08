package com.circulesearch.app.data.capture

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the ambiguous cases research.md/T066 called out: a legitimately dark screen
 * must never trip the fallback, but a real `FLAG_SECURE` capture must always be
 * caught — even though real devices rarely render a *perfectly* uniform black frame
 * (the status/navigation bar chrome commonly remains visible around a blacked-out
 * app surface).
 */
class BlackFrameDetectorTest {
    private val detector = BlackFrameDetector()

    @Test
    fun `uniform true-black frame is detected as blocked`() {
        assertTrue(detector.isBlankOrBlack(uniformBitmap(color = argb(0, 0, 0))))
    }

    @Test
    fun `near-black but not exactly zero uniform frame is still detected as blocked`() {
        // Real FLAG_SECURE frames aren't always pure (0,0,0) due to encoding.
        assertTrue(detector.isBlankOrBlack(uniformBitmap(color = argb(2, 2, 2))))
    }

    @Test
    fun `mostly-black frame with a visible status bar strip is still detected as blocked`() {
        // The realistic failure case: FLAG_SECURE blacks out app content, but system
        // bars at the very top/bottom of the frame commonly remain visible — a naive
        // "every sampled pixel must be identical" check misses this entirely.
        assertTrue(detector.isBlankOrBlack(mostlyBlackWithStatusBarBitmap()))
    }

    @Test
    fun `legitimately dark but non-uniform photo is not flagged`() {
        assertFalse(detector.isBlankOrBlack(noisyDarkPhotoBitmap()))
    }

    @Test
    fun `uniform dark-mode UI surface color is not flagged as blocked`() {
        // Material dark theme's typical #121212 surface — legitimately dark, not FLAG_SECURE.
        assertFalse(detector.isBlankOrBlack(uniformBitmap(color = argb(0x12, 0x12, 0x12))))
    }

    @Test
    fun `uniform bright color is not flagged`() {
        assertFalse(detector.isBlankOrBlack(uniformBitmap(color = argb(255, 255, 255))))
    }

    private fun uniformBitmap(
        color: Int,
        size: Int = 100,
    ): Bitmap {
        val bitmap = mockk<Bitmap>()
        every { bitmap.width } returns size
        every { bitmap.height } returns size
        every { bitmap.getPixel(any(), any()) } returns color
        return bitmap
    }

    private fun mostlyBlackWithStatusBarBitmap(size: Int = 100): Bitmap {
        val bitmap = mockk<Bitmap>()
        every { bitmap.width } returns size
        every { bitmap.height } returns size
        every {
            bitmap.getPixel(any(), any())
        } answers {
            val y = secondArg<Int>()
            // Top ~7% of the frame (status bar) stays a normal, non-black color; the rest
            // (blacked-out app content) is pure black.
            if (y < size * STATUS_BAR_FRACTION) argb(30, 136, 229) else argb(0, 0, 0)
        }
        return bitmap
    }

    private fun noisyDarkPhotoBitmap(size: Int = 100): Bitmap {
        val bitmap = mockk<Bitmap>()
        every { bitmap.width } returns size
        every { bitmap.height } returns size
        every {
            bitmap.getPixel(any(), any())
        } answers {
            val x = firstArg<Int>()
            val y = secondArg<Int>()
            // Varies per pixel (20-60 range) so it's dark overall but never uniform or
            // within the strict near-black threshold.
            val shade = 20 + ((x * 7 + y * 13) % 40)
            argb(shade, shade, shade)
        }
        return bitmap
    }

    private fun argb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private companion object {
        const val STATUS_BAR_FRACTION = 0.07
    }
}
