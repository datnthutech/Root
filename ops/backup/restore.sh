#!/usr/bin/env bash
# Dùng: restore.sh /backups/giapha-<ts>.dump <tên-DB-đích>
# CẢNH BÁO: xoá DB đích nếu đã tồn tại. Không bao giờ dùng với tên DB đang chạy thật.
set -euo pipefail
DUMP="${1:?thiếu tệp .dump}"
TARGET="${2:?thiếu tên DB đích}"
[ -f "$DUMP" ] || { echo "không thấy tệp $DUMP" >&2; exit 1; }
[ "$TARGET" != "${PGDATABASE:-giapha}" ] || { echo "từ chối ghi đè DB đang chạy ($TARGET)" >&2; exit 1; }
dropdb  -h "${PGHOST:?}" -U "${PGUSER:?}" --if-exists "$TARGET"
createdb -h "$PGHOST" -U "$PGUSER" "$TARGET"
pg_restore -h "$PGHOST" -U "$PGUSER" -d "$TARGET" --no-owner --exit-on-error "$DUMP"
echo "đã khôi phục $DUMP vào $TARGET"
