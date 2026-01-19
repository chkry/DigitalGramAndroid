package com.digitalgram.android.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

interface NetworkDiscoveryDelegate {
    fun onPeerFound(peer: DiscoveredPeer)
    fun onPeerLost(peer: DiscoveredPeer)
}

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscoveredPeer) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class NetworkDiscovery(context: Context, private val deviceName: String = "DigitalGram-Android") {
    
    var delegate: NetworkDiscoveryDelegate? = null
    
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceName = "_digitalgram-sync._tcp"
    private val discoveredPeers = mutableSetOf<DiscoveredPeer>()
    
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    
    // MARK: - Service Advertisement
    
    fun startAdvertising(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = serviceName
            setPort(port)
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service registration failed: $errorCode")
            }
            
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }
            
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")
            }
            
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
            }
        }
        
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register service", e)
        }
    }
    
    fun stopAdvertising() {
        try {
            registrationListener?.let {
                nsdManager.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering service", e)
        }
        registrationListener = null
    }
    
    // MARK: - Service Discovery
    
    fun startBrowsing() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
            
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started")
            }
            
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                
                // Skip our own service
                if (serviceInfo.serviceName == deviceName) {
                    return
                }
                
                // Resolve the service to get host and port
                resolveService(serviceInfo)
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                
                val peerId = "${serviceInfo.serviceName}.$serviceName"
                val peer = discoveredPeers.find { it.id == peerId }
                peer?.let {
                    discoveredPeers.remove(it)
                    delegate?.onPeerLost(it)
                }
            }
        }
        
        try {
            nsdManager.discoverServices(serviceName, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
        }
    }
    
    fun stopBrowsing() {
        try {
            discoveryListener?.let {
                nsdManager.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
        discoveryListener = null
        discoveredPeers.clear()
    }
    
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service resolved: ${serviceInfo.serviceName}")
                
                val peer = DiscoveredPeer(
                    id = "${serviceInfo.serviceName}.$serviceName",
                    name = serviceInfo.serviceName,
                    host = serviceInfo.host.hostAddress ?: "",
                    port = serviceInfo.port
                )
                
                if (discoveredPeers.add(peer)) {
                    delegate?.onPeerFound(peer)
                }
            }
        }
        
        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve service", e)
        }
    }
    
    companion object {
        private const val TAG = "NetworkDiscovery"
    }
}
