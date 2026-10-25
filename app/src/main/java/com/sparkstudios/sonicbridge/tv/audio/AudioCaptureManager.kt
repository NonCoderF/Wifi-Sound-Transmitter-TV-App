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
import kotlinx.coroutines.channels.BufferOverflow
import kotlin.math.max

class AudioCaptureManager {

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 48_000
        const val CHANNEL_COUNT = 2
        const val BYTES_PER_SAMPLE = 2
        const val BYTES_PER_FRAME = CHANNEL_COUNT * BYTES_PER_SAMPLE
        const val PACKET_DURATION_MS = 10L
        const val PACKET_SIZE_BYTES = 1_920
        // Three 10-ms packets absorb short Android/Ktor scheduling hiccups
        // without allowing an unbounded latency backlog.
        private const val CHANNEL_CAPACITY = 3
    }

    data class AudioPacket(
        val data: ByteArray,
        val capturedAtNanos: Long
    )

    // Keep a small newest-first buffer. This prevents a single scheduler hiccup
    // from causing audible gaps while still bounding stale audio to 30 ms.
    val audioChannel = Channel<AudioPacket>(
        capacity = CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var audioRecord: AudioRecord? = null

    private var captureJob: Job? = null

    @Volatile
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.IO)

    private val sampleRate = SAMPLE_RATE

    private val channelMask = AudioFormat.CHANNEL_IN_STEREO

    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    private val minimumBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        channelMask,
        encoding
    )

    private val bufferSize = run {
        // Do not add an artificial 20-ms buffer. AudioRecord still enforces its
        // platform minimum, but we avoid making the app's own buffer larger.
        val required = max(minimumBufferSize, PACKET_SIZE_BYTES)
        // PCM stereo frames are four bytes; keep the recorder buffer frame-aligned.
        required + ((BYTES_PER_FRAME - (required % BYTES_PER_FRAME)) % BYTES_PER_FRAME)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startCapture(
        mediaProjection: MediaProjection
    ): Boolean {

        if (isRecording)
            return true

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

            return false
        }

        audioRecord?.startRecording()

        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {

            Log.e(TAG, "Recording failed")

            stopCapture()

            return false
        }

        isRecording = true

        captureJob = scope.launch {

            Process.setThreadPriority(
                Process.THREAD_PRIORITY_AUDIO
            )

            val packet = ByteArray(PACKET_SIZE_BYTES)
            var packetOffset = 0
            var packetCount = 0L

            Log.d(
                TAG,
                "Capture Started: ${PACKET_SIZE_BYTES} bytes, ${PACKET_DURATION_MS} ms, " +
                    "AudioRecord min buffer=$minimumBufferSize bytes, " +
                    "internal buffer=$bufferSize bytes, channel capacity=$CHANNEL_CAPACITY"
            )

            while (isRecording) {

                val read =
                    audioRecord?.read(
                        packet,
                        packetOffset,
                        PACKET_SIZE_BYTES - packetOffset,
                        AudioRecord.READ_BLOCKING
                    ) ?: break

                if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                    read == AudioRecord.ERROR_BAD_VALUE) {

                    Log.e(TAG, "AudioRecord error: $read")
                    continue
                }

                if (read <= 0)
                    continue

                if (read % BYTES_PER_FRAME != 0) {
                    Log.e(
                        TAG,
                        "AudioRecord returned $read bytes, not a complete stereo frame; " +
                            "discarding incomplete read"
                    )
                    continue
                }

                packetOffset += read

                if (packetOffset == PACKET_SIZE_BYTES) {
                    packetCount++
                    val capturedAtNanos = System.nanoTime()
                    val completePacket = packet.copyOf()
                    // Never wait behind old audio. DROP_OLDEST keeps the stream
                    // close to real time when the server or network is busy.
                    audioChannel.trySend(
                        AudioPacket(
                            data = completePacket,
                            capturedAtNanos = capturedAtNanos
                        )
                    )

                    if (packetCount % 100L == 0L) {
                        Log.d(
                            TAG,
                            "Packet #$packetCount queued: size=${completePacket.size} bytes, " +
                                "duration=${PACKET_DURATION_MS} ms, channel depth<=${CHANNEL_CAPACITY}"
                        )
                    }

                    packetOffset = 0
                }

            }

            Log.d(TAG, "Capture Finished")

        }

        return true

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
