.PHONY: build build-release test lint run connected-test screenshots clean full-test

build:
	./gradlew assembleDebug

build-release:
	./gradlew assembleRelease

test:
	./gradlew test

lint:
	./gradlew lint

run:
	./gradlew installDebug

connected-test:
	./gradlew connectedDebugAndroidTest

# Run the deterministic 7-scenario screenshot suite and pull the PNGs.
# Requires a connected emulator/device with animations disabled (see
# ScreenshotTest header). The suite is a no-op unless `screenshots=true`.
screenshots:
	adb shell settings put global animator_duration_scale 0
	adb shell settings put global transition_animation_scale 0
	adb shell settings put global window_animation_scale 0
	./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.screenshots=true \
		-Pandroid.testInstrumentationRunnerArguments.screenshotsOutputDir=/sdcard/screenshots
	adb pull /sdcard/screenshots app/src/main/play/phoneScreenshots

clean:
	./gradlew clean

full-test:
	python3 build_and_test.py
