package com.digitalgram.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.digitalgram.android.R
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.data.JournalEntry
import com.digitalgram.android.databinding.ItemDiaryEntryBinding
import com.digitalgram.android.databinding.ItemTimelineDotBinding
import com.digitalgram.android.util.MarkdownParser
import com.digitalgram.android.util.ThemeColors
import java.text.SimpleDateFormat
import java.util.*

sealed class TimelineItem {
    data class DotOnly(val date: Calendar, val isToday: Boolean) : TimelineItem()
    data class EntryWithDot(val entry: JournalEntry, val isToday: Boolean) : TimelineItem()
}

class JournalAdapter(
    private val onEntryClick: (JournalEntry) -> Unit,
    private val onDayClick: ((Calendar) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val TYPE_DOT_ONLY = 0
        private const val TYPE_ENTRY = 1
    }
    
    private var items: List<TimelineItem> = emptyList()
    private var entries: List<JournalEntry> = emptyList()
    private var currentMonth: Calendar = Calendar.getInstance()
    private var themeColors: ThemeColors = ThemeColors.getTheme(AppSettings.THEME_DEFAULT)
    private var fontSizeSp: Float = 16f
    
    fun submitList(journalEntries: List<JournalEntry>) {
        entries = journalEntries
        buildTimelineItems()
        notifyDataSetChanged()
    }
    
    fun setCurrentMonth(calendar: Calendar) {
        currentMonth = calendar.clone() as Calendar
        buildTimelineItems()
        notifyDataSetChanged()
    }
    
    fun setTheme(theme: ThemeColors) {
        themeColors = theme
        notifyDataSetChanged()
    }
    
    fun setFontSize(sizeSp: Float) {
        fontSizeSp = sizeSp
        notifyDataSetChanged()
    }
    
    fun getTodayPosition(): Int {
        return items.indexOfFirst { 
            when (it) {
                is TimelineItem.DotOnly -> it.isToday
                is TimelineItem.EntryWithDot -> it.isToday
            }
        }
    }
    
    private fun buildTimelineItems() {
        val newItems = mutableListOf<TimelineItem>()
        val today = Calendar.getInstance()
        
        val year = currentMonth.get(Calendar.YEAR)
        val month = currentMonth.get(Calendar.MONTH)
        val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Create a map of date key to entry for quick lookup
        val entriesMap = entries.associateBy { it.date }
        
        // Build items for each day of the month (ascending order - 1st to 31st)
        for (day in 1..daysInMonth) {
            val dayCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }
            
            val dateKey = String.format("%04d-%02d-%02d", year, month + 1, day)
            val isToday = dayCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    dayCalendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    dayCalendar.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
            
            val entry = entriesMap[dateKey]
            val isFuture = day > today.get(Calendar.DAY_OF_MONTH) && 
                    year == today.get(Calendar.YEAR) && 
                    month == today.get(Calendar.MONTH)
            
            // Show future days only if they have content
            if (isFuture && (entry == null || entry.content.isBlank())) {
                continue
            }
            
            if (entry != null && entry.content.isNotBlank()) {
                newItems.add(TimelineItem.EntryWithDot(entry, isToday))
            } else {
                newItems.add(TimelineItem.DotOnly(dayCalendar, isToday))
            }
        }
        
        items = newItems
    }
    
    override fun getItemCount(): Int = items.size
    
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TimelineItem.DotOnly -> TYPE_DOT_ONLY
            is TimelineItem.EntryWithDot -> TYPE_ENTRY
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DOT_ONLY -> {
                val binding = ItemTimelineDotBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                DotViewHolder(binding)
            }
            else -> {
                val binding = ItemDiaryEntryBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                EntryViewHolder(binding)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val isFirst = position == 0
        val isLast = position == itemCount - 1
        
        when (val item = items[position]) {
            is TimelineItem.DotOnly -> (holder as DotViewHolder).bind(item, isFirst, isLast)
            is TimelineItem.EntryWithDot -> (holder as EntryViewHolder).bind(item, isFirst, isLast)
        }
    }
    
    inner class DotViewHolder(
        private val binding: ItemTimelineDotBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = items[position]
                    if (item is TimelineItem.DotOnly) {
                        onDayClick?.invoke(item.date)
                    }
                }
            }
        }
        
        fun bind(item: TimelineItem.DotOnly, isFirst: Boolean, isLast: Boolean) {
            // Apply theme color to dot
            val dotColor = if (item.isToday) {
                themeColors.todayDotColor
            } else {
                themeColors.dotColor
            }
            
            // Create circular drawable with theme color
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
            drawable.setColor(dotColor)
            binding.timelineDot.background = drawable
            
            // Hide vertical lines
            binding.lineTop.visibility = View.GONE
            binding.lineBottom.visibility = View.GONE
        }
    }
    
    inner class EntryViewHolder(
        private val binding: ItemDiaryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.entryCard.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = items[position]
                    if (item is TimelineItem.EntryWithDot) {
                        onEntryClick(item.entry)
                    }
                }
            }
        }
        
        fun bind(item: TimelineItem.EntryWithDot, isFirst: Boolean, isLast: Boolean) {
            val entry = item.entry
            val date = entry.toDate()
            
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dateFormat = SimpleDateFormat("d", Locale.getDefault())
            
            binding.dayName.text = dayFormat.format(date).uppercase()
            binding.dayNumber.text = dateFormat.format(date)
            val previewText = MarkdownParser.parse(
                entry.content,
                themeColors.linkColor,
                themeColors.codeBackgroundColor,
                themeColors.textColor
            )
            binding.entryContent.text = previewText
            
            // Apply font size
            binding.entryContent.textSize = fontSizeSp
            
            // Apply theme colors
            binding.entryContent.setTextColor(themeColors.textColor)
            binding.dayName.setTextColor(themeColors.accentColor)
            binding.dayNumber.setTextColor(themeColors.textColor)
            binding.entryCard.setCardBackgroundColor(themeColors.backgroundColor)
            binding.entryCard.strokeColor = themeColors.borderColor
            binding.entryCard.strokeWidth = 2
            
            // Apply date background color to date box
            val dateBoxDrawable = binding.dateBox.background
            if (dateBoxDrawable is android.graphics.drawable.GradientDrawable) {
                dateBoxDrawable.setColor(themeColors.dateBackgroundColor)
            } else {
                val newDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(themeColors.dateBackgroundColor)
                }
                binding.dateBox.background = newDrawable
            }
            
            // Apply theme color to dot
            val dotColor = if (item.isToday) {
                themeColors.todayDotColor
            } else {
                themeColors.dotColor
            }
            
            // Create circular drawable with theme color
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
            drawable.setColor(dotColor)
            binding.timelineDot.background = drawable
            
            // Hide vertical lines
            binding.lineTop.visibility = View.GONE
            binding.lineBottom.visibility = View.GONE
            binding.entryCard.visibility = View.VISIBLE
        }
    }
}
