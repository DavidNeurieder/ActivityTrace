#!/usr/bin/env python3
"""Build debug + release APKs, run unit tests, boot emulator if needed, run instrumented tests."""

import subprocess
import sys
import time
import os

HOME_FOLDER = os.path.expanduser("~")
ANDROID_SDK = f"{HOME_FOLDER}/Android/Sdk"
EMULATOR = f"{ANDROID_SDK}/emulator/emulator"
ADB = f"{ANDROID_SDK}/platform-tools/adb"
DEVNULL = subprocess.DEVNULL


def run(cmd, **kwargs):
    result = subprocess.run(cmd, capture_output=True, text=True, **kwargs)
    if result.returncode != 0:
        print(result.stderr[-500:])
    else:
        print(result.stdout[-200:])
    return result.returncode


def step(msg):
    print(f"\n{'='*60}")
    print(f"==> {msg}")
    print(f"{'='*60}")


def build():
    failures = 0
    step("Building debug APK...")
    code = run(["./gradlew", "assembleDebug"], timeout=300)
    if code != 0:
        print("  DEBUG BUILD FAILED")
        failures += 1
    step("Building release APK...")
    code = run(["./gradlew", "assembleRelease"], timeout=300)
    if code != 0:
        print("  RELEASE BUILD FAILED")
        failures += 1
    return failures


def unit_tests():
    step("Running unit tests...")
    code = run(["./gradlew", "test"], timeout=300)
    if code != 0:
        print("  UNIT TESTS FAILED")
        return 1
    return 0

def lint_tests():
    step("Running lint tests...")
    code = run(["./gradlew", "lint"], timeout=300)
    if code != 0:
        print("  LINT TESTS FAILED")
        return 1
    return 0


def boot_emulator_if_needed():
    result = subprocess.run(
        [ADB, "devices"], capture_output=True, text=True, timeout=10
    )
    if "emulator" in result.stdout:
        print("  Emulator already running")
        return

    result = subprocess.run(
        [EMULATOR, "-list-avds"], capture_output=True, text=True, timeout=10
    )
    avds = [a.strip() for a in result.stdout.strip().split("\n") if a.strip()]
    if not avds:
        print("  No AVDs found")
        raise FileNotFoundError("No AVDs available")
    avd = avds[0]
    print(f"  Booting emulator '{avd}'...")

    subprocess.Popen(
        [EMULATOR, "-avd", avd, "-no-snapshot", "-noaudio"],
        stdout=DEVNULL, stderr=DEVNULL,
    )

    subprocess.run([ADB, "wait-for-device"], timeout=120)

    for attempt in range(45):
        result = subprocess.run(
            [ADB, "shell", "getprop", "sys.boot_completed"],
            capture_output=True, text=True, timeout=10,
        )
        if result.stdout.strip() == "1":
            print(f"  Emulator booted (attempt {attempt + 1})")
            time.sleep(5)
            return
        time.sleep(2)

    raise TimeoutError("Emulator failed to boot within 90s")


def connected_tests():
    step("Running instrumented tests...")
    code = run(["./gradlew", "connectedDebugAndroidTest"], timeout=600)
    if code != 0:
        print("  INSTRUMENTED TESTS FAILED")
        return 1
    return 0


def main():
    failures = []

    b = build()
    if b:
        failures.append(f"build ({b} variant(s) failed)")

    l = lint_tests()
    if l:
        failures.append("lint tests")

    u = unit_tests()
    if u:
        failures.append("unit tests")

    try:
        boot_emulator_if_needed()
    except Exception as e:
        print(f"  EMULATOR ERROR: {e}")
        failures.append("emulator boot")

    if "emulator boot" not in failures:
        c = connected_tests()
        if c:
            failures.append("instrumented tests")

    print(f"\n{'='*60}")
    if failures:
        print(f"FAILURES: {', '.join(failures)}")
        sys.exit(1)
    else:
        print("ALL PASSED")


if __name__ == "__main__":
    main()
