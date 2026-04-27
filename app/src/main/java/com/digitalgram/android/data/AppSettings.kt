package com.digitalgram.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class LockState { Ok, KeystoreUnavailable }

enum class VerifyResult { Match, NoMatch, Locked, Migrated }

/**
 * App Settings manager using SharedPreferences
 * Handles all user preferences for the DigitalGram app
 *
 * Callers must check getLockState() == LockState.Ok before relying on any
 * securePrefs-backed value (passcode, tokens). When KeystoreUnavailable,
 * securePrefs is a no-op stub and all reads return defaults.
 */
class AppSettings private constructor(context: Context) {

    private var lockState: LockState = LockState.Ok

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences by lazy {
        createSecurePrefs(context)
    }

    fun getLockState(): LockState = lockState

    private fun createSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            try {
                context.deleteSharedPreferences(SECURE_PREFS_NAME)
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                lockState = LockState.KeystoreUnavailable
                NoOpSharedPreferences()
            }
        }
    }
    
    // Theme setting (DEFAULT, DARK, LIGHT, etc.)
    var theme: String
        get() = prefs.getString(KEY_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
        set(value) = prefs.edit { putString(KEY_THEME, value) }
    
    // Font size (1-5 scale)
    var fontSize: Int
        get() = prefs.getInt(KEY_FONT_SIZE, 3)
        set(value) = prefs.edit { putInt(KEY_FONT_SIZE, value.coerceIn(1, 5)) }
    
    // Use system font
    var useSystemFont: Boolean
        get() = prefs.getBoolean(KEY_USE_SYSTEM_FONT, true)
        set(value) = prefs.edit { putBoolean(KEY_USE_SYSTEM_FONT, value) }
    
    // Font family
    var fontFamily: String
        get() = prefs.getString(KEY_FONT_FAMILY, FONT_SERIF) ?: FONT_SERIF
        set(value) = prefs.edit { putString(KEY_FONT_FAMILY, value) }
    
    // Fullscreen mode
    var fullscreen: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN, true)
        set(value) = prefs.edit { putBoolean(KEY_FULLSCREEN, value) }
    
    // Timeline preview style (false = dots only, true = with preview text)
    var timelinePreview: Boolean
        get() = prefs.getBoolean(KEY_TIMELINE_PREVIEW, true)
        set(value) = prefs.edit { putBoolean(KEY_TIMELINE_PREVIEW, value) }
    
    // Border style (A, B, C, E)
    var borderStyle: String
        get() = prefs.getString(KEY_BORDER_STYLE, BORDER_A) ?: BORDER_A
        set(value) = prefs.edit { putString(KEY_BORDER_STYLE, value) }
    
    // Passcode enabled
    var passcodeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PASSCODE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_PASSCODE_ENABLED, value) }
    
    // Passcode (stored securely)
    var passcode: String
        get() = securePrefs.getString(KEY_PASSCODE, "") ?: ""
        set(value) = securePrefs.edit { putString(KEY_PASSCODE, value) }
    
    // Fingerprint/Biometric enabled
    var fingerprintEnabled: Boolean
        get() = prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_FINGERPRINT_ENABLED, value) }
    
    // Reminder enabled
    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_REMINDER_ENABLED, value) }
    
    // Reminder time (in minutes from midnight, e.g., 21:00 = 21*60 = 1260)
    var reminderTimeMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_TIME, 21 * 60) // Default 9:00 PM
        set(value) = prefs.edit { putInt(KEY_REMINDER_TIME, value) }
    
    // Current database name
    var currentDatabase: String
        get() = prefs.getString(KEY_CURRENT_DATABASE, DEFAULT_DATABASE) ?: DEFAULT_DATABASE
        set(value) = prefs.edit { putString(KEY_CURRENT_DATABASE, value) }
    
    // Custom storage path (null = use default app database folder)
    var customStoragePath: String?
        get() = prefs.getString(KEY_STORAGE_PATH, null)
        set(value) = prefs.edit { putString(KEY_STORAGE_PATH, value) }
    
    // Dropbox access token (stored securely)
    var dropboxAccessToken: String
        get() = securePrefs.getString(KEY_DROPBOX_TOKEN, "") ?: ""
        set(value) = securePrefs.edit { putString(KEY_DROPBOX_TOKEN, value) }
    
    // Check if connected to Dropbox
    fun isDropboxConnected(): Boolean {
        return dropboxAccessToken.isNotEmpty()
    }
    
    // Google Drive account email (stored securely)
    var googleDriveAccountEmail: String
        get() = securePrefs.getString(KEY_GOOGLE_DRIVE_EMAIL, "") ?: ""
        set(value) = securePrefs.edit { putString(KEY_GOOGLE_DRIVE_EMAIL, value) }
    
    // Check if connected to Google Drive
    fun isGoogleDriveConnected(): Boolean {
        return googleDriveAccountEmail.isNotEmpty()
    }
    
    // Wallpaper URI for background image
    var wallpaperUri: String?
        get() = prefs.getString(KEY_WALLPAPER_URI, null)
        set(value) = prefs.edit { putString(KEY_WALLPAPER_URI, value) }
    
    // Theme customizations - stored per theme
    fun getThemeCustomColor(themeName: String, colorKey: String): Int? {
        val key = "theme_custom_${themeName}_$colorKey"
        return if (prefs.contains(key)) prefs.getInt(key, 0) else null
    }
    
    fun setThemeCustomColor(themeName: String, colorKey: String, color: Int) {
        prefs.edit { putInt("theme_custom_${themeName}_$colorKey", color) }
    }
    
    fun resetThemeCustomColors(themeName: String) {
        val colorKeys = listOf("bg", "text", "secondaryText", "link", "accent", "codeBg", "border", "button", "dot", "todayDot", "dateBg")
        prefs.edit {
            colorKeys.forEach { key ->
                remove("theme_custom_${themeName}_$key")
            }
        }
    }
    
    fun hasThemeCustomColors(themeName: String): Boolean {
        val colorKeys = listOf("bg", "text", "secondaryText", "link", "accent", "codeBg", "border", "button", "dot", "todayDot", "dateBg")
        return colorKeys.any { key ->
            prefs.contains("theme_custom_${themeName}_$key")
        }
    }
    
    // Custom theme colors
    var customBackgroundColor: Int
        get() = prefs.getInt(KEY_CUSTOM_BG, android.graphics.Color.WHITE)
        set(value) = prefs.edit { putInt(KEY_CUSTOM_BG, value) }
    
    var customTextColor: Int
        get() = prefs.getInt(KEY_CUSTOM_TEXT, android.graphics.Color.parseColor("#1A1A1A"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_TEXT, value) }
    
    var customSecondaryTextColor: Int
        get() = prefs.getInt(KEY_CUSTOM_SECONDARY_TEXT, android.graphics.Color.parseColor("#757575"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_SECONDARY_TEXT, value) }
    
    var customLinkColor: Int
        get() = prefs.getInt(KEY_CUSTOM_LINK, android.graphics.Color.parseColor("#2196F3"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_LINK, value) }
    
    var customAccentColor: Int
        get() = prefs.getInt(KEY_CUSTOM_ACCENT, android.graphics.Color.parseColor("#FF6B6B"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_ACCENT, value) }
    
    var customCodeBackgroundColor: Int
        get() = prefs.getInt(KEY_CUSTOM_CODE_BG, android.graphics.Color.parseColor("#F5F5F5"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_CODE_BG, value) }
    
    var customBorderColor: Int
        get() = prefs.getInt(KEY_CUSTOM_BORDER, android.graphics.Color.parseColor("#E0E0E0"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_BORDER, value) }
    
    var customButtonColor: Int
        get() = prefs.getInt(KEY_CUSTOM_BUTTON, android.graphics.Color.parseColor("#FF6B6B"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_BUTTON, value) }
    
    var customDotColor: Int
        get() = prefs.getInt(KEY_CUSTOM_DOT, android.graphics.Color.parseColor("#BDBDBD"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_DOT, value) }
    
    var customTodayDotColor: Int
        get() = prefs.getInt(KEY_CUSTOM_TODAY_DOT, android.graphics.Color.parseColor("#FF6B6B"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_TODAY_DOT, value) }
    
    var customDateBackgroundColor: Int
        get() = prefs.getInt(KEY_CUSTOM_DATE_BG, android.graphics.Color.parseColor("#F0F0F0"))
        set(value) = prefs.edit { putInt(KEY_CUSTOM_DATE_BG, value) }
    
    // Get reminder time as formatted string
    fun getReminderTimeFormatted(): String {
        val hours = reminderTimeMinutes / 60
        val minutes = reminderTimeMinutes % 60
        val amPm = if (hours >= 12) "PM" else "AM"
        val displayHours = when {
            hours == 0 -> 12
            hours > 12 -> hours - 12
            else -> hours
        }
        return String.format("%02d:%02d %s", displayHours, minutes, amPm)
    }
    
    // Get font size in SP
    fun getFontSizeSp(): Float {
        return when (fontSize) {
            1 -> 12f
            2 -> 14f
            3 -> 16f
            4 -> 18f
            5 -> 20f
            else -> 16f
        }
    }
    
    // Verify passcode — routes through PBKDF2 migration path; backward-compat Boolean wrapper.
    fun verifyPasscode(input: String): Boolean {
        return when (verifyPasscodeAndMigrate(input)) {
            VerifyResult.Match, VerifyResult.Migrated -> true
            VerifyResult.NoMatch, VerifyResult.Locked -> false
        }
    }

    /**
     * Verify the passcode and transparently migrate plaintext storage to PBKDF2 on first match.
     * Returns Locked when the keystore is unavailable.
     */
    fun verifyPasscodeAndMigrate(input: String): VerifyResult {
        if (lockState == LockState.KeystoreUnavailable) return VerifyResult.Locked

        if (prefs.getBoolean(KEY_PASSCODE_MIGRATED, false)) {
            val saltB64 = prefs.getString(KEY_PASSCODE_SALT, null) ?: return VerifyResult.NoMatch
            val hashB64 = securePrefs.getString(KEY_PASSCODE_HASH, null) ?: return VerifyResult.NoMatch
            val stored = Base64.getDecoder().decode(hashB64)
            val computed = pbkdf2Hash(input, Base64.getDecoder().decode(saltB64))
            return if (MessageDigest.isEqual(stored, computed)) VerifyResult.Match else VerifyResult.NoMatch
        }

        val oldPlaintext = securePrefs.getString(KEY_PASSCODE, "") ?: ""
        if (oldPlaintext.isEmpty()) return VerifyResult.NoMatch
        if (oldPlaintext != input) return VerifyResult.NoMatch

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2Hash(input, salt)
        prefs.edit { putString(KEY_PASSCODE_SALT, Base64.getEncoder().encodeToString(salt)) }
        securePrefs.edit { putString(KEY_PASSCODE_HASH, Base64.getEncoder().encodeToString(hash)) }
        prefs.edit { putBoolean(KEY_PASSCODE_MIGRATED, true) }
        securePrefs.edit { putString(KEY_PASSCODE, "") }
        return VerifyResult.Migrated
    }

    /**
     * Set a new passcode using PBKDF2. Generates a fresh salt, stores hash in securePrefs,
     * salt in plain prefs, and clears any legacy plaintext slot.
     */
    fun updatePasscode(newPasscode: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2Hash(newPasscode, salt)
        prefs.edit { putString(KEY_PASSCODE_SALT, Base64.getEncoder().encodeToString(salt)) }
        securePrefs.edit { putString(KEY_PASSCODE_HASH, Base64.getEncoder().encodeToString(hash)) }
        prefs.edit { putBoolean(KEY_PASSCODE_MIGRATED, true) }
        securePrefs.edit { putString(KEY_PASSCODE, "") }
    }

    private fun pbkdf2Hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /**
     * Get (or generate) the SQLCipher database passphrase. The 32-byte random key is
     * stored Base64-encoded in EncryptedSharedPreferences, so it never lives on disk
     * unencrypted. If the keystore is unavailable the caller cannot proceed safely —
     * we throw rather than silently degrade to a plaintext or weak-key database.
     */
    fun getDbPassphrase(): ByteArray {
        if (lockState == LockState.KeystoreUnavailable) {
            throw IllegalStateException("Keystore unavailable; cannot derive DB passphrase")
        }
        val existing = securePrefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) return Base64.getDecoder().decode(existing)
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        securePrefs.edit { putString(KEY_DB_PASSPHRASE, Base64.getEncoder().encodeToString(key)) }
        return key
    }

    fun isDbMigratedToSqlcipher(): Boolean = prefs.getBoolean(KEY_DB_MIGRATED_SQLCIPHER, false)

    fun markDbMigratedToSqlcipher() {
        prefs.edit { putBoolean(KEY_DB_MIGRATED_SQLCIPHER, true) }
    }
    
    // Reset all settings to default
    fun resetToDefaults() {
        prefs.edit {
            putString(KEY_THEME, THEME_DEFAULT)
            putInt(KEY_FONT_SIZE, 3)
            putBoolean(KEY_USE_SYSTEM_FONT, true)
            putBoolean(KEY_FULLSCREEN, true)
            putBoolean(KEY_TIMELINE_PREVIEW, true)
            putString(KEY_BORDER_STYLE, BORDER_A)
            putBoolean(KEY_PASSCODE_ENABLED, false)
            putBoolean(KEY_FINGERPRINT_ENABLED, false)
            putBoolean(KEY_REMINDER_ENABLED, false)
            putInt(KEY_REMINDER_TIME, 21 * 60)
        }
        securePrefs.edit {
            putString(KEY_PASSCODE, "")
        }
    }
    
    companion object {
        private const val PREFS_NAME = "digitalgram_settings"
        private const val SECURE_PREFS_NAME = "digitalgram_secure_settings"
        
        // Keys
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_USE_SYSTEM_FONT = "use_system_font"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_FULLSCREEN = "fullscreen"
        private const val KEY_TIMELINE_PREVIEW = "timeline_preview"
        private const val KEY_BORDER_STYLE = "border_style"
        private const val KEY_PASSCODE_ENABLED = "passcode_enabled"
        private const val KEY_PASSCODE = "passcode"
        private const val KEY_PASSCODE_SALT = "passcode_salt"
        private const val KEY_PASSCODE_HASH = "passcode_hash_v2"
        private const val KEY_PASSCODE_MIGRATED = "passcode_migrated_v2"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_TIME = "reminder_time"
        private const val KEY_CURRENT_DATABASE = "current_database"
        private const val KEY_STORAGE_PATH = "storage_path"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_DROPBOX_TOKEN = "dropbox_access_token"
        private const val KEY_GOOGLE_DRIVE_EMAIL = "google_drive_account_email"
        private const val KEY_CUSTOM_BG = "custom_bg_color"
        private const val KEY_CUSTOM_TEXT = "custom_text_color"
        private const val KEY_CUSTOM_SECONDARY_TEXT = "custom_secondary_text_color"
        private const val KEY_CUSTOM_LINK = "custom_link_color"
        private const val KEY_CUSTOM_ACCENT = "custom_accent_color"
        private const val KEY_CUSTOM_CODE_BG = "custom_code_bg_color"
        private const val KEY_CUSTOM_BORDER = "custom_border_color"
        private const val KEY_CUSTOM_BUTTON = "custom_button_color"
        private const val KEY_CUSTOM_DOT = "custom_dot_color"
        private const val KEY_CUSTOM_TODAY_DOT = "custom_today_dot_color"
        private const val KEY_CUSTOM_DATE_BG = "custom_date_bg_color"
        private const val KEY_DB_PASSPHRASE = "db_passphrase_v1"
        private const val KEY_DB_MIGRATED_SQLCIPHER = "db_migrated_sqlcipher"
        
        // Theme values
        const val THEME_DEFAULT = "LIGHT"
        const val THEME_DARK = "DARK"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_CUSTOM = "CUSTOM"
        
        // Light Themes
        const val THEME_SEPIA = "SEPIA"
        const val THEME_MINT = "MINT"
        const val THEME_LAVENDER = "LAVENDER"
        const val THEME_PEACH = "PEACH"
        const val THEME_SKY = "SKY"
        
        // Dark Themes
        const val THEME_NORD = "NORD"
        const val THEME_GRUVBOX = "GRUVBOX"
        const val THEME_SOLARIZED_DARK = "SOLARIZED_DARK"
        const val THEME_ONE_DARK = "ONE_DARK"
        const val THEME_DRACULA = "DRACULA"
        const val THEME_AMOLED = "AMOLED"
        
        // Additional Themes
        const val THEME_ROSE_GOLD = "ROSE_GOLD"
        const val THEME_CHERRY_BLOSSOM = "CHERRY_BLOSSOM"
        const val THEME_OCEAN_BREEZE = "OCEAN_BREEZE"
        const val THEME_SUNSET = "SUNSET"
        const val THEME_FOREST = "FOREST"
        const val THEME_MIDNIGHT_PURPLE = "MIDNIGHT_PURPLE"
        
        val ALL_THEMES = listOf(
            THEME_LIGHT,
            THEME_SEPIA,
            THEME_MINT,
            THEME_LAVENDER,
            THEME_PEACH,
            THEME_SKY,
            THEME_DARK,
            THEME_NORD,
            THEME_GRUVBOX,
            THEME_SOLARIZED_DARK,
            THEME_ONE_DARK,
            THEME_DRACULA,
            THEME_AMOLED,
            THEME_ROSE_GOLD,
            THEME_CHERRY_BLOSSOM,
            THEME_OCEAN_BREEZE,
            THEME_SUNSET,
            THEME_FOREST,
            THEME_MIDNIGHT_PURPLE,
            THEME_CUSTOM
        )
        
        // Database
        private const val DEFAULT_DATABASE = "daygram.sqlite"
        
        // Font Families (Kindle-style)
        const val FONT_SERIF = "serif" // Georgia-like
        const val FONT_SANS = "sans-serif" // Clean modern
        const val FONT_MONO = "monospace" // Typewriter
        const val FONT_CONDENSED = "sans-serif-condensed" // Compact
        const val FONT_BOOKERLY = "serif" // Bookerly style (serif)
        const val FONT_CURSIVE = "cursive" // Handwriting/Script style
        const val FONT_CASUAL = "casual" // Casual handwriting
        const val FONT_LIGHT = "sans-serif-light" // Light elegant
        const val FONT_THIN = "sans-serif-thin" // Ultra light
        const val FONT_SMALLCAPS = "sans-serif-smallcaps" // Small caps
        
        // Stylish fonts using system typefaces
        const val FONT_SERIF_BOLD = "serif-bold" // Bold serif
        const val FONT_SERIF_ITALIC = "serif-italic" // Italic serif
        const val FONT_SANS_BOLD = "sans-serif-bold" // Bold sans
        const val FONT_MONO_BOLD = "monospace-bold" // Bold mono
        const val FONT_CURSIVE_BOLD = "cursive-bold" // Bold cursive
        
        val ALL_FONTS = listOf(
            FONT_BOOKERLY to "Bookerly",
            FONT_SERIF to "Georgia",
            FONT_SANS to "Helvetica",
            FONT_LIGHT to "Elegant Light",
            FONT_THIN to "Ultra Thin",
            FONT_CONDENSED to "Compact",
            FONT_SMALLCAPS to "Small Caps",
            FONT_MONO to "Courier",
            FONT_CURSIVE to "Cursive",
            FONT_CASUAL to "Casual",
            FONT_SERIF_BOLD to "Bold Serif",
            FONT_SERIF_ITALIC to "Italic Serif",
            FONT_SANS_BOLD to "Bold Sans",
            FONT_MONO_BOLD to "Bold Mono",
            FONT_CURSIVE_BOLD to "Fancy Cursive"
        )
        
        // Border styles
        const val BORDER_A = "A"
        const val BORDER_B = "B"
        const val BORDER_C = "C"
        const val BORDER_E = "E"
        
        @Volatile
        private var INSTANCE: AppSettings? = null
        
        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}

private class NoOpSharedPreferences : SharedPreferences {
    private val noOpEditor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = this
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = this
        override fun clear() = this
        override fun commit() = true
        override fun apply() {}
    }
    override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    override fun getString(key: String, defValue: String?) = defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = defValue
    override fun getLong(key: String, defValue: Long) = defValue
    override fun getFloat(key: String, defValue: Float) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = defValue
    override fun contains(key: String) = false
    override fun edit() = noOpEditor
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}
