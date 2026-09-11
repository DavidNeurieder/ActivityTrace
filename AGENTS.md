# Session context for Activity Trace Android project

## Project
- **Name:** Activity Trace
- **License:** GPL-3.0-only
- **Distribution:** F-Droid (no Google Play)
- **Architecture:** Single-Activity, Jetpack Compose, Material 3
- **Database:** Room + SQLCipher (AES-256-CBC via Android Keystore/StrongBox)
- **Search:** SQLite FTS5 with porter tokenizer (no AI/ML)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 36
- **Package:** com.activitytrace

## Build
```bash
./gradlew assembleDebug     # debug build
./gradlew assembleRelease   # release build (minified, arm64-only)
make build                  # same as assembleDebug
make test                   # unit tests
make full-test              # builds both + unit tests + boots emulator + instrumented tests
make run                    # install debug APK on connected device
python3 build_and_test.py   # automate full CI workflow
```

## Dependencies (version catalog: gradle/libs.versions.toml)
- AGP 8.2.2 / Kotlin 1.9.22 / Compose Compiler 1.5.10
- Compose BOM 2024.01.00 (Material 3, dynamic color)
- Room 2.6.1 + KSP for codegen
- WorkManager 2.9.0 (scheduled retention cleanup)
- SQLCipher 4.17.0 (net.zetetic:sqlcipher-android; androidx.sqlite forced to 2.4.0 for Kotlin 1.9 compatibility)
- FTS5 table created via Room callback (not Room annotation, due to KSP resolution order)
- Search uses SQL LIKE `%keyword%` (substring match, no FTS5); FTS5 table retained for content sync triggers

## Key files
- **Config:** `app/build.gradle.kts`, `gradle/libs.versions.toml`
- **Entry point:** `MainActivity.kt` → `SearchScreen.kt`
- **Capture:** `ActivityTraceNotificationListener.kt`, `AccessibilityCaptureService.kt`
- **Store:** `ActivityTraceDatabase.kt` (Room + SQLCipher), `CaptureDao.kt`, `EncryptionManager.kt`, `RetentionCleanupWorker.kt`
- **Search:** `QueryParser.kt`, `SearchEngine.kt`
- **Demo mode:** `demo/` package — curated deterministic showcase (`DemoAppCatalog`, `DemoClock` anchored `2026-09-08T06:30:00Z`, `DemoStories` ×4, `DemoDocuments` ≤15, `DemoBackgroundActivity`) assembled+validated by `DemoDataGenerator.showcaseDataset()`/`DemoDatasetValidator`, benchmark corpus in `DemoRecordFactory` (dev-only `SearchBenchmark`), `DemoDataRepository` facade, `ui/DemoDataScreen.kt`. Schema column `demo_dataset_id` (`activitytrace_showcase_v1`), never touches real captures.
- **File indexing:** `FileIndexer.kt`, `FileIndexingWorker.kt`
- **Model:** `CapturedItem.kt`
- **Theme:** `ui/theme/Theme.kt` (dynamic color API 31+, fallback green seed)
- **F-Droid metadata:** `fastlane/metadata/android/`
- **Build tooling:** `Makefile`, `build_and_test.py`, `AGENTS.md`

