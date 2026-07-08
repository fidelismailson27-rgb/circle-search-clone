package com.circulesearch.app.ui.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** FR-003/FR-021: a large-enough drag confirms a selection; a near-tap drag is rejected. */
class SelectionCanvasTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dragLargerThanMinimumConfirmsASelection() {
        var confirmed: Rect? = null

        composeRule.setContent {
            SelectionCanvas(onSelectionChanged = {}, onSelectionConfirmed = { confirmed = it })
        }

        composeRule.onRoot().performTouchInput {
            swipe(start = Offset(50f, 50f), end = Offset(250f, 250f))
        }
        composeRule.waitForIdle()

        assertTrue(confirmed != null && confirmed!!.width >= MIN_SELECTION_SIDE_PX)
    }

    @Test
    fun tinyDragBelowMinimumSizeIsRejectedNotConfirmed() {
        var confirmed: Rect? = null
        var tooSmall = false

        composeRule.setContent {
            SelectionCanvas(
                onSelectionChanged = {},
                onSelectionConfirmed = { confirmed = it },
                onSelectionTooSmall = { tooSmall = true },
            )
        }

        composeRule.onRoot().performTouchInput {
            swipe(start = Offset(50f, 50f), end = Offset(52f, 52f))
        }
        composeRule.waitForIdle()

        assertNull(confirmed)
        assertTrue(tooSmall)
    }

    private companion object {
        const val MIN_SELECTION_SIDE_PX = 24f
    }
}
