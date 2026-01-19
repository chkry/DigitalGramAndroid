package com.digitalgram.android.sync

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.*

interface WebRTCManagerDelegate {
    fun onDataReceived(data: ByteArray)
    fun onConnectionStateChanged(state: PeerConnection.IceConnectionState)
    fun onOfferGenerated(sdp: SessionDescription)
    fun onAnswerGenerated(sdp: SessionDescription)
    fun onIceCandidateGenerated(candidate: IceCandidate)
}

class WebRTCManager(private val context: Context) {
    
    var delegate: WebRTCManagerDelegate? = null
    
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private val factory: PeerConnectionFactory
    
    private val localCandidates = mutableListOf<IceCandidate>()
    private var isInitiator: Boolean = false
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        // Initialize WebRTC
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        
        val options = PeerConnectionFactory.Options()
        
        val encoderFactory = DefaultVideoEncoderFactory(
            null, /* eglContext */
            true, /* enableIntelVp8Encoder */
            true  /* enableH264HighProfile */
        )
        
        val decoderFactory = DefaultVideoDecoderFactory(null)
        
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
    
    // MARK: - Connection Management
    
    fun createPeerConnection(asInitiator: Boolean) {
        this.isInitiator = asInitiator
        
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        
        val observer = object : PeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate) {
                localCandidates.add(candidate)
                delegate?.onIceCandidateGenerated(candidate)
            }
            
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                delegate?.onConnectionStateChanged(newState)
            }
            
            override fun onDataChannel(dc: DataChannel) {
                dataChannel = dc
                dc.registerObserver(DataChannelObserver())
            }
        }
        
        peerConnection = factory.createPeerConnection(rtcConfig, observer)
        
        if (asInitiator) {
            createDataChannel()
            createOffer()
        }
    }
    
    private fun createDataChannel() {
        val init = DataChannel.Init().apply {
            ordered = true
            negotiated = false
        }
        
        dataChannel = peerConnection?.createDataChannel("sync-channel", init)
        dataChannel?.registerObserver(DataChannelObserver())
    }
    
    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        delegate?.onOfferGenerated(sdp)
                    }
                    override fun onSetFailure(error: String?) {
                        println("Failed to set local description: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            
            override fun onCreateFailure(error: String?) {
                println("Failed to create offer: $error")
            }
            
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    fun handleRemoteOffer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                createAnswer()
            }
            
            override fun onSetFailure(error: String?) {
                println("Failed to set remote description: $error")
            }
            
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }
    
    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        delegate?.onAnswerGenerated(sdp)
                    }
                    override fun onSetFailure(error: String?) {
                        println("Failed to set local description: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            
            override fun onCreateFailure(error: String?) {
                println("Failed to create answer: $error")
            }
            
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    fun handleRemoteAnswer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                println("Remote answer set successfully")
            }
            
            override fun onSetFailure(error: String?) {
                println("Failed to set remote description: $error")
            }
            
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }
    
    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }
    
    // MARK: - Data Channel
    
    fun sendData(data: ByteArray): Boolean {
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) {
            println("Data channel not ready")
            return false
        }
        
        val buffer = DataChannel.Buffer(
            java.nio.ByteBuffer.wrap(data),
            true /* binary */
        )
        return channel.send(buffer)
    }
    
    fun sendMessage(message: SyncMessage): Boolean {
        val jsonString = json.encodeToString(message)
        return sendData(jsonString.toByteArray())
    }
    
    fun disconnect() {
        dataChannel?.close()
        dataChannel?.dispose()
        dataChannel = null
        
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        
        localCandidates.clear()
    }
    
    val isConnected: Boolean
        get() = dataChannel?.state() == DataChannel.State.OPEN
    
    // MARK: - Observers
    
    private open class PeerConnectionObserver : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(dc: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }
    
    private inner class DataChannelObserver : DataChannel.Observer {
        override fun onBufferedAmountChange(amount: Long) {}
        
        override fun onStateChange() {
            println("Data channel state: ${dataChannel?.state()}")
        }
        
        override fun onMessage(buffer: DataChannel.Buffer) {
            val data = ByteArray(buffer.data.remaining())
            buffer.data.get(data)
            delegate?.onDataReceived(data)
        }
    }
    
    fun dispose() {
        disconnect()
        factory.dispose()
    }
}
