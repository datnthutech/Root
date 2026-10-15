# Nghiệm thu M1–M4 — Phát hành

Ngày ghi: 2026-10-15  Người thực hiện: ____  Máy chủ: CHƯA TRIỂN KHAI (chưa có máy chủ thật)  Phiên bản (git sha): ____

**Quy ước trạng thái:** `ĐÃ CHẠY` = có số liệu thật, ghi rõ điều kiện. `CHƯA ĐO` / `CHƯA KIỂM` = chưa chạy, KHÔNG được tính là đạt. Không ô nào dưới đây được đánh dấu đạt nếu chưa chạy; không chỉnh ngưỡng để cho đạt.

**Kết luận hiện tại: CHƯA NGHIỆM THU ĐƯỢC (các mục CHƯA ĐO bên dưới cần làm trên phần cứng/máy chủ thật).**

## Số liệu đã đo (điều kiện cục bộ, KHÔNG phải máy chủ thật)
- Graph API, MockMvc cục bộ (nóng, gzip mô phỏng chứ không qua Tomcat): 2.000 người = **40 ms / 37 KB gz**; 10.000 người = **195 ms / 179 KB gz** (`./gradlew perfTest`).
- Nhập Excel: khoảng **2,4 ms/người** (đo cục bộ, ghi nhận trong quá trình làm Task 2/8; chưa đo lại bằng tệp 2.000 người qua trang `/admin/import`).
- `npm run measure` (Playwright, headless, **software GL — không đại diện**): cây mở lần đầu 1090 ms (dev server), nóng 297 ms, popup **278 ms**, treeNodes 18 (chỉ 18/2000 nút được bố cục ở màn hình ban đầu nên đây là cây thu gọn). Popup 278 ms đã sát ngưỡng 300 ms ngay cả cục bộ, cần đo trên máy chủ thật.
- M1 bố cục 10.000 nút: 31 ms (2.000 người: 25 ms), headless Chromium software GL, dev build (`docs/reports/M1-render-prototype.md`). FPS tham khảo trên software GL: 15 fps (2.000), 6 fps (10.000) — KHÔNG đại diện.

## M1 — Bản thử vẽ (cổng)
- [x] Bố cục 10.000 nút < 1000 ms — ĐÃ CHẠY: 31 ms (software GL, dev build; phép đo chỉ dùng CPU nên hợp lệ)
- [ ] FPS kéo-phóng ≥ 55 với 10.000 nút (trung bình 3 lần) — **CHƯA ĐO** (cần Chrome + GPU thật, trang `/proto`)
- [ ] Hover trúng đúng nút 10/10 — **CHƯA ĐO** (cần máy thật)
- Kết luận M1: **CHƯA KẾT LUẬN** (nếu không đạt: xem quy tắc dự phòng trong báo cáo M1, có thể chuyển AntV G6)

## M2 — Xem cây, đăng nhập, phân quyền
- [x] Mọi ô ma trận phân quyền có test API — ĐÃ CHẠY trong bộ test BE (`PermissionMatrixTest`, 4 test, gồm test chặn endpoint thiếu trong ma trận/OpenAPI). Lệnh: `cd backend && ./gradlew test --tests '*PermissionMatrixTest'`. Ghi lại số test thực tế khi nghiệm thu: ____
- [ ] Toàn bộ test BE + FE xanh trên commit phát hành — **CHƯA KIỂM LẠI** trên commit cuối (ghi nhận lần chạy gần nhất của Task 1: 234 test BE; cần chạy lại `./gradlew build` và `npm test` rồi điền kết quả: ____)
- [ ] BE graph 2.000 người < 800 ms, gzip < 120 KB — đã đo MockMvc cục bộ: 40 ms / 37 KB gz (xem trên); **CHƯA ĐO trên máy chủ thật qua Caddy** (10.000 người: 195 ms / 179 KB gz, chỉ tham khảo)
- [ ] Mở cây < 2 giây (lần đầu) trên máy chủ thật — **CHƯA ĐO** (`BASE_URL=https://… E2E_USER=… E2E_PASS=… npm run measure`; coldTreeMs = ____). Cục bộ 1090 ms chỉ là cây thu gọn, software GL.
- [ ] Popup < 300 ms trên máy chủ thật — **CHƯA ĐO** (popupMs = ____; cục bộ 278 ms, sát ngưỡng)

## M3 — Chỉnh sửa, nhật ký
- [x] Test BE cho chỉnh sửa, nhật ký, revert nằm trong bộ test BE (E6, E7) — ĐÃ CHẠY khi hoàn thành từng plan; chưa xác nhận lại trên commit phát hành (xem mục "Toàn bộ test" ở M2)
- [ ] E6 kiểm thủ công trên trình duyệt (thêm người, gắn quan hệ, sửa, đổi ảnh; cấp thấp không thấy ngày sinh đầy đủ và không có nút sửa) — **CHƯA KIỂM**
- [ ] Đổi/hiển thị ảnh qua MinIO thật (pin ảnh, smoke test upload + xem) — **CHƯA KIỂM** (test dùng `InMemoryPhotoStorage`; MinIO chưa ghim phiên bản)

