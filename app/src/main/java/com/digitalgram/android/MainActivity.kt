package com.digitalgram.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.data.JournalDatabase
import com.digitalgram.android.databinding.ActivityMainBinding
import com.digitalgram.android.ui.JournalAdapter
import com.digitalgram.android.util.ImageUtils
import com.digitalgram.android.util.ThemeColors
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var database: JournalDatabase
    private lateinit var adapter: JournalAdapter
    private lateinit var settings: AppSettings
    
    private var isLocked = true
    private var currentMonth = Calendar.getInstance()
    
    private val dateTimeHandler = Handler(Looper.getMainLooper())
    private val dateTimeRunnable = object : Runnable {
        override fun run() {
            updateDateTime()
            dateTimeHandler.postDelayed(this, 60000) // Update every minute
        }
    }
    
    private val unlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            isLocked = false
        } else {
            // User cancelled, finish the app
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display before setting content
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        settings = AppSettings.getInstance(this)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize database
        database = JournalDatabase.getInstance(applicationContext)
        database.setMonthFilter(
            currentMonth.get(Calendar.YEAR),
            currentMonth.get(Calendar.MONTH) + 1
        )

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "DigitalGram"

        setupWindowInsets()
        setupRecyclerView()
        setupBottomBar()
        observeEntries()
        applySettings()
        updateMonthYear()
    }
    
    private fun setupWindowInsets() {
        // Handle system window insets for proper padding with edge-to-edge display
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top padding to appBarLayout to account for status bar
            binding.appBarLayout.setPadding(
                binding.appBarLayout.paddingLeft,
                systemBars.top,
                binding.appBarLayout.paddingRight,
                binding.appBarLayout.paddingBottom
            )
            
            // Handle bottom bar padding for navigation bar
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight,
                systemBars.bottom
            )
            
            // Handle RecyclerView padding - wait for layout
            binding.bottomBar.post {
                val bottomBarHeight = binding.bottomBar.height
                val totalBottomPadding = bottomBarHeight
                binding.recyclerView.setPadding(
                    binding.recyclerView.paddingLeft,
                    binding.recyclerView.paddingTop,
                    binding.recyclerView.paddingRight,
                    totalBottomPadding
                )
            }
            
            insets
        }
    }
    
    override fun onResume() {
        super.onResume()
        applySettings()
        
        // Reset window layout to ensure proper display after returning from editor
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Force layout refresh to fix any cramped layout issues
        binding.coordinatorLayout.post {
            binding.coordinatorLayout.requestLayout()
            binding.recyclerView.requestLayout()
        }
        
        // Start date/time updates
        updateDateTime()
        dateTimeHandler.postDelayed(dateTimeRunnable, 60000)
        
        // Reinitialize database to pick up any database switches
        val newDatabase = JournalDatabase.getInstance(applicationContext)
        if (newDatabase !== database) {
            // Database instance changed, need to reattach observer
            database = newDatabase
            observeEntries()
        }
        
        // Refresh entries from database
        database.refreshEntries()
        
        // Check if passcode is enabled and we need to lock
        if (settings.passcodeEnabled && isLocked) {
            showLockScreen()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop date/time updates
        dateTimeHandler.removeCallbacks(dateTimeRunnable)
        // Lock when app goes to background
        if (settings.passcodeEnabled) {
            isLocked = true
        }
    }
    
    private fun showLockScreen() {
        val intent = Intent(this, PasscodeActivity::class.java).apply {
            putExtra(PasscodeActivity.EXTRA_MODE, PasscodeMode.VERIFY.name)
            putExtra(PasscodeActivity.EXTRA_LOCK_SCREEN, true)
        }
        unlockLauncher.launch(intent)
    }
    
    private fun applySettings() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (settings.fullscreen) {
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
            windowInsetsController.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        }

        // Apply theme colors after fullscreen handling
        applyThemeColors()
        
        // Update adapter font size
        val fontSizeSp = settings.getFontSizeSp()
        adapter.setFontSize(fontSizeSp)
    }
    
    private fun applyThemeColors() {
        val themeColors = ThemeColors.getTheme(settings.theme, this)
        val isDark = ThemeColors.isDarkTheme(settings.theme)
        
        // Apply wallpaper if set
        val wallpaperUri = settings.wallpaperUri
        if (wallpaperUri != null) {
            try {
                val uri = android.net.Uri.parse(wallpaperUri)
                val bitmap = ImageUtils.loadOrientedBitmap(contentResolver, uri)
                val drawable = bitmap?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
                if (drawable != null) {
                    binding.coordinatorLayout.background = drawable
                } else {
                    binding.coordinatorLayout.setBackgroundColor(themeColors.backgroundColor)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to theme background color if wallpaper fails
                binding.coordinatorLayout.setBackgroundColor(themeColors.backgroundColor)
            }
        } else {
            // No wallpaper, use theme background color
            binding.coordinatorLayout.setBackgroundColor(themeColors.backgroundColor)
        }
        
        // Apply to main views - make transparent if wallpaper is set
        if (wallpaperUri != null) {
            // Set 50% transparent background (50% opacity) for bars
            val semiTransparentBg = Color.argb(128, 0, 0, 0)
            binding.appBarLayout.setBackgroundColor(semiTransparentBg)
            binding.appBarLayout.alpha = 1.0f // Keep content at 100% opacity
            binding.toolbar.setBackgroundColor(Color.TRANSPARENT)
            binding.bottomBar.setBackgroundColor(semiTransparentBg)
            binding.bottomBar.alpha = 1.0f // Keep content at 100% opacity
            binding.recyclerView.setBackgroundColor(Color.TRANSPARENT)
            // Add bottom padding so entries don't go under bottom bar
            val bottomPadding = (80 * resources.displayMetrics.density).toInt()
            binding.recyclerView.setPadding(
                binding.recyclerView.paddingLeft,
                binding.recyclerView.paddingTop,
                binding.recyclerView.paddingRight,
                bottomPadding
            )
        } else {
            binding.appBarLayout.setBackgroundColor(themeColors.backgroundColor)
            binding.appBarLayout.alpha = 1.0f
            binding.toolbar.setBackgroundColor(themeColors.backgroundColor)
            binding.bottomBar.setBackgroundColor(themeColors.backgroundColor)
            binding.bottomBar.alpha = 1.0f
            binding.recyclerView.setBackgroundColor(themeColors.backgroundColor)
            // Reset RecyclerView padding to default (80dp)
            val bottomPadding = (80 * resources.displayMetrics.density).toInt()
            binding.recyclerView.setPadding(
                binding.recyclerView.paddingLeft,
                binding.recyclerView.paddingTop,
                binding.recyclerView.paddingRight,
                bottomPadding
            )
        }
        binding.toolbar.setTitleTextColor(themeColors.textColor)
        
        // Apply to bottom bar elements
        binding.monthText.setTextColor(themeColors.textColor)
        binding.yearText.setTextColor(themeColors.textColor)
        binding.monthIndicator.setBackgroundColor(themeColors.accentColor)
        binding.separator1.setTextColor(themeColors.secondaryTextColor)
        binding.separator2.setTextColor(themeColors.secondaryTextColor)
        
        // Apply to date/time display
        binding.dateTimeText.setTextColor(themeColors.secondaryTextColor)
        
        // Apply to add button tint
        binding.addButton.setColorFilter(themeColors.buttonColor)
        
        // Apply to settings button tint
        binding.settingsButton.setColorFilter(themeColors.buttonColor)
        
        // Apply to progress bars
        binding.progressFilled.setBackgroundColor(themeColors.accentColor)
        binding.progressEmpty.setBackgroundColor(themeColors.dotColor)

        // Update status bar and navigation bar - same as EditorActivity
        window.statusBarColor = themeColors.backgroundColor
        window.navigationBarColor = themeColors.backgroundColor
        
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        
        // Update adapter theme
        adapter.setTheme(themeColors)
        adapter.setBorderStyle(settings.borderStyle)
    }
    
    private fun setupRecyclerView() {
        adapter = JournalAdapter(
            onEntryClick = { entry ->
                val intent = Intent(this, EditorActivity::class.java).apply {
                    putExtra(EditorActivity.EXTRA_DATE_KEY, entry.date)
                }
                startActivity(intent)
            },
            onDayClick = { calendar ->
                // Open editor for the selected day
                val dateKey = String.format(
                    "%04d-%02d-%02d",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                val intent = Intent(this, EditorActivity::class.java).apply {
                    putExtra(EditorActivity.EXTRA_DATE_KEY, dateKey)
                }
                startActivity(intent)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }
    
    private fun setupBottomBar() {
        binding.addButton.setOnClickListener {
            val intent = Intent(this, EditorActivity::class.java)
            startActivity(intent)
        }
        
        // Month switching - click on month to show month picker, click on year to show year picker
        binding.monthText.setOnClickListener { showMonthOnlyPicker() }
        binding.yearText.setOnClickListener { showYearOnlyPicker() }
        
        // Click on progress bar area to open settings
        binding.progressFilled.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        binding.progressEmpty.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        
        // Settings button
        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun showMonthOnlyPicker() {
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        
        val currentMonthIndex = currentMonth.get(Calendar.MONTH)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Month")
            .setSingleChoiceItems(months, currentMonthIndex) { dialog, which ->
                currentMonth.set(Calendar.MONTH, which)
                updateMonthYear()
                database.setMonthFilter(
                    currentMonth.get(Calendar.YEAR),
                    currentMonth.get(Calendar.MONTH) + 1
                )
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showYearOnlyPicker() {
        val currentYear = currentMonth.get(Calendar.YEAR)
        
        // Create a NumberPicker for year selection
        val yearPicker = android.widget.NumberPicker(this).apply {
            minValue = 2000
            maxValue = 2100
            value = currentYear
            wrapSelectorWheel = false
        }
        
        // Wrap in a container with padding
        val container = android.widget.FrameLayout(this).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
            addView(yearPicker, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ))
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Year")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                currentMonth.set(Calendar.YEAR, yearPicker.value)
                updateMonthYear()
                database.setMonthFilter(
                    currentMonth.get(Calendar.YEAR),
                    currentMonth.get(Calendar.MONTH) + 1
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateDateTime() {
        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateStr = dateFormat.format(now.time)
        val timeStr = timeFormat.format(now.time)
        binding.dateTimeText.text = "$dateStr • $timeStr"
    }
    
    private fun showMonthPicker() {
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        
        val currentMonthIndex = currentMonth.get(Calendar.MONTH)
        val currentYear = currentMonth.get(Calendar.YEAR)
        
        // Create a dialog with month and year selection
        val dialogView = layoutInflater.inflate(R.layout.dialog_month_picker, null)
        val monthSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.monthSpinner)
        val yearPicker = dialogView.findViewById<android.widget.NumberPicker>(R.id.yearPicker)
        
        // Setup month spinner
        val monthAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        monthSpinner.adapter = monthAdapter
        monthSpinner.setSelection(currentMonthIndex)
        
        // Setup year picker
        yearPicker.minValue = 2000
        yearPicker.maxValue = 2100
        yearPicker.value = currentYear
        yearPicker.wrapSelectorWheel = false
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Month")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                currentMonth.set(Calendar.MONTH, monthSpinner.selectedItemPosition)
                currentMonth.set(Calendar.YEAR, yearPicker.value)
                updateMonthYear()
                database.setMonthFilter(
                    currentMonth.get(Calendar.YEAR),
                    currentMonth.get(Calendar.MONTH) + 1
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateMonthYear() {
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        
        binding.monthText.text = monthFormat.format(currentMonth.time).uppercase()
        binding.yearText.text = yearFormat.format(currentMonth.time)
    }
    
    private fun observeEntries() {
        database.entries.observe(this) { entries ->
            adapter.setCurrentMonth(currentMonth)
            adapter.submitEntries(entries) {
                val itemCount = adapter.itemCount
                if (itemCount > 0) {
                    binding.recyclerView.scrollToPosition(itemCount - 1)
                }
            }

            val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            val entriesThisMonth = entries.count { entry ->
                entry.year == currentMonth.get(Calendar.YEAR) &&
                entry.month == currentMonth.get(Calendar.MONTH) + 1 &&
                entry.content.isNotBlank()
            }
            updateProgressBar(entriesThisMonth, daysInMonth)
        }
    }
    
    private fun updateProgressBar(filled: Int, total: Int) {
        val filledWeight = if (total > 0) filled.toFloat() / total else 0f
        val emptyWeight = 1f - filledWeight
        
        val filledParams = binding.progressFilled.layoutParams as android.widget.LinearLayout.LayoutParams
        val emptyParams = binding.progressEmpty.layoutParams as android.widget.LinearLayout.LayoutParams
        
        filledParams.weight = filledWeight
        emptyParams.weight = emptyWeight
        
        binding.progressFilled.layoutParams = filledParams
        binding.progressEmpty.layoutParams = emptyParams
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Menu removed - settings accessible via footer bar
        return false
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }
}
