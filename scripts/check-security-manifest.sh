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

XML=$("$AAPT" dump xmltree "$APK" AndroidManifest.xml)
fail=0

if echo "$XML" | grep -qE 'usesCleartextTraffic[^=]*=(true|"0x1")'; then
    echo "FAIL: release app declares android:usesCleartextTraffic=true" >&2
    fail=1
fi

if echo "$XML" | grep -qE 'allowBackup[^=]*=(true|"0x1")'; then
    echo "FAIL: release app declares android:allowBackup=true" >&2
    fail=1
fi

if echo "$XML" | grep -qE 'debuggable[^=]*=(true|"0x1")'; then
    echo "FAIL: release app declares android:debuggable=true" >&2
    fail=1
fi

if ! echo "$XML" | grep -q 'dataExtractionRules'; then
    echo "FAIL: release app has no android:dataExtractionRules backup guard" >&2
    fail=1
fi

if [ "$fail" -ne 0 ]; then
    echo "$XML" >&2
    exit 1
fi

echo "OK: release manifest forbids cleartext, is not debuggable, disables allowBackup, and guards backups"