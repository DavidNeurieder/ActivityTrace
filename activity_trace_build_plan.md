# Activity Trace — Build Plan (No AI)

**Core idea:** One natural language query across everything on your phone. No AI, no cloud, no models. Just smart FTS5 + metadata filtering.

---

## Architecture Overview

```
[NotificationListenerService] ──┐
[ScreenshotDetectionCallback] ──┤──► [CaptureIngestor] ──► [SQLite FTS5 + Metadata]
[AccessibilityService (opt)] ───┘                                  │
                                                                    ▼
[User types query] ──► [QueryParser] ──► [FTS5 Search + Filters] ──► [Results ranked by relevance + recency]
```

---

## Data Model

### `captured_items` table

| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | auto-increment |
| text | TEXT | raw text from notification/screenshot OCR |
| app_package | TEXT | com.whatsapp, com.google.android.gm, etc. |
| app_name | TEXT | "WhatsApp", "Gmail" — denormalized for display |
| content_type | TEXT | notification, screenshot, browser_page, clipboard |
| timestamp | INTEGER | unix epoch millis |
| metadata | TEXT | JSON blob — sender/chat name for messaging, URL for browser, etc. |

### `captured_items_fts` FTS5 virtual table

```sql
CREATE VIRTUAL TABLE captured_items_fts USING fts5(
    text, app_name, metadata,
    content='captured_items',
    content_rowid='id',
    tokenize='porter unicode61'
);
```

Tokenizers:
- **porter** — stem words (e.g. "running" matches "run", "restaurant" matches "restaurants")
- **unicode61** — handles Unicode, removes punctuation

### Synonyms table (optional v2 enhancement)

```sql
CREATE TABLE synonyms (
    term TEXT PRIMARY KEY,
    alternatives TEXT  -- comma-separated: "restaurant,cafe,bistro,eatery,diner"
);
```

When a user queries a term, expand to alternatives before searching. Covers semantic gaps without an AI model.

---

## Query Parsing

A lightweight rule-based parser extracts filters from natural language:

### Patterns extracted

```
"ethiopian restaurant my brother sent march" →
  - keywords: ethiopian, restaurant, brother, sent
  - time_range: march → WHERE timestamp BETWEEN 2026-03-01 AND 2026-03-31
  - contact_match: brother → fuzzy match against system contacts display names
```

```
"pdf warranty info for my router" →
  - keywords: pdf, warranty, info, router
  - content_type: pdf → infer from keyword "pdf"
```

```
"whatsapp photos from last week" →
  - keywords: photos
  - app: whatsapp → matches "whatsapp"
  - time_range: last week → WHERE timestamp > current_epoch - 7*86400000
```

### Date/time parser

Handle these patterns:
- `"march"` or `"march 2026"` → month range
- `"last week"`, `"yesterday"`, `"today"` → relative epoch calc
- `"2026-03-15"` → specific date
- `"march 15"` → current-year date
- `"2 days ago"`, `"3 weeks ago"` → relative

### Contact matching

Use `ContactsContract.Contacts` provider:
- Look up query tokens against display names
- Multi-word names ("Mom", "John Smith") → partial match
- Cache contact names in-memory at startup

---

## FTS5 Search Query Construction

```kotlin
fun buildFtsQuery(rawQuery: String, parsedFilters: QueryFilters): String {
    val terms = parsedFilters.keywords
        .filterNot { it in parsedFilters.consumedTokens }
        .joinToString(" AND ") { "\"$it\"" }
    
    val baseQuery = if (terms.isNotBlank()) terms else "*"
    
    // FTS5 column targeting:
    // Search text, app_name, and metadata fields
    // Weight: text=1.0, app_name=2.0, metadata=0.5
    return """
        SELECT i.*, rank
        FROM captured_items_fts f
        JOIN captured_items i ON f.rowid = i.id
        WHERE captured_items_fts MATCH ?
          AND i.timestamp BETWEEN ? AND ?
          AND (i.app_package = ? OR ? = '')
        ORDER BY rank DESC, i.timestamp DESC
        LIMIT 50
    """
}
```

**Ranking:** FTS5 `rank` is built-in BM25 relevance. After retrieval, apply:
- **Recency boost**: `score = bm25_score * (1 + 0.5 * recency_normalized)` where recency = (now - timestamp) / (30 days)
- **App priority**: user's most-used apps get a small multiplier
- **Content type priority**: screenshots + browser pages rank higher than transient notifications

