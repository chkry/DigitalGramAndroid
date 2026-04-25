# Google Drive Sign-In Issues on Play Store - Troubleshooting Guide

## Problem: Sign-In Cancelled When Installed from Play Store

When users download the app from Play Store, Google Drive sign-in fails with "Sign-in cancelled" or error 10.

## Root Cause

The issue is **SHA-1 fingerprint mismatch**. When Google Play distributes your app:

1. Your upload APK is signed with your **Release Key**
2. Google Play re-signs it with the **Play Store App Signing Key**
3. Users download the re-signed version
4. The device validates the SHA-1 of the re-signed APK against Google Cloud Console

**If the Play Store signing key's SHA-1 isn't in Google Cloud Console → Sign-in fails**

---

## Solution: Add Play Store Signing Key to Google Cloud Console

### Step 1: Get Your Play Store App Signing Key SHA-1

1. Go to **Google Play Console** → Your app
2. Navigate to **Setup** → **App integrity**
3. Look for **"App signing"** section
4. You'll see:
   - **App signing certificate**: This is your Play Store key
   - **SHA-1**: Copy this value
   - **Example**: `F2:7B:8D:94:C6:DC:86:40:4E:42:B2:03:15:BA:BD:FB:32:B4:3F:C9`

### Step 2: Add to Google Cloud Console

1. Go to **Google Cloud Console** → Your DigitalGram project
2. Navigate to **APIs & Services** → **Credentials**
3. Look for existing OAuth clients named:
   - "DigitalGram Android" or similar
4. If you have one, **create a NEW one** for the Play Store key:
   - Click **+ CREATE CREDENTIALS**
   - Select **OAuth client ID**
   - Application type: **Android**
   - Package name: `com.digitalgram.android`
   - SHA-1: (Paste the Play Store key from Play Console)
   - Click **CREATE**

### Step 3: Verify OAuth Consent Screen

1. Go to **APIs & Services** → **OAuth consent screen**
2. Check **Publishing status**:
   - If it says "**Testing**" → Click **PUBLISH APP**
   - Change to **Production**
   - Review and confirm

---

## You Should Now Have 3 OAuth Clients

| Purpose | SHA-1 | Status |
|---------|-------|--------|
| **Play Store** | F2:7B:8D:94:C6:DC:86:40:4E:42:B2:03:15:BA:BD:FB:32:B4:3F:C9 | ✅ For production users |
| **Release Build** | 02:62:27:79:BD:4E:19:C5:F0:9C:95:81:5A:79:8C:9D:DC:AA:98:7A | For testing signed APKs |
| **Debug Build** | F8:51:52:7B:FD:E1:12:3A:97:56:7E:90:B0:E1:D0:78:F5:C4:AB:BB | For development |

---

## Verification

After making changes:

1. **Wait 5-10 minutes** for Google's servers to propagate
2. **Uninstall the app** completely from your phone
3. **Clear Google account cache** (Settings → Accounts → Google → Remove)
4. **Download fresh from Play Store**
5. Try Google Drive sign-in again

---

## Still Not Working? Diagnostic Steps

### Check App Configuration
- Go to **Settings** → **Google Drive Backup** → Tap **Connect**
- The error message will show:
  - Package name (should be: `com.digitalgram.android`)
  - Version number
  - Error details

### Check Google Cloud Console
1. **Verify all 3 SHA-1s are added**
   - Each needs its own OAuth Android client
2. **Verify Google Drive API is enabled**
   - APIs & Services → Library → Search "Google Drive API" → Should show "API enabled"
3. **Verify package name**
   - Each OAuth client must have exactly: `com.digitalgram.android`

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| Error 10 | OAuth Client ID not found | Add Play Store SHA-1 to Google Cloud |
| Error 12501 | User cancelled | Try again, ensure OAuth consent published |
| Error 12500 | Network/API error | Check internet, wait 5-10 min for propagation |
| No package found | Package name mismatch | Must be exactly `com.digitalgram.android` |

---

## Testing Locally Before Play Store Release

```bash
# Build and test with Release key
cd DigitalGramAndroid
./gradlew assembleRelease

# Install on device (after adding Release SHA-1 to Google Cloud)
adb install -r app/build/outputs/apk/release/app-release.apk

# Test Google Drive sign-in
```

---

## Need Help?

### Useful Links
- [Google Cloud Console](https://console.cloud.google.com/)
- [Google Play Console](https://play.google.com/console/)
- [Google Drive API Setup](https://developers.google.com/drive/android/get-started)

### Debug Info to Collect
When reporting issues, include:
1. Error message shown in app (Settings → Google Drive)
2. Package name from diagnostics
3. Play Store app version
4. Screenshot of Google Cloud Console OAuth clients
5. Screenshot of Play Console App signing certificate

