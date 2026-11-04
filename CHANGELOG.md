# Changelog

## v0.11.0 — 2026-11-02

### New
- Android 17 (API 37) support — `compileSdk`/`targetSdk` raised to 37
- 16 KB page-size support — the release APK and its bundled SQLCipher native library are 16 KB aligned, so the app runs on 16 KB-page devices without compatibility mode

### Improvements
- Library updates: Compose BOM 2026.09.00, Room 2.8.5, WorkManager 2.12.0, SQLCipher 4.19.1, androidx.sqlite 2.7.1, Navigation 2.10.2, Lifecycle 2.11.0
- Kotlin 2.3.21 with the Compose Compiler Gradle plugin (`composeOptions.kotlinCompilerExtensionVersion` removed)

### Technical
- Search screen resolves localized strings with `stringResource()` instead of `context.getString()` inside click handlers
- Instrumented tests run on Android 17: Espresso 3.7.0 (3.5.0 crashes on the removed `InputManager.getInstance()`), and androidTest no longer depends on MockK, whose JVMTI agent is only 4 KB aligned
- `verifyNoInternetPermissionInRelease` is a typed Gradle task again (`Project.exec` was removed in Gradle 9)
- Build: AGP 9.4.1 / Gradle 9.8.1 / Kotlin 2.3.21 / KSP 2.3.12
- AGP 9 stopgaps `android.builtInKotlin=false` and `android.newDsl=false` in `gradle.properties` (both removed in AGP 10); their deprecation notices are silenced with `android.sync.suppressAgpWarnings` and `kotlin.suppressGradlePluginWarnings`, so a normal build is warning-free
- Deprecated-API sweep: `HorizontalDivider`, `androidx.lifecycle.compose.LocalLifecycleOwner` (new `lifecycle-runtime-compose` dependency), `BundleCompat` + `Icon.toBitmap()` for notification images, accessibility node recycling only below API 33, `@OptIn(FlowPreview::class)` for `debounce`
- Build script no longer uses the deprecated legacy accessors: Room schema assets are added via `AndroidSourceDirectorySet.directories`, and the `android {}` block is configured through the `ApplicationExtension` interface
- Dropped the androidx.sqlite 2.4.0 force-resolution hack; Kotlin 2.x metadata no longer needs it
- `SearchEngineApi` interface extracted so UI tests can use a plain fake instead of a mocking framework
- Statistics are computed by SQLite aggregates over the shared search criteria (`SearchCriteria.kt`, `SearchStatistics.kt`) instead of in-memory grouping over the loaded page; they are only queried while the statistics view is visible and never recomputed by scrolling
- Robolectric 4.17, MockK 1.14.11 (JVM-only), coroutines-test 1.11.0, Turbine 1.2.1
- Database schema unchanged (version 10); no migration

### Fixes
- Statistics now aggregate over every matching record in SQLite instead of only the loaded result page; the search list itself stays capped at 50 results per page

---

## v0.10.0 — 2026-09-11

### New
- Demo mode — explore ActivityTrace with curated fictional data (messages, notifications, emails, documents, browsing) via the demo dataset entry; never touches real captures
- Theme selection — system default, light, dark, and OLED black
- Search ranking — results now ordered by relevance (BM25) merged with a recency pool (reciprocal-rank fusion)
- FTS5 full-text search — implicit prefix matching so partial words (`whats`) find matches; FTS5 index backed by content-sync triggers
- Privacy settings screen — documents what gets captured and that events from blocked apps and password fields are never captured
- Welcome screen with capture status dashboard — summary states (ok/partial/off), "Needs setup" rows, and contextual permission explanations

### Improvements
- Encrypted backup/restore with integrity verification (backup envelope with authenticated payload)
- Search results paginated (50/page) instead of loaded at once
- File indexing extraction budget — avoids excessive memory use on large folder scans
- Settings screen reorganized: capture status dashboard now includes the blocked-apps row
- Backup export explicitly labeled as unencrypted plain SQLite
- SQLCipher upgraded to 4.17.0 with `DatabaseOpener`/`DatabaseKeyStore` recovery seams
- Security CI: release-manifest and exported-component checks in `.github/workflows/security.yml`

### Fixes
- Capture toggle no longer shows stale state after toggling permissions
- Export crash on some Android 16 devices (`MediaStore.Downloads.insert()` null)
- Export UI improved with explicit backup/restore/JSON/CSV actions
- SQLite backup no longer fails on retry (stale temp file)
- Duplicate permissions settings section removed

