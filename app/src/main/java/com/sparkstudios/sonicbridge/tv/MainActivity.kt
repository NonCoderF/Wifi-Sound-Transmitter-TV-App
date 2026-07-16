package com.sparkstudios.sonicbridge.tv

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.sparkstudios.sonicbridge.tv.ui.HomeScreen

class MainActivity : androidx.activity.ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private var isStreaming by mutableStateOf(false)

    /**
     * RECORD_AUDIO permission launcher
     */
    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (!granted)
                return@registerForActivityResult

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projectionLauncher.launch(
                    projectionManager.createScreenCaptureIntent()
                )
            }

        }

    /**
     * MediaProjection permission launcher
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private val projectionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != Activity.RESULT_OK)
                return@registerForActivityResult

            val serviceIntent =
                Intent(
                    this,
                    _root_ide_package_.com.sparkstudios.sonicbridge.tv.service.StreamingService::class.java
                ).apply {

                    putExtra(
                        _root_ide_package_.com.sparkstudios.sonicbridge.tv.service.StreamingService.Companion.EXTRA_RESULT_CODE,
                        result.resultCode
                    )

                    putExtra(
                        _root_ide_package_.com.sparkstudios.sonicbridge.tv.service.StreamingService.Companion.EXTRA_RESULT_DATA,
                        result.data
                    )

                }

            ContextCompat.startForegroundService(
                this,
                serviceIntent
            )

            isStreaming = true

        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        projectionManager =
            getSystemService(
                MediaProjectionManager::class.java
            )

        setContent {

            _root_ide_package_.com.sparkstudios.sonicbridge.tv.ui.theme.SoundTransmitterTheme {

                HomeScreen(

                    isStreaming = isStreaming,

                    onStartStreaming = {

                        startStreaming()

                    },

                    onStopStreaming = {

                        stopService(
                            Intent(
                                this,
                                _root_ide_package_.com.sparkstudios.sonicbridge.tv.service.StreamingService::class.java
                            )
                        )

                        isStreaming = false

                    }

                )

            }

        }

    }

    private fun startStreaming() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            audioPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )

            return
        }

        projectionLauncher.launch(
            projectionManager.createScreenCaptureIntent()
        )

    }

    override fun onResume() {

        super.onResume()

        isStreaming =
            _root_ide_package_.com.sparkstudios.sonicbridge.tv.service.StreamingService.Companion.isRunning

    }

}