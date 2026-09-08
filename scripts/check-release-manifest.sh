#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?ANDROID_HOME or ANDROID_SDK_ROOT is required}}"
AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1)
if [ -z "$AAPT" ]; then
    echo "error: aapt not found under \$ANDROID_HOME/build-tools" >&2
    exit 1
fi

./gradlew :app:assembleRelease --quiet

APK=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
if [ -z "$APK" ]; then
    echo "error: no release APK found in app/build/outputs/apk/release" >&2
    exit 1
fi

PERMISSIONS=$("$AAPT" dump permissions "$APK")
if echo "$PERMISSIONS" | grep -q 'android.permission.INTERNET'; then
    echo "FAIL: release APK declares android.permission.INTERNET" >&2
    echo "$PERMISSIONS" >&2
    exit 1
fi

echo "OK: release APK does not declare android.permission.INTERNET"