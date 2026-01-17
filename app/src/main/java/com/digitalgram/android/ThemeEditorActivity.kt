package com.digitalgram.android

import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.databinding.ActivityThemeEditorBinding
import com.digitalgram.android.util.ThemeColors

/**
 * Activity for editing any theme's colors (not just Custom theme)
 * Allows users to override default theme colors and reset them
 */
class ThemeEditorActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_THEME_NAME = "theme_name"
    }
    
    private lateinit var binding: ActivityThemeEditorBinding
    private lateinit var settings: AppSettings
    private lateinit var themeName: String
    private lateinit var defaultTheme: ThemeColors
    
    // Track current colors (may include custom overrides)
    private var bgColor: Int = 0
    private var textColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var linkColor: Int = 0
    private var accentColor: Int = 0
    private var codeBackgroundColor: Int = 0
    private var borderColor: Int = 0
    private var buttonColor: Int = 0
    private var dotColor: Int = 0
    private var todayDotColor: Int = 0
    private var dateBackgroundColor: Int = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settings = AppSettings.getInstance(this)
        themeName = intent.getStringExtra(EXTRA_THEME_NAME) ?: settings.theme
        
        // Get default (non-customized) theme colors
        defaultTheme = ThemeColors.getDefaultTheme(themeName)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit ${getThemeDisplayName(themeName)}"
        
        binding.themeTitle.text = "Editing: ${getThemeDisplayName(themeName)}"
        
        loadCurrentColors()
        updateAllPreviews()
        setupColorButtons()
        setupButtons()
    }
    
    private fun getThemeDisplayName(theme: String): String {
        return when (theme) {
            AppSettings.THEME_LIGHT -> "Light"
            AppSettings.THEME_DARK -> "Dark"
            AppSettings.THEME_SEPIA -> "Sepia"
            AppSettings.THEME_MINT -> "Mint"
            AppSettings.THEME_LAVENDER -> "Lavender"
            AppSettings.THEME_PEACH -> "Peach"
            AppSettings.THEME_SKY -> "Sky"
            AppSettings.THEME_NORD -> "Nord"
            AppSettings.THEME_GRUVBOX -> "Gruvbox"
            AppSettings.THEME_SOLARIZED_DARK -> "Solarized Dark"
            AppSettings.THEME_ONE_DARK -> "One Dark"
            AppSettings.THEME_DRACULA -> "Dracula"
            AppSettings.THEME_AMOLED -> "AMOLED"
            AppSettings.THEME_CUSTOM -> "Custom"
            else -> theme
        }
    }
    
    private fun loadCurrentColors() {
        // Load colors, using custom overrides if they exist, otherwise defaults
        bgColor = settings.getThemeCustomColor(themeName, "bg") ?: defaultTheme.backgroundColor
        textColor = settings.getThemeCustomColor(themeName, "text") ?: defaultTheme.textColor
        secondaryTextColor = settings.getThemeCustomColor(themeName, "secondaryText") ?: defaultTheme.secondaryTextColor
        linkColor = settings.getThemeCustomColor(themeName, "link") ?: defaultTheme.linkColor
        accentColor = settings.getThemeCustomColor(themeName, "accent") ?: defaultTheme.accentColor
        codeBackgroundColor = settings.getThemeCustomColor(themeName, "codeBg") ?: defaultTheme.codeBackgroundColor
        borderColor = settings.getThemeCustomColor(themeName, "border") ?: defaultTheme.borderColor
        buttonColor = settings.getThemeCustomColor(themeName, "button") ?: defaultTheme.buttonColor
        dotColor = settings.getThemeCustomColor(themeName, "dot") ?: defaultTheme.dotColor
        todayDotColor = settings.getThemeCustomColor(themeName, "todayDot") ?: defaultTheme.todayDotColor
        dateBackgroundColor = settings.getThemeCustomColor(themeName, "dateBg") ?: defaultTheme.dateBackgroundColor
    }
    
    private fun updateAllPreviews() {
        binding.bgColorPreview.setBackgroundColor(bgColor)
        binding.bgColorValue.text = colorToHex(bgColor)
        
        binding.textColorPreview.setBackgroundColor(textColor)
        binding.textColorValue.text = colorToHex(textColor)
        
        binding.secondaryTextColorPreview.setBackgroundColor(secondaryTextColor)
        binding.secondaryTextColorValue.text = colorToHex(secondaryTextColor)
        
        binding.linkColorPreview.setBackgroundColor(linkColor)
        binding.linkColorValue.text = colorToHex(linkColor)
        
        binding.accentColorPreview.setBackgroundColor(accentColor)
        binding.accentColorValue.text = colorToHex(accentColor)
        
        binding.codeBackgroundColorPreview.setBackgroundColor(codeBackgroundColor)
        binding.codeBackgroundColorValue.text = colorToHex(codeBackgroundColor)
        
        binding.borderColorPreview.setBackgroundColor(borderColor)
        binding.borderColorValue.text = colorToHex(borderColor)
        
        binding.buttonColorPreview.setBackgroundColor(buttonColor)
        binding.buttonColorValue.text = colorToHex(buttonColor)
        
        binding.dotColorPreview.setBackgroundColor(dotColor)
        binding.dotColorValue.text = colorToHex(dotColor)
        
        binding.todayDotColorPreview.setBackgroundColor(todayDotColor)
        binding.todayDotColorValue.text = colorToHex(todayDotColor)
        
        binding.dateBackgroundColorPreview.setBackgroundColor(dateBackgroundColor)
        binding.dateBackgroundColorValue.text = colorToHex(dateBackgroundColor)
    }
    
    private fun setupColorButtons() {
        binding.bgColorRow.setOnClickListener {
            showColorPickerDialog("Background Color", bgColor) { color ->
                bgColor = color
                settings.setThemeCustomColor(themeName, "bg", color)
                binding.bgColorPreview.setBackgroundColor(color)
                binding.bgColorValue.text = colorToHex(color)
            }
        }
        
        binding.textColorRow.setOnClickListener {
            showColorPickerDialog("Text Color", textColor) { color ->
                textColor = color
                settings.setThemeCustomColor(themeName, "text", color)
                binding.textColorPreview.setBackgroundColor(color)
                binding.textColorValue.text = colorToHex(color)
            }
        }
        
        binding.secondaryTextColorRow.setOnClickListener {
            showColorPickerDialog("Secondary Text Color", secondaryTextColor) { color ->
                secondaryTextColor = color
                settings.setThemeCustomColor(themeName, "secondaryText", color)
                binding.secondaryTextColorPreview.setBackgroundColor(color)
                binding.secondaryTextColorValue.text = colorToHex(color)
            }
        }
        
        binding.linkColorRow.setOnClickListener {
            showColorPickerDialog("Link Color", linkColor) { color ->
                linkColor = color
                settings.setThemeCustomColor(themeName, "link", color)
                binding.linkColorPreview.setBackgroundColor(color)
                binding.linkColorValue.text = colorToHex(color)
            }
        }
        
        binding.accentColorRow.setOnClickListener {
            showColorPickerDialog("Accent Color", accentColor) { color ->
                accentColor = color
                settings.setThemeCustomColor(themeName, "accent", color)
                binding.accentColorPreview.setBackgroundColor(color)
                binding.accentColorValue.text = colorToHex(color)
            }
        }
        
        binding.codeBackgroundColorRow.setOnClickListener {
            showColorPickerDialog("Code Background Color", codeBackgroundColor) { color ->
                codeBackgroundColor = color
                settings.setThemeCustomColor(themeName, "codeBg", color)
                binding.codeBackgroundColorPreview.setBackgroundColor(color)
                binding.codeBackgroundColorValue.text = colorToHex(color)
            }
        }
        
        binding.borderColorRow.setOnClickListener {
            showColorPickerDialog("Border Color", borderColor) { color ->
                borderColor = color
                settings.setThemeCustomColor(themeName, "border", color)
                binding.borderColorPreview.setBackgroundColor(color)
                binding.borderColorValue.text = colorToHex(color)
            }
        }
        
        binding.buttonColorRow.setOnClickListener {
            showColorPickerDialog("Button Color", buttonColor) { color ->
                buttonColor = color
                settings.setThemeCustomColor(themeName, "button", color)
                binding.buttonColorPreview.setBackgroundColor(color)
                binding.buttonColorValue.text = colorToHex(color)
            }
        }
        
        binding.dotColorRow.setOnClickListener {
            showColorPickerDialog("Dot Color", dotColor) { color ->
                dotColor = color
                settings.setThemeCustomColor(themeName, "dot", color)
                binding.dotColorPreview.setBackgroundColor(color)
                binding.dotColorValue.text = colorToHex(color)
            }
        }
        
        binding.todayDotColorRow.setOnClickListener {
            showColorPickerDialog("Today Dot Color", todayDotColor) { color ->
                todayDotColor = color
                settings.setThemeCustomColor(themeName, "todayDot", color)
                binding.todayDotColorPreview.setBackgroundColor(color)
                binding.todayDotColorValue.text = colorToHex(color)
            }
        }
        
        binding.dateBackgroundColorRow.setOnClickListener {
            showColorPickerDialog("Date Background Color", dateBackgroundColor) { color ->
                dateBackgroundColor = color
                settings.setThemeCustomColor(themeName, "dateBg", color)
                binding.dateBackgroundColorPreview.setBackgroundColor(color)
                binding.dateBackgroundColorValue.text = colorToHex(color)
            }
        }
    }
    
    private fun setupButtons() {
        binding.resetButton.setOnClickListener {
            if (settings.hasThemeCustomColors(themeName)) {
                AlertDialog.Builder(this)
                    .setTitle("Reset Theme")
                    .setMessage("Reset all colors to default values?")
                    .setPositiveButton("Reset") { _, _ ->
                        settings.resetThemeCustomColors(themeName)
                        loadCurrentColors()
                        updateAllPreviews()
                        Toast.makeText(this, "Theme reset to defaults", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(this, "No customizations to reset", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.saveButton.setOnClickListener {
            Toast.makeText(this, "Theme colors saved!", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }
    
    private fun showColorPickerDialog(title: String, currentColor: Int, onColorSelected: (Int) -> Unit) {
        val editText = EditText(this)
        editText.setText(colorToHex(currentColor))
        editText.filters = arrayOf(InputFilter.AllCaps(), object : InputFilter {
            override fun filter(
                source: CharSequence?,
                start: Int,
                end: Int,
                dest: Spanned?,
                dstart: Int,
                dend: Int
            ): CharSequence? {
                return source?.filter { it.isDigit() || it in 'A'..'F' || it == '#' }
            }
        }, InputFilter.LengthFilter(7))
        
        editText.hint = "#RRGGBB"
        editText.setPadding(50, 20, 50, 20)
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Enter hex color code (e.g. #FF6B6B)\n\nDefault: ${colorToHex(getDefaultColor(title))}")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val hexValue = editText.text.toString().trim()
                try {
                    val color = if (hexValue.startsWith("#")) {
                        Color.parseColor(hexValue)
                    } else {
                        Color.parseColor("#$hexValue")
                    }
                    onColorSelected(color)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(this, "Invalid color format. Use #RRGGBB", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Default") { _, _ ->
                val defaultColor = getDefaultColor(title)
                onColorSelected(defaultColor)
            }
            .show()
    }
    
    private fun getDefaultColor(colorName: String): Int {
        return when {
            colorName.contains("Background Color", ignoreCase = true) && !colorName.contains("Code") && !colorName.contains("Date") -> defaultTheme.backgroundColor
            colorName.contains("Text Color", ignoreCase = true) && !colorName.contains("Secondary") -> defaultTheme.textColor
            colorName.contains("Secondary Text", ignoreCase = true) -> defaultTheme.secondaryTextColor
            colorName.contains("Link", ignoreCase = true) -> defaultTheme.linkColor
            colorName.contains("Accent", ignoreCase = true) -> defaultTheme.accentColor
            colorName.contains("Code Background", ignoreCase = true) -> defaultTheme.codeBackgroundColor
            colorName.contains("Border", ignoreCase = true) -> defaultTheme.borderColor
            colorName.contains("Button", ignoreCase = true) -> defaultTheme.buttonColor
            colorName.contains("Today Dot", ignoreCase = true) -> defaultTheme.todayDotColor
            colorName.contains("Dot", ignoreCase = true) -> defaultTheme.dotColor
            colorName.contains("Date Background", ignoreCase = true) -> defaultTheme.dateBackgroundColor
            else -> Color.WHITE
        }
    }
    
    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
