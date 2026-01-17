package com.digitalgram.android

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.data.JournalDatabase
import com.digitalgram.android.data.JournalEntry
import com.digitalgram.android.databinding.ActivityEditorBinding
import com.digitalgram.android.util.MarkdownParser
import com.digitalgram.android.util.ThemeColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EditorActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEditorBinding
    private lateinit var database: JournalDatabase
    private lateinit var settings: AppSettings
    private lateinit var themeColors: ThemeColors
    private lateinit var gestureDetector: GestureDetector
    
    private var currentEntry: JournalEntry? = null
    private var selectedDate: Calendar = Calendar.getInstance()
    private var isNewEntry = true
    private var isPreviewMode = false
    
    companion object {
        const val EXTRA_DATE_KEY = "extra_date_key"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settings = AppSettings.getInstance(this)
        themeColors = ThemeColors.getTheme(settings.theme, this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit Entry"
        
        try {
            database = JournalDatabase.getInstance(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error initializing database", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        applyThemeColors()
        loadEntry()
        setupDateDisplay()
        setupDoneButton()
        setupMarkdownToolbar()
        setupPreviewToggle()
        setupAutoSave()
    }
    
    private fun setupAutoSave() {
        binding.contentEditText.addTextChangedListener(object : android.text.TextWatcher {
            private var saveRunnable: Runnable? = null
            private val handler = Handler(Looper.getMainLooper())
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                // Cancel any pending save
                saveRunnable?.let { handler.removeCallbacks(it) }
                
                // Schedule a new save after 2 seconds of no typing
                saveRunnable = Runnable {
                    autoSaveEntry()
                }
                handler.postDelayed(saveRunnable!!, 2000)
            }
        })
    }
    
    private fun autoSaveEntry() {
        val content = binding.contentEditText.text.toString().trim()
        
        if (content.isEmpty()) return
        
        lifecycleScope.launch {
            val entry = if (currentEntry != null) {
                with(JournalEntry.Companion) {
                    currentEntry!!.withUpdatedContent(content)
                }
            } else {
                JournalEntry.create(selectedDate, content)
            }
            
            database.saveEntry(entry)
            currentEntry = entry
            isNewEntry = false
        }
    }
    
    private fun applyThemeColors() {
        themeColors = ThemeColors.getTheme(settings.theme, this)
        val isDark = ThemeColors.isDarkTheme(settings.theme)
        val fontSizeSp = settings.getFontSizeSp()
        
        // Apply wallpaper if set
        val wallpaperUri = settings.wallpaperUri
        if (wallpaperUri != null) {
            try {
                val uri = android.net.Uri.parse(wallpaperUri)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                binding.rootLayout.background = drawable
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to theme background color if wallpaper fails
                binding.rootLayout.setBackgroundColor(themeColors.backgroundColor)
            }
        } else {
            // No wallpaper, use theme background color
            binding.rootLayout.setBackgroundColor(themeColors.backgroundColor)
        }
        
        // Apply background colors - make transparent if wallpaper is set
        if (wallpaperUri != null) {
            // Set 50% transparent background (50% opacity) for bars
            val semiTransparentBg = android.graphics.Color.argb(128, 0, 0, 0)
            binding.appBarLayout.setBackgroundColor(semiTransparentBg)
            binding.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.contentContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.previewScroll.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // Make editor and preview semi-transparent for legibility (85% opacity)
            binding.contentEditText.alpha = 0.85f
            binding.contentEditText.setBackgroundColor(themeColors.backgroundColor)
            binding.previewText.alpha = 0.85f
            binding.previewText.setBackgroundColor(themeColors.backgroundColor)
        } else {
            binding.appBarLayout.setBackgroundColor(themeColors.backgroundColor)
            binding.toolbar.setBackgroundColor(themeColors.backgroundColor)
            binding.contentContainer.setBackgroundColor(themeColors.backgroundColor)
            binding.contentEditText.alpha = 1.0f
            binding.contentEditText.setBackgroundColor(themeColors.backgroundColor)
            binding.previewText.alpha = 1.0f
            binding.previewText.setBackgroundColor(themeColors.backgroundColor)
            binding.previewScroll.setBackgroundColor(themeColors.backgroundColor)
        }
        
        // Apply background to the content frame (has border_card drawable)
        val contentFrameDrawable = binding.contentFrame.background
        if (contentFrameDrawable is android.graphics.drawable.GradientDrawable) {
            contentFrameDrawable.setColor(themeColors.backgroundColor)
            contentFrameDrawable.setStroke(2, themeColors.borderColor)
        }
        
        // Apply toolbar title color
        binding.toolbar.setTitleTextColor(themeColors.textColor)
        
        // Apply text colors
        binding.contentEditText.setTextColor(themeColors.textColor)
        binding.contentEditText.setHintTextColor(themeColors.secondaryTextColor)
        binding.previewText.setTextColor(themeColors.textColor)
        binding.monthYear.setTextColor(themeColors.textColor)
        binding.dayNumber.setTextColor(themeColors.textColor)
        binding.previewToggle.setTextColor(themeColors.linkColor)
        binding.dayName.setTextColor(themeColors.accentColor)
        
        // Apply to done button
        binding.doneButton.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColors.buttonColor)
        
        // Apply date background color to the date box
        val dateBox = binding.dateContainer.getChildAt(0) as? android.view.ViewGroup
        dateBox?.let {
            val drawable = it.background
            if (drawable is android.graphics.drawable.GradientDrawable) {
                drawable.setColor(themeColors.dateBackgroundColor)
                drawable.setStroke(2, themeColors.borderColor, 8f, 8f) // Dashed border
            } else {
                // Create new drawable with theme colors
                val newDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(themeColors.dateBackgroundColor)
                    setStroke(2, themeColors.borderColor, 8f, 8f) // Dashed border
                }
                it.background = newDrawable
            }
        }
        
        // Apply font size and family
        binding.contentEditText.textSize = fontSizeSp
        binding.previewText.textSize = fontSizeSp
        
        // Apply font family
        val fontFamily = android.graphics.Typeface.create(settings.fontFamily, android.graphics.Typeface.NORMAL)
        binding.contentEditText.typeface = fontFamily
        binding.previewText.typeface = fontFamily
        
        // Update status bar
        window.statusBarColor = themeColors.backgroundColor
        window.navigationBarColor = themeColors.backgroundColor
        
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }
    
    private fun loadEntry() {
        val dateKey = intent.getStringExtra(EXTRA_DATE_KEY)
        
        if (dateKey != null) {
            // Editing existing entry by date key
            isNewEntry = false
            selectedDate = JournalEntry.parseToCalendar(dateKey)
            
            lifecycleScope.launch {
                currentEntry = database.getEntryByDate(dateKey)
                currentEntry?.let { entry ->
                    binding.contentEditText.setText(entry.content)
                    updateDateDisplay()
                    
                    // Start in preview mode if content exists
                    isPreviewMode = true
                    updatePreviewMode()
                }
            }
        } else {
            // New entry for today
            isNewEntry = true
            selectedDate = Calendar.getInstance()
            updateDateDisplay()
            
            // Check if there's already an entry for today
            lifecycleScope.launch {
                val todayKey = JournalEntry.getDateKey(selectedDate)
                currentEntry = database.getEntryByDate(todayKey)
                currentEntry?.let { entry ->
                    binding.contentEditText.setText(entry.content)
                    isNewEntry = false
                    
                    // Start in preview mode if content exists
                    isPreviewMode = true
                    updatePreviewMode()
                }
            }
        }
    }
    
    private fun setupDateDisplay() {
        updateDateDisplay()
        
        // Make date clickable
        binding.dateContainer.setOnClickListener {
            showDatePicker()
        }
    }
    
    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateDisplay()
                
                // Fetch existing entry for the selected date
                fetchEntryForSelectedDate()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }
    
    private fun fetchEntryForSelectedDate() {
        lifecycleScope.launch {
            val dateKey = JournalEntry.getDateKey(selectedDate)
            val existingEntry = database.getEntryByDate(dateKey)
            
            if (existingEntry != null) {
                currentEntry = existingEntry
                isNewEntry = false
                binding.contentEditText.setText(existingEntry.content)
                
                // Switch to preview mode if content exists
                isPreviewMode = true
                updatePreviewMode()
                
                Toast.makeText(this@EditorActivity, 
                    "Loaded existing entry", Toast.LENGTH_SHORT).show()
            } else {
                // No existing entry - reset to empty and switch to edit mode
                currentEntry = null
                isNewEntry = true
                binding.contentEditText.setText("")
                
                // Switch to edit mode for new content
                isPreviewMode = false
                updatePreviewMode()
            }
            
            // Invalidate options menu to show/hide delete button
            invalidateOptionsMenu()
        }
    }
    
    private fun updateDateDisplay() {
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("d", Locale.getDefault())
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        
        binding.dayName.text = dayFormat.format(selectedDate.time).uppercase()
        binding.dayNumber.text = dateFormat.format(selectedDate.time)
        binding.monthYear.text = monthYearFormat.format(selectedDate.time).uppercase()
    }
    
    private fun setupDoneButton() {
        binding.doneButton.setOnClickListener {
            saveEntry()
        }
    }
    
    private fun setupPreviewToggle() {
        binding.previewToggle.setOnClickListener {
            isPreviewMode = !isPreviewMode
            updatePreviewMode()
        }
        
        // Setup gesture detector for double tap on preview to enter edit mode
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isPreviewMode) {
                    isPreviewMode = false
                    updatePreviewMode()
                    return true
                }
                return false
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isPreviewMode) {
                    // Handle checkbox toggle on single tap
                    handleCheckboxTap(e)
                    return true
                }
                return false
            }
        })
        
        binding.previewText.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            // Don't consume touch for links
            false
        }
    }
    
    private fun handleCheckboxTap(event: MotionEvent) {
        val layout = binding.previewText.layout ?: return
        val x = event.x.toInt() - binding.previewText.totalPaddingLeft
        val y = event.y.toInt() - binding.previewText.totalPaddingTop
        
        if (x < 0 || y < 0) return
        
        val line = layout.getLineForVertical(y)
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        
        val previewText = binding.previewText.text.toString()
        val editText = binding.contentEditText.text.toString()
        
        // Find the line in preview text
        val lines = previewText.split("\n")
        var charCount = 0
        var targetLineIndex = -1
        
        for ((index, lineText) in lines.withIndex()) {
            if (charCount <= offset && offset <= charCount + lineText.length) {
                targetLineIndex = index
                break
            }
            charCount += lineText.length + 1 // +1 for newline
        }
        
        if (targetLineIndex < 0) return
        
        val targetLine = lines.getOrNull(targetLineIndex) ?: return
        
        // Check if this line is a checkbox (☐ or ☑)
        if (targetLine.startsWith("☐ ") || targetLine.startsWith("☑ ")) {
            // Toggle the checkbox in the original markdown
            val editLines = editText.split("\n").toMutableList()
            
            if (targetLineIndex < editLines.size) {
                val editLine = editLines[targetLineIndex]
                
                // Toggle between - [ ] and - [x]
                val newLine = when {
                    editLine.trimStart().startsWith("- [ ]") -> 
                        editLine.replace("- [ ]", "- [x]")
                    editLine.trimStart().startsWith("- [x]") || editLine.trimStart().startsWith("- [X]") -> 
                        editLine.replace(Regex("- \\[[xX]]"), "- [ ]")
                    else -> editLine
                }
                
                editLines[targetLineIndex] = newLine
                val newContent = editLines.joinToString("\n")
                binding.contentEditText.setText(newContent)
                
                // Refresh preview
                updatePreviewMode()
            }
        }
    }
    
    private fun updatePreviewMode() {
        if (isPreviewMode) {
            // Show preview
            binding.contentEditText.visibility = View.GONE
            binding.previewScroll.visibility = View.VISIBLE
            binding.markdownToolbar.visibility = View.GONE
            binding.previewToggle.text = "Edit"
            
            // Parse and display markdown
            val content = binding.contentEditText.text.toString()
            binding.previewText.text = MarkdownParser.parse(
                content,
                themeColors.linkColor,
                themeColors.codeBackgroundColor,
                themeColors.textColor
            )
            binding.previewText.movementMethod = LinkMovementMethod.getInstance()
        } else {
            // Show editor
            binding.contentEditText.visibility = View.VISIBLE
            binding.previewScroll.visibility = View.GONE
            binding.markdownToolbar.visibility = View.VISIBLE
            binding.previewToggle.text = "Preview"
        }
    }
    
    private fun setupMarkdownToolbar() {
        binding.btnBold.setOnClickListener { insertMarkdown("**", "**") }
        binding.btnItalic.setOnClickListener { insertMarkdown("*", "*") }
        binding.btnCode.setOnClickListener { insertMarkdown("`", "`") }
        binding.btnH1.setOnClickListener { insertAtLineStart("# ") }
        binding.btnH2.setOnClickListener { insertAtLineStart("## ") }
        binding.btnH3.setOnClickListener { insertAtLineStart("### ") }
        binding.btnList.setOnClickListener { insertAtLineStart("- ") }
        binding.btnCheckbox.setOnClickListener { insertAtLineStart("- [ ] ") }
        binding.btnLink.setOnClickListener { insertLink() }
        binding.btnCodeBlock.setOnClickListener { insertCodeBlock() }
        binding.btnTimestamp.setOnClickListener { insertTimestamp() }
    }
    
    private fun insertMarkdown(prefix: String, suffix: String) {
        val editText = binding.contentEditText
        val start = editText.selectionStart
        val end = editText.selectionEnd
        
        if (start != end) {
            // Text is selected - wrap it
            val selectedText = editText.text.toString().substring(start, end)
            editText.text.replace(start, end, "$prefix$selectedText$suffix")
            editText.setSelection(start + prefix.length, end + prefix.length)
        } else {
            // No selection - insert placeholder
            editText.text.insert(start, "$prefix text $suffix")
            editText.setSelection(start + prefix.length, start + prefix.length + 5)
        }
    }
    
    private fun insertAtLineStart(prefix: String) {
        val editText = binding.contentEditText
        val start = editText.selectionStart
        val text = editText.text.toString()
        
        // Find the start of the current line
        var lineStart = start
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }
        
        // Insert prefix at line start
        editText.text.insert(lineStart, prefix)
        editText.setSelection(start + prefix.length)
    }
    
    private fun insertLink() {
        val editText = binding.contentEditText
        val start = editText.selectionStart
        val end = editText.selectionEnd
        
        if (start != end) {
            // Text is selected - use as link text
            val selectedText = editText.text.toString().substring(start, end)
            editText.text.replace(start, end, "[$selectedText](url)")
            editText.setSelection(start + selectedText.length + 3, start + selectedText.length + 6)
        } else {
            // No selection - insert template
            editText.text.insert(start, "[link text](url)")
            editText.setSelection(start + 1, start + 10)
        }
    }
    
    private fun insertCodeBlock() {
        val editText = binding.contentEditText
        val start = editText.selectionStart
        
        val codeBlock = "\n```\ncode here\n```\n"
        editText.text.insert(start, codeBlock)
        editText.setSelection(start + 5, start + 14)
    }
    
    private fun insertTimestamp() {
        val editText = binding.contentEditText
        val start = editText.selectionStart
        
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timestamp = "**${timeFormat.format(Date())}** - "
        editText.text.insert(start, timestamp)
        editText.setSelection(start + timestamp.length)
    }
    
    private fun saveEntry() {
        val content = binding.contentEditText.text.toString().trim()
        
        if (content.isEmpty()) {
            // If content is empty and there was an existing entry, delete it
            currentEntry?.let { entry ->
                lifecycleScope.launch {
                    database.deleteEntry(entry.date)
                    finish()
                }
                return
            }
            finish()
            return
        }
        
        lifecycleScope.launch {
            val entry = if (currentEntry != null) {
                // Update existing entry
                with(JournalEntry.Companion) {
                    currentEntry!!.withUpdatedContent(content)
                }
            } else {
                // Create new entry
                JournalEntry.create(selectedDate, content)
            }
            
            database.saveEntry(entry)
            finish()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (!isNewEntry) {
            menuInflater.inflate(R.menu.editor_menu, menu)
        }
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete -> {
                currentEntry?.let { entry ->
                    lifecycleScope.launch {
                        database.deleteEntry(entry.date)
                        finish()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
