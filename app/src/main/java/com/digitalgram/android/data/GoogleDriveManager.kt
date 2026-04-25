package com.digitalgram.android.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages Google Drive authentication and file operations for database backup/restore
 */
class GoogleDriveManager(private val context: Context) {
    
    private val settings = AppSettings.getInstance(context)
    private var driveService: Drive? = null
    
    companion object {
        const val REQUEST_CODE_SIGN_IN = 9003
        private const val BACKUP_FOLDER_NAME = "DigitalGram Backups"
        private const val TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"
        
        @Volatile
        private var instance: GoogleDriveManager? = null
        
        fun getInstance(context: Context): GoogleDriveManager {
            return instance ?: synchronized(this) {
                instance ?: GoogleDriveManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Get Google Sign-In client
     */
    fun getSignInClient(activity: Activity): GoogleSignInClient {
        android.util.Log.d("GoogleDriveManager", "--- Configuring Google Sign-In ---")
        android.util.Log.d("GoogleDriveManager", "Package Name: ${activity.packageName}")
        android.util.Log.d("GoogleDriveManager", "Scope: ${DriveScopes.DRIVE_FILE}")
        
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        
        return GoogleSignIn.getClient(activity, signInOptions)
    }
    
    /**
     * Get sign-in intent
     */
    fun getSignInIntent(activity: Activity): Intent {
        return getSignInClient(activity).signInIntent
    }
    
    /**
     * Handle sign-in result
     */
    fun handleSignInResult(account: GoogleSignInAccount?): Boolean {
        return if (account != null) {
            android.util.Log.d("GoogleDriveManager", "✓ Sign-in successful")
            android.util.Log.d("GoogleDriveManager", "Account: ${account.email}")
            android.util.Log.d("GoogleDriveManager", "Account ID: ${account.id}")
            android.util.Log.d("GoogleDriveManager", "Display Name: ${account.displayName}")
            initializeDriveService(account)
            settings.googleDriveAccountEmail = account.email ?: ""
            true
        } else {
            android.util.Log.e("GoogleDriveManager", "❌ Sign-in failed - account is null")
            android.util.Log.e("GoogleDriveManager", "This usually means:")
            android.util.Log.e("GoogleDriveManager", "1. OAuth Client ID not added to Google Cloud Console for Play Store SHA-1")
            android.util.Log.e("GoogleDriveManager", "2. Package name mismatch (got: com.digitalgram.android)")
            android.util.Log.e("GoogleDriveManager", "3. OAuth consent screen not published (still in Testing mode)")
            android.util.Log.e("GoogleDriveManager", "4. App not on allow list or test users not added")
            false
        }
    }
    
    /**
     * Initialize Drive service with signed-in account
     */
    private fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("DigitalGram")
            .build()
        
        android.util.Log.d("GoogleDriveManager", "Drive service initialized")
    }
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastSignedInAccount != null && driveService == null) {
            initializeDriveService(lastSignedInAccount)
        }
        return lastSignedInAccount != null && driveService != null
    }
    
    /**
     * Refresh/reinitialize Drive service to fix any serialization issues
     */
    fun refreshDriveService() {
        try {
            val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (lastSignedInAccount != null) {
                driveService = null
                android.util.Log.d("GoogleDriveManager", "Refreshing Drive service...")
                initializeDriveService(lastSignedInAccount)
                android.util.Log.d("GoogleDriveManager", "✓ Drive service refreshed")
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "Failed to refresh service", e)
        }
    }
    
    /**
     * Get current account email
     */
    fun getAccountEmail(): String {
        return GoogleSignIn.getLastSignedInAccount(context)?.email 
            ?: settings.googleDriveAccountEmail
    }
    
    /**
     * Disconnect from Google Drive (synchronous version for UI)
     */
    fun disconnect(activity: Activity? = null) {
        android.util.Log.d("GoogleDriveManager", "Disconnecting from Google Drive")
        driveService = null
        settings.googleDriveAccountEmail = ""
        
        // Sign out from Google Sign-In if activity is provided
        if (activity != null) {
            getSignInClient(activity).signOut().addOnCompleteListener {
                android.util.Log.d("GoogleDriveManager", "✓ Signed out from Google Sign-In")
            }
        }
        
        android.util.Log.d("GoogleDriveManager", "✓ Disconnected successfully")
    }
    
