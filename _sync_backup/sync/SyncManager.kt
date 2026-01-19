package com.digitalgram.android.sync

import android.content.Context
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.text.SimpleDateFormat
import java.util.*

enum class SyncState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    SYNCING,
    CONNECTED,
    ERROR
}

interface SyncManagerDelegate {
    fun onSyncStateChanged(state: SyncState)
    fun onSyncUpdatesReceived(count: Int)
    fun onPeerFound(name: String)
}

class SyncManager private constructor(private val context: Context) {
    
    var delegate: SyncManagerDelegate? = null
    
    private val crdt: CRDTManager = CRDTManager.getInstance(context)
    private var webRTC: WebRTCManager? = null
    private var discovery: NetworkDiscovery? = null
    
    var state: SyncState = SyncState.IDLE
        private set(value) {
            field = value
            delegate?.onSyncStateChanged(value)
        }
    
    private val discoveredPeers = mutableListOf<DiscoveredPeer>()
    private var lastSyncTimestamp: String = ""
    private val json = Json { ignoreUnknownKeys = true }
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    
    init {
        iso8601Format.timeZone = TimeZone.getDefault()
    }
    
    // MARK: - Public API
    
    fun startDiscovery() {
        if (state != SyncState.IDLE) return
        
        state = SyncState.DISCOVERING
        
        val deviceId = crdt.getDeviceId().take(8)
        val deviceName = "DigitalGram-$deviceId"
        
        discovery = NetworkDiscovery(context, deviceName).apply {
            delegate = object : NetworkDiscoveryDelegate {
                override fun onPeerFound(peer: DiscoveredPeer) {
                    handlePeerFound(peer)
                }
                
                override fun onPeerLost(peer: DiscoveredPeer) {
                    handlePeerLost(peer)
                }
            }
            
            // Start advertising on a random high port
            val port = 50000 + Random().nextInt(10000)
            startAdvertising(port)
            
            // Start browsing for peers
            startBrowsing()
        }
        
        Log.d(TAG, "Started discovery as: $deviceName")
    }
    
    fun stopDiscovery() {
        discovery?.stopAdvertising()
        discovery?.stopBrowsing()
        discovery = null
        discoveredPeers.clear()
        
        if (state == SyncState.DISCOVERING) {
            state = SyncState.IDLE
        }
    }
    
    fun connectToPeer(peer: DiscoveredPeer) {
        if (state != SyncState.DISCOVERING) return
        
        state = SyncState.CONNECTING
        
        // Initialize WebRTC
        webRTC = WebRTCManager(context).apply {
            delegate = createWebRTCDelegate()
            createPeerConnection(asInitiator = true)
        }
        
        // Note: In production, you would exchange SDP and ICE candidates
        // through a signaling channel
    }
    
    fun handleConnectionRequest(peer: DiscoveredPeer) {
        if (state != SyncState.DISCOVERING) return
        
        state = SyncState.CONNECTING
        
        // Initialize WebRTC as responder
        webRTC = WebRTCManager(context).apply {
            delegate = createWebRTCDelegate()
            createPeerConnection(asInitiator = false)
        }
    }
    
    fun disconnect() {
        webRTC?.disconnect()
        webRTC = null
        
        if (state == SyncState.CONNECTED || state == SyncState.SYNCING) {
            state = SyncState.IDLE
        }
    }
    
    // MARK: - Sync Operations
    
    private fun performFullSync() {
        val webRTC = this.webRTC
        if (webRTC == null || !webRTC.isConnected) {
            state = SyncState.ERROR
            return
        }
        
        state = SyncState.SYNCING
        
        // Get all entries from local database
        val database = com.digitalgram.android.data.JournalDatabase.getInstance(context)
        val allEntries = database.getAllEntriesForSync()
        
        // Convert to sync entries
        val syncEntries = allEntries.map { entry ->
            val metadata = crdt.getSyncMetadata(entry.date)
            val vectorClock = metadata?.vectorClock ?: 0
            val isTombstone = metadata?.isTombstone ?: false
            
            crdt.createSyncEntry(entry, vectorClock, isTombstone)
        }
        
        // Send full sync message
        val message = SyncMessage(
            type = SyncMessage.MessageType.FULL_SYNC,
            deviceId = crdt.getDeviceId(),
            entries = syncEntries,
            timestamp = iso8601Format.format(Date())
        )
        
        if (webRTC.sendMessage(message)) {
            Log.d(TAG, "Sent full sync with ${syncEntries.size} entries")
            lastSyncTimestamp = iso8601Format.format(Date())
        } else {
            state = SyncState.ERROR
        }
    }
    
