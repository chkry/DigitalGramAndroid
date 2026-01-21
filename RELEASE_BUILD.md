# DigitalGram Android - Play Store Release Build

## Build Information
**Date:** January 19, 2026  
**Version:** 1.0 (versionCode: 1)  
**Target SDK:** Android 14 (API 35)  
**Minimum SDK:** Android 8.0 (API 26)

## Build Artifacts

### For Play Store Submission (Recommended)
**Android App Bundle (AAB):**
- Location: `app/build/outputs/bundle/release/app-release.aab`
- Size: 3.6 MB
- Status: ✅ Signed and ready for Play Store upload
- Format: AAB (Android App Bundle) - Required for Google Play Store

### For Testing/Distribution
**Release APK:**
- Location: `app/build/outputs/apk/release/app-release.apk`
- Size: 2.4 MB  
- Status: ✅ Signed and ready for sideloading/testing
- Format: APK (Android Package)

## Build Configuration

### Release Features
- **Code Minification:** Enabled (ProGuard/R8)
- **Resource Shrinking:** Enabled
- **Signing:** Release keystore configured
- **Optimization:** Full R8 optimization enabled

### Keystore Details
- **Alias:** digitalgram
- **Algorithm:** RSA 2048-bit
- **Validity:** 10,000 days
- **Location:** `/Users/chkry/Documents/CODE/digitalgram-release.jks`

⚠️ **Important:** Keep your keystore file and passwords secure! You'll need them for all future app updates.

## Submission Steps for Google Play Console

1. **Upload the AAB file:**
   - Go to Google Play Console
   - Navigate to your app → Production → Create new release
   - Upload `app/build/outputs/bundle/release/app-release.aab`

2. **Complete the release details:**
   - Add release notes
   - Review app content rating
   - Ensure privacy policy is set (if applicable)
   - Complete store listing

3. **Submit for review:**
   - Review all information
   - Submit to production/beta/alpha track

## Testing the Build

To test the APK before submission:
```bash
# Install on connected device/emulator
adb install app/build/outputs/apk/release/app-release.apk

# Or install with replacement
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Rebuilding

To rebuild the release version:
```bash
# Clean and rebuild AAB
./gradlew clean bundleRelease

# Or rebuild APK
./gradlew clean assembleRelease
```

## ProGuard Rules
Custom ProGuard rules have been configured for:
- Room Database entities
- ViewModels
- Crypto libraries (Tink)
- JSR 305 annotations

## Notes
- First production release
- All security features enabled
- Biometric authentication included
- Encrypted storage configured
