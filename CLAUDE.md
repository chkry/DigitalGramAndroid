# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

JDK 17, Android SDK 36, Kotlin 2.1.0, AGP 8.8.0. Gradle wrapper included.

```bash
./gradlew assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease        # signed release APK (needs keystore.properties)
./gradlew bundleRelease          # AAB for Play Store → app/build/outputs/bundle/release/
./gradlew clean
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
```

No tests exist yet — the `test` / `androidTest` source sets are empty. `./build.sh` is a convenience wrapper around `clean + assembleDebug`.

## Required local config (gitignored)

- `keystore.properties` at repo root — `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it, release builds are unsigned. Keystore lives at `/Users/chkry/Documents/CODE/digitalgram-release.jks` (outside repo).
- `local.properties` — must define `dropboxAppKey=...`. Exposed via `BuildConfig.DROPBOX_APP_KEY`. Without it the Dropbox OAuth redirect scheme `db-cub1jyzclswfroz` in `AndroidManifest.xml` will still work (hard-coded), but API calls will fail.
- Google Drive OAuth requires SHA-1 registration in Google Cloud Console — see `GOOGLE_DRIVE_SETUP.md` and `PLAYSTORE_GOOGLE_DRIVE_TROUBLESHOOTING.md`.

## Architecture: two databases coexist

This is the single most important thing to understand before changing data code.

1. **`JournalDatabase`** (`data/JournalDatabase.kt`) — **the active database.** Raw `SQLiteOpenHelper`, schema **matches the DigitalGram macOS app exactly** (`diary` table keyed by `date TEXT PRIMARY KEY`, plus `year/month/day/content/created/updated`). This is what `MainActivity`, `EditorActivity`, and `JournalAdapter` use. Cross-platform sync (Dropbox/Google Drive) relies on this schema parity.
   - Supports **multiple switchable SQLite files** in `context.getDatabasePath(...).parentFile` — create/rename/delete/import/merge. Current DB name is persisted in `AppSettings.currentDatabase` (default `daygram.sqlite`).
   - Merge logic: newer `updated` timestamp wins; identical content skipped.
   - Paired with `JournalEntry` (data class) and `ui/JournalAdapter.kt`.

2. **`DiaryDatabase`** (`data/DiaryDatabase.kt`) — **legacy Room database, not wired into any current activity.** Still present because Room + KSP + `DiaryDao` + `DiaryEntry` + `ui/DiaryViewModel*` + `ui/DiaryAdapter.kt` all compile. Treat as dead code unless you see a reference from an Activity. Don't add new features here.

When adding journal features, use `JournalDatabase` + `JournalEntry`. When in doubt, grep for `getInstance` usage.

## Activities & flow

- `MainActivity` — timeline/calendar, observes `JournalDatabase.entries` LiveData. Launches `PasscodeActivity` on cold start if `AppSettings.passcodeEnabled`.
- `EditorActivity` — single-entry editor, markdown rendering via `util/MarkdownParser.kt`.
- `SettingsActivity` — theme, font, passcode/biometric, reminder, database switcher, Dropbox/Google Drive connect.
- `CustomThemeActivity` / `ThemeEditorActivity` — per-theme color overrides (stored as `theme_custom_<name>_<key>` in prefs).
- `PasscodeActivity` — 4-digit passcode + optional `androidx.biometric` fingerprint.
- `DropboxAuthActivity` — receives OAuth redirect (scheme `db-cub1jyzclswfroz`).
- `BootReceiver` + `ReminderReceiver` — rescheduling daily reminder alarms after boot.

## Settings & secure storage

`AppSettings` (singleton) wraps two `SharedPreferences`:
- `digitalgram_settings` — plain prefs (theme, font, reminder, etc.)
- `digitalgram_secure_settings` — `EncryptedSharedPreferences` (AES256) for passcode, Dropbox token, Google Drive email. Has a fallback path if the keystore is corrupt (deletes + recreates, final fallback to plain prefs with `_fallback` suffix).

Theme system: `util/ThemeColors.kt` resolves colors for ~20 named themes (LIGHT, DARK, NORD, DRACULA, etc.) with optional per-color user overrides pulled from `AppSettings.getThemeCustomColor()`.

## Release build notes

- `isMinifyEnabled = true` and `isShrinkResources = true` in release — ProGuard rules in `app/proguard-rules.pro` must cover Room entities, ViewModels, Tink crypto, JSR 305.
- Version bumps: update `versionCode` + `versionName` in `app/build.gradle.kts`. Current: `1.2.1` / `versionCode 14`.
- `allowBackup="false"` in manifest — users must use in-app Dropbox/Google Drive/local-file export, not Android auto-backup.

## Conventions

- Kotlin data classes for entities, coroutines (`Dispatchers.IO`) for DB work, LiveData for UI observation.
- ViewBinding is enabled (`buildFeatures.viewBinding = true`) — use `ActivityMainBinding.inflate(...)` pattern, not `findViewById`.
- `fallbackToDestructiveMigration()` is set on the legacy Room DB; `JournalDatabase` has manual `onUpgrade` (currently a no-op).

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
|------|----------|
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
