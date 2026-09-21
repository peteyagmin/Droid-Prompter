#!/bin/bash
# Manual build script for A500 Teleprompter.
#
# This project is a normal Gradle/Android-Gradle-Plugin project and should
# normally be built with:
#
#   ./gradlew assembleDebug
#
# from a machine with normal internet access (Android Studio does this for
# you). This script exists as a fallback for environments where Gradle
# cannot reach dl.google.com/maven.google.com (so the Android Gradle Plugin
# itself cannot be downloaded), but a local Android SDK platform and build
# tools ARE available. It drives aapt2, javac, dx, zipalign and apksigner
# directly to produce the same debug APK without Gradle or AGP.
#
# Override any of the paths below via environment variables if your tool
# locations differ (e.g. a normal $ANDROID_HOME/platforms/android-XX and
# $ANDROID_HOME/build-tools/XX.X.X on a full SDK install).
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ID="com.a500teleprompter.app"
PACKAGE_PATH="com/a500teleprompter/app"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
AAPT2="${AAPT2:-/usr/bin/aapt2}"
DX="${DX:-/usr/lib/android-sdk/build-tools/debian/dx}"
ZIPALIGN="${ZIPALIGN:-/usr/bin/zipalign}"
APKSIGNER="${APKSIGNER:-/usr/bin/apksigner}"

BUILD_DIR="$PROJECT_ROOT/build"
RES_DIR="$PROJECT_ROOT/app/src/main/res"
MANIFEST="$PROJECT_ROOT/app/src/main/AndroidManifest.xml"
JAVA_SRC_DIR="$PROJECT_ROOT/app/src/main/java/$PACKAGE_PATH"
OUT_DIR="$PROJECT_ROOT/app/build/outputs/apk/debug"
KEYSTORE_DIR="$BUILD_DIR/keystore"
KEYSTORE="$KEYSTORE_DIR/debug.keystore"

for tool in "$AAPT2" "$DX" "$ZIPALIGN" "$APKSIGNER"; do
    if [ ! -x "$tool" ]; then
        echo "Required tool not found or not executable: $tool" >&2
        exit 1
    fi
done
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Android platform jar not found: $ANDROID_JAR" >&2
    exit 1
fi

echo "== Cleaning previous manual build output =="
rm -rf "$BUILD_DIR/res-compiled" "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/apk" "$BUILD_DIR/dex"
mkdir -p "$BUILD_DIR/res-compiled" "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/apk" "$BUILD_DIR/dex" "$OUT_DIR"

echo "== Compiling resources (aapt2 compile) =="
"$AAPT2" compile --dir "$RES_DIR" -o "$BUILD_DIR/res-compiled/res.zip"

echo "== Linking resources + manifest, generating R.java (aapt2 link) =="
"$AAPT2" link \
    -o "$BUILD_DIR/apk/base.apk" \
    --manifest "$MANIFEST" \
    -I "$ANDROID_JAR" \
    --java "$BUILD_DIR/gen" \
    --min-sdk-version 14 \
    --target-sdk-version 14 \
    --version-code 1 --version-name 1.0 \
    "$BUILD_DIR/res-compiled/res.zip"

echo "== Compiling Java sources (javac) =="
javac --release 8 \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD_DIR/obj" \
    -encoding UTF-8 \
    "$JAVA_SRC_DIR"/*.java \
    "$BUILD_DIR/gen/$PACKAGE_PATH/R.java"

echo "== Converting bytecode to Dalvik format (dx) =="
"$DX" --dex --output="$BUILD_DIR/dex/classes.dex" "$BUILD_DIR/obj"

echo "== Assembling unsigned APK =="
cp "$BUILD_DIR/apk/base.apk" "$BUILD_DIR/apk/unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -X -q ../apk/unsigned.apk classes.dex)

echo "== Aligning APK (zipalign) =="
"$ZIPALIGN" -f -p 4 "$BUILD_DIR/apk/unsigned.apk" "$BUILD_DIR/apk/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    echo "== Generating a debug keystore (first run only) =="
    mkdir -p "$KEYSTORE_DIR"
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass android -keypass android \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

echo "== Signing APK (apksigner) =="
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --min-sdk-version 14 \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled false \
    --v4-signing-enabled false \
    --out "$OUT_DIR/app-debug.apk" \
    "$BUILD_DIR/apk/aligned.apk"

echo "== Verifying signature =="
"$APKSIGNER" verify -v --min-sdk-version 14 "$OUT_DIR/app-debug.apk"

echo
echo "Build complete: $OUT_DIR/app-debug.apk"