---

## Capture Components

### 1. NotificationListenerService

```kotlin
class ActivityTraceNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val text = extractText(sbn.notification)
        val extras = sbn.notification.extras
        val metadata = jsonOf(
            "title" to extras.getString(EXTRA_TITLE),
            "text" to extras.getCharSequence(EXTRA_TEXT)?.toString(),
            "subtext" to extras.getCharSequence(EXTRA_SUB_TEXT)?.toString(),
            "conversation" to extras.getString(EXTRA_CONVERSATION_TITLE),
            "bigText" to extras.getCharSequence(EXTRA_BIG_TEXT)?.toString()
        )
        captureIngestor.ingest(
            text = text,
            appPackage = sbn.packageName,
            contentType = "notification",
            metadata = metadata
        )
    }
}
```

Capture scenarios:
- **Messaging**: Chat message from WhatsApp/Telegram/Signal — captured in `EXTRA_TEXT`
- **Email summary**: Gmail notification subject line
- **Link share**: "Check this out: https://..." — captures the text, extract URL into metadata
- **Screenshot notification**: System notification when screenshot taken (alternative to ScreenshotDetectionCallback)

**Debouncing:** Same-app notifications within 2s → merge into single entry (grouped conversations)

### 2. ScreenshotDetectionCallback (Android 14+)

```kotlin
val screenshotCallback = ScreenshotCallback { 
    val text = onDeviceOcr(screenshotBitmap)
    val appInForeground = getForegroundAppPackage()
    captureIngestor.ingest(
        text = text,
        appPackage = appInForeground,
        contentType = "screenshot"
    )
}
```

- Only capture when the **foreground app** is in our allowed list (messaging, browser, gallery)
- OCR via ML Kit on-device: `TextRecognition.getClient(TextRecognizerOptions.FORCE_DEVICE`)
- **Privacy**: Never store bitmap, only extracted text
- **Battery**: OCR runs only when screenshot detected, not continuous

### 3. AccessibilityService (optional, v2)

```kotlin
class ActivityTraceAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == TYPE_WINDOW_STATE_CHANGED) {
            val pageText = extractVisibleText(event.source)
            if (isMeaningfulContent(pageText)) {
                captureIngestor.ingest(
                    text = pageText,
                    appPackage = event.packageName,
                    contentType = "browser_page"
                )
            }
        }
    }
}
```

- **Friction**: Manual enable in Settings → Accessibility → Activity Trace
- **Scoped**: Only process pages in browser or content-heavy apps
- **Throttle**: Skip if text length < 20 chars or same as last capture in same app
- **Best for**: Capturing web page content the user is actively reading but not sharing/screenshotting

### 4. Clipboard listener

```kotlin
val clipboardManager = getSystemService(CLIPBOARD_SERVICE)
clipboardManager.addPrimaryClipChangedListener {
    val clip = clipboardManager.primaryClip?.getItemAt(0)
    val text = clip?.text?.toString() ?: clip?.uri?.toString() ?: return
    if (text.length > 15) { // ignore one-word copies
        captureIngestor.ingest(text = text, contentType = "clipboard")
    }
}
```

Captures copied links, addresses, code snippets — high-signal content the user explicitly selected.

### 5. File Storage Indexing (MediaStore + SAF)

```kotlin
class FileIndexer(private val context: Context) {
    fun indexRecentFiles() {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE
        )
        val selection = "${MediaStore.Files.FileColumns.DATE_ADDED} > ?"
        val selectionArgs = arrayOf((System.currentTimeMillis() / 1000 - 7 * 86400).toString())
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection, selection, selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(2) ?: continue
                val mimeType = cursor.getString(3) ?: "application/octet-stream"
                val text = extractTextFromFile(path, mimeType) ?: continue
                captureIngestor.ingest(
                    text = text,
                    appPackage = "com.android.documentsui",
                    contentType = "file",
                    metadata = jsonOf("path" to path, "mimeType" to mimeType)
                )
            }
        }
    }

    private fun extractTextFromFile(path: String, mimeType: String): String? {
        return when {
            mimeType.startsWith("text/") -> File(path).readText().take(5000)
            mimeType == "application/pdf" -> extractPdfText(path)  // via PdfRenderer or iText
            else -> null  // images handled by screenshot OCR, binaries skipped
        }
    }
}
```

