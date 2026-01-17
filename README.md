# DigitalGram Android

A beautiful, privacy-focused diary/journal app for Android with Material Design 3 and elegant theming.

## Features

### Core Functionality
- 📝 **Rich Text Editing** - Create and edit diary entries with markdown preview
- 📅 **Date-based Organization** - Browse entries by date with intuitive navigation
- 🔍 **Smart Search** - Find entries quickly by content
- 💾 **Local Storage** - Your data stays on your device with Room database
- 🔒 **Biometric Security** - Optional fingerprint/face unlock
- 📤 **Import/Export** - Backup your data with JSON export/import
- 🗄️ **Database Management** - Switch, merge, delete, and import databases

### Customization
- 🎨 **20 Themes** - Light, Dark, Sepia, Mint, Purple, Peach, Gray, Frost, Cyber, Lab, AMOLED, Vampire, Rose Gold, Cherry Blossom, Ocean Breeze, Sunset, Forest, Midnight Purple
- 🖼️ **Wallpaper Support** - Set custom backgrounds with transparency
- ⚙️ **Per-Theme Settings** - Customize accent colors, fonts, and backgrounds
- 🔤 **Font Selection** - Choose from multiple font families
- 💾 **Auto-save** - Never lose your work with automatic saving

### User Experience
- 🕐 **Real-time Date/Time** - Current date and time displayed in title bar
- 📱 **Edge-to-edge Display** - Modern full-screen experience
- 🎯 **Material Design 3** - Beautiful, consistent UI
- 🌓 **Dark Mode Support** - Comfortable viewing in any lighting

## Technical Stack

- **Language**: Kotlin 1.9.24
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 35
- **Architecture**: MVVM with LiveData
- **Database**: Room 2.6.1
- **UI**: Material Design 3 (Material Components 1.11.0)
- **Build System**: Gradle 8.7 with Kotlin DSL
- **Security**: AndroidX Biometric
- **Storage**: Android Storage Access Framework (SAF)
- **JSON**: Gson for data serialization

## Building the APK

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 34

### Build Instructions

#### Option 1: Using Android Studio

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. The APK will be generated in `app/build/outputs/apk/release/`

#### Option 2: Using Command Line

```bash
cd /Users/chkry/Documents/CODE/DIGITALGRAM_ANDROID

# Make gradlew executable
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Build release APK (unsigned)
./gradlew assembleRelease
```

The APK will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

#### Option 3: Build Signed Release APK

1. Create a keystore:
```bash
keytool -genkey -v -keystore digitalgram.keystore -alias digitalgram -keyalg RSA -keysize 2048 -validity 10000
```

2. Add to `app/build.gradle.kts` in the `android` block:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../digitalgram.keystore")
        storePassword = "your_password"
        keyAlias = "digitalgram"
        keyPassword = "your_password"
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ... rest of config
    }
}
```

3. Build:
```bash
./gradlew assembleRelease
```

## Database Schema

### DiaryEntry Table(milliseconds) of entry date |
| content | String | Diary entry text content |
| createdAt | Long | Creation timestamp (milliseconds) |
| updatedAt | Long | Last update timestamp (milliseconds) |

## Key Features Detail

### Theme System
- **20 Built-in Themes**: Each with carefully selected color palettes
- **Custom Overrides**: Per-theme customization of:
  -Default Theme Design

- **Background**: Beige (#F5F1E8)
- **Surface**: White (#FFFFFF)
- **Primary**: Dark gray (#333333)
- **Accent**: Red (#B85450) for dates and actions
- **Border**: Black 2dp borders on cards
- **Typography**: Default Android sans-serif
- **Cards**: Date header with striped background + content area
- **Transparency**: 70% opacity for entries when wallpaper is activeary
- **Local Storage**: All data stored locally using Room database
- **No Cloud Sync**: Your entries stay private on your device

### Data Management      # Main timeline/list screen
│   ├── EditorActivity.kt                  # Entry editor with markdown preview
│   ├── SettingsActivity.kt                # Settings and theme management
│   ├── DatabaseManagerActivity.kt         # Database management UI
│   ├── data/
│   │   ├── DiaryEntry.kt                  # Room entity
│   │   ├── DiaryDao.kt                    # Database access object
│   │   ├── DiaryDatabase.kt               # Room database instance
│   │   ├── AppSettings.kt                 # SharedPreferences wrapper
│   │   └── ThemeColors.kt                 # Theme definitions
│   └── ui/
│       ├── DiaryViewModel.kt              # Main ViewModel
│       ├── DiaryViewModelFactory.kt       # ViewModel factory
│       └── DiaryAdapter.kt                # RecyclerView adapter
├── res/
│   ├── layout/
│   │   ├── activity_main.xml              # Main screen layout
│   │   ├── activity_editor.xml            # Editor layout
│   │   ├── activity_settings.xml          # Settings layout
│   │   ├── activity_database_manager.xml  # Database manager layout
│   │   ├── item_diary_entry.xml           # List item layout
│   │   └── dialog_*.xml                   # Dialog layouts
│   ├── drawable/
│   │   └── striped_background.xml         # Striped pattern for date headers
│   ├── values/
│   │   ├── colors.xml                     # Color resources
│   │   ├── strings.xml                    # String resources
│   │   └── themes.xml                     # App themes
│   └── menu/
│       ├── menu_main.xml                  # Main toolbar menu
│       └── menu_editor.xml                # Editor toolbar menu
└── AndroidManifest.xml                    # App manifest
```

## Screenshots

*(Add your app screenshots here)*

## Installation

### From APK
1. Download the latest APK from releases
2. Enable "Install from Unknown Sources" in Android settings
3. Install the APK

### From Source
See "Building the APK" section above

## Privacy

DigitalGram Android is designed with privacy in mind:
- ✅ All data stored locally on your device
- ✅ No internet permission required
- ✅ No analytics or tracking
- ✅ No ads
- ✅ Optional biometric lock
- ✅ Your entries never leave your device

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

**Chakradhar Reddy**

Created as the Android companion to the DigitalGram macOS app.

## Acknowledgments

- Material Design 3 for the beautiful UI components
- AndroidX libraries for modern Android development
- Room database for reliable local storage content area

## Project Structure

```
app/src/main/
├── java/com/digitalgram/android/
│   ├── MainActivity.kt              # Timeline screen
│   ├── EditorActivity.kt            # Entry editor
│   ├── data/
│   │   ├── DiaryEntry.kt            # Room entity
│   │   ├── DiaryDao.kt              # Database operations
│   │   └── DiaryDatabase.kt         # Room database
│   └── ui/
│       ├── DiaryViewModel.kt        # ViewModel
│       ├── DiaryViewModelFactory.kt # Factory
│       └── DiaryAdapter.kt          # RecyclerView adapter
└── res/
    ├── layout/                      # XML layouts
    ├── drawable/                    # Icons & backgrounds
    ├── values/                      # Colors, themes, strings
    └── menu/                        # Toolbar menus
```

## License

Same as DigitalGram macOS

## Author

Created to complement the DigitalGram macOS app