    /**
     * Sign out from Google Drive
     */
    suspend fun signOut(activity: Activity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getSignInClient(activity).signOut().addOnCompleteListener {
                driveService = null
                settings.googleDriveAccountEmail = ""
                android.util.Log.d("GoogleDriveManager", "Signed out successfully")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "Sign-out failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get or create backup folder in user's visible Drive
     * Files in this folder are visible to the user and persist after app uninstall
     */
    private suspend fun getOrCreateBackupFolder(): String? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("GoogleDriveManager", "--- Getting/creating backup folder ---")
            
            val service = driveService ?: run {
                android.util.Log.e("GoogleDriveManager", "❌ Drive service is null - user not authenticated")
                return@withContext null
            }
            
            android.util.Log.d("GoogleDriveManager", "✓ Drive service exists")
            
            // Search for existing folder
            android.util.Log.d("GoogleDriveManager", "Searching for existing folder: $BACKUP_FOLDER_NAME")
            
            try {
                val result: FileList = service.files().list()
                    .setQ("name='$BACKUP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
                    .setSpaces("drive")
                    .setPageSize(1)
                    .setFields("files(id, name)")
                    .execute()
                
                if (result.files != null && result.files.isNotEmpty()) {
                    android.util.Log.d("GoogleDriveManager", "✓ Found existing backup folder: ${result.files[0].id}")
                    return@withContext result.files[0].id
                }
            } catch (e: Exception) {
                android.util.Log.w("GoogleDriveManager", "Error searching for folder: ${e.message}")
                // Continue to create folder
            }
            
            android.util.Log.d("GoogleDriveManager", "Backup folder not found, creating new one...")
            
            // Create new folder in user's Drive root
            val folderMetadata = File().apply {
                name = BACKUP_FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }
            
            val folder = service.files().create(folderMetadata)
                .setFields("id, name, webViewLink")
                .execute()
            
            if (folder != null && !folder.id.isNullOrEmpty()) {
                android.util.Log.d("GoogleDriveManager", "✓ Created backup folder: ${folder.id}")
                android.util.Log.d("GoogleDriveManager", "Folder link: ${folder.webViewLink}")
                return@withContext folder.id
            }
            
            android.util.Log.e("GoogleDriveManager", "❌ Failed to create folder")
            null
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            android.util.Log.e("GoogleDriveManager", "❌ Google API Error (${e.statusCode}): ${e.details?.toPrettyString()}")
            null
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "❌ Error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Upload database backup to Google Drive
     */
    suspend fun uploadBackup(databaseFile: java.io.File, databaseName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("GoogleDriveManager", "===== Upload Backup Start =====")
            android.util.Log.d("GoogleDriveManager", "Database file: ${databaseFile.absolutePath}")
            android.util.Log.d("GoogleDriveManager", "Database exists: ${databaseFile.exists()}")
            android.util.Log.d("GoogleDriveManager", "Database size: ${databaseFile.length()} bytes")
            
            val service = driveService ?: return@withContext Result.failure(
                Exception("Not authenticated. Please sign in to Google Drive first.")
            )
            
            if (!databaseFile.exists()) {
                return@withContext Result.failure(Exception("Database file not found"))
            }
            
            android.util.Log.d("GoogleDriveManager", "Getting or creating backup folder...")
            val folderId = getOrCreateBackupFolder()
            
            if (folderId == null) {
                android.util.Log.e("GoogleDriveManager", "Backup folder ID is null - cannot proceed with upload")
                return@withContext Result.failure(
                    Exception("Failed to create backup folder. Ensure Google Cloud Console has OAuth clients configured for:\n" +
                        "• Play Store App Signing: F2:7B:8D:94:C6:DC:86:40:4E:42:B2:03:15:BA:BD:FB:32:B4:3F:C9\n" +
                        "• Release Key: 02:62:27:79:BD:4E:19:C5:F0:9C:95:81:5A:79:8C:9D:DC:AA:98:7A\n" +
                        "• Debug Key: F8:51:52:7B:FD:E1:12:3A:97:56:7E:90:B0:E1:D0:78:F5:C4:AB:BB\n" +
                        "Also ensure OAuth consent screen is published (not in Testing mode).")
                )
            }
            
            // Generate backup filename with timestamp
            val timestamp = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).format(Date())
            val backupFileName = "${databaseName}_$timestamp.sqlite"
            
            android.util.Log.d("GoogleDriveManager", "Uploading to folder: $folderId")
            android.util.Log.d("GoogleDriveManager", "Uploading file: $backupFileName")
            
            // Create file metadata
            val fileMetadata = File().apply {
                name = backupFileName
                parents = listOf(folderId)
            }
            
            // Upload file
            FileInputStream(databaseFile).use { inputStream ->
                val mediaContent = com.google.api.client.http.InputStreamContent(
                    "application/x-sqlite3",
                    inputStream
                )
                mediaContent.length = databaseFile.length()
                
                val uploadedFile = service.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, size, createdTime, webViewLink")
                    .execute()
                
                if (uploadedFile != null && !uploadedFile.id.isNullOrEmpty()) {
                    android.util.Log.d("GoogleDriveManager", "✓ Upload successful: ${uploadedFile.name}")
                    android.util.Log.d("GoogleDriveManager", "File ID: ${uploadedFile.id}")
                    android.util.Log.d("GoogleDriveManager", "File link: ${uploadedFile.webViewLink}")
                    Result.success(uploadedFile)
                } else {
                    android.util.Log.e("GoogleDriveManager", "Upload succeeded but file object is invalid")
                    Result.failure(Exception("Upload succeeded but received invalid file data"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "✗ Upload failed", e)
            android.util.Log.e("GoogleDriveManager", "Error type: ${e.javaClass.simpleName}")
            android.util.Log.e("GoogleDriveManager", "Error message: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * List available backups from Google Drive
     */
    suspend fun listBackups(): Result<List<BackupFileInfo>> = withContext(Dispatchers.IO) {
        try {
            // Ensure service is initialized
            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (lastAccount != null && driveService == null) {
                initializeDriveService(lastAccount)
            }
            
            val service = driveService ?: return@withContext Result.failure(
                Exception("Not authenticated. Please sign in to Google Drive first.")
            )
            
            val folderId = getOrCreateBackupFolder()
                ?: return@withContext Result.failure(Exception("Failed to access backup folder"))
            
            try {
                val result: FileList = service.files().list()
                    .setQ("'$folderId' in parents and name contains '.sqlite' and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id, name, size, createdTime, modifiedTime)")
                    .setOrderBy("createdTime desc")
                    .execute()
                
                val backups = result.files.map { file ->
                    BackupFileInfo(
                        name = file.name,
                        path = file.id,
                        size = file.getSize() ?: 0,
                        modified = Date(file.createdTime?.value ?: 0)
                    )
                }
                
                android.util.Log.d("GoogleDriveManager", "Found ${backups.size} backups")
                Result.success(backups)
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                android.util.Log.e("GoogleDriveManager", "❌ API Error listing backups (${e.statusCode}): ${e.details?.toPrettyString()}")
                
                if (e.statusCode == 401) {
                    // Token expired, reset service
                    driveService = null
                }
                Result.failure(Exception("Failed to list backups: ${e.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "Failed to list backups", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Download backup from Google Drive
     */
    suspend fun downloadBackup(fileId: String, destinationFile: java.io.File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Ensure service is initialized
            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (lastAccount != null && driveService == null) {
                initializeDriveService(lastAccount)
            }
            
            val service = driveService ?: return@withContext Result.failure(
                Exception("Not authenticated. Please sign in to Google Drive first.")
            )
            
            android.util.Log.d("GoogleDriveManager", "Downloading file: $fileId")
            
            try {
                val outputStream = ByteArrayOutputStream()
                service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                
                FileOutputStream(destinationFile).use { fileOutputStream ->
                    outputStream.writeTo(fileOutputStream)
                }
                
                android.util.Log.d("GoogleDriveManager", "✓ Download successful: ${destinationFile.absolutePath}")
                Result.success(Unit)
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                android.util.Log.e("GoogleDriveManager", "❌ API Error during download (${e.statusCode}): ${e.details?.toPrettyString()}")
                
                if (e.statusCode == 401) {
                    // Token expired, reset service
                    driveService = null
                }
                Result.failure(Exception("Download failed: ${e.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "✗ Download failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get the latest backup file info
     */
    suspend fun getLatestBackup(): Result<BackupFileInfo?> = withContext(Dispatchers.IO) {
        try {
            val backupsResult = listBackups()
            if (backupsResult.isSuccess) {
                val backups = backupsResult.getOrNull()
                Result.success(backups?.firstOrNull())
            } else {
                Result.failure(backupsResult.exceptionOrNull() ?: Exception("Failed to list backups"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
