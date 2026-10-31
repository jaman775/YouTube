package com.drishyopot.app.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.drishyopot.app.diagnostics.DiagnosticLog

data class CastDevice(
    val id: String,
    val name: String,
    val model: String,
    val host: String,
    val port: Int,
)

/** Finds Google Cast devices on the local network using mDNS (`_googlecast._tcp`). */
class CastDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())

    val devices = mutableStateListOf<CastDevice>()
    var searching by mutableStateOf(false)
        private set

    private var listener: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val serviceToDevice = HashMap<String, String>()

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { main.post { searching = true } }
            override fun onDiscoveryStopped(serviceType: String) { main.post { searching = false } }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                DiagnosticLog.log("Cast", "Discovery failed to start ($errorCode)")
                main.post { searching = false; listener = null }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { main.post { enqueue(info) } }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post {
                    val id = serviceToDevice.remove(info.serviceName) ?: return@post
                    devices.removeAll { it.id == id }
                }
            }
        }
        listener = l
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            DiagnosticLog.log("Cast", "discoverServices threw", e)
            listener = null
        }
    }

    fun stop() {
        listener?.let { try { nsd.stopServiceDiscovery(it) } catch (_: IllegalArgumentException) { } }
        listener = null
        pending.clear()
        resolving = false
        searching = false
    }

    private fun enqueue(info: NsdServiceInfo) {
        pending.addLast(info)
        resolveNext()
    }

    // NsdManager can resolve only one service at a time.
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val info = pending.removeFirstOrNull() ?: return
        resolving = true
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    main.post { resolving = false; resolveNext() }
                }
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    main.post {
                        addDevice(resolved)
                        resolving = false
                        resolveNext()
                    }
                }
            })
        } catch (e: Exception) {
            DiagnosticLog.log("Cast", "resolveService threw", e)
            resolving = false
        }
    }

    @Suppress("DEPRECATION")
    private fun addDevice(info: NsdServiceInfo) {
        val host = info.host?.hostAddress ?: return
        fun attr(key: String) = info.attributes[key]?.toString(Charsets.UTF_8)
        val id = attr("id") ?: info.serviceName
        val device = CastDevice(
            id = id,
            name = attr("fn")?.takeIf { it.isNotBlank() } ?: info.serviceName,
            model = attr("md").orEmpty(),
            host = host,
            port = info.port.takeIf { it > 0 } ?: DEFAULT_PORT,
        )
        serviceToDevice[info.serviceName] = id
        val index = devices.indexOfFirst { it.id == id }
        if (index >= 0) devices[index] = device else devices += device
    }

    companion object {
        const val SERVICE_TYPE = "_googlecast._tcp."
        const val DEFAULT_PORT = 8009
    }
}
