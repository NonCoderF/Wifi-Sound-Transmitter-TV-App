package com.sparkstudios.sonicbridge.tv.network

import android.util.Log
import com.sparkstudios.sonicbridge.tv.audio.AudioCaptureManager
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

class WebSocketServer(
    private val audioChannel: Channel<AudioCaptureManager.AudioPacket>
) {

    companion object {
        private const val TAG = "WebSocketServer"
        // Three 10-ms packets provide a small jitter buffer for network and
        // receiver scheduling without allowing latency to grow indefinitely.
        private const val CLIENT_QUEUE_CAPACITY = 3
    }

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var broadcastJob: Job? = null

    private class ClientConnection(
        val session: DefaultWebSocketServerSession,
        private val scope: CoroutineScope,
        private val onSendFailure: (ClientConnection, Throwable) -> Unit
    ) {
        val queue = Channel<AudioCaptureManager.AudioPacket>(CLIENT_QUEUE_CAPACITY)
        private val depth = AtomicInteger(0)
        private var sentPacketCount = 0L
        private val senderJob = scope.launch {
            for (packet in queue) {
                decrementDepth()
                try {
                    val sendStartedNanos = System.nanoTime()
                    session.send(Frame.Binary(fin = true, data = packet.data))
                    val sendDurationMs = (System.nanoTime() - sendStartedNanos) / 1_000_000
                    val ageMs = (System.nanoTime() - packet.capturedAtNanos) / 1_000_000
                    sentPacketCount++
                    if (sentPacketCount % 100L == 0L) {
                        Log.d(
                            TAG,
                            "Sent packet #$sentPacketCount: size=${packet.data.size} bytes, " +
                                "duration=${AudioCaptureManager.PACKET_DURATION_MS} ms, " +
                                "send=${sendDurationMs} ms, capture-to-send=${ageMs} ms, " +
                                "queue depth=${depth.get()}"
                        )
                    }
                    if (sendDurationMs > AudioCaptureManager.PACKET_DURATION_MS) {
                        Log.w(
                            TAG,
                            "Slow client: send took ${sendDurationMs} ms for a " +
                                "${AudioCaptureManager.PACKET_DURATION_MS}-ms packet"
                        )
                    }
                } catch (error: Throwable) {
                    onSendFailure(this@ClientConnection, error)
                    queue.close()
                    break
                }
            }
        }

        @Synchronized
        fun enqueue(packet: AudioCaptureManager.AudioPacket): String {
            if (queue.trySend(packet).isSuccess) {
                return "queued depth=${depth.incrementAndGet()}"
            }

            // Keep the newest audio. Removing one old packet preserves the order of
            // packets that are actually sent while bounding latency to two packets.
            val dropped = queue.tryReceive().getOrNull()
            if (dropped != null) depth.decrementAndGet()

            return if (queue.trySend(packet).isSuccess) {
                "dropped stale packet; queued depth=${depth.incrementAndGet()}"
            } else {
                "dropped incoming packet; queue depth=${depth.get()}"
            }
        }

        @Synchronized
        private fun decrementDepth() {
            depth.decrementAndGet()
        }

        suspend fun close() {
            queue.close()
            senderJob.cancelAndJoin()
        }
    }

    private val clients = ConcurrentHashMap<DefaultWebSocketServerSession, ClientConnection>()

    fun start(port: Int = 8080) {

        if (server != null) return

        server = embeddedServer(CIO, port = port) {

            install(WebSockets)

            routing {

                webSocket("/audio") {

                    val client = ClientConnection(this, scope) { connection, error ->
                        Log.e(TAG, "Send failed; removing client", error)
                        clients.remove(connection.session)
                    }
                    clients[this] = client

                    Log.d(TAG, "Client Connected: ${clients.size}, queue capacity=$CLIENT_QUEUE_CAPACITY")

                    try {

                        for (frame in incoming) {

                            if (frame is Frame.Close)
                                break

                        }

                    } finally {

                        clients.remove(this)?.close()

                        Log.d(TAG, "Client Disconnected: ${clients.size}")

                    }

                }

            }

        }

        server!!.start(wait = false)

        Log.d(TAG, "WebSocket Server Started on port $port, endpoint=/audio")

        startBroadcaster()

    }

    private fun startBroadcaster() {

        broadcastJob = scope.launch {
            var packetCount = 0L

            while (isActive) {

                val packet = try {
                    audioChannel.receive()
                } catch (_: ClosedReceiveChannelException) {
                    break
                }

                if (clients.isEmpty())
                    continue

                packetCount++

                clients.values.forEach { client ->
                    val result = client.enqueue(packet)
                    if (packetCount % 100L == 0L) {
                        Log.d(
                            TAG,
                            "Broadcast packet #$packetCount: size=${packet.data.size} bytes, " +
                                "duration=${AudioCaptureManager.PACKET_DURATION_MS} ms, $result"
                        )
                    }
                }

            }

        }

    }

    suspend fun stop() {

        broadcastJob?.cancel()

        broadcastJob = null

        scope.cancel()

        clients.values.forEach { client ->
            try {
                client.session.close()
                client.close()
            } catch (_: Exception) {
            }
        }
        clients.clear()

        server?.stop(
            gracePeriodMillis = 1000,
            timeoutMillis = 3000
        )

        server = null

        Log.d(TAG, "WebSocket Server Stopped")

    }

}
