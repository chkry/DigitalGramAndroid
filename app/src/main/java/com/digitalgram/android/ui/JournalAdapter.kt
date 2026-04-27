package com.digitalgram.android.ui

import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.data.JournalEntry
import com.digitalgram.android.databinding.ItemDiaryEntryBinding
import com.digitalgram.android.databinding.ItemTimelineDotBinding
import com.digitalgram.android.util.MarkdownParser
import com.digitalgram.android.util.ThemeColors
import java.text.SimpleDateFormat
import java.util.*

sealed class TimelineItem {
    abstract val id: Long
    data class DotOnly(val date: Calendar, val isToday: Boolean) : TimelineItem() {
        override val id: Long get() = date.hashCode().toLong() shl 1
    }
    data class EntryWithDot(val entry: JournalEntry, val isToday: Boolean) : TimelineItem() {
        override val id: Long get() = (entry.date.hashCode().toLong() shl 1) or 1L
    }
}

class JournalAdapter(
    private val onEntryClick: (JournalEntry) -> Unit,
    private val onDayClick: ((Calendar) -> Unit)? = null
) : ListAdapter<TimelineItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_DOT_ONLY = 0
        private const val TYPE_ENTRY = 1
        private const val PAYLOAD_RERENDER = "rerender"

        private val DIFF = object : DiffUtil.ItemCallback<TimelineItem>() {
            override fun areItemsTheSame(a: TimelineItem, b: TimelineItem): Boolean =
                a.id == b.id

            override fun areContentsTheSame(a: TimelineItem, b: TimelineItem): Boolean =
                a == b

            override fun getChangePayload(oldItem: TimelineItem, newItem: TimelineItem): Any? =
                null
        }
    }

    private var entries: List<JournalEntry> = emptyList()
    private var currentMonth: Calendar = Calendar.getInstance()
    private var themeColors: ThemeColors = ThemeColors.getTheme(AppSettings.THEME_DEFAULT)
    private var fontSizeSp: Float = 16f
    private var borderStyle: String = AppSettings.BORDER_A

    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d", Locale.getDefault())
    private val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
    }
    private val dateBoxDrawable = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = 8f
    }

    // Parsed-markdown cache. Spans embed theme colors, so the cache is invalidated
    // whenever the theme changes.
    private val previewCache = LruCache<String, CharSequence>(128)

    fun submitEntries(journalEntries: List<JournalEntry>, onCommitted: Runnable? = null) {
        entries = journalEntries
        submitList(buildTimelineItems(), onCommitted)
    }

    fun setCurrentMonth(calendar: Calendar) {
        currentMonth = calendar.clone() as Calendar
        submitList(buildTimelineItems())
    }

    fun setTheme(theme: ThemeColors) {
        themeColors = theme
        previewCache.evictAll()
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_RERENDER)
    }

    fun setFontSize(sizeSp: Float) {
        if (fontSizeSp == sizeSp) return
        fontSizeSp = sizeSp
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_RERENDER)
    }

    fun setBorderStyle(style: String) {
        if (borderStyle == style) return
        borderStyle = style
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_RERENDER)
    }

    private fun getBorderWidth(borderStyle: String): Int = when (borderStyle) {
        AppSettings.BORDER_A -> 0
        AppSettings.BORDER_B -> 1
        AppSettings.BORDER_C -> 2
        AppSettings.BORDER_E -> 4
        else -> 2
    }

    fun getTodayPosition(): Int {
        return currentList.indexOfFirst {
            when (it) {
                is TimelineItem.DotOnly -> it.isToday
                is TimelineItem.EntryWithDot -> it.isToday
            }
        }
    }

    private fun buildTimelineItems(): List<TimelineItem> {
        val newItems = mutableListOf<TimelineItem>()
        val today = Calendar.getInstance()

        val year = currentMonth.get(Calendar.YEAR)
        val month = currentMonth.get(Calendar.MONTH)
        val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

        val entriesMap = entries.associateBy { it.date }

        for (day in 1..daysInMonth) {
            val dayCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dateKey = String.format("%04d-%02d-%02d", year, month + 1, day)
            val isToday = dayCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    dayCalendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    dayCalendar.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)

            val entry = entriesMap[dateKey]
            val isFuture = day > today.get(Calendar.DAY_OF_MONTH) &&
                    year == today.get(Calendar.YEAR) &&
                    month == today.get(Calendar.MONTH)

            if (isFuture && (entry == null || entry.content.isBlank())) {
                continue
            }

            if (entry != null && entry.content.isNotBlank()) {
                newItems.add(TimelineItem.EntryWithDot(entry, isToday))
            } else {
                newItems.add(TimelineItem.DotOnly(dayCalendar, isToday))
            }
        }

        return newItems
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TimelineItem.DotOnly -> TYPE_DOT_ONLY
        is TimelineItem.EntryWithDot -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DOT_ONLY -> DotViewHolder(
                ItemTimelineDotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> EntryViewHolder(
                ItemDiaryEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TimelineItem.DotOnly -> (holder as DotViewHolder).bind(item)
            is TimelineItem.EntryWithDot -> (holder as EntryViewHolder).bind(item)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty() || !payloads.contains(PAYLOAD_RERENDER)) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        // Payload bind: only restyle. Skip markdown re-parse when content unchanged.
        when (val item = getItem(position)) {
            is TimelineItem.DotOnly -> (holder as DotViewHolder).applyStyle(item)
            is TimelineItem.EntryWithDot -> (holder as EntryViewHolder).applyStyle(item)
        }
    }

    private fun parsePreview(content: String): CharSequence {
        previewCache.get(content)?.let { return it }
        val parsed = MarkdownParser.parse(
            content,
            themeColors.linkColor,
            themeColors.codeBackgroundColor,
            themeColors.textColor,
            themeColors.accentColor
        )
        previewCache.put(content, parsed)
        return parsed
    }

    inner class DotViewHolder(
        private val binding: ItemTimelineDotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val item = getItem(pos)
                    if (item is TimelineItem.DotOnly) {
                        onDayClick?.invoke(item.date)
                    }
                }
            }
        }

        fun bind(item: TimelineItem.DotOnly) {
            applyStyle(item)
        }

        fun applyStyle(item: TimelineItem.DotOnly) {
            val dotColor = if (item.isToday) themeColors.todayDotColor else themeColors.dotColor
            dotDrawable.setColor(dotColor)
            binding.timelineDot.background = dotDrawable
            binding.lineTop.visibility = View.GONE
            binding.lineBottom.visibility = View.GONE
        }
    }

    inner class EntryViewHolder(
        private val binding: ItemDiaryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.entryCard.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val item = getItem(pos)
                    if (item is TimelineItem.EntryWithDot) {
                        onEntryClick(item.entry)
                    }
                }
            }
        }

        fun bind(item: TimelineItem.EntryWithDot) {
            val entry = item.entry
            val date = entry.toDate()
            binding.dayName.text = dayFormat.format(date).uppercase()
            binding.dayNumber.text = dateFormat.format(date)
            binding.entryContent.text = parsePreview(entry.content)
            applyStyle(item)
        }

        fun applyStyle(item: TimelineItem.EntryWithDot) {
            binding.entryContent.textSize = fontSizeSp
            binding.entryContent.setTextColor(themeColors.textColor)
            binding.dayName.setTextColor(themeColors.accentColor)
            val dow = Calendar.getInstance().apply {
                set(Calendar.YEAR, item.entry.year)
                set(Calendar.MONTH, item.entry.month - 1)
                set(Calendar.DAY_OF_MONTH, item.entry.day)
            }.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
            binding.dayNumber.setTextColor(if (isWeekend) themeColors.accentColor else themeColors.textColor)
            binding.entryCard.setCardBackgroundColor(themeColors.backgroundColor)
            binding.entryCard.strokeColor = themeColors.borderColor
            binding.entryCard.strokeWidth = getBorderWidth(borderStyle)

            dateBoxDrawable.setColor(themeColors.dateBackgroundColor)
            binding.dateBox.background = dateBoxDrawable

            val dotColor = if (item.isToday) themeColors.todayDotColor else themeColors.dotColor
            dotDrawable.setColor(dotColor)
            binding.timelineDot.background = dotDrawable

            binding.lineTop.visibility = View.GONE
            binding.lineBottom.visibility = View.GONE
            binding.entryCard.visibility = View.VISIBLE
        }
    }
}
