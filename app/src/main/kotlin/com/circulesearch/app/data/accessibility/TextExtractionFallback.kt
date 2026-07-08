package com.circulesearch.app.data.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject

/**
 * On-demand text extraction from the current screen's accessibility node tree —
 * invoked only when [com.circulesearch.app.data.capture.BlackFrameDetector] flags a
 * blocked/blank capture (constitution IV/V, FR-019). Never runs continuously; walks
 * the tree once per call and releases every node immediately after use.
 */
class TextExtractionFallback
    @Inject
    constructor() {
        fun extractVisibleText(): String? {
            val service = TriggerAccessibilityService.currentInstance() ?: return null
            val root = service.rootInActiveWindow ?: return null
            return try {
                val builder = StringBuilder()
                collectText(root, builder)
                builder.toString().trim().takeIf { it.isNotEmpty() }
            } finally {
                root.recycleCompat()
            }
        }

        private fun collectText(
            node: AccessibilityNodeInfo,
            builder: StringBuilder,
        ) {
            node.text?.let { text ->
                if (text.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(text)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectText(child, builder)
                child.recycleCompat()
            }
        }

        // AccessibilityNodeInfo.recycle() is a documented no-op from API 33 onward (node
        // pooling was removed) but is still meaningful on this app's minSdk 26 - 32 range.
        @Suppress("DEPRECATION")
        private fun AccessibilityNodeInfo.recycleCompat() = recycle()
    }
