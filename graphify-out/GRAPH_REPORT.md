# Graph Report - /Users/chkry/Documents/CODE/DigitalGramAndroid  (2026-04-30)

## Corpus Check
- Corpus is ~36,210 words - fits in a single context window. You may not need a graph.

## Summary
- 523 nodes · 506 edges · 35 communities detected
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 31 edges (avg confidence: 0.88)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Settings & Theme UI|Settings & Theme UI]]
- [[_COMMUNITY_Journal Entry Editor|Journal Entry Editor]]
- [[_COMMUNITY_Launcher Icons|Launcher Icons]]
- [[_COMMUNITY_Audit & Database Legacy|Audit & Database Legacy]]
- [[_COMMUNITY_Database Operations|Database Operations]]
- [[_COMMUNITY_Security Findings|Security Findings]]
- [[_COMMUNITY_Timeline List Adapter|Timeline List Adapter]]
- [[_COMMUNITY_App Settings Module|App Settings Module]]
- [[_COMMUNITY_Main Timeline Screen|Main Timeline Screen]]
- [[_COMMUNITY_Settings Storage|Settings Storage]]
- [[_COMMUNITY_Passcode & Biometric|Passcode & Biometric]]
- [[_COMMUNITY_Markdown Rendering|Markdown Rendering]]
- [[_COMMUNITY_Google Drive Sync|Google Drive Sync]]
- [[_COMMUNITY_Dropbox Backup|Dropbox Backup]]
- [[_COMMUNITY_Database Schema Init|Database Schema Init]]
- [[_COMMUNITY_Theme Editor|Theme Editor]]
- [[_COMMUNITY_Design System|Design System]]
- [[_COMMUNITY_Dropbox OAuth|Dropbox OAuth]]
- [[_COMMUNITY_Image Utilities|Image Utilities]]
- [[_COMMUNITY_Custom Theme Creator|Custom Theme Creator]]
- [[_COMMUNITY_Entry Data Model|Entry Data Model]]
- [[_COMMUNITY_App Identity|App Identity]]
- [[_COMMUNITY_Theme Colors|Theme Colors]]
- [[_COMMUNITY_Icon Visual Design|Icon Visual Design]]
- [[_COMMUNITY_Boot & Reminder|Boot & Reminder]]
- [[_COMMUNITY_Product Vision|Product Vision]]
- [[_COMMUNITY_Application Class|Application Class]]
- [[_COMMUNITY_Reminder Receiver|Reminder Receiver]]
- [[_COMMUNITY_API Compat Issue|API Compat Issue]]
- [[_COMMUNITY_Root Build Script|Root Build Script]]
- [[_COMMUNITY_Project Settings|Project Settings]]
- [[_COMMUNITY_App Build Script|App Build Script]]
- [[_COMMUNITY_README|README]]
- [[_COMMUNITY_View Binding|View Binding]]
- [[_COMMUNITY_App Version|App Version]]

## God Nodes (most connected - your core abstractions)
1. `SettingsActivity` - 84 edges
2. `EditorActivity` - 39 edges
3. `JournalDatabase` - 29 edges
4. `MainActivity` - 19 edges
5. `AppSettings` - 19 edges
6. `PasscodeActivity` - 16 edges
7. `GoogleDriveManager` - 15 edges
8. `JournalAdapter` - 13 edges
9. `MarkdownParser` - 13 edges
10. `NoOpSharedPreferences` - 12 edges

## Surprising Connections (you probably didn't know these)
- `Room Database (README)` --semantically_similar_to--> `Room Database (DiaryDatabase)`  [INFERRED] [semantically similar]
  README.md → PROJECT_SUMMARY.md
- `Canonical Merge Rule (LWW + 60s tie-window append)` --semantically_similar_to--> `C5: importAndMergeDatabase Opens User SQLite Without Validation`  [INFERRED] [semantically similar]
  SYNC_PLAN.md → audit/findings.md
- `JournalDatabase as Active Database (SQLiteOpenHelper)` --semantically_similar_to--> `JournalDatabase (Android active DB)`  [INFERRED] [semantically similar]
  CLAUDE.md → SYNC_PLAN.md
- `Brand Personality (calm, minimal, warm)` --expressed_via--> `Beige Theme (#F5F1E8)`  [INFERRED]
  PRODUCT.md → PROJECT_SUMMARY.md
