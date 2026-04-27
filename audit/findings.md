# DigitalGramAndroid — Consolidated Audit Findings

Source: three audits (compat / security / perf) + four-agent adversarial panel (security skeptic / perf skeptic / compat-cleanup skeptic / orchestration skeptic) + opus arbiter, run during plan mode.

## CRITICAL

| ID | File:line | Claim | Sprint | Fix task |
|---|---|---|---|---|
| C1 | `DropboxManager.kt:30-46` | Global TLS bypass (`HttpsURLConnection.setDefaultSSLSocketFactory` + `setDefaultHostnameVerifier { _, _ -> true }`) — affects every HTTPS call in process | S2 | T2.1 rip + system TLS |
| C2 | `network_security_config.xml:12-17` | `cleartextTrafficPermitted="true"` + trusts user-installed CAs in production base-config | S2 | T2.1 lockdown |
| C3 | `AppSettings.kt:32-51` | EncryptedSharedPreferences silent fallback to plain SharedPreferences for passcode + Dropbox token + Google Drive email | S2 | T2.2 fail-closed + banner |
| C4 | `AppSettings.kt:246-248` + `PasscodeActivity.kt` | Passcode stored plaintext-equivalent and compared with `==` (timing leak; brute-forceable in seconds offline) | S2 | T2.2 PBKDF2 + T2.4 constant-time |
| C5 | `JournalDatabase.kt:210-306` (`importAndMergeDatabase`) | Opens user-supplied SQLite directly; no trigger/virtual-table validation; calls `saveEntrySync` in tight loop with no transaction batching (10k entries = 10k fsyncs) | S2 | T2.6 schema validation + batch txn |
| C6 | `res/xml/file_paths.xml:6-8` | `<cache-path path="."/>` exposes the entire cache via FileProvider | S2 | T2.3 explicit subpaths |
| C7 | `JournalAdapter.kt:42,48,53,58,63` | `notifyDataSetChanged()` on every data update; markdown re-parsed and `SimpleDateFormat`/`GradientDrawable` allocated per bind | S3 | T3.1 ListAdapter+DiffUtil + adapter-level caches |
| C8 | `MarkdownParser.kt` (full) + `JournalAdapter.kt:228-234` | 9 regex passes on full content per bind; no truncation, no cache — root cause of "lengthy conversation preview" lag | S3 | T3.2 truncate(500) + LRU |
| C9 | `JournalDatabase.kt` synchronous methods + `SettingsActivity.kt:792, 872, 876, 878, 978, 1040` | UI-thread file I/O on every database admin action | S3 | T3.4 suspendify + T3.5 callsite wrap |
| C10 | `JournalDatabase.kt:27` | `private val scope = CoroutineScope(Dispatchers.IO)` never cancelled (singleton lifetime) | S3 | T3.0 + T3.4 (ProcessLifecycleOwner cancel) |
| C11 | `JournalDatabase.kt:381` | Unguarded `java.time.Instant.parse` — API 26+ only; needs core-library desugaring at minSdk 24 | S4 | T4.1 desugar + T4.3 verify |

## HIGH

| ID | File:line | Claim | Sprint | Fix task |
|---|---|---|---|---|
| H1 | `DropboxAuthActivity.kt:117-119` + `DropboxManager.kt:119,135` | Logs token prefixes via `.take(20)` — leaks to logcat | S2 | T2.1 + T2.5 redact |
| H2 | `GoogleDriveManager.kt:78-80` | Logs `account.email` + `account.id` + `account.displayName` (PII) | S5 | T5.2 redact |
| H3 | `SettingsActivity.kt:~2046-2048` | Puts `errorMsg` (may include tokens) on system clipboard | S5 | T5.2 strip / redact |
| H4 | `AndroidManifest.xml:90-96` | DropboxAuthActivity scheme `db-cub1jyzclswfroz` exported — OAuth hijack risk | S2 | T2.5 PKCE-only flow |
| H5 | `AndroidManifest.xml:18-19` | `allowBackup="false"` AND `fullBackupContent="@xml/backup_rules"` — contradictory | S5 | T5.3 delete fullBackupContent + file |
| H6 | `JournalAdapter.kt:225-226` (and per-bind allocations) | DiffUtil missing stable IDs on `TimelineItem` sealed class | S3 | T3.1 add `id: Long` to subclasses |
| H7 | `MainActivity.applyThemeColors()` + `EditorActivity.applyThemeColors()` + `ImageUtils.kt:19-20` | Wallpaper bitmap re-decoded on every theme switch on main thread | S3 | T3.3 async + URI cache |