    private fun performDeltaSync() {
        val webRTC = this.webRTC ?: return
        if (!webRTC.isConnected) return
        
        // Get entries modified since last sync
        val database = com.digitalgram.android.data.JournalDatabase.getInstance(context)
        val modifiedEntries = database.getEntriesModifiedAfter(lastSyncTimestamp)
        
        if (modifiedEntries.isEmpty()) return
        
        val syncEntries = modifiedEntries.map { entry ->
            val metadata = crdt.getSyncMetadata(entry.date)
            val vectorClock = metadata?.vectorClock ?: 0
            val isTombstone = metadata?.isTombstone ?: false
            
            crdt.createSyncEntry(entry, vectorClock, isTombstone)
        }
        
        val message = SyncMessage(
            type = SyncMessage.MessageType.DELTA_SYNC,
            deviceId = crdt.getDeviceId(),
            entries = syncEntries,
            timestamp = iso8601Format.format(Date())
        )
        
        if (webRTC.sendMessage(message)) {
            Log.d(TAG, "Sent delta sync with ${syncEntries.size} entries")
        }
    }
    
    private fun handleReceivedSyncMessage(message: SyncMessage) {
        val entries = message.entries ?: return
        
        Log.d(TAG, "Received ${message.type} with ${entries.size} entries")
        
        // Merge entries using CRDT
        val (updates, deletions) = crdt.mergeEntries(entries)
        
        // Apply updates to database
        val database = com.digitalgram.android.data.JournalDatabase.getInstance(context)
        
        for (entry in updates) {
            database.saveEntry(entry)
            
            // Update sync metadata
            val remoteEntry = entries.find { it.id == entry.date }
            remoteEntry?.let {
                val metadata = SyncMetadata(
                    entryId = entry.date,
                    deviceId = it.deviceId,
                    vectorClock = it.vectorClock,
                    isTombstone = false
                )
                crdt.saveSyncMetadata(metadata)
            }
        }
        
        for (entryId in deletions) {
            database.deleteEntry(entryId)
        }
        
        // Notify delegate
        val totalChanges = updates.size + deletions.size
        if (totalChanges > 0) {
            delegate?.onSyncUpdatesReceived(totalChanges)
        }
        
        // Send acknowledgment
        val ackMessage = SyncMessage(
            type = SyncMessage.MessageType.ACK,
            deviceId = crdt.getDeviceId(),
            entries = null,
            timestamp = iso8601Format.format(Date())
        )
        webRTC?.sendMessage(ackMessage)
        
        state = SyncState.CONNECTED
    }
    
    // MARK: - Peer Discovery Handlers
    
    private fun handlePeerFound(peer: DiscoveredPeer) {
        discoveredPeers.add(peer)
        delegate?.onPeerFound(peer.name)
        Log.d(TAG, "Found peer: ${peer.name}")
    }
    
    private fun handlePeerLost(peer: DiscoveredPeer) {
        discoveredPeers.removeAll { it.id == peer.id }
        Log.d(TAG, "Lost peer: ${peer.name}")
    }
    
    // MARK: - WebRTC Delegate
    
    private fun createWebRTCDelegate() = object : WebRTCManagerDelegate {
        override fun onDataReceived(data: ByteArray) {
            try {
                val jsonString = String(data)
                val message = json.decodeFromString<SyncMessage>(jsonString)
                handleReceivedSyncMessage(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode sync message", e)
            }
        }
        
        override fun onConnectionStateChanged(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    this@SyncManager.state = SyncState.CONNECTED
                    performFullSync()
                }
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.CLOSED -> {
                    this@SyncManager.state = SyncState.IDLE
                    disconnect()
                }
                else -> {}
            }
        }
        
        override fun onOfferGenerated(sdp: SessionDescription) {
            Log.d(TAG, "Generated offer - implement signaling exchange")
            // In production: Send SDP offer through signaling channel
        }
        
        override fun onAnswerGenerated(sdp: SessionDescription) {
            Log.d(TAG, "Generated answer - implement signaling exchange")
            // In production: Send SDP answer through signaling channel
        }
        
        override fun onIceCandidateGenerated(candidate: IceCandidate) {
            Log.d(TAG, "Generated ICE candidate - implement signaling exchange")
            // In production: Send ICE candidate through signaling channel
        }
    }
    
    companion object {
        private const val TAG = "SyncManager"
        
        @Volatile
        private var INSTANCE: SyncManager? = null
        
        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
