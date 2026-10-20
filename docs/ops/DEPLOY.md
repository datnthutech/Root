# Hướng dẫn vận hành — Gia Phả Web

Mọi lệnh chạy ở thư mục gốc dự án (`src/Root`) trên máy chủ. Đặt bí danh cho gọn:

```bash
alias dc='docker compose -f docker-compose.prod.yml --env-file .env.prod'
```

## 1. Chuẩn bị máy chủ
- Ubuntu 22.04+, từ 2 vCPU / 4 GB RAM; mở cổng 80 và 443.
- Cài Docker và Compose plugin (`docker compose version` phải chạy được).
- DNS: bản ghi `A` của `DOMAIN` trỏ về IP máy chủ (Caddy cần cổng 80/443 và DNS đúng để lấy chứng chỉ Let's Encrypt).

## 2. Lấy mã nguồn và cấu hình
```bash
git clone <địa-chỉ-repo> giapha && cd giapha/src/Root
cp .env.prod.example .env.prod
chmod 600 .env.prod
```
Điền MỌI giá trị trong `.env.prod` (không để trống; thiếu biến bắt buộc thì `docker compose` dừng ngay với thông báo `... is required`). Gợi ý sinh giá trị (có sẵn trong tệp mẫu):
```bash
openssl rand -base64 24   # DB_PASSWORD
openssl rand -base64 48   # JWT_SECRET
openssl rand -hex 12      # MINIO_ACCESS_KEY
openssl rand -base64 36   # MINIO_SECRET_KEY
openssl rand -base64 18   # BOOTSTRAP_ADMIN_PASSWORD
```
Không commit `.env.prod`.

### Biến môi trường (`.env.prod`)
| Biến | Bắt buộc | Ý nghĩa |
| --- | --- | --- |
| `DOMAIN` | có | Tên miền; Caddy phục vụ HTTPS cho tên này |
| `DB_PASSWORD` | có | Mật khẩu PostgreSQL (user `giapha`, DB `giapha`); dùng chung cho `backend` và `backup` |
| `JWT_SECRET` | có | Khoá ký JWT (`openssl rand -base64 48`). Đổi khoá sẽ vô hiệu mọi phiên đang đăng nhập |
| `MINIO_ACCESS_KEY` | có | Access key của kho ảnh S3 (RustFS, truyền vào `RUSTFS_ACCESS_KEY`; lưu ảnh), `backend` dùng cùng giá trị |
| `MINIO_SECRET_KEY` | có | Secret key của kho ảnh S3 (RustFS, truyền vào `RUSTFS_SECRET_KEY`) |
| `BOOTSTRAP_ADMIN_USER` | không (mặc định `admin`) | Tên admin khởi tạo |
| `BOOTSTRAP_ADMIN_PASSWORD` | có | Mật khẩu admin khởi tạo |

Các giá trị cố định trong `docker-compose.prod.yml` (không nằm trong `.env.prod`): `COOKIE_SECURE=true`, `SWAGGER_ENABLED=false`, `MINIO_BUCKET=giapha`, `RETENTION_DAYS=14` (dịch vụ `backup`).

## 3. Dựng hệ thống
```bash
dc up -d --build
dc ps                       # tất cả dịch vụ phải ở trạng thái running (postgres healthy)
curl -I https://$DOMAIN/    # mong đợi HTTP 200 và header Strict-Transport-Security
curl -I http://$DOMAIN/     # mong đợi chuyển hướng sang https
```
Dịch vụ: `postgres`, `minio`, `backend`, `caddy` (cổng 80/443, phục vụ giao diện và proxy `/api/*`), `backup`. Lần dựng đầu có thể mất vài phút; `backend` khởi động chậm hơn các dịch vụ còn lại (xem `dc logs -f backend`).

## 4. Lần đầu sử dụng
1. Mở `https://$DOMAIN/`, đăng nhập bằng `BOOTSTRAP_ADMIN_USER` / `BOOTSTRAP_ADMIN_PASSWORD`.
2. Vào Admin → Quản lý tài khoản: tạo tài khoản admin thật (mật khẩu mạnh), đăng nhập bằng tài khoản đó, rồi **khoá tài khoản bootstrap**. Không dùng lâu dài tài khoản bootstrap.
3. Tạo dòng họ (hoặc để nhập Excel tạo dòng họ — xem mục 9).
4. Nhập dữ liệu từ Excel tại `/admin/import`: tải mẫu → kiểm tra thử → nhập thật (mục 9).

Người dùng tự đăng ký có trạng thái `PENDING` (không đăng nhập được, báo `ACCOUNT_PENDING`) cho đến khi admin duyệt trong trang quản lý tài khoản. Trạng thái tài khoản: `PENDING`, `ACTIVE`, `LOCKED`.

## 5. Cập nhật phiên bản
```bash
dc exec backup backup.sh          # sao lưu TRƯỚC khi cập nhật
git pull
dc up -d --build                  # Flyway tự áp migration mới khi backend khởi động
dc ps && dc logs --tail=100 backend
```
Chạy lại `up -d --build` lần thứ hai (hoặc nhiều lần) là an toàn: Flyway chỉ áp migration chưa có trong `flyway_schema_history`; không bao giờ sửa migration đã commit (thay đổi DB = thêm `V{n}__*.sql`).

## 6. Quay lui
Chỉ mã nguồn (migration chưa chạy hoặc tương thích ngược):
```bash
git checkout <tag-cũ>
dc up -d --build
```
Nếu migration đã chạy và cần quay lui cả DB — **cách an toàn (DR, khôi phục sang tên DB mới rồi trỏ lại)**:
```bash
dc exec backup ls -1t /backups
dc exec backup restore.sh /backups/giapha-<ts>.dump giapha_restore_<ngày>
# kiểm tra số liệu trong DB mới (psql), sau đó đổi DB_URL của backend trong docker-compose.prod.yml
# thành jdbc:postgresql://postgres:5432/giapha_restore_<ngày>, rồi:
dc up -d --build backend
```
Hoặc khôi phục vào DB chính sau khi dừng `backend` (tên `giapha` bị `restore.sh` từ chối vì trùng DB đang chạy và không khớp mẫu tên an toàn, nên phải đặt `FORCE_TARGET=1`, và đây là thao tác XOÁ DB `giapha`):
```bash
dc stop backend
dc exec -e FORCE_TARGET=1 -e PGDATABASE=postgres backup restore.sh /backups/giapha-<ts>.dump giapha
dc start backend
```
Lưu ý về `restore.sh`: (1) tên DB đích mặc định chỉ được dạng `giapha_verify_*` hoặc `giapha_restore_*`, muốn khác phải đặt `FORCE_TARGET=1`; (2) tự từ chối nếu tên đích trùng `PGDATABASE` (mặc định `giapha`) — vì vậy ví dụ trên đổi `PGDATABASE` sang `postgres` chỉ cho lệnh đó; (3) luôn `dropdb --if-exists` rồi tạo lại DB đích. Cách khôi phục vào DB chính chưa được thử trong quá trình nghiệm thu; hãy thử trên bản sao trước.

Ảnh (RustFS, volume `miniodata`): giải nén `photos-<ts>.tar.gz` vào volume này khi cần khôi phục ảnh.

## 7. Sao lưu, khôi phục, kiểm tra
- Dịch vụ `backup` chạy cron **02:30 UTC** hằng ngày (`ops/backup/crontab`): `pg_dump -Fc` ra `/backups/giapha-<ts>.dump` (có kiểm `pg_restore -l`) và nén ảnh MinIO ra `/backups/photos-<ts>.tar.gz`. Giữ `RETENTION_DAYS=14` ngày.
- Chạy tay một lần: `dc exec backup backup.sh`. Bỏ qua ảnh (cố ý): `SKIP_PHOTOS=1`.
- Dấu thành công: tệp `/backups/.last_success` chỉ được ghi khi một lần sao lưu hoàn toàn thành công. Healthcheck của `backup` báo `unhealthy` nếu dấu này cũ hơn 26 giờ hoặc chưa có (có `start_period` 26 giờ để chờ lần cron đầu tiên). Kiểm: `dc ps` (cột health) hoặc `dc exec backup cat /backups/.last_success`.
- **Thử khôi phục (bắt buộc, nên chạy định kỳ):**
  ```bash
  dc exec backup verify-restore.sh      # kết quả mong đợi: dòng cuối "VERIFY OK (...)"
  ```
  Script khôi phục bản mới nhất vào DB tạm `giapha_verify_<pid>`, so số dòng các bảng `person, union_rel, parent_child, clan, clan_member, app_user, audit_log` với DB hiện tại (dung sai hai chiều: lớn hơn của 5 dòng và 5 %), so `flyway_schema_history` tuyệt đối, và thất bại nếu bản mới nhất cũ hơn 26 giờ. Xong tự xoá DB tạm.
- **Chép `backups` ra nơi khác** — sao lưu cùng máy không chống được hỏng ổ đĩa. Ví dụ hằng ngày (cron của máy chủ, sau 02:30 UTC):
  ```bash
  # đường dẫn volume: docker volume inspect giapha-prod_backups
  0 4 * * * rsync -a /var/lib/docker/volumes/giapha-prod_backups/_data/ backup-user@host-khac:/duong-dan/giapha-backups/
  ```
  (hoặc `rclone sync` lên kho đám mây). Việc này CHƯA được thiết lập sẵn trong repo.

## 8. Sự cố thường gặp
- **Caddy không lấy được chứng chỉ**: kiểm DNS `A` của `DOMAIN`, cổng 80/443 mở trên tường lửa/nhà cung cấp, `dc logs caddy`. Đừng thử liên tục (Let's Encrypt giới hạn tần suất).
- **`backend` khởi động chậm / Caddy trả 502 trong vài giây đầu**: bình thường (chưa có healthcheck cho `backend`); xem `dc logs -f backend`, đợi Flyway chạy xong.
- **Hết dung lượng đĩa**: `df -h`, `du -sh /var/lib/docker/volumes/*`, `docker system df`; giảm `RETENTION_DAYS` hoặc chép `backups` ra ngoài rồi dọn.
- **`backup` unhealthy**: `dc logs backup`; chạy tay `dc exec backup backup.sh` để xem lỗi (ví dụ `/minio-data` không có → báo LỖI trừ khi `SKIP_PHOTOS=1`).
- **Thiếu biến môi trường**: `docker compose` báo `<BIẾN> is required` — điền vào `.env.prod`.

## 9. Nhập Excel
Chỉ ADMIN. Trang `/admin/import` (API: `GET /api/v1/import/template`, `POST /api/v1/import/excel?dryRun=true|false`).
1. Tải tệp mẫu `mau-nhap-gia-pha.xlsx` và điền dữ liệu.
2. **Kiểm tra thử trước** (`dryRun=true`, mặc định): không lưu gì, trả báo cáo số người/hôn phối/quan hệ, danh sách lỗi và cảnh báo. Sửa tệp cho đến khi không còn lỗi.
3. Nhập thật (`dryRun=false`). Nếu còn lỗi, API trả 422 `IMPORT_INVALID` và **không nhập gì cả** (toàn bộ nằm trong một giao dịch).
4. Giới hạn: tối đa **20.000 dòng dữ liệu tổng cộng** (đếm trên mọi sheet, bỏ dòng trống), tối đa **10 sheet**, tệp tối đa **6 MB**.
5. Dòng họ: tên dòng họ trùng (không phân biệt hoa/thường) với dòng họ đã có nhưng khác thủy tổ → lỗi `CLAN_EXISTS` (nhập Excel không đổi được thủy tổ). Vì vậy **nhập lại cùng một tệp lần hai sẽ bị từ chối** `IMPORT_INVALID` / `CLAN_EXISTS`; nếu cùng thủy tổ thì dòng họ được cập nhật.

## 10. Ma trận phân quyền (tóm tắt)
Ba vai trò: `VIEWER` (cấp thấp), `EDITOR` (cấp trung), `ADMIN` (cấp cao). Bắt buộc đăng nhập (trừ `/api/v1/auth/*`, `/actuator/health`). Nguồn đúng là `SecurityConfig` và `PermissionMatrixTest`.
| Chức năng | VIEWER | EDITOR | ADMIN |
| --- | --- | --- | --- |
| Xem cây, tra cứu, popup | có | có | có |
| Ngày sinh đầy đủ của người còn sống | không (chỉ năm sinh) | có | có |
| Thêm người, gắn/gỡ quan hệ, đổi thứ tự anh em | không | có | có |
| Sửa hồ sơ / đổi ảnh | không | chỉ người do mình tạo, trong 24 giờ (sau đó `EDIT_WINDOW_EXPIRED`) | có |
| Xem nhật ký sửa (`GET /audit`) | không | có | có |
| Xoá người, khôi phục từ nhật ký (revert) | không | không | có |
| Quản lý tài khoản (`/admin/**`), nhập Excel (`/import/**`) | không | không | có |

## 10b. Mật khẩu và chặn đăng nhập dò mật khẩu
- **Người dùng tự đổi mật khẩu**: `PUT /api/v1/me/password` (cần mật khẩu hiện tại). Giao diện: nút "Đổi mật khẩu" ở thanh trên cùng, cạnh "Đăng xuất".
- **Admin đặt lại mật khẩu hộ**: `PUT /api/v1/admin/users/{id}/password`. Giao diện: Admin → Quản lý tài khoản → "Đặt lại mật khẩu". Thao tác này tự **mở khoá** tài khoản và xoá bộ đếm sai mật khẩu.
- Cả hai trường hợp đều **thu hồi toàn bộ refresh token** của người đó: các thiết bị đang đăng nhập sẽ bị đẩy ra trong vòng tối đa 15 phút (hết hạn access token).
- **Khoá tạm tài khoản**: sai mật khẩu 5 lần → khoá 15 phút, trả `ACCOUNT_LOCKED` (423). Đăng nhập đúng sẽ xoá bộ đếm. Trong lúc đang khoá, người gõ sai mật khẩu chỉ nhận 401 (không tiết lộ là tài khoản đang bị khoá).
- **Chặn tần suất theo IP** (`AuthRateLimitFilter`), trả `TOO_MANY_REQUESTS` (429) kèm header `Retry-After`:

  | Đường dẫn | Hạn mức |
  | --- | --- |
  | `POST /api/v1/auth/login` | 10 lần / phút / IP |
  | `POST /api/v1/auth/register` | 5 lần / giờ / IP |
  | `POST /api/v1/auth/refresh` | 30 lần / phút / IP |

  Bật/tắt bằng biến `RATE_LIMIT_ENABLED` (mặc định `true`; chỉ nên tắt khi gỡ lỗi). Ở stack local `deploy.sh` mặc định **tắt** vì mọi request trong Docker dùng chung một IP gateway.
- Dọn refresh token hết hạn: tác vụ định kỳ 03:15 hằng ngày (`RefreshTokenCleanup`), không cần can thiệp.

## 11. Nhật ký và khôi phục (revert) — lưu ý
- Revert một mục `UPDATE` ghi đè **toàn bộ snapshot "trước" của người đó** (không chỉ trường bạn thấy thay đổi): các sửa đổi sau đó trên các trường khác cũng bị đưa về giá trị cũ. Xem kỹ trước khi bấm.
- Revert việc gỡ hôn phối (union `DELETE`) tạo hôn phối mới với **id mới** và không gắn lại các `parent_child.union_id` cũ (chúng đã bị bỏ khi xoá); cần gắn lại quan hệ con–hôn phối thủ công nếu muốn.
- Revert `CREATE` người là xoá mềm; revert `DELETE` người là khôi phục. Nếu một đối tượng liên quan đã bị xoá thì mục đó không khôi phục được (báo lỗi, không đổi dữ liệu). Mỗi lần revert tự ghi thêm một mục nhật ký `REVERT`.

## 12. Hạn chế đã biết
- Mỗi request đã đăng nhập tra lại tài khoản trong DB (role và trạng thái lấy từ DB, không tin role trong JWT), nên hạ quyền/khoá có hiệu lực ngay. Riêng **đổi/đặt lại mật khẩu** không vô hiệu access token đang dùng: thiết bị cũ còn dùng được tối đa ~15 phút (refresh token đã bị thu hồi).
- Chặn tần suất đăng nhập đếm **theo IP trong bộ nhớ của backend**: mất khi khởi động lại và không chia sẻ giữa nhiều bản backend (hiện chỉ chạy 1 bản nên chấp nhận được).
- IP dùng để đếm lấy từ `X-Forwarded-For` do Caddy **ghi đè** (`header_up X-Forwarded-For {remote_host}`). Giả định: **Caddy là proxy duy nhất đứng trước backend**. Nếu sau này đặt CDN/cân bằng tải phía trước Caddy thì mọi người dùng sẽ mang cùng một IP (của CDN) và chung hạn mức — khi đó phải đổi sang lấy IP theo số chặng proxy tin cậy.
- Kho ảnh: image `rustfs/rustfs:1.0.3` (đã ghim; thay cho `minio/minio` đã bị gỡ khỏi Docker Hub), chưa có healthcheck; chưa chạy thử ảnh trên máy chủ thật.
- Các image `node:22`, `caddy:2`, `postgres:16` dùng tag trôi; Caddy chạy root; `backend` chưa có healthcheck. (CSP và `Permissions-Policy` đã có trong `ops/caddy/Caddyfile`; CSP còn `'unsafe-eval'` cho PixiJS và `'unsafe-inline'` cho style của Ant Design.)
- Sao lưu mặc định nằm cùng máy chủ; chưa có sao lưu ngoài máy (mục 7).
- Cây 2.000 người khi mở lần đầu chỉ bố cục phần đang mở (đo cục bộ: 18/2000 nút), nên "thời gian mở cây" đo ở trạng thái thu gọn.
- Hiệu năng vẽ cây 10.000 nút (M1: ≥ 55 fps, hover) chưa được đo trên phần cứng thật (xem `docs/reports/M1-render-prototype.md`).
- Nhập Excel không đổi được thủy tổ của dòng họ đã tồn tại (`CLAN_EXISTS`).
