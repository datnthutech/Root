#!/usr/bin/env bash
set -euo pipefail
BACKUP_DIR="${BACKUP_DIR:-/backups}"
: "${PGHOST:?}" "${PGUSER:?}" "${PGDATABASE:?}"
LATEST="$(ls -1t "$BACKUP_DIR"/giapha-*.dump 2>/dev/null | head -n 1 || true)"
[ -n "$LATEST" ] || { echo "VERIFY FAILED: chưa có bản sao lưu nào" >&2; exit 1; }
TMP="giapha_verify_$$"
cleanup() { dropdb -h "$PGHOST" -U "$PGUSER" --if-exists "$TMP" >/dev/null 2>&1 || true; }
trap cleanup EXIT
/usr/local/bin/restore.sh "$LATEST" "$TMP"

q() { psql -h "$PGHOST" -U "$PGUSER" -d "$1" -Atc "$2"; }
status=0
for t in person union_rel parent_child clan clan_member app_user audit_log; do
  live="$(q "$PGDATABASE" "SELECT count(*) FROM $t")"
  restored="$(q "$TMP" "SELECT count(*) FROM $t")"
  if [ "$restored" -gt "$live" ]; then echo "BẤT THƯỜNG $t: khôi phục=$restored > hiện tại=$live"; status=1; fi
  if [ "$restored" -eq 0 ] && [ "$live" -gt 0 ] && [ "$t" != "audit_log" ]; then echo "BẤT THƯỜNG $t: bản sao lưu rỗng nhưng DB hiện tại có $live dòng"; status=1; fi
  echo "$t: hiện tại=$live, bản sao lưu=$restored"
done
[ "$status" -eq 0 ] && echo "VERIFY OK ($LATEST)" || { echo "VERIFY FAILED" >&2; exit 1; }
