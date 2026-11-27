package com.example.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal sealed interface MqttDecodedPacket {
    data class ConnAck(val returnCode: Int) : MqttDecodedPacket
    data class SubAck(val packetId: Int) : MqttDecodedPacket
    data class Publish(val topic: String, val payload: String, val qos: Int, val packetId: Int?) : MqttDecodedPacket
    data object PingResp : MqttDecodedPacket
    data class Other(val packetType: Int) : MqttDecodedPacket
}

internal object MqttCodec {

    fun encodeConnect(clientId: String, keepAliveSec: Int = 25): ByteArray {
        val variableAndPayload = ByteArrayOutputStream().apply {
            writeUtf8("MQTT")
            write(0x04) // MQTT 3.1.1
            write(0x02) // Clean session
            write((keepAliveSec ushr 8) and 0xFF)
            write(keepAliveSec and 0xFF)
            writeUtf8(clientId)
        }.toByteArray()
        return wrapPacket(0x10, variableAndPayload)
    }

    fun encodeSubscribe(packetId: Int, topic: String, qos: Int = 0): ByteArray {
        val variableAndPayload = ByteArrayOutputStream().apply {
            write((packetId ushr 8) and 0xFF)
            write(packetId and 0xFF)
            writeUtf8(topic)
            write(qos and 0x03)
        }.toByteArray()
        return wrapPacket(0x82, variableAndPayload)
    }

    fun encodePublish(topic: String, payload: String): ByteArray {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val variableAndPayload = ByteArrayOutputStream().apply {
            writeUtf8(topic)
            write(payloadBytes)
        }.toByteArray()
        return wrapPacket(0x30, variableAndPayload)
    }

    fun encodePubAck(packetId: Int): ByteArray = byteArrayOf(
        0x40,
        0x02,
        ((packetId ushr 8) and 0xFF).toByte(),
        (packetId and 0xFF).toByte()
    )

    fun encodePingReq(): ByteArray = byteArrayOf(0xC0.toByte(), 0x00)

    fun encodeDisconnect(): ByteArray = byteArrayOf(0xE0.toByte(), 0x00)

    fun decodePackets(buffer: ByteArray): Pair<List<MqttDecodedPacket>, Int> {
        val packets = mutableListOf<MqttDecodedPacket>()
        var offset = 0
        while (offset + 2 <= buffer.size) {
            val header = buffer[offset].toInt() and 0xFF
            var multiplier = 1
            var remainingLength = 0
            var idx = offset + 1
            var completeHeader = false
            while (idx < buffer.size && multiplier <= 128 * 128 * 128) {
                val encoded = buffer[idx].toInt() and 0xFF
                remainingLength += (encoded and 127) * multiplier
                idx += 1
                if ((encoded and 128) == 0) {
                    completeHeader = true
                    break
                }
                multiplier *= 128
            }
            if (!completeHeader || idx + remainingLength > buffer.size) {
                break
            }

            val bodyStart = idx
            val bodyEnd = idx + remainingLength
            val packetType = (header ushr 4) and 0x0F
            when (packetType) {
                2 -> {
                    val rc = if (remainingLength >= 2) buffer[bodyStart + 1].toInt() and 0xFF else 0xFF
                    packets += MqttDecodedPacket.ConnAck(rc)
                }
                3 -> {
                    val qos = (header ushr 1) and 0x03
                    if (remainingLength >= 2) {
                        val topicLen = ((buffer[bodyStart].toInt() and 0xFF) shl 8) or
                            (buffer[bodyStart + 1].toInt() and 0xFF)
                        var cursor = bodyStart + 2
                        if (cursor + topicLen <= bodyEnd) {
                            val topic = String(buffer, cursor, topicLen, StandardCharsets.UTF_8)
                            cursor += topicLen
                            var packetId: Int? = null
                            if (qos > 0 && cursor + 2 <= bodyEnd) {
                                packetId = ((buffer[cursor].toInt() and 0xFF) shl 8) or
                                    (buffer[cursor + 1].toInt() and 0xFF)
                                cursor += 2
                            }
                            val payload = if (cursor <= bodyEnd) {
                                String(buffer, cursor, bodyEnd - cursor, StandardCharsets.UTF_8)
                            } else {
                                ""
                            }
                            packets += MqttDecodedPacket.Publish(topic, payload, qos, packetId)
                        }
                    }
                }
                9 -> {
                    val pid = if (remainingLength >= 2) {
                        ((buffer[bodyStart].toInt() and 0xFF) shl 8) or
                            (buffer[bodyStart + 1].toInt() and 0xFF)
                    } else 0
                    packets += MqttDecodedPacket.SubAck(pid)
                }
                13 -> packets += MqttDecodedPacket.PingResp
                else -> packets += MqttDecodedPacket.Other(packetType)
            }
            offset = bodyEnd
        }
        return packets to offset
    }

