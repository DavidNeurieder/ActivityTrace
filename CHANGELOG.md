# Changelog

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
