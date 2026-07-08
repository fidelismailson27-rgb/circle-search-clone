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

/**
 * Freeform/rectangular lasso selection drawn over a transparent background
 * (constitution VI). The live drag's bounding box is reported via
 * [onSelectionChanged]; the finished selection via [onSelectionConfirmed] once the
 * drag ends — [SelectionOverlayWindow] hosts this over the still-visible app
 * underneath, per research.md R1.
 */
@Composable
fun SelectionCanvas(
    onSelectionChanged: (Rect?) -> Unit,
    onSelectionConfirmed: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var path by remember { mutableStateOf(Path()) }
    var boundingBox by remember { mutableStateOf<Rect?>(null) }
    val points = remember { mutableListOf<Offset>() }

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
                            boundingBox?.let(onSelectionConfirmed)
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
