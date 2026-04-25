package com.digitalgram.android.util

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.*
import android.view.View

/**
 * Custom URLSpan that normalizes URLs before opening
 */
class CustomURLSpan(private val url: String) : URLSpan(url) {
    override fun onClick(widget: View) {
        val context = widget.context
        val normalizedUrl = normalizeUrl(url)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        
        // If URL doesn't have a protocol, add https://
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        
        return normalized
    }
}

/**
 * Markdown parser for rendering markdown text to styled SpannableString
 */
object MarkdownParser {
    
    fun parse(text: String, linkColor: Int, codeBackgroundColor: Int, textColor: Int, accentColor: Int = linkColor): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        
        // Process block-level elements first
        processCheckboxes(builder, accentColor)
        processBulletPoints(builder)
        processHeaders(builder)
        processBlockquotes(builder, textColor)
        
        // Process inline elements
        processLinks(builder, linkColor)
        processInlineCode(builder, codeBackgroundColor)
        processStrikethrough(builder)
        processBold(builder)
        processItalic(builder)
        
        return builder
    }
    
    private fun processCheckboxes(builder: SpannableStringBuilder, accentColor: Int) {
        // Process unchecked checkboxes: - [ ] item
        var offset = 0
        var text = builder.toString()
        val uncheckedPattern = Regex("^- \\[ ] ", RegexOption.MULTILINE)
        var match = uncheckedPattern.find(text, offset)
        
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            
            // Replace "- [ ] " with "☐ "
            builder.replace(start, end, "☐ ")
            
            // Apply larger size and accent color to checkbox
            val checkboxEnd = start + 2 // "☐ " is 2 characters
            builder.setSpan(
                RelativeSizeSpan(1.4f), // Make checkbox 40% larger
                start,
                checkboxEnd - 1, // Just the checkbox character
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                ForegroundColorSpan(accentColor),
                start,
                checkboxEnd - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            offset = checkboxEnd
            text = builder.toString()
            match = uncheckedPattern.find(text, offset)
        }
        
        // Process checked checkboxes: - [x] item or - [X] item
        offset = 0
        text = builder.toString()
        val checkedPattern = Regex("^- \\[[xX]] ", RegexOption.MULTILINE)
        match = checkedPattern.find(text, offset)
        
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            
            // Replace "- [x] " with "☑ "
            builder.replace(start, end, "☑ ")
            
            // Apply larger size and accent color to checkbox
            val checkboxEnd = start + 2 // "☑ " is 2 characters
            builder.setSpan(
                RelativeSizeSpan(1.4f), // Make checkbox 40% larger
                start,
                checkboxEnd - 1, // Just the checkbox character
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                ForegroundColorSpan(accentColor),
                start,
                checkboxEnd - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            offset = checkboxEnd
            text = builder.toString()
            match = checkedPattern.find(text, offset)
        }
    }
    
    private fun processBulletPoints(builder: SpannableStringBuilder) {
        // Process bullet points: - item (but not checkboxes which are already converted)
        var offset = 0
        var text = builder.toString()
        val bulletPattern = Regex("^- (?!\\[)", RegexOption.MULTILINE)
        var match = bulletPattern.find(text, offset)
        
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            
            // Replace "- " with "• "
            builder.replace(start, end, "• ")
            
            offset = start + 2
            text = builder.toString()
            match = bulletPattern.find(text, offset)
        }
        
        // Also handle * bullet points
        offset = 0
        text = builder.toString()
        val starBulletPattern = Regex("^\\* (?![*])", RegexOption.MULTILINE)
        match = starBulletPattern.find(text, offset)
        
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            
            // Replace "* " with "• "
            builder.replace(start, end, "• ")
            
            offset = start + 2
            text = builder.toString()
            match = starBulletPattern.find(text, offset)
        }
    }
    
    private fun processHeaders(builder: SpannableStringBuilder) {
        val text = builder.toString()
        val lines = text.split("\n")
        var offset = 0
        
        for (line in lines) {
            val lineStart = offset
            val lineEnd = offset + line.length
            
            when {
                line.startsWith("### ") -> {
                    builder.replace(lineStart, lineStart + 4, "")
                    val newEnd = lineEnd - 4
                    builder.setSpan(
                        RelativeSizeSpan(1.2f),
                        lineStart,
                        newEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        lineStart,
                        newEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    offset = newEnd
                }
                line.startsWith("## ") -> {
                    builder.replace(lineStart, lineStart + 3, "")
                    val newEnd = lineEnd - 3
                    builder.setSpan(
                        RelativeSizeSpan(1.4f),
                        lineStart,
                        newEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        lineStart,
                        newEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    offset = newEnd
                }
                line.startsWith("# ") -> {
                    builder.replace(lineStart, lineStart + 2, "")
                    val newEnd = lineEnd - 2
                    builder.setSpan(
                        RelativeSizeSpan(1.6f),
                        lineStart,
                        newEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        lineStart,
                        newEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    offset = newEnd
                }
                else -> {
                    offset = lineEnd
                }
            }
            // Account for newline
            if (offset < builder.length && builder[offset] == '\n') {
                offset++
            }
        }
    }
    
    private fun processBlockquotes(builder: SpannableStringBuilder, textColor: Int) {
        val text = builder.toString()
        val lines = text.split("\n")
        var offset = 0
        
        for (line in lines) {
            val lineStart = offset
            val lineEnd = offset + line.length
            
            if (line.startsWith("> ")) {
                builder.replace(lineStart, lineStart + 2, "")
                val newEnd = lineEnd - 2
                builder.setSpan(
                    QuoteSpan(textColor),
                    lineStart,
                    newEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    lineStart,
                    newEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                offset = newEnd
            } else {
                offset = lineEnd
            }
            
            // Account for newline
            if (offset < builder.length && builder[offset] == '\n') {
                offset++
            }
        }
    }
    
    private fun processBold(builder: SpannableStringBuilder) {
        // Process bold text (**text**)
        val pattern = Regex("\\*\\*([^*]+)\\*\\*")
        var offset = 0
        var currentText = builder.toString()
        var match = pattern.find(currentText, offset)
        
        while (match != null) {
            val fullMatch = match.range
            val contentGroup = match.groups[1]!!
            val adjustedStart = fullMatch.first
            val adjustedEnd = fullMatch.last + 1
            
            // Remove the ** markers - delete from end first to preserve positions
            builder.delete(adjustedEnd - 2, adjustedEnd)  // Remove trailing **
            builder.delete(adjustedStart, adjustedStart + 2) // Remove leading **
            
            // Apply bold style to the content (now at adjustedStart)
            val contentEnd = adjustedStart + contentGroup.value.length
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                adjustedStart,
                contentEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Update offset to continue after this match
            offset = contentEnd
            currentText = builder.toString()
            match = pattern.find(currentText, offset)
        }
    }
    
    private fun processItalic(builder: SpannableStringBuilder) {
        // Process italic text (*text* or _text_)
        val pattern = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|_([^_]+)_")
        var offset = 0
        var currentText = builder.toString()
        var match = pattern.find(currentText, offset)
        
        while (match != null) {
            val fullMatch = match.range
            val contentGroup = match.groups[1] ?: match.groups[2]
            
            if (contentGroup != null) {
                val adjustedStart = fullMatch.first
                val adjustedEnd = fullMatch.last + 1
                
                // Remove the markers - delete from end first
                builder.delete(adjustedEnd - 1, adjustedEnd)  // Remove trailing marker
                builder.delete(adjustedStart, adjustedStart + 1) // Remove leading marker
                
                // Apply italic style
                val contentEnd = adjustedStart + contentGroup.value.length
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    adjustedStart,
                    contentEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                offset = contentEnd
            }
            
            // Find next match
            currentText = builder.toString()
            match = pattern.find(currentText, offset)
        }
    }
    
    private fun processStrikethrough(builder: SpannableStringBuilder) {
        // Process strikethrough text (~~text~~)
        val pattern = Regex("~~([^~]+)~~")
        var offset = 0
        var currentText = builder.toString()
        var match = pattern.find(currentText, offset)
        
        while (match != null) {
            val fullMatch = match.range
            val contentGroup = match.groups[1]!!
            val adjustedStart = fullMatch.first
            val adjustedEnd = fullMatch.last + 1
            
            // Remove the ~~ markers - delete from end first
            builder.delete(adjustedEnd - 2, adjustedEnd)  // Remove trailing ~~
            builder.delete(adjustedStart, adjustedStart + 2) // Remove leading ~~
            
            // Apply strikethrough style
            val contentEnd = adjustedStart + contentGroup.value.length
            builder.setSpan(
                StrikethroughSpan(),
                adjustedStart,
                contentEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            offset = contentEnd
            currentText = builder.toString()
            match = pattern.find(currentText, offset)
        }
    }
    
    private fun processInlineCode(builder: SpannableStringBuilder, codeBackgroundColor: Int) {
        // Process inline code (`code`)
        val pattern = Regex("`([^`]+)`")
        var offset = 0
        var currentText = builder.toString()
        var match = pattern.find(currentText, offset)
        
        while (match != null) {
            val fullMatch = match.range
            val contentGroup = match.groups[1]!!
            val adjustedStart = fullMatch.first
            val adjustedEnd = fullMatch.last + 1
            
            // Remove the ` markers - delete from end first
            builder.delete(adjustedEnd - 1, adjustedEnd)  // Remove trailing `
            builder.delete(adjustedStart, adjustedStart + 1) // Remove leading `
            
            // Apply code styling
            val contentEnd = adjustedStart + contentGroup.value.length
            builder.setSpan(
                BackgroundColorSpan(codeBackgroundColor),
                adjustedStart,
                contentEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                TypefaceSpan("monospace"),
                adjustedStart,
                contentEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            offset = contentEnd
            currentText = builder.toString()
            match = pattern.find(currentText, offset)
        }
    }
    
    private fun processLinks(builder: SpannableStringBuilder, linkColor: Int) {
        // Process links [text](url)
        val pattern = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
        var offset = 0
        var currentText = builder.toString()
        var match = pattern.find(currentText, offset)
        
        while (match != null) {
            val fullMatch = match.range
            val textGroup = match.groups[1]!!
            val urlGroup = match.groups[2]!!
            val adjustedStart = fullMatch.first
            val adjustedEnd = fullMatch.last + 1
            
            // Replace [text](url) with just text
            builder.replace(adjustedStart, adjustedEnd, textGroup.value)
            
            // Apply link styling to the text
            val textEnd = adjustedStart + textGroup.value.length
            builder.setSpan(
                ForegroundColorSpan(linkColor),
                adjustedStart,
                textEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                UnderlineSpan(),
                adjustedStart,
                textEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Add clickable span with normalized URL
            builder.setSpan(
                CustomURLSpan(urlGroup.value),
                adjustedStart,
                textEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            offset = textEnd
            currentText = builder.toString()
            match = pattern.find(currentText, offset)
        }
    }
}