## MEDIUM

| ID | File:line | Claim | Sprint | Fix task |
|---|---|---|---|---|
| M1 | `JournalDbHelper.onConfigure` | WAL mode not enabled (default DELETE journal mode) | S3 | T3.4 add `enableWriteAheadLogging` |
| M2 | `PasscodeActivity.kt` (BiometricPrompt path) | Biometric path has no rate limiting and no re-verify timeout | S2 (incidental) | document; defer dedicated fix |
| M3 | absent | No custom `Application` class, no StrictMode, no `ProcessLifecycleOwner` cancellation | S3 | T3.0 new file |
| M4 | `AppSettings.kt:22-31` | MasterKey alias not unique per install; Keystore corruption recovery silently downgrades | S2 | covered by T2.2 fail-closed |
| M5 | several | 30+ `e.printStackTrace()` and unredacted `Log.d/e` calls | S5 | T5.2 |
| M6 | `JournalDbHelper.onUpgrade` empty | Future `DATABASE_VERSION` bump silently drops user data | deferred | document risk |
| M7 | `BootReceiver.kt` | Re-registers all alarms on main thread of receiver (10s deadline if many) | deferred | document risk |
| M8 | manifest, themes | `tools:targetApi="31"` redundant; `windowLightNavigationBar` inert in `values/` (needs `values-v27/`) | S4 | T4.2 |

## Dead code

| ID | Files | Claim | Sprint | Fix task |
|---|---|---|---|---|
| D1 | `data/DiaryDatabase.kt`, `data/DiaryDao.kt`, `data/DiaryEntry.kt`, `ui/DiaryViewModel.kt`, `ui/DiaryViewModelFactory.kt`, `ui/DiaryAdapter.kt` | ~600 lines of legacy Room not referenced by any Activity | S5 | T5.1 delete + drop Room/KSP |
| D1' | `res/layout/item_diary_entry.xml` | **LIVE** — consumed by `JournalAdapter` via `ItemDiaryEntryBinding`. Do NOT delete. | S5 | T5.1 explicitly preserve |

## Counter-audit-resolved (panel debate, decided)

- **At-rest DB encryption (SQLCipher)** — proposed by Panel A (security skeptic) as missing CRITICAL. Arbiter ruling: real concern, scope-expanding (~5MB APK + migration), surfaced as **optional Sprint 2.5** that the user toggles at the S1 gate.
- **Device-bound MasterKey** (`setUserAuthenticationRequired`) — proposed by Panel A. Arbiter ruling: PBKDF2 sufficient for the threat model; binding adds UX friction. Deferred.
- **TLS rip might break Dropbox on older API** — Panel A. Arbiter ruling: real risk, mitigation = T2.7 includes API 24 emulator TLS-handshake smoke test.
- **PKCE migration UX** — Panel A. Arbiter ruling: T2.5 explicitly handles legacy-token re-auth flow.
- **`PRAGMA quick_check` insufficient** — Panel A. Arbiter ruling: T2.6 spec corrected to enumerate `sqlite_master`, allow `android_metadata` + `sqlite_sequence`, reject triggers/views/virtual tables.
- **PBKDF2 salt circular dependency** — Panel D. Arbiter ruling: salt in plain `prefs`, hash in `securePrefs`. Plan corrected.
- **T2.6 misplaced in W2** — Panel D. Arbiter ruling: moved to W1.
- **`item_diary_entry.xml` deletion error** — Panel C. Arbiter ruling: file is live; T5.1 spec corrected to preserve.
- **`KSP` plugin removal** — Panel C. Arbiter ruling: add to T5.1.
- **Markdown cache key collision** — Panel B. Arbiter ruling: include `length` in key.
- **150ms debounce dead code** — Panel B. Arbiter ruling: replaced with `MutableSharedFlow.conflate()` + import-loop transaction batching.
- **No `Application` class / `StrictMode`** — Panel B + C. Arbiter ruling: new task T3.0.
- **`google-api-services-drive` minSdk** — Panel C. User decision: bump app minSdk to 24, no library workaround.

## Sprint assignment summary

- **S2 (security):** C1, C2, C3, C4, C5, C6, H1, H4
- **S2.5 (optional):** SQLCipher at-rest encryption
- **S3 (perf):** C7, C8, C9, C10, M1, M3, H6, H7
- **S4 (compat, minSdk 24):** C11, M8
- **S5 (cleanup):** D1, H2, H3, H5, M5
- **Deferred:** M2, M6, M7, device-bound MasterKey, cert pinning, splash screen, localization, RTL, accessibility audit