**Scenarios indexed:**
- **Documents** (.txt, .md, .pdf) — extract plain text up to 5KB per file
- **Code files** (.py, .kt, .java, .js, etc.) — indexed as-is
- **Downloads folder** — newest files indexed on a daily WorkManager schedule
- **SAF (Storage Access Framework)** — opt-in: user picks directories via `Intent(ACTION_OPEN_DOCUMENT_TREE)`

**Exclusions:**
- Binary files (images, audio, video) — text content extracted via OCR in separate capture path
- App-internal directories — already captured via other components
- Files < 1KB or > 10MB — too small (noise) or too large (performance)
- System directories, cache, temp

**Privacy note:** File indexing is **opt-in only**. Users enable it in Settings and must grant `READ_EXTERNAL_STORAGE` (Android 12-) or `READ_MEDIA_DOCUMENTS` (Android 13+). SAF directory grants are scoped to user-selected folders.

---

## Permissions & Onboarding

### Required permissions

| Permission | OS Level | When to Request |
|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | System setting | Onboarding screen 1 — explain why, provide "Open Settings" button |
| `POST_NOTIFICATIONS` (Android 13+) | Runtime | Onboarding screen 1 — for in-app search result notifications |
| `READ_MEDIA_IMAGES` (if gallery indexing added) | Runtime | Later — only if user opts into photo library indexing |

### Optional permissions

| Permission | Friction | When to Offer |
|---|---|---|
| Accessibility Service | Very high — manual settings navigation | Onboarding screen 2 — "Advanced: Capture pages you read" |
| System screenshot detection | Low — one-time dialog | Onboarding screen 2 — "Capture screenshots you take" |

### Onboarding flow (3 screens)

1. **"Search everything on your phone"** — demo video + CTA "Grant Notification Access" (deep link to system settings)
2. **"[Optional] Capture more"** — Accessibility + Screenshot detection toggle explanations
3. **First search** — pre-load some mock data or recent captured items so the user can try searching immediately

**Notification access link:** `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`

---

## UI Architecture

### Single Activity, 3 screens:

1. **SearchScreen** (main)
   - Search bar at top (material `SearchView`)
   - Filter chips below: "Today", "This Week", "WhatsApp", "Gmail", "Links", "Images"
   - Results as cards: app icon + snippet + timestamp + actions (copy, share, open in app)
   - Empty state: suggestion list of common searches + recent captures

2. **CaptureHistoryScreen**
   - Chronological timeline of all captured items
   - Quick-filter by app (horizontal app icon row)
   - Swipe-to-delete individual items
   - Bulk delete: "Delete all older than 30 days", "Delete all from [app]"

3. **SettingsScreen**
   - Privacy controls: data retention (7/30/90 days / forever)
   - Capture management: which apps to capture notifications from
   - Exclusion list: notification keywords to ignore (OTP codes, etc.)
   - Export data (JSON file)
   - Delete all data
   - Accessibility service toggle info

### Quick search overlay

- **Trigger**: Notification shortcut + Quick Settings tile
- **Behavior**: Semi-transparent overlay `Dialog` or `Activity` with `FLAG_WATCH_OUTSIDE_TOUCH`
- **Result tap**: If result is a notification → open the source app via `packageManager.getLaunchIntentForPackage`; if screenshot → open gallery at that screenshot (if we stored a reference)

---

## Data Retention & Privacy

| Period | Behavior |
|---|---|
| 7 days | Default retention for notifications (transient content) |
| 30 days | Screenshots + clipboard content |
| 90 days | Browser pages (optional Accessibility captures) |
| Forever opt-in | User explicitly chooses |

**Auto-expiry:** `WorkManager` daily job deletes rows older than retention period.

**Privacy guarantees:**
- Zero network calls — verified by `StrictMode.setThreadPolicy` in debug builds
- All data stored in app-internal storage (not MediaStore)
- **Full encryption at rest** — see Encryption Architecture below
- Open source the capture pipeline so security-conscious users can audit
- No account, no login, no telemetry

---

## Encryption Architecture

### Goal

If the device is stolen, the attacker gets nothing readable — even with root access. Every byte of user data is encrypted with a key that never leaves the hardware security module (TEE/StrongBox).

