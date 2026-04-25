package com.digitalgram.android.data

import android.content.Context
import com.digitalgram.android.BuildConfig
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.http.StandardHttpRequestor
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.WriteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages Dropbox authentication and file operations for database backup/restore
 */
class DropboxManager(private val context: Context) {

    private val settings = AppSettings.getInstance(context)

    private val requestConfig = DbxRequestConfig.newBuilder("DigitalGram").build()
    
    companion object {
        private const val BACKUP_FOLDER = "/DigitalGram/backups"
        private const val TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"
        
        @Volatile
        private var instance: DropboxManager? = null
        
        fun getInstance(context: Context): DropboxManager {
            return instance ?: synchronized(this) {
                instance ?: DropboxManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Get configured Dropbox client if authenticated
     */
    private fun getClient(): DbxClientV2? {
        val accessToken = settings.dropboxAccessToken
        return if (accessToken.isNotEmpty()) {
            DbxClientV2(requestConfig, accessToken)
        } else {
            null
        }
    }
    
    /**
     * Start OAuth authentication flow
     */
    fun startAuthentication(activityContext: android.app.Activity): Result<Unit> {
        val appKey = BuildConfig.DROPBOX_APP_KEY.trim()
        if (appKey.isEmpty()) {
            return Result.failure(IllegalStateException("Dropbox app key is missing. Add dropboxAppKey to local.properties."))
        }
        return try {
            android.util.Log.d("DropboxManager", "Starting OAuth with app key: ${appKey.take(10)}...")
            Auth.startOAuth2PKCE(
                activityContext,
                appKey,
                requestConfig
            )
            android.util.Log.d("DropboxManager", "OAuth flow initiated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("DropboxManager", "OAuth start failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Complete authentication and store access token
     * Call this in onResume() of your activity
     */
    fun completeAuthentication(): Boolean {
        android.util.Log.d("DropboxManager", "===== Auth completion attempt =====")
        
        // Method 1: Try standard PKCE credential
        val credential = try {
            Auth.getDbxCredential()
        } catch (e: Exception) {
            android.util.Log.e("DropboxManager", "Error getting credential", e)
            null
        }
        
        android.util.Log.d("DropboxManager", "  PKCE Credential: ${credential != null}")
        
        if (credential?.accessToken != null) {
            settings.dropboxAccessToken = credential.accessToken
            android.util.Log.d("DropboxManager", "✓ PKCE Token saved: ${credential.accessToken.take(20)}...")
            return true
        }
        
        // Method 2: Try legacy OAuth2 token
        val legacyToken = try {
            Auth.getOAuth2Token()
        } catch (e: Exception) {
            android.util.Log.e("DropboxManager", "Error getting legacy token", e)
            null
        }
        
        android.util.Log.d("DropboxManager", "  Legacy token: ${legacyToken != null}")
        
        if (!legacyToken.isNullOrEmpty()) {
            settings.dropboxAccessToken = legacyToken
            android.util.Log.d("DropboxManager", "✓ Legacy token saved: ${legacyToken.take(20)}...")
            return true
        }
        
        // Method 3: Try to read from Dropbox SDK's internal SharedPreferences
        try {
            val dbxPrefs = context.getSharedPreferences("com.dropbox.core.android", android.content.Context.MODE_PRIVATE)
            val savedCredential = dbxPrefs.getString("credential", null)
            android.util.Log.d("DropboxManager", "  Dropbox internal prefs credential: ${savedCredential != null}")
            if (savedCredential != null) {
                // Parse the JSON credential
                val json = org.json.JSONObject(savedCredential)
                val accessToken = json.optString("access_token", "")
                if (accessToken.isNotEmpty()) {
                    settings.dropboxAccessToken = accessToken
                    android.util.Log.d("DropboxManager", "✓ Token from prefs saved: ${accessToken.take(20)}...")
                    return true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DropboxManager", "Error reading Dropbox prefs", e)
        }
        
        android.util.Log.w("DropboxManager", "✗ No token available from any source")
        return false
    }
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return settings.dropboxAccessToken.isNotEmpty()
    }
    
    /**
     * Disconnect from Dropbox
     */
    fun disconnect() {
        settings.dropboxAccessToken = ""
    }
    
    /**
     * Upload database file to Dropbox
     * @param databaseFile The local database file to upload
     * @param databaseName Name of the database (for backup naming)
     * @return Success status
     */
    suspend fun uploadBackup(databaseFile: File, databaseName: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("DropboxManager", "===== Upload Backup Start =====")
            android.util.Log.d("DropboxManager", "Database file: ${databaseFile.absolutePath}")
            android.util.Log.d("DropboxManager", "Database exists: ${databaseFile.exists()}")
            android.util.Log.d("DropboxManager", "Database size: ${databaseFile.length()} bytes")
            
            val accessToken = settings.dropboxAccessToken
            android.util.Log.d("DropboxManager", "Access token length: ${accessToken.length}")
            android.util.Log.d("DropboxManager", "Access token prefix: ${accessToken.take(20)}...")
            
            val client = getClient() ?: return@withContext Result.failure(
                Exception("Not authenticated. Please connect to Dropbox first.")
            )
            
            android.util.Log.d("DropboxManager", "Client created successfully")
            
            if (!databaseFile.exists()) {
                return@withContext Result.failure(Exception("Database file not found"))
            }
            
            // Generate backup filename with timestamp
            val timestamp = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).format(Date())
            val backupFileName = "${databaseName}_$timestamp.sqlite"
            val remotePath = "$BACKUP_FOLDER/$backupFileName"
            
            android.util.Log.d("DropboxManager", "Uploading to: $remotePath")
            
            // Upload file
            FileInputStream(databaseFile).use { inputStream ->
                android.util.Log.d("DropboxManager", "Starting upload...")
                val metadata = client.files()
                    .uploadBuilder(remotePath)
                    .withMode(WriteMode.ADD)
                    .uploadAndFinish(inputStream)
                
                android.util.Log.d("DropboxManager", "✓ Upload successful: ${metadata.name}")
                Result.success(metadata)
            }
        } catch (e: Exception) {
            android.util.Log.e("DropboxManager", "✗ Upload failed", e)
            android.util.Log.e("DropboxManager", "Error type: ${e.javaClass.simpleName}")
            android.util.Log.e("DropboxManager", "Error message: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * List available backups from Dropbox
     * @return List of backup file information
     */
    suspend fun listBackups(): Result<List<BackupFileInfo>> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                Exception("Not authenticated. Please connect to Dropbox first.")
            )
            
            // Create backup folder if it doesn't exist
            try {
                client.files().createFolderV2(BACKUP_FOLDER)
            } catch (e: Exception) {
                // Folder may already exist, ignore
            }
            
            val result = client.files().listFolder(BACKUP_FOLDER)
            val backups = result.entries
                .filterIsInstance<FileMetadata>()
                .filter { it.name.endsWith(".sqlite") }
                .map { metadata ->
                    BackupFileInfo(
                        name = metadata.name,
                        path = metadata.pathLower ?: metadata.pathDisplay,
                        size = metadata.size,
                        modified = metadata.serverModified
                    )
                }
                .sortedByDescending { it.modified }
            
            Result.success(backups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Download backup file from Dropbox
     * @param remotePath Path to the backup file in Dropbox
     * @param destinationFile Local file to save the download
     * @return Success status
     */
    suspend fun downloadBackup(remotePath: String, destinationFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                Exception("Not authenticated. Please connect to Dropbox first.")
            )
            
            // Download file
            FileOutputStream(destinationFile).use { outputStream ->
                client.files().download(remotePath).download(outputStream)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get the most recent backup information
     */
    suspend fun getLatestBackup(): Result<BackupFileInfo?> = withContext(Dispatchers.IO) {
        val backupsResult = listBackups()
        if (backupsResult.isSuccess) {
            Result.success(backupsResult.getOrNull()?.firstOrNull())
        } else {
            Result.failure(backupsResult.exceptionOrNull() ?: Exception("Failed to get latest backup"))
        }
    }
}

/**
 * Data class representing backup file information
 */
data class BackupFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Date
) {
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
    
    fun getFormattedDate(): String {
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return format.format(modified)
    }
}
