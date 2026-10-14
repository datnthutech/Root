# Gia Phả Web — Chỉ mục kế hoạch triển khai

Spec: `../specs/2026-10-14-gia-pha-design.md` (đọc trước mọi plan).
Tài liệu gốc: `../../../../../plan-and-doc/Thiết kế hệ thống gia phả web.md`.

> **Cho người/model thực hiện:** làm **đúng thứ tự task** trong từng plan, mỗi step một hành động, chạy lệnh test như ghi, commit cuối mỗi task. Không tự đổi tên hàm/kiểu đã ghi ở khối **Interfaces**. Nếu một lệnh không chạy được như mô tả, DỪNG và báo lại, đừng đoán.

| Plan | File | Phụ thuộc | Mốc | Gợi ý model |
| --- | --- | --- | --- | --- |
| E0 Khởi tạo | `2026-10-14-E0-bootstrap.md` | — | M1 | thấp |
| E1 Bản thử vẽ | `2026-10-14-E1-render-prototype.md` | E0 | M1 (cổng) | trung (thuật toán bố cục) |
| E2 DB + nghiệp vụ lõi | `2026-10-14-E2-domain-core.md` | E0 | M2 | trung |
| E3 Xác thực + phân quyền | `2026-10-14-E3-auth.md` | E2 | M2 | trung |
| E4 API xem cây | `2026-10-14-E4-tree-api.md` | E2, E3 | M2 | thấp–trung |
| E5 Giao diện xem cây | `2026-10-14-E5-tree-ui.md` | E1, E4 | M2 | trung |
| E6 Chỉnh sửa | `2026-10-14-E6-editing.md` | E4, E5 | M3 | trung |
| E7 Nhật ký + khôi phục | `2026-10-14-E7-audit.md` | E6 | M3 | thấp–trung |
| E8 Nhập Excel | `2026-10-14-E8-excel-import.md` | E2, E6 | M4 | thấp |
| E9 Kiểm thử + triển khai | `2026-10-14-E9-release.md` | tất cả | M4 | thấp |

**Song song được:** BE (E2→E3→E4→E6→E7→E8) và FE (E1→E5) chạy cùng lúc sau E0. M1 là cổng: nếu E1 không đạt ≥ 55 fps với 10.000 nút thì dừng, chuyển AntV G6 (xem E1, Task 7).

**Quy ước chung cho mọi plan**
- Lệnh BE chạy ở `src/Root/backend` bằng `./gradlew` (Gradle wrapper do E0 tạo; không cần cài Gradle); lệnh FE chạy ở `src/Root/frontend`; git ở `src/Root`.
- Commit message dạng `feat(E2): ...`, `test(E3): ...`, `chore(E0): ...`.
- Không sửa file migration đã commit; thay đổi DB = thêm `V{n}__*.sql` mới.
- Test BE có DB dùng `@Import(PostgresTestConfig.class)` (tạo ở E0 Task 2).