### How it works

```
┌─────────────────────────────────────────────────────────┐
│  Android Keystore (TEE / StrongBox)                     │
│  ┌──────────────────────────────────────────────────┐   │
│  │ "activity_trace_master_key"  │  AES-256                 │   │
│  │                      │  never exportable        │   │
│  │                      │  locked when device locks│   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────┘
                       │ Key retrieved at app startup
                       ▼
┌─────────────────────────────────────────────────────────┐
│  SQLCipher (AES-256-CBC)                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │  recall.db  (encrypted at page level)            │   │
│  │  ├── captured_items                              │   │
│  │  ├── captured_items_fts  ← FTS5 works normally  │   │
│  │  └── synonyms                                    │   │
│  │  If opened without key: "file is not a database" │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Key generation (once, at first launch)

```kotlin
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

fun generateDatabaseKey(context: Context) {
    val keyGen = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
    )
    val spec = KeyGenParameterSpec.Builder("activity_trace_master_key")
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setIsStrongBoxBacked(true)        // hardware-backed security chip
        .setUserAuthenticationRequired(false)  // no biometric prompt needed
        .build()
    keyGen.init(spec)
    keyGen.generateKey()
}
```

### Opening the encrypted database

```kotlin
// SQLCipher uses a 256-bit passphrase derived from the Keystore key
fun openSecureDatabase(context: Context): SupportSQLiteDatabase {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .build()

    val db = SupportSQLiteOpenHelperFactory(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("activity_trace.db")
            .callback(ActivityTraceDbCallback())
            .build()
    )
    val room = Room.databaseBuilder(context, ActivityTraceDatabase::class.java, "activity_trace.db")
        .openHelperFactory(SupportFactory(masterKey))
        .build()
    return room
}
```

> **Note:** If using Room, use `net.zetetic:android-database-sqlcipher` + `androidx.sqlite:sqlite-ktx` with `SupportFactory` wrapping the master key. If using raw SQLite, pass the key bytes directly to `SQLiteDatabase.openOrCreateDatabase()` with the SQLCipher `PRAGMA key`.

### Stolen-device threat model

| Scenario | Outcome |
|---|---|
| Device stolen while **locked** | Keystore locked — key unavailable → DB stays encrypted |
| Device stolen while **unlocked** | Keystore accessible until next lock — data readable |
| Attacker extracts `/data/data/com.activitytrace/*` via ADB/root | All files encrypted — no key without Keystore access |
| Attacker reads physical NAND flash | AES-256-CBC ciphertext — indistinguishable from random |
| Attacker has **root** on unlocked device | Must read key from Keystore — TEE/StrongBox prevents key extraction |
| Brute-force AES-256 | ~10¹⁷ years at current compute |

### Key invalidation handling

Certain events invalidate Keystore keys: biometric enrollment change, device removal from work profile, factory reset.

```kotlin
fun getOpenDatabase(context: Context): ActivityTraceDatabase {
    return try {
        Room.databaseBuilder(context, ActivityTraceDatabase::class.java, "activity_trace.db")
            .openHelperFactory(SupportFactory(MasterKey.Builder(context)...build()))
            .build()
    } catch (e: MasterKey.UnrecoverableKeyException) {
        // Keystore key invalidated — rotate
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()  // regenerates key
        // Export old data via backup, re-import under new key
        // (or simply start fresh if data loss is acceptable)
        recreateEmptyDatabase(context)
    }
}
```

**Simple v1 approach:** On key invalidation, delete old DB and start fresh with a new key. The user just loses their capture history — no data leak. In v2, add encrypted export/import for seamless key rotation.

### Practical tradeoffs

| Factor | Without encryption | With SQLCipher |
|---|---|---|
| App size | Baseline | +2.5 MB (native libs) |
| DB open time | ~10ms | ~100ms (key derivation) |
| Write throughput | Baseline | ~5-10% slower |
| FTS5 query speed | Baseline | ~5% slower |
| Cold start search | Instant | +100ms for DB open |
| Stolen device | All data exposed | Useless to attacker |

### Dependencies

```kotlin
// build.gradle.kts (app level)
dependencies {
    implementation("net.zetetic:android-database-sqlcipher:4.6.0")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

---

## MVP Scope (v1)

### Must have
- [ ] `NotificationListenerService` — capture from all apps
- [ ] SQLCipher + Keystore encryption — key generated at first launch, DB never stored in plaintext
- [ ] SQLite FTS5 with porter tokenizer (works transparently on encrypted DB)
- [ ] Key invalidation handling — graceful fallback to new DB if Keystore key is invalidated
- [ ] Search screen with basic query → FTS5 MATCH
- [ ] Date/entity parsing (month, last week, yesterday)
- [ ] Results grouped by app + time
- [ ] Onboarding flow for notification access
- [ ] 7-day auto-delete default

### Nice to have (v1.1)
- [ ] Screenshot OCR capture
- [ ] Clipboard listener
- [ ] Contact name matching
- [ ] "Open in app" from result tap
- [ ] Settings → retention period, app exclusions

### v2
- [ ] AccessibilityService for browser content
- [ ] File storage indexing (MediaStore + SAF, opt-in)
- [ ] Synonyms table for query expansion
- [ ] Gallery image text indexing (opt-in)
- [ ] Data export / backup
- [ ] Full-text search ranking tuner (recency boost)

---

## Sample Queries vs FTS5 Match

| User Query | Parser Output | FTS5 Query | Hit? |
|---|---|---|---|
| `"ethiopian restaurant brother march"` | kw: ethiopian,restaurant,brother, sent; time: mar | `"ethiopian" AND "restaurant" AND "brother"` | Yes — "brother" in sender field |
| `"pdf warranty for my router"` | kw: pdf,warranty,router; type: pdf | `"warranty" AND "router"` metadata MATCH "pdf" | Yes — "router" in text |
| `"that recipe you sent"` | kw: recipe,sent | `"recipe" AND "sent"` | No — too vague, needs more specific term |
| `"link to hiking trail"` | kw: link,hiking,trail | `"hiking" AND "trail"` | Maybe — if text contains both words |
| `"what did sarah say about the meeting"` | kw: sarah, meeting | `"sarah" AND "meeting"` | Yes — "sarah" in sender or text |
| `"screenshot of tracking number"` | kw: screenshot,tracking,number; type: screenshot | `"tracking" AND "number"` | Yes — if OCR caught the number |

**Known gap that v2 (embeddings) fills:**
- `"that place my sibling told me about"` — no keyword overlap with original text "Try Azura Ethiopian Restaurant" → FTS5 misses it
- Coverage: FTS5 handles ~70-80% of real queries. Semantic search lifts this to ~90-95%.

---

## Why This Ships Fast

1. **No model** → no model download, no ONNX setup, no GPU concerns, works on $150 Android Go phones
2. **FTS5 is built into Android** → `SQLiteDatabase` with `ENABLE_FTS5` enabled on all modern devices
3. **NotificationListenerService** is mature API, well-documented, stable since Android 4.3
4. **ML Kit OCR** works fully offline, no API keys, 5MB download
5. **Single developer, 3-4 weeks** for v1 if focused

---

## File Structure

```
ActivityTrace/
├── app/
│   ├── src/main/
│   │   ├── java/com/activitytrace/
│   │   │   ├── ActivityTraceApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── capture/
│   │   │   │   ├── CaptureIngestor.kt
│   │   │   │   ├── ActivityTraceNotificationListener.kt
│   │   │   │   ├── ScreenshotCaptureService.kt
│   │   │   │   ├── ClipboardCaptureService.kt
│   │   │   │   └── AccessibilityCaptureService.kt
│   │   │   ├── store/
│   │   │   │   ├── Database.kt
│   │   │   │   ├── CaptureDao.kt
│   │   │   │   ├── EncryptionManager.kt   ← Key gen + open encrypted DB
│   │   │   │   └── RetentionCleanupWorker.kt
│   │   │   ├── search/
│   │   │   │   ├── QueryParser.kt
│   │   │   │   ├── ContactMatcher.kt
│   │   │   │   └── SearchEngine.kt
│   │   │   ├── ui/
│   │   │   │   ├── SearchScreen.kt
│   │   │   │   ├── HistoryScreen.kt
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   ├── OnboardingActivity.kt
│   │   │   │   └── SearchOverlayActivity.kt
│   │   │   └── model/
│   │   │       └── CapturedItem.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── ...
├── gradle/
└── build.gradle.kts
```
