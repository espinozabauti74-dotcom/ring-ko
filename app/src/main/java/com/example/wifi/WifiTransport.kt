package com.example.wifi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

const val WIFI_DEFAULT_PORT = 47474
private const val NSD_SERVICE_TYPE = "_ringko._tcp."
private const val TAG = "WifiTransport"

data class DiscoveredWifiRoom(
    val serviceName: String,
    val hostName: String,
    val hostIp: String,
    val port: Int,
    val rounds: Int
)

fun getLocalIpv4Addresses(): List<String> {
    val preferred = mutableListOf<String>()
    val secondary = mutableListOf<String>()
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            val name = iface.name.lowercase()
            val isWifiOrHotspot = name.startsWith("wlan") ||
                name.startsWith("ap") ||
                name.startsWith("swlan") ||
                name.startsWith("eth")
            for (addr in Collections.list(iface.inetAddresses)) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    val ip = addr.hostAddress ?: continue
                    if (isWifiOrHotspot) preferred.add(ip) else secondary.add(ip)
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to enumerate local IPv4 addresses", e)
    }
    return (preferred + secondary).distinct()
}

class WifiDiscoveryManager(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _rooms = MutableStateFlow<List<DiscoveredWifiRoom>>(emptyList())
    val rooms: StateFlow<List<DiscoveredWifiRoom>> = _rooms.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var isResolving = false

    fun startDiscovery() {
        val manager = nsdManager ?: return
        if (discoveryListener != null) return

        try {
            multicastLock = wifiManager?.createMulticastLock("RingKoDiscoveryLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock unavailable: ${e.message}")
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _isScanning.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceName.startsWith("RingKO-")) return
                enqueueResolve(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _rooms.update { list -> list.filterNot { it.serviceName == service.serviceName } }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD startDiscovery failed: $errorCode")
                _isScanning.value = false
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD stopDiscovery failed: $errorCode")
                _isScanning.value = false
            }
        }

        discoveryListener = listener
        try {
            manager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices threw exception", e)
            discoveryListener = null
            _isScanning.value = false
        }
    }

    @Synchronized
    private fun enqueueResolve(service: NsdServiceInfo) {
        if (resolveQueue.none { it.serviceName == service.serviceName }) {
            resolveQueue.addLast(service)
        }
        if (!isResolving) {
            resolveNextLocked()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNextLocked() {
        val next = resolveQueue.removeFirstOrNull()
        if (next == null) {
            isResolving = false
            return
        }
        val manager = nsdManager ?: run {
            isResolving = false
            return
        }
        isResolving = true
        try {
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "NSD resolve failed ($errorCode) for ${serviceInfo.serviceName}")
                    synchronized(this@WifiDiscoveryManager) {
                        resolveNextLocked()
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val ip = extractHostIp(serviceInfo)
                    val port = serviceInfo.port
                    if (!ip.isNullOrBlank() && port > 0) {
                        val parsed = parseServiceMetadata(serviceInfo.serviceName, ip, port)
                        _rooms.update { current ->
                            (current.filterNot { it.serviceName == parsed.serviceName } + parsed)
                        }
                    }
                    synchronized(this@WifiDiscoveryManager) {
                        resolveNextLocked()
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "resolveService threw exception", e)
            isResolving = false
        }
    }

    @Suppress("DEPRECATION")
    private fun extractHostIp(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val v4 = info.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
            if (!v4.isNullOrBlank()) return v4
            val any = info.hostAddresses.firstOrNull()?.hostAddress
            if (!any.isNullOrBlank()) return any
        }
        return info.host?.hostAddress
    }

    private fun parseServiceMetadata(serviceName: String, ip: String, port: Int): DiscoveredWifiRoom {
        // Format: RingKO-<3R|10R>-<HostName>
        val parts = serviceName.split("-", limit = 3)
        val rounds = if (parts.getOrNull(1) == "10R") 10 else 3
        val hostName = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "Retador"
        return DiscoveredWifiRoom(
            serviceName = serviceName,
            hostName = hostName,
            hostIp = ip,
            port = port,
            rounds = rounds
        )
    }

    fun stopDiscovery() {
        val listener = discoveryListener
        discoveryListener = null
        if (listener != null) {
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
        synchronized(this) {
            resolveQueue.clear()
            isResolving = false
        }
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {
        }
        multicastLock = null
        _isScanning.value = false
    }
}

class WifiMatchTransport(
    context: Context,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var sessionJob: Job? = null
    private var writerJob: Job? = null
    private val outboundQueue = Channel<String>(capacity = Channel.UNLIMITED)

    @Volatile
    private var closed = false

    fun startHost(
        hostName: String,
        hostGloveId: String,
        rounds: Int,
        onWaiting: (ips: List<String>, port: Int) -> Unit,
        onConnected: (oppName: String, oppGloveId: String) -> Unit,
        onPacket: (JSONObject) -> Unit,
        onError: (String) -> Unit,
        onDisconnected: () -> Unit
    ) {
        close()
        closed = false

        sessionJob = scope.launch(Dispatchers.IO) {
            try {
                val server = try {
                    ServerSocket(WIFI_DEFAULT_PORT)
                } catch (_: Exception) {
                    ServerSocket(0)
                }
                serverSocket = server
                val boundPort = server.localPort

                registerNsdService(hostName, rounds, boundPort)
                val ips = getLocalIpv4Addresses()
                withContext(Dispatchers.Main) {
                    onWaiting(ips, boundPort)
                }

                val client = server.accept()
                client.tcpNoDelay = true
                socket = client
                // Stop accepting additional clients & stop NSD advertising once matched
                try {
                    server.close()
                } catch (_: Exception) {
                }
                serverSocket = null
                unregisterNsd()

                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val out = BufferedWriter(OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8))
                writer = out
                startWriterLoop(out)

                val firstLine = reader.readLine()
                    ?: throw IllegalStateException("El rival cerró la conexión antes de iniciar.")
                val hello = JSONObject(firstLine)
                val oppName = hello.optString("name", "Retador").take(18).ifBlank { "Retador" }
                val oppGloveId = hello.optString("gloveId", "cobalt_flash")

                sendJson(
                    JSONObject().apply {
                        put("type", "WELCOME")
                        put("name", hostName)
                        put("gloveId", hostGloveId)
                        put("rounds", rounds)
                    }
                )

                withContext(Dispatchers.Main) {
                    onConnected(oppName, oppGloveId)
                }

                readLoop(reader, onPacket, onDisconnected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!closed) {
                    Log.w(TAG, "Host Wi-Fi socket error", e)
                    withContext(Dispatchers.Main) {
                        onError(e.localizedMessage ?: "No se pudo abrir la sala Wi-Fi.")
                    }
                }
            }
        }
    }

    fun startGuest(
        hostIp: String,
        port: Int,
        guestName: String,
        guestGloveId: String,
        onConnected: (oppName: String, oppGloveId: String, rounds: Int) -> Unit,
        onPacket: (JSONObject) -> Unit,
        onError: (String) -> Unit,
        onDisconnected: () -> Unit
    ) {
        close()
        closed = false

        sessionJob = scope.launch(Dispatchers.IO) {
            try {
                val client = Socket()
                client.tcpNoDelay = true
                client.connect(InetSocketAddress(hostIp.trim(), port), 7000)
                socket = client

                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val out = BufferedWriter(OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8))
                writer = out
                startWriterLoop(out)

                sendJson(
                    JSONObject().apply {
                        put("type", "HELLO")
                        put("name", guestName)
                        put("gloveId", guestGloveId)
                    }
                )

                val firstLine = reader.readLine()
                    ?: throw IllegalStateException("No hubo respuesta del anfitrión.")
                val welcome = JSONObject(firstLine)
                val oppName = welcome.optString("name", "Anfitrión").take(18).ifBlank { "Anfitrión" }
                val oppGloveId = welcome.optString("gloveId", "classic_red")
                val rounds = if (welcome.optInt("rounds", 3) == 10) 10 else 3

                withContext(Dispatchers.Main) {
                    onConnected(oppName, oppGloveId, rounds)
                }

                readLoop(reader, onPacket, onDisconnected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!closed) {
                    Log.w(TAG, "Guest Wi-Fi socket error", e)
                    withContext(Dispatchers.Main) {
                        onError("No se pudo conectar a $hostIp:$port. Verifica que ambos estén en el mismo Wi-Fi.")
                    }
                }
            }
        }
    }

    private suspend fun readLoop(
        reader: BufferedReader,
        onPacket: (JSONObject) -> Unit,
        onDisconnected: () -> Unit
    ) {
        while (!closed) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            try {
                val json = JSONObject(line)
                withContext(Dispatchers.Main) {
                    onPacket(json)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Malformed Wi-Fi packet ignored: $line", e)
            }
        }
        if (!closed) {
            withContext(Dispatchers.Main) {
                onDisconnected()
            }
        }
    }

    private fun startWriterLoop(out: BufferedWriter) {
        writerJob?.cancel()
        writerJob = scope.launch(Dispatchers.IO) {
            try {
                for (line in outboundQueue) {
                    if (closed) break
                    out.write(line)
                    out.newLine()
                    out.flush()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun sendJson(obj: JSONObject) {
        if (closed) return
        outboundQueue.trySend(obj.toString())
    }

    private fun registerNsdService(hostName: String, rounds: Int, port: Int) {
        val manager = nsdManager ?: return
        try {
            multicastLock = wifiManager?.createMulticastLock("RingKoHostLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }

        val safeName = hostName
            .replace(Regex("[^a-zA-Z0-9 ]"), "")
            .trim()
            .take(16)
            .ifBlank { "Retador" }
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "RingKO-${rounds}R-$safeName"
            serviceType = NSD_SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "registerService threw exception", e)
            registrationListener = null
        }
    }

    private fun unregisterNsd() {
        val listener = registrationListener
        registrationListener = null
        if (listener != null) {
            try {
                nsdManager?.unregisterService(listener)
            } catch (_: Exception) {
            }
        }
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {
        }
        multicastLock = null
    }

    fun close() {
        closed = true
        unregisterNsd()
        sessionJob?.cancel()
        writerJob?.cancel()
        sessionJob = null
        writerJob = null
        // Drain queued messages
        while (outboundQueue.tryReceive().isSuccess) {
        }
        scope.launch(Dispatchers.IO) {
            try {
                writer?.close()
            } catch (_: Exception) {
            }
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            try {
                serverSocket?.close()
            } catch (_: Exception) {
            }
            writer = null
            socket = null
            serverSocket = null
        }
    }
}
