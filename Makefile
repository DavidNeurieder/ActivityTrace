.PHONY: build build-release test lint run connected-test clean full-test

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

clean:
	./gradlew clean

full-test:
	python3 build_and_test.py
