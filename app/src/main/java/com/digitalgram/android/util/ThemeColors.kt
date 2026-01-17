package com.digitalgram.android.util

import android.content.Context
import android.graphics.Color
import com.digitalgram.android.R
import com.digitalgram.android.data.AppSettings

/**
 * Theme color definitions for DigitalGram journal app
 */
data class ThemeColors(
    val backgroundColor: Int,
    val textColor: Int,
    val secondaryTextColor: Int,
    val linkColor: Int,
    val accentColor: Int,
    val codeBackgroundColor: Int,
    val borderColor: Int,
    val buttonColor: Int,
    val dotColor: Int,
    val todayDotColor: Int,
    val dateBackgroundColor: Int
) {
    companion object {
        fun getTheme(themeName: String, context: Context? = null): ThemeColors {
            val baseTheme = when (themeName) {
                AppSettings.THEME_LIGHT -> ThemeColors(
                    backgroundColor = Color.parseColor("#FFFFFF"),
                    textColor = Color.parseColor("#1A1A1A"),
                    secondaryTextColor = Color.parseColor("#757575"),
                    linkColor = Color.parseColor("#2196F3"),
                    accentColor = Color.parseColor("#FF6B6B"),
                    codeBackgroundColor = Color.parseColor("#F5F5F5"),
                    borderColor = Color.parseColor("#E0E0E0"),
                    buttonColor = Color.parseColor("#FF6B6B"),
                    dotColor = Color.parseColor("#BDBDBD"),
                    todayDotColor = Color.parseColor("#FF6B6B"),
                    dateBackgroundColor = Color.parseColor("#F0F0F0")
                )
                AppSettings.THEME_DARK -> ThemeColors(
                    backgroundColor = Color.parseColor("#0D1117"),
                    textColor = Color.parseColor("#C9D1D9"),
                    secondaryTextColor = Color.parseColor("#8B949E"),
                    linkColor = Color.parseColor("#58A6FF"),
                    accentColor = Color.parseColor("#F85149"),
                    codeBackgroundColor = Color.parseColor("#161B22"),
                    borderColor = Color.parseColor("#30363D"),
                    buttonColor = Color.parseColor("#F85149"),
                    dotColor = Color.parseColor("#30363D"),
                    todayDotColor = Color.parseColor("#F85149"),
                    dateBackgroundColor = Color.parseColor("#161B22")
                )
                // Light Themes - Mood Based
                AppSettings.THEME_SEPIA -> ThemeColors( // Calm, Nostalgic
                    backgroundColor = Color.parseColor("#F4ECD8"),
                    textColor = Color.parseColor("#5C4B37"),
                    secondaryTextColor = Color.parseColor("#8B7355"),
                    linkColor = Color.parseColor("#A0826D"),
                    accentColor = Color.parseColor("#D4A574"),
                    codeBackgroundColor = Color.parseColor("#E8DCC5"),
                    borderColor = Color.parseColor("#D9C7A8"),
                    buttonColor = Color.parseColor("#C19A6B"),
                    dotColor = Color.parseColor("#C4B59D"),
                    todayDotColor = Color.parseColor("#C19A6B"),
                    dateBackgroundColor = Color.parseColor("#EAE0CF")
                )
                AppSettings.THEME_MINT -> ThemeColors( // Fresh, Focused
                    backgroundColor = Color.parseColor("#F0FFF4"),
                    textColor = Color.parseColor("#22543D"),
                    secondaryTextColor = Color.parseColor("#2F855A"),
                    linkColor = Color.parseColor("#38A169"),
                    accentColor = Color.parseColor("#48BB78"),
                    codeBackgroundColor = Color.parseColor("#E6F7ED"),
                    borderColor = Color.parseColor("#C6F6D5"),
                    buttonColor = Color.parseColor("#48BB78"),
                    dotColor = Color.parseColor("#9AE6B4"),
                    todayDotColor = Color.parseColor("#48BB78"),
                    dateBackgroundColor = Color.parseColor("#E0F7E9")
                )
                AppSettings.THEME_LAVENDER -> ThemeColors( // Creative, Dreamy
                    backgroundColor = Color.parseColor("#FAF5FF"),
                    textColor = Color.parseColor("#44337A"),
                    secondaryTextColor = Color.parseColor("#6B46C1"),
                    linkColor = Color.parseColor("#805AD5"),
                    accentColor = Color.parseColor("#9F7AEA"),
                    codeBackgroundColor = Color.parseColor("#F3EBFF"),
                    borderColor = Color.parseColor("#E9D8FD"),
                    buttonColor = Color.parseColor("#9F7AEA"),
                    dotColor = Color.parseColor("#D6BCFA"),
                    todayDotColor = Color.parseColor("#9F7AEA"),
                    dateBackgroundColor = Color.parseColor("#F0E6FF")
                )
                AppSettings.THEME_PEACH -> ThemeColors( // Warm, Energetic
                    backgroundColor = Color.parseColor("#FFF5F5"),
                    textColor = Color.parseColor("#742A2A"),
                    secondaryTextColor = Color.parseColor("#C53030"),
                    linkColor = Color.parseColor("#E53E3E"),
                    accentColor = Color.parseColor("#FC8181"),
                    codeBackgroundColor = Color.parseColor("#FED7D7"),
                    borderColor = Color.parseColor("#FEB2B2"),
                    buttonColor = Color.parseColor("#FC8181"),
                    dotColor = Color.parseColor("#FEB2B2"),
                    todayDotColor = Color.parseColor("#FC8181"),
                    dateBackgroundColor = Color.parseColor("#FFE8E8")
                )
                AppSettings.THEME_SKY -> ThemeColors( // Clear, Productive
                    backgroundColor = Color.parseColor("#EBF8FF"),
                    textColor = Color.parseColor("#2C5282"),
                    secondaryTextColor = Color.parseColor("#2B6CB0"),
                    linkColor = Color.parseColor("#3182CE"),
                    accentColor = Color.parseColor("#4299E1"),
                    codeBackgroundColor = Color.parseColor("#BEE3F8"),
                    borderColor = Color.parseColor("#90CDF4"),
                    buttonColor = Color.parseColor("#4299E1"),
                    dotColor = Color.parseColor("#90CDF4"),
                    todayDotColor = Color.parseColor("#4299E1"),
                    dateBackgroundColor = Color.parseColor("#D6F0FF")
                )
                // Dark Themes - Mood Based
                AppSettings.THEME_NORD -> ThemeColors( // Cool, Professional
                    backgroundColor = Color.parseColor("#2E3440"),
                    textColor = Color.parseColor("#ECEFF4"),
                    secondaryTextColor = Color.parseColor("#D8DEE9"),
                    linkColor = Color.parseColor("#88C0D0"),
                    accentColor = Color.parseColor("#81A1C1"),
                    codeBackgroundColor = Color.parseColor("#3B4252"),
                    borderColor = Color.parseColor("#4C566A"),
                    buttonColor = Color.parseColor("#5E81AC"),
                    dotColor = Color.parseColor("#4C566A"),
                    todayDotColor = Color.parseColor("#5E81AC"),
                    dateBackgroundColor = Color.parseColor("#2E3440")
                )
                AppSettings.THEME_GRUVBOX -> ThemeColors( // Retro, Comfortable
                    backgroundColor = Color.parseColor("#282828"),
                    textColor = Color.parseColor("#EBDBB2"),
                    secondaryTextColor = Color.parseColor("#D5C4A1"),
                    linkColor = Color.parseColor("#83A598"),
                    accentColor = Color.parseColor("#FB4934"),
                    codeBackgroundColor = Color.parseColor("#3C3836"),
                    borderColor = Color.parseColor("#504945"),
                    buttonColor = Color.parseColor("#FB4934"),
                    dotColor = Color.parseColor("#665C54"),
                    todayDotColor = Color.parseColor("#FB4934"),
                    dateBackgroundColor = Color.parseColor("#32302F")
                )
                AppSettings.THEME_SOLARIZED_DARK -> ThemeColors( // Balanced, Scientific
                    backgroundColor = Color.parseColor("#002B36"),
                    textColor = Color.parseColor("#839496"),
                    secondaryTextColor = Color.parseColor("#657B83"),
                    linkColor = Color.parseColor("#268BD2"),
                    accentColor = Color.parseColor("#DC322F"),
                    codeBackgroundColor = Color.parseColor("#073642"),
                    borderColor = Color.parseColor("#586E75"),
                    buttonColor = Color.parseColor("#DC322F"),
                    dotColor = Color.parseColor("#586E75"),
                    todayDotColor = Color.parseColor("#DC322F"),
                    dateBackgroundColor = Color.parseColor("#073642")
                )
                AppSettings.THEME_ONE_DARK -> ThemeColors( // Modern, Sleek
                    backgroundColor = Color.parseColor("#282C34"),
                    textColor = Color.parseColor("#ABB2BF"),
                    secondaryTextColor = Color.parseColor("#5C6370"),
                    linkColor = Color.parseColor("#61AFEF"),
                    accentColor = Color.parseColor("#E06C75"),
                    codeBackgroundColor = Color.parseColor("#21252B"),
                    borderColor = Color.parseColor("#3E4451"),
                    buttonColor = Color.parseColor("#E06C75"),
                    dotColor = Color.parseColor("#3E4451"),
                    todayDotColor = Color.parseColor("#E06C75"),
                    dateBackgroundColor = Color.parseColor("#21252B")
                )
                AppSettings.THEME_DRACULA -> ThemeColors( // Vibrant, Mysterious
                    backgroundColor = Color.parseColor("#282A36"),
                    textColor = Color.parseColor("#F8F8F2"),
                    secondaryTextColor = Color.parseColor("#6272A4"),
                    linkColor = Color.parseColor("#8BE9FD"),
                    accentColor = Color.parseColor("#FF79C6"),
                    codeBackgroundColor = Color.parseColor("#44475A"),
                    borderColor = Color.parseColor("#6272A4"),
                    buttonColor = Color.parseColor("#FF79C6"),
                    dotColor = Color.parseColor("#44475A"),
                    todayDotColor = Color.parseColor("#FF79C6"),
                    dateBackgroundColor = Color.parseColor("#383A59")
                )
                AppSettings.THEME_AMOLED -> ThemeColors( // Pure Black, Battery Saving
                    backgroundColor = Color.parseColor("#000000"),
                    textColor = Color.parseColor("#FFFFFF"),
                    secondaryTextColor = Color.parseColor("#888888"),
                    linkColor = Color.parseColor("#4CAF50"),
                    accentColor = Color.parseColor("#FF6B6B"),
                    codeBackgroundColor = Color.parseColor("#0A0A0A"),
                    borderColor = Color.parseColor("#1A1A1A"),
                    buttonColor = Color.parseColor("#FF6B6B"),
                    dotColor = Color.parseColor("#333333"),
                    todayDotColor = Color.parseColor("#FF6B6B"),
                    dateBackgroundColor = Color.parseColor("#0D0D0D")
                )
                AppSettings.THEME_ROSE_GOLD -> ThemeColors( // Elegant, Luxurious
                    backgroundColor = Color.parseColor("#FFF5F7"),
                    textColor = Color.parseColor("#5D4037"),
                    secondaryTextColor = Color.parseColor("#8D6E63"),
                    linkColor = Color.parseColor("#D4A5A5"),
                    accentColor = Color.parseColor("#E8B4B8"),
                    codeBackgroundColor = Color.parseColor("#FDEAEC"),
                    borderColor = Color.parseColor("#F4D7D9"),
                    buttonColor = Color.parseColor("#E8B4B8"),
                    dotColor = Color.parseColor("#F4C7C9"),
                    todayDotColor = Color.parseColor("#E8B4B8"),
                    dateBackgroundColor = Color.parseColor("#FEF0F2")
                )
                AppSettings.THEME_CHERRY_BLOSSOM -> ThemeColors( // Delicate, Spring-like
                    backgroundColor = Color.parseColor("#FFF0F5"),
                    textColor = Color.parseColor("#4A1942"),
                    secondaryTextColor = Color.parseColor("#8E4585"),
                    linkColor = Color.parseColor("#D8A7C5"),
                    accentColor = Color.parseColor("#FFB7D5"),
                    codeBackgroundColor = Color.parseColor("#FFE4EC"),
                    borderColor = Color.parseColor("#FFD6E8"),
                    buttonColor = Color.parseColor("#FFB7D5"),
                    dotColor = Color.parseColor("#FFC9E1"),
                    todayDotColor = Color.parseColor("#FFB7D5"),
                    dateBackgroundColor = Color.parseColor("#FFF5FA")
                )
                AppSettings.THEME_OCEAN_BREEZE -> ThemeColors( // Calm, Serene
                    backgroundColor = Color.parseColor("#E8F4F8"),
                    textColor = Color.parseColor("#004D61"),
                    secondaryTextColor = Color.parseColor("#006B7D"),
                    linkColor = Color.parseColor("#0097A7"),
                    accentColor = Color.parseColor("#00BCD4"),
                    codeBackgroundColor = Color.parseColor("#D1E9EE"),
                    borderColor = Color.parseColor("#B2D9E1"),
                    buttonColor = Color.parseColor("#00BCD4"),
                    dotColor = Color.parseColor("#80DEEA"),
                    todayDotColor = Color.parseColor("#00BCD4"),
                    dateBackgroundColor = Color.parseColor("#D8EDF2")
                )
                AppSettings.THEME_SUNSET -> ThemeColors( // Warm, Romantic
                    backgroundColor = Color.parseColor("#FFF4E6"),
                    textColor = Color.parseColor("#5D2E0D"),
                    secondaryTextColor = Color.parseColor("#8B4513"),
                    linkColor = Color.parseColor("#FF7043"),
                    accentColor = Color.parseColor("#FF8A65"),
                    codeBackgroundColor = Color.parseColor("#FFE4D1"),
                    borderColor = Color.parseColor("#FFD4B8"),
                    buttonColor = Color.parseColor("#FF8A65"),
                    dotColor = Color.parseColor("#FFAB91"),
                    todayDotColor = Color.parseColor("#FF8A65"),
                    dateBackgroundColor = Color.parseColor("#FFF0E0")
                )
                AppSettings.THEME_FOREST -> ThemeColors( // Natural, Grounded
                    backgroundColor = Color.parseColor("#F1F8F4"),
                    textColor = Color.parseColor("#1B5E20"),
                    secondaryTextColor = Color.parseColor("#2E7D32"),
                    linkColor = Color.parseColor("#4CAF50"),
                    accentColor = Color.parseColor("#66BB6A"),
                    codeBackgroundColor = Color.parseColor("#E0F2E5"),
                    borderColor = Color.parseColor("#C8E6C9"),
                    buttonColor = Color.parseColor("#66BB6A"),
                    dotColor = Color.parseColor("#81C784"),
                    todayDotColor = Color.parseColor("#66BB6A"),
                    dateBackgroundColor = Color.parseColor("#E5F5E9")
                )
                AppSettings.THEME_MIDNIGHT_PURPLE -> ThemeColors( // Mystical, Elegant
                    backgroundColor = Color.parseColor("#1A0F2E"),
                    textColor = Color.parseColor("#E8DFF5"),
                    secondaryTextColor = Color.parseColor("#B8A9D1"),
                    linkColor = Color.parseColor("#9C7FC8"),
                    accentColor = Color.parseColor("#BB86FC"),
                    codeBackgroundColor = Color.parseColor("#2D1B4E"),
                    borderColor = Color.parseColor("#4A2F6E"),
                    buttonColor = Color.parseColor("#BB86FC"),
                    dotColor = Color.parseColor("#5A3F7E"),
                    todayDotColor = Color.parseColor("#BB86FC"),
                    dateBackgroundColor = Color.parseColor("#251540")
                )
                AppSettings.THEME_CUSTOM -> {
                    if (context != null) {
                        val settings = AppSettings.getInstance(context)
                        ThemeColors(
                            backgroundColor = settings.customBackgroundColor,
                            textColor = settings.customTextColor,
                            secondaryTextColor = settings.customSecondaryTextColor,
                            linkColor = settings.customLinkColor,
                            accentColor = settings.customAccentColor,
                            codeBackgroundColor = settings.customCodeBackgroundColor,
                            borderColor = settings.customBorderColor,
                            buttonColor = settings.customButtonColor,
                            dotColor = settings.customDotColor,
                            todayDotColor = settings.customTodayDotColor,
                            dateBackgroundColor = settings.customDateBackgroundColor
                        )
                    } else {
                        // Fallback to light theme if context not available
                        getTheme(AppSettings.THEME_LIGHT, null)
                    }
                }
                else -> ThemeColors( // DEFAULT - Light theme
                    backgroundColor = Color.parseColor("#FFFFFF"),
                    textColor = Color.parseColor("#1A1A1A"),
                    secondaryTextColor = Color.parseColor("#757575"),
                    linkColor = Color.parseColor("#2196F3"),
                    accentColor = Color.parseColor("#FF6B6B"),
                    codeBackgroundColor = Color.parseColor("#F5F5F5"),
                    borderColor = Color.parseColor("#E0E0E0"),
                    buttonColor = Color.parseColor("#FF6B6B"),
                    dotColor = Color.parseColor("#BDBDBD"),
                    todayDotColor = Color.parseColor("#FF6B6B"),
                    dateBackgroundColor = Color.parseColor("#F0F0F0")
                )
            }
            
            // Apply custom overrides if context is available and theme has customizations
            if (context != null && themeName != AppSettings.THEME_CUSTOM) {
                val settings = AppSettings.getInstance(context)
                if (settings.hasThemeCustomColors(themeName)) {
                    return applyCustomOverrides(baseTheme, settings, themeName)
                }
            }
            
            return baseTheme
        }
        
        private fun applyCustomOverrides(base: ThemeColors, settings: AppSettings, themeName: String): ThemeColors {
            return ThemeColors(
                backgroundColor = settings.getThemeCustomColor(themeName, "bg") ?: base.backgroundColor,
                textColor = settings.getThemeCustomColor(themeName, "text") ?: base.textColor,
                secondaryTextColor = settings.getThemeCustomColor(themeName, "secondaryText") ?: base.secondaryTextColor,
                linkColor = settings.getThemeCustomColor(themeName, "link") ?: base.linkColor,
                accentColor = settings.getThemeCustomColor(themeName, "accent") ?: base.accentColor,
                codeBackgroundColor = settings.getThemeCustomColor(themeName, "codeBg") ?: base.codeBackgroundColor,
                borderColor = settings.getThemeCustomColor(themeName, "border") ?: base.borderColor,
                buttonColor = settings.getThemeCustomColor(themeName, "button") ?: base.buttonColor,
                dotColor = settings.getThemeCustomColor(themeName, "dot") ?: base.dotColor,
                todayDotColor = settings.getThemeCustomColor(themeName, "todayDot") ?: base.todayDotColor,
                dateBackgroundColor = settings.getThemeCustomColor(themeName, "dateBg") ?: base.dateBackgroundColor
            )
        }
        
        /**
         * Get the default (non-customized) theme colors
         */
        fun getDefaultTheme(themeName: String): ThemeColors {
            return getTheme(themeName, null)
        }
        
        fun isDarkTheme(themeName: String): Boolean {
            return themeName in listOf(
                AppSettings.THEME_DARK,
                AppSettings.THEME_NORD,
                AppSettings.THEME_GRUVBOX,
                AppSettings.THEME_SOLARIZED_DARK,
                AppSettings.THEME_ONE_DARK,
                AppSettings.THEME_DRACULA,
                AppSettings.THEME_AMOLED,
                AppSettings.THEME_MIDNIGHT_PURPLE
            )
        }
    }
}
