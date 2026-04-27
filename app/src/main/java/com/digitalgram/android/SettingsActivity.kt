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
import android.util.Log
import android.widget.Toast
import com.digitalgram.android.BuildConfig
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
import com.digitalgram.android.data.DropboxManager
import com.digitalgram.android.data.GoogleDriveManager
import com.digitalgram.android.data.JournalDatabase
import com.digitalgram.android.databinding.ActivitySettingsBinding
import androidx.lifecycle.lifecycleScope
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
    private lateinit var dropboxManager: DropboxManager
    private lateinit var googleDriveManager: GoogleDriveManager
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    
    private val fontSizeViews = mutableListOf<TextView>()
    private val borderViews = mutableListOf<TextView>()
    
    // Dynamic button drawables for theme colors
    private lateinit var selectedButtonDrawable: android.graphics.drawable.GradientDrawable
    private lateinit var unselectedButtonDrawable: android.graphics.drawable.GradientDrawable
    private lateinit var selectedToggleButtonDrawable: android.graphics.drawable.GradientDrawable
    private lateinit var unselectedToggleButtonDrawable: android.graphics.drawable.GradientDrawable
    
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
    
    // Restore file picker - only accept .db and .sqlite files
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
        uri?.let { launchCropActivity(it) }
    }
    
    // UCrop result launcher
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val resultUri = com.yalantis.ucrop.UCrop.getOutput(data)
                resultUri?.let { handleWallpaperSelected(it) }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settings = AppSettings.getInstance(this)
        dropboxManager = DropboxManager.getInstance(this)
        googleDriveManager = GoogleDriveManager(this)
        
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
        setupDropbox()
        setupGoogleDrive()
        setupExport()
        setupDatabaseExport()
        setupAboutLinks()
        
        loadSettings()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh theme colors in case they were edited in ThemeEditorActivity
        applyThemeColors()
        // Refresh Dropbox UI in case connection state changed
        updateDropboxUI()
        // Refresh Google Drive UI
        updateGoogleDriveUI()
    }
    
    private fun applyThemeColors() {
        val themeColors = com.digitalgram.android.util.ThemeColors.getTheme(settings.theme, this)
        val isDark = com.digitalgram.android.util.ThemeColors.isDarkTheme(settings.theme)
        
        // Apply background color
        binding.root.setBackgroundColor(themeColors.backgroundColor)
        
        // Apply text colors to values
        binding.themeValue.setTextColor(themeColors.settingsFontColor)
        binding.databaseValue.setTextColor(themeColors.settingsFontColor)
        
        // Apply settings font color to all labels
        applySettingsLabelColors(themeColors.settingsFontColor)
        
        // Apply colors to About and Database Info sections
        applyAboutAndDatabaseColors(themeColors)
        
        // Apply button colors
        applyButtonColors(themeColors.accentColor, themeColors.secondaryTextColor)
        
        // Refresh UI with new button colors
        updateFontSizeUI()
        updateBorderUI()
        updateSystemFontUI()
        updateFullscreenUI()
        updatePasscodeUI()
        updateFingerprintUI()
        updateReminderUI()
        
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
    
    private fun applyButtonColors(accentColor: Int, unselectedColor: Int) {
        // Create drawable for selected state (accent color)
        val selectedDrawable = android.graphics.drawable.GradientDrawable()
        selectedDrawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        selectedDrawable.setColor(accentColor)
        
        // Create drawable for unselected state (muted color)
        val unselectedDrawable = android.graphics.drawable.GradientDrawable()
        unselectedDrawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        unselectedDrawable.setColor(unselectedColor)
        
        // Store drawables for later use
        selectedButtonDrawable = selectedDrawable
        unselectedButtonDrawable = unselectedDrawable
        
        // Apply to toggle buttons
        val selectedToggleDrawable = android.graphics.drawable.GradientDrawable()
        selectedToggleDrawable.cornerRadius = 20f
        selectedToggleDrawable.setColor(accentColor)
        
        val unselectedToggleDrawable = android.graphics.drawable.GradientDrawable()
        unselectedToggleDrawable.cornerRadius = 20f
        unselectedToggleDrawable.setColor(unselectedColor)
        
        selectedToggleButtonDrawable = selectedToggleDrawable
        unselectedToggleButtonDrawable = unselectedToggleDrawable
    }
    
    private fun applySettingsLabelColors(color: Int) {
        // Apply color to all label TextViews in settings
        val views = ArrayList<View>()
        binding.root.findViewsWithText(views, "▌", View.FIND_VIEWS_WITH_TEXT)
        views.forEach { view ->
            (view as? TextView)?.setTextColor(color)
        }
        
        // Specific labels that need themed colors
        binding.fullscreenLabel.setTextColor(color)
        binding.fingerprintLabel.setTextColor(color)
        binding.backupRestoreLabel.setTextColor(color)
        binding.exportLabel.setTextColor(color)
        binding.storageLocationLabel.setTextColor(color)
        binding.wallpaperLabel.setTextColor(color)
        binding.databaseLabel.setTextColor(color)
        
        // Value text views
        binding.databaseValue.setTextColor(color)
        binding.storageLocationValue.setTextColor(color)
        binding.wallpaperValue.setTextColor(color)
        binding.databaseCount.setTextColor(color)
    }
    
    private fun applyAboutAndDatabaseColors(themeColors: com.digitalgram.android.util.ThemeColors) {
        // Database Info section
        binding.databaseInfoTitle.setTextColor(themeColors.textColor)
        binding.databaseType.setTextColor(themeColors.secondaryTextColor)
        binding.databaseDescription.setTextColor(themeColors.secondaryTextColor)
        
        // About section
        binding.aboutTitle.setTextColor(themeColors.textColor)
        binding.authorLabel.setTextColor(themeColors.secondaryTextColor)
        binding.authorName.setTextColor(themeColors.textColor)
        
        // Links - use link color
        binding.linkGithub.setTextColor(themeColors.linkColor)
        binding.linkLinkedin.setTextColor(themeColors.linkColor)
        binding.linkWebsite.setTextColor(themeColors.linkColor)
        binding.linkEmail.setTextColor(themeColors.linkColor)
        
        // Feedback section
        binding.feedbackTitle.setTextColor(themeColors.textColor)
        binding.feedbackSubtitle.setTextColor(themeColors.secondaryTextColor)
        binding.feedbackMessage.setTextColor(themeColors.secondaryTextColor)
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
        
        // Create custom adapter to show fonts in their own typeface
        val adapter = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.select_dialog_singlechoice,
            fontNames
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.CheckedTextView>(android.R.id.text1)
                
                // Apply the font typeface to the text
                val fontFamily = fonts[position].first
                val typeface = try {
                    when (fontFamily) {
                        AppSettings.FONT_BOOKERLY, AppSettings.FONT_SERIF -> android.graphics.Typeface.SERIF
                        AppSettings.FONT_SANS -> android.graphics.Typeface.SANS_SERIF
                        AppSettings.FONT_MONO -> android.graphics.Typeface.MONOSPACE
                        AppSettings.FONT_CURSIVE -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
                        AppSettings.FONT_CASUAL -> android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL)
                        AppSettings.FONT_SERIF_BOLD -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
                        AppSettings.FONT_SERIF_ITALIC -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
                        AppSettings.FONT_SANS_BOLD -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        AppSettings.FONT_MONO_BOLD -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        AppSettings.FONT_CURSIVE_BOLD -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.BOLD)
                        else -> android.graphics.Typeface.create(fontFamily, android.graphics.Typeface.NORMAL)
                    } ?: android.graphics.Typeface.DEFAULT
                } catch (e: Exception) {
                    android.graphics.Typeface.DEFAULT
                }
                textView.typeface = typeface
                textView.textSize = 18f
                
                return view
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Font Family")
            .setSingleChoiceItems(adapter, currentIndex) { dialog, which ->
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
                if (BuildConfig.DEBUG) Log.w(TAG, "move db failed", e)
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
                if (BuildConfig.DEBUG) Log.w(TAG, "copy db failed", e)
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
    
    private fun launchCropActivity(sourceUri: Uri) {
        try {
            // Get screen dimensions for crop ratio
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Calculate aspect ratio
            val aspectRatioX = screenWidth.toFloat()
            val aspectRatioY = screenHeight.toFloat()
            
            // Create destination file for cropped image
            val destinationFileName = "wallpaper_${System.currentTimeMillis()}.jpg"
            val destinationUri = Uri.fromFile(File(cacheDir, destinationFileName))
            
            // Launch UCrop with screen aspect ratio
            val uCrop = com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(aspectRatioX, aspectRatioY)
                .withMaxResultSize(screenWidth, screenHeight)
            
            // Apply theme colors to UCrop
            val themeColors = com.digitalgram.android.util.ThemeColors.getTheme(settings.theme, this)
            val options = com.yalantis.ucrop.UCrop.Options()
            options.setToolbarColor(themeColors.backgroundColor)
            options.setStatusBarColor(themeColors.backgroundColor)
            options.setActiveControlsWidgetColor(themeColors.accentColor)
            options.setToolbarWidgetColor(themeColors.textColor)
            options.setRootViewBackgroundColor(themeColors.backgroundColor)
            options.setLogoColor(themeColors.accentColor)
            options.setShowCropFrame(true)
            options.setShowCropGrid(true)
            options.setCropGridStrokeWidth(2)
            options.setCropGridColor(themeColors.accentColor)
            options.setToolbarTitle("Crop Wallpaper")
            
            // Add padding to avoid status bar overlap
            options.setFreeStyleCropEnabled(false)
            options.setHideBottomControls(false)
            options.setCompressionQuality(95)
            
            uCrop.withOptions(options)
            
            cropLauncher.launch(uCrop.getIntent(this))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "crop editor failed", e)
            Toast.makeText(this, "Failed to open crop editor: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun handleWallpaperSelected(uri: Uri) {
        try {
            // For cropped images from cache, copy to persistent location
            if (uri.path?.startsWith(cacheDir.absolutePath) == true) {
                // Copy cropped image to app's private storage
                val inputStream = contentResolver.openInputStream(uri)
                val fileName = "wallpaper_${System.currentTimeMillis()}.jpg"
                val outputFile = File(filesDir, fileName)
                
                inputStream?.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Save the file URI
                val fileUri = Uri.fromFile(outputFile)
                settings.wallpaperUri = fileUri.toString()
                updateWallpaperUI()
                Toast.makeText(this, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
            } else {
                // For other URIs, take persistent permission
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // Save the URI
                settings.wallpaperUri = uri.toString()
                updateWallpaperUI()
                Toast.makeText(this, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "set wallpaper failed", e)
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
        lifecycleScope.launch {
        val databases = database.getAvailableDatabases()
        val currentDb = database.getCurrentDatabaseName()

        val items = databases.map { db ->
            if (db == currentDb) "$db ✓" else db
        }.toTypedArray()

        AlertDialog.Builder(this@SettingsActivity)
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
        } // end lifecycleScope.launch
    }

    private fun showDatabaseOptionsDialog() {
        val options = arrayOf(
            "Rename Database",
            "Import Database",
            "Import & Merge Database",
            "Delete Current Database",
            "Share Current Database"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Database Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDatabaseDialog()
                    1 -> launchImportDatabasePicker(merge = false)
                    2 -> launchImportDatabasePicker(merge = true)
                    3 -> confirmDeleteCurrentDatabase()
                    4 -> exportCurrentDatabase()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showRenameDatabaseDialog() {
        val database = JournalDatabase.getInstance(applicationContext)
        val currentName = database.getCurrentDatabaseName()
        val nameWithoutExt = currentName.removeSuffix(".sqlite").removeSuffix(".db")
        
        val editText = android.widget.EditText(this)
        editText.setText(nameWithoutExt)
        editText.selectAll()
        editText.setHint("Database name")
        
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 2
        params.rightMargin = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 2
        editText.layoutParams = params
        container.addView(editText)
        
        AlertDialog.Builder(this)
            .setTitle("Rename Database")
            .setMessage("Enter a new name for the database")
            .setView(container)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Database name cannot be empty", Toast.LENGTH_SHORT).show()
                } else if (newName.contains("/") || newName.contains("\\")) {
                    Toast.makeText(this, "Database name cannot contain slashes", Toast.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch {
                        val renamed = database.renameDatabase(newName)
                        if (renamed) {
                            Toast.makeText(this@SettingsActivity, "Database renamed successfully", Toast.LENGTH_SHORT).show()
                            binding.databaseValue.text = database.getCurrentDatabaseName()
                            setResult(RESULT_OK)
                        } else {
                            Toast.makeText(this@SettingsActivity, "Failed to rename database. Name may already exist.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        // Show keyboard
        editText.postDelayed({
            editText.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 100)
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
                if (BuildConfig.DEBUG) Log.w(TAG, "import failed", e)
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
                lifecycleScope.launch {
                    if (database.deleteCurrentDatabase()) {
                        Toast.makeText(this@SettingsActivity, "Database deleted", Toast.LENGTH_SHORT).show()
                        JournalDatabase.reinitialize(applicationContext)
                        updateDatabaseUI()
                        setResult(RESULT_OK)
                    } else {
                        Toast.makeText(this@SettingsActivity, "Failed to delete database", Toast.LENGTH_SHORT).show()
                    }
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
            if (BuildConfig.DEBUG) Log.w(TAG, "export failed", e)
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
                    lifecycleScope.launch {
                        if (database.createNewDatabase(name)) {
                            Toast.makeText(this@SettingsActivity, R.string.database_created, Toast.LENGTH_SHORT).show()
                            switchDatabase(if (name.endsWith(".sqlite") || name.endsWith(".db")) name else "$name.sqlite")
                        } else {
                            Toast.makeText(this@SettingsActivity, R.string.database_exists, Toast.LENGTH_SHORT).show()
                        }
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
            if (BuildConfig.DEBUG) Log.w(TAG, "switch db failed", e)
            Toast.makeText(this, "Error switching database: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun updateDatabaseUI() {
        val database = JournalDatabase.getInstance(applicationContext)
        binding.databaseValue.text = database.getCurrentDatabaseName()
        lifecycleScope.launch {
            try {
                val count = database.getAvailableDatabases().size
                binding.databaseCount.text = getString(R.string.total_databases, count)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "db count failed", e)
                binding.databaseCount.text = "Error"
            }
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
        // ON/OFF toggle
        binding.reminderOff.setOnClickListener {
            settings.reminderEnabled = false
            updateReminderUI()
            cancelReminder()
        }
        
        binding.reminderOn.setOnClickListener {
            settings.reminderEnabled = true
            updateReminderUI()
            scheduleReminder()
        }
        
        // Time adjustment buttons
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
    
    private fun setupDatabaseExport() {
        binding.exportDatabaseRow.setOnClickListener {
            exportCurrentDatabase()
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
            view.background = if (index + 1 == settings.fontSize) 
                selectedButtonDrawable.constantState?.newDrawable()?.mutate()
            else 
                unselectedButtonDrawable.constantState?.newDrawable()?.mutate()
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
            view.background = if (borderStyles[index] == settings.borderStyle) 
                selectedButtonDrawable.constantState?.newDrawable()?.mutate()
            else 
                unselectedButtonDrawable.constantState?.newDrawable()?.mutate()
        }
    }
    
    private fun updateSystemFontUI() {
        binding.systemFontOff.background = if (!settings.useSystemFont)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
            
        binding.systemFontOn.background = if (settings.useSystemFont)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
    }

    private fun updateFullscreenUI() {
        binding.fullscreenOff.background = if (!settings.fullscreen)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
            
        binding.fullscreenOn.background = if (settings.fullscreen)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
    }
    
    private fun updatePasscodeUI() {
        binding.passcodeOff.background = if (!settings.passcodeEnabled)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
            
        binding.passcodeOn.background = if (settings.passcodeEnabled)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        
        // Show/hide change passcode button
        binding.changePasscode.visibility = if (settings.passcodeEnabled) View.VISIBLE else View.INVISIBLE
    }
    
    private fun updateFingerprintUI() {
        val enabled = settings.passcodeEnabled
        binding.fingerprintRow.alpha = if (enabled) 1.0f else 0.5f
        binding.fingerprintLabel.setTextColor(
            ContextCompat.getColor(this, if (enabled) R.color.text_dark else R.color.text_hint)
        )
        
        binding.fingerprintOff.background = if (!settings.fingerprintEnabled)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
            
        binding.fingerprintOn.background = if (settings.fingerprintEnabled)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        
        // Disable clicks if passcode not enabled
        binding.fingerprintOff.isClickable = enabled
        binding.fingerprintOn.isClickable = enabled
    }
    
    private fun updateReminderUI() {
        binding.reminderTime.text = settings.getReminderTimeFormatted()
        
        // Update toggle buttons
        binding.reminderOff.background = if (!settings.reminderEnabled)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
            
        binding.reminderOn.background = if (settings.reminderEnabled)
            selectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        else
            unselectedToggleButtonDrawable.constantState?.newDrawable()?.mutate()
        
        // Enable/disable time controls based on reminder state
        val enabled = settings.reminderEnabled
        binding.reminderTimeRow.alpha = if (enabled) 1.0f else 0.5f
        binding.reminderHourDown.isClickable = enabled
        binding.reminderMinuteDown.isClickable = enabled
        binding.reminderMinuteUp.isClickable = enabled
        binding.reminderHourUp.isClickable = enabled
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
                    1 -> restoreFilePicker.launch(arrayOf("application/x-sqlite3", "application/vnd.sqlite3", "application/octet-stream"))
                }
            }
            .show()
    }
    
    private fun revertLastRestore(backupFile: File) {
        if (!backupFile.exists()) {
            Toast.makeText(this, "Backup file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Revert Merge?")
            .setMessage("This will restore your database to the state before the merge. All changes from the merge will be lost.")
            .setPositiveButton("Revert") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val database = JournalDatabase.getInstance(applicationContext)
                        val dbName = database.getCurrentDatabaseName()
                        val dbFile = getDatabasePath(dbName)
                        
                        // Close database
                        database.close()
                        
                        // Restore from backup
                        backupFile.copyTo(dbFile, overwrite = true)
                        
                        // Delete the backup file
                        backupFile.delete()
                        
                        // Reinitialize database
                        JournalDatabase.reinitialize(applicationContext)
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "Database reverted successfully", Toast.LENGTH_SHORT).show()
                            recreate()
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "revert failed", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "Revert failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
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
        // Validate file extension
        val fileName = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else null
        } ?: "unknown"
        
        if (!fileName.endsWith(".db", ignoreCase = true) && !fileName.endsWith(".sqlite", ignoreCase = true)) {
            Toast.makeText(this, "Please select a .db or .sqlite file", Toast.LENGTH_LONG).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_from_backup)
            .setMessage("This will merge entries from \"$fileName\" with your current database.\n\nNewer entries will be kept in case of conflicts.\n\nAn automatic backup will be created that you can revert if needed.")
            .setPositiveButton("Merge") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val tempFile = File(cacheDir, "restore_temp.sqlite")
                    val backupFile = File(cacheDir, "auto_backup_before_restore.db")
                    
                    try {
                        val database = JournalDatabase.getInstance(applicationContext)
                        val dbName = database.getCurrentDatabaseName()
                        val dbFile = getDatabasePath(dbName)
                        
                        // Create automatic backup before merging
                        dbFile.copyTo(backupFile, overwrite = true)
                        
                        // Copy selected file to temp location
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Merge the databases
                        val (imported, updated, skipped) = database.importAndMergeDatabase(tempFile)
                        
                        // Cleanup temp file
                        tempFile.delete()
                        
                        withContext(Dispatchers.Main) {
                            // Show success with stats and revert option
                            AlertDialog.Builder(this@SettingsActivity)
                                .setTitle("Merge Complete")
                                .setMessage("Import successful!\n\nNew entries: $imported\nUpdated entries: $updated\nSkipped entries: $skipped\n\nAn automatic backup was created before the merge.")
                                .setPositiveButton("OK") { _, _ ->
                                    // Refresh the activity
                                    recreate()
                                }
                                .setNegativeButton("Revert Merge") { _, _ ->
                                    revertLastRestore(backupFile)
                                }
                                .setCancelable(false)
                                .show()
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "restore failed", e)
                        tempFile.delete()

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "${getString(R.string.restore_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
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
                val entries = database.getAllEntries()
                
                // Sort entries by date (oldest first for chronological reading), skip empty ones
                val sortedEntries = entries.filter { it.content.isNotBlank() }.sortedBy { it.date }
                
                val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                
                val content = buildString {
                    appendLine("DigitalGram Journal Export")
                    appendLine("Exported: ${timeFormat.format(Date())}")
                    appendLine("Database: ${database.getCurrentDatabaseName()}")
                    appendLine("Total Entries: ${sortedEntries.size}")
                    appendLine("=".repeat(50))
                    appendLine()
                    
                    sortedEntries.forEach { entry ->
                        appendLine(dateFormat.format(entry.toDate()))
                        appendLine("-".repeat(30))
                        appendLine(entry.content)
                        appendLine()
                        appendLine("=".repeat(50))
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
                if (BuildConfig.DEBUG) Log.w(TAG, "export db failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "${getString(R.string.export_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
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
    
    private fun cancelReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
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
    
    // DROPBOX BACKUP/RESTORE
    
    // Activity result launcher for Dropbox auth
    private val dropboxAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("SettingsActivity", "Dropbox auth result: ${result.resultCode}")
        when (result.resultCode) {
            DropboxAuthActivity.RESULT_AUTH_SUCCESS -> {
                Toast.makeText(this, "✓ Connected to Dropbox!", Toast.LENGTH_LONG).show()
                updateDropboxUI()
            }
            DropboxAuthActivity.RESULT_AUTH_FAILED -> {
                Toast.makeText(this, "Dropbox connection failed or cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startDropboxAuth() {
        val intent = Intent(this, DropboxAuthActivity::class.java)
        dropboxAuthLauncher.launch(intent)
    }

    private fun setupDropbox() {
        updateDropboxUI()
        binding.dropboxConnectButton.setOnClickListener {
            if (dropboxManager.isAuthenticated()) {
                showDropboxDisconnectDialog()
            } else {
                startDropboxAuth()
            }
        }
        binding.dropboxBackupButton.setOnClickListener {
            backupToDropbox()
        }
        binding.dropboxRestoreButton.setOnClickListener {
            showDropboxRestoreDialog()
        }
    }
    
    private fun updateDropboxUI() {
        val isConnected = dropboxManager.isAuthenticated()
        
        if (isConnected) {
            binding.dropboxStatusText.text = "Connected"
            binding.dropboxStatusText.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.dropboxConnectButton.text = "Disconnect"
            binding.dropboxBackupButton.isEnabled = true
            binding.dropboxRestoreButton.isEnabled = true
            
            // Update button colors when enabled
            (binding.dropboxBackupButton as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.accent_red))
                strokeColor = ContextCompat.getColorStateList(this@SettingsActivity, R.color.accent_red)
            }
            (binding.dropboxRestoreButton as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.accent_red))
                strokeColor = ContextCompat.getColorStateList(this@SettingsActivity, R.color.accent_red)
            }
            
            // Fetch and display last backup info
            CoroutineScope(Dispatchers.Main).launch {
                val result = dropboxManager.getLatestBackup()
                if (result.isSuccess) {
                    result.getOrNull()?.let { backup ->
                        binding.dropboxStatusText.text = "Last backup: ${backup.getFormattedDate()}"
                    }
                }
            }
        } else {
            binding.dropboxStatusText.text = "Not connected"
            binding.dropboxStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            binding.dropboxConnectButton.text = "Connect"
            binding.dropboxBackupButton.isEnabled = false
            binding.dropboxRestoreButton.isEnabled = false
            
            // Reset button colors when disabled
            (binding.dropboxBackupButton as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_hint))
                strokeColor = ContextCompat.getColorStateList(this@SettingsActivity, R.color.text_hint)
            }
            (binding.dropboxRestoreButton as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_hint))
                strokeColor = ContextCompat.getColorStateList(this@SettingsActivity, R.color.text_hint)
            }
        }
    }
    
    private fun showDropboxDisconnectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Disconnect from Dropbox?")
            .setMessage("This will remove the Dropbox connection. Your backups will remain in Dropbox.")
            .setPositiveButton("Disconnect") { _, _ ->
                dropboxManager.disconnect()
                updateDropboxUI()
                Toast.makeText(this, "Disconnected from Dropbox", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun backupToDropbox() {
        val database = JournalDatabase.getInstance(applicationContext)
        val databaseFile = getDatabasePath(settings.currentDatabase)
        val databaseName = settings.currentDatabase.removeSuffix(".sqlite").removeSuffix(".db")
        
        if (!databaseFile.exists()) {
            Toast.makeText(this, "Database file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Close database before backup
        database.close()
        
        binding.dropboxBackupButton.isEnabled = false
        binding.dropboxBackupButton.text = "Uploading..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val result = dropboxManager.uploadBackup(databaseFile, databaseName)
            
            // Reopen database
            JournalDatabase.getInstance(applicationContext)
            
            if (result.isSuccess) {
                val metadata = result.getOrNull()
                Toast.makeText(
                    this@SettingsActivity,
                    "Backup uploaded successfully",
                    Toast.LENGTH_SHORT
                ).show()
                updateDropboxUI()
            } else {
                val error = result.exceptionOrNull()
                val errorMsg = error?.message ?: "Unknown error"
                
                // Check if it's a certificate error
                if (errorMsg.contains("CertPathValidator", ignoreCase = true) ||
                    errorMsg.contains("Trust anchor", ignoreCase = true) ||
                    errorMsg.contains("certificate", ignoreCase = true)) {
                    
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Dropbox Connection Issue")
                        .setMessage("Unable to connect to Dropbox. This may be due to network restrictions.\n\nTry:\n• Using mobile data instead of Wi-Fi\n• Disabling VPN\n• Using Google Drive backup instead")
                        .setPositiveButton("Try Google Drive") { _, _ ->
                            // Highlight Google Drive section
                            binding.googleDriveConnectButton.requestFocus()
                        }
                        .setNegativeButton("OK", null)
                        .show()
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Backup failed: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            binding.dropboxBackupButton.isEnabled = true
            binding.dropboxBackupButton.text = "Backup"
        }
    }
    
    private fun showDropboxRestoreDialog() {
        binding.dropboxRestoreButton.isEnabled = false
        binding.dropboxRestoreButton.text = "Loading..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val result = dropboxManager.listBackups()
            
            if (result.isSuccess) {
                val backups = result.getOrNull() ?: emptyList()
                
                if (backups.isEmpty()) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "No backups found in Dropbox",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.dropboxRestoreButton.isEnabled = true
                    binding.dropboxRestoreButton.text = "Restore"
                    return@launch
                }
                
                // Show list of backups
                val backupNames = backups.map { "${it.name} (${it.getFormattedDate()})" }.toTypedArray()
                
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Select Backup to Restore")
                    .setItems(backupNames) { _, which ->
                        val selectedBackup = backups[which]
                        confirmAndRestoreFromDropbox(selectedBackup)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.dropboxRestoreButton.isEnabled = true
                        binding.dropboxRestoreButton.text = "Restore"
                    }
                    .setOnCancelListener {
                        binding.dropboxRestoreButton.isEnabled = true
                        binding.dropboxRestoreButton.text = "Restore"
                    }
                    .show()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Toast.makeText(
                    this@SettingsActivity,
                    "Failed to list backups: $error",
                    Toast.LENGTH_LONG
                ).show()
                binding.dropboxRestoreButton.isEnabled = true
                binding.dropboxRestoreButton.text = "Restore"
            }
        }
    }
    
    private fun confirmAndRestoreFromDropbox(backup: com.digitalgram.android.data.BackupFileInfo) {
        AlertDialog.Builder(this)
            .setTitle("Restore from Dropbox?")
            .setMessage("This will merge entries from \"${backup.name}\" with your current database. Newer entries will be kept in case of conflicts.\\n\\nSize: ${backup.getFormattedSize()}\\nDate: ${backup.getFormattedDate()}")
            .setPositiveButton("Restore & Merge") { _, _ ->
                restoreFromDropbox(backup)
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.dropboxRestoreButton.isEnabled = true
                binding.dropboxRestoreButton.text = "Restore"
            }
            .show()
    }
    
    private fun restoreFromDropbox(backup: com.digitalgram.android.data.BackupFileInfo) {
        binding.dropboxRestoreButton.text = "Downloading..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val tempFile = File(cacheDir, "dropbox_restore_temp.sqlite")
            
            try {
                val downloadResult = dropboxManager.downloadBackup(backup.path, tempFile)
                
                if (downloadResult.isFailure) {
                    throw downloadResult.exceptionOrNull() ?: Exception("Download failed")
                }
                
                // Close current database
                val database = JournalDatabase.getInstance(applicationContext)
                database.close()
                
                // Import and merge
                withContext(Dispatchers.IO) {
                    val db = JournalDatabase.getInstance(applicationContext)
                    db.importAndMergeDatabase(tempFile)
                }
                
                // Cleanup
                tempFile.delete()
                
                Toast.makeText(
                    this@SettingsActivity,
                    "Restore completed successfully",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Refresh UI
                recreate()
                
            } catch (e: Exception) {
                tempFile.delete()
                Toast.makeText(
                    this@SettingsActivity,
                    "Restore failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                
                binding.dropboxRestoreButton.isEnabled = true
                binding.dropboxRestoreButton.text = "Restore"
            }
        }
    }
    
    // GOOGLE DRIVE BACKUP/RESTORE
    
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("SettingsActivity", "Google Sign-In result: ${result.resultCode}, data: ${result.data}")
        if (result.resultCode == RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                android.util.Log.d("SettingsActivity", "Google account: ${account?.email}")
                if (googleDriveManager.handleSignInResult(account)) {
                    Toast.makeText(this, "✓ Connected to Google Drive!", Toast.LENGTH_LONG).show()
                    updateGoogleDriveUI()
                } else {
                    Toast.makeText(this, "Google Drive sign-in failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Google Sign-In exception: ${e.javaClass.simpleName}", e)
                android.util.Log.e("SettingsActivity", "Error message: ${e.message}")
                android.util.Log.e("SettingsActivity", "Error status code: ${e.cause}")
                
                // Get more detailed error info
                val statusCode = if (e.message?.contains("10:") == true) {
                    "Error 10 (Developer Error)"
                } else if (e.message?.contains("12501") == true) {
                    "Error 12501 (User Cancelled)"
                } else if (e.message?.contains("12500") == true) {
                    "Error 12500 (API Error)"
                } else {
                    "Unknown Error"
                }
                
                val errorMsg = "$statusCode: ${e.message}\n\n" +
                    "DIAGNOSTIC INFO:\n" +
                    "Package: ${packageName}\n" +
                    "App Version: ${packageManager.getPackageInfo(packageName, 0).versionName}\n\n" +
                    "COMMON CAUSES (for Play Store installs):\n" +
                    "1. OAuth Client ID NOT added for Play Store App Signing key\n" +
                    "   → Go to Google Cloud Console\n" +
                    "   → Add OAuth client for: F2:7B:8D:94:C6:DC:86:40:4E:42:B2:03:15:BA:BD:FB:32:B4:3F:C9\n\n" +
                    "2. OAuth Consent Screen still in TESTING mode\n" +
                    "   → Change to PRODUCTION\n\n" +
                    "3. User not added as test user (if still in Testing)\n" +
                    "   → Add your Google account as test user\n\n" +
                    "4. Package name mismatch: should be com.digitalgram.android"
                
                AlertDialog.Builder(this)
                    .setTitle("Google Drive Sign-In Failed")
                    .setMessage(errorMsg)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("View Logs") { _, _ ->
                        // Copy logs to clipboard for debugging
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Google Drive Debug", errorMsg)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Diagnostic info copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        } else {
            android.util.Log.w("SettingsActivity", "Google Sign-In cancelled (result code: ${result.resultCode})")
            AlertDialog.Builder(this)
                .setTitle("Sign-In Cancelled")
                .setMessage(
                    "Google Drive sign-in was cancelled.\n\n" +
                    "If you saw an 'This app isn't verified' warning screen, " +
                    "scroll down and tap Advanced → Go to DigitalGram (unsafe) to continue."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun setupGoogleDrive() {
        updateGoogleDriveUI()
        binding.googleDriveConnectButton.setOnClickListener {
            if (googleDriveManager.isAuthenticated()) {
                showGoogleDriveDisconnectDialog()
            } else {
                startGoogleDriveSignIn()
            }
        }
        binding.googleDriveBackupButton.setOnClickListener {
            backupToGoogleDrive()
        }
        binding.googleDriveRestoreButton.setOnClickListener {
            showGoogleDriveRestoreDialog()
        }
    }
    
    private fun updateGoogleDriveUI() {
        val isConnected = googleDriveManager.isAuthenticated()
        
        if (isConnected) {
            binding.googleDriveStatusText.text = "Connected: ${settings.googleDriveAccountEmail}"
            binding.googleDriveStatusText.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.googleDriveConnectButton.text = "Disconnect"
            binding.googleDriveBackupButton.isEnabled = true
            binding.googleDriveRestoreButton.isEnabled = true
            
            // Update button colors when enabled
            (binding.googleDriveBackupButton as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.accent_red))
                strokeColor = ContextCompat.getColorStateList(this@SettingsActivity, R.color.accent_red)
            }
            (binding.googleDriveRestoreButton as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.accent_red))
                strokeColor = ContextCompat.getColorStateList(this@SettingsActivity, R.color.accent_red)
            }
        } else {
            binding.googleDriveStatusText.text = "Not connected"
            binding.googleDriveStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            binding.googleDriveConnectButton.text = "Connect"
            binding.googleDriveBackupButton.isEnabled = false
            binding.googleDriveRestoreButton.isEnabled = false
            
            // Reset button colors when disabled
            (binding.googleDriveBackupButton as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_hint))
                strokeColor = ContextCompat.getColorStateList(this@SettingsActivity, R.color.text_hint)
            }
            (binding.googleDriveRestoreButton as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_hint))
                strokeColor = ContextCompat.getColorStateList(this@SettingsActivity, R.color.text_hint)
            }
        }
    }
    
    private fun startGoogleDriveSignIn() {
        val signInIntent = googleDriveManager.getSignInClient(this).signInIntent
        googleSignInLauncher.launch(signInIntent)
    }
    
    private fun showGoogleDriveDisconnectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Disconnect from Google Drive?")
            .setMessage("This will remove the Google Drive connection. Your backups will remain in Google Drive.")
            .setPositiveButton("Disconnect") { _, _ ->
                googleDriveManager.disconnect(this)
                updateGoogleDriveUI()
                Toast.makeText(this, "Disconnected from Google Drive", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun backupToGoogleDrive() {
        android.util.Log.d("SettingsActivity", "===== Google Drive Backup Start =====")
        val database = JournalDatabase.getInstance(applicationContext)
        val databaseFile = getDatabasePath(settings.currentDatabase)
        val databaseName = settings.currentDatabase.removeSuffix(".sqlite").removeSuffix(".db")
        
        android.util.Log.d("SettingsActivity", "Database: ${databaseFile.absolutePath}")
        android.util.Log.d("SettingsActivity", "Database exists: ${databaseFile.exists()}")
        android.util.Log.d("SettingsActivity", "Is authenticated: ${googleDriveManager.isAuthenticated()}")
        
        if (!databaseFile.exists()) {
            Toast.makeText(this, "Database file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!googleDriveManager.isAuthenticated()) {
            Toast.makeText(this, "Not connected to Google Drive. Please connect first.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Close database before backup
        database.close()
        
        binding.googleDriveBackupButton.isEnabled = false
        binding.googleDriveBackupButton.text = "Uploading..."
        
        CoroutineScope(Dispatchers.Main).launch {
            android.util.Log.d("SettingsActivity", "Starting upload...")
            val result = googleDriveManager.uploadBackup(databaseFile, databaseName)
            
            android.util.Log.d("SettingsActivity", "Upload result: ${result.isSuccess}")
            if (result.isFailure) {
                android.util.Log.e("SettingsActivity", "Upload error", result.exceptionOrNull())
            }
            
            // Reopen database
            JournalDatabase.getInstance(applicationContext)
            
            if (result.isSuccess) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Backup uploaded to Google Drive",
                    Toast.LENGTH_SHORT
                ).show()
                updateGoogleDriveUI()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e("SettingsActivity", "Backup failed: $error")
                Toast.makeText(
                    this@SettingsActivity,
                    "Backup failed: $error",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            binding.googleDriveBackupButton.isEnabled = true
            binding.googleDriveBackupButton.text = "Backup"
        }
    }
    
    private fun showGoogleDriveRestoreDialog() {
        binding.googleDriveRestoreButton.isEnabled = false
        binding.googleDriveRestoreButton.text = "Loading..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val result = googleDriveManager.listBackups()
            
            if (result.isSuccess) {
                val backups = result.getOrNull() ?: emptyList()
                
                if (backups.isEmpty()) {
                    Toast.makeText(this@SettingsActivity, "No backups found in Google Drive", Toast.LENGTH_SHORT).show()
                    binding.googleDriveRestoreButton.isEnabled = true
                    binding.googleDriveRestoreButton.text = "Restore"
                    return@launch
                }
                
                val backupNames = backups.map { "${it.name} (${it.getFormattedDate()})" }.toTypedArray()
                
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Select Backup to Restore")
                    .setItems(backupNames) { _, which ->
                        val selectedBackup = backups[which]
                        confirmAndRestoreFromGoogleDrive(selectedBackup)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.googleDriveRestoreButton.isEnabled = true
                        binding.googleDriveRestoreButton.text = "Restore"
                    }
                    .setOnCancelListener {
                        binding.googleDriveRestoreButton.isEnabled = true
                        binding.googleDriveRestoreButton.text = "Restore"
                    }
                    .show()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Toast.makeText(
                    this@SettingsActivity,
                    "Failed to list backups: $error",
                    Toast.LENGTH_LONG
                ).show()
                binding.googleDriveRestoreButton.isEnabled = true
                binding.googleDriveRestoreButton.text = "Restore"
            }
        }
    }
    
    private fun confirmAndRestoreFromGoogleDrive(backup: com.digitalgram.android.data.BackupFileInfo) {
        AlertDialog.Builder(this)
            .setTitle("Restore from Google Drive?")
            .setMessage("This will merge entries from \"${backup.name}\" with your current database. Newer entries will be kept in case of conflicts.\\n\\nSize: ${backup.getFormattedSize()}\\nDate: ${backup.getFormattedDate()}")
            .setPositiveButton("Restore & Merge") { _, _ ->
                restoreFromGoogleDrive(backup)
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.googleDriveRestoreButton.isEnabled = true
                binding.googleDriveRestoreButton.text = "Restore"
            }
            .show()
    }
    
    private fun restoreFromGoogleDrive(backup: com.digitalgram.android.data.BackupFileInfo) {
        binding.googleDriveRestoreButton.text = "Downloading..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val tempFile = File(cacheDir, "googledrive_restore_temp.sqlite")
            
            try {
                val downloadResult = googleDriveManager.downloadBackup(backup.path, tempFile)
                
                if (downloadResult.isFailure) {
                    throw downloadResult.exceptionOrNull() ?: Exception("Download failed")
                }
                
                // Close current database
                val database = JournalDatabase.getInstance(applicationContext)
                database.close()
                
                // Import and merge
                withContext(Dispatchers.IO) {
                    val db = JournalDatabase.getInstance(applicationContext)
                    db.importAndMergeDatabase(tempFile)
                }
                
                // Cleanup
                tempFile.delete()
                
                Toast.makeText(
                    this@SettingsActivity,
                    "Restore completed successfully",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Refresh UI
                recreate()
                
            } catch (e: Exception) {
                tempFile.delete()
                Toast.makeText(
                    this@SettingsActivity,
                    "Restore failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                
                binding.googleDriveRestoreButton.isEnabled = true
                binding.googleDriveRestoreButton.text = "Restore"
            }
        }
    }
    
    private fun showSHA1Instructions() {
        // Get SHA-1 for debug keystore
        val sha1 = try {
            val process = Runtime.getRuntime().exec("keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            val sha1Line = output.lines().find { it.trim().startsWith("SHA1:") }
            sha1Line?.substringAfter("SHA1:")?.trim() ?: "Could not extract SHA-1"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
        
        AlertDialog.Builder(this)
            .setTitle("Get Your SHA-1 Fingerprint")
            .setMessage("For Debug Build:\n\nRun this command in terminal:\n\nkeytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android\n\nThen copy the SHA1 value to Google Cloud Console > Credentials > OAuth 2.0 Client IDs")
            .setPositiveButton("Copy Command") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("SHA-1 Command", "keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    companion object {
        private const val TAG = "Settings"
        const val REQUEST_PASSCODE = 1001
        const val REQUEST_NOTIFICATION_PERMISSION = 1002
        const val NOTIFICATION_CHANNEL_ID = "digitalgram_reminders"
    }
}

enum class PasscodeMode {
    SET, VERIFY, CHANGE
}
