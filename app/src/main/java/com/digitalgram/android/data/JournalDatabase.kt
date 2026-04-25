package com.digitalgram.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile private var filterYear: Int? = null
    @Volatile private var filterMonth: Int? = null
    
    init {
        openDatabase(databaseName)
    }
    
    private fun openDatabase(name: String) {
        dbHelper?.close()
        databaseName = name
        dbHelper = JournalDbHelper(context, name)
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
    fun getAvailableDatabases(): List<String> {
        val dbDir = context.getDatabasePath("dummy").parentFile ?: return listOf(databaseName)
        val databases = mutableListOf<String>()
        
        dbDir.listFiles()?.forEach { file ->
            // Include .sqlite, .db files, exclude -journal and -wal files
            if (file.isFile && 
                (file.name.endsWith(".sqlite") || file.name.endsWith(".db")) &&
                !file.name.contains("-journal") && 
                !file.name.contains("-wal") &&
                !file.name.contains("-shm")) {
                databases.add(file.name)
            }
        }
        
        // Always include current database
        if (!databases.contains(databaseName)) {
            databases.add(0, databaseName)
        }
        
        return databases.sorted()
    }
    
    /**
     * Create a new database
     */
    fun createNewDatabase(name: String): Boolean {
        val dbName = sanitizeDatabaseName(name) ?: return false
        val dbFile = context.getDatabasePath(dbName)

        if (dbFile.exists()) {
            return false // Database already exists
        }

        // Create the database by opening and closing it
        val helper = JournalDbHelper(context, dbName)
        helper.writableDatabase // This creates the tables
        helper.close()

        return true
    }

    /**
     * Rename the current database
     */
    fun renameDatabase(newName: String): Boolean {
        val newDbName = sanitizeDatabaseName(newName) ?: return false

        // Check if a database with the new name already exists
        val newDbFile = context.getDatabasePath(newDbName)
        if (newDbFile.exists()) {
            return false // Database with new name already exists
        }
        
        // Close current database connection
        dbHelper?.close()
        
        // Get file references
        val oldDbFile = context.getDatabasePath(databaseName)
        val oldWalFile = File(oldDbFile.path + "-wal")
        val oldShmFile = File(oldDbFile.path + "-shm")
        val oldJournalFile = File(oldDbFile.path + "-journal")
        
        val newWalFile = File(newDbFile.path + "-wal")
        val newShmFile = File(newDbFile.path + "-shm")
        val newJournalFile = File(newDbFile.path + "-journal")
        
        // Rename main database file
        val renamed = oldDbFile.renameTo(newDbFile)
        if (!renamed) {
            // Reopen old database
            dbHelper = JournalDbHelper(context, databaseName)
            return false
        }
        
        // Rename associated files if they exist
        if (oldWalFile.exists()) oldWalFile.renameTo(newWalFile)
        if (oldShmFile.exists()) oldShmFile.renameTo(newShmFile)
        if (oldJournalFile.exists()) oldJournalFile.renameTo(newJournalFile)
        
        // Update current database name and reopen
        databaseName = newDbName
        val settings = AppSettings.getInstance(context)
        settings.currentDatabase = newDbName
        dbHelper = JournalDbHelper(context, newDbName)
        refreshEntriesAsync()
        
        return true
    }
    
    /**
     * Delete a database (cannot delete current database)
     */
    fun deleteDatabase(name: String): Boolean {
        if (name == databaseName) {
            return false // Cannot delete current database
        }
        
        val dbFile = context.getDatabasePath(name)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        val journalFile = File(dbFile.path + "-journal")
        
        walFile.delete()
        shmFile.delete()
        journalFile.delete()
        
        return dbFile.delete()
    }
    
    /**
     * Delete current database and switch to default
     */
    fun deleteCurrentDatabase(): Boolean {
        if (databaseName == DEFAULT_DATABASE) {
            return false // Cannot delete default database
        }
        
        val oldDbName = databaseName
        val settings = AppSettings.getInstance(context)
        
        // Switch to default database first
        settings.currentDatabase = DEFAULT_DATABASE
        openDatabase(DEFAULT_DATABASE)
        
        // Now delete the old database
        val dbFile = context.getDatabasePath(oldDbName)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        val journalFile = File(dbFile.path + "-journal")
        
        walFile.delete()
        shmFile.delete()
        journalFile.delete()
        
        return dbFile.delete()
    }
    
    /**
     * Import a database file
     */
    suspend fun importDatabase(sourceFile: File, targetName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dbName = sanitizeDatabaseName(targetName) ?: return@withContext false
            val targetFile = context.getDatabasePath(dbName)

            sourceFile.copyTo(targetFile, overwrite = true)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
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
        
        val targetDb = dbHelper?.writableDatabase
        var sourceDb: SQLiteDatabase? = null
        try {
            sourceDb = SQLiteDatabase.openDatabase(
                sourceFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            targetDb?.beginTransaction()

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
            
            targetDb?.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (targetDb != null && targetDb.inTransaction()) {
                targetDb.endTransaction()
            }
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
            e.printStackTrace()
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
            e.printStackTrace()
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
        scope.launch {
            val y = filterYear
            val m = filterMonth
            val entries = if (y != null && m != null) {
                getEntriesForMonthSync(y, m)
            } else {
                getAllEntriesSync()
            }
            _entriesLiveData.postValue(entries)
        }
    }

    fun refreshEntries() {
        refreshEntriesAsync()
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
            e.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Get all entries synchronously
     */
    fun getAllEntriesSync(): List<JournalEntry> {
        try {
            val db = dbHelper?.readableDatabase ?: return emptyList()
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
            
            return entries
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
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
            e.printStackTrace()
        }
    }
    
    /**
     * Get total entry count
     */
    fun getEntryCount(): Int {
        return try {
            val db = dbHelper?.readableDatabase ?: return 0
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_DIARY", null)
            cursor.use {
                if (it.moveToFirst()) {
                    it.getInt(0)
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
    
    fun close() {
        dbHelper?.close()
        dbHelper = null
    }
    
    private class JournalDbHelper(context: Context, databaseName: String) : 
        SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {
        
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
            // WAL mode must be set in onConfigure — matches macOS DigitalGram so synced
            // SQLite files don't leave stale WAL frames invisible to the other platform.
            db.enableWriteAheadLogging()
            db.setForeignKeyConstraintsEnabled(true)
        }
    }
    
    companion object {
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
        
        fun getInstance(context: Context): JournalDatabase {
            return INSTANCE ?: synchronized(this) {
                val settings = AppSettings.getInstance(context)
                val dbName = settings.currentDatabase
                INSTANCE ?: JournalDatabase(context.applicationContext, dbName).also { INSTANCE = it }
            }
        }
        
        fun reinitialize(context: Context) {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                INSTANCE = null
                getInstance(context)
            }
        }
    }
}
