package com.sparkstudios.sonicbridge.tv.service

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StreamingService : android.app.Service() {

    companion object {

        private const val TAG = "StreamingService"

        private const val CHANNEL_ID = "stream_channel"

        private const val NOTIFICATION_ID = 1001

        const val EXTRA_RESULT_CODE = "resultCode"

        const val EXTRA_RESULT_DATA = "resultData"

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var audioCaptureManager: com.sparkstudios.sonicbridge.tv.audio.AudioCaptureManager

    private lateinit var webSocketServer: com.sparkstudios.sonicbridge.tv.network.WebSocketServer

    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {

        super.onCreate()

        Log.d(TAG, "Service Created")

        createNotificationChannel()

        audioCaptureManager =
            _root_ide_package_.com.sparkstudios.sonicbridge.tv.audio.AudioCaptureManager()

        webSocketServer =
            _root_ide_package_.com.sparkstudios.sonicbridge.tv.network.WebSocketServer(
                audioCaptureManager.audioChannel
            )

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle("Sound Transmitter")
                .setContentText("Streaming TV Audio")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(createPendingIntent())
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )

        }

    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (isRunning)
            return START_STICKY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startStreaming(intent)

        }

        return START_STICKY

    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startStreaming(
        intent: Intent?
    ) {

        val resultCode =
            intent?.getIntExtra(
                EXTRA_RESULT_CODE,
                Activity.RESULT_CANCELED
            ) ?: Activity.RESULT_CANCELED

        val resultData =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                intent?.getParcelableExtra(
                    EXTRA_RESULT_DATA,
                    Intent::class.java
                )

            } else {

                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(
                    EXTRA_RESULT_DATA
                )

            }

        if (resultCode != Activity.RESULT_OK || resultData == null) {

            Log.e(TAG, "MediaProjection Permission Missing")

            stopSelf()

            return

        }

        val projectionManager =
            getSystemService(
                MediaProjectionManager::class.java
            )

        mediaProjection =
            projectionManager.getMediaProjection(
                resultCode,
                resultData
            )

        if (mediaProjection == null) {

            Log.e(TAG, "MediaProjection Failed")

            stopSelf()

            return

        }

        Log.d(TAG, "Starting WebSocket")

        webSocketServer.start()

        Log.d(TAG, "Starting Audio Capture")

        audioCaptureManager.startCapture(
            mediaProjection!!
        )

        isRunning = true

        Log.d(TAG, "Streaming Started")

    }

    override fun onDestroy() {

        Log.d(TAG, "Stopping Service")

        isRunning = false

        audioCaptureManager.stopCapture()

        CoroutineScope(Dispatchers.IO).launch {
            webSocketServer.stop()
        }

        mediaProjection?.stop()

        mediaProjection = null

        stopForeground(STOP_FOREGROUND_REMOVE)

        super.onDestroy()

    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Streaming",
                NotificationManager.IMPORTANCE_LOW
            )

        manager.createNotificationChannel(channel)

    }

    private fun createPendingIntent(): PendingIntent {

        val intent =
            Intent(
                this,
                _root_ide_package_.com.sparkstudios.sonicbridge.tv.MainActivity::class.java
            )

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

    }

}