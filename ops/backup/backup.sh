#!/usr/bin/env bash
set -euo pipefail
umask 077
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
MINIO_DATA_DIR="${MINIO_DATA_DIR:-/minio-data}"
SKIP_PHOTOS="${SKIP_PHOTOS:-0}"
case "$RETENTION_DAYS" in ''|*[!0-9]*) echo "RETENTION_DAYS phải là số nguyên không âm, nhận '$RETENTION_DAYS'" >&2; exit 1;; esac
ts="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "$BACKUP_DIR"
# không để lại tệp .tmp dở dang khi thoát (thành công hay lỗi)
trap 'rm -f "$BACKUP_DIR/giapha-$ts.dump.tmp" "$BACKUP_DIR/photos-$ts.tar.gz.tmp" "$BACKUP_DIR/.last_success.tmp"' EXIT

pg_dump -Fc -h "${PGHOST:?}" -U "${PGUSER:?}" "${PGDATABASE:?}" > "$BACKUP_DIR/giapha-$ts.dump.tmp"
pg_restore -l "$BACKUP_DIR/giapha-$ts.dump.tmp" > /dev/null
mv "$BACKUP_DIR/giapha-$ts.dump.tmp" "$BACKUP_DIR/giapha-$ts.dump"

if [ "$SKIP_PHOTOS" = "1" ]; then
  echo "[$(date -u +%FT%TZ)] CẢNH BÁO: SKIP_PHOTOS=1, bỏ qua sao lưu ảnh" >&2
elif [ ! -d "$MINIO_DATA_DIR" ]; then
  echo "[$(date -u +%FT%TZ)] LỖI: không thấy $MINIO_DATA_DIR (đặt SKIP_PHOTOS=1 nếu cố ý bỏ qua ảnh)" >&2
  exit 1
else
  rc=0
  tar --exclude=./.minio.sys/tmp --exclude=./.rustfs.sys/tmp -C "$MINIO_DATA_DIR" -czf "$BACKUP_DIR/photos-$ts.tar.gz.tmp" . || rc=$?
  if [ "$rc" -eq 1 ]; then
    echo "[$(date -u +%FT%TZ)] CẢNH BÁO: tar báo tệp thay đổi khi đọc (exit 1), vẫn chấp nhận" >&2
  elif [ "$rc" -ge 2 ]; then
    echo "[$(date -u +%FT%TZ)] LỖI: tar thất bại (exit $rc)" >&2
    exit "$rc"
  fi
  tar -tzf "$BACKUP_DIR/photos-$ts.tar.gz.tmp" > /dev/null
  mv "$BACKUP_DIR/photos-$ts.tar.gz.tmp" "$BACKUP_DIR/photos-$ts.tar.gz"
fi

find "$BACKUP_DIR" -type f \( -name 'giapha-*.dump' -o -name 'photos-*.tar.gz' \) -mmin +"$((RETENTION_DAYS * 1440))" -delete
find "$BACKUP_DIR" -type f -name '*.tmp' -mtime +0 -delete

# dấu hiệu thành công: chỉ ghi ở cuối một lần chạy hoàn toàn thành công (healthcheck đọc tệp này)
date -u +%FT%TZ > "$BACKUP_DIR/.last_success.tmp"
mv "$BACKUP_DIR/.last_success.tmp" "$BACKUP_DIR/.last_success"
echo "[$(date -u +%FT%TZ)] backup ok: giapha-$ts.dump"

# bản local đã an toàn; offsite lỗi thì báo và thoát khác 0, nhưng .last_success vẫn được giữ
if ! /usr/local/bin/offsite.sh "$ts"; then
  echo "[$(date -u +%FT%TZ)] LỖI: offsite thất bại (bản local giapha-$ts.dump vẫn hợp lệ)" >&2
  exit 1
fi
