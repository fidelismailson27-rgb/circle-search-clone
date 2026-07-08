package com.circulesearch.app.data.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the `MediaProjection` for exactly one capture (research.md R1, constitution
 * X): calls [ServiceCompat.startForeground] with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`
 * *before* touching any `MediaProjection` API, registers [MediaProjection.Callback]
 * before `createVirtualDisplay()`, and explicitly stops the projection — and itself —
 * the instant the single frame is captured (or capture fails/times out). Never lives
 * longer than one search.
 */
@AndroidEntryPoint
class ScreenCaptureForegroundService : Service() {
    @Inject
    lateinit var captureCoordinator: CaptureCoordinator

    @Inject
    lateinit var singleFrameCapturer: SingleFrameCapturer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaProjection: MediaProjection? = null

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                // Terminal per research.md R1 — the projection can never be reused
                // regardless of why it stopped (our own .stop() call, the user tapping
                // the system status-bar chip, or a screen lock). Always clean up.
                singleFrameCapturer.releaseCurrentCapture()
                mediaProjection = null
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForegroundWithNotification()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, RESULT_CODE_NONE) ?: RESULT_CODE_NONE
        val resultData = intent?.parcelableExtra(EXTRA_RESULT_DATA)

        if (resultData == null) {
            serviceScope.launch {
                captureCoordinator.publish(CaptureResult.Failed(CaptureFailureReason.ConsentDenied))
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            serviceScope.launch {
                captureCoordinator.publish(CaptureResult.Failed(CaptureFailureReason.ConsentDenied))
            }
            stopSelf()
            return START_NOT_STICKY
        }
        projection.registerCallback(projectionCallback, null)
        mediaProjection = projection

        serviceScope.launch {
            val result = singleFrameCapturer.captureSingleFrame(projection)
            captureCoordinator.publish(result)
            // Explicit stop right after the single frame — a MediaProjection token can
            // never be reused for another createVirtualDisplay() call anyway (R1), so
            // holding onto it any longer only keeps the system's recording indicator
            // and this foreground service alive for no benefit.
            projection.stop()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // NOTE: on API 33+ (Android 13+), actually *displaying* this notification also
    // requires the runtime POST_NOTIFICATIONS permission, which is intentionally not
    // in this app's permission set (constitution's fixed list: SYSTEM_ALERT_WINDOW,
    // FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, INTERNET — "any
    // additional permission requires a constitution amendment"). Without it,
    // startForeground() below still succeeds and the service still runs correctly,
    // but the transparency notification silently won't be shown to the user on
    // API 33+. Flagging this as a real, known gap pending a constitution decision,
    // not fixing it unilaterally here.
    private fun startForegroundWithNotification() {
        val channel =
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "Visual search capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification: Notification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(com.circulesearch.app.R.string.app_name))
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
    }

    private fun Intent.parcelableExtra(key: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val RESULT_CODE_NONE = 0
        private const val NOTIFICATION_CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
