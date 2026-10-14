#!/bin/bash
# Setup script for Michel Mastering PRO
# Generates Gradle wrapper files

echo "=== Michel Mastering PRO - Setup ==="
echo ""

# Check if Gradle is available
if command -v gradle &> /dev/null; then
    echo "Found Gradle: $(gradle --version | head -3)"
    gradle wrapper --gradle-version 8.4
    echo "✅ Gradle wrapper generated successfully"
else
    echo "Gradle not found in PATH."
    echo ""
    echo "To generate the Gradle wrapper, install Gradle and run:"
    echo "  gradle wrapper --gradle-version 8.4"
    echo ""
    echo "Or open the project in Android Studio and it will generate the wrapper automatically."
    echo ""
    echo "For GitHub Actions: The workflow uses gradle/actions/setup-gradle@v3"
    echo "which handles wrapper generation automatically."
fi

echo ""
echo "=== Build Commands ==="
echo "./gradlew assembleDebug    - Build debug APK"
echo "./gradlew assembleRelease  - Build release APK"
echo ""
echo "APK output: app/build/outputs/apk/debug/app-debug.apk"
