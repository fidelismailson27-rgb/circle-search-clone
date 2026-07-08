package com.circulesearch.app.ui.trigger

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.circulesearch.app.ui.overlay.SelectionOverlayWindow
import com.circulesearch.app.ui.result.ResultViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Coordinates one search interaction end to end: shows the single persistent overlay
 * window, hosts [VisualSearchOverlayContent] in it, and tears everything down on
 * cancel/dismiss. A fresh [ResultViewModel] (unscoped — a new instance per search) is
 * requested from [resultViewModelProvider] each time.
 *
 * `@Singleton`-scoped so [TriggerEntryActivity] can inject the *same* controller
 * instance regardless of which particular Activity instance triggered it — a second
 * trigger tears down any overlay the first one left showing (FR-018/FR-023 at the
 * trigger level, above and beyond [ResultViewModel]'s own in-flight-request superseding).
 */
@Singleton
class VisualSearchFlowController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val resultViewModelProvider: Provider<ResultViewModel>,
    ) {
        private var overlayWindow: SelectionOverlayWindow? = null
        private var activeResultViewModel: ResultViewModel? = null

        fun startNewSearch() {
            dismissCurrent()

            val metrics = currentDisplayMetrics()
            val window = SelectionOverlayWindow(context)
            val resultViewModel = resultViewModelProvider.get()
            overlayWindow = window
            activeResultViewModel = resultViewModel

            window.show {
                VisualSearchOverlayContent(
                    resultViewModel = resultViewModel,
                    overlayWindowWidthPx = metrics.widthPixels,
                    overlayWindowHeightPx = metrics.heightPixels,
                    onCancel = ::dismissCurrent,
                    onResultDismissed = ::dismissCurrent,
                )
            }
        }

        private fun dismissCurrent() {
            activeResultViewModel?.dismiss()
            activeResultViewModel?.close()
            activeResultViewModel = null
            overlayWindow?.dismiss()
            overlayWindow = null
        }

        @Suppress("DEPRECATION")
        private fun currentDisplayMetrics(): DisplayMetrics {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: error("WindowManager unavailable")
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            return metrics
        }
    }
