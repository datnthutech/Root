#!/usr/bin/env bash
# Dùng: offsite.sh <ts>
# Mã hoá bằng age (chỉ cần khoá công khai) các tệp sao lưu của lần <ts> rồi đẩy lên OFFSITE_REMOTE bằng rclone.
# OFFSITE_REMOTE rỗng: bỏ qua. OFFSITE_REMOTE có giá trị mà khoá thiếu/sai: thoát lỗi, tuyệt đối không đẩy bản chưa mã hoá.
set -euo pipefail
umask 077
BACKUP_DIR="${BACKUP_DIR:-/backups}"
OFFSITE_REMOTE="${OFFSITE_REMOTE:-}"
OFFSITE_RETENTION_DAYS="${OFFSITE_RETENTION_DAYS:-30}"
SKIP_PHOTOS="${SKIP_PHOTOS:-0}"
STAGE="$BACKUP_DIR/.offsite-tmp"

if [ -z "$OFFSITE_REMOTE" ]; then
  echo "[$(date -u +%FT%TZ)] CẢNH BÁO: OFFSITE_REMOTE rỗng, bỏ qua đẩy bản sao ra ngoài" >&2
  exit 0
fi

ts="${1:?thiếu timestamp (dùng: offsite.sh <ts>)}"
[[ "$ts" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || { echo "timestamp sai định dạng: '$ts'" >&2; exit 1; }
case "$OFFSITE_RETENTION_DAYS" in ''|*[!0-9]*) echo "OFFSITE_RETENTION_DAYS phải là số nguyên không âm, nhận '$OFFSITE_RETENTION_DAYS'" >&2; exit 1;; esac

# mỗi khoá công khai là một -r; kiểm định dạng trước khi mã hoá để không bao giờ rơi về bản chưa mã hoá
read -ra RECIPS <<< "${BACKUP_AGE_RECIPIENTS:-}" || true
[ "${#RECIPS[@]}" -gt 0 ] || { echo "LỖI: OFFSITE_REMOTE đã đặt nhưng BACKUP_AGE_RECIPIENTS rỗng, không đẩy bản chưa mã hoá" >&2; exit 1; }
RECIP_ARGS=()
for r in "${RECIPS[@]}"; do
  [[ "$r" =~ ^age1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{58}$ ]] || { echo "LỖI: BACKUP_AGE_RECIPIENTS có khoá không hợp lệ (phải dạng age1...)" >&2; exit 1; }
  RECIP_ARGS+=(-r "$r")
done

mkdir -p "$STAGE"
# không để lại tệp tạm dở dang khi thoát (thành công hay lỗi)
trap 'rm -f "$STAGE"/*.age "$STAGE"/*.age.tmp "$BACKUP_DIR/.last_offsite_success.tmp"' EXIT

# mã hoá một tệp, kiểm tiêu đề age, đổi tên rồi đẩy lên remote (rclone tự so md5 với Drive)
push() {
  local name="$1"
  local enc="$STAGE/$name.age"
  age "${RECIP_ARGS[@]}" -o "$enc.tmp" "$BACKUP_DIR/$name"
  [ "$(head -n 1 "$enc.tmp")" = "age-encryption.org/v1" ] || { echo "LỖI: $name.age không có tiêu đề age hợp lệ" >&2; exit 1; }
  mv "$enc.tmp" "$enc"
  rclone copyto "$enc" "$OFFSITE_REMOTE/$name.age" --retries 3 --low-level-retries 10
  rm -f "$enc"
  echo "[$(date -u +%FT%TZ)] đã đẩy $name.age"
}

dump="giapha-$ts.dump"
[ -f "$BACKUP_DIR/$dump" ] || { echo "LỖI: không thấy $BACKUP_DIR/$dump" >&2; exit 1; }
push "$dump"

photos="photos-$ts.tar.gz"
if [ -f "$BACKUP_DIR/$photos" ]; then
  push "$photos"
elif [ "$SKIP_PHOTOS" = "1" ]; then
  echo "[$(date -u +%FT%TZ)] CẢNH BÁO: SKIP_PHOTOS=1, không đẩy ảnh" >&2
else
  echo "LỖI: không thấy $BACKUP_DIR/$photos (đặt SKIP_PHOTOS=1 nếu cố ý bỏ qua ảnh)" >&2
  exit 1
fi

# chỉ xoá tệp đúng mẫu tên trên remote
rclone delete "$OFFSITE_REMOTE" --min-age "${OFFSITE_RETENTION_DAYS}d" --include "giapha-*.dump.age" --include "photos-*.tar.gz.age" --drive-use-trash=false

# dấu thành công offsite: chỉ ghi khi mọi bước trên đã thành công
date -u +%FT%TZ > "$BACKUP_DIR/.last_offsite_success.tmp"
mv "$BACKUP_DIR/.last_offsite_success.tmp" "$BACKUP_DIR/.last_offsite_success"
echo "[$(date -u +%FT%TZ)] offsite ok: giapha-$ts"
