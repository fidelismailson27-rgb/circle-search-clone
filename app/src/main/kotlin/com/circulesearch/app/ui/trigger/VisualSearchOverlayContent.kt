package com.circulesearch.app.ui.trigger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.circulesearch.app.domain.repository.OverlaySelectionRegion
import com.circulesearch.app.ui.overlay.SelectionCanvas
import com.circulesearch.app.ui.result.ResultBottomSheet
import com.circulesearch.app.ui.result.ResultUiState
import com.circulesearch.app.ui.result.ResultViewModel

/**
 * Root content of the one persistent overlay window a search interaction uses: the
 * lasso [SelectionCanvas] while idle (FR-003/FR-004), then [ResultBottomSheet] once a
 * search has started (FR-006). Both live in the same window so the transition between
 * them never involves a second system-level surface.
 */
@Composable
fun VisualSearchOverlayContent(
    resultViewModel: ResultViewModel,
    overlayWindowWidthPx: Int,
    overlayWindowHeightPx: Int,
    onCancel: () -> Unit,
    onResultDismissed: () -> Unit,
) {
    val uiState by resultViewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState is ResultUiState.Idle) {
            SelectionCanvas(
                modifier = Modifier.fillMaxSize(),
                onSelectionChanged = {},
                onSelectionConfirmed = { bounds ->
                    resultViewModel.startSearch(bounds.toOverlaySelectionRegion(overlayWindowWidthPx, overlayWindowHeightPx))
                },
                onSelectionTooSmall = {
                    // FR-021 — no-op beyond not submitting; the canvas itself already
                    // resets its in-progress path so the user can simply redraw.
                },
            )
            IconButton(
                onClick = onCancel,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }
        } else {
            ResultBottomSheet(viewModel = resultViewModel, onDismissed = onResultDismissed)
        }
    }
}

private fun Rect.toOverlaySelectionRegion(
    overlayWindowWidthPx: Int,
    overlayWindowHeightPx: Int,
) = OverlaySelectionRegion(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
    overlayWindowWidthPx = overlayWindowWidthPx,
    overlayWindowHeightPx = overlayWindowHeightPx,
)
