# Báo cáo M1 — bản thử vẽ

Máy: Chưa đo trên laptop thật, Chrome: Chưa đo trên laptop thật

| Phép đo | Kết quả | Ngưỡng | Đạt? |
| --- | --- | --- | --- |
| Bố cục 10.000 nút (ms) | 31 ms (2.000 người: 25 ms). Đo bằng headless Chromium, software GL (swiftshader), bản dev build | < 1000 | Đạt |
| FPS trung bình kéo-phóng (3 lần) | CHƯA ĐO — cần người đo thủ công trên laptop thật (Chrome, GPU thật) | ≥ 55 | |
| Hover trúng đúng nút (10/10) | CHƯA ĐO — cần người đo thủ công trên laptop thật (Chrome, GPU thật) | 10/10 | |

Ghi chú về bố cục 10.000 nút: phép đo bố cục chỉ dùng CPU nên số liệu hợp lệ. Số liệu này không phải là kết quả render thật.

## Số liệu tham khảo (KHÔNG đại diện)

Trong headless Chromium chỉ có software WebGL (swiftshader), nên các số dưới đây chỉ để tham khảo, không dùng để đánh giá đạt/không đạt:

- FPS khi kéo-phóng: 15 fps với 2.000 người, 6 fps với 10.000 người.

## Quan sát từ Task 6

- Cây 10.000 nút khi vừa khung nhìn (fit) có dạng một dải rất rộng và rất thấp. Logic fit/tỷ lệ khung có thể cần chỉnh ở E5.

## Cách đo

1. Chạy `./deploy.sh` ở thư mục gốc (toàn bộ app chạy trong Docker).
2. Mở Chrome trên laptop thật và truy cập `http://localhost:7070/proto`.
3. Chọn 10.000 người. Ghi lại số "Bố cục: ... ms" hiển thị trên trang.
4. Bấm "Đo fps 6 giây" 3 lần. Ghi lại fps trung bình và fps thấp nhất của mỗi lần.
5. Phóng to đến mức zoom ≥ 0.8. Rê chuột vào 10 nút bất kỳ, xác nhận tooltip hiện đúng nút và không hiện khi rê vào vùng trống.
6. Điền kết quả vào bảng trên, ghi rõ cấu hình máy (CPU/GPU/RAM) và phiên bản Chrome.

## Kết luận

CHƯA KẾT LUẬN (M1 chờ đo fps/hover thủ công)

Quy tắc dự phòng: nếu KHÔNG ĐẠT, thử tối ưu tối đa 1 ngày (giảm `antialias`, tăng ngưỡng LOD dot lên 0.4). Nếu vẫn không đạt, dừng và báo chủ dự án để chuyển sang AntV G6.
