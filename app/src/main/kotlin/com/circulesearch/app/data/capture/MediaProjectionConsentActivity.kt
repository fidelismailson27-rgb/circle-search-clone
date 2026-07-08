package com.circulesearch.app.data.capture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Translucent, non-exported Activity whose only job is hosting the
 * `MediaProjectionManager` consent Activity-Result flow — that system dialog
 * requires an Activity context. Launched only *after* the user has already confirmed
 * a selection in the overlay (research.md R1), never at trigger time, so it never
 * gates how quickly the selection overlay itself appears (SC-001).
 *
 * Every single capture needs a fresh instance of this flow: Android 14+ allows a
 * `MediaProjection` token to back exactly one `createVirtualDisplay()` call, so the
 * consent dialog is unavoidable on every search (research.md R1) — this Activity
 * exists to make that one unavoidable step as small and immediate as possible.
 */
@AndroidEntryPoint
class MediaProjectionConsentActivity : ComponentActivity() {
    @Inject
    lateinit var captureCoordinator: CaptureCoordinator

    private val requestProjection =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val serviceIntent =
                    Intent(this, ScreenCaptureForegroundService::class.java)
                        .putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                        .putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_DATA, data)
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                lifecycleScope.launch {
                    captureCoordinator.publish(CaptureResult.Failed(CaptureFailureReason.ConsentDenied))
                }
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        requestProjection.launch(projectionManager.createScreenCaptureIntent())
    }
}
