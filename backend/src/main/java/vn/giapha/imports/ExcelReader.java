package vn.giapha.imports;

import java.io.InputStream;
import java.time.ZoneId;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import vn.giapha.common.ApiException;

public final class ExcelReader {
    public record Row(int number, Map<String, String> cells) {}
    public record Sheet(String name, List<String> headers, List<Row> rows) {}

    public static final int MAX_ROWS = 20_000;

    private ExcelReader() {}

    public static Map<String, Sheet> read(InputStream in) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            Map<String, Sheet> out = new LinkedHashMap<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet s = wb.getSheetAt(i);
                org.apache.poi.ss.usermodel.Row head = s.getRow(s.getFirstRowNum());
                if (head == null) continue;
                List<String> headers = new ArrayList<>();
                for (int c = 0; c < head.getLastCellNum(); c++) headers.add(Norm.key(fmt.formatCellValue(head.getCell(c), ev)));
                List<Row> rows = new ArrayList<>();
                for (int r = s.getFirstRowNum() + 1; r <= s.getLastRowNum(); r++) {
                    org.apache.poi.ss.usermodel.Row row = s.getRow(r);
                    if (row == null) continue;
                    Map<String, String> cells = new LinkedHashMap<>();
                    boolean any = false;
                    for (int c = 0; c < headers.size(); c++) {
                        if (headers.get(c).isEmpty()) continue;
                        String v = value(row.getCell(c), fmt, ev);
                        if (!v.isEmpty()) any = true;
                        cells.put(headers.get(c), v);
                    }
                    if (!any) continue;
                    if (rows.size() >= MAX_ROWS) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Sheet \"" + s.getSheetName() + "\" quá " + MAX_ROWS + " dòng");
                    }
                    rows.add(new Row(r + 1, cells));
                }
                out.put(Norm.key(s.getSheetName()), new Sheet(Norm.key(s.getSheetName()), headers.stream().filter(h -> !h.isEmpty()).toList(), rows));
            }
            return out;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Không đọc được tệp Excel (.xlsx)");
        }
    }

    private static String value(Cell cell, DataFormatter fmt, FormulaEvaluator ev) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA ? ev.evaluateFormulaCell(cell) : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
        }
        return fmt.formatCellValue(cell, ev).trim();
    }
}
