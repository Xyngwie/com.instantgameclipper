package com.instantgameclipper.service

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class ClipperForegroundService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var projectionResultCode: Int = Activity.RESULT_CANCELED
    private var projectionResultData: Intent? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped")
            releaseProjection()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> stopSelf()
            else -> {
                if (!promoteToForeground()) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseProjection()
        ServiceState.setRunning(false)
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        if (!promoteToForeground()) {
            stopSelf()
            return
        }

        projectionResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        projectionResultData = intent.parcelableExtraCompat(EXTRA_RESULT_DATA)

        Log.i(
            TAG,
            "FGS running. projectionGranted=" +
                "${projectionResultData != null && projectionResultCode == Activity.RESULT_OK}",
        )
        ServiceState.setRunning(true)
    }

    private fun promoteToForeground(): Boolean {
        return runCatching {
            val notification: Notification = ServiceNotification.build(this)
            ServiceCompat.startForeground(
                this,
                ServiceNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
            true
        }.getOrElse { error ->
            Log.e(TAG, "startForeground(MEDIA_PROJECTION) failed", error)
            false
        }
    }

    internal fun acquireMediaProjection(): MediaProjection? {
        val data = projectionResultData ?: return null
        if (projectionResultCode != Activity.RESULT_OK) return null
        mediaProjection?.let { return it }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(projectionResultCode, data) ?: return null
        projection.registerCallback(projectionCallback, Handler(mainLooper))
        mediaProjection = projection
        return projection
    }

    private fun releaseProjection() {
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        projectionResultData = null
        projectionResultCode = Activity.RESULT_CANCELED
    }

    companion object {
        private const val TAG = "ClipperFgs"

        const val ACTION_START = "com.instantgameclipper.action.START"
        const val ACTION_STOP = "com.instantgameclipper.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ClipperForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ClipperForegroundService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}

private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}
