package vn.giapha.support;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class XlsxBuilder {
    private final XSSFWorkbook wb = new XSSFWorkbook();

    public XlsxBuilder sheet(String name, List<String> headers, List<List<Object>> rows) {
        Sheet s = wb.createSheet(name);
        Row h = s.createRow(0);
        for (int i = 0; i < headers.size(); i++) h.createCell(i).setCellValue(headers.get(i));
        CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
        for (int r = 0; r < rows.size(); r++) {
            Row row = s.createRow(r + 1);
            List<Object> vals = rows.get(r);
            for (int c = 0; c < vals.size(); c++) {
                Object v = vals.get(c);
                if (v == null) continue;
                Cell cell = row.createCell(c);
                if (v instanceof Integer i) cell.setCellValue(i);
                else if (v instanceof LocalDate d) { cell.setCellValue(java.sql.Date.valueOf(d)); cell.setCellStyle(dateStyle); }
                else if (v instanceof String str && str.startsWith("=")) cell.setCellFormula(str.substring(1));
                else cell.setCellValue(String.valueOf(v));
            }
        }
        return this;
    }

    public byte[] bytes() {
        try (var out = new ByteArrayOutputStream()) { wb.write(out); wb.close(); return out.toByteArray(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
