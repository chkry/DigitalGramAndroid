package com.digitalgram.android

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.data.JournalDatabase
import com.digitalgram.android.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    
    private val fontSizeViews = mutableListOf<TextView>()
    private val borderViews = mutableListOf<TextView>()
    
    private var passcodeCallback: (() -> Unit)? = null
    
    // Passcode activity launcher
    private val passcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            passcodeCallback?.invoke()
            passcodeCallback = null
        }
    }
    
    // Backup file picker
    private val backupFilePicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { createBackup(it) }
    }
    
    // Restore file picker
    private val restoreFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { restoreBackup(it) }
    }
    
    // Export file picker
    private val exportFilePicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { exportData(it) }
    }
    
    // Storage location picker
    private val storageLocationPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleStorageLocationSelected(it) }
    }
    
    // Wallpaper image picker
    private val wallpaperPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleWallpaperSelected(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settings = AppSettings.getInstance(this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        
        applyThemeColors()
        setupTheme()
        setupFontSizeButtons()
        setupBorderButtons()
        setupToggles()
        setupPasscode()
        setupFingerprint()
        setupReminder()
        setupStorageLocation()
        setupWallpaper()
        setupDatabases()
        setupBackupRestore()
        setupExport()
        setupAboutLinks()
        
        loadSettings()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh theme colors in case they were edited in ThemeEditorActivity
        applyThemeColors()
    }
    
    private fun applyThemeColors() {
        val themeColors = com.digitalgram.android.util.ThemeColors.getTheme(settings.theme, this)
        val isDark = com.digitalgram.android.util.ThemeColors.isDarkTheme(settings.theme)
        
        // Apply background color
        binding.root.setBackgroundColor(themeColors.backgroundColor)
        
        // Apply text colors to values
        binding.themeValue.setTextColor(themeColors.textColor)
        binding.databaseValue.setTextColor(themeColors.textColor)
        
        // Apply toolbar color
        binding.toolbar.setBackgroundColor(themeColors.backgroundColor)
        supportActionBar?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(themeColors.backgroundColor))
        
        // Update status bar and navigation bar colors
        window.statusBarColor = themeColors.backgroundColor
        window.navigationBarColor = themeColors.backgroundColor
        
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }
    
    private fun setupAboutLinks() {
        binding.linkGithub.setOnClickListener {
            openUrl("https://github.com/chkry")
        }
        binding.linkLinkedin.setOnClickListener {
            openUrl("https://linkedin.com/in/chkry")
        }
        binding.linkWebsite.setOnClickListener {
            openUrl("https://www.chakrireddy.com")
        }
        binding.linkEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:ChakradharReddyPakala@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "DigitalGram Feedback")
            }
            startActivity(Intent.createChooser(intent, "Send Email"))
        }
    }
    
    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
    
    private fun setupTheme() {
        binding.themeValue.setOnClickListener {
            showThemeDialog()
        }
        
        binding.fontFamilyRow.setOnClickListener {
            showFontFamilyDialog()
        }
    }
    
    private fun showFontFamilyDialog() {
        val fonts = AppSettings.ALL_FONTS
        val fontNames = fonts.map { it.second }.toTypedArray()
        val currentIndex = fonts.indexOfFirst { it.first == settings.fontFamily }.takeIf { it >= 0 } ?: 0
        
        AlertDialog.Builder(this)
            .setTitle("Font Family")
            .setSingleChoiceItems(fontNames, currentIndex) { dialog, which ->
                val selectedFont = fonts[which].first
                settings.fontFamily = selectedFont
                binding.fontFamilyValue.text = fonts[which].second
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showThemeDialog() {
        val themes = AppSettings.ALL_THEMES.toTypedArray()
        val themeNames = themes.map { theme -> 
            val displayName = getThemeDisplayName(theme)
            val hasCustom = settings.hasThemeCustomColors(theme)
            if (hasCustom && theme != AppSettings.THEME_CUSTOM) "$displayName ★" else displayName
        }.toTypedArray()
        val currentIndex = themes.indexOf(settings.theme).takeIf { it >= 0 } ?: 0
        
        AlertDialog.Builder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(themeNames, currentIndex) { dialog, which ->
                val selectedTheme = themes[which]
                settings.theme = selectedTheme
                binding.themeValue.text = getThemeDisplayName(selectedTheme)
                
                // Apply theme colors immediately
                applyThemeColors()
                
                // If CUSTOM theme is selected, open color picker
                if (selectedTheme == AppSettings.THEME_CUSTOM) {
                    dialog.dismiss()
                    showCustomThemeColorPicker()
                } else {
                    dialog.dismiss()
                }
            }
            .setPositiveButton("Edit Colors") { dialog, _ ->
                dialog.dismiss()
                val selectedTheme = settings.theme
                openThemeEditor(selectedTheme)
            }
            .setNeutralButton("Reset") { dialog, _ ->
                dialog.dismiss()
                val selectedTheme = settings.theme
                if (selectedTheme == AppSettings.THEME_CUSTOM) {
                    Toast.makeText(this, "Use Edit Colors to modify custom theme", Toast.LENGTH_SHORT).show()
                } else if (settings.hasThemeCustomColors(selectedTheme)) {
                    confirmResetTheme(selectedTheme)
                } else {
                    Toast.makeText(this, "Theme has no customizations to reset", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmResetTheme(themeName: String) {
        AlertDialog.Builder(this)
            .setTitle("Reset Theme")
            .setMessage("Reset '${getThemeDisplayName(themeName)}' to default colors?")
            .setPositiveButton("Reset") { _, _ ->
                settings.resetThemeCustomColors(themeName)
                applyThemeColors()
                Toast.makeText(this, "Theme reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openThemeEditor(themeName: String) {
        val intent = Intent(this, ThemeEditorActivity::class.java).apply {
            putExtra(ThemeEditorActivity.EXTRA_THEME_NAME, themeName)
        }
        startActivity(intent)
    }
    
    private fun getThemeDisplayName(theme: String): String {
        return when (theme) {
            AppSettings.THEME_LIGHT -> "☀️ Light"
            AppSettings.THEME_DARK -> "🌑 Dark"
            AppSettings.THEME_SEPIA -> "📜 Sepia (Calm)"
            AppSettings.THEME_MINT -> "🍃 Mint (Focus)"
            AppSettings.THEME_LAVENDER -> "💜 Lavender (Creative)"
            AppSettings.THEME_PEACH -> "🍑 Peach (Energetic)"
            AppSettings.THEME_SKY -> "☁️ Sky (Productive)"
            AppSettings.THEME_NORD -> "❄️ Nord (Professional)"
            AppSettings.THEME_GRUVBOX -> "🎨 Gruvbox (Retro)"
            AppSettings.THEME_SOLARIZED_DARK -> "🔬 Solarized Dark (Balanced)"
            AppSettings.THEME_ONE_DARK -> "⚫ One Dark (Modern)"
            AppSettings.THEME_DRACULA -> "🦇 Dracula (Vibrant)"
            AppSettings.THEME_AMOLED -> "⚡ AMOLED (Pure Black)"
            AppSettings.THEME_ROSE_GOLD -> "🌸 Rose Gold (Elegant)"
            AppSettings.THEME_CHERRY_BLOSSOM -> "🌺 Cherry Blossom (Delicate)"
            AppSettings.THEME_OCEAN_BREEZE -> "🌊 Ocean Breeze (Calm)"
            AppSettings.THEME_SUNSET -> "🌅 Sunset (Warm)"
            AppSettings.THEME_FOREST -> "🌲 Forest (Natural)"
            AppSettings.THEME_MIDNIGHT_PURPLE -> "🌙 Midnight Purple (Mystical)"
            AppSettings.THEME_CUSTOM -> "🎨 Custom"
            else -> theme
        }
    }
    
    private fun showCustomThemeColorPicker() {
        val intent = Intent(this, CustomThemeActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupStorageLocation() {
        binding.storageLocationRow.setOnClickListener {
            showStorageLocationDialog()
        }
        updateStorageLocationUI()
    }
    
    private fun showStorageLocationDialog() {
        val options = if (settings.customStoragePath != null) {
            arrayOf("Use Default Location", "Choose Custom Folder")
        } else {
            arrayOf("Choose Custom Folder")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Storage Location")
            .setItems(options) { _, which ->
                if (settings.customStoragePath != null && which == 0) {
                    // Reset to default - copy database back to app internal storage
                    confirmMoveToDefaultLocation()
                } else {
                    // Choose custom folder - SAF handles permissions automatically
                    storageLocationPicker.launch(null)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmMoveToDefaultLocation() {
        AlertDialog.Builder(this)
            .setTitle("Move to Default Location")
            .setMessage("This will copy your database back to the app's internal storage. Continue?")
            .setPositiveButton("Move") { _, _ ->
                moveToDefaultLocation()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun moveToDefaultLocation() {
        val customPath = settings.customStoragePath ?: return
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val progressDialog = AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Moving Database")
                    .setMessage("Please wait...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()
                
                withContext(Dispatchers.IO) {
                    val database = JournalDatabase.getInstance(applicationContext)
                    val currentDbName = database.getCurrentDatabaseName()
                    
                    // Close database before copying
                    database.close()
                    
                    // Source: custom location
                    val sourceUri = Uri.parse(customPath)
                    val sourceDocUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, sourceUri)
                    val sourceFile = sourceDocUri?.findFile(currentDbName)
                    
                    // Destination: app internal storage
                    val destFile = getDatabasePath(currentDbName)
                    
                    if (sourceFile != null && sourceFile.exists()) {
                        contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                
                progressDialog.dismiss()
                
                // Clear custom path
                settings.customStoragePath = null
                
                // Reinitialize database
                JournalDatabase.reinitialize(applicationContext)
                
                updateStorageLocationUI()
                Toast.makeText(this@SettingsActivity, "Database moved to default location", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SettingsActivity, "Failed to move database: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun handleStorageLocationSelected(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Take persistable permissions
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                val progressDialog = AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Copying Database")
                    .setMessage("Please wait while your data is being copied...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()
                
                withContext(Dispatchers.IO) {
                    val database = JournalDatabase.getInstance(applicationContext)
                    val currentDbName = database.getCurrentDatabaseName()
                    
                    // Get source file (current database)
                    val sourceFile = if (settings.customStoragePath != null) {
                        // Currently using custom storage
                        val sourceUri = Uri.parse(settings.customStoragePath)
                        val sourceDocFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, sourceUri)
                        sourceDocFile?.findFile(currentDbName)?.uri?.let { 
                            contentResolver.openInputStream(it)
                        }
                    } else {
                        // Using default app storage
                        FileInputStream(getDatabasePath(currentDbName))
                    }
                    
                    // Close database before copying
                    database.close()
                    
                    // Get destination folder
                    val destDocFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, uri)
                    
                    // Delete existing file if present
                    destDocFile?.findFile(currentDbName)?.delete()
                    
                    // Create new file in destination
                    val newFile = destDocFile?.createFile("application/octet-stream", currentDbName)
                    
                    if (newFile != null && sourceFile != null) {
                        contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            sourceFile.use { input ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        throw Exception("Failed to create file in destination")
                    }
                }
                
                progressDialog.dismiss()
                
                // Update setting with new path
                settings.customStoragePath = uri.toString()
                
                // Reinitialize database from new location
                JournalDatabase.reinitialize(applicationContext)
                
                updateStorageLocationUI()
                Toast.makeText(this@SettingsActivity, "Database copied to new location", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SettingsActivity, "Failed to copy database: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateStorageLocationUI() {
        val customPath = settings.customStoragePath
        if (customPath != null) {
            try {
                val uri = Uri.parse(customPath)
                val displayPath = uri.lastPathSegment ?: customPath
                binding.storageLocationValue.text = displayPath
            } catch (e: Exception) {
                binding.storageLocationValue.text = "Custom folder"
            }
        } else {
            binding.storageLocationValue.text = "Default (app internal storage)"
        }
    }
    
    private fun setupWallpaper() {
        binding.wallpaperRow.setOnClickListener {
            showWallpaperDialog()
        }
        updateWallpaperUI()
    }
    
    private fun showWallpaperDialog() {
        val options = if (settings.wallpaperUri != null) {
            arrayOf("Choose Wallpaper", "Remove Wallpaper")
        } else {
            arrayOf("Choose Wallpaper")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Background Wallpaper")
            .setItems(options) { _, which ->
                if (settings.wallpaperUri != null && which == 1) {
                    // Remove wallpaper
                    settings.wallpaperUri = null
                    updateWallpaperUI()
                    Toast.makeText(this, "Wallpaper removed", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                } else {
                    // Choose wallpaper
                    wallpaperPicker.launch(arrayOf("image/*"))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleWallpaperSelected(uri: Uri) {
        try {
            // Take persistent permission
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            // Save the URI
            settings.wallpaperUri = uri.toString()
            updateWallpaperUI()
            Toast.makeText(this, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to set wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun updateWallpaperUI() {
        val wallpaperUri = settings.wallpaperUri
        if (wallpaperUri != null) {
            binding.wallpaperValue.text = "Wallpaper selected"
        } else {
            binding.wallpaperValue.text = "No wallpaper selected"
        }
    }
    
    private fun setupDatabases() {
        binding.databaseRow.setOnClickListener {
            showDatabaseDialog()
        }
        updateDatabaseUI()
    }
    
    private fun showDatabaseDialog() {
        val database = JournalDatabase.getInstance(applicationContext)
        val databases = database.getAvailableDatabases()
        val currentDb = database.getCurrentDatabaseName()
        
        val items = databases.map { db ->
            if (db == currentDb) "$db ✓" else db
        }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle(R.string.databases)
            .setItems(items) { _, which ->
                val selectedDb = databases[which]
                if (selectedDb != currentDb) {
                    switchDatabase(selectedDb)
                }
            }
            .setPositiveButton(R.string.new_database) { _, _ ->
                showNewDatabaseDialog()
            }
            .setNeutralButton("More") { _, _ ->
                showDatabaseOptionsDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDatabaseOptionsDialog() {
        val options = arrayOf(
            "Import Database",
            "Import & Merge Database",
            "Delete Current Database",
            "Export Current Database"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Database Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchImportDatabasePicker(merge = false)
                    1 -> launchImportDatabasePicker(merge = true)
                    2 -> confirmDeleteCurrentDatabase()
                    3 -> exportCurrentDatabase()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private var pendingMerge = false
    
    private val importDatabaseLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importDatabaseFromUri(it, pendingMerge) }
    }
    
    private fun launchImportDatabasePicker(merge: Boolean) {
        pendingMerge = merge
        importDatabaseLauncher.launch("*/*")
    }
    
    private fun importDatabaseFromUri(uri: Uri, merge: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Toast.makeText(this@SettingsActivity, "Failed to open file", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Create a temp file
                val tempFile = File(cacheDir, "import_temp.sqlite")
                withContext(Dispatchers.IO) {
                    inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                val database = JournalDatabase.getInstance(applicationContext)
                
                if (merge) {
                    // Import and merge
                    val result = database.importAndMergeDatabase(tempFile)
                    val (imported, updated, skipped) = result
                    
                    Toast.makeText(
                        this@SettingsActivity,
                        "Merged: $imported new, $updated updated, $skipped skipped",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    JournalDatabase.reinitialize(applicationContext)
                    updateDatabaseUI()
                    setResult(RESULT_OK)
                } else {
                    // Get filename from URI
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.sqlite"
                    val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    
                    val success = database.importDatabase(tempFile, cleanName)
                    if (success) {
                        Toast.makeText(this@SettingsActivity, "Database imported: $cleanName", Toast.LENGTH_SHORT).show()
                        switchDatabase(if (cleanName.endsWith(".sqlite") || cleanName.endsWith(".db")) cleanName else "$cleanName.sqlite")
                    } else {
                        Toast.makeText(this@SettingsActivity, "Failed to import database", Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Clean up temp file
                tempFile.delete()
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SettingsActivity, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun confirmDeleteCurrentDatabase() {
        val database = JournalDatabase.getInstance(applicationContext)
        val currentDb = database.getCurrentDatabaseName()
        
        if (currentDb == "daygram.sqlite") {
            Toast.makeText(this, "Cannot delete default database", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Delete Database")
            .setMessage("Are you sure you want to delete '$currentDb'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                if (database.deleteCurrentDatabase()) {
                    Toast.makeText(this, "Database deleted", Toast.LENGTH_SHORT).show()
                    JournalDatabase.reinitialize(applicationContext)
                    updateDatabaseUI()
                    setResult(RESULT_OK)
                } else {
                    Toast.makeText(this, "Failed to delete database", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun exportCurrentDatabase() {
        val database = JournalDatabase.getInstance(applicationContext)
        val currentDb = database.getCurrentDatabaseName()
        val dbFile = getDatabasePath(currentDb)
        
        if (!dbFile.exists()) {
            Toast.makeText(this, "Database file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Create share intent
            val exportDir = File(cacheDir, "export")
            exportDir.mkdirs()
            val exportFile = File(exportDir, currentDb)
            dbFile.copyTo(exportFile, overwrite = true)
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                exportFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Export Database"))
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showNewDatabaseDialog() {
        val input = EditText(this).apply {
            hint = "Database name"
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.new_database)
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val database = JournalDatabase.getInstance(applicationContext)
                    if (database.createNewDatabase(name)) {
                        Toast.makeText(this, R.string.database_created, Toast.LENGTH_SHORT).show()
                        switchDatabase(if (name.endsWith(".sqlite") || name.endsWith(".db")) name else "$name.sqlite")
                    } else {
                        Toast.makeText(this, R.string.database_exists, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun switchDatabase(databaseName: String) {
        try {
            settings.currentDatabase = databaseName
            JournalDatabase.reinitialize(applicationContext)
            updateDatabaseUI()
            Toast.makeText(this, getString(R.string.switched_to_database, databaseName), Toast.LENGTH_SHORT).show()
            
            // Set result to notify MainActivity to reload data
            setResult(RESULT_OK)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error switching database: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun updateDatabaseUI() {
        try {
            val database = JournalDatabase.getInstance(applicationContext)
            binding.databaseValue.text = database.getCurrentDatabaseName()
            binding.databaseCount.text = getString(R.string.total_databases, database.getAvailableDatabases().size)
        } catch (e: Exception) {
            e.printStackTrace()
            binding.databaseValue.text = "Error"
            binding.databaseCount.text = "Error"
        }
    }
    
    private fun setupFontSizeButtons() {
        fontSizeViews.addAll(listOf(
            binding.fontSize1,
            binding.fontSize2,
            binding.fontSize3,
            binding.fontSize4,
            binding.fontSize5
        ))
        
        fontSizeViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                settings.fontSize = index + 1
                updateFontSizeUI()
            }
        }
    }
    
    private fun setupBorderButtons() {
        borderViews.addAll(listOf(
            binding.borderA,
            binding.borderB,
            binding.borderC,
            binding.borderE
        ))
        
        val borderStyles = listOf(
            AppSettings.BORDER_A,
            AppSettings.BORDER_B,
            AppSettings.BORDER_C,
            AppSettings.BORDER_E
        )
        
        borderViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                settings.borderStyle = borderStyles[index]
                updateBorderUI()
            }
        }
    }
    
    private fun setupToggles() {
        // System Font toggle
        binding.systemFontOff.setOnClickListener {
            settings.useSystemFont = false
            updateSystemFontUI()
        }
        binding.systemFontOn.setOnClickListener {
            settings.useSystemFont = true
            updateSystemFontUI()
        }

        // Fullscreen toggle
        binding.fullscreenOff.setOnClickListener {
            settings.fullscreen = false
            applyFullscreenSetting()
            updateFullscreenUI()
        }
        binding.fullscreenOn.setOnClickListener {
            settings.fullscreen = true
            applyFullscreenSetting()
            updateFullscreenUI()
        }
    }
    
    private fun setupPasscode() {
        binding.passcodeOff.setOnClickListener {
            if (settings.passcodeEnabled) {
                // Verify current passcode before disabling
                showPasscodeDialog(PasscodeMode.VERIFY) {
                    settings.passcodeEnabled = false
                    settings.passcode = ""
                    settings.fingerprintEnabled = false
                    updatePasscodeUI()
                    updateFingerprintUI()
                }
            }
        }
        
        binding.passcodeOn.setOnClickListener {
            if (!settings.passcodeEnabled) {
                // Set new passcode
                showPasscodeDialog(PasscodeMode.SET) {
                    settings.passcodeEnabled = true
                    updatePasscodeUI()
                    updateFingerprintUI()
                }
            }
        }
        
        binding.changePasscode.setOnClickListener {
            if (settings.passcodeEnabled) {
                showPasscodeDialog(PasscodeMode.CHANGE) {
                    Toast.makeText(this, R.string.passcode_set, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setupFingerprint() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // Show row but indicate biometric not available
            binding.fingerprintRow.visibility = View.VISIBLE
            binding.fingerprintRow.alpha = 0.3f
            binding.fingerprintLabel.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            binding.fingerprintOff.isClickable = false
            binding.fingerprintOn.isClickable = false
            
            val errorMessage = when (canAuthenticate) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Hardware unavailable"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint enrolled"
                else -> "Biometric unavailable"
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            return
        }
        
        executor = ContextCompat.getMainExecutor(this)
        
        binding.fingerprintOff.setOnClickListener {
            settings.fingerprintEnabled = false
            updateFingerprintUI()
        }
        
        binding.fingerprintOn.setOnClickListener {
            if (settings.passcodeEnabled) {
                // Test biometric before enabling
                showBiometricPrompt {
                    settings.fingerprintEnabled = true
                    updateFingerprintUI()
                }
            } else {
                Toast.makeText(this, "Enable passcode first", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@SettingsActivity, 
                        "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@SettingsActivity, 
                        "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Verify your identity")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Cancel")
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    private fun setupReminder() {
        binding.reminderHourDown.setOnClickListener {
            val newTime = settings.reminderTimeMinutes - 60
            settings.reminderTimeMinutes = if (newTime < 0) 23 * 60 + (newTime + 60) % 60 else newTime
            updateReminderUI()
            scheduleReminder()
        }
        
        binding.reminderMinuteDown.setOnClickListener {
            val newTime = settings.reminderTimeMinutes - 15
            settings.reminderTimeMinutes = if (newTime < 0) 24 * 60 + newTime else newTime
            updateReminderUI()
            scheduleReminder()
        }
        
        binding.reminderMinuteUp.setOnClickListener {
            val newTime = settings.reminderTimeMinutes + 15
            settings.reminderTimeMinutes = newTime % (24 * 60)
            updateReminderUI()
            scheduleReminder()
        }
        
        binding.reminderHourUp.setOnClickListener {
            val newTime = settings.reminderTimeMinutes + 60
            settings.reminderTimeMinutes = newTime % (24 * 60)
            updateReminderUI()
            scheduleReminder()
        }
    }
    
    private fun setupBackupRestore() {
        binding.backupRestoreRow.setOnClickListener {
            showBackupRestoreDialog()
        }
    }
    
    private fun setupExport() {
        binding.exportRow.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportFilePicker.launch("digitalgram_export_$timestamp.txt")
        }
    }
    
    private fun loadSettings() {
        binding.themeValue.text = getThemeDisplayName(settings.theme)
        binding.fontFamilyValue.text = AppSettings.ALL_FONTS.find { it.first == settings.fontFamily }?.second ?: "Bookerly"
        updateFontSizeUI()
        updateSystemFontUI()
        updateFullscreenUI()
        updateBorderUI()
        updatePasscodeUI()
        updateFingerprintUI()
        updateReminderUI()
    }
    
    private fun updateFontSizeUI() {
        fontSizeViews.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index + 1 == settings.fontSize) 
                    R.drawable.circle_button_selected 
                else 
                    R.drawable.circle_button
            )
        }
    }
    
    private fun updateBorderUI() {
        val borderStyles = listOf(
            AppSettings.BORDER_A,
            AppSettings.BORDER_B,
            AppSettings.BORDER_C,
            AppSettings.BORDER_E
        )
        
        borderViews.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (borderStyles[index] == settings.borderStyle) 
                    R.drawable.circle_button_selected 
                else 
                    R.drawable.circle_button
            )
        }
    }
    
    private fun updateSystemFontUI() {
        binding.systemFontOff.setBackgroundResource(
            if (!settings.useSystemFont) R.drawable.toggle_button_selected else R.drawable.toggle_button
        )
        binding.systemFontOn.setBackgroundResource(
            if (settings.useSystemFont) R.drawable.toggle_button_selected else R.drawable.toggle_button
        )
    }

    private fun updateFullscreenUI() {
        binding.fullscreenOff.setBackgroundResource(
            if (!settings.fullscreen) R.drawable.toggle_button_selected else R.drawable.toggle_button
        )
        binding.fullscreenOn.setBackgroundResource(
            if (settings.fullscreen) R.drawable.toggle_button_selected else R.drawable.toggle_button
        )
    }
    
    private fun updatePasscodeUI() {
        binding.passcodeOff.setBackgroundResource(
            if (!settings.passcodeEnabled) R.drawable.toggle_button_selected else R.drawable.toggle_button
        )
        binding.passcodeOn.setBackgroundResource(
            if (settings.passcodeEnabled) R.drawable.toggle_button_selected else R.drawable.toggle_button
        )
        
        // Show/hide change passcode button
        binding.changePasscode.visibility = if (settings.passcodeEnabled) View.VISIBLE else View.INVISIBLE
    }
    
    private fun updateFingerprintUI() {
        val enabled = settings.passcodeEnabled
        binding.fingerprintRow.alpha = if (enabled) 1.0f else 0.5f
        binding.fingerprintLabel.setTextColor(
            ContextCompat.getColor(this, if (enabled) R.color.text_dark else R.color.text_hint)
        )
        
        binding.fingerprintOff.setBackgroundResource(
            if (!settings.fingerprintEnabled) R.drawable.toggle_button_selected else R.drawable.toggle_button
        )
        binding.fingerprintOn.setBackgroundResource(
            if (settings.fingerprintEnabled) R.drawable.toggle_button_selected else R.drawable.toggle_button
        )
        
        // Disable clicks if passcode not enabled
        binding.fingerprintOff.isClickable = enabled
        binding.fingerprintOn.isClickable = enabled
    }
    
    private fun updateReminderUI() {
        binding.reminderTime.text = settings.getReminderTimeFormatted()
    }
    
    private fun applyFullscreenSetting() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (settings.fullscreen) {
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
            windowInsetsController.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    
    private fun showPasscodeDialog(mode: PasscodeMode, onSuccess: () -> Unit) {
        val intent = Intent(this, PasscodeActivity::class.java).apply {
            putExtra(PasscodeActivity.EXTRA_MODE, mode.name)
        }
        passcodeCallback = onSuccess
        passcodeLauncher.launch(intent)
    }
    
    private fun showBackupRestoreDialog() {
        val options = arrayOf(getString(R.string.create_backup), getString(R.string.restore_from_backup))
        
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_restore)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        backupFilePicker.launch("digitalgram_backup_$timestamp.db")
                    }
                    1 -> restoreFilePicker.launch(arrayOf("*/*"))
                }
            }
            .show()
    }
    
    private fun createBackup(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = JournalDatabase.getInstance(applicationContext)
                val dbName = database.getCurrentDatabaseName()
                val dbFile = getDatabasePath(dbName)
                
                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(dbFile).use { input ->
                        input.copyTo(output)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, R.string.backup_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, R.string.backup_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun restoreBackup(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_from_backup)
            .setMessage("This will replace all current data. Continue?")
            .setPositiveButton("Restore") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val database = JournalDatabase.getInstance(applicationContext)
                        val dbName = database.getCurrentDatabaseName()
                        val dbFile = getDatabasePath(dbName)
                        
                        // Close the database
                        database.close()
                        
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(dbFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Reinitialize database
                        JournalDatabase.reinitialize(applicationContext)
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, R.string.restore_success, Toast.LENGTH_SHORT).show()
                            // Restart app to reload database
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                            finish()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun exportData(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = JournalDatabase.getInstance(applicationContext)
                val entries = database.getAllEntriesSync()
                
                val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                
                val content = buildString {
                    appendLine("DigitalGram Journal Export")
                    appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine("Database: ${database.getCurrentDatabaseName()}")
                    appendLine("=" .repeat(50))
                    appendLine()
                    
                    entries.forEach { entry ->
                        appendLine(dateFormat.format(entry.toDate()))
                        appendLine("-".repeat(30))
                        appendLine(entry.content)
                        appendLine()
                        appendLine()
                    }
                }
                
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(content.toByteArray())
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, R.string.export_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun scheduleReminder() {
        if (!settings.reminderEnabled) return
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, 
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 
                    REQUEST_NOTIFICATION_PERMISSION
                )
                return
            }
        }
        
        createNotificationChannel()
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.reminderTimeMinutes / 60)
            set(Calendar.MINUTE, settings.reminderTimeMinutes % 60)
            set(Calendar.SECOND, 0)
            
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.reminder_channel_description)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    companion object {
        const val REQUEST_PASSCODE = 1001
        const val REQUEST_NOTIFICATION_PERMISSION = 1002
        const val NOTIFICATION_CHANNEL_ID = "digitalgram_reminders"
    }
}

enum class PasscodeMode {
    SET, VERIFY, CHANGE
}