### Technical
- `ActivityTraceDatabase` version 7→10 (FTS5 `captured_items_fts`, demo `demo_dataset_id`, encryption-recovery state)
- `SearchRanker` with RRF merge of BM25 + recency pools
- Backup envelope replaced plain archive format; restore validates integrity and leaves the live DB unchanged on failure
- `CaptureLimits`/`ExtractionBudget` bound accessibility and file-indexing work

---

## v0.9.0 — 2026-08-12

### New
- Project landing page with documentation and screenshots
- Statistics view now persists across app restarts

### Improvements
- Daily chart bars and labels separated for clearer statistics
- Daily statistic no longer shows zero-value entries

### Fixes
- Duplicate captures prevented when the same content appears repeatedly
- Upgrade crash caused by wrong dedup-index name in the v6→v7 migration (index renamed to the canonical Room-generated name)
- Database corruption from an encryption key mismatch now resets safely instead of crashing

### Technical
- `ActivityTraceDatabase` version 6→7 with `exportSchema = true` and committed schema JSONs
- Migration path regression-tested via `MigrationTestHelper` through Room's real open path
- `buildDatabase` hardened: eager open + detection of SQLCipher encryption failures (key-mismatch reset only on genuine corruption)

---

## v0.8.0 — 2026-07-15

### New
- Multi-language support: German, French, Spanish, Italian, Portuguese (BR), Russian, Polish
- Search now accepts localized month names (e.g. "marzo", "mai", "juin")
- All hardcoded UI strings extracted to `strings.xml` with `stringResource()` bindings
- Statistics screen with summary cards, timeline charts, top apps, and content type breakdown
- Auto-blocked system apps on fresh install (7 system packages blocked by default)

### Improvements
- Timeline charts unified to same size (160dp) — no layout jump on tab switch
- DailyChart count labels no longer overlap with date labels (short bars draw count inside the bar)
- Export/backup failures now write diagnostic error logs to `Download/ActivityTrace/`
- Export operations use `ExportStatus` sealed return type with detailed error information

### Fixes
- Export crashing when `MediaStore.Downloads.insert()` returns null
- SQLite backup failing silently when `openOutputStream` returns null
- `exportToPlainSqlite` crashing on retry with "table captured_items already exists" (stale temp file)
- `IS_PENDING` constant removed in API 36 — uses string literal `"is_pending"` instead
- Stale `topApps` data causing duplicate breakdown when filtering by app in Statistics
- Statistics top apps now shown even with 0 or 1 entry; breakdown always follows timeline

### Technical
- `ExportStatus` sealed class replaces raw string color logic in Settings
- `ExportErrorLogger` writes full stack traces with fallback locations
- `BlockedAppDefaults.kt` centralizes blocked packages (avoids circular dependency)
- `ActivityTraceDatabase` version 5→6 with auto-seed migration
- Proper `<plurals>` for result count and import count in all 7 new locales
- `QueryParser` months map built dynamically from `java.time.Month` per device locale
- Fastlane metadata and changelogs for all 7 new languages

---

## v0.7.0 — 2026-07-05

### New features
- CSV export — share captured data as CSV (UTF-8 BOM, RFC 4180, Excel-compatible)
- JSON export — export items in readable JSON format
- SQLite backup and restore — full backup/restore of encrypted database (plain SQLite)
- SQLite import merges with existing data (deduplicated on text/timestamp/appPackage)

### Improvements
- Data section in Settings reorganized: "Backup & Restore" and "Export Formats" subsections with descriptive labels
- Retention options simplified to 7, 30, 90 days ("Never" removed)
- Onboarding redesigned with live permission status indicators and step-by-step instructions
- Permission reminder banner on Search screen when no capture service is active
- Database export changed to unencrypted format for broader compatibility

### Fixes
- Button labels clarified: "Backup to SQLite", "Restore from backup", "Export as JSON", "Export as CSV"

### Technical
- `insertAll()` and `getAllItemKeys()` added to CaptureDao for import/CSV support
- `BackupImporter` and `DataExporter` (CSV) added with full test coverage
- All 71 unit tests + 35 instrumented tests pass

---

## v0.6.2 — 2026-07-01

### Fixes
- `ACCESS_NETWORK_STATE` permission removed from merged manifest (was pulled in by WorkManager, not needed by the app)

---

## v0.6.1 — 2026-07-01

### Improvements
- `type:` and `in:` search filters now use substring matching instead of exact match — `in:signal` matches Signal's display name, `type:accessibility` maps to screen captures
- Type synonyms added: `notif`, `access`, `folder`, `document`, `file` and more resolve to their canonical content types

### Fixes
- `in:signal` returning no results — now searches both `app_package` and `app_name` with LIKE
- `type:accessibility` returning no results — synonym maps to stored `screen` type
- Exact-match requirement on `content_type` and `app_package` filters relaxed to substring match

---

## v0.6.0 — 2026-07-01

