#!/usr/bin/env bash
# Triển khai toàn bộ app (Postgres + MinIO + Backend + Frontend) bằng Docker ở máy local.
# Cách dùng:  ./deploy.sh [lệnh]      (mặc định: up)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
COMPOSE=(docker compose -f docker-compose.local.yml)

# Biến có thể ghi đè khi chạy, ví dụ:  SEED=2000 WEB_PORT=9000 ./deploy.sh up
export WEB_PORT="${WEB_PORT:-7000}"
export API_PORT="${API_PORT:-7080}"
export MINIO_PORT="${MINIO_PORT:-7001}"
export DEV_SEED_PERSONS="${SEED:-300}"          # số người mẫu (0 = DB trống); chỉ sinh khi chưa có dòng họ nào
export BOOTSTRAP_ADMIN_USER="${ADMIN_USER:-admin}"
export BOOTSTRAP_ADMIN_PASSWORD="${ADMIN_PASSWORD:-password123}"
URL="http://localhost:${WEB_PORT}"

usage() {
  cat <<USAGE
Lệnh:
  up        Build và chạy toàn bộ (mặc định)
  down      Dừng và gỡ container (giữ dữ liệu)
  restart   Build lại và chạy lại backend + web (giữ dữ liệu)
  reset     XOÁ TOÀN BỘ dữ liệu (DB, ảnh) rồi chạy lại từ đầu
  status    Xem trạng thái các service
  logs [s]  Xem log (s = postgres|minio|backend|web, bỏ trống = tất cả)
  help      Hiện trợ giúp

Biến môi trường (tuỳ chọn): SEED (mặc định 300), WEB_PORT (7000), API_PORT (7080), MINIO_PORT (7001), ADMIN_USER (admin), ADMIN_PASSWORD (password123),
  NO_MINIO=1 (bỏ MinIO nếu không pull được image; upload ảnh sẽ không dùng được)
USAGE
}

require_docker() {
  command -v docker >/dev/null 2>&1 || { echo "Chưa cài Docker." >&2; exit 1; }
  if ! docker info >/dev/null 2>&1; then
    echo "Docker chưa chạy. Mở Docker Desktop (macOS: open -a Docker) rồi chạy lại." >&2
    exit 1
  fi
}

wait_ready() {
  echo "Đang chờ app sẵn sàng tại ${URL} (lần đầu có thể mất 1-2 phút)..."
  for _ in $(seq 1 90); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "${URL}/api/v1/me" || true)"
    if [ "$code" = "401" ]; then
      echo
      echo "Sẵn sàng:  ${URL}"
      echo "Đăng nhập: ${BOOTSTRAP_ADMIN_USER} / ${BOOTSTRAP_ADMIN_PASSWORD}"
      echo "Swagger:   http://localhost:${API_PORT}/swagger-ui/index.html"
      [ -z "${NO_MINIO:-}" ] && echo "MinIO:     http://localhost:${MINIO_PORT} (minioadmin / minioadmin)"
      return 0
    fi
    sleep 2
  done
  echo "Quá thời gian chờ. Xem log:  ./deploy.sh logs backend" >&2
  return 1
}

cmd="${1:-up}"
case "$cmd" in
  up)
    require_docker
    scale=()
    [ -n "${NO_MINIO:-}" ] && scale=(--scale minio=0)
    "${COMPOSE[@]}" up -d --build "${scale[@]}"
    wait_ready
    ;;
  down)
    require_docker
    "${COMPOSE[@]}" down
    ;;
  restart)
    require_docker
    "${COMPOSE[@]}" up -d --build backend web
    wait_ready
    ;;
  reset)
    require_docker
    read -r -p "Lệnh này XOÁ toàn bộ dữ liệu local (DB, ảnh). Gõ 'yes' để tiếp tục: " ans
    [ "$ans" = "yes" ] || { echo "Đã huỷ."; exit 1; }
    "${COMPOSE[@]}" down -v
    exec "$0" up
    ;;
  status)
    require_docker
    "${COMPOSE[@]}" ps
    ;;
  logs)
    require_docker
    shift || true
    "${COMPOSE[@]}" logs -f --tail=100 "$@"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
