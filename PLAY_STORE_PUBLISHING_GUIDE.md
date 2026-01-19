# Google Play Store Publishing Guide for DigitalGram

## Prerequisites Completed ✓
- ✓ Release keystore created: `digitalgram-release.jks`
- ✓ Build configuration updated for signed releases
- ✓ Keystore properties file created

## Step 1: Update keystore.properties
Edit `keystore.properties` and replace the placeholder passwords with the actual passwords you entered when creating the keystore:

```properties
storePassword=YOUR_ACTUAL_KEYSTORE_PASSWORD
keyPassword=YOUR_ACTUAL_KEY_PASSWORD
keyAlias=digitalgram
storeFile=digitalgram-release.jks
```

**IMPORTANT:** Keep this file secure and never commit it to version control!

## Step 2: Build the Release App Bundle (AAB)
Run the following command to build the release bundle:

```bash
./gradlew bundleRelease
```

The signed AAB will be generated at:
`app/build/outputs/bundle/release/app-release.aab`

## Step 3: Create Google Play Developer Account
1. Go to: https://play.google.com/console
2. Sign in with your Google account
3. Pay the one-time $25 registration fee
4. Complete the account details

## Step 4: Create Your App in Play Console
1. Click "Create app"
2. Fill in the required details:
   - **App name:** DigitalGram
   - **Default language:** English (United States)
   - **App or game:** App
   - **Free or paid:** Free (or Paid if you prefer)
3. Accept the declarations and create the app

## Step 5: Set Up App Content
Complete the following sections in the Play Console:

### Privacy Policy
- You need to host a privacy policy URL
- Example content for a journal app is provided below

### App Access
- Specify if the app requires login or special access
- For DigitalGram: Select "All functionality is available without special access"

### Ads
- Declare whether your app contains ads
- For DigitalGram: Select "No, my app does not contain ads"

### Content Rating
- Complete the questionnaire to get content ratings
- Answer honestly based on your app's content

### Target Audience
- Select appropriate age groups
- For a journal app: "18 and over" or "16 and over"

### Data Safety
- Complete the data safety form
- Declare what data you collect and how you use it
- For DigitalGram:
  - Journal entries stored locally
  - Optional fingerprint/passcode for security
  - No data collected or shared with third parties

## Step 6: Store Listing
Fill in your store listing details:

### App Details
- **App name:** DigitalGram
- **Short description:** (max 80 characters)
  "Minimalist digital journal with markdown support and elegant design"

- **Full description:** (max 4000 characters)
```
DigitalGram is a beautiful and minimalist digital journal designed for thoughtful reflection and daily writing.

✨ KEY FEATURES:
• Elegant timeline view of all your entries
• Rich markdown formatting support (bold, italic, headers, links, code blocks)
• Live markdown preview as you write
• Multiple beautiful themes (Beige, Dark, Dusk, Forest, Ocean, Lavender)
• Custom wallpaper backgrounds
• Timeline visualization (simple or detailed)
• Secure with fingerprint or passcode lock
• Fullscreen writing mode
• Export and backup your entries
• Completely offline - your data stays on your device

📝 WRITING EXPERIENCE:
DigitalGram provides a distraction-free writing environment with markdown toolbar for easy formatting. See your formatted text in real-time with the preview mode.

🎨 PERSONALIZATION:
Choose from carefully crafted color themes or create your own custom theme. Set wallpapers to make your journal uniquely yours.

🔒 PRIVACY & SECURITY:
Your journal entries are stored locally on your device. Optional biometric or passcode protection keeps your thoughts private. No accounts, no cloud sync, no data collection.

Perfect for daily journaling, note-taking, creative writing, or personal reflection.
```

### Graphics Assets Required:
- **App icon:** 512 x 512 px (already created)
- **Feature graphic:** 1024 x 500 px
- **Phone screenshots:** At least 2, up to 8 (min 320px, max 3840px on shortest side)
- **7-inch tablet screenshots:** Optional but recommended
- **10-inch tablet screenshots:** Optional

### Contact Details
- **Email:** Your support email address
- **Website:** (Optional) Your website or GitHub repository
- **Phone:** (Optional)

## Step 7: Upload the App Bundle
1. Go to "Production" → "Create new release"
2. Upload `app-release.aab`
3. Add release notes:
```
Initial release of DigitalGram

Features:
- Beautiful timeline journal interface
- Markdown formatting support
- Multiple themes
- Biometric/passcode security
- Offline-first design
- Custom wallpapers
- Export functionality
```

## Step 8: Review and Publish
1. Complete all required sections (they'll show checkmarks when done)
2. Review your app details in the dashboard
3. Click "Send for review"
4. Google will review your app (usually takes 1-3 days)
5. You'll receive an email when it's approved and published

## Important Notes

### Version Management
- **versionCode:** Integer that must increase with each update (currently 1)
- **versionName:** Display version (currently "1.0")

To update the app later, increment these in `app/build.gradle.kts`:
```kotlin
versionCode = 2
versionName = "1.1"
```

### App Bundle vs APK
- Play Store requires **AAB (Android App Bundle)** format
- Google generates optimized APKs for different device configurations
- Smaller download sizes for users

### Testing Before Publishing
You can use internal testing or closed testing to test the app with a limited audience before releasing to production.

## Privacy Policy Template

If you need a privacy policy, here's a basic template:

```
Privacy Policy for DigitalGram

Last updated: [Date]

DigitalGram ("we", "our", or "us") respects your privacy.

Data Storage
All journal entries and app data are stored locally on your device. We do not collect, transmit, or store any of your personal data on our servers.

Security
You can optionally protect your journal with fingerprint authentication or a passcode. This security is managed entirely on your device.

Permissions
- Storage: To save your journal entries and wallpapers locally
- Biometric: Optional, for fingerprint authentication
- Notifications: Optional, for reminders

Data Sharing
We do not share any data with third parties. Your journal entries never leave your device unless you explicitly export them.

Changes to This Policy
We may update this privacy policy from time to time. We will notify you of any changes by posting the new policy in the app.

Contact Us
If you have questions about this privacy policy, contact us at: [your-email@example.com]
```

## Quick Commands Reference

```bash
# Build release AAB
./gradlew bundleRelease

# Build release APK (for testing)
./gradlew assembleRelease

# Clean and build
./gradlew clean bundleRelease

# Check signing config
./gradlew signingReport
```

## Troubleshooting

**Build fails with signing error:**
- Verify `keystore.properties` has correct passwords
- Check that `digitalgram-release.jks` exists in project root

**AAB upload fails:**
- Ensure versionCode is higher than any previous upload
- Check that the package name matches: `com.digitalgram.android`

**App rejected:**
- Review Google's feedback carefully
- Common issues: Missing privacy policy, content rating not completed, inappropriate content

## Post-Launch

After your app is published:
1. Monitor user reviews and respond promptly
2. Track crashes and errors in Play Console
3. Plan updates based on user feedback
4. Keep the app updated with latest Android versions

Good luck with your launch! 🚀
