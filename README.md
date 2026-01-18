# DigitalGram Android

A beautiful diary/journal app for Android with Material Design 3 and a custom beige aesthetic.

## Features

- 📝 Create and edit diary entries
- 🎨 Beautiful beige-themed UI matching Daygram aesthetic
- 💾 Local Room database storage
- 📅 Date-based organization
- 🔒 Privacy-focused (local storage only)

## Technical Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM with LiveData
- **Database**: Room
- **UI**: Material Design 3

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

### DiaryEntry Table

| Column | Type | Description |
|--------|------|-------------|
| id | Long | Auto-increment primary key |
| date | Long | Unix timestamp of entry date |
| content | String | Diary entry text content |
| createdAt | Long | Creation timestamp |
| updatedAt | Long | Last update timestamp |

## UI Design

- **Background**: Beige (#F5F1E8)
- **Borders**: Black 2dp borders on cards
- **Accent**: Red (#B85450) for dates and actions
- **Typography**: Sans-serif condensed for headers
- **Cards**: Date box with striped background + content area

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
