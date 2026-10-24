#!/usr/bin/env bash
# Needs JDK 17 and the Android SDK (ANDROID_HOME). Run from the launcher/ folder.
cd "$(dirname "$0")"
[ -n "${ANDROID_HOME:-}" ] || { echo "Set ANDROID_HOME to your Android SDK (Android Studio: ~/Library/Android/sdk or ~/Android/Sdk)"; exit 1; }
echo "sdk.dir=$ANDROID_HOME" > local.properties
if [ ! -x gradlew ]; then
  command -v gradle >/dev/null || { echo "Install Gradle 8.5+ (brew install gradle) or open launcher/ in Android Studio and build there"; exit 1; }
  gradle wrapper --gradle-version 8.5
fi
./gradlew assembleDebug && echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
