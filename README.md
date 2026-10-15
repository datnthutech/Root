# Gia Phả Web

Web gia phả nhiều dòng họ (nội + ngoại) dạng cây dọc, kéo/phóng như bản đồ, bắt buộc đăng nhập, 3 vai trò (ADMIN, EDITOR, VIEWER), có nhật ký sửa/khôi phục, nhập từ Excel, sao lưu hằng ngày.

## Cấu trúc thư mục
- `backend/` — Spring Boot (Java), Flyway, PostgreSQL, MinIO (ảnh)
- `frontend/` — giao diện (Vite), vẽ cây bằng canvas/WebGL, script đo `e2e/measure.mjs`
- `ops/` — `backup/` (sao lưu, khôi phục, kiểm tra khôi phục), `caddy/` (HTTPS, proxy)
- `docker-compose.yml` — hạ tầng phát triển; `docker-compose.prod.yml` — triển khai thật
- `docs/` — spec, kế hoạch, báo cáo, hướng dẫn vận hành

## Chạy cục bộ bằng Docker (một lệnh)
```bash
./deploy.sh          # build + chạy Postgres, MinIO, backend, frontend → http://localhost:7000 (admin / password123)
./deploy.sh help     # down | restart | reset | status | logs
```

## Chạy cục bộ để phát triển (không đóng gói)
```bash
docker compose up -d
# Backend (thư mục backend)
DEV_SEED_PERSONS=2000 BOOTSTRAP_ADMIN_USER=admin BOOTSTRAP_ADMIN_PASSWORD=password123 ./gradlew bootRun
# Frontend (thư mục frontend)
npm install && npm run dev
```

## Kiểm thử
```bash
./gradlew test       # backend (thư mục backend)
./gradlew perfTest   # test hiệu năng backend
npm test             # frontend (thư mục frontend)
npm run measure      # đo hiệu năng frontend (BASE_URL, E2E_USER, E2E_PASS cho máy chủ thật)
```

## Tài liệu
- Thiết kế: `docs/superpowers/specs/2026-10-14-gia-pha-design.md`
- Kế hoạch: `docs/superpowers/plans/00-INDEX.md`
- Vận hành: `docs/ops/DEPLOY.md`
- Nghiệm thu: `docs/ops/ACCEPTANCE.md`
- Báo cáo M1: `docs/reports/M1-render-prototype.md`
