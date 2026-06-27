package com.sparkstudios.soundtransmitter

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import com.sparkstudios.soundtransmitter.service.StreamingService
import com.sparkstudios.soundtransmitter.ui.HomeScreen
import com.sparkstudios.soundtransmitter.ui.theme.SoundTransmitterTheme

class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private var isStreaming by mutableStateOf(false)

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
                    StreamingService::class.java
                ).apply {

                    putExtra(
                        StreamingService.EXTRA_RESULT_CODE,
                        result.resultCode
                    )

                    putExtra(
                        StreamingService.EXTRA_RESULT_DATA,
                        result.data
                    )

                }

            startForegroundService(serviceIntent)

            isStreaming = true

        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        projectionManager =
            getSystemService(
                MediaProjectionManager::class.java
            )

        setContent {

            SoundTransmitterTheme {

                HomeScreen(

                    isStreaming = isStreaming,

                    onStartStreaming = {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                            projectionLauncher.launch(
                                projectionManager.createScreenCaptureIntent()
                            )

                        }

                    },

                    onStopStreaming = {

                        stopService(
                            Intent(
                                this,
                                StreamingService::class.java
                            )
                        )

                        isStreaming = false

                    }

                )

            }

        }

    }

    override fun onResume() {

        super.onResume()

        isStreaming =
            StreamingService.isRunning

    }

}