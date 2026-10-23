package com.jaafer.tvremote.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class TvDevice(val name: String, val host: String)

/** Finds Google TV / Android TV devices advertising the remote service over mDNS. */
class TvDiscovery(context: Context) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null

    val devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val scanning = MutableStateFlow(false)

    fun start() {
        if (listener != null) return
        lock = wifi?.createMulticastLock("tv-remote")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { scanning.value = true }
            override fun onDiscoveryStopped(serviceType: String) { scanning.value = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post { listener = null; scanning.value = false }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { main.post { enqueue(info) } }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { devices.update { list -> list.filterNot { it.name == info.serviceName } } }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { listener = null }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        scanning.value = false
        queue.clear()
        runCatching { lock?.release() }
        lock = null
    }

    private fun enqueue(info: NsdServiceInfo) {
        queue.addLast(info)
        resolveNext()
    }

    // NsdManager can only resolve one service at a time on older Android versions.
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val info = queue.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                main.post { resolving = false; resolveNext() }
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val address = info.host
                main.post {
                    if (address != null) {
                        val host = address.hostAddress
                        if (host != null) {
                            val device = TvDevice(info.serviceName, host)
                            devices.update { list ->
                                (list.filterNot { it.host == host || it.name == device.name } + device).sortedBy { it.name }
                            }
                        }
                    }
                    resolving = false
                    resolveNext()
                }
            }
        })
    }

    private companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp"
    }
}
