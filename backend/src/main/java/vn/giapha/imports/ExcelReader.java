package vn.giapha.imports;

import java.io.InputStream;
import java.time.ZoneId;
import java.util.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import vn.giapha.common.ApiException;

public final class ExcelReader {
    public record Row(int number, Map<String, String> cells) {}
    public record Sheet(String name, List<String> headers, List<Row> rows) {}

    public static final int MAX_ROWS = 20_000;

    public static final int MAX_SHEETS = 10;
    private static final int ROW_MARGIN = 50;

    static {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);
    }

    private ExcelReader() {}

    public static Map<String, Sheet> read(InputStream in) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            Map<String, Sheet> out = new LinkedHashMap<>();
            if (wb.getNumberOfSheets() > MAX_SHEETS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Tệp có quá " + MAX_SHEETS + " sheet");
            }
            int total = 0;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet s = wb.getSheetAt(i);
                org.apache.poi.ss.usermodel.Row head = s.getRow(s.getFirstRowNum());
                if (head == null) continue;
                if (s.getLastRowNum() > MAX_ROWS + ROW_MARGIN) {
                    throw tooMany(s.getSheetName());
                }
                List<String> headers = new ArrayList<>();
                for (int c = 0; c < head.getLastCellNum(); c++) headers.add(Norm.key(fmt.formatCellValue(head.getCell(c))));
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
                    if (++total > MAX_ROWS) throw tooMany(s.getSheetName());
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

    private static ApiException tooMany(String sheet) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
            "Tệp quá lớn (sheet \"" + sheet + "\"): tối đa " + MAX_ROWS + " dòng dữ liệu cho toàn bộ tệp");
    }

    private static String value(Cell cell, DataFormatter fmt, FormulaEvaluator ev) {
        if (cell == null) return "";
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                try {
                    type = ev.evaluateFormulaCell(cell);
                } catch (Exception e) {
                    return cachedValue(cell, fmt);
                }
                if (type == CellType.ERROR) return "";
            }
            if (type == CellType.ERROR) return "";
            if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            return fmt.formatCellValue(cell, ev).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String cachedValue(Cell cell, DataFormatter fmt) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> fmt.formatCellValue(cell).trim();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
