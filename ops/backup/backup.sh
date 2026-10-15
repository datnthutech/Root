#!/usr/bin/env bash
set -euo pipefail
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
MINIO_DATA_DIR="${MINIO_DATA_DIR:-/minio-data}"
ts="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "$BACKUP_DIR"
# không để lại tệp .tmp dở dang nếu lỗi
trap 'rm -f "$BACKUP_DIR/giapha-$ts.dump.tmp" "$BACKUP_DIR/photos-$ts.tar.gz.tmp"' ERR

pg_dump -Fc -h "${PGHOST:?}" -U "${PGUSER:?}" "${PGDATABASE:?}" > "$BACKUP_DIR/giapha-$ts.dump.tmp"
mv "$BACKUP_DIR/giapha-$ts.dump.tmp" "$BACKUP_DIR/giapha-$ts.dump"

if [ -d "$MINIO_DATA_DIR" ]; then
  tar -C "$MINIO_DATA_DIR" -czf "$BACKUP_DIR/photos-$ts.tar.gz.tmp" .
  mv "$BACKUP_DIR/photos-$ts.tar.gz.tmp" "$BACKUP_DIR/photos-$ts.tar.gz"
fi

find "$BACKUP_DIR" -type f \( -name 'giapha-*.dump' -o -name 'photos-*.tar.gz' \) -mtime +"$RETENTION_DAYS" -delete
echo "[$(date -u +%FT%TZ)] backup ok: giapha-$ts.dump"
