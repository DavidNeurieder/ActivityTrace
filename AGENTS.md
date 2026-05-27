# Session context for Activity Trace Android project

## Project
- **Name:** Activity Trace
- **License:** GPL-3.0-only
- **Distribution:** F-Droid (no Google Play)
- **Architecture:** Single-Activity, Jetpack Compose, Material 3
- **Database:** Room + SQLCipher (AES-256-CBC via Android Keystore/StrongBox)
- **Search:** SQLite FTS5 with porter tokenizer (no AI/ML)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34
- **Package:** com.activitytrace

## Build
```bash
./gradlew assembleDebug   # debug build
./gradlew assembleRelease  # release build (minified)
```

## Dependencies (version catalog: gradle/libs.versions.toml)
- AGP 8.2.2 / Kotlin 1.9.22 / Compose Compiler 1.5.10
- Compose BOM 2024.01.00 (Material 3, dynamic color)
- Room 2.6.1 + KSP for codegen
- WorkManager 2.9.0 (scheduled retention cleanup)
- SQLCipher 4.5.4 (net.zetetic:android-database-sqlcipher)
- FTS5 table created via Room callback (not Room annotation, due to KSP resolution order)
- Search currently uses LIKE (FTS5 table exists, will be switched when query builder is refined)

## Key files
- **Config:** `app/build.gradle.kts`, `gradle/libs.versions.toml`
- **Entry point:** `MainActivity.kt` → `SearchScreen.kt`
- **Capture:** `ActivityTraceNotificationListener.kt`, `ClipboardCaptureService.kt`, `AccessibilityCaptureService.kt`, `ScreenshotCaptureService.kt`
- **Store:** `ActivityTraceDatabase.kt` (Room + SQLCipher), `CaptureDao.kt`, `EncryptionManager.kt`, `RetentionCleanupWorker.kt`
- **Search:** `QueryParser.kt`, `SearchEngine.kt`, `ContactMatcher.kt`
- **Model:** `CapturedItem.kt`
- **Theme:** `ui/theme/Theme.kt` (dynamic color API 31+, fallback green seed)
- **F-Droid metadata:** `fastlane/metadata/android/`

## F-Droid release checklist ✓
- LICENSE (GPL-3.0) in repo root ✓
- README.md with build instructions ✓
- `fastlane/metadata/android/` with descriptions and changelogs ✓
- All deps verified FOSS (SQLCipher BSD-3, PDFBox Apache-2.0, no Play Services) ✓
- Gradle reproducible builds: `android.r8.minification-repository-mode=true` in `gradle.properties` ✓
- Disable logging in release builds: `-assumenosideeffects` for `android.util.Log` in `proguard-rules.pro` ✓
- ProGuard minification enabled for release builds ✓
- `isDebuggable = false` for release builds ✓
- Note: `reproducibleBuildEnabled` requires AGP 8.5+; current AGP 8.2.2 uses R8 deterministic mode instead

## Development notes
- Run lint: `./gradlew lint`
- Run tests: `./gradlew test`
- Compose reports at `app/build/reports/`
- PRs and tags used for release; no APK uploads (F-Droid builds from source)
