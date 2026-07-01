# Changelog

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
