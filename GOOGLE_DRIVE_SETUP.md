# Google Drive Integration Setup

## Current Status
Google Drive integration is implemented in the app, but requires OAuth 2.0 credentials to function.

## Error You're Seeing
"Sign-in cancelled" or "Developer Error: OAuth not configured"

## Why This Happens
Google requires OAuth 2.0 credentials and SHA-1 fingerprint registration to authenticate with Google Drive API.

## How to Fix

### Step 1: Get Your SHA-1 Fingerprint

**For Debug Build (current):**
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

**Your Debug SHA-1:** `F8:51:52:7B:FD:E1:12:3A:97:56:7E:90:B0:E1:D0:78:F5:C4:AB:BB`

**For Release Build:**
```bash
keytool -list -v -keystore /path/to/your/release/keystore -alias your-key-alias
```

### Step 2: Set Up Google Cloud Project

1. **Go to Google Cloud Console:**
   - Visit: https://console.cloud.google.com/

2. **Create or Select a Project:**
   - Click "Select a project" → "New Project"
   - Name: `DigitalGram` or similar
   - Click "Create"

3. **Enable Google Drive API:**
   - Go to "APIs & Services" → "Library"
   - Search for "Google Drive API"
   - Click "Enable"

### Step 3: Create OAuth 2.0 Credentials

1. **Go to Credentials:**
   - "APIs & Services" → "Credentials"
   - Click "+ CREATE CREDENTIALS"
   - Select "OAuth client ID"

2. **Configure Consent Screen (if prompted):**
   - User Type: "External" (or "Internal" if using Google Workspace)
   - App name: `DigitalGram`
   - User support email: your email
   - Developer contact: your email
   - Click "Save and Continue"
   - Skip "Scopes" → "Save and Continue"
   - Skip "Test users" (or add your own email for testing)
   - Click "Back to Dashboard"

3. **Create Android OAuth Client:**
   - Application type: "Android"
   - Name: `DigitalGram Android`
   - Package name: `com.digitalgram.android`
   - SHA-1 certificate fingerprint: `F8:51:52:7B:FD:E1:12:3A:97:56:7E:90:B0:E1:D0:78:F5:C4:AB:BB`
   - Click "Create"

### Step 4: Test the Integration

1. **Rebuild and install the app** (already done)
2. **Open Settings** → Scroll to "GOOGLE DRIVE BACKUP"
3. **Click "Connect"**
4. **Sign in with your Google account**
5. **Grant permissions** when prompted
6. **Test backup/restore**

## Alternative: Use Dropbox Instead

If you don't want to set up Google Cloud credentials, **Dropbox backup is fully functional** and doesn't require OAuth configuration:

1. Scroll to "DROPBOX BACKUP" in Settings
2. Click "Connect"
3. Authorize in browser
4. Use "Backup" and "Restore" buttons

## Troubleshooting

### Still Getting "Sign-in Cancelled"
- Make sure you added the **exact SHA-1** fingerprint to Google Cloud Console
- Verify the **package name** is `com.digitalgram.android`
- Wait 5-10 minutes after creating credentials for them to propagate
- Clear app data and try again

### "Access Denied" or "Invalid Credentials"
- Ensure Google Drive API is **enabled** in your project
- Check that OAuth consent screen is **configured**
- Make sure you're signing in with a **Google account** (not other providers)

### Development vs Production
- **Debug builds** use the debug keystore SHA-1 (already provided above)
- **Release builds** need the release keystore SHA-1 (you need to generate this)
- You'll need to add **both SHA-1s** to Google Cloud Console for both to work

## Quick Reference

**Package Name:** `com.digitalgram.android`  
**Play Store App Signing SHA-1:** `F2:7B:8D:94:C6:DC:86:40:4E:42:B2:03:15:BA:BD:FB:32:B4:3F:C9`  
**Release Key SHA-1:** `02:62:27:79:BD:4E:19:C5:F0:9C:95:81:5A:79:8C:9D:DC:AA:98:7A`  
**Debug SHA-1:** `F8:51:52:7B:FD:E1:12:3A:97:56:7E:90:B0:E1:D0:78:F5:C4:AB:BB`  
**Google Cloud Console:** https://console.cloud.google.com/  
**Required API:** Google Drive API  
**OAuth Scopes:** `https://www.googleapis.com/auth/drive.file`

## Important: For Play Store Apps

When your app is distributed via Play Store, Google Play re-signs your app with the **App Signing Key**. You MUST create separate OAuth 2.0 Android client IDs in Google Cloud Console for:

1. **Play Store App Signing Key** (for production users downloading from Play Store)
   - SHA-1: `F2:7B:8D:94:C6:DC:86:40:4E:42:B2:03:15:BA:BD:FB:32:B4:3F:C9`
   
2. **Your Upload/Release Key** (for testing signed APKs before upload)
   - SHA-1: `02:62:27:79:BD:4E:19:C5:F0:9C:95:81:5A:79:8C:9D:DC:AA:98:7A`

3. **Debug Key** (for development builds)
   - SHA-1: `F8:51:52:7B:FD:E1:12:3A:97:56:7E:90:B0:E1:D0:78:F5:C4:AB:BB`

### Creating Multiple OAuth Clients

For each SHA-1 above, go to Google Cloud Console → APIs & Services → Credentials → Create Credentials → OAuth client ID → Android, and create a separate entry with:
- Package name: `com.digitalgram.android`
- SHA-1: (the respective fingerprint)

## Notes

- Google Drive integration is optional - Dropbox works without extra setup
- You only need to do this setup once
- The same OAuth credentials work for all users of your app
- For production, consider adding your release keystore SHA-1 as well