## M4 — Phát hành
| # | Điều kiện | Cách kiểm | Kết quả | Trạng thái |
| --- | --- | --- | --- | --- |
| 1 | Mọi ô ma trận có test API | `./gradlew test --tests '*PermissionMatrixTest'` | xem M2 | ĐÃ CHẠY (số test cuối: ____) |
| 2 | Toàn bộ test BE + FE xanh | `./gradlew build` ; `npm test` | ____ | CHƯA KIỂM lại |
| 3 | Nhập đủ 2.000 người từ Excel | `/admin/import` với tệp 2.000 người: kiểm tra thử sạch lỗi; nhập thật | ~2,4 ms/người cục bộ; ____ người, ____ quan hệ, ____ s | CHƯA ĐO qua UI/máy chủ thật |
| 4 | Mở cây < 2 giây (lần đầu) | `npm run measure` với `BASE_URL` thật | coldTreeMs = ____ | CHƯA ĐO |
| 5 | Popup < 300 ms | cùng lệnh | popupMs = ____ (cục bộ 278 ms) | CHƯA ĐO |
| 6 | BE graph 2.000 người < 800 ms, gzip < 120 KB | `./gradlew perfTest` | MockMvc cục bộ: 40 ms, 37 KB | ĐÃ CHẠY cục bộ; chưa đo máy chủ thật |
| 7 | HTTPS tự chuyển hướng, cookie an toàn | `curl -I http://$DOMAIN`, DevTools → Cookies | ____ | CHƯA KIỂM (chưa có tên miền/Let's Encrypt thật) |
| 8 | Có sao lưu hằng ngày | `ls /backups` sau 1 đêm; cron 02:30 UTC | ____ tệp | CHƯA KIỂM (chưa thấy cron chạy thật) |
| 9 | **Đã thử khôi phục** | `dc exec backup verify-restore.sh` | ____ | Script đã chạy trong stack cô lập khi phát triển (Task 6); **CHƯA chạy trên máy chủ thật** |
| 10 | Chạy được trên Chrome, Safari, điện thoại 375 px | Danh sách kiểm thủ công bên dưới | | CHƯA KIỂM |
| 11 | Sao lưu chép ra ngoài máy chủ | rsync/rclone (DEPLOY.md mục 7) | ____ | CHƯA CÓ |

## Danh sách kiểm thủ công (Chrome, Safari, 375 px) — chưa thực hiện
- [ ] Đăng nhập / đăng xuất; chưa đăng nhập vào `/tree` bị chuyển về `/login`
- [ ] Cây mở, kéo, phóng bằng chuột/cảm ứng (chụm hai ngón trên điện thoại)
- [ ] Thẻ hover (máy tính) và chạm mở popup (điện thoại)
- [ ] Popup ba tab (+ Lịch sử sửa với cấp trung/cao), cuộn được trên 375 px
- [ ] Thêm người, gắn quan hệ, sửa, đổi ảnh (cấp trung/cao)
- [ ] Nhập Excel: tải mẫu, kiểm tra thử, nhập thật
- [ ] Cấp thấp không thấy ngày sinh đầy đủ của người còn sống; không có nút sửa

## Việc cần làm trước khi go-live
1. Triển khai theo `docs/ops/DEPLOY.md` trên máy chủ thật với tên miền thật; xác nhận HTTPS/Let's Encrypt và chuyển hướng 80 → 443, cookie `Secure`/`HttpOnly`.
2. Ghim phiên bản `minio/minio` (tag RELEASE), thêm healthcheck, chạy smoke test đổi/xem ảnh.
3. Đo M1 (fps ≥ 55 với 10.000 nút, hover 10/10) bằng Chrome + GPU thật theo `docs/reports/M1-render-prototype.md`; nếu không đạt, báo chủ dự án (phương án AntV G6), không chỉnh ngưỡng.
4. Nhập tệp 2.000 người thật qua `/admin/import` và ghi số liệu; chạy `npm run measure` với `BASE_URL`, `E2E_USER`, `E2E_PASS` của máy chủ để lấy coldTreeMs và popupMs (popup cục bộ 278 ms đã sát 300 ms).
5. Chạy `./gradlew build` và `npm test` trên commit phát hành; điền số test.
6. Xác nhận cron sao lưu chạy lúc 02:30 UTC (`.last_success`, `dc ps` healthy), chạy `verify-restore.sh` ra `VERIFY OK`, và thử một lần DR khôi phục sang DB tên mới.
7. Thiết lập chép `backups` ra ngoài máy chủ (rsync/rclone) và kiểm bản chép.
8. Kiểm thủ công E6 và danh sách Chrome/Safari/375 px ở trên.
9. Đổi mật khẩu/khoá tài khoản bootstrap sau khi tạo admin thật.

Kết luận: CHƯA ĐẠT ĐỦ ĐIỀU KIỆN KẾT LUẬN (nhiều mục CHƯA ĐO).  Việc cần làm lại: xem danh sách trên.
