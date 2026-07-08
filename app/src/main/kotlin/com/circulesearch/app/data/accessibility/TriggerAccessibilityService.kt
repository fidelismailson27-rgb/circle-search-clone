package com.circulesearch.app.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Constitution IV: minimal event/flag subscription (see
 * `res/xml/accessibility_service_config.xml`) — this service does not react to
 * events continuously. Its purpose is exposing on-demand root-window text
 * extraction when image capture is blocked (US5's `TextExtractionFallback`), reading
 * [rootInActiveWindow] directly at the moment it's needed rather than accumulating
 * state from the event stream. Never logs event content.
 */
class TriggerAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — see class doc.
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: TriggerAccessibilityService? = null

        /** Null if the user hasn't enabled the service (or the system has since killed it). */
        fun currentInstance(): TriggerAccessibilityService? = instance
    }
}
