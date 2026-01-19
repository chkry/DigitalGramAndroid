package com.digitalgram.android.sync

import android.content.Context
import android.content.SharedPreferences
import com.digitalgram.android.data.JournalDatabase
import com.digitalgram.android.data.JournalEntry
import java.util.*

class CRDTManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val deviceId: String
    private val database: JournalDatabase = JournalDatabase.getInstance(context)
    
    init {
        // Generate or retrieve persistent device ID
        deviceId = prefs.getString(DEVICE_ID_KEY, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(DEVICE_ID_KEY, newId).apply()
            newId
        }
    }
    
    fun getDeviceId(): String = deviceId
    
    // MARK: - LWW (Last-Write-Wins) Merge Logic
    
    /**
     * Merge remote entry with local entry using LWW-Element-Set CRDT
     * Returns: Pair(shouldUpdate: Boolean, mergedEntry: JournalEntry?)
     */
    fun merge(local: JournalEntry?, remote: SyncEntry): Pair<Boolean, JournalEntry?> {
        // If remote is tombstone (deletion)
        if (remote.isTombstone) {
            return if (local != null) {
                // Remote deletion wins, mark for deletion
                Pair(true, null)
            } else {
                Pair(false, null)
            }
        }
        
        // If no local entry exists, accept remote
        if (local == null) {
            val entry = createJournalEntry(remote)
            return Pair(true, entry)
        }
        
        // Compare timestamps (ISO8601 format allows lexicographic comparison)
        val remoteTimestamp = remote.timestamp
        val localTimestamp = local.updated
        
        return when {
            remoteTimestamp > localTimestamp -> {
                // Remote is newer
                val entry = createJournalEntry(remote)
                Pair(true, entry)
            }
            remoteTimestamp < localTimestamp -> {
                // Local is newer, no update needed
                Pair(false, null)
            }
            else -> {
                // Timestamps are equal, use device ID as tie-breaker
                if (remote.deviceId > deviceId) {
                    val entry = createJournalEntry(remote)
                    Pair(true, entry)
                } else {
                    Pair(false, null)
                }
            }
        }
    }
    
    /**
     * Merge multiple remote entries with local database
     */
    fun mergeEntries(remoteEntries: List<SyncEntry>): Pair<List<JournalEntry>, List<String>> {
        val updates = mutableListOf<JournalEntry>()
        val deletions = mutableListOf<String>()
        
        for (remote in remoteEntries) {
            // Get local entry if exists
            val local = database.getEntry(remote.id)
            
            val (shouldUpdate, mergedEntry) = merge(local, remote)
            
            if (shouldUpdate) {
                if (mergedEntry != null) {
                    updates.add(mergedEntry)
                } else {
                    // Tombstone - mark for deletion
                    deletions.add(remote.id)
                }
            }
        }
        
        return Pair(updates, deletions)
    }
    
    // MARK: - Conversion Helpers
    
    private fun createJournalEntry(syncEntry: SyncEntry): JournalEntry {
        val components = syncEntry.id.split("-").map { it.toIntOrNull() ?: 0 }
        val year = components[0]
        val month = components[1]
        val day = components[2]
        
        return JournalEntry(
            date = syncEntry.id,
            year = year,
            month = month,
            day = day,
            content = syncEntry.value,
            created = syncEntry.timestamp, // Use remote timestamp
            updated = syncEntry.timestamp
        )
    }
    
    fun createSyncEntry(
        journalEntry: JournalEntry,
        vectorClock: Int = 0,
        isTombstone: Boolean = false
    ): SyncEntry {
        return SyncEntry(
            id = journalEntry.date,
            value = journalEntry.content,
            timestamp = journalEntry.updated,
            deviceId = deviceId,
            vectorClock = vectorClock,
            isTombstone = isTombstone
        )
    }
    
    // MARK: - Sync Metadata Management
    
    fun getSyncMetadata(entryId: String): SyncMetadata? {
        return database.getSyncMetadata(entryId)
    }
    
    fun saveSyncMetadata(metadata: SyncMetadata) {
        database.saveSyncMetadata(metadata)
    }
    
    fun incrementVectorClock(entryId: String): Int {
        val metadata = getSyncMetadata(entryId)
        return if (metadata != null) {
            metadata.vectorClock++
            saveSyncMetadata(metadata)
            metadata.vectorClock
        } else {
            // Create new metadata
            val newMetadata = SyncMetadata(
                entryId = entryId,
                deviceId = deviceId,
                vectorClock = 1,
                isTombstone = false
            )
            saveSyncMetadata(newMetadata)
            1
        }
    }
    
    fun markAsTombstone(entryId: String) {
        val metadata = getSyncMetadata(entryId)
        if (metadata != null) {
            metadata.isTombstone = true
            metadata.vectorClock++
            saveSyncMetadata(metadata)
        } else {
            val newMetadata = SyncMetadata(
                entryId = entryId,
                deviceId = deviceId,
                vectorClock = 1,
                isTombstone = true
            )
            saveSyncMetadata(newMetadata)
        }
    }
    
    companion object {
        private const val DEVICE_ID_KEY = "com.digitalgram.deviceId"
        
        @Volatile
        private var INSTANCE: CRDTManager? = null
        
        fun getInstance(context: Context): CRDTManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CRDTManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
