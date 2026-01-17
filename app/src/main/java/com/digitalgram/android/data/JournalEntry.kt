package com.digitalgram.android.data

import java.text.SimpleDateFormat
import java.util.*

/**
 * Journal Entry matching DigitalGram macOS SQLite structure
 * Primary key is the date string in YYYY-MM-DD format
 */
data class JournalEntry(
    val date: String,           // YYYY-MM-DD format (primary key)
    val year: Int,
    val month: Int,
    val day: Int,
    val content: String,
    val created: String,        // ISO8601 timestamp
    val updated: String         // ISO8601 timestamp
) {
    companion object {
        private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        
        private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        /**
         * Create a new entry for the given date
         */
        fun create(calendar: Calendar, content: String = ""): JournalEntry {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-indexed
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val dateKey = String.format("%04d-%02d-%02d", year, month, day)
            val now = iso8601Format.format(Date())
            
            return JournalEntry(
                date = dateKey,
                year = year,
                month = month,
                day = day,
                content = content,
                created = now,
                updated = now
            )
        }
        
        /**
         * Create an updated copy with new content
         */
        fun JournalEntry.withUpdatedContent(newContent: String): JournalEntry {
            return copy(
                content = newContent,
                updated = iso8601Format.format(Date())
            )
        }
        
        /**
         * Get date key from a Calendar
         */
        fun getDateKey(calendar: Calendar): String {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            return String.format("%04d-%02d-%02d", year, month, day)
        }
        
        /**
         * Parse date key to Calendar
         */
        fun parseToCalendar(dateKey: String): Calendar {
            val parts = dateKey.split("-")
            return Calendar.getInstance().apply {
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1) // Calendar months are 0-indexed
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }
    
    /**
     * Get the Date object for this entry
     */
    fun toDate(): Date {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return calendar.time
    }
    
    /**
     * Check if this entry has content
     */
    fun hasContent(): Boolean = content.isNotBlank()
}
