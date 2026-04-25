# DigitalGram — Local Wi-Fi Sync (v1)

> **Status**: Approved plan, not yet implemented. Sister copy lives at `/Users/chkry/Documents/CODE/DigitalGram/SYNC_PLAN.md`. Keep them in sync if either is edited.

## Context

DigitalGram exists as two siblings — a macOS menu bar app at `/Users/chkry/Documents/CODE/DigitalGram` (Swift/AppKit, sandboxed) and an Android app at `/Users/chkry/Documents/CODE/DigitalGramAndroid` (Kotlin/AndroidX). Schemas already match exactly (`diary` table keyed by `date TEXT NOT NULL PRIMARY KEY`, ISO8601 `created`/`updated`).

Today the only cross-device path is exporting a `.db` file and manually importing it on the other side. We're building peer-to-peer sync over local Wi-Fi: discover the sibling via Bonjour/NSD, pair once with a 6-digit visual confirmation, then merge entries (and image bytes) over a TLS-pinned TCP connection. Goal: seamless after one-time pairing — open either app on the same Wi-Fi and journals converge.

### Critical correction surfaced in planning

The two existing merge functions are **inconsistent today** — `SettingsView.mergeDatabases` (macOS) appends on conflict but never overwrites; `JournalDatabase.importAndMergeDatabase` (Android) does LWW but never appends. This sprint imposes ONE canonical rule on both. Without this, sync would silently diverge between platforms.

## Product decisions (confirmed)

| Decision | Choice | Rationale |
|---|---|---|
| Image sync | Bytes in v1, Android rendering in v1.5 | Avoids data loss without expanding scope into Android image-attachment UX |
| macOS deployment | Bump 12.0 → 13.0 | `NWBrowser` requires 13; aligns with existing `SMAppService` 13+ gate; <1% user impact |
| Auto-sync trigger | App-resume + manual button (5-min rate limit) | Seamless without being chatty; predictable battery/network |
| Pairing topology | Strict 1:1 (re-pair to swap) | Simpler state machine, simpler error paths; 1:N a v2 concern |

## Final design

