package com.digitalgram.android.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncEntry(
    val id: String,              // YYYY-MM-DD (entry date)
    val value: String,           // markdown content
    val timestamp: String,       // ISO8601 updated timestamp
    val deviceId: String,        // unique device identifier
    val vectorClock: Int,        // version number
    val isTombstone: Boolean     // true if entry was deleted
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncEntry) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

@Serializable
data class SyncMessage(
    val type: MessageType,
    val deviceId: String,
    val entries: List<SyncEntry>? = null,
    val timestamp: String
) {
    @Serializable
    enum class MessageType {
        HANDSHAKE,
        FULL_SYNC,
        DELTA_SYNC,
        ACK
    }
}

@Serializable
data class SyncMetadata(
    val entryId: String,
    val deviceId: String,
    var vectorClock: Int,
    var isTombstone: Boolean
)