## F-Droid release checklist ✓
- LICENSE (GPL-3.0) in repo root ✓
- README.md with build instructions ✓
- Screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/` ✓
- `fastlane/metadata/android/` with descriptions and changelogs ✓
- All deps verified FOSS (SQLCipher BSD-3, PDFBox Apache-2.0, no Play Services) ✓
- Gradle reproducible builds: `android.r8.minification-repository-mode=true` in `gradle.properties` ✓
- Disable logging in release builds: `-assumenosideeffects` for `android.util.Log` in `proguard-rules.pro` ✓
- ProGuard minification enabled for release builds ✓
- `isDebuggable = false` for release builds ✓
- Note: `reproducibleBuildEnabled` requires AGP 8.5+; current AGP 8.2.2 uses R8 deterministic mode instead

## Security CI (P0.5)
- `.github/workflows/security.yml`: rebuilds release + unit/lint/instrumented/vuln/secret checks on push/PR
- `scripts/check-release-manifest.sh`: fails if the release APK declares `android.permission.INTERNET` (must NOT be added; app is fully offline)
- `scripts/check-exported-components.sh`: lists `exported` components from the release APK for human review (`NotificationWidget` exported=true is required for the app widget)
- Gradle task `:app:verifyNoInternetPermissionInRelease` — same guard, name does not collide with AGP's own `checkReleaseManifest` task
- `.github/dependabot.yml`: weekly scans for Gradle + GitHub Actions
- No INTERNET permission in the manifest; do not add it (no network features)

## Development notes
- Run lint: `./gradlew lint`
- Run tests: `./gradlew test`
- Compose reports at `app/build/reports/`
- PRs and tags used for release; no APK uploads (F-Droid builds from source)

## Room schema & migrations
- `@Database(exportSchema = true)`; KSP arg `room.schemaLocation="$projectDir/schemas"` in `app/build.gradle.kts`
- Schema JSONs committed to `app/schemas/com.activitytrace.store.ActivityTraceDatabase/{version}.json`
- Unit tests (Robolectric) read schemas via `android.sourceSets["debug"]/["release"].assets.srcDir("$projectDir/schemas")` — the JSONs must land in the apk-for-local-test that Robolectric mounts. Robolectric does NOT serve test source-set assets, and test `src/test/resources` are not on the unit-test classpath (AGP routes Android source-set resources to `java_res/<variant>UnitTest/out`, which the worker classloader cannot resolve). Consequence: the two small schema JSONs also ship in the debug and release APKs
- `MigrationTestHelper` (from `androidx.room:room-testing`) requires an Instrumentation; under Robolectric use `ShadowInstrumentation.getInstrumentation()` — `RuntimeEnvironment` has no such accessor
- Migration tests must exercise the real migration through Room's open path (see `ActivityTraceDatabaseMigrationTest`), because Room validates the full schema incl. index names after every migration (the dedup-index v6→7 bug was an index-name mismatch)
- When bumping the schema version: bump `version`, add `MIGRATION_x_y` to the builder's `.addMigrations(...)`, build to export the new JSON, and update/add a MigrationTestHelper test
- SQLCipher native libs do NOT load under Robolectric (`UnsatisfiedLinkError: no sqlcipher in java.library.path`). Unit tests must not touch `net.zetetic` open/create paths; test the recovery/key logic via a seam (`DatabaseOpener`, `DatabaseKeyStore.WrappingKeyProvider`) and push real SQLCipher assertions (plaintext-not-in-file, wrong-key-preserves-bytes, `.encoded == null`) to instrumented tests (`DatabaseEncryptionTest`). `System.loadLibrary("sqlcipher")` is called in `ActivityTraceApplication.onCreate`; `UnsatisfiedLinkError` under Robolectric is absorbed by the `catch (Throwable)` there.
- Database-opening failures are never destructive: `ActivityTraceDatabase.tryOpen()` classifies failures (`DatabaseOpenResult` / `RecoveryReason`), persists recovery state, and never deletes the DB or key. No `deleteDatabase` anywhere
- SQLCipher's first creation of a brand-new database can fail its own header self-verify with `file is not a database` (the file is left fully initialized and a subsequent open succeeds). Both `getInstance` and `DatabaseOpener.open()` retry once for this reason; do not remove the retry.
- Backup round-trip (`BackupRoundTripInstrumentedTest`) is deliberately a NON-Compose instrumented class: it runs `ATTACH` + `sqlcipher_export` on the live Room connection, which races the SettingsScreen blocked-apps Flow if that composable is mounted simultaneously (previously intermittent `file is not a database` / `SQLiteDiskIOException` under the Compose test rule). Keep DB-exercising round-trips out of compose UI tests.
- Instrumented (androidTest) JUnit methods must NOT use backtick names containing spaces/parens with a lambda in the body: Kotlin names anonymous lambda classes after the enclosing function (e.g. `Foo$concurrent identical inserts produce one row$1`), and DEX < 040 (minSdk 26 → DEX 037) rejects spaces in class names, so `dexDebugAndroidTest` fails with "Space characters in SimpleName ... are not allowed prior to DEX version 040". Use underscore names (`concurrent_identical_inserts_produce_one_row`) for instrumented tests; Robolectric unit tests are not dexed and can keep spaced backtick names