- `Complements DigitalGram macOS App` --relates_to--> `macOS Schema Parity`  [INFERRED]
  README.md → PROJECT_SUMMARY.md

## Hyperedges (group relationships)
- **Security Triad: EncryptedSharedPreferences + Passcode + TLS form the app's security boundary** — audit_findings_c3_encrypted_prefs_fallback, audit_findings_c4_passcode_plaintext, audit_findings_c1_tls_bypass [INFERRED 0.85]
- **Cross-platform sync contract: schema parity + canonical merge rule + TLS transport must all hold for sync correctness** — project_summary_macos_schema_parity, sync_plan_canonical_merge_rule, sync_plan_tls13_mtls [INFERRED 0.88]
- **Theme system: ThemeColors + accentColor + SettingsActivity dynamic buttons form the complete theming pipeline** — claude_md_themecolors, theme_button_preview_accent_color, theme_button_preview_settings_activity [INFERRED 0.82]

## Communities

### Community 0 - "Settings & Theme UI"
Cohesion: 0.02
Nodes (2): PasscodeMode, SettingsActivity

### Community 1 - "Journal Entry Editor"
Cohesion: 0.05
Nodes (1): EditorActivity

### Community 2 - "Launcher Icons"
Cohesion: 0.06
Nodes (38): Orange Bookmark Tab, HDPI Density Variant, App Launcher Icon, Journal Page Shape, Orange Bookmark Tab, HDPI Density Variant Round, App Launcher Round Icon, Journal Page Shape (+30 more)

