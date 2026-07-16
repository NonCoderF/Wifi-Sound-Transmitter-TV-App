package com.sparkstudios.sonicbridge.tv.audio

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class AudioCaptureManager {

    companion object {
        private const val TAG = "AudioCaptureManager"
    }

    val audioChannel = Channel<ByteArray>(capacity = 8)

    private var audioRecord: AudioRecord? = null

    private var captureJob: Job? = null

    @Volatile
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.IO)

    private val sampleRate = 48000

    private val channelMask = AudioFormat.CHANNEL_IN_STEREO

    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    private val bufferSize =
        AudioRecord.getMinBufferSize(
            sampleRate,
            channelMask,
            encoding
        ) * 2

    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startCapture(
        mediaProjection: MediaProjection
    ) {

        if (isRecording)
            return

        val playbackConfig =
            AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

        val audioFormat =
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(channelMask)
                .build()

        audioRecord =
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {

            Log.e(TAG, "AudioRecord initialization failed")

            stopCapture()

            return
        }

        audioRecord?.startRecording()

        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {

            Log.e(TAG, "Recording failed")

            stopCapture()

            return
        }

        isRecording = true

        captureJob = scope.launch {

            Process.setThreadPriority(
                Process.THREAD_PRIORITY_AUDIO
            )

            val buffer = ByteArray(bufferSize)

            Log.d(TAG, "Capture Started")

            while (isRecording) {

                val read =
                    audioRecord?.read(
                        buffer,
                        0,
                        buffer.size,
                        AudioRecord.READ_BLOCKING
                    ) ?: break

                if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                    read == AudioRecord.ERROR_BAD_VALUE) {

                    Log.e(TAG, "AudioRecord error: $read")
                    continue
                }

                if (read <= 0)
                    continue

                val packet = ByteArray(read)
                System.arraycopy(buffer, 0, packet, 0, read)
                audioChannel.send(packet)

            }

            Log.d(TAG, "Capture Finished")

        }

    }

    fun stopCapture() {

        isRecording = false

        captureJob?.cancel()

        captureJob = null

        try {

            audioRecord?.stop()

        } catch (_: Exception) {
        }

        audioRecord?.release()

        audioRecord = null

        audioChannel.close()

    }

}