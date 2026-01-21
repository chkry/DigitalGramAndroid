# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Room entities
-keep class com.digitalgram.android.data.** { *; }

# Keep ViewModel classes
-keep class com.digitalgram.android.ui.** { *; }

# JSR 305 annotations
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# Tink crypto
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.crypto.tink.** { *; }

# Google API client (not used but referenced by Tink)
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# Security: Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Security: Obfuscate security-related classes
-keep class com.digitalgram.android.PasscodeActivity { *; }
-keep class com.digitalgram.android.data.AppSettings {
    public <methods>;
}

# Keep UCrop classes
-keep class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# Keep Biometric classes
-keep class androidx.biometric.** { *; }

# Security: Prevent reflection on sensitive methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
