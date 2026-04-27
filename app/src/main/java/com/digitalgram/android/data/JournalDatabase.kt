package com.digitalgram.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainSQLiteDatabase
import android.util.Log
import com.digitalgram.android.BuildConfig
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File

/**
 * SQLite Database Manager matching DigitalGram macOS structure exactly
 * Supports multiple databases and switching between them
 */
class JournalDatabase private constructor(
    private val context: Context,
    private var databaseName: String
) {
    
    private var dbHelper: JournalDbHelper? = null
    private val _entriesLiveData = MutableLiveData<List<JournalEntry>>()
    val entries: LiveData<List<JournalEntry>> = _entriesLiveData
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Volatile private var filterYear: Int? = null
    @Volatile private var filterMonth: Int? = null
    
    init {
        scope.launch {
            refreshTrigger.conflate().collect {
                val y = filterYear
                val m = filterMonth
                val fetched = if (y != null && m != null) {
                    getEntriesForMonthSync(y, m)
                } else {
                    getAllEntries()
                }
                _entriesLiveData.postValue(fetched)
            }
        }
        openDatabase(databaseName)
    }
    
    private fun openDatabase(name: String) {
        dbHelper?.close()
        databaseName = name
        dbHelper = JournalDbHelper(context, name, AppSettings.getInstance(context).getDbPassphrase())
        refreshEntriesAsync()
    }
    
    fun switchDatabase(name: String) {
        openDatabase(name)
        // Persist immediately so a crash between switch and a later AppSettings.save()
        // doesn't leave the next launch pointed at the wrong DB file.
        AppSettings.getInstance(context).currentDatabase = name
    }

    /**
     * Validate and normalize a user-entered database name. Rejects components that
     * would let appendingPathComponent / getDatabasePath escape the app's database
     * directory or shadow internal SQLite housekeeping files.
     */
    private fun sanitizeDatabaseName(rawName: String): String? {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return null

        val base = when {
            trimmed.endsWith(".sqlite", ignoreCase = true) -> trimmed.dropLast(7)
            trimmed.endsWith(".db", ignoreCase = true) -> trimmed.dropLast(3)
            else -> trimmed
        }

        if (base.isEmpty() ||
            base.contains('/') ||
            base.contains('\\') ||
            base.contains("..") ||
            base.startsWith('.')) {
            return null
        }

        return if (trimmed.endsWith(".db", ignoreCase = true)) "$base.db" else "$base.sqlite"
    }
    
    fun getCurrentDatabaseName(): String = databaseName
    
    /**
     * Get list of all database files in the app's database directory
     */
    suspend fun getAvailableDatabases(): List<String> = withContext(Dispatchers.IO) {
        val dbDir = context.getDatabasePath("dummy").parentFile ?: return@withContext listOf(databaseName)
        val databases = mutableListOf<String>()

        dbDir.listFiles()?.forEach { file ->
            if (file.isFile &&
                (file.name.endsWith(".sqlite") || file.name.endsWith(".db")) &&
                !file.name.contains("-journal") &&
                !file.name.contains("-wal") &&
                !file.name.contains("-shm")) {
                databases.add(file.name)
            }
        }

        if (!databases.contains(databaseName)) {
            databases.add(0, databaseName)
        }

        databases.sorted()
    }
    
    /**
     * Create a new database
     */
    suspend fun createNewDatabase(name: String): Boolean = withContext(Dispatchers.IO) {
        val dbName = sanitizeDatabaseName(name) ?: return@withContext false
        val dbFile = context.getDatabasePath(dbName)

        if (dbFile.exists()) return@withContext false

        val helper = JournalDbHelper(context, dbName, AppSettings.getInstance(context).getDbPassphrase())
        helper.writableDatabase
        helper.close()

        true
    }

    /**
     * Rename the current database
     */
    suspend fun renameDatabase(newName: String): Boolean = withContext(Dispatchers.IO) {
        val newDbName = sanitizeDatabaseName(newName) ?: return@withContext false

        val newDbFile = context.getDatabasePath(newDbName)
        if (newDbFile.exists()) return@withContext false

        dbHelper?.close()

        val oldDbFile = context.getDatabasePath(databaseName)
        val oldWalFile = File(oldDbFile.path + "-wal")
        val oldShmFile = File(oldDbFile.path + "-shm")
        val oldJournalFile = File(oldDbFile.path + "-journal")

        val newWalFile = File(newDbFile.path + "-wal")
        val newShmFile = File(newDbFile.path + "-shm")
        val newJournalFile = File(newDbFile.path + "-journal")

        val renamed = oldDbFile.renameTo(newDbFile)
        val passphrase = AppSettings.getInstance(context).getDbPassphrase()
        if (!renamed) {
            dbHelper = JournalDbHelper(context, databaseName, passphrase)
            return@withContext false
        }

        if (oldWalFile.exists()) oldWalFile.renameTo(newWalFile)
        if (oldShmFile.exists()) oldShmFile.renameTo(newShmFile)
        if (oldJournalFile.exists()) oldJournalFile.renameTo(newJournalFile)

        databaseName = newDbName
        val settings = AppSettings.getInstance(context)
        settings.currentDatabase = newDbName
        dbHelper = JournalDbHelper(context, newDbName, passphrase)
        refreshEntriesAsync()

        true
    }
    
    /**
     * Delete a database (cannot delete current database)
     */
    suspend fun deleteDatabase(name: String): Boolean = withContext(Dispatchers.IO) {
        if (name == databaseName) return@withContext false

        val dbFile = context.getDatabasePath(name)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        val journalFile = File(dbFile.path + "-journal")

        walFile.delete()
        shmFile.delete()
        journalFile.delete()

        dbFile.delete()
    }
    
    /**
     * Delete current database and switch to default
     */
    suspend fun deleteCurrentDatabase(): Boolean = withContext(Dispatchers.IO) {
        if (databaseName == DEFAULT_DATABASE) return@withContext false

        val oldDbName = databaseName
        val settings = AppSettings.getInstance(context)

        settings.currentDatabase = DEFAULT_DATABASE
        openDatabase(DEFAULT_DATABASE)

        val dbFile = context.getDatabasePath(oldDbName)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        val journalFile = File(dbFile.path + "-journal")

        walFile.delete()
        shmFile.delete()
        journalFile.delete()

        dbFile.delete()
    }
    
    private fun validateSourceFile(file: File): Boolean {
        if (!file.exists() || !file.canRead()) return false
        if (file.length() > 50L * 1024 * 1024) return false
        return true
    }

    private fun validateImportSchema(sourceDb: PlainSQLiteDatabase): Boolean {
        sourceDb.rawQuery("PRAGMA quick_check", null).use { c ->
            if (!c.moveToFirst() || c.getString(0) != "ok") return false
        }
        sourceDb.rawQuery("SELECT type, name, sql FROM sqlite_master", null).use { c ->
            while (c.moveToNext()) {
                val type = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val sql = if (!c.isNull(2)) c.getString(2) ?: "" else ""
                when (type) {
                    "trigger", "view" -> return false
                    "table" -> {
                        if (sql.contains("CREATE VIRTUAL TABLE", ignoreCase = true)) return false
                        if (name !in setOf("diary", "android_metadata", "sqlite_sequence")) return false
                    }
                    "index" -> {
                        if (!name.startsWith("diary_idx_") && !name.startsWith("sqlite_autoindex_")) return false
                    }
                }
            }
        }
        return true
    }

    /**
     * Import a database file
     */
    suspend fun importDatabase(sourceFile: File, targetName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!validateSourceFile(sourceFile)) return@withContext false
            val dbName = sanitizeDatabaseName(targetName) ?: return@withContext false
            val targetFile = context.getDatabasePath(dbName)

            val sourceDb = PlainSQLiteDatabase.openDatabase(
                sourceFile.path, null,
                PlainSQLiteDatabase.OPEN_READONLY or PlainSQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            try {
                if (!validateImportSchema(sourceDb)) return@withContext false
            } finally {
                sourceDb.close()
            }

            // Re-encrypt the imported plaintext source into a SQLCipher database at the target path.
            if (targetFile.exists()) targetFile.delete()
            File(targetFile.path + "-wal").delete()
            File(targetFile.path + "-shm").delete()
            File(targetFile.path + "-journal").delete()

            val passphrase = AppSettings.getInstance(context).getDbPassphrase()
            val helper = JournalDbHelper(context, dbName, passphrase)
            val destDb = helper.writableDatabase

            val src = PlainSQLiteDatabase.openDatabase(
                sourceFile.path, null,
                PlainSQLiteDatabase.OPEN_READONLY or PlainSQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            try {
                copyDiaryRows(src, destDb)
            } finally {
                src.close()
                helper.close()
            }
            return@withContext true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
            return@withContext false
        }
    }
    
    /**
     * Import and merge a database file with current database
     * - If entry dates match but content differs, keep the newer version
     * - Skip identical entries
     * - Add new entries from imported database
     * Returns: Triple(imported, updated, skipped)
     */
    suspend fun importAndMergeDatabase(sourceFile: File): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        var imported = 0
        var updated = 0
        var skipped = 0

        if (!validateSourceFile(sourceFile)) return@withContext Triple(0, 0, 0)

        var sourceDb: PlainSQLiteDatabase? = null
        try {
            sourceDb = PlainSQLiteDatabase.openDatabase(
                sourceFile.path, null,
                PlainSQLiteDatabase.OPEN_READONLY or PlainSQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            if (!validateImportSchema(sourceDb)) return@withContext Triple(0, 0, 0)

            val destDb = dbHelper?.writableDatabase ?: return@withContext Triple(0, 0, 0)
            destDb.beginTransaction()
            try {

            val sourceCursor = sourceDb.query(
                TABLE_DIARY,
                null,
                null, null, null, null,
                "$COLUMN_DATE DESC"
            )

            sourceCursor.use { cursor ->
                val dateIdx = cursor.getColumnIndex(COLUMN_DATE)
                val yearIdx = cursor.getColumnIndex(COLUMN_YEAR)
                val monthIdx = cursor.getColumnIndex(COLUMN_MONTH)
                val dayIdx = cursor.getColumnIndex(COLUMN_DAY)
                val contentIdx = cursor.getColumnIndex(COLUMN_CONTENT)
                val createdIdx = cursor.getColumnIndex(COLUMN_CREATED)
                val updatedIdx = cursor.getColumnIndex(COLUMN_UPDATED)

                while (cursor.moveToNext()) {
                    val date = if (dateIdx >= 0) cursor.getString(dateIdx) ?: "" else ""
                    val year = if (yearIdx >= 0) cursor.getInt(yearIdx) else 0
                    val month = if (monthIdx >= 0) cursor.getInt(monthIdx) else 0
                    val day = if (dayIdx >= 0) cursor.getInt(dayIdx) else 0
                    val content = if (contentIdx >= 0) cursor.getString(contentIdx) ?: "" else ""
                    val created = if (createdIdx >= 0) cursor.getString(createdIdx) ?: "" else ""
                    val sourceUpdated = if (updatedIdx >= 0 && !cursor.isNull(updatedIdx)) {
                        cursor.getString(updatedIdx) ?: created
                    } else {
                        created
                    }
                    
                    // Check if entry exists in current database
                    val existingEntry = getEntryByDateSync(date)
                    
                    if (existingEntry == null) {
                        // New entry - import it
                        val newEntry = JournalEntry(
                            date = date,
                            year = year,
                            month = month,
                            day = day,
                            content = content,
                            created = created,
                            updated = sourceUpdated
                        )
                        saveEntrySync(newEntry)
                        imported++
                    } else if (existingEntry.content.trim() == content.trim()) {
                        // Identical content - skip
                        skipped++
                    } else {
                        // Content differs - keep the newer version
                        val existingUpdatedTime = parseTimestamp(existingEntry.updated)
                        val sourceUpdatedTime = parseTimestamp(sourceUpdated)
                        
                        if (sourceUpdatedTime > existingUpdatedTime) {
                            // Source is newer - update
                            val updatedEntry = JournalEntry(
                                date = date,
                                year = year,
                                month = month,
                                day = day,
                                content = content,
                                created = existingEntry.created,
                                updated = sourceUpdated
                            )
                            saveEntrySync(updatedEntry)
                            updated++
                        } else {
                            // Existing is newer or same - skip
                            skipped++
                        }
                    }
                }
            }

            destDb.setTransactionSuccessful()
            } finally {
                destDb.endTransaction()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
        } finally {
            sourceDb?.close()
            refreshEntries()
        }

        return@withContext Triple(imported, updated, skipped)
    }
    
    /**
     * Get entry by date synchronously (for merge operations)
     */
    private fun getEntryByDateSync(dateKey: String): JournalEntry? {
        try {
            val db = dbHelper?.readableDatabase ?: return null
            
            val cursor = db.query(
                TABLE_DIARY,
                null,
                "$COLUMN_DATE = ?",
                arrayOf(dateKey),
                null, null, null
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    val createdIdx = it.getColumnIndexOrThrow(COLUMN_CREATED)
                    val updatedIdx = it.getColumnIndex(COLUMN_UPDATED)
                    val created = it.getString(createdIdx) ?: ""
                    val updated = if (updatedIdx >= 0 && !it.isNull(updatedIdx)) {
                        it.getString(updatedIdx)
                    } else {
                        created
                    }
                    
                    return JournalEntry(
                        date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE)),
                        year = it.getInt(it.getColumnIndexOrThrow(COLUMN_YEAR)),
                        month = it.getInt(it.getColumnIndexOrThrow(COLUMN_MONTH)),
                        day = it.getInt(it.getColumnIndexOrThrow(COLUMN_DAY)),
                        content = it.getString(it.getColumnIndexOrThrow(COLUMN_CONTENT)) ?: "",
                        created = created,
                        updated = updated
                    )
                }
            }
            return null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
            return null
        }
    }
    
    /**
     * Save entry synchronously (for merge operations)
     */
    private fun saveEntrySync(entry: JournalEntry) {
        try {
            val db = dbHelper?.writableDatabase ?: return
            
            val values = ContentValues().apply {
                put(COLUMN_DATE, entry.date)
                put(COLUMN_YEAR, entry.year)
                put(COLUMN_MONTH, entry.month)
                put(COLUMN_DAY, entry.day)
                put(COLUMN_CONTENT, entry.content)
                put(COLUMN_CREATED, entry.created)
                put(COLUMN_UPDATED, entry.updated)
            }
            
            db.insertWithOnConflict(TABLE_DIARY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
        }
    }
    
    /**
     * Parse timestamp string to comparable Long value
     */
    private fun parseTimestamp(timestamp: String): Long {
        return try {
            // Try ISO 8601 format
            java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            try {
                // Try common date format
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                    .parse(timestamp)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }
    
    private fun refreshEntriesAsync() {
        scope.launch { refreshTrigger.tryEmit(Unit) }
    }

    fun refreshEntries() {
        refreshTrigger.tryEmit(Unit)
    }

    /**
     * Set the month filter used by refreshEntries(). Calendar months are 0-based,
     * but the schema stores month 1-12.
     */
    fun setMonthFilter(year: Int, monthOneBased: Int) {
        filterYear = year
        filterMonth = monthOneBased
        refreshEntriesAsync()
    }

    /**
     * Save or update an entry
     */
    suspend fun saveEntry(entry: JournalEntry) = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper?.writableDatabase ?: return@withContext
            
            val values = ContentValues().apply {
                put(COLUMN_DATE, entry.date)
                put(COLUMN_YEAR, entry.year)
                put(COLUMN_MONTH, entry.month)
                put(COLUMN_DAY, entry.day)
                put(COLUMN_CONTENT, entry.content)
                put(COLUMN_CREATED, entry.created)
                put(COLUMN_UPDATED, entry.updated)
            }
            
            db.insertWithOnConflict(TABLE_DIARY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            refreshEntries()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
        }
    }
    
    /**
     * Get entry by date key
     */
    suspend fun getEntryByDate(dateKey: String): JournalEntry? = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper?.readableDatabase ?: return@withContext null
            
            val cursor = db.query(
                TABLE_DIARY,
                null,
                "$COLUMN_DATE = ?",
                arrayOf(dateKey),
                null, null, null
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    val createdIdx = it.getColumnIndexOrThrow(COLUMN_CREATED)
                    val updatedIdx = it.getColumnIndex(COLUMN_UPDATED)
                    val created = it.getString(createdIdx) ?: ""
                    val updated = if (updatedIdx >= 0 && !it.isNull(updatedIdx)) {
                        it.getString(updatedIdx)
                    } else {
                        created
                    }
                    
                    return@withContext JournalEntry(
                        date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE)),
                        year = it.getInt(it.getColumnIndexOrThrow(COLUMN_YEAR)),
                        month = it.getInt(it.getColumnIndexOrThrow(COLUMN_MONTH)),
                        day = it.getInt(it.getColumnIndexOrThrow(COLUMN_DAY)),
                        content = it.getString(it.getColumnIndexOrThrow(COLUMN_CONTENT)) ?: "",
                        created = created,
                        updated = updated
                    )
                }
            }
            return@withContext null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
            return@withContext null
        }
    }
    
    /**
     * Get entries for a single (year, month) using the existing
     * `diary_idx_0` index on (year, month). Month is 1-based per schema.
     */
    fun getEntriesForMonthSync(year: Int, monthOneBased: Int): List<JournalEntry> {
        try {
            val db = dbHelper?.readableDatabase ?: return emptyList()
            val entries = mutableListOf<JournalEntry>()

            val cursor = db.query(
                TABLE_DIARY,
                null,
                "$COLUMN_YEAR = ? AND $COLUMN_MONTH = ?",
                arrayOf(year.toString(), monthOneBased.toString()),
                null, null,
                "$COLUMN_DATE DESC"
            )

            cursor.use {
                val dateIdx = it.getColumnIndex(COLUMN_DATE)
                val yearIdx = it.getColumnIndex(COLUMN_YEAR)
                val monthIdx = it.getColumnIndex(COLUMN_MONTH)
                val dayIdx = it.getColumnIndex(COLUMN_DAY)
                val contentIdx = it.getColumnIndex(COLUMN_CONTENT)
                val createdIdx = it.getColumnIndex(COLUMN_CREATED)
                val updatedIdx = it.getColumnIndex(COLUMN_UPDATED)

                while (it.moveToNext()) {
                    val created = if (createdIdx >= 0) it.getString(createdIdx) ?: "" else ""
                    val updated = if (updatedIdx >= 0 && !it.isNull(updatedIdx)) {
                        it.getString(updatedIdx)
                    } else {
                        created
                    }

                    entries.add(
                        JournalEntry(
                            date = if (dateIdx >= 0) it.getString(dateIdx) ?: "" else "",
                            year = if (yearIdx >= 0) it.getInt(yearIdx) else 0,
                            month = if (monthIdx >= 0) it.getInt(monthIdx) else 0,
                            day = if (dayIdx >= 0) it.getInt(dayIdx) else 0,
                            content = if (contentIdx >= 0) it.getString(contentIdx) ?: "" else "",
                            created = created,
                            updated = updated
                        )
                    )
                }
            }

            return entries
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
            return emptyList()
        }
    }

    /**
     * Get all entries
     */
    suspend fun getAllEntries(): List<JournalEntry> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper?.readableDatabase ?: return@withContext emptyList()
            val entries = mutableListOf<JournalEntry>()

            val cursor = db.query(
                TABLE_DIARY,
                null,
                null, null, null, null,
                "$COLUMN_DATE DESC"
            )
            
            cursor.use {
                val dateIdx = it.getColumnIndex(COLUMN_DATE)
                val yearIdx = it.getColumnIndex(COLUMN_YEAR)
                val monthIdx = it.getColumnIndex(COLUMN_MONTH)
                val dayIdx = it.getColumnIndex(COLUMN_DAY)
                val contentIdx = it.getColumnIndex(COLUMN_CONTENT)
                val createdIdx = it.getColumnIndex(COLUMN_CREATED)
                val updatedIdx = it.getColumnIndex(COLUMN_UPDATED)
                
                while (it.moveToNext()) {
                    val created = if (createdIdx >= 0) it.getString(createdIdx) ?: "" else ""
                    val updated = if (updatedIdx >= 0 && !it.isNull(updatedIdx)) {
                        it.getString(updatedIdx)
                    } else {
                        created
                    }
                    
                    val entry = JournalEntry(
                        date = if (dateIdx >= 0) it.getString(dateIdx) ?: "" else "",
                        year = if (yearIdx >= 0) it.getInt(yearIdx) else 0,
                        month = if (monthIdx >= 0) it.getInt(monthIdx) else 0,
                        day = if (dayIdx >= 0) it.getInt(dayIdx) else 0,
                        content = if (contentIdx >= 0) it.getString(contentIdx) ?: "" else "",
                        created = created,
                        updated = updated
                    )
                    entries.add(entry)
                }
            }
            
            entries
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
            emptyList()
        }
    }

    /**
     * Delete entry by date key
     */
    suspend fun deleteEntry(dateKey: String) = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper?.writableDatabase ?: return@withContext
            db.delete(TABLE_DIARY, "$COLUMN_DATE = ?", arrayOf(dateKey))
            refreshEntries()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
        }
    }
    
    /**
     * Get total entry count
     */
    suspend fun getEntryCount(): Int = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper?.readableDatabase ?: return@withContext 0
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_DIARY", null)
            cursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
            0
        }
    }

    fun cancelScope() { scopeJob.cancel() }

    fun close() {
        dbHelper?.close()
        dbHelper = null
    }
    
    private class JournalDbHelper(context: Context, databaseName: String, passphrase: ByteArray) :
        SQLiteOpenHelper(
            context,
            databaseName,
            passphrase,
            null,
            DATABASE_VERSION,
            0,
            null,
            null,
            true
        ) {

        override fun onCreate(db: SQLiteDatabase) {
            // Create android_metadata table (for compatibility)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)
            """)
            
            // Insert default locale if table is empty
            val cursor = db.rawQuery("SELECT COUNT(*) FROM android_metadata", null)
            cursor.use {
                if (it.moveToFirst() && it.getInt(0) == 0) {
                    db.execSQL("INSERT INTO android_metadata VALUES ('en_US')")
                }
            }
            
            // Create diary table matching DigitalGram macOS structure exactly
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_DIARY (
                    $COLUMN_DATE TEXT NOT NULL PRIMARY KEY,
                    $COLUMN_YEAR INTEGER NOT NULL,
                    $COLUMN_MONTH INTEGER NOT NULL,
                    $COLUMN_DAY INTEGER NOT NULL,
                    $COLUMN_CONTENT TEXT NOT NULL,
                    $COLUMN_CREATED TEXT NOT NULL,
                    $COLUMN_UPDATED TEXT
                )
            """)
            
            // Create index for faster month-based queries
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS diary_idx_0 ON $TABLE_DIARY ($COLUMN_YEAR, $COLUMN_MONTH)
            """)
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Handle migrations if needed
        }
        
        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            // Enable optimizations only if database is writable
            if (!db.isReadOnly) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
        
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            // WAL is enabled via the SQLiteOpenHelper constructor (enableWriteAheadLogging=true);
            // calling enableWriteAheadLogging() here would conflict with that.
            db.setForeignKeyConstraintsEnabled(true)
        }
    }
    
    companion object {
        private const val TAG = "JournalDB"
        private const val DATABASE_VERSION = 1
        private const val DEFAULT_DATABASE = "daygram.sqlite"
        
        // Table and columns matching DigitalGram macOS exactly
        private const val TABLE_DIARY = "diary"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_YEAR = "year"
        private const val COLUMN_MONTH = "month"
        private const val COLUMN_DAY = "day"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_CREATED = "created"
        private const val COLUMN_UPDATED = "updated"
        
        @Volatile
        private var INSTANCE: JournalDatabase? = null
        @Volatile
        private var librariesLoaded: Boolean = false

        private fun ensureLibrariesLoaded(context: Context) {
            if (librariesLoaded) return
            synchronized(this) {
                if (!librariesLoaded) {
                    // Modern net.zetetic:sqlcipher-android no longer exposes loadLibs(context);
                    // it ships the JNI library which we load directly. Per the upstream README
                    // this must happen before any other database operation.
                    System.loadLibrary("sqlcipher")
                    librariesLoaded = true
                }
            }
        }

        fun getInstance(context: Context): JournalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val app = context.applicationContext
                    ensureLibrariesLoaded(app)
                    val settings = AppSettings.getInstance(app)
                    if (!settings.isDbMigratedToSqlcipher()) {
                        if (migrateAllToSqlcipher(app)) {
                            settings.markDbMigratedToSqlcipher()
                        }
                    }
                    val dbName = settings.currentDatabase
                    JournalDatabase(app, dbName).also { INSTANCE = it }
                }
            }
        }

        fun reinitialize(context: Context) {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
                }
                INSTANCE = null
                getInstance(context)
            }
        }

        /**
         * One-time migration of all plaintext .sqlite/.db files in the app's databases
         * directory to SQLCipher-encrypted equivalents. For each file we keep a
         * `.bak_premigrate` defensive copy until ALL files succeed; on success the
         * backups are deleted, on any failure they are retained for manual recovery.
         *
         * Returns true if every file migrated cleanly. False means at least one file
         * failed; the caller will not flip the migrated flag, so we'll retry next launch.
         */
        private fun migrateAllToSqlcipher(context: Context): Boolean {
            val dbDir = context.getDatabasePath("dummy").parentFile ?: return true
            val targets = dbDir.listFiles()?.filter { f ->
                f.isFile &&
                    (f.name.endsWith(".sqlite") || f.name.endsWith(".db")) &&
                    !f.name.contains("-journal") &&
                    !f.name.contains("-wal") &&
                    !f.name.contains("-shm") &&
                    !f.name.endsWith(".bak_premigrate") &&
                    !f.name.endsWith(".tmp_encrypted")
            }.orEmpty()

            if (targets.isEmpty()) return true

            val passphrase = AppSettings.getInstance(context).getDbPassphrase()
            val backups = mutableListOf<File>()
            var allOk = true

            for (original in targets) {
                if (isAlreadyEncrypted(original, passphrase)) {
                    continue
                }

                val backup = File(original.path + ".bak_premigrate")
                val tmpEncrypted = File(original.path + ".tmp_encrypted")
                try {
                    if (tmpEncrypted.exists()) tmpEncrypted.delete()
                    File(tmpEncrypted.path + "-wal").delete()
                    File(tmpEncrypted.path + "-shm").delete()
                    File(tmpEncrypted.path + "-journal").delete()

                    original.copyTo(backup, overwrite = true)
                    backups.add(backup)

                    val src = PlainSQLiteDatabase.openDatabase(
                        original.path, null,
                        PlainSQLiteDatabase.OPEN_READONLY or PlainSQLiteDatabase.NO_LOCALIZED_COLLATORS
                    )
                    val dst = SQLiteDatabase.openOrCreateDatabase(tmpEncrypted, passphrase, null, null)
                    try {
                        createSchema(dst)
                        copyDiaryRows(src, dst)
                    } finally {
                        try { dst.close() } catch (_: Exception) {}
                        try { src.close() } catch (_: Exception) {}
                    }

                    // Atomic-ish replace: remove old WAL/SHM/journal, delete original, rename tmp.
                    File(original.path + "-wal").delete()
                    File(original.path + "-shm").delete()
                    File(original.path + "-journal").delete()
                    if (!original.delete()) {
                        throw IllegalStateException("Failed to delete plaintext original ${original.name}")
                    }
                    if (!tmpEncrypted.renameTo(original)) {
                        // Best-effort restore from backup so we don't lose data.
                        backup.copyTo(original, overwrite = true)
                        throw IllegalStateException("Failed to rename encrypted DB into place for ${original.name}")
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "operation failed", e)
                    allOk = false
                    if (tmpEncrypted.exists()) tmpEncrypted.delete()
                }
            }

            if (allOk) {
                backups.forEach { it.delete() }
            }
            return allOk
        }

        /**
         * Heuristic: open as SQLCipher with the provided passphrase. If the first
         * read succeeds, the file is already encrypted. Used so migration is idempotent
         * if interrupted between rename and flag-flip.
         */
        private fun isAlreadyEncrypted(file: File, passphrase: ByteArray): Boolean {
            return try {
                val db = SQLiteDatabase.openDatabase(
                    file.path, passphrase, null,
                    SQLiteDatabase.OPEN_READONLY, null
                )
                try {
                    db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { c -> c.moveToFirst() }
                    true
                } finally {
                    try { db.close() } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)")
            val cur = db.rawQuery("SELECT COUNT(*) FROM android_metadata", null)
            cur.use {
                if (it.moveToFirst() && it.getInt(0) == 0) {
                    db.execSQL("INSERT INTO android_metadata VALUES ('en_US')")
                }
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_DIARY (" +
                    "$COLUMN_DATE TEXT NOT NULL PRIMARY KEY, " +
                    "$COLUMN_YEAR INTEGER NOT NULL, " +
                    "$COLUMN_MONTH INTEGER NOT NULL, " +
                    "$COLUMN_DAY INTEGER NOT NULL, " +
                    "$COLUMN_CONTENT TEXT NOT NULL, " +
                    "$COLUMN_CREATED TEXT NOT NULL, " +
                    "$COLUMN_UPDATED TEXT" +
                    ")"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS diary_idx_0 ON $TABLE_DIARY ($COLUMN_YEAR, $COLUMN_MONTH)"
            )
        }

        private fun copyDiaryRows(src: PlainSQLiteDatabase, dst: SQLiteDatabase) {
            val cursor = src.query(
                TABLE_DIARY, null, null, null, null, null, null
            )
            dst.beginTransaction()
            try {
                cursor.use { c ->
                    val dateIdx = c.getColumnIndex(COLUMN_DATE)
                    val yearIdx = c.getColumnIndex(COLUMN_YEAR)
                    val monthIdx = c.getColumnIndex(COLUMN_MONTH)
                    val dayIdx = c.getColumnIndex(COLUMN_DAY)
                    val contentIdx = c.getColumnIndex(COLUMN_CONTENT)
                    val createdIdx = c.getColumnIndex(COLUMN_CREATED)
                    val updatedIdx = c.getColumnIndex(COLUMN_UPDATED)
                    if (dateIdx < 0) return@use
                    while (c.moveToNext()) {
                        val v = ContentValues().apply {
                            put(COLUMN_DATE, c.getString(dateIdx))
                            put(COLUMN_YEAR, if (yearIdx >= 0) c.getInt(yearIdx) else 0)
                            put(COLUMN_MONTH, if (monthIdx >= 0) c.getInt(monthIdx) else 0)
                            put(COLUMN_DAY, if (dayIdx >= 0) c.getInt(dayIdx) else 0)
                            put(COLUMN_CONTENT, if (contentIdx >= 0) c.getString(contentIdx) ?: "" else "")
                            put(COLUMN_CREATED, if (createdIdx >= 0) c.getString(createdIdx) ?: "" else "")
                            if (updatedIdx >= 0 && !c.isNull(updatedIdx)) {
                                put(COLUMN_UPDATED, c.getString(updatedIdx))
                            }
                        }
                        dst.insertWithOnConflict(TABLE_DIARY, null, v, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }
                dst.setTransactionSuccessful()
            } finally {
                dst.endTransaction()
            }
        }
    }
}
