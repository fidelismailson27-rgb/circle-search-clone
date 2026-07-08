package com.circulesearch.app.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts the selection overlay as a `WindowManager` `TYPE_APPLICATION_OVERLAY` surface
 * (constitution VI) — transparent over the live screen underneath, per research.md
 * R1's decoupling of overlay display from `MediaProjection` capture. Only needs the
 * once-granted `SYSTEM_ALERT_WINDOW` permission; no per-use system prompt.
 *
 * Compose has no first-class support for rendering outside an Activity, so this class
 * supplies its own [LifecycleOwner]/[ViewModelStoreOwner]/[SavedStateRegistryOwner] to
 * the hosted [ComposeView] — the standard pattern for WindowManager-hosted Compose
 * content (the same technique floating "chat head"-style overlays use).
 */
class SelectionOverlayWindow(
    private val context: Context,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    val isShowing: Boolean get() = composeView != null

    fun show(content: @Composable () -> Unit) {
        if (composeView != null) return

        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val view =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(this@SelectionOverlayWindow)
                setViewTreeViewModelStoreOwner(this@SelectionOverlayWindow)
                setViewTreeSavedStateRegistryOwner(this@SelectionOverlayWindow)
                setContent(content)
            }
        composeView = view

        val layoutParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )

        windowManager.addView(view, layoutParams)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Removes the overlay window. Idempotent — safe to call even if not currently showing. */
    fun dismiss() {
        val view = composeView ?: return
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        windowManager.removeView(view)
        composeView = null
    }
}
