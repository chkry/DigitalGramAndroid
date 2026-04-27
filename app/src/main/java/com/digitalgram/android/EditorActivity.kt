package com.digitalgram.android

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.digitalgram.android.BuildConfig
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.data.JournalDatabase
import com.digitalgram.android.data.JournalEntry
import com.digitalgram.android.databinding.ActivityEditorBinding
import com.digitalgram.android.util.ImageUtils
import com.digitalgram.android.util.ImageUtilsAsync
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
    private var isKeyboardVisible = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var currentKeyboardHeight = 0
    private var scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null

    // Auto-save debounce state at class scope so date-change paths can flush it
    // synchronously — otherwise typing on day N + navigating to day N+1 within the
    // 2s window stamps day N's last keystroke onto day N+1's entry.
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private var autoSaveRunnable: Runnable? = null
    
    companion object {
        private const val TAG = "Editor"
        const val EXTRA_DATE_KEY = "extra_date_key"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for seamless status/navigation bar blending
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
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
            if (BuildConfig.DEBUG) Log.w(TAG, "db init failed", e)
            Toast.makeText(this, "Error initializing database", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        applyThemeColors()
        setupWindowInsets()
        setupKeyboardDetection()
        setupEditTextScrolling()
        setupEnterKeyHandler()
        loadEntry()
        setupDateDisplay()
        setupDoneButton()
        setupMarkdownToolbar()
        setupPreviewToggle()
        setupAutoSave()
    }
    
    private fun setupWindowInsets() {
        // Handle system window insets for proper padding with edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top padding to appBarLayout to account for status bar
            binding.appBarLayout.setPadding(
                binding.appBarLayout.paddingLeft,
                systemBars.top,
                binding.appBarLayout.paddingRight,
                binding.appBarLayout.paddingBottom
            )
            
            // Don't apply bottom padding here - keyboard handling manages bottom space
            // and we don't want to push the Done button off screen
            
            insets
        }
    }
    
    private fun setupKeyboardDetection() {
        // Use OnGlobalLayoutListener to detect keyboard and adjust layout
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rootView = binding.root
            val visibleRect = Rect()
            rootView.getWindowVisibleDisplayFrame(visibleRect)
            
            val screenHeight = rootView.rootView.height
            val keyboardHeight = screenHeight - visibleRect.bottom
            
            // Keyboard is considered visible if it takes more than 15% of screen height
            val keyboardVisible = keyboardHeight > screenHeight * 0.15
            
            // Only update if keyboard height actually changed to prevent flickering
            if (keyboardVisible && keyboardHeight != currentKeyboardHeight) {
                currentKeyboardHeight = keyboardHeight
                isKeyboardVisible = true
                
                // Remove done button margin when keyboard is visible
                val doneButtonLayoutParams = binding.doneButton.layoutParams as android.widget.LinearLayout.LayoutParams
                doneButtonLayoutParams.bottomMargin = 0
                binding.doneButton.layoutParams = doneButtonLayoutParams
                
                // Resize contentFrame to sit just above keyboard (with 10% extra space)
                val contentFrameLayoutParams = binding.contentFrame.layoutParams as android.widget.LinearLayout.LayoutParams
                contentFrameLayoutParams.bottomMargin = (keyboardHeight * 0.8).toInt()
                binding.contentFrame.layoutParams = contentFrameLayoutParams
                
                // Add small bottom padding to editScrollView for scroll space
                binding.editScrollView.setPadding(
                    binding.editScrollView.paddingLeft,
                    binding.editScrollView.paddingTop,
                    binding.editScrollView.paddingRight,
                    100  // Small padding for scroll space
                )
                
                // Debounced scroll to cursor to prevent flickering
                scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
                scrollRunnable = Runnable {
                    scrollToCursorPosition()
                }
                scrollRunnable?.let { scrollHandler.postDelayed(it, 150) }
                
            } else if (!keyboardVisible && isKeyboardVisible) {
                currentKeyboardHeight = 0
                isKeyboardVisible = false
                
                // Cancel any pending scroll
                scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
                
                // Restore original contentFrame margin
                val contentFrameLayoutParams = binding.contentFrame.layoutParams as android.widget.LinearLayout.LayoutParams
                contentFrameLayoutParams.bottomMargin = 0
                binding.contentFrame.layoutParams = contentFrameLayoutParams
                
                // Restore original padding
                binding.editScrollView.setPadding(
                    binding.editScrollView.paddingLeft,
                    binding.editScrollView.paddingTop,
                    binding.editScrollView.paddingRight,
                    0
                )
                
                // Add margin under done button when keyboard is not visible
                val doneButtonLayoutParams = binding.doneButton.layoutParams as android.widget.LinearLayout.LayoutParams
                doneButtonLayoutParams.bottomMargin = 32.dpToPx()
                binding.doneButton.layoutParams = doneButtonLayoutParams
            }
        }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        
        // Also scroll to cursor whenever selection changes
        binding.contentEditText.setOnClickListener {
            if (isKeyboardVisible) {
                scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
                scrollRunnable = Runnable {
                    scrollToCursorPosition()
                }
                scrollRunnable?.let { scrollHandler.postDelayed(it, 100) }
            }
        }
    }
    
    private fun scrollToCursorPosition() {
        val editText = binding.contentEditText
        val layout = editText.layout ?: return
        
        val cursorPos = editText.selectionEnd
        val cursorLine = layout.getLineForOffset(cursorPos)
        val cursorY = layout.getLineTop(cursorLine)
        val lineHeight = layout.getLineBottom(cursorLine) - layout.getLineTop(cursorLine)
        
        // Get the visible height of the ScrollView
        val scrollViewHeight = binding.editScrollView.height
        val currentScrollY = binding.editScrollView.scrollY
        
        // Calculate cursor bottom position
        val cursorBottom = cursorY + lineHeight + editText.paddingTop
        val visibleBottom = currentScrollY + scrollViewHeight - binding.editScrollView.paddingBottom
        
        if (cursorBottom > visibleBottom - lineHeight) {
            // Scroll to make cursor visible with padding
            val targetScroll = cursorBottom - scrollViewHeight + binding.editScrollView.paddingBottom + lineHeight * 2
            binding.editScrollView.smoothScrollTo(0, maxOf(0, targetScroll))
        } else if (cursorY < currentScrollY) {
            // Cursor is above visible area
            binding.editScrollView.smoothScrollTo(0, maxOf(0, cursorY - lineHeight))
        }
    }
    
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun getBorderWidth(borderStyle: String): Int {
        return when (borderStyle) {
            AppSettings.BORDER_A -> 0  // No border
            AppSettings.BORDER_B -> 1  // Thin border
            AppSettings.BORDER_C -> 2  // Medium border
            AppSettings.BORDER_E -> 4  // Thick border
            else -> 2 // Default medium border
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        } ?: run {
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }
    
    private fun scrollToEditText() {
        // Scroll the editScrollView to show the cursor
        binding.editScrollView.post {
            val editText = binding.contentEditText
            val cursorLine = editText.layout?.getLineForOffset(editText.selectionStart) ?: 0
            val lineTop = editText.layout?.getLineTop(cursorLine) ?: 0
            
            binding.editScrollView.smoothScrollTo(0, lineTop)
        }
    }
    
    private fun setupEditTextScrolling() {
        // Scroll to cursor when EditText gains focus
        binding.contentEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.contentEditText.postDelayed({ scrollToEditText() }, 300)
            }
        }
    }
    
    private fun setupEnterKeyHandler() {
        binding.contentEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && 
                event.action == android.view.KeyEvent.ACTION_DOWN) {
                handleEnterKey()
                return@setOnKeyListener true
            }
            false
        }
    }
    
    private fun handleEnterKey() {
        val editText = binding.contentEditText
        val cursorPosition = editText.selectionStart
        val text = editText.text.toString()
        
        // Find the current line
        val lineStart = text.lastIndexOf('\n', cursorPosition - 1) + 1
        val lineEnd = text.indexOf('\n', cursorPosition).let { if (it == -1) text.length else it }
        val currentLine = text.substring(lineStart, lineEnd)
        
        // Check for checkbox patterns
        val checkboxUnchecked = Regex("^- \\[ ] (.*)$")
        val checkboxChecked = Regex("^- \\[[xX]] (.*)$")
        val bulletPoint = Regex("^- (.*)$")
        val starBullet = Regex("^\\* (.*)$")
        
        val prefix = when {
            checkboxUnchecked.matches(currentLine) -> {
                val content = checkboxUnchecked.find(currentLine)?.groups?.get(1)?.value ?: ""
                // If line is empty checkbox, remove it instead of creating new one
                if (content.trim().isEmpty()) {
                    // Remove the empty checkbox line
                    editText.text.delete(lineStart, cursorPosition)
                    return
                }
                "- [ ] "
            }
            checkboxChecked.matches(currentLine) -> {
                val content = checkboxChecked.find(currentLine)?.groups?.get(1)?.value ?: ""
                if (content.trim().isEmpty()) {
                    editText.text.delete(lineStart, cursorPosition)
                    return
                }
                "- [ ] "  // Start new line with unchecked checkbox
            }
            bulletPoint.matches(currentLine) -> {
                val content = bulletPoint.find(currentLine)?.groups?.get(1)?.value ?: ""
                if (content.trim().isEmpty()) {
                    editText.text.delete(lineStart, cursorPosition)
                    return
                }
                "- "
            }
            starBullet.matches(currentLine) -> {
                val content = starBullet.find(currentLine)?.groups?.get(1)?.value ?: ""
                if (content.trim().isEmpty()) {
                    editText.text.delete(lineStart, cursorPosition)
                    return
                }
                "* "
            }
            else -> null
        }
        
        if (prefix != null) {
            // Insert newline and prefix
            editText.text.insert(cursorPosition, "\n$prefix")
            editText.setSelection(cursorPosition + prefix.length + 1)
        } else {
            // Normal newline
            editText.text.insert(cursorPosition, "\n")
            editText.setSelection(cursorPosition + 1)
        }
    }
    
    private fun scrollToCursor() {
        scrollToEditText()
    }
    
    private fun setupAutoSave() {
        binding.contentEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                // Cancel any pending save
                autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }

                // Debounced scroll to keep cursor visible after text change
                if (isKeyboardVisible) {
                    scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
                    scrollRunnable = Runnable {
                        scrollToCursorPosition()
                    }
                    scrollRunnable?.let { scrollHandler.postDelayed(it, 100) }
                }

                // Schedule a new save after 2 seconds of no typing
                autoSaveRunnable = Runnable {
                    autoSaveEntry()
                }
                autoSaveRunnable?.let { autoSaveHandler.postDelayed(it, 2000) }

                // Scroll to cursor after text changes when keyboard is visible
                if (isKeyboardVisible) {
                    binding.contentEditText.postDelayed({ scrollToEditText() }, 50)
                }
            }
        })
    }

    /**
     * Flush any pending debounced save against the entry+date that owned the text
     * at flush time. Call this before navigating to a different day or pausing.
     */
    private fun flushPendingAutoSave() {
        autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
        autoSaveRunnable = null

        val content = binding.contentEditText.text.toString().trim()
        if (content.isEmpty()) return

        // Snapshot the entry and date that this text actually belongs to.
        val ownerEntry = currentEntry
        val ownerDate = selectedDate.clone() as Calendar

        lifecycleScope.launch {
            val entry = ownerEntry?.let { current ->
                with(JournalEntry.Companion) { current.withUpdatedContent(content) }
            } ?: JournalEntry.create(ownerDate, content)
            database.saveEntry(entry)
        }
    }
    
    private fun autoSaveEntry() {
        val content = binding.contentEditText.text.toString().trim()
        
        if (content.isEmpty()) return
        
        lifecycleScope.launch {
            val entry = currentEntry?.let { current ->
                with(JournalEntry.Companion) {
                    current.withUpdatedContent(content)
                }
            } ?: JournalEntry.create(selectedDate, content)
            
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
        if (!wallpaperUri.isNullOrEmpty()) {
            ImageUtilsAsync.loadWallpaperAsync(
                contentResolver, android.net.Uri.parse(wallpaperUri), binding.rootLayout, resources, lifecycleScope
            )
        } else {
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
        
        // Apply background to the content frame with border - always create new drawable
        val borderWidth = getBorderWidth(settings.borderStyle)
        val contentFrameDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(themeColors.backgroundColor)
            if (borderWidth > 0) {
                setStroke(borderWidth.dpToPx(), themeColors.borderColor)
            }
            cornerRadius = 0f
        }
        binding.contentFrame.background = contentFrameDrawable
        
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
        val fontFamily = getTypefaceForFont(settings.fontFamily)
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
                    // Post to ensure view is laid out before updating preview
                    binding.previewText.post {
                        updatePreviewMode()
                    }
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
                    // Post to ensure view is laid out before updating preview
                    binding.previewText.post {
                        updatePreviewMode()
                    }
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
                // Persist the current entry's last keystroke before swapping context.
                flushPendingAutoSave()
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
                // Post to ensure view is laid out before updating preview
                binding.previewText.post {
                    updatePreviewMode()
                }
                
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
                    // Check if tap is on a link first
                    if (!isTapOnLink(e)) {
                        // Handle checkbox toggle on single tap
                        handleCheckboxTap(e)
                        return true
                    }
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
    
    private fun isTapOnLink(event: MotionEvent): Boolean {
        val textView = binding.previewText
        val text = textView.text
        
        if (text !is android.text.Spanned) return false
        
        val layout = textView.layout ?: return false
        val x = event.x.toInt() - textView.totalPaddingLeft
        val y = event.y.toInt() - textView.totalPaddingTop
        
        if (x < 0 || y < 0) return false
        
        val line = layout.getLineForVertical(y)
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        
        // Check if there's a URLSpan at this position
        val urlSpans = text.getSpans(offset, offset, android.text.style.URLSpan::class.java)
        return urlSpans.isNotEmpty()
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
            // Hide keyboard when entering preview mode
            hideKeyboard()
            
            // Show preview
            binding.contentEditText.visibility = View.GONE
            binding.previewScroll.visibility = View.VISIBLE
            binding.markdownToolbar.visibility = View.GONE
            binding.previewToggle.text = "Edit"
            
            // Parse and display markdown with compatibility fixes
            val content = binding.contentEditText.text.toString()
            try {
                val styledText = MarkdownParser.parse(
                    content,
                    themeColors.linkColor,
                    themeColors.codeBackgroundColor,
                    themeColors.textColor,
                    themeColors.accentColor
                )
                
                // Set text first
                binding.previewText.text = styledText
                
                // Force software rendering for better compatibility
                binding.previewText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                
                // Set movement method for clickable links
                binding.previewText.movementMethod = LinkMovementMethod.getInstance()
                
                // Trigger redraw
                binding.previewText.invalidate()
            } catch (e: Exception) {
                binding.previewText.text = content
                if (BuildConfig.DEBUG) Log.w(TAG, "markdown parse failed", e)
            }
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
            val entry = currentEntry?.let { current ->
                // Update existing entry
                with(JournalEntry.Companion) {
                    current.withUpdatedContent(content)
                }
            } ?: JournalEntry.create(selectedDate, content)  // Create new entry
            
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
    
    override fun onPause() {
        // Flush before the activity goes off-screen so a backgrounded process
        // doesn't lose the last 2 seconds of typing.
        flushPendingAutoSave()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
        // Clean up the global layout listener
        globalLayoutListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
    }
    
    private fun getTypefaceForFont(fontFamily: String): android.graphics.Typeface {
        return when (fontFamily) {
            AppSettings.FONT_SERIF_BOLD -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            AppSettings.FONT_SERIF_ITALIC -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
            AppSettings.FONT_SANS_BOLD -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            AppSettings.FONT_MONO_BOLD -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            AppSettings.FONT_CURSIVE_BOLD -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.BOLD)
            else -> android.graphics.Typeface.create(fontFamily, android.graphics.Typeface.NORMAL)
        }
    }
}
