package com.circulesearch.app.ui.overlay

import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect

data class PixelSize(val width: Int, val height: Int)

/** Framework-neutral float rect — decouples [CoordinateMapper] from any UI toolkit's own Rect type. */
data class FloatBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Maps a selection bounding box from the overlay window's own pixel coordinate space
 * to the captured `Bitmap`'s pixel coordinate space (constitution VI, research.md
 * coordinate-mapping decision). Both sizes MUST be read fresh at the moment of the
 * specific capture they describe — never cached from a previous invocation — so this
 * stays correct across multi-window/split-screen and foldable posture changes that
 * may occur between when the overlay was shown and when capture actually completes.
 *
 * Pure geometry — no Compose/Android UI framework dependency — so it can be called
 * from the `data.capture` pipeline (which learns the captured `Bitmap`'s actual pixel
 * size only after capture) as well as from `ui.overlay`, without either layer
 * depending on the other's framework types (constitution II).
 */
object CoordinateMapper {
    fun mapToBitmapSpace(
        selectionInOverlaySpace: FloatBounds,
        overlayWindowPixelSize: PixelSize,
        capturedBitmapPixelSize: PixelSize,
    ): AndroidRect {
        require(overlayWindowPixelSize.width > 0 && overlayWindowPixelSize.height > 0) {
            "overlayWindowPixelSize must be positive, was $overlayWindowPixelSize"
        }

        val scaleX = capturedBitmapPixelSize.width.toFloat() / overlayWindowPixelSize.width
        val scaleY = capturedBitmapPixelSize.height.toFloat() / overlayWindowPixelSize.height

        val left = (selectionInOverlaySpace.left * scaleX).roundToInt()
        val top = (selectionInOverlaySpace.top * scaleY).roundToInt()
        val right = (selectionInOverlaySpace.right * scaleX).roundToInt()
        val bottom = (selectionInOverlaySpace.bottom * scaleY).roundToInt()

        return AndroidRect(
            left.coerceIn(0, capturedBitmapPixelSize.width),
            top.coerceIn(0, capturedBitmapPixelSize.height),
            right.coerceIn(0, capturedBitmapPixelSize.width),
            bottom.coerceIn(0, capturedBitmapPixelSize.height),
        )
    }
}
