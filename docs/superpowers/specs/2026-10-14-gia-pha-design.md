# Gia Phả Web — Spec thiết kế

Nguồn gốc: `plan-and-doc/Thiết kế hệ thống gia phả web.md` (tài liệu thiết kế đã chốt). Spec này là bản kỹ thuật hoá để chia việc cho người/model thực hiện. Khi hai tài liệu mâu thuẫn, **spec này thắng**; các điểm bổ sung so với tài liệu gốc được đánh dấu `[BỔ SUNG]`.

## 1. Hiểu biết đã thống nhất

**Mục tiêu:** web hiển thị gia phả nhiều dòng họ (nội + ngoại) dạng cây dọc, kéo/phóng như bản đồ, mượt 60 fps với ~2.000 người (kiểm thử 10.000). Bắt buộc đăng nhập. 3 vai trò: ADMIN, EDITOR, VIEWER.

**Người dùng:** thành viên các dòng họ (xem), người được tin cậy (thêm/chỉnh), quản trị viên.

**Thành công khi:** đạt 4 mốc M1–M4 trong tài liệu gốc (mục "Mốc và điều kiện nghiệm thu").

**Giả định `[BỔ SUNG]`** (đổi được, không ảnh hưởng kiến trúc):
- Build BE bằng **Gradle 8.10 (Groovy DSL, dùng `./gradlew`)**, Java 21, Spring Boot 3.3.x; test tích hợp dùng **Testcontainers PostgreSQL**.
- FE dùng **Ant Design 5**, **Vitest** + Testing Library, **Playwright** không bắt buộc ở bản đầu.
- Monorepo: `backend/`, `frontend/`, `docker-compose.yml` ở gốc `src/Root`.
- Package Java gốc: `vn.giapha`. Ngôn ngữ giao diện: tiếng Việt; code, tên bảng, tên API: tiếng Anh.
- Giờ lưu DB: UTC (`timestamptz`); ngày sinh/mất là `date` + `precision`.

## 2. Kiến trúc

```
Browser (React+TS+Vite)
  ├─ UI: Ant Design, TanStack Query, react-router
  ├─ Canvas: PixiJS + pixi-viewport  (vẽ, culling, LOD, hit-test quadtree)
  └─ Web Worker: layout cây dọc  → IndexedDB cache theo ETag
        │ HTTPS  /api/v1  (JWT 15' + refresh cookie httpOnly)
Spring Boot 3 (REST, Security, JPA, Flyway, springdoc-openapi)
        │
PostgreSQL 16 (+ unaccent, pg_trgm)      MinIO (S3) — ảnh gốc + thumb 96/256 px
```

Dữ liệu là **đồ thị**: `person`, `union_rel`, `parent_child`. Cây là một lát nhìn từ gốc. Anh chị em, thứ tự, danh xưng luôn **tính ở BE**, FE chỉ hiển thị.

## 3. Cấu trúc repo `src/Root`

```
backend/                 Gradle project (vn.giapha)
  src/main/java/vn/giapha/{common,auth,person,relation,clan,tree,audit,imports,storage}
  src/main/resources/{application.yml,db/migration/V*.sql}
  src/test/java/...
frontend/                Vite React TS
  src/{api,auth,components,features/{tree,person,admin,import},lib,workers}
docker-compose.yml       postgres + minio
docs/superpowers/{specs,plans}
```

## 4. Hợp đồng API dùng chung (BE ⇄ FE)

Tiền tố `/api/v1`. Lỗi luôn là `{ "code": string, "message": string, "details": object|null }` với HTTP status tương ứng. Mã lỗi: `VALIDATION_FAILED`(400) `UNAUTHENTICATED`(401) `FORBIDDEN`(403) `NOT_FOUND`(404) `VERSION_CONFLICT`(409) `RELATION_CYCLE`(409) `RELATION_INVALID`(409) `ACCOUNT_PENDING`(403) `ACCOUNT_LOCKED`(423) `BAD_CREDENTIALS`(401) `EDIT_WINDOW_EXPIRED`(403).

**Graph (`GET /clans/{id}/graph?side=both|paternal|maternal`)** — header `ETag`, hỗ trợ `If-None-Match` → 304, gzip:

