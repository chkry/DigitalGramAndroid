package com.digitalgram.android.util

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.*

/**
 * Markdown parser for rendering markdown text to styled SpannableString
 */
object MarkdownParser {
    
    fun parse(text: String, linkColor: Int, codeBackgroundColor: Int, textColor: Int): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        
        // Process block-level elements first
        processCheckboxes(builder)
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
    
    private fun processCheckboxes(builder: SpannableStringBuilder) {
        // Process unchecked checkboxes: - [ ] item
        var text = builder.toString()
        val uncheckedPattern = Regex("^- \\[ ] ", RegexOption.MULTILINE)
        var match = uncheckedPattern.find(text)
        
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            
            // Replace "- [ ] " with "☐ "
            builder.replace(start, end, "☐ ")
            
            text = builder.toString()
            match = uncheckedPattern.find(text)
        }
        
        // Process checked checkboxes: - [x] item or - [X] item
        text = builder.toString()
        val checkedPattern = Regex("^- \\[[xX]] ", RegexOption.MULTILINE)
        match = checkedPattern.find(text)
        
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            
            // Replace "- [x] " with "☑ "
            builder.replace(start, end, "☑ ")
            
            text = builder.toString()
            match = checkedPattern.find(text)
        }
    }
    
    private fun processBulletPoints(builder: SpannableStringBuilder) {
        // Process bullet points: - item (but not checkboxes which are already converted)
        var text = builder.toString()
        val bulletPattern = Regex("^- (?!\\[)", RegexOption.MULTILINE)
        var match = bulletPattern.find(text)
        
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            
            // Replace "- " with "• "
            builder.replace(start, end, "• ")
            
            text = builder.toString()
            match = bulletPattern.find(text)
        }
        
        // Also handle * bullet points
        text = builder.toString()
        val starBulletPattern = Regex("^\\* (?![*])", RegexOption.MULTILINE)
        match = starBulletPattern.find(text)
        
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            
            // Replace "* " with "• "
            builder.replace(start, end, "• ")
            
            text = builder.toString()
            match = starBulletPattern.find(text)
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
        var match = pattern.find(builder.toString())
        
        while (match != null) {
            val fullMatch = match.range
            val contentGroup = match.groups[1]!!
            
            // Remove the ** markers
            builder.delete(fullMatch.last - 1, fullMatch.last + 1)  // Remove trailing **
            builder.delete(fullMatch.first, fullMatch.first + 2) // Remove leading **
            
            // Apply bold style to the content (now at fullMatch.first)
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                fullMatch.first,
                fullMatch.first + contentGroup.value.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Find next match
            match = pattern.find(builder.toString())
        }
    }
    
    private fun processItalic(builder: SpannableStringBuilder) {
        // Process italic text (*text* or _text_)
        val pattern = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|_([^_]+)_")
        var match = pattern.find(builder.toString())
        
        while (match != null) {
            val fullMatch = match.range
            val contentGroup = match.groups[1] ?: match.groups[2]
            
            if (contentGroup != null) {
                // Remove the markers
                builder.delete(fullMatch.last, fullMatch.last + 1)  // Remove trailing marker
                builder.delete(fullMatch.first, fullMatch.first + 1) // Remove leading marker
                
                // Apply italic style
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    fullMatch.first,
                    fullMatch.first + contentGroup.value.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            // Find next match
            match = pattern.find(builder.toString())
        }
    }
    
    private fun processStrikethrough(builder: SpannableStringBuilder) {
        // Process strikethrough text (~~text~~)
        val pattern = Regex("~~([^~]+)~~")
        var match = pattern.find(builder.toString())
        
        while (match != null) {
            val fullMatch = match.range
            val contentGroup = match.groups[1]!!
            
            // Remove the ~~ markers
            builder.delete(fullMatch.last - 1, fullMatch.last + 1)  // Remove trailing ~~
            builder.delete(fullMatch.first, fullMatch.first + 2) // Remove leading ~~
            
            // Apply strikethrough style
            builder.setSpan(
                StrikethroughSpan(),
                fullMatch.first,
                fullMatch.first + contentGroup.value.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Find next match
            match = pattern.find(builder.toString())
        }
    }
    
    private fun processInlineCode(builder: SpannableStringBuilder, codeBackgroundColor: Int) {
        // Process inline code (`code`)
        val pattern = Regex("`([^`]+)`")
        var match = pattern.find(builder.toString())
        
        while (match != null) {
            val fullMatch = match.range
            val contentGroup = match.groups[1]!!
            
            // Remove the ` markers
            builder.delete(fullMatch.last, fullMatch.last + 1)  // Remove trailing `
            builder.delete(fullMatch.first, fullMatch.first + 1) // Remove leading `
            
            // Apply code styling
            builder.setSpan(
                BackgroundColorSpan(codeBackgroundColor),
                fullMatch.first,
                fullMatch.first + contentGroup.value.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                TypefaceSpan("monospace"),
                fullMatch.first,
                fullMatch.first + contentGroup.value.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Find next match
            match = pattern.find(builder.toString())
        }
    }
    
    private fun processLinks(builder: SpannableStringBuilder, linkColor: Int) {
        // Process links [text](url)
        val pattern = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
        var match = pattern.find(builder.toString())
        
        while (match != null) {
            val fullMatch = match.range
            val textGroup = match.groups[1]!!
            val urlGroup = match.groups[2]!!
            
            // Replace [text](url) with just text
            builder.replace(fullMatch.first, fullMatch.last + 1, textGroup.value)
            
            // Apply link styling to the text
            builder.setSpan(
                ForegroundColorSpan(linkColor),
                fullMatch.first,
                fullMatch.first + textGroup.value.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                UnderlineSpan(),
                fullMatch.first,
                fullMatch.first + textGroup.value.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Optional: Add clickable span with URL
            builder.setSpan(
                android.text.style.URLSpan(urlGroup.value),
                fullMatch.first,
                fullMatch.first + textGroup.value.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Find next match
            match = pattern.find(builder.toString())
        }
    }
}
