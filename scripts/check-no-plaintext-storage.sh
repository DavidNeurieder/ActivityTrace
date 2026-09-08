#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

fail=0

# Any place that stages a plaintext SQLite artifact (via a File(...) with a
# *.sqlite name, or via sqlcipher_export) must clean it up in the same file.
# This catches accidental reintroduction of persistent plaintext databases.
while IFS= read -r f; do
    if ! grep -qE '\.delete\(\)|deleteRecursively\(\)' "$f"; then
        echo "FAIL: $f creates a plaintext SQLite artifact without an explicit cleanup" >&2
        fail=1
    fi
done < <(grep -rln -E 'File\([^)]*"[^"]*\.sqlite"|sqlcipher_export' app/src/main/java || true)

# A plaintext *.sqlite backup must never be written to user-visible storage
# (MediaStore / external public dirs) except through the explicitly warned,
# user-initiated plaintext export feature (DatabaseExporter).
while IFS= read -r f; do
    if grep -qE 'MediaStore|Environment\.getExternalStorage' "$f"; then
        echo "FAIL: $f persists a plaintext SQLite artifact to user-visible storage" >&2
        fail=1
    fi
done < <(grep -rln -E 'File\([^)]*"[^"]*\.sqlite"' \
    app/src/main/java/com/activitytrace --include='*.kt' \
    | grep -v 'DatabaseExporter.kt' || true)

if [ "$fail" -ne 0 ]; then
    exit 1
fi

echo "OK: no unencrypted export artifacts outside the transient, cleaned staging paths"