    private fun wrapPacket(fixedHeader: Int, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(body.size + 5)
        out.write(fixedHeader)
        var x = body.size
        do {
            var encodedByte = x % 128
            x /= 128
            if (x > 0) encodedByte = encodedByte or 128
            out.write(encodedByte)
        } while (x > 0)
        out.write(body)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeUtf8(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        write((bytes.size ushr 8) and 0xFF)
        write(bytes.size and 0xFF)
        write(bytes)
    }
}

class MqttWebSocketRelay(
    private val scope: CoroutineScope,
    private val clientId: String
) {
    private val brokers = listOf(
        "wss://broker.emqx.io:8084/mqtt",
        "wss://broker.hivemq.com:8884/mqtt"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val lock = Any()
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var receiveBuffer = ByteArray(0)

    private var activeTopic: String? = null
    private var closedManually: Boolean = false
    private var brokerIndex: Int = 0
    private var retryAttempt: Int = 0
    private var packetCounter: Int = 1

    private var onStateChanged: ((connected: Boolean, statusLabel: String) -> Unit)? = null
    private var onMessageReceived: ((topic: String, payload: String) -> Unit)? = null
    private var onRelayError: ((String) -> Unit)? = null

    fun connect(
        topic: String,
        onState: (connected: Boolean, statusLabel: String) -> Unit,
        onMessage: (topic: String, payload: String) -> Unit,
        onError: (String) -> Unit
    ) {
        disconnectInternal(manual = false)
        synchronized(lock) {
            activeTopic = topic
            closedManually = false
            brokerIndex = 0
            retryAttempt = 0
            onStateChanged = onState
            onMessageReceived = onMessage
            onRelayError = onError
        }
        openSocket()
    }

    fun publish(payload: String): Boolean {
        val ws: WebSocket
        val topic: String
        synchronized(lock) {
            ws = webSocket ?: return false
            topic = activeTopic ?: return false
        }
        val frame = MqttCodec.encodePublish(topic, payload).toByteString()
        return ws.send(frame)
    }

    fun disconnect() {
        disconnectInternal(manual = true)
    }

    private fun openSocket() {
        val topic: String
        val url: String
        synchronized(lock) {
            if (closedManually) return
            topic = activeTopic ?: return
            url = brokers[brokerIndex % brokers.size]
            receiveBuffer = ByteArray(0)
        }
        onStateChanged?.invoke(false, "Conectando a la red online...")

        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "mqtt")
            .build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (closedManually || this@MqttWebSocketRelay.webSocket !== webSocket) {
                        webSocket.close(1000, "Superseded")
                        return
                    }
                }
                webSocket.send(MqttCodec.encodeConnect(clientId).toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingBytes(webSocket, bytes.toByteArray(), topic)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleSocketLoss(webSocket, "Conexión cerrada ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleSocketLoss(webSocket, t.message ?: "Sin conexión a internet")
            }
        })

        synchronized(lock) {
            webSocket = ws
        }
    }

    private fun handleIncomingBytes(ws: WebSocket, chunk: ByteArray, subscribedTopic: String) {
        val packets: List<MqttDecodedPacket>
        synchronized(lock) {
            if (closedManually || webSocket !== ws) return
            val merged = ByteArray(receiveBuffer.size + chunk.size)
            System.arraycopy(receiveBuffer, 0, merged, 0, receiveBuffer.size)
            System.arraycopy(chunk, 0, merged, receiveBuffer.size, chunk.size)
            val (decoded, consumed) = MqttCodec.decodePackets(merged)
            packets = decoded
            receiveBuffer = if (consumed < merged.size) {
                merged.copyOfRange(consumed, merged.size)
            } else {
                ByteArray(0)
            }
        }

        packets.forEach { pkt ->
            when (pkt) {
                is MqttDecodedPacket.ConnAck -> {
                    if (pkt.returnCode == 0) {
                        val subId = synchronized(lock) { packetCounter++ }
                        ws.send(MqttCodec.encodeSubscribe(subId, subscribedTopic, qos = 0).toByteString())
                        retryAttempt = 0
                        startPingLoop(ws)
                        onStateChanged?.invoke(true, "Conectado online")
                    } else {
                        handleSocketLoss(ws, "Servidor rechazó conexión (${pkt.returnCode})")
                    }
                }
                is MqttDecodedPacket.Publish -> {
                    if (pkt.qos == 1 && pkt.packetId != null) {
                        ws.send(MqttCodec.encodePubAck(pkt.packetId).toByteString())
                    }
                    onMessageReceived?.invoke(pkt.topic, pkt.payload)
                }
                is MqttDecodedPacket.SubAck,
                MqttDecodedPacket.PingResp,
                is MqttDecodedPacket.Other -> Unit
            }
        }
    }

    private fun startPingLoop(ws: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000L)
                val current = synchronized(lock) { webSocket }
                if (current !== ws || closedManually) break
                ws.send(MqttCodec.encodePingReq().toByteString())
            }
        }
    }

    private fun handleSocketLoss(ws: WebSocket, reason: String) {
        val shouldRetry: Boolean
        val attempt: Int
        synchronized(lock) {
            if (closedManually || webSocket !== ws) return
            webSocket = null
            pingJob?.cancel()
            pingJob = null
            shouldRetry = activeTopic != null
            retryAttempt += 1
            brokerIndex = (brokerIndex + 1) % brokers.size
            attempt = retryAttempt
        }
        if (!shouldRetry) return
        onStateChanged?.invoke(false, "Reconectando...")
        if (attempt >= 4) {
            onRelayError?.invoke("No se pudo conectar al servidor online ($reason). Revisa tu internet.")
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            val backoffMs = (1500L * attempt.coerceAtMost(4))
            delay(backoffMs)
            val stillOpen = synchronized(lock) { !closedManually && activeTopic != null && webSocket == null }
            if (stillOpen) openSocket()
        }
    }

    private fun disconnectInternal(manual: Boolean) {
        val oldWs: WebSocket?
        synchronized(lock) {
            if (manual) {
                closedManually = true
                activeTopic = null
                onStateChanged = null
                onMessageReceived = null
                onRelayError = null
            }
            oldWs = webSocket
            webSocket = null
            receiveBuffer = ByteArray(0)
        }
        pingJob?.cancel()
        pingJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        if (oldWs != null) {
            runCatching {
                oldWs.send(MqttCodec.encodeDisconnect().toByteString())
                oldWs.close(1000, "Left room")
            }
        }
    }
}
