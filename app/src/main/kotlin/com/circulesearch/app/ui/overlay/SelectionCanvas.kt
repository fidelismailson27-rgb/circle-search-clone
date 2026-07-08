package com.circulesearch.app.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Freeform/rectangular lasso selection drawn over a transparent background
 * (constitution VI). The live drag's bounding box is reported via
 * [onSelectionChanged]; the finished selection via [onSelectionConfirmed] once the
 * drag ends and meets [MIN_SELECTION_SIZE_DP] on both axes (FR-021) — a smaller
 * selection instead calls [onSelectionTooSmall] so the caller can prompt a redraw,
 * rather than submitting a near-empty image. [SelectionOverlayWindow] hosts this over
 * the still-visible app underneath, per research.md R1.
 */
@Composable
fun SelectionCanvas(
    onSelectionChanged: (Rect?) -> Unit,
    onSelectionConfirmed: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    onSelectionTooSmall: () -> Unit = {},
) {
    var path by remember { mutableStateOf(Path()) }
    var boundingBox by remember { mutableStateOf<Rect?>(null) }
    val points = remember { mutableListOf<Offset>() }
    val minSelectionSidePx = with(LocalDensity.current) { MIN_SELECTION_SIZE_DP.dp.toPx() }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            points.clear()
                            points.add(offset)
                            path = Path().apply { moveTo(offset.x, offset.y) }
                            boundingBox = Rect(offset.x, offset.y, offset.x, offset.y)
                            onSelectionChanged(boundingBox)
                        },
                        onDrag = { change, _ ->
                            val position = change.position
                            points.add(position)
                            path.lineTo(position.x, position.y)
                            boundingBox = points.boundingBox()
                            onSelectionChanged(boundingBox)
                        },
                        onDragEnd = {
                            val box = boundingBox
                            if (box != null && box.width >= minSelectionSidePx && box.height >= minSelectionSidePx) {
                                onSelectionConfirmed(box)
                            } else {
                                onSelectionTooSmall()
                            }
                        },
                    )
                },
    ) {
        drawPath(path = path, color = Color.White, style = Stroke(width = STROKE_WIDTH_PX))
        boundingBox?.let { box ->
            drawRect(color = SELECTION_FILL_COLOR, topLeft = box.topLeft, size = box.size)
        }
    }
}

private fun List<Offset>.boundingBox(): Rect {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (point in this) {
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
    }
    return Rect(minX, minY, maxX, maxY)
}

private const val STROKE_WIDTH_PX = 4f
private val SELECTION_FILL_COLOR = Color.White.copy(alpha = 0.15f)

// Matches Android's standard minimum touch-target size — doubles as a reasonable
// "was this obviously not a deliberate drag" signal (research.md, FR-021).
private const val MIN_SELECTION_SIZE_DP = 24