```json
{
  "clanId": 1, "rootPersonId": 10, "siblingCountMode": "PER_UNION",
  "persons": [
    {"id":10,"name":"Nguyễn Văn A","gender":"M","birthYear":1900,"deathYear":1970,
     "alive":false,"thumb":"/persons/10/photo?size=96&v=<photoKey>","birthOrder":null,"orderLabel":null,
     "generation":1,"membership":"BLOOD"}
  ],
  "unions": [{"id":5,"partnerAId":10,"partnerBId":11,"order":1,"status":"MARRIED"}],
  "links":  [{"parentId":10,"childId":12,"unionId":5,"kind":"BIOLOGICAL"}]
}
```
`birthOrder` = số thứ (2 = Hai…) hoặc null nếu là con một cặp chưa có anh em/không tính; `orderLabel` = "anh Hai"/"chị Út"… hoặc null.

**Quyền riêng tư:** với VIEWER, người `alive=true` chỉ trả `birthYear` (không trả ngày đủ ở `/persons/{id}`).

## 5. Quy tắc nghiệp vụ lõi

Giữ nguyên tài liệu gốc cho: ràng buộc quan hệ (1 cha ruột + 1 mẹ ruột, không vòng lặp, cảnh báo ngày tháng, không tự kết hôn, version chống sửa đè), thứ tự anh em kiểu miền Nam, ma trận phân quyền, danh sách API, hiệu năng.

`[BỔ SUNG]` chi tiết chốt:
- **Cảnh báo ngày tháng** không chặn: API trả `warnings: [{code,message}]` trong body 200/201.
- **Thứ tự**: nhóm anh em = cùng `union_id` (mặc định `PER_UNION`) hoặc cùng cha (`PER_FATHER`), lưu ở `clan.sibling_count_mode`. `birth_order_override` là **số thứ** gán trực tiếp (≥ 2, ví dụ 3 = "Ba"); người không có override lấp các số còn trống theo `birth_date` tăng dần (ngày không rõ xếp cuối, hoà thì theo id và đánh dấu `needsConfirm`), bắt đầu từ 2. Con có `counts_in_order=false` (nuôi/riêng) không có số thứ. Con cuối: nếu `clan.use_ut_label` và nhóm ≥ 2 người thì nhãn "Út (thứ N)".
- **Đời (generation)**: gốc = 1; con = cha/mẹ trong dòng + 1; vợ/chồng ngoài dòng (`IN_LAW`) mang đời của người phối ngẫu. Tính lại cho cả dòng họ sau mỗi thay đổi quan hệ (đủ nhanh cho ≤ 10.000).
- **Xoá mềm** người: ẩn khỏi graph; quan hệ liên quan bị bỏ qua khi tính.
- **Cấp trung sửa hồ sơ**: chỉ người `created_by = mình` và `now - created_at ≤ 24h`, ngược lại `EDIT_WINDOW_EXPIRED`.
- Bản đầu: `PUT /persons/{id}` và `PUT /persons/{id}/photo` theo bảng API là ADMIN; EDITOR sửa người mình tạo trong 24h theo ma trận → áp dụng **ma trận phân quyền** (nguồn chuẩn), bảng API gốc ghi "ADMIN" là tối thiểu cho trường hợp tổng quát.

## 6. Bố cục cây (front-end)

Thuật toán "tidy tree theo đơn vị gia đình" (chi tiết và mã ở plan E1):
- **Đơn vị (unit)** = một người + các vợ/chồng của họ theo `union.order`, xếp ngang cạnh nhau.
- Con của đơn vị = mọi con chưa được đặt của mọi thành viên, sắp theo thứ tự anh em.
- Chiều rộng cây con tính từ dưới lên; đơn vị căn giữa trên dải con; y = (đời − đời gốc) × `ROW_H`.
- Người đã đặt rồi (hôn nhân trong họ, hai dòng gặp nhau) chỉ vẽ **một lần**; các nhánh còn lại dùng đường nối.
- Vợ/chồng ngoài dòng chỉ vẽ là lá cạnh phối ngẫu, không bung tổ tiên của họ.

`[BỔ SUNG]` Ảnh thu nhỏ: tải bằng `fetch` kèm Bearer (endpoint ảnh cần đăng nhập), LRU tối đa 400 ảnh, 6 lượt tải song song; **không** gom texture atlas ở bản đầu (PixiJS tự gộp lô vẽ) — chỉ làm atlas nếu đo M1/M4 không đạt.

## 7. Phạm vi bản đầu

Làm: E0–E9 (chế độ 1, 2, 3; popup 3 tab + tab lịch sử; chỉnh sửa; audit; nhập Excel). Không làm: chế độ 4–6, GEDCOM, vai trò theo dòng họ, số điện thoại/địa chỉ hiện tại.

## 8. Định nghĩa hoàn thành

Theo tài liệu gốc (5 điều). Bổ sung `[BỔ SUNG]`: mọi plan task kết thúc bằng một commit nhỏ; lệnh test trong từng step phải chạy được ở thư mục ghi rõ.