### Discovery
- Bonjour service type **`_dgsync._tcp`** (the obvious `_digitalgram-sync._tcp` exceeds Bonjour's 15-char service-name limit)
- TXT records: `pv` (protocol version `1`), `av` (app version), `os` (`macos`/`android`), `dn` (display name), `did` (stable device id, base32 of 8 random bytes), `fpr` (first 16 hex of SHA-256(SPKI)), `pa` (`0`=pairable, `1`=paired-only)
- Port: kernel-assigned, advertised via Bonjour record

### Pairing — X25519 ECDH + 6-digit SAS (Magic-Wormhole pattern)
1. Both apps over a not-yet-pinned TLS channel exchange ephemeral X25519 public keys + cert SPKI fingerprints.
2. Both compute `SAS = decimal(HMAC-SHA256(shared_secret, "dgsync-sas")[..5]) mod 10^6`, displayed as `482 195`.
3. User visually confirms identical PIN on both screens, taps "Match" on each.
4. On both-confirm, persist `{peerDeviceId, peerCertFingerprint, peerName, pairedAt}` — macOS Keychain, Android EncryptedSharedPreferences.
5. Re-pair when fingerprint changes, with explicit user confirmation. 60s display window, 5-min lockout after a mismatch.

**Why ECDH+SAS** over SPAKE2 (no maintained Swift impl), PSK-from-PIN (~20 bits, brute-forceable), or PIN-MAC (custom protocol surface). Both platforms have X25519 in their stdlib.

### Transport
- TCP + TLS 1.3 mandatory; mTLS with raw-public-key style pinning (`SHA-256(SPKI) == pinned`); CN/SAN/expiry ignored
- Identity certs: P-256 self-signed, generated at first launch
  - macOS: Keychain `kSecClassIdentity`
  - Android: AndroidKeyStore P-256
- **Conscrypt** required on Android API 26-28 for TLS 1.3 (`org.conscrypt:conscrypt-android`, installed as preferred provider)
- Cipher suites: AEAD-only (`TLS_AES_128_GCM_SHA256`, `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`)

### Wire format
- 1-byte type prefix (`0x01` JSON, `0x02` BINARY_CHUNK, `0x03` BINARY_END) + 4-byte big-endian length + payload, max frame 1 MiB
- Strictly sequential request/response per connection (no multiplexing)
- Messages: `Hello`/`HelloAck`, `ListEntries{since}` → `EntriesIndex{[date,updated,contentHash]}`, `GetEntry{date}` → `Entry{...}`, `PutEntry{...}` → `PutEntryAck{date,mergedAs}`, `ListImages` → `ImagesIndex{[name,sha256,size]}`, `GetImage{name}` → header + chunks + terminator, `Bye{reason}`, `Err{code,message}`
- 25 MiB hard cap per image; defensively reject frames > 1 MiB

### Sync algorithm — per-entry diff (NOT snapshot)
Snapshot transfer is fragile (WAL state, sandbox, file portability). Per-entry diff streams:
1. Initiator with lower `deviceId` wins concurrent-sync race; the other backs off.
2. Both `Hello`/`HelloAck`.
3. Initiator sends `ListEntries{since: lastSyncWithPeer_X}`.
4. Responder returns `EntriesIndex` with `(date, updated, contentHash[16-byte SHA-256 prefix])`.
5. Initiator diffs against its local index, fetches updated/missing rows via `GetEntry`.
6. Receives `Entry` rows, applies the **canonical merge rule** below, in a single SQLite txn per row.
7. Initiator pushes its newer rows via `PutEntry`; responder applies same rule.
8. Image phase: same pattern with `ListImages`/`GetImage`, dedup by SHA-256, write to `.partial` then atomic-rename.
9. `Bye{ok}`. Both update `lastSyncWithPeer_X` watermark only on clean exit.

### Canonical merge rule (replaces both existing impls)

```
on receiving (date, content_R, updated_R) at receiver L:
    local = lookup(date)
    if local == null:
        INSERT (date, content_R, updated_R)             → "inserted"
    if local.content.trim() == content_R.trim():
        return                                          → "skipped"
    if abs(parse(updated_R) - parse(local.updated)) <= 60s:
        # tie window — concurrent edit case
        if not local.content.contains("\n\n---\n\n" + content_R.trim()):
            UPDATE local.content = local.content + "\n\n---\n\n" + content_R
            UPDATE local.updated = max(updated_R, local.updated)
            return                                      → "appended"
        return                                          → "skipped" (idempotent)
    if parse(updated_R) > parse(local.updated):
        UPDATE local with remote                        → "updated"
    return                                              → "skipped"
```

The trim-equality check + `contains` guard makes the rule **idempotent across repeated sync rounds**.

## Critical files

### macOS — to modify
- `DigitalGram.xcodeproj/project.pbxproj` — bump `MACOSX_DEPLOYMENT_TARGET` 12.0 → 13.0
- `DigitalGram/DigitalGram.entitlements` — add `com.apple.security.network.server`, `com.apple.security.network.client`
- `DigitalGram/Info.plist` — add `NSLocalNetworkUsageDescription`, `NSBonjourServices = ["_dgsync._tcp"]`
- `DigitalGram/AppDelegate.swift` — add `applicationDidBecomeActive` hook; wire `SyncEngine.start/stop/resume`
- `DigitalGram/Storage/StorageManager.swift` — **activate the dormant `dbQueue`** as the single SQLite serialization point (wraps `saveEntry`, `loadEntries`, `deleteEntry`, merge); add `merge(sourceURL:into:)` and `importImages(from:)` methods
- `DigitalGram/Views/SettingsView.swift` — strip merge SQLite body (lift to `StorageManager+Merge.swift`); keep UI shim that calls into the new method; add `SyncSettingsSection` import

### macOS — to create
```
DigitalGram/Sync/
    SyncEngine.swift            (state machine, AppDelegate hooks)
    SyncDiscovery.swift         (NWBrowser + NWListener for _dgsync._tcp)
    SyncTransport.swift         (TLS NWConnection, length-prefixed I/O)
    SyncPairing.swift           (X25519 + SAS + lockout)
    SyncProtocol.swift          (Codable messages + framer)
    SyncStorageBridge.swift     (per-entry diff orchestration)
    SyncKeychain.swift          (P-256 identity + peer fingerprint pin)
    SyncModels.swift            (PairedPeer, SyncState, errors)
DigitalGram/Storage/
    StorageManager+Merge.swift  (lifted body from SettingsView, with LWW branch added)
DigitalGram/Views/
    SyncSettingsSection.swift   (GroupBox section)
    PairingSheet.swift          (modal SAS display + confirm)
```

### Android — to modify
- `app/build.gradle.kts` — add `org.conscrypt:conscrypt-android` dep
- `app/src/main/AndroidManifest.xml` — add `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`; declare `SyncService` + `PairingActivity`
- `app/src/main/java/com/digitalgram/android/data/AppSettings.kt` — add `syncEnabled`, `pairedPeerName`, `pairedPeerSpki`, `pairedSecret`, `lastSyncWithPeer_<id>`, `deviceUuid`
- `app/src/main/java/com/digitalgram/android/data/JournalDatabase.kt` — replace `importAndMergeDatabase` body with the canonical rule (adds the missing append branch)
- `app/src/main/java/com/digitalgram/android/MainActivity.kt` — observe `SyncEngine.state`, bind/unbind service in `onResume`/`onPause`
- `app/src/main/java/com/digitalgram/android/SettingsActivity.kt` + `app/src/main/res/layout/activity_settings.xml` — new `▌ SYNC` section (matches existing `▌ THEME` / `▌ CLOUD BACKUP` aesthetic)
- `app/src/main/java/com/digitalgram/android/BootReceiver.kt` — restart `SyncService` in discovery-only mode if `syncEnabled`

### Android — to create
```
app/src/main/java/com/digitalgram/android/sync/
    SyncEngine.kt              (singleton facade, StateFlow)
    SyncService.kt             (foreground Service, dataSync type, MulticastLock)
    SyncDiscovery.kt           (NsdManager register + discover)
    SyncTransport.kt           (SSLSocket + custom X509TrustManager)
    SyncPairing.kt             (X25519 + SAS, persists to EncryptedSharedPreferences)
    SyncProtocol.kt            (sealed-class messages + framer)
    SyncImageManager.kt        (filesDir/images/ provisioning + chunked receive)
    SyncCertStore.kt           (AndroidKeyStore P-256 + self-signed cert)
    SyncCrypto.kt              (HKDF, HMAC, SPKI fingerprint helpers)
    PairingActivity.kt         (PIN UI)
app/src/main/res/layout/
    activity_pairing.xml
    dialog_pin_entry.xml
```

## Reusable existing code (don't reinvent)

| Existing | File:Line | Use for |
|---|---|---|
| `mergeDatabases` body | `SettingsView.swift:1288-1386` | Lift verbatim into `StorageManager+Merge.swift`, add LWW branch |
| `imagesDirectory` | `StorageManager.swift:52-76` | Source/target for image sync — bookmark-aware |
| `DatabaseChanged` notification | Posted at `SettingsView.swift:1084,1269`, observed at `JournalEntryView.swift:301-307` | Post after merge; UI refresh free |
| Dormant `dbQueue` | `StorageManager.swift:10` | Activate as SQLite serialization point |
| `importAndMergeDatabase` skeleton | `JournalDatabase.kt:239` | Replace body with canonical rule; keep UI-refresh pathway |
| `EncryptedSharedPreferences` pattern | `AppSettings.kt`, mirrored in `DropboxManager`/`GoogleDriveManager` | Sync identity + paired peer state |
| `NotificationChannel "digitalgram_reminders"` | `SettingsActivity.kt` (channel init) | Mirror as `"digitalgram_sync"` (IMPORTANCE_LOW) |
| `_entriesLiveData` refresh | `JournalDatabase.kt:25,425` → `MainActivity.kt:448` | Sync-applied merges refresh UI for free |
| Existing `keystore.properties` signing config | `app/build.gradle.kts:33-46` | Reuse for Android v1.31 signed APK |
| Existing macOS `build.sh` + `xcodebuild` | `DigitalGram/build.sh` | Reuse for macOS .app build |

## Sprint plan — Team Members Concept

**Hierarchy & dependency**: 9 sprints, executed by **fresh agents per sprint** (no reuse — preserves context windows). Within a sprint, parallel tracks run as separate agents. Sprint N+1 cannot start until Sprint N's blocking deliverables are merged.

| Sprint | Personas (fresh per sprint) | Parallelism | Depends on | LOC |
|---|---|---|---|---|
| **0** | Protocol scribe + canonical-merge author | Solo | — | ~150 (spec+tests) |
| **1A** | macOS plumber + macOS refactor specialist + protocol designer (Swift) | A.1‖A.2‖A.3 | 0 | ~400 |
| **1B** | Android plumber + Android data eng + protocol designer (Kotlin) | B.1‖B.2‖B.3 | 0 | ~250 |
| **2A** | macOS networking eng + macOS crypto eng | A.1‖A.2 | 1A | ~360 |
| **2B** | Android networking eng + Android crypto eng | B.1‖B.2 | 1B | ~340 |
| **3A** | macOS TLS-careful network eng | Solo | 2A | ~220 |
| **3B** | Android TLS-careful network eng (with Conscrypt focus) | Solo | 2B | ~260 |
| **4A** | macOS pairing eng + SwiftUI eng | Sequential within | 3A | ~440 |
| **4B** | Android pairing eng + UI eng | Sequential within | 3B | ~580 |
| **4X** | Cross-platform integration eng (joint test session) | Solo | 4A∧4B | 0 (test only) |
| **5A** | Senior Swift eng | Sequential | 4A | ~580 |
| **5B** | Senior Kotlin eng | Sequential | 4B | ~600 |
| **6A** | macOS service eng + macOS UX eng | A.1‖A.2 | 5A | ~440 |
| **6B** | Android service eng + Android UX eng | B.1‖B.2‖B.3 | 5B | ~380 |
| **7A** | QA-minded eng (macOS) | A‖B‖C | 6A | (no LOC; tests) |
| **7B** | QA-minded eng (Android) | A‖B‖C | 6B | (no LOC; tests) |
| **7X** | Application security reviewer | Solo | 4X | 0 |
| **8** | Release manager | Solo | 7A∧7B∧7X | docs + builds |

**Total estimated LOC**: ~4,500 across both platforms (≈2,400 macOS, ≈2,100 Android).

### Sprint 0 — Shared foundations (BLOCKING)
- 0.1 Wire-spec freeze: `dgsync-spec-v1.md` + JSON schema files for every message + golden-vector encoded bytes for 10 known messages (used by both platforms' codec tests)
- 0.2 Canonical merge rule: 30-row truth table; reference implementation as pseudocode

### Sprint 1 — Platform foundations (parallel A‖B)
**1A (macOS)**: A.1 deployment-target bump + entitlements + Info.plist; A.2 lift `mergeDatabases` to `StorageManager+Merge.swift`, activate `dbQueue`, add LWW branch; A.3 `SyncProtocol.swift` Codable structs + framer + golden-vector tests
**1B (Android)**: B.1 manifest perms + service decl + Conscrypt provider install; B.2 replace `importAndMergeDatabase` body with canonical rule; B.3 `SyncProtocol.kt` sealed-class messages + framer + golden-vector tests

### Sprint 2 — Identity + discovery (parallel A‖B)
**2A**: `SyncKeychain` (P-256 in Keychain), `SyncDiscovery` (NWBrowser + NWListener for `_dgsync._tcp` with TXT records)
**2B**: `SyncCertStore` (AndroidKeystore P-256), `SyncDiscovery` (NsdManager + MulticastLock)

### Sprint 3 — Transport (parallel A‖B)
**3A**: `SyncTransport.swift` (TLS NWConnection, custom verify block scaffolding, length-prefixed I/O)
**3B**: `SyncTransport.kt` (SSLSocket + custom X509TrustManager, framer)

### Sprint 4 — Pairing (parallel A‖B, then joint validation)
**4A**: `SyncPairing.swift` + `PairingSheet.swift` (SAS UI, 60s timeout, 5-min lockout); wire transport to use `SyncKeychain`-pinned fingerprints
**4B**: `SyncPairing.kt` + `PairingActivity.kt` + layouts; wire transport pinning
**4X**: Joint manual pairing session — Mac ↔ Android, SAS digits identical on both screens

### Sprint 5 — Sync engine (parallel A‖B)
**5A**: `SyncEngine.swift` state machine + `SyncStorageBridge.swift` (orchestrates ListEntries/GetEntry/PutEntry per canonical merge); AppDelegate hooks (start/stop/resume; rate-limited 5-min auto-trigger on `applicationDidBecomeActive`)
**5B**: `SyncService.kt` (foreground, dataSync type, notification channel); `SyncEngine.kt` StateFlow; MainActivity bind/unbind; BootReceiver restart-after-reboot; per-peer watermark in AppSettings

### Sprint 6 — Image sync + Settings UI (parallel A‖B)
**6A**: image sync (ListImages/GetImage/chunked, atomic rename, 25 MiB cap); `SyncSettingsSection.swift` (GroupBox, all 4 states); menu-bar bezel for background-sync success
**6B**: `SyncImageManager.kt` (filesDir/images/, manifest diff, .partial → atomic rename); `▌ SYNC` section in `activity_settings.xml`; foreground notification with progress + Cancel action

### Sprint 7 — Hardening + cross-platform integration (parallel A‖B‖C)
**7A**: macOS failure paths (peer disappears, TLS pin fail, disk full, malformed); 5000-entry+2GB-images stress
**7B**: Android failure paths (Wi-Fi off, Doze, MulticastLock loss, frame > 1MiB)
**7X (security review)**: pairing-pcap inspection, frame-parser fuzz both sides, threat model validation

### Sprint 8 — Release
- Update `CLAUDE.md` (both projects) + `README.md` + new `SYNC.md` (protocol + threat model + pairing flow)
- Build artifacts: macOS `.app` + `.dmg` via existing `build.sh`; Android signed APK via existing `keystore.properties`
- Tag versions: macOS v1.31.0, Android v1.31.0

### v1.5 (deferred, not in v1)
- Android per-entry image rendering: extend `MarkdownParser.kt` to resolve `./images/` against `filesDir/images`; ImageSpan or Markwon images plugin
- Android per-entry image insertion: SAF picker → copy to `filesDir/images` in `EditorActivity`

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Local-network permission prompt timing on macOS (`NSLocalNetworkUsageDescription`) | Defer Bonjour startup until user toggles "Enable sync" on; never browse on launch |
| `dbQueue` activation breaks existing call sites | Wrap in `dbQueue.sync` to preserve synchronous API; comprehensive grep for every `StorageManager.shared.*` call before merge |
| Conscrypt provider conflict on Android API 26-28 | Install as `Security.insertProviderAt(..., 1)` early in Application.onCreate; verify TLS 1.3 negotiates in instrumented test |
| Same-day same-content append duplication across re-sync | `local.content.contains(separator + remote.trim())` idempotency guard in canonical rule |
| Clock skew between devices | 60s tie window; beyond that, raw LWW with explicit acceptance |
| Pairing key loss / device reset | "Unpair" wipes Keychain identity + pinned fingerprint; re-pair flow identical to first pair |
| Foreground service notification annoyance on Android | IMPORTANCE_LOW, hidden during discovery-only mode, only visible during active transfer |
| Sync race vs auto-save | `dbQueue` is the single serialization point; in-flight saves serialize naturally with sync writes |
| Same-LAN MitM during pairing | SAS over user's eyes (1e-6 collision); 5-min lockout after a single mismatch; re-pair required if SAS rejected |

## Verification plan

### Functional (run after Sprint 7)
1. Cold pair: both apps freshly installed, both on same Wi-Fi, both unpaired → tap "Pair" on either, SAS displayed, identical 6 digits, confirm both → "Paired with X" appears within 5s
2. First sync: each side has 50 unique entries → both converge to 100 entries; all classified as "inserted"
3. No-op sync: zero-byte transfer beyond `Hello`/`Bye` + index frames
4. Conflict (concurrent edit): same date, both apps edit content within 30s → both end up with `originalA \n\n---\n\n originalB`. Re-sync: idempotent (no second separator)
5. LWW (separated edit): same date, edits 5 minutes apart → newer wins; older's content not preserved
6. Image transfer: 10 images macOS → Android (incl. one >5 MB); SHA-256 verifies; references resolve in macOS (Android shows broken-image — expected for v1)
7. Network drop: kill Wi-Fi at 50% image transfer; reconnect; next sync resumes (tmp cleaned, missing image fetched)
8. Peer offline: graceful timeout in 10s; no UI hang; Settings shows "No devices found"

### Security (run after Sprint 7)
- Cert tampering: swap peer cert post-pairing → handshake fails with `ERR_PEER_FPR_CHANGED`
- SAS brute force: 1 mismatch → 5-min lockout enforced via monotonic timer (clock-change-immune)
- Replay: pcap capture of pairing → replay against fresh peer → TLS handshake fails (different ephemeral keys)
- Frame attack: send 10 MiB frame → disconnect within 1 frame budget
- Wrong protocol version: peer with `pv=99` → rejected at `Hello` with `version_too_new`

### Build artifacts (Sprint 8)
- macOS: `cd DigitalGram && ./build.sh` → notarization-ready `.app` at `build/Build/Products/Release/DigitalGram.app`; `./create_dmg.sh` (in scripts/) for `.dmg`
- Android: `cd DigitalGramAndroid && ./gradlew assembleRelease` → signed APK at `app/build/outputs/apk/release/app-release.apk`; verify with `apksigner verify --verbose <apk>`

## Out of scope for v1 (explicit non-goals)

- Multi-peer (1:N) — strict 1:1 only
- Sync over the internet (relay server) — Bonjour link-local only
- Background-on-resume notifications — silent for v1
- Resumable byte-range image transfers — full re-transfer on failure
- Encrypted-at-rest journal database — only in-flight encryption
- Wi-Fi Direct fallback (when not on the same SSID) — out of v1
- Android per-entry image rendering and insertion — v1.5

## How to pick this up later

1. Re-read this document end-to-end. The "Critical correction" callout above is the single most load-bearing decision.
2. Verify Phase 1 facts are still accurate — the codebase may have drifted. Spot-check:
   - macOS `mergeDatabases` still at `SettingsView.swift:1288-1386`
   - Android `importAndMergeDatabase` still at `JournalDatabase.kt:239`
   - macOS `Info.plist` `CFBundleShortVersionString` (sets the v1 release tag)
   - macOS deployment target hasn't already moved
3. Begin with **Sprint 0**. Its deliverables (wire spec + canonical-merge truth table) are the contract that lets Sprint 1A and 1B run as parallel fresh agents.
4. Spawn fresh agents per sprint. Do not reuse agents across sprints — context-window preservation is the explicit team-members convention.
