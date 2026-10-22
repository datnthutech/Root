package vn.giapha.imports;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class TemplateBuilder {
    private TemplateBuilder() {}

    public static byte[] build() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            guide(wb);
            sheet(wb, bold, "Người",
                List.of("Mã", "Họ và tên", "Tên thường gọi", "Giới tính", "Ngày sinh", "Ngày sinh âm lịch", "Còn sống", "Ngày mất",
                    "Ngày giỗ âm lịch", "Nguyên quán", "Quê quán", "Nơi an táng", "Sơ yếu lý lịch"),
                List.of(List.of("P001", "Nguyễn Văn Tổ", "", "Nam", "1900", "", "Không", "1970", "12/3 âm lịch", "Bến Tre", "Bến Tre", "Nghĩa trang họ", "Thủy tổ"),
                        List.of("P002", "Trần Thị Bà", "", "Nữ", "1905", "", "Không", "1980", "", "", "", "", "")));
            sheet(wb, bold, "Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Loại", "Mã hôn phối"),
                List.of(List.of("P001", "P003", "Ruột", "H001"), List.of("P002", "P003", "Ruột", "H001")));
            sheet(wb, bold, "Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B", "Thứ", "Tình trạng", "Ngày bắt đầu", "Ngày kết thúc"),
                List.of(List.of("H001", "P001", "P002", "1", "Đang kết hôn", "1925", "")));
            sheet(wb, bold, "Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ", "Đếm thứ", "Gọi Út"),
                List.of(List.of("Họ Nguyễn", "P001", "Theo hôn phối", "Có")));
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void guide(Workbook wb) {
        Sheet s = wb.createSheet("Hướng dẫn");
        String[] lines = {
            "Điền dữ liệu vào các sheet: Người (bắt buộc), Quan hệ, Hôn phối, Dòng họ (tuỳ chọn). Xoá các dòng ví dụ trước khi nhập.",
            "Cột \"Mã\" là mã tự đặt, duy nhất trong tệp (vd P001). Dùng #123 để trỏ tới người đã có trong hệ thống (ID 123).",
            "Ngày: 1950 (chỉ năm), 03/1950 (tháng/năm), 05/03/1950 (ngày/tháng/năm). Không rõ thì để trống.",
            "Giới tính: Nam, Nữ hoặc Không rõ. Còn sống: Có hoặc Không (trống = còn sống nếu không có Ngày mất).",
            "Quan hệ — Loại: Ruột (mặc định), Con nuôi, Con riêng. Mã hôn phối là tuỳ chọn, cho biết con của cặp nào.",
            "Hôn phối — Tình trạng: Đang kết hôn (mặc định), Đã ly hôn, Góa. Thứ: vợ cả = 1, vợ thứ = 2...",
            "Dòng họ — Đếm thứ: Theo hôn phối (mặc định) hoặc Liền theo cha. Gọi Út: Có/Không.",
            "Bấm \"Kiểm tra thử\" trên trang nhập để xem lỗi từng dòng trước khi nhập thật. Nhập thật chỉ thực hiện khi không còn lỗi.",
        };
        for (int i = 0; i < lines.length; i++) s.createRow(i).createCell(0).setCellValue(lines[i]);
        s.setColumnWidth(0, 120 * 256);
    }

    private static void sheet(Workbook wb, CellStyle bold, String name, List<String> headers, List<List<String>> rows) {
        Sheet s = wb.createSheet(name);
        Row h = s.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = h.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(bold);
            s.setColumnWidth(i, 18 * 256);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = s.createRow(r + 1);
            for (int c = 0; c < rows.get(r).size(); c++) row.createCell(c).setCellValue(rows.get(r).get(c));
        }
    }
}
