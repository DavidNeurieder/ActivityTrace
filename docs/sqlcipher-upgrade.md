# SQLCipher upgrade — rollback & recovery

This document covers what to do if a SQLCipher library upgrade (or any
database migration) goes wrong, and how the app behaves automatically.

## Database format compatibility

SQLCipher maintains on-disk format compatibility across releases. A database
written by `android-database-sqlcipher` 4.5.x (legacy package) opens with
`sqlcipher-android` 4.17.x (new package) using the same key material — **no
re-encryption is required on upgrade**.

Because of this:

- The SQLCipher library migration **does not get a Room schema version bump**
  (`ActivityTraceDatabase.CURRENT_VERSION` is unchanged).
- The Room migration chain (`6 → 7 → 8 → 9`) is independent of the SQLCipher
  packaging change, so search regression and encryption regression cannot be
  confused with one another.

## What the app does automatically on a failed open

`ActivityTraceDatabase.tryOpen()` classifies failures via
`RecoveryClassifier` and records the outcome in `RecoveryStateStore`
(SharedPreferences) — it **never deletes the database or key material**:

| Error signature                     | Reason                  |
| ----------------------------------- | ----------------------- |
| `file is encrypted`, `not a database` | `INVALID_KEY`         |
| `database disk image is malformed`    | `CORRUPTED_DATABASE`  |
| `migration didn't properly handle`, `cannot verify the data integrity`, `expected version doesn't match` | `MIGRATION_FAILURE` |
| anything else                       | `Failed` (no state recorded) |

`DatabaseOpenResult`:

- `Opened(database)` — success; recovery state is cleared.
- `RecoveryRequired(reason)` — a known, non-destructive failure; UI shows the
  reason.
- `Failed(error)` — unknown failure; UI shows an error.

There is **no `deleteDatabase` anywhere** in the codebase (enforced by a
grep-path acceptance check). A failed open preserves the database file
byte-for-byte (see `DatabaseEncryptionTest.failed open leaves the database
byte-for-byte unchanged`).

The app's `ActivityTraceApplication.onCreate` calls
`System.loadLibrary("sqlcipher")` before the first database access; an
`UnsatisfiedLinkError` (missing/ABI-mismatched native library) is absorbed by
the `catch (Throwable)` so the app still launches (search is unavailable until
a fixed build is installed).

## Rollback procedure (release tooling)

If a shipped build regresses on opening existing databases:

1. **Do not re-delete user databases.** The offending release never deleted
   data; verify this in the release APK before proceeding:
   ```bash
   ./gradlew :app:assembleRelease
   # confirm no deleteDatabase call:
   grep "deleteDatabase" app/build/outputs/apk/release/*.apk || true
   ```
2. Revert the SQLCipher bump only:
   ```bash
   git revert <commit-that-swapped-sqlcipher-android>
   ```
   Because the DB file format is unchanged, the old package opens the same
   file. No Room migration is involved.
3. The FTS5 index (schema 9) is created via `MIGRATION_8_9` / `onCreate`
   with `CREATE VIRTUAL TABLE IF NOT EXISTS`, so an old-package release that
   supports schema 9 is fine. A release at an *older* Room version will run
   the normal Room downgrade path: Room refuses a downgrade and the app
   records `MIGRATION_FAILURE`; data is still intact.
4. If the working tree is mid-flight, keep the schema JSONs
   (`app/schemas/.../*.json`) and migration chain intact when reverting —
   they must not diverge from `CURRENT_VERSION`.

## Upgrade test matrix (§36)

Before shipping a SQLCipher or migration release, the on-device suite must
cover (see `DatabaseEncryptionTest`, `FtsMigrationTest`,
`FtsSearchTest`):

- Fresh install (Room `onCreate` creates the FTS5 index).
- Existing database (`MIGRATION_8_9` backfills the FTS index).
- Existing database + old wrapped key (Keystore wrapping is unchanged).
- Existing database + new wrapped key.
- 0 rows, 10k rows.
- Wrong key (bytes preserved, open fails with `INVALID_KEY`).
- Corrupted database (`CORRUPTED_DATABASE`).
- Interrupted upgrade / process killed mid-migration (SQLCipher + SQLite
  transactional DDL; DB file untouched until commit).
- Device reboot between upgrade steps.
- Export/import round-trip (plain and encrypted backup).

The golden end-to-end check is: install old build → generate realistic DB →
insert fixture → upgrade to new build → open → verify every row, search
(FTS5), capture, and export.

## Instrumented coverage note

Robolectric's emulated platform SQLite has **no FTS5 module** and cannot load
the SQLCipher native library, so:

- FTS5 migration and search assertions live in instrumented tests
  (`FtsMigrationTest`, `FtsSearchTest`, `FtsSearchBenchmarkTest`).
- SQLCipher open/error assertions live in `DatabaseEncryptionTest`.
- Unit tests exercise only the seams (`DatabaseOpener`, `RecoveryClassifier`,
  `DatabaseKeyStore`).