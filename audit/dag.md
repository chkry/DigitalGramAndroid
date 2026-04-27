# Sprint DAG (post-arbitration)

This file is a navigable index. Authoritative source is `/Users/chkry/.claude/plans/interview-me-relentlessly-about-compiled-abelson.md` (plan v2).

## Sprints

```
S2 security        → S2.5 SQLCipher (optional)? → S3 perf → S4 compat → S5 cleanup → S6 final review
```

## SQLCipher decision

Arbiter recommends **YES**: closes the at-rest leak (rooted devices, cloud backups). Cost is bounded (~5MB APK, one-shot migration, 5-15% query overhead). User has bumped minSdk to 24 to keep cloud sync working — adding SQLCipher in the same release maintains threat-model coherence (passcode lock with no encryption is theatre).

User decision required at S1 gate: include S2.5 or skip.

## Per-sprint task counts

| Sprint | W1 parallel | W2 parallel | W3/verify | Total tasks |
|---|---|---|---|---|
| S2 | 4 | 2 | 2 | 8 |
| S2.5 | 1 | — | 1 | 2 |
| S3 | 5 | 2 | 2 | 9 |
| S4 | 3 | — | 2 | 5 |
| S5 | 3 | — | 1 | 4 |
| S6 | 2 | — | 2 | 4 |

Total without S2.5: 30 tasks across 5 sprints. With S2.5: 32.

## Halt-and-notify

Each sprint's adversarial review (T2.8, T2.5.2, T3.7, T4.5, T5.4 build, T6.1a/b) auto-halts on CRITICAL and surfaces to user. The S1 gate is the only scheduled user approval; sprint-internal halts are runtime safety brakes.

## File ownership conflict matrix (verified zero parallel-write overlap)

- S2.W1: `DropboxManager.kt` (T2.1) ⊥ `AppSettings.kt` (T2.2) ⊥ `file_paths.xml` (T2.3) ⊥ `JournalDatabase.kt` import-only (T2.6).
- S2.W2: `PasscodeActivity.kt` (T2.4) ⊥ `DropboxAuthActivity.kt` (T2.5).
- S3.W1: `DigitalGramApplication.kt` new (T3.0) ⊥ `JournalAdapter.kt` (T3.1) ⊥ `MarkdownParser.kt` (T3.2) ⊥ `ImageUtils.kt`+wallpaper-call-sites (T3.3) ⊥ `JournalDatabase.kt` suspend-only (T3.4).
- S3.W2: `SettingsActivity.kt` (T3.5a) ⊥ `MainActivity.kt`+`EditorActivity.kt` DB-call-sites (T3.5b). Note T3.3 owns `applyThemeColors` lines in MainActivity/EditorActivity; T3.5b owns DB-call-site lines. Different line ranges.
- S4.W1: `app/build.gradle.kts` (T4.1) ⊥ `AndroidManifest.xml`+themes (T4.2) ⊥ alarm receivers (T4.3).
- S5.W1: legacy Room files+`build.gradle.kts` (T5.1) ⊥ logging files (T5.2) ⊥ manifest+backup XMLs (T5.3). Note: T5.1 and T5.3 both touch `AndroidManifest.xml` — **moved T5.3's manifest edit to a fourth task or sequenced after T5.1 in W1.5**.

## Open file-overlap correction (post-arbiter)

`AndroidManifest.xml` is touched by:
- T3.0 (Application registration)
- T4.2 (manifest cleanup, `tools:targetApi`, theme refs)
- T5.3 (delete `fullBackupContent` attribute)

Sprints serialize so this is fine across sprints (S3 → S4 → S5 in order). Within S5, T5.3's manifest edit is the only touch — clean.

T5.1 also edits `app/build.gradle.kts`. T4.1 edits `app/build.gradle.kts`. Different sprints — fine.
