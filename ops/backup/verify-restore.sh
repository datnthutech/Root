#!/usr/bin/env bash
# Khôi phục bản sao lưu mới nhất vào DB tạm rồi so số liệu với DB hiện tại.
# Dung sai (hai chiều, vì DB hiện tại thay đổi sau lần sao lưu):
#   |khôi phục - hiện tại| <= max(VERIFY_TOLERANCE_ROWS=5, VERIFY_TOLERANCE_PCT=5 % của hiện tại)
# Luôn thất bại nếu: bản mới nhất cũ hơn VERIFY_MAX_AGE_HOURS=26 giờ, pg_restore -l lỗi,
# bảng khôi phục rỗng trong khi bảng hiện tại có dòng, hoặc flyway_schema_history lệch (so khớp tuyệt đối).
set -euo pipefail
BACKUP_DIR="${BACKUP_DIR:-/backups}"
TOL_ROWS="${VERIFY_TOLERANCE_ROWS:-5}"
TOL_PCT="${VERIFY_TOLERANCE_PCT:-5}"
MAX_AGE_H="${VERIFY_MAX_AGE_HOURS:-26}"
for v in "$TOL_ROWS" "$TOL_PCT" "$MAX_AGE_H"; do
  case "$v" in ''|*[!0-9]*) echo "tham số dung sai phải là số nguyên" >&2; exit 1;; esac
done
: "${PGHOST:?}" "${PGUSER:?}" "${PGDATABASE:?}"
LATEST="$(ls -1t "$BACKUP_DIR"/giapha-*.dump 2>/dev/null | head -n 1 || true)"
[ -n "$LATEST" ] || { echo "VERIFY FAILED: chưa có bản sao lưu nào" >&2; exit 1; }
if [ -n "$(find "$LATEST" -mmin +"$((MAX_AGE_H * 60))")" ]; then
  echo "VERIFY FAILED: bản mới nhất $LATEST cũ hơn ${MAX_AGE_H} giờ" >&2; exit 1
fi
pg_restore -l "$LATEST" > /dev/null || { echo "VERIFY FAILED: pg_restore -l lỗi" >&2; exit 1; }

TMP="giapha_verify_$$"
cleanup() { dropdb -h "$PGHOST" -U "$PGUSER" --if-exists "$TMP" >/dev/null 2>&1 || true; }
trap cleanup EXIT
/usr/local/bin/restore.sh "$LATEST" "$TMP"

q() { psql -h "$PGHOST" -U "$PGUSER" -d "$1" -Atc "$2"; }
status=0
for t in person union_rel parent_child clan clan_member app_user audit_log; do
  live="$(q "$PGDATABASE" "SELECT count(*) FROM $t")"
  restored="$(q "$TMP" "SELECT count(*) FROM $t")"
  echo "$t: hiện tại=$live, bản sao lưu=$restored"
  if [ "$restored" -eq 0 ] && [ "$live" -gt 0 ]; then echo "BẤT THƯỜNG $t: bản sao lưu rỗng nhưng DB hiện tại có $live dòng"; status=1; continue; fi
  allowed=$(( live * TOL_PCT / 100 )); [ "$allowed" -ge "$TOL_ROWS" ] || allowed="$TOL_ROWS"
  delta=$(( restored > live ? restored - live : live - restored ))
  if [ "$delta" -gt "$allowed" ]; then echo "BẤT THƯỜNG $t: lệch $delta > dung sai $allowed"; status=1; fi
done
fl="$(q "$PGDATABASE" "SELECT count(*) FROM flyway_schema_history")"
fr="$(q "$TMP" "SELECT count(*) FROM flyway_schema_history")"
echo "flyway_schema_history: hiện tại=$fl, bản sao lưu=$fr"
if [ "$fl" != "$fr" ]; then echo "BẤT THƯỜNG flyway_schema_history lệch"; status=1; fi
[ "$status" -eq 0 ] && echo "VERIFY OK ($LATEST)" || { echo "VERIFY FAILED" >&2; exit 1; }