### Community 3 - "Audit & Database Legacy"
Cohesion: 0.07
Nodes (30): C5: importAndMergeDatabase Opens User SQLite Without Validation, D1: Legacy Room Dead Code (~600 lines, DiaryDatabase + DiaryDao + DiaryEntry + DiaryViewModel*+ DiaryAdapter), Audit Sprint S5 (Cleanup), DiaryDatabase as Legacy Dead Code (Room), JournalDatabase as Active Database (SQLiteOpenHelper), Brand Personality (calm, minimal, warm), Beige Theme (#F5F1E8), DiaryEntry Schema (id, date, content, createdAt, updatedAt) (+22 more)

### Community 4 - "Database Operations"
Cohesion: 0.07
Nodes (1): JournalDatabase

### Community 5 - "Security Findings"
Cohesion: 0.07
Nodes (28): C10: JournalDatabase CoroutineScope Never Cancelled, C1: Global TLS Bypass in DropboxManager, C2: cleartextTrafficPermitted=true + user CA trust, C3: EncryptedSharedPreferences Silent Fallback to Plain, C4: Passcode Stored Plaintext-Equivalent (timing leak), C6: FileProvider Exposes Entire Cache Directory, C7: notifyDataSetChanged on Every Update + Per-bind Allocations, C8: 9 Regex Passes Per Bind, No Cache (MarkdownParser) (+20 more)

### Community 6 - "Timeline List Adapter"
Cohesion: 0.08
Nodes (6): DotOnly, DotViewHolder, EntryViewHolder, EntryWithDot, JournalAdapter, TimelineItem

### Community 7 - "App Settings Module"
Cohesion: 0.08
Nodes (3): LockState, NoOpSharedPreferences, VerifyResult

### Community 8 - "Main Timeline Screen"
Cohesion: 0.1
Nodes (1): MainActivity

### Community 9 - "Settings Storage"
Cohesion: 0.11
Nodes (1): AppSettings

### Community 10 - "Passcode & Biometric"
Cohesion: 0.12
Nodes (1): PasscodeActivity

### Community 11 - "Markdown Rendering"
Cohesion: 0.12
Nodes (2): CustomURLSpan, MarkdownParser

### Community 12 - "Google Drive Sync"
Cohesion: 0.12
Nodes (1): GoogleDriveManager

### Community 13 - "Dropbox Backup"
Cohesion: 0.13
Nodes (2): BackupFileInfo, DropboxManager

### Community 14 - "Database Schema Init"
Cohesion: 0.15
Nodes (1): JournalDbHelper

### Community 15 - "Theme Editor"
Cohesion: 0.17
Nodes (1): ThemeEditorActivity

### Community 16 - "Design System"
Cohesion: 0.18
Nodes (11): applyButtonColors() Method, Rationale: Programmatic drawables over static XML for per-theme color, Version 1.0.1 Build 2, ThemeColors Utility (~20 named themes), Accessibility (WCAG AA, system font, reduced motion, color-blind safe, screen reader), Theme Accent Color, Dynamic Button Theming, GradientDrawable Programmatic Creation (+3 more)

### Community 17 - "Dropbox OAuth"
Cohesion: 0.2
Nodes (1): DropboxAuthActivity

### Community 18 - "Image Utilities"
Cohesion: 0.2
Nodes (2): ImageUtils, ImageUtilsAsync

### Community 19 - "Custom Theme Creator"
Cohesion: 0.22
Nodes (1): CustomThemeActivity

### Community 20 - "Entry Data Model"
Cohesion: 0.25
Nodes (1): JournalEntry

### Community 21 - "App Identity"
Cohesion: 0.36
Nodes (8): App Launcher Icon, Blue border/outline visual motif, DigitalGram Android App, ic_launcher_round (mipmap-mdpi), mipmap-mdpi density bucket (160dpi), Orange/amber bookmark tab visual motif, Round Icon Shape (Android adaptive icon), White card/page visual motif

### Community 22 - "Theme Colors"
Cohesion: 0.33
Nodes (1): ThemeColors

### Community 23 - "Icon Visual Design"
Cohesion: 0.6
Nodes (5): App Launcher Icon, Clipboard/Notepad Motif, White, Blue, Orange Color Palette, Journal/Notes App, Flat Minimal Icon Style

### Community 24 - "Boot & Reminder"
Cohesion: 0.5
Nodes (1): BootReceiver

### Community 25 - "Product Vision"
Cohesion: 0.5
Nodes (4): Anti-references (no social, no feature sprawl, no gamification), Design Principles (disappear into writing, one thing per moment, warmth without decoration, respect the ritual, honest simplicity), DigitalGram Product Purpose (daily journal, one entry per day), Target Users (personal reflection, mindfulness, emotional processing)

### Community 26 - "Application Class"
Cohesion: 0.67
Nodes (1): DigitalGramApplication

### Community 27 - "Reminder Receiver"
Cohesion: 0.67
Nodes (1): ReminderReceiver

### Community 28 - "API Compat Issue"
Cohesion: 1.0
Nodes (2): C11: java.time.Instant.parse Without Desugaring at minSdk 24, Audit Sprint S4 (Compat, minSdk 24)

### Community 29 - "Root Build Script"
Cohesion: 1.0
Nodes (0): 

### Community 30 - "Project Settings"
Cohesion: 1.0
Nodes (0): 

### Community 31 - "App Build Script"
Cohesion: 1.0
Nodes (0): 

### Community 32 - "README"
Cohesion: 1.0
Nodes (1): MVVM Architecture (README)

### Community 33 - "View Binding"
Cohesion: 1.0
Nodes (1): ViewBinding (buildFeatures.viewBinding = true)

### Community 34 - "App Version"
Cohesion: 1.0
Nodes (1): Current Version: 1.2.1 / versionCode 14

## Knowledge Gaps
- **76 isolated node(s):** `PasscodeMode`, `TimelineItem`, `DotOnly`, `EntryWithDot`, `ThemeColors` (+71 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `API Compat Issue`** (2 nodes): `C11: java.time.Instant.parse Without Desugaring at minSdk 24`, `Audit Sprint S4 (Compat, minSdk 24)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Root Build Script`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Project Settings`** (1 nodes): `settings.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `App Build Script`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `README`** (1 nodes): `MVVM Architecture (README)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `View Binding`** (1 nodes): `ViewBinding (buildFeatures.viewBinding = true)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `App Version`** (1 nodes): `Current Version: 1.2.1 / versionCode 14`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Audit Sprint S2 (Security)` connect `Security Findings` to `Audit & Database Legacy`?**
  _High betweenness centrality (0.007) - this node is a cross-community bridge._
- **Why does `C5: importAndMergeDatabase Opens User SQLite Without Validation` connect `Audit & Database Legacy` to `Security Findings`?**
  _High betweenness centrality (0.006) - this node is a cross-community bridge._
- **What connects `PasscodeMode`, `TimelineItem`, `DotOnly` to the rest of the system?**
  _76 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Settings & Theme UI` be split into smaller, more focused modules?**
  _Cohesion score 0.02 - nodes in this community are weakly interconnected._
- **Should `Journal Entry Editor` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Launcher Icons` be split into smaller, more focused modules?**
  _Cohesion score 0.06 - nodes in this community are weakly interconnected._
- **Should `Audit & Database Legacy` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._