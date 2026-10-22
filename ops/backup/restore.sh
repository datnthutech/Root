#!/usr/bin/env bash
# Dùng: restore.sh /backups/giapha-<ts>.dump <tên-DB-đích>
# Tên đích chỉ được dạng giapha_verify_* hoặc giapha_restore_* (đặt FORCE_TARGET=1 để bỏ qua, cố ý).
# CẢNH BÁO: xoá DB đích nếu đã tồn tại. Không bao giờ dùng với tên DB đang chạy thật.
#
# DR (mất máy chủ/DB hỏng): KHÔNG khôi phục đè lên DB đang chạy. Khôi phục sang tên mới
#   restore.sh /backups/giapha-<ts>.dump giapha_restore_<ngày>
# kiểm tra số liệu, rồi dừng backend và trỏ lại DB_URL sang DB mới (hoặc đổi tên DB sau khi
# dừng backend), khởi động lại. Ảnh: giải nén photos-<ts>.tar.gz vào volume MinIO.
set -euo pipefail
DUMP="${1:?thiếu tệp .dump}"
TARGET="${2:?thiếu tên DB đích}"
[ -f "$DUMP" ] || { echo "không thấy tệp $DUMP" >&2; exit 1; }
if [ "${FORCE_TARGET:-0}" != "1" ] && ! [[ "$TARGET" =~ ^giapha_(verify|restore)_[A-Za-z0-9_]+$ ]]; then
  echo "từ chối tên đích '$TARGET': phải khớp ^giapha_(verify|restore)_[A-Za-z0-9_]+\$ (hoặc FORCE_TARGET=1)" >&2
  exit 1
fi
[ "$TARGET" != "${PGDATABASE:-giapha}" ] || { echo "từ chối ghi đè DB đang chạy ($TARGET)" >&2; exit 1; }
dropdb  -h "${PGHOST:?}" -U "${PGUSER:?}" --if-exists "$TARGET"
createdb -h "$PGHOST" -U "$PGUSER" "$TARGET"
pg_restore -h "$PGHOST" -U "$PGUSER" -d "$TARGET" --no-owner --exit-on-error "$DUMP"
echo "đã khôi phục $DUMP vào $TARGET"
