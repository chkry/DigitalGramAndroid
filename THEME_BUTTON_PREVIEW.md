# Settings Button Theme Preview

## Overview
Settings buttons now dynamically match each theme's accent color. This creates a cohesive visual experience where the selected buttons use the theme's accent color and unselected buttons use a muted secondary color.

## Implementation Details

### Circle Buttons (Font Size & Border Style)
- **Selected State**: Theme's `accentColor` 
- **Unselected State**: Theme's `secondaryTextColor` (muted gray)
- **Shape**: Circular (GradientDrawable.OVAL)

### Toggle Buttons (On/Off switches)
- **Selected State**: Theme's `accentColor`
- **Unselected State**: Theme's `secondaryTextColor` (muted gray)
- **Shape**: Rounded rectangle (20dp corner radius)

---

## Theme Examples

### Light Theme
- **Accent Color**: `#2196F3` (Blue)
- **Secondary Color**: `#757575` (Gray)
- **Selected Buttons**: Bright blue circles/rectangles
- **Unselected Buttons**: Subtle gray circles/rectangles
- **Visual**: Clean, modern look with blue highlights

### Dark Theme
- **Accent Color**: `#64B5F6` (Light Blue)
- **Secondary Color**: `#9E9E9E` (Light Gray)
- **Selected Buttons**: Soft light blue
- **Unselected Buttons**: Medium gray
- **Visual**: Easy on eyes in dark mode, blue accents stand out

### Lavender Theme
- **Accent Color**: `#9C87C4` (Purple)
- **Secondary Color**: `#8F8599` (Muted Purple-Gray)
- **Selected Buttons**: Elegant purple
- **Unselected Buttons**: Subtle purple-tinted gray
- **Visual**: Sophisticated, calming purple aesthetic

### Mint Theme
- **Accent Color**: `#4DB6AC` (Teal)
- **Secondary Color**: `#6B8E8A` (Muted Teal)
- **Selected Buttons**: Fresh mint/teal
- **Unselected Buttons**: Soft teal-gray
- **Visual**: Refreshing, nature-inspired green-blue

### Peach Theme
- **Accent Color**: `#FF8A65` (Coral)
- **Secondary Color**: `#A18674` (Muted Peach)
- **Selected Buttons**: Warm coral/peach
- **Unselected Buttons**: Soft peachy-brown
- **Visual**: Warm, inviting peachy tones

### Sky Theme
- **Accent Color**: `#4FC3F7` (Sky Blue)
- **Secondary Color**: `#6B92A3` (Muted Sky)
- **Selected Buttons**: Bright sky blue
- **Unselected Buttons**: Soft slate blue
- **Visual**: Airy, open sky feeling

### Nord Theme
- **Accent Color**: `#81A1C1` (Nordic Blue)
- **Secondary Color**: `#717C8C` (Nord Gray)
- **Selected Buttons**: Cool Nordic blue
- **Unselected Buttons**: Subtle Nord gray
- **Visual**: Minimalist, Scandinavian aesthetic

### Gruvbox Theme
- **Accent Color**: `#B8BB26` (Yellow-Green)
- **Secondary Color**: `#928374` (Gruvbox Gray)
- **Selected Buttons**: Retro yellow-green
- **Unselected Buttons**: Warm brownish-gray
- **Visual**: Vintage terminal vibes

### Solarized Dark Theme
- **Accent Color**: `#268BD2` (Solarized Blue)
- **Secondary Color**: `#657B83` (Solarized Gray)
- **Selected Buttons**: Classic Solarized blue
- **Unselected Buttons**: Muted teal-gray
- **Visual**: Timeless, well-balanced colors

### Dracula Theme
- **Accent Color**: `#BD93F9` (Purple)
- **Secondary Color**: `#7B7A93` (Muted Purple)
- **Selected Buttons**: Vibrant Dracula purple
- **Unselected Buttons**: Dark purple-gray
- **Visual**: Bold, dramatic purple theme

### AMOLED Theme
- **Accent Color**: `#BB86FC` (Purple)
- **Secondary Color**: `#888888` (Gray)
- **Selected Buttons**: Bright purple on black
- **Unselected Buttons**: Medium gray on black
- **Visual**: High contrast for OLED screens

### Rose Gold Theme
- **Accent Color**: `#E6B8A2` (Rose Gold)
- **Secondary Color**: `#A89585` (Muted Rose)
- **Selected Buttons**: Elegant rose gold
- **Unselected Buttons**: Soft beige-brown
- **Visual**: Luxurious, feminine aesthetic

### Cherry Blossom Theme
- **Accent Color**: `#FFB7C5` (Pink)
- **Secondary Color**: `#B8939A` (Muted Pink)
- **Selected Buttons**: Soft cherry blossom pink
- **Unselected Buttons**: Dusty rose-gray
- **Visual**: Delicate, spring-like pink tones

### Ocean Breeze Theme
- **Accent Color**: `#4DD0E1` (Cyan)
- **Secondary Color**: `#6B969E` (Muted Cyan)
- **Selected Buttons**: Fresh ocean cyan
- **Unselected Buttons**: Soft sea-gray
- **Visual**: Refreshing ocean colors

### Sunset Theme
- **Accent Color**: `#FFB74D` (Orange)
- **Secondary Color**: `#B38F6D` (Muted Orange)
- **Selected Buttons**: Warm sunset orange
- **Unselected Buttons**: Soft sandy-brown
- **Visual**: Warm evening glow

### Forest Theme
- **Accent Color**: `#81C784` (Green)
- **Secondary Color**: `#7A9A7C` (Forest Gray)
- **Selected Buttons**: Fresh forest green
- **Unselected Buttons**: Muted moss-gray
- **Visual**: Natural woodland greens

### Midnight Purple Theme
- **Accent Color**: `#9575CD` (Purple)
- **Secondary Color**: `#847893` (Muted Purple)
- **Selected Buttons**: Deep midnight purple
- **Unselected Buttons**: Soft purple-gray
- **Visual**: Mysterious nighttime purple

### Sepia Theme
- **Accent Color**: `#8D6E63` (Brown)
- **Secondary Color**: `#7D6F69` (Sepia Gray)
- **Selected Buttons**: Vintage sepia brown
- **Unselected Buttons**: Muted sepia-gray
- **Visual**: Classic photograph aesthetic

---

## User Experience Benefits

1. **Visual Consistency**: Buttons integrate seamlessly with each theme's color palette
2. **Clear Selection State**: Accent color makes selected options immediately obvious
3. **Reduced Cognitive Load**: Color-coded selections are faster to process than shape alone
4. **Aesthetic Cohesion**: Entire settings screen feels unified and thoughtfully designed
5. **Personalization**: Each theme has a unique personality that extends to interactive elements

## Technical Approach

Instead of static gray/black XML drawables, buttons are now created programmatically:

```kotlin
// Selected button - uses theme accent color
val selectedDrawable = GradientDrawable()
selectedDrawable.shape = GradientDrawable.OVAL
selectedDrawable.setColor(theme.accentColor)

// Unselected button - uses muted secondary color
val unselectedDrawable = GradientDrawable()
unselectedDrawable.shape = GradientDrawable.OVAL
unselectedDrawable.setColor(theme.secondaryTextColor)
```

This allows each theme to showcase its unique accent color in the settings UI, creating a polished and cohesive user experience.
