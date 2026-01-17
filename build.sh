#!/bin/bash

# Build script for DigitalGram Android

echo "Building DigitalGram Android..."

cd "$(dirname "$0")"

# Make gradlew executable if not already
chmod +x gradlew

# Clean build
echo "Cleaning previous builds..."
./gradlew clean

# Build debug APK
echo "Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "Debug APK location:"
    echo "  app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To build release APK, run:"
    echo "  ./gradlew assembleRelease"
else
    echo "❌ Build failed!"
    exit 1
fi
