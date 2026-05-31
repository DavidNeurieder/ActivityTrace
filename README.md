# Activity Trace

On-device cross-app memory search for Android. No AI, no cloud, no internet permission.

Search across notifications, accessibility screen captures, and indexed files with natural language — all encrypted at rest with **SQLCipher AES-256-CBC** + Android Keystore.

## Features

- **Search anything** — notifications, screen captures, indexed files
- **Natural language queries** — `yesterday`, `last week`, `in:signal`, `type:notification`
- **Prefix & wildcard search** — `test` matches `testing`/`tested`, `*error` matches `fatal error`
- **Full encryption** — SQLCipher + Android Keystore (StrongBox on capable hardware)
- **Zero-network** — no internet permission, no telemetry, no cloud
- **Open source** — GPL-3.0-only, distributed via F-Droid

## Architecture

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 (dynamic color) |
| Architecture | Single-Activity, ViewModel, reactive Flows |
| Storage | Room + SQLCipher (AES-256-CBC, arm64-v8a only) |
| Search | SQLite FTS5 with prefix matching |
| Capture | NotificationListener, AccessibilityService, File indexing (SAF) |
| Retention | WorkManager (configurable periodic cleanup) |

**Min SDK:** 26 (Android 8.0) · **Target SDK:** 36

## Screenshots

| Search | Settings | Onboarding |
|--------|----------|------------|
| _(coming soon)_ | _(coming soon)_ | _(coming soon)_ |

## Build

```bash
./gradlew assembleDebug     # debug build (unminified)
./gradlew assembleRelease   # release build (minified, ProGuard)
```

```bash
./gradlew test               # unit tests
./gradlew lint               # static analysis
./gradlew connectedDebugAndroidTest  # instrumentation tests
```

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 36

## Dependencies

- **Compose BOM 2024.01.00** — Material 3, dynamic color
- **Room 2.6.1** + KSP — type-safe SQLite
- **WorkManager 2.9.0** — background retention cleanup
- **SQLCipher 4.5.4** — encrypted database at rest
- **MockK 1.13.9** — test mocking
- **Turbine 1.0.0** — Flow testing

See [`gradle/libs.versions.toml`](gradle/libs.versions.toml) for the full catalog.

## Key files

| File | Purpose |
|------|---------|
| [`MainActivity.kt`](app/src/main/java/com/activitytrace/MainActivity.kt) | Entry point, onboarding & settings routing |
| [`SearchScreen.kt`](app/src/main/java/com/activitytrace/ui/SearchScreen.kt) | Main search UI with prefix/wildcard matching |
| [`SearchViewModel.kt`](app/src/main/java/com/activitytrace/ui/SearchViewModel.kt) | Reactive search pipeline (flatMapLatest) |
| [`SettingsScreen.kt`](app/src/main/java/com/activitytrace/ui/SettingsScreen.kt) | Permissions, retention, database export |
| [`SearchEngine.kt`](app/src/main/java/com/activitytrace/search/SearchEngine.kt) | FTS5 + LIKE search routing |
| [`QueryParser.kt`](app/src/main/java/com/activitytrace/search/QueryParser.kt) | Natural language date & filter parsing |
| [`EncryptionManager.kt`](app/src/main/java/com/activitytrace/store/EncryptionManager.kt) | Two-tier key derivation (Keystore → fallback) |
| [`ActivityTraceDatabase.kt`](app/src/main/java/com/activitytrace/store/ActivityTraceDatabase.kt) | Room + SQLCipher + FTS5 setup |
| [`CaptureDao.kt`](app/src/main/java/com/activitytrace/store/CaptureDao.kt) | FTS5 MATCH + LIKE queries |
| [`RetentionCleanupWorker.kt`](app/src/main/java/com/activitytrace/store/RetentionCleanupWorker.kt) | Configurable auto-delete via WorkManager |
| [`ActivityTraceNotificationListener.kt`](app/src/main/java/com/activitytrace/capture/ActivityTraceNotificationListener.kt) | Notification capture service |
| [`AccessibilityCaptureService.kt`](app/src/main/java/com/activitytrace/capture/AccessibilityCaptureService.kt) | Screen capture via AccessibilityService |
| [`CaptureIngestor.kt`](app/src/main/java/com/activitytrace/capture/CaptureIngestor.kt) | Unified capture pipeline (write to Room + FTS5) |
| [`PendingIntentSerializer.kt`](app/src/main/java/com/activitytrace/capture/PendingIntentSerializer.kt) | Notification deep link serialization (Parcel ↔ Base64) |
| [`FileIndexer.kt`](app/src/main/java/com/activitytrace/capture/FileIndexer.kt) | SAF directory walk + PDF text extraction |
| [`FileIndexingWorker.kt`](app/src/main/java/com/activitytrace/capture/FileIndexingWorker.kt) | Background file indexing (daily / manual) |

## Privacy

Activity Trace is designed to be **zero-trust by default:**

- No internet permission in the manifest
- No telemetry, no analytics, no crash reporting
- All data encrypted at rest with SQLCipher + Android Keystore
- F-Droid only — no proprietary dependencies, no Google Play Services

## License

GNU General Public License v3.0 only. See [LICENSE](LICENSE).

## Contributing

Contributions are welcome. Please open an issue first to discuss changes.

- Run `./gradlew test` before submitting
- Maintain the GPL-3.0-only license
- No AI/ML dependencies, no cloud features, no Play Services