### New features
- Search by app name — `app:signal`, `app:telegram` filter to a specific app's notifications
- Date shown in notification results alongside the timestamp

### Improvements
- Deep link handling rewritten with robust `PendingIntent` serialization — fewer fallback hops
- QueryParser cleaned up, tests expanded for app-name and date-range queries
- Deduplication at ingestion — identical consecutive notifications discarded before storage

### Fixes
- Search results missing when query matched app name but not notification text
- Duplicate notifications appearing when both accessibility and listener captured the same event
- Deep links failing after process death (`PendingIntent` serialization edge cases)

---

## v0.5.0 — 2026-05-31

### New features
- Screenshots added to README and F-Droid listing (5 screenshots: search, settings, onboarding)

### Improvements
- App icon updated with adjusted background color
- F-Droid metadata updated (full description, short description)
- Fastlane images restructured (old screenshots replaced)

### Technical
- Launcher background color changed (`ic_launcher_background.xml`)

---

## v0.4.0 — 2026-05-31

### New features
- Indexed files (images, text, PDFs) open in their default viewer via `ACTION_VIEW`
- File names and extensions searchable — PNGs, JPEGs, and other images now appear in results
- `Makefile` with `build`, `test`, `full-test` targets
- `build_and_test.py` script — builds debug+release, runs unit tests, boots emulator, runs instrumented tests
- File indexing schedule persisted and restored on app startup (Never/Daily)

### Improvements
- Compact 40dp uniform circular icons for all search result types
- Edge-to-edge rendering with `enableEdgeToEdge()` — proper system bar colors for day/night
- `.systemBarsPadding()` and `.navigationBarsPadding()` added across all screens
- Minimum text length requirement removed from file indexing (short filenames now accepted)
- File indexing schedule setting now properly saved to `SharedPreferences`

### Size
- Release APK reduced from **21 MB → 12 MB** (43% reduction)
- Arm64-only native libs (`abiFilters`) — dropped x86/x86_64/armeabi-v7a
- No new dependencies added

### Fixes
- File indexing schedule not persisted (selection reset on process death)
- `PendingIntent` / `IntentSender` / `getLaunchIntentForPackage` fallback chain in `openItem()`
- PNG/image files skipped by `resolveMimeType()` — now accepted via extension map and SAF provider type

### Technical
- `compileSdk` / `targetSdk` bumped to 36 (Android 16)
- `AGENTS.md` maintained with full project context
- App icon replaced

---

## v0.3.0 — 2026-05-30

### New features
- App icons in search results (40dp circle, loaded via `PackageManager`)
- Expandable result cards — tap to show full text, long-press to copy
- Filter and search query persisted across app restarts (`SharedPreferences`)
- App name shown in results (resolved at capture time)
- Settings page with file indexing (SAF multi-folder, PDF via PDFBox)
- Schedule file indexing (Daily/Never) with manual scan button
- Deep link fallback chain: `PendingIntent` → `IntentSender` → `getLaunchIntentForPackage` → `ACTION_MAIN`
- Swipe-to-delete with undo snackbar

### Improvements
- Full screen text capture via `AccessibilityService` tree traversal (`collectText()`)
- Dedicated `OpenInNew` icon button for result deep links
- Empty states with contextual messages
- Room migrations for `app_name` and `category` columns
- FTS5 triggers include `app_name` and `category`

### Fixes
- `PackageManager` visibility on API 30+ (`<queries>` manifest block)
- Backwards-compatible `IntentSender` deserialization for old notification deep links
- App icon retry on transient failures (no longer permanently hidden)
- Accessibility `AccessibilityNodeInfo` lifecycle compliance on Android 14+
- `AdaptiveIconDrawable` rendering (intrinsic size -1 handled)

### Removed
- Clipboard capture service (passive clipboard monitoring)
- Dead code: `ScreenshotCaptureService`, `ContactMatcher`, `HistoryScreen`, `SearchOverlayActivity`

---

## v0.2.0 — 2026-05-26

### New features
- Notification deep links via `PendingIntent` serialization
- Accessibility notification capture fallback path
- Initial settings screen with database export
- Retention cleanup worker (WorkManager, scheduled daily)

### Improvements
- Version catalog migrated to `libs.versions.toml`
- Minification enabled for release builds (ProGuard)

### Fixes
- `startForeground()` strict requirement on Android 16 preview
- Database encryption fallback when StrongBox unavailable

---

## v0.1.0 — 2026-05-25

### New features
- Initial release
- Notification capture via `NotificationListenerService`
- Clipboard capture via foreground service
- Basic FTS5 search with SQLCipher encryption
- Accessibility screen capture (basic)
- Onboarding with permission grants
