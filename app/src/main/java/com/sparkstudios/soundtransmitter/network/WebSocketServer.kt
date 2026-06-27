package com.sparkstudios.soundtransmitter.network

import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

class WebSocketServer(
    private val audioChannel: Channel<ByteArray>
) {

    companion object {
        private const val TAG = "WebSocketServer"
    }

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var broadcastJob: Job? = null

    private val sessions =
        ConcurrentHashMap.newKeySet<DefaultWebSocketServerSession>()

    fun start(port: Int = 8080) {

        if (server != null) return

        server = embeddedServer(CIO, port = port) {

            install(WebSockets)

            routing {

                webSocket("/audio") {

                    sessions.add(this)

                    Log.d(TAG, "Client Connected : ${sessions.size}")

                    try {

                        for (frame in incoming) {

                            if (frame is Frame.Close)
                                break

                        }

                    } finally {

                        sessions.remove(this)

                        Log.d(TAG, "Client Disconnected : ${sessions.size}")

                    }

                }

            }

        }

        server!!.start(wait = false)

        Log.d(TAG, "WebSocket Server Started")

        startBroadcaster()

    }

    private fun startBroadcaster() {

        broadcastJob = scope.launch {

            while (isActive) {

                val audio = audioChannel.receive()

                if (sessions.isEmpty())
                    continue

                val deadSessions = mutableListOf<DefaultWebSocketServerSession>()

                coroutineScope {

                    sessions.forEach { session ->

                        launch {

                            try {

                                session.send(
                                    Frame.Binary(
                                        fin = true,
                                        data = audio
                                    )
                                )

                            } catch (e: Exception) {

                                Log.e(
                                    TAG,
                                    "Send Failed",
                                    e
                                )

                                deadSessions.add(session)

                            }

                        }

                    }

                }

                deadSessions.forEach {

                    sessions.remove(it)

                }

            }

        }

    }

    suspend fun stop() {

        broadcastJob?.cancel()

        broadcastJob = null

        scope.cancel()

        sessions.forEach {

            try {

                it.close()

            } catch (_: Exception) {
            }

        }

        sessions.clear()

        server?.stop(
            gracePeriodMillis = 1000,
            timeoutMillis = 3000
        )

        server = null

        Log.d(TAG, "WebSocket Server Stopped")

    }

}