package com.digitalgram.android

import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.databinding.ActivityCustomThemeBinding
import com.digitalgram.android.util.ThemeColors

class CustomThemeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCustomThemeBinding
    private lateinit var settings: AppSettings
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settings = AppSettings.getInstance(this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Custom Theme Colors"
        
        applyCurrentTheme()
        setupColorButtons()
        setupSaveButton()
    }
    
    private fun applyCurrentTheme() {
        val themeColors = ThemeColors.getTheme(settings.theme, this)
        
        binding.bgColorPreview.setBackgroundColor(settings.customBackgroundColor)
        binding.textColorPreview.setBackgroundColor(settings.customTextColor)
        binding.secondaryTextColorPreview.setBackgroundColor(settings.customSecondaryTextColor)
        binding.linkColorPreview.setBackgroundColor(settings.customLinkColor)
        binding.accentColorPreview.setBackgroundColor(settings.customAccentColor)
        binding.codeBackgroundColorPreview.setBackgroundColor(settings.customCodeBackgroundColor)
        binding.borderColorPreview.setBackgroundColor(settings.customBorderColor)
        binding.buttonColorPreview.setBackgroundColor(settings.customButtonColor)
        binding.dotColorPreview.setBackgroundColor(settings.customDotColor)
        binding.todayDotColorPreview.setBackgroundColor(settings.customTodayDotColor)
        binding.dateBackgroundColorPreview.setBackgroundColor(settings.customDateBackgroundColor)
        
        binding.bgColorValue.text = colorToHex(settings.customBackgroundColor)
        binding.textColorValue.text = colorToHex(settings.customTextColor)
        binding.secondaryTextColorValue.text = colorToHex(settings.customSecondaryTextColor)
        binding.linkColorValue.text = colorToHex(settings.customLinkColor)
        binding.accentColorValue.text = colorToHex(settings.customAccentColor)
        binding.codeBackgroundColorValue.text = colorToHex(settings.customCodeBackgroundColor)
        binding.borderColorValue.text = colorToHex(settings.customBorderColor)
        binding.buttonColorValue.text = colorToHex(settings.customButtonColor)
        binding.dotColorValue.text = colorToHex(settings.customDotColor)
        binding.todayDotColorValue.text = colorToHex(settings.customTodayDotColor)
        binding.dateBackgroundColorValue.text = colorToHex(settings.customDateBackgroundColor)
    }
    
    private fun setupColorButtons() {
        binding.bgColorRow.setOnClickListener {
            showColorPickerDialog("Background Color", settings.customBackgroundColor) { color ->
                settings.customBackgroundColor = color
                binding.bgColorPreview.setBackgroundColor(color)
                binding.bgColorValue.text = colorToHex(color)
            }
        }
        
        binding.textColorRow.setOnClickListener {
            showColorPickerDialog("Text Color", settings.customTextColor) { color ->
                settings.customTextColor = color
                binding.textColorPreview.setBackgroundColor(color)
                binding.textColorValue.text = colorToHex(color)
            }
        }
        
        binding.secondaryTextColorRow.setOnClickListener {
            showColorPickerDialog("Secondary Text Color", settings.customSecondaryTextColor) { color ->
                settings.customSecondaryTextColor = color
                binding.secondaryTextColorPreview.setBackgroundColor(color)
                binding.secondaryTextColorValue.text = colorToHex(color)
            }
        }
        
        binding.linkColorRow.setOnClickListener {
            showColorPickerDialog("Link Color", settings.customLinkColor) { color ->
                settings.customLinkColor = color
                binding.linkColorPreview.setBackgroundColor(color)
                binding.linkColorValue.text = colorToHex(color)
            }
        }
        
        binding.accentColorRow.setOnClickListener {
            showColorPickerDialog("Accent Color", settings.customAccentColor) { color ->
                settings.customAccentColor = color
                binding.accentColorPreview.setBackgroundColor(color)
                binding.accentColorValue.text = colorToHex(color)
            }
        }
        
        binding.codeBackgroundColorRow.setOnClickListener {
            showColorPickerDialog("Code Background Color", settings.customCodeBackgroundColor) { color ->
                settings.customCodeBackgroundColor = color
                binding.codeBackgroundColorPreview.setBackgroundColor(color)
                binding.codeBackgroundColorValue.text = colorToHex(color)
            }
        }
        
        binding.borderColorRow.setOnClickListener {
            showColorPickerDialog("Border Color", settings.customBorderColor) { color ->
                settings.customBorderColor = color
                binding.borderColorPreview.setBackgroundColor(color)
                binding.borderColorValue.text = colorToHex(color)
            }
        }
        
        binding.buttonColorRow.setOnClickListener {
            showColorPickerDialog("Button Color", settings.customButtonColor) { color ->
                settings.customButtonColor = color
                binding.buttonColorPreview.setBackgroundColor(color)
                binding.buttonColorValue.text = colorToHex(color)
            }
        }
        
        binding.dotColorRow.setOnClickListener {
            showColorPickerDialog("Dot Color", settings.customDotColor) { color ->
                settings.customDotColor = color
                binding.dotColorPreview.setBackgroundColor(color)
                binding.dotColorValue.text = colorToHex(color)
            }
        }
        
        binding.todayDotColorRow.setOnClickListener {
            showColorPickerDialog("Today Dot Color", settings.customTodayDotColor) { color ->
                settings.customTodayDotColor = color
                binding.todayDotColorPreview.setBackgroundColor(color)
                binding.todayDotColorValue.text = colorToHex(color)
            }
        }
        
        binding.dateBackgroundColorRow.setOnClickListener {
            showColorPickerDialog("Date Background Color", settings.customDateBackgroundColor) { color ->
                settings.customDateBackgroundColor = color
                binding.dateBackgroundColorPreview.setBackgroundColor(color)
                binding.dateBackgroundColorValue.text = colorToHex(color)
            }
        }
    }
    
    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            Toast.makeText(this, "Custom theme colors saved!", Toast.LENGTH_SHORT).show()
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
            .setMessage("Enter hex color code (e.g. #FF6B6B)")
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
            .show()
    }
    
    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
