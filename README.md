# Gia Phả Web

Web gia phả nhiều dòng họ (nội + ngoại) dạng cây dọc, kéo/phóng như bản đồ, bắt buộc đăng nhập, 3 vai trò (ADMIN, EDITOR, VIEWER), có nhật ký sửa/khôi phục, nhập từ Excel, sao lưu hằng ngày.

## Cấu trúc thư mục
- `backend/` — Spring Boot (Java), Flyway, PostgreSQL, RustFS tương thích S3 (ảnh)
- `frontend/` — giao diện (Vite), vẽ cây bằng canvas/WebGL, script đo `e2e/measure.mjs`
- `ops/` — `backup/` (sao lưu, khôi phục, kiểm tra khôi phục), `caddy/` (HTTPS, proxy)
- `docker-compose.yml` — chạy local trong Docker (qua `deploy.sh`); `docker-compose.prod.yml` — triển khai thật
- `docs/` — spec, kế hoạch, báo cáo, hướng dẫn vận hành

## Chạy cục bộ (toàn bộ trong Docker)
```bash
./deploy.sh          # build + chạy Postgres, kho ảnh, backend, frontend → http://localhost:7070 (admin / password123)
./deploy.sh help     # down | restart | reset | status | logs
```
Cổng: web 7070, API/Swagger 7080, console kho ảnh `http://localhost:7001/rustfs/console/` (đổi bằng `WEB_PORT`, `API_PORT`, `MINIO_PORT`). Chỉ cần Docker, không cần cài JDK/Node. `docker-compose.yml` là stack local; `docker-compose.prod.yml` dùng cho máy chủ thật.

Ở local, chặn tần suất đăng nhập mặc định **tắt** (trong Docker mọi request đến từ cùng một IP gateway nên hạn mức theo IP sẽ tính chung cho cả máy); bật lại bằng `RATE_LIMIT=1 ./deploy.sh restart`. Khoá tạm tài khoản sau 5 lần sai mật khẩu (15 phút) vẫn luôn có hiệu lực.

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
