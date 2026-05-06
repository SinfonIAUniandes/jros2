#!/bin/bash
set -e
export JAVA_HOME='/c/Program Files/Android/Android Studio/jbr'
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_NDK='/c/Users/David.DESKTOP-A6NC9IE/AppData/Local/Android/Sdk/ndk/30.0.14904198'
cd /c/Users/David.DESKTOP-A6NC9IE/Desktop/Cuevas/WearROS2/jros2
echo "=== Starting ARM64 build ==="
bash build-android-arm64.bash
echo "=== ARM64 build complete ==="
echo "=== Publishing AAR to Maven Local ==="
cd /c/Users/David.DESKTOP-A6NC9IE/Desktop/Cuevas/WearROS2/jros2/android
../gradlew -p . publishReleasePublicationToMavenLocal
echo "=== AAR published ==="
echo "=== Rebuilding Android app ==="
cd /c/Users/David.DESKTOP-A6NC9IE/Desktop/Cuevas/WearROS2/jros2_cellphone_interface
./gradlew --stop
./gradlew clean assembleDebug
echo "=== App built successfully ==="
echo "=== APK location ==="
find app/build/outputs/apk -name '*.apk' 2>/dev/null
echo "=== ALL DONE ==="
