# E8 Nhập liệu Excel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quản trị viên tải mẫu Excel, điền khoảng 2.000 người + hôn phối + quan hệ + dòng họ, bấm **Kiểm tra thử** (không lưu, báo lỗi từng dòng) rồi **Nhập thật**. Nhập là "tất cả hoặc không gì": có lỗi thì không ghi gì.

**Architecture:** `ExcelReader` (POI) đọc sheet theo tên/cột đã chuẩn hoá (bỏ dấu, thường hoá) → `ImportParser` (thuần) kiểm tra ô, khoá trùng, tham chiếu → nếu sạch, `ExcelImportService` chạy **thật** trong một giao dịch bằng các service E2/E6 (nên kiểm vòng lặp, cha/mẹ ruột…), rồi **rollback** nếu là chạy thử hoặc có lỗi. Báo cáo `ImportReport` dùng chung cho cả hai chế độ. FE: trang `/admin/import`.

**Tech Stack:** Apache POI 5.3 (đã có trong `build.gradle` từ E0), Spring `TransactionTemplate`, React + Ant Design Upload.

**Spec:** `docs/superpowers/specs/2026-10-14-gia-pha-design.md`; tài liệu gốc mục "E8 Nhập liệu Excel" và "Nguồn 2.000 người ban đầu".

## Global Constraints

- Chỉ ADMIN dùng `/api/v1/import/**` `[BỔ SUNG]` (ma trận quyền gốc không nêu; nhập hàng loạt là thao tác cấp cao).
- Tệp `.xlsx` ≤ 10 MB; mỗi sheet ≤ 20.000 dòng dữ liệu; dòng trống bị bỏ qua; số dòng trong báo cáo là **số dòng Excel** (dòng tiêu đề = 1, dữ liệu bắt đầu từ 2).
- Sheet và cột được nhận diện sau khi chuẩn hoá: bỏ dấu tiếng Việt (`đ→d`), chữ thường, chỉ giữ `a-z0-9` (ví dụ `Họ và tên` ≡ `hovaten` ≡ `HỌ VÀ TÊN`).
- Sheet `Người` bắt buộc; `Quan hệ`, `Hôn phối`, `Dòng họ` tuỳ chọn.
- Khoá dòng (cột `Mã`) là chuỗi tự đặt, duy nhất trong tệp; tham chiếu người đã có trong hệ thống bằng `#<id>` (ví dụ `#123`).
- Ngày chấp nhận: `yyyy` (YEAR), `MM/yyyy` hoặc `M/yyyy` (MONTH), `dd/MM/yyyy` hoặc `d/M/yyyy` (DAY), `yyyy-MM-dd` (DAY), và ô kiểu ngày của Excel (DAY).
- Giới tính: `Nam`/`M` → M; `Nữ`/`Nu`/`F` → F; trống hoặc `Không rõ` → U. Còn sống: `Có`/`Co`/`1`/`true`/`x` → true; `Không`/`Khong`/`0`/`false` → false; để trống → còn sống nếu không có `Ngày mất`.
- Loại quan hệ: `Ruột` (mặc định) → BIOLOGICAL, `Con nuôi` → ADOPTED, `Con riêng` → STEP. Tình trạng hôn phối: `Đang kết hôn` (mặc định) → MARRIED, `Đã ly hôn` → DIVORCED, `Góa` → WIDOWED. Đếm thứ: `Theo hôn phối` (mặc định) → PER_UNION, `Liền theo cha` → PER_FATHER.
- Mã lỗi nhập: `IMPORT_INVALID` (422) khi nhập thật mà có lỗi; chạy thử luôn trả 200 kèm báo cáo.
- Không ghi `audit_log` từng người; ghi **một** dòng `entity_type='IMPORT'` `[BỔ SUNG]` (không khôi phục được).

## Review Focus

- Tệp có lỗi ở dòng 500: **không** người nào được ghi (kiểm đếm bảng `person` trước/sau).
- Chạy thử (dryRun) trên tệp sạch: báo cáo đúng số lượng nhưng DB không đổi, kể cả sau khi dựng `clan_member`.
- Hai hàng cùng `Mã`, tham chiếu `Mã` không tồn tại, ngày `31/02/1950`, giới tính `Robot`, người có ngày mất mà ghi còn sống: mỗi lỗi chỉ đúng **dòng và cột** gây lỗi.
- Quan hệ tạo vòng lặp (P1→P3→P4→P1) và con có hai cha ruột: báo ở dòng của quan hệ gây lỗi, không phải lỗi 500.
- Ô năm lưu dạng **số** (1950) và ô ngày dạng **ngày Excel** đều đọc đúng; ô công thức không làm hỏng việc đọc.
- Tệp không phải xlsx (đổi đuôi `.txt` → `.xlsx`) → 400 `VALIDATION_FAILED`, không 500.

---

### Task 1: Chuẩn hoá tên, đọc ngày, đọc giá trị ô (thuần)

**Files:**
- Create (package `vn.giapha.imports`): `Norm.java`, `DateParser.java`, `Cells.java`
- Test: `backend/src/test/java/vn/giapha/imports/NormTest.java`, `DateParserTest.java`, `CellsTest.java`

**Interfaces:**
- Produces:
```java
public final class Norm { public static String key(String s); }                                   // "Họ và tên" -> "hovaten"
public final class DateParser {
    public record Parsed(LocalDate date, DatePrecision precision) {}
    public static Parsed parse(String s);            // null nếu trống; ném IllegalArgumentException nếu sai
}
public final class Cells {
    public static Gender gender(String s);           // ném IllegalArgumentException nếu lạ
    public static Boolean bool(String s);            // null nếu trống; ném IllegalArgumentException nếu lạ
    public static ParentKind kind(String s);
    public static UnionStatus unionStatus(String s);
    public static SiblingCountMode countMode(String s);
}
```

- [ ] **Step 1: Viết test (failing)**

`NormTest.java`:
```java
package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormTest {
    @Test void stripsDiacriticsSpacesAndCase() {
        assertThat(Norm.key("Họ và tên")).isEqualTo("hovaten");
        assertThat(Norm.key("  NGÀY SINH (âm lịch) ")).isEqualTo("ngaysinhamlich");
        assertThat(Norm.key("Đếm thứ")).isEqualTo("demthu");
        assertThat(Norm.key("Mã cha/mẹ")).isEqualTo("machame");
        assertThat(Norm.key(null)).isEmpty();
    }
}
```
`DateParserTest.java`:
```java
package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.giapha.person.DatePrecision;

class DateParserTest {
    @Test void forms() {
        assertThat(DateParser.parse("1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 1, 1), DatePrecision.YEAR));
        assertThat(DateParser.parse("3/1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 1), DatePrecision.MONTH));
        assertThat(DateParser.parse("03/1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 1), DatePrecision.MONTH));
        assertThat(DateParser.parse("5/3/1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 5), DatePrecision.DAY));
        assertThat(DateParser.parse("05/03/1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 5), DatePrecision.DAY));
        assertThat(DateParser.parse("1950-03-05")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 5), DatePrecision.DAY));
        assertThat(DateParser.parse(" 1950 ")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 1, 1), DatePrecision.YEAR));
    }
    @Test void blankIsNull() {
        assertThat(DateParser.parse("")).isNull();
        assertThat(DateParser.parse("   ")).isNull();
        assertThat(DateParser.parse(null)).isNull();
    }
    @Test void invalidThrows() {
        for (String bad : new String[]{"31/02/1950", "13/1950", "abc", "1950/03/05", "0/0/0", "99", "3000", "12345"}) {
            assertThatThrownBy(() -> DateParser.parse(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
```
`CellsTest.java`:
```java
package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.giapha.clan.SiblingCountMode;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.relation.UnionStatus;

class CellsTest {
    @Test void gender() {
        assertThat(Cells.gender("Nam")).isEqualTo(Gender.M);
        assertThat(Cells.gender(" m ")).isEqualTo(Gender.M);
        assertThat(Cells.gender("Nữ")).isEqualTo(Gender.F);
        assertThat(Cells.gender("nu")).isEqualTo(Gender.F);
        assertThat(Cells.gender("")).isEqualTo(Gender.U);
        assertThat(Cells.gender("Không rõ")).isEqualTo(Gender.U);
        assertThatThrownBy(() -> Cells.gender("Robot")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bool() {
        assertThat(Cells.bool("Có")).isTrue();
        assertThat(Cells.bool("co")).isTrue();
        assertThat(Cells.bool("x")).isTrue();
        assertThat(Cells.bool("Không")).isFalse();
        assertThat(Cells.bool("0")).isFalse();
        assertThat(Cells.bool("")).isNull();
        assertThatThrownBy(() -> Cells.bool("có lẽ")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void enums() {
        assertThat(Cells.kind("")).isEqualTo(ParentKind.BIOLOGICAL);
        assertThat(Cells.kind("Ruột")).isEqualTo(ParentKind.BIOLOGICAL);
        assertThat(Cells.kind("Con nuôi")).isEqualTo(ParentKind.ADOPTED);
        assertThat(Cells.kind("Con riêng")).isEqualTo(ParentKind.STEP);
        assertThat(Cells.unionStatus("")).isEqualTo(UnionStatus.MARRIED);
        assertThat(Cells.unionStatus("Đã ly hôn")).isEqualTo(UnionStatus.DIVORCED);
        assertThat(Cells.unionStatus("Góa")).isEqualTo(UnionStatus.WIDOWED);
        assertThat(Cells.countMode("")).isEqualTo(SiblingCountMode.PER_UNION);
        assertThat(Cells.countMode("Liền theo cha")).isEqualTo(SiblingCountMode.PER_FATHER);
        assertThatThrownBy(() -> Cells.kind("Khác")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run (ở `backend`): `./gradlew test --tests '*NormTest' --tests '*DateParserTest' --tests '*CellsTest'`
Expected: FAIL (thiếu lớp).

- [ ] **Step 3: Viết triển khai**

`Norm.java`:
```java
package vn.giapha.imports;

import java.text.Normalizer;
import java.util.Locale;

public final class Norm {
    private Norm() {}

    public static String key(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('đ', 'd');
        return t.replaceAll("[^a-z0-9]", "");
    }
}
```
`DateParser.java`:
```java
package vn.giapha.imports;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.giapha.person.DatePrecision;

public final class DateParser {
    public record Parsed(LocalDate date, DatePrecision precision) {}

    private static final Pattern YEAR = Pattern.compile("^(\\d{4})$");
    private static final Pattern MONTH = Pattern.compile("^(\\d{1,2})/(\\d{4})$");
    private static final Pattern DAY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$");
    private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");

    private DateParser() {}

    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            Matcher m;
            if ((m = YEAR.matcher(s)).matches()) return new Parsed(LocalDate.of(year(m.group(1)), 1, 1), DatePrecision.YEAR);
            if ((m = MONTH.matcher(s)).matches()) return new Parsed(LocalDate.of(year(m.group(2)), Integer.parseInt(m.group(1)), 1), DatePrecision.MONTH);
            if ((m = DAY.matcher(s)).matches()) return new Parsed(LocalDate.of(year(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))), DatePrecision.DAY);
            if ((m = ISO.matcher(s)).matches()) return new Parsed(LocalDate.of(year(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))), DatePrecision.DAY);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Ngày không hợp lệ: " + s);
        }
        throw new IllegalArgumentException("Ngày không đúng định dạng (yyyy, MM/yyyy hoặc dd/MM/yyyy): " + s);
    }

    private static int year(String y) {
        int v = Integer.parseInt(y);
        if (v < 1000 || v > 2100) throw new DateTimeException("year");
        return v;
    }
}
```
`Cells.java`:
```java
package vn.giapha.imports;

import java.util.Set;
import vn.giapha.clan.SiblingCountMode;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.relation.UnionStatus;

public final class Cells {
    private Cells() {}

    public static Gender gender(String s) {
        String k = Norm.key(s);
        if (k.isEmpty() || k.equals("khongro") || k.equals("u")) return Gender.U;
        if (k.equals("nam") || k.equals("m")) return Gender.M;
        if (k.equals("nu") || k.equals("f")) return Gender.F;
        throw new IllegalArgumentException("Giới tính phải là Nam, Nữ hoặc Không rõ: " + s);
    }

    public static Boolean bool(String s) {
        String k = Norm.key(s);
        if (k.isEmpty()) return null;
        if (Set.of("co", "1", "true", "x", "yes").contains(k)) return true;
        if (Set.of("khong", "0", "false", "no").contains(k)) return false;
        throw new IllegalArgumentException("Giá trị phải là Có hoặc Không: " + s);
    }

    public static ParentKind kind(String s) {
        String k = Norm.key(s);
        if (k.isEmpty() || k.equals("ruot")) return ParentKind.BIOLOGICAL;
        if (k.equals("connuoi")) return ParentKind.ADOPTED;
        if (k.equals("conrieng")) return ParentKind.STEP;
        throw new IllegalArgumentException("Loại phải là Ruột, Con nuôi hoặc Con riêng: " + s);
    }

    public static UnionStatus unionStatus(String s) {
        String k = Norm.key(s);
        if (k.isEmpty() || k.equals("dangkethon")) return UnionStatus.MARRIED;
        if (k.equals("dalyhon")) return UnionStatus.DIVORCED;
        if (k.equals("goa")) return UnionStatus.WIDOWED;
        throw new IllegalArgumentException("Tình trạng phải là Đang kết hôn, Đã ly hôn hoặc Góa: " + s);
    }

    public static SiblingCountMode countMode(String s) {
        String k = Norm.key(s);
        if (k.isEmpty() || k.equals("theohonphoi")) return SiblingCountMode.PER_UNION;
        if (k.equals("lientheocha")) return SiblingCountMode.PER_FATHER;
        throw new IllegalArgumentException("Cách đếm phải là Theo hôn phối hoặc Liền theo cha: " + s);
    }
}
```

- [ ] **Step 4: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*NormTest' --tests '*DateParserTest' --tests '*CellsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m "feat(E8): normalisation, date and cell value parsers"
```

---

### Task 2: Đọc workbook và mẫu Excel

**Files:**
- Create (package `vn.giapha.imports`): `ExcelReader.java`, `TemplateBuilder.java`
- Create (test): `backend/src/test/java/vn/giapha/support/XlsxBuilder.java`, `backend/src/test/java/vn/giapha/imports/ExcelReaderTest.java`, `TemplateBuilderTest.java`

**Interfaces:**
- Produces:
```java
public final class ExcelReader {
    public record Row(int number, Map<String, String> cells) {}                       // khoá = Norm.key(tiêu đề)
    public record Sheet(String name, List<String> headers, List<Row> rows) {}          // name = Norm.key(tên sheet)
    public static Map<String, Sheet> read(InputStream in);                             // khoá = Norm.key(tên sheet); ném ApiException 400 nếu không đọc được
    public static final int MAX_ROWS = 20_000;
}
public final class TemplateBuilder { public static byte[] build(); }
// Test helper
public final class XlsxBuilder { public XlsxBuilder sheet(String name, List<String> headers, List<List<Object>> rows); public byte[] bytes(); }
```
`Row.cells` chứa mọi cột có tiêu đề (giá trị rỗng là `""`). Ô ngày kiểu Excel được đọc thành `yyyy-MM-dd`; ô số nguyên (1950) đọc thành `"1950"`; ô công thức đọc theo giá trị tính được. Dòng mà mọi ô đều trống bị bỏ qua. Mẫu có sheet `Hướng dẫn`, `Người`, `Quan hệ`, `Hôn phối`, `Dòng họ` (đúng tiêu đề trong khối Global Constraints) và mỗi sheet dữ liệu có 1–2 dòng ví dụ.

- [ ] **Step 1: Viết helper và test (failing)**

`support/XlsxBuilder.java`:
```java
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
```
`ExcelReaderTest.java`:
```java
package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.giapha.common.ApiException;
import vn.giapha.support.XlsxBuilder;

class ExcelReaderTest {
    @Test
    void readsSheetsByNormalisedNamesAndHeaders() {
        byte[] x = new XlsxBuilder().sheet("Người", List.of("Mã", "Họ và tên", "Ngày sinh"), List.of(
            Arrays.asList("P1", "Nguyễn A", 1950),
            Arrays.asList("P2", "Nguyễn B", LocalDate.of(1952, 3, 4)),
            Arrays.asList(null, null, null),                 // dòng trống bị bỏ
            Arrays.asList("P3", "Nguyễn C", "05/03/1960"))).bytes();
        var sheets = ExcelReader.read(new ByteArrayInputStream(x));
        var s = sheets.get("nguoi");
        assertThat(s.rows()).hasSize(3);
        assertThat(s.rows().get(0).number()).isEqualTo(2);
        assertThat(s.rows().get(0).cells().get("ngaysinh")).isEqualTo("1950");
        assertThat(s.rows().get(1).cells().get("ngaysinh")).isEqualTo("1952-03-04");
        assertThat(s.rows().get(2).number()).isEqualTo(5);     // dòng Excel thật
        assertThat(s.rows().get(2).cells().get("ngaysinh")).isEqualTo("05/03/1960");
        assertThat(s.headers()).containsExactly("ma", "hovaten", "ngaysinh");
    }

    @Test
    void formulaCellsUseComputedValue() {
        byte[] x = new XlsxBuilder().sheet("Người", List.of("Mã", "Họ và tên"), List.of(Arrays.asList("P1", "=\"Nguyễn \"&\"A\""))).bytes();
        assertThat(ExcelReader.read(new ByteArrayInputStream(x)).get("nguoi").rows().get(0).cells().get("hovaten")).isEqualTo("Nguyễn A");
    }

    @Test
    void notAnExcelFileIs400() {
        assertThatThrownBy(() -> ExcelReader.read(new ByteArrayInputStream("hello".getBytes())))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void tooManyRowsIs400() {
        var rows = new java.util.ArrayList<List<Object>>();
        for (int i = 0; i < ExcelReader.MAX_ROWS + 1; i++) rows.add(Arrays.asList("P" + i, "N"));
        byte[] x = new XlsxBuilder().sheet("Người", List.of("Mã", "Họ và tên"), rows).bytes();
        assertThatThrownBy(() -> ExcelReader.read(new ByteArrayInputStream(x)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }
}
```
`TemplateBuilderTest.java`:
```java
package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class TemplateBuilderTest {
    @Test
    void templateHasAllSheetsAndHeadersAndIsReadableByReader() {
        var sheets = ExcelReader.read(new ByteArrayInputStream(TemplateBuilder.build()));
        assertThat(sheets).containsKeys("huongdan", "nguoi", "quanhe", "honphoi", "dongho");
        assertThat(sheets.get("nguoi").headers()).containsExactly("ma", "hovaten", "tenthuonggoi", "gioitinh", "ngaysinh", "ngaysinhamlich",
            "consong", "ngaymat", "ngaygioamlich", "nguyenquan", "quequan", "noiantang", "soyeulylich");
        assertThat(sheets.get("quanhe").headers()).containsExactly("machame", "macon", "loai", "mahonphoi");
        assertThat(sheets.get("honphoi").headers()).containsExactly("mahonphoi", "manguoia", "manguoib", "thu", "tinhtrang", "ngaybatdau", "ngayketthuc");
        assertThat(sheets.get("dongho").headers()).containsExactly("tendongho", "mathuyto", "demthu", "goiut");
        assertThat(sheets.get("nguoi").rows()).isNotEmpty();
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `./gradlew test --tests '*ExcelReaderTest' --tests '*TemplateBuilderTest'`
Expected: FAIL.

- [ ] **Step 3: Viết `ExcelReader.java`**

```java
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
```
Lưu ý đường chéo: `evaluateFormulaCell` trả về `CellType` của kết quả, và với công thức ngày cần `DateUtil.isCellDateFormatted(cell)` hoạt động trên ô công thức — chấp nhận.

- [ ] **Step 4: Viết `TemplateBuilder.java`**

```java
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
```
Lưu ý: ví dụ trong mẫu tham chiếu `P003` chưa có hàng → nếu người dùng nhập mẫu nguyên trạng, báo lỗi tham chiếu (đúng ý: họ phải xoá/điền). Test đọc mẫu chỉ kiểm sheet/tiêu đề.

- [ ] **Step 5: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*ExcelReaderTest' --tests '*TemplateBuilderTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add . && git commit -m "feat(E8): Excel reader and template builder"
```

---

### Task 3: ImportParser (kiểm tra ô, khoá trùng, tham chiếu)

**Files:**
- Create (package `vn.giapha.imports`): `ImportModel.java`, `ImportParser.java`
- Test: `backend/src/test/java/vn/giapha/imports/ImportParserTest.java`

**Interfaces:**
- Produces:
```java
public final class ImportModel {
    public record Error(String sheet, int row, String column, String code, String message) {}
    public record PersonRow(int row, String key, String fullName, String nickname, Gender gender, DateParser.Parsed birth, String birthLunar,
        boolean alive, DateParser.Parsed death, String deathLunar, String origin, String home, String burial, String biography) {}
    public record UnionRow(int row, String key, String a, String b, Integer order, UnionStatus status, LocalDate start, LocalDate end) {}
    public record LinkRow(int row, String parent, String child, ParentKind kind, String unionKey) {}
    public record ClanRow(int row, String name, String founderKey, SiblingCountMode mode, Boolean useUt) {}
    public record Parsed(List<PersonRow> persons, List<UnionRow> unions, List<LinkRow> links, ClanRow clan) {}
}
public final class ImportParser {
    public static ImportModel.Parsed parse(Map<String, ExcelReader.Sheet> sheets, List<ImportModel.Error> errors, List<ImportModel.Error> warnings);
}
```
Tên hiển thị trong lỗi: sheet `Người`/`Quan hệ`/`Hôn phối`/`Dòng họ`; cột là tiêu đề hiển thị (`Họ và tên`, `Mã`, `Ngày sinh`, `Giới tính`, `Mã cha/mẹ`, `Mã con`, `Loại`, `Mã hôn phối`, `Mã người A`, `Mã người B`, `Tình trạng`, `Ngày bắt đầu`, `Ngày kết thúc`, `Thứ`, `Tên dòng họ`, `Mã thủy tổ`, `Đếm thứ`, `Gọi Út`, `Ngày mất`, `Còn sống`).
Mã lỗi: `MISSING_SHEET`, `REQUIRED`, `DUPLICATE_KEY`, `UNKNOWN_REF`, `BAD_VALUE`, `TOO_LONG`, `INCONSISTENT`; cảnh báo: `POSSIBLE_DUPLICATE`.

Quy tắc:
1. Thiếu sheet `Người` → lỗi `MISSING_SHEET` (row 0).
2. Mỗi dòng Người: `Mã` bắt buộc & duy nhất (không được bắt đầu bằng `#`); `Họ và tên` bắt buộc, ≤ 200; các cột chuỗi ≤ giới hạn (tên thường gọi 100, địa danh 200, âm lịch 40, sơ yếu 20000); giới tính/ngày/boolean theo `Cells`/`DateParser`; `alive == true` mà có `Ngày mất` → `INCONSISTENT` (cột `Còn sống`); `Ngày mất` < `Ngày sinh` → `INCONSISTENT` (cột `Ngày mất`).
3. Hôn phối: `Mã hôn phối` bắt buộc & duy nhất; `Mã người A/B` bắt buộc; hai mã khác nhau; mỗi mã là `#<số>` hoặc có trong sheet Người, ngược lại `UNKNOWN_REF`; `Thứ` nguyên ≥ 1 nếu có; ngày dùng `DateParser` (lấy `.date()`).
4. Quan hệ: `Mã cha/mẹ`, `Mã con` bắt buộc, tồn tại như trên, khác nhau; nếu `Mã hôn phối` có thì phải có trong sheet Hôn phối.
5. Dòng họ: tối đa 1 dòng dữ liệu (nhiều hơn → lỗi `BAD_VALUE` ở dòng thứ 2 trở đi); `Tên dòng họ` bắt buộc ≤ 150; `Mã thủy tổ` bắt buộc & tồn tại.
6. Cảnh báo `POSSIBLE_DUPLICATE` khi hai dòng Người có cùng (chuẩn hoá tên, ngày sinh đầy đủ giống nhau).
7. Sheet thiếu cột bắt buộc (không có cột `Mã` hoặc `Họ và tên`) → `MISSING_SHEET` kèm tên cột (đặt `column` = tiêu đề thiếu).

- [ ] **Step 1: Viết test (failing)** `ImportParserTest.java`

```java
package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.giapha.imports.ImportModel.Error;
import vn.giapha.person.DatePrecision;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.support.XlsxBuilder;

class ImportParserTest {
    static final List<String> PEOPLE = List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh", "Còn sống", "Ngày mất");
    List<Error> errors = new ArrayList<>(), warnings = new ArrayList<>();

    ImportModel.Parsed parse(XlsxBuilder b) {
        return ImportParser.parse(ExcelReader.read(new ByteArrayInputStream(b.bytes())), errors, warnings);
    }
    static List<Object> p(Object... v) { return Arrays.asList(v); }

    @Test
    void parsesHappyPath() {
        var parsed = parse(new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("P1", "Lê Tổ", "Nam", "1880", "Không", "1950"), p("P2", "Phạm Vợ", "Nữ", "05/03/1885", "", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B", "Thứ"), List.of(p("H1", "P1", "P2", 1)))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Loại", "Mã hôn phối"), List.of(p("P1", "#77", "Con nuôi", "H1")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ"), List.of(p("Họ Lê", "P1"))));
        assertThat(errors).isEmpty();
        assertThat(parsed.persons()).hasSize(2);
        var p1 = parsed.persons().get(0);
        assertThat(p1.gender()).isEqualTo(Gender.M);
        assertThat(p1.birth().precision()).isEqualTo(DatePrecision.YEAR);
        assertThat(p1.alive()).isFalse();
        var p2 = parsed.persons().get(1);
        assertThat(p2.birth().precision()).isEqualTo(DatePrecision.DAY);
        assertThat(p2.alive()).isTrue();                       // trống + không có ngày mất
        assertThat(parsed.unions().get(0).order()).isEqualTo(1);
        assertThat(parsed.links().get(0).kind()).isEqualTo(ParentKind.ADOPTED);
        assertThat(parsed.links().get(0).child()).isEqualTo("#77");
        assertThat(parsed.clan().name()).isEqualTo("Họ Lê");
    }

    @Test
    void missingPeopleSheetAndMissingColumns() {
        parse(new XlsxBuilder().sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of()));
        assertThat(errors).extracting(Error::code).contains("MISSING_SHEET");
        errors.clear();
        parse(new XlsxBuilder().sheet("Người", List.of("Mã"), List.of(p("P1"))));
        assertThat(errors).anySatisfy(e -> { assertThat(e.code()).isEqualTo("MISSING_SHEET"); assertThat(e.column()).isEqualTo("Họ và tên"); });
    }

    @Test
    void rowLevelErrorsCarryExcelRowAndColumn() {
        parse(new XlsxBuilder().sheet("Người", PEOPLE, List.of(
            p("P1", "Tốt", "Nam", "1950", "Có", ""),                  // dòng 2: hợp lệ
            p("P1", "Trùng mã", "Nam", "1950", "Có", ""),             // dòng 3: trùng mã
            p("P3", "", "Nam", "1950", "Có", ""),                     // dòng 4: thiếu tên
            p("P4", "Sai ngày", "Nam", "31/02/1950", "Có", ""),       // dòng 5
            p("P5", "Sai giới tính", "Robot", "1950", "Có", ""),      // dòng 6
            p("P6", "Còn sống mà có mất", "Nam", "1950", "Có", "1990"), // dòng 7
            p("P7", "Mất trước sinh", "Nam", "1950", "Không", "1940"),  // dòng 8
            p("#9", "Mã bắt đầu bằng #", "Nam", "1950", "Có", ""))));  // dòng 9
        assertThat(errors).extracting(e -> e.row() + ":" + e.column() + ":" + e.code()).containsExactlyInAnyOrder(
            "3:Mã:DUPLICATE_KEY", "4:Họ và tên:REQUIRED", "5:Ngày sinh:BAD_VALUE", "6:Giới tính:BAD_VALUE",
            "7:Còn sống:INCONSISTENT", "8:Ngày mất:INCONSISTENT", "9:Mã:BAD_VALUE");
        assertThat(errors).allSatisfy(e -> assertThat(e.sheet()).isEqualTo("Người"));
    }

    @Test
    void referencesAreChecked() {
        parse(new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("P1", "A", "Nam", "1950", "Có", ""), p("P2", "B", "Nữ", "1952", "Có", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B"), List.of(p("H1", "P1", "P1"), p("H2", "P1", "P9")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Mã hôn phối"), List.of(p("P1", "P1", ""), p("P1", "P2", "H9"), p("PX", "P2", ""))));
        assertThat(errors).extracting(e -> e.sheet() + ":" + e.row() + ":" + e.column() + ":" + e.code()).containsExactlyInAnyOrder(
            "Hôn phối:2:Mã người B:BAD_VALUE", "Hôn phối:3:Mã người B:UNKNOWN_REF",
            "Quan hệ:2:Mã con:BAD_VALUE", "Quan hệ:3:Mã hôn phối:UNKNOWN_REF", "Quan hệ:4:Mã cha/mẹ:UNKNOWN_REF");
    }

    @Test
    void secondClanRowAndUnknownFounderAreErrors() {
        parse(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("P1", "A", "Nam", "1950", "Có", "")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ"), List.of(p("Họ A", "P1"), p("Họ B", "P1"), p("Họ C", "P7"))));
        assertThat(errors).extracting(e -> e.row() + ":" + e.code()).containsExactlyInAnyOrder("3:BAD_VALUE", "4:BAD_VALUE");
    }

    @Test
    void possibleDuplicateInFileIsOnlyAWarning() {
        parse(new XlsxBuilder().sheet("Người", PEOPLE, List.of(
            p("P1", "Nguyễn Văn A", "Nam", "05/03/1950", "Có", ""), p("P2", "nguyen van a", "Nam", "05/03/1950", "Có", ""))));
        assertThat(errors).isEmpty();
        assertThat(warnings).extracting(Error::code).containsExactly("POSSIBLE_DUPLICATE");
        assertThat(warnings.get(0).row()).isEqualTo(3);
    }

    @Test
    void tooLongValuesAreRejected() {
        parse(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("P1", "x".repeat(201), "Nam", "1950", "Có", ""))));
        assertThat(errors).extracting(Error::code).containsExactly("TOO_LONG");
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `./gradlew test --tests '*ImportParserTest'`
Expected: FAIL.

- [ ] **Step 3: Viết `ImportModel.java`**

```java
package vn.giapha.imports;

import java.time.LocalDate;
import java.util.List;
import vn.giapha.clan.SiblingCountMode;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.relation.UnionStatus;

public final class ImportModel {
    private ImportModel() {}

    public record Error(String sheet, int row, String column, String code, String message) {}
    public record PersonRow(int row, String key, String fullName, String nickname, Gender gender, DateParser.Parsed birth, String birthLunar,
                            boolean alive, DateParser.Parsed death, String deathLunar, String origin, String home, String burial, String biography) {}
    public record UnionRow(int row, String key, String a, String b, Integer order, UnionStatus status, LocalDate start, LocalDate end) {}
    public record LinkRow(int row, String parent, String child, ParentKind kind, String unionKey) {}
    public record ClanRow(int row, String name, String founderKey, SiblingCountMode mode, Boolean useUt) {}
    public record Parsed(List<PersonRow> persons, List<UnionRow> unions, List<LinkRow> links, ClanRow clan) {}
}
```

- [ ] **Step 4: Viết `ImportParser.java`**

```java
package vn.giapha.imports;

import java.util.*;
import java.util.function.Supplier;
import vn.giapha.imports.ImportModel.*;

public final class ImportParser {
    private static final String S_PEOPLE = "Người", S_LINKS = "Quan hệ", S_UNIONS = "Hôn phối", S_CLAN = "Dòng họ";

    private ImportParser() {}

    public static Parsed parse(Map<String, ExcelReader.Sheet> sheets, List<Error> errors, List<Error> warnings) {
        List<PersonRow> persons = new ArrayList<>();
        List<UnionRow> unions = new ArrayList<>();
        List<LinkRow> links = new ArrayList<>();
        ClanRow clan = null;

        ExcelReader.Sheet people = sheets.get("nguoi");
        if (people == null) {
            errors.add(new Error(S_PEOPLE, 0, "", "MISSING_SHEET", "Thiếu sheet \"Người\""));
            return new Parsed(persons, unions, links, null);
        }
        if (missing(people, S_PEOPLE, errors, Map.of("ma", "Mã", "hovaten", "Họ và tên"))) return new Parsed(persons, unions, links, null);

        Set<String> keys = new LinkedHashSet<>();
        Map<String, Integer> seenIdentity = new HashMap<>();
        for (ExcelReader.Row r : people.rows()) {
            int before = errors.size();
            String key = cell(r, "ma");
            if (key.isEmpty()) errors.add(err(S_PEOPLE, r, "Mã", "REQUIRED", "Thiếu mã"));
            else if (key.startsWith("#")) errors.add(err(S_PEOPLE, r, "Mã", "BAD_VALUE", "Mã không được bắt đầu bằng #"));
            else if (!keys.add(key)) errors.add(err(S_PEOPLE, r, "Mã", "DUPLICATE_KEY", "Mã bị trùng: " + key));
            String name = cell(r, "hovaten");
            if (name.isEmpty()) errors.add(err(S_PEOPLE, r, "Họ và tên", "REQUIRED", "Thiếu họ và tên"));
            length(errors, S_PEOPLE, r, "Họ và tên", name, 200);
            length(errors, S_PEOPLE, r, "Tên thường gọi", cell(r, "tenthuonggoi"), 100);
            length(errors, S_PEOPLE, r, "Ngày sinh âm lịch", cell(r, "ngaysinhamlich"), 40);
            length(errors, S_PEOPLE, r, "Ngày giỗ âm lịch", cell(r, "ngaygioamlich"), 40);
            length(errors, S_PEOPLE, r, "Nguyên quán", cell(r, "nguyenquan"), 200);
            length(errors, S_PEOPLE, r, "Quê quán", cell(r, "quequan"), 200);
            length(errors, S_PEOPLE, r, "Nơi an táng", cell(r, "noiantang"), 200);
            length(errors, S_PEOPLE, r, "Sơ yếu lý lịch", cell(r, "soyeulylich"), 20000);

            var gender = guard(errors, S_PEOPLE, r, "Giới tính", () -> Cells.gender(cell(r, "gioitinh")));
            var birth = guard(errors, S_PEOPLE, r, "Ngày sinh", () -> DateParser.parse(cell(r, "ngaysinh")));
            var death = guard(errors, S_PEOPLE, r, "Ngày mất", () -> DateParser.parse(cell(r, "ngaymat")));
            Boolean aliveCell = guard(errors, S_PEOPLE, r, "Còn sống", () -> Cells.bool(cell(r, "consong")));
            boolean alive = aliveCell != null ? aliveCell : death == null;
            if (alive && death != null) errors.add(err(S_PEOPLE, r, "Còn sống", "INCONSISTENT", "Còn sống nhưng có ngày mất"));
            if (birth != null && death != null && death.date().isBefore(birth.date())) {
                errors.add(err(S_PEOPLE, r, "Ngày mất", "INCONSISTENT", "Ngày mất trước ngày sinh"));
            }
            if (errors.size() == before && gender != null) {
                persons.add(new PersonRow(r.number(), key, name, blank(cell(r, "tenthuonggoi")), gender, birth, blank(cell(r, "ngaysinhamlich")),
                    alive, alive ? null : death, blank(cell(r, "ngaygioamlich")), blank(cell(r, "nguyenquan")), blank(cell(r, "quequan")),
                    blank(cell(r, "noiantang")), blank(cell(r, "soyeulylich"))));
                if (birth != null && birth.precision() == vn.giapha.person.DatePrecision.DAY) {
                    String ident = Norm.key(name) + "|" + birth.date();
                    Integer first = seenIdentity.putIfAbsent(ident, r.number());
                    if (first != null) warnings.add(err(S_PEOPLE, r, "Họ và tên", "POSSIBLE_DUPLICATE", "Có thể trùng với dòng " + first));
                }
            }
        }

        Set<String> unionKeys = new LinkedHashSet<>();
        ExcelReader.Sheet us = sheets.get("honphoi");
        if (us != null && !missing(us, S_UNIONS, errors, Map.of("mahonphoi", "Mã hôn phối", "manguoia", "Mã người A", "manguoib", "Mã người B"))) {
            for (ExcelReader.Row r : us.rows()) {
                int before = errors.size();
                String key = cell(r, "mahonphoi"), a = cell(r, "manguoia"), b = cell(r, "manguoib");
                if (key.isEmpty()) errors.add(err(S_UNIONS, r, "Mã hôn phối", "REQUIRED", "Thiếu mã hôn phối"));
                else if (!unionKeys.add(key)) errors.add(err(S_UNIONS, r, "Mã hôn phối", "DUPLICATE_KEY", "Mã hôn phối bị trùng: " + key));
                checkRef(errors, S_UNIONS, r, "Mã người A", a, keys);
                checkRef(errors, S_UNIONS, r, "Mã người B", b, keys);
                if (!a.isEmpty() && a.equals(b)) errors.add(err(S_UNIONS, r, "Mã người B", "BAD_VALUE", "Hai người trong hôn phối phải khác nhau"));
                Integer order = null;
                if (!cell(r, "thu").isEmpty()) {
                    try { order = Integer.parseInt(cell(r, "thu")); if (order < 1) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { errors.add(err(S_UNIONS, r, "Thứ", "BAD_VALUE", "Thứ phải là số nguyên ≥ 1")); }
                }
                var status = guard(errors, S_UNIONS, r, "Tình trạng", () -> Cells.unionStatus(cell(r, "tinhtrang")));
                var start = guard(errors, S_UNIONS, r, "Ngày bắt đầu", () -> DateParser.parse(cell(r, "ngaybatdau")));
                var end = guard(errors, S_UNIONS, r, "Ngày kết thúc", () -> DateParser.parse(cell(r, "ngayketthuc")));
                if (errors.size() == before) unions.add(new UnionRow(r.number(), key, a, b, order, status, start == null ? null : start.date(), end == null ? null : end.date()));
            }
        }

        ExcelReader.Sheet ls = sheets.get("quanhe");
        if (ls != null && !missing(ls, S_LINKS, errors, Map.of("machame", "Mã cha/mẹ", "macon", "Mã con"))) {
            for (ExcelReader.Row r : ls.rows()) {
                int before = errors.size();
                String parent = cell(r, "machame"), child = cell(r, "macon"), uKey = cell(r, "mahonphoi");
                checkRef(errors, S_LINKS, r, "Mã cha/mẹ", parent, keys);
                checkRef(errors, S_LINKS, r, "Mã con", child, keys);
                if (!parent.isEmpty() && parent.equals(child)) errors.add(err(S_LINKS, r, "Mã con", "BAD_VALUE", "Cha/mẹ và con phải khác nhau"));
                if (!uKey.isEmpty() && !unionKeys.contains(uKey)) errors.add(err(S_LINKS, r, "Mã hôn phối", "UNKNOWN_REF", "Không có hôn phối " + uKey + " trong sheet Hôn phối"));
                var kind = guard(errors, S_LINKS, r, "Loại", () -> Cells.kind(cell(r, "loai")));
                if (errors.size() == before) links.add(new LinkRow(r.number(), parent, child, kind, uKey.isEmpty() ? null : uKey));
            }
        }

        ExcelReader.Sheet cs = sheets.get("dongho");
        if (cs != null && !missing(cs, S_CLAN, errors, Map.of("tendongho", "Tên dòng họ", "mathuyto", "Mã thủy tổ"))) {
            for (int i = 0; i < cs.rows().size(); i++) {
                ExcelReader.Row r = cs.rows().get(i);
                if (i > 0) { errors.add(err(S_CLAN, r, "Tên dòng họ", "BAD_VALUE", "Chỉ nhập một dòng họ mỗi lần")); continue; }
                int before = errors.size();
                String name = cell(r, "tendongho"), founder = cell(r, "mathuyto");
                if (name.isEmpty()) errors.add(err(S_CLAN, r, "Tên dòng họ", "REQUIRED", "Thiếu tên dòng họ"));
                length(errors, S_CLAN, r, "Tên dòng họ", name, 150);
                if (founder.isEmpty()) errors.add(err(S_CLAN, r, "Mã thủy tổ", "REQUIRED", "Thiếu mã thủy tổ"));
                else checkRef(errors, S_CLAN, r, "Mã thủy tổ", founder, keys);
                var mode = guard(errors, S_CLAN, r, "Đếm thứ", () -> Cells.countMode(cell(r, "demthu")));
                Boolean useUt = guard(errors, S_CLAN, r, "Gọi Út", () -> Cells.bool(cell(r, "goiut")));
                if (errors.size() == before) clan = new ClanRow(r.number(), name, founder, mode, useUt);
            }
        }
        return new Parsed(persons, unions, links, clan);
    }

    private static boolean missing(ExcelReader.Sheet s, String sheetName, List<Error> errors, Map<String, String> required) {
        boolean bad = false;
        for (var e : required.entrySet()) {
            if (!s.headers().contains(e.getKey())) {
                errors.add(new Error(sheetName, 1, e.getValue(), "MISSING_SHEET", "Sheet \"" + sheetName + "\" thiếu cột \"" + e.getValue() + "\""));
                bad = true;
            }
        }
        return bad;
    }

    private static void checkRef(List<Error> errors, String sheet, ExcelReader.Row r, String col, String ref, Set<String> keys) {
        if (ref.isEmpty()) { errors.add(err(sheet, r, col, "REQUIRED", "Thiếu " + col)); return; }
        if (ref.startsWith("#")) {
            try { Long.parseLong(ref.substring(1)); } catch (NumberFormatException e) { errors.add(err(sheet, r, col, "BAD_VALUE", "Mã người đã có phải dạng #123")); }
            return;
        }
        if (!keys.contains(ref)) errors.add(err(sheet, r, col, "UNKNOWN_REF", "Không có người mã " + ref + " trong sheet Người"));
    }

    private static <T> T guard(List<Error> errors, String sheet, ExcelReader.Row r, String col, Supplier<T> f) {
        try { return f.get(); } catch (IllegalArgumentException e) { errors.add(err(sheet, r, col, "BAD_VALUE", e.getMessage())); return null; }
    }

    private static void length(List<Error> errors, String sheet, ExcelReader.Row r, String col, String v, int max) {
        if (v.length() > max) errors.add(err(sheet, r, col, "TOO_LONG", col + " dài quá " + max + " ký tự"));
    }

    private static String cell(ExcelReader.Row r, String key) { return r.cells().getOrDefault(key, "").trim(); }
    private static String blank(String s) { return s.isEmpty() ? null : s; }
    private static Error err(String sheet, ExcelReader.Row r, String col, String code, String msg) { return new Error(sheet, r.number(), col, code, msg); }
}
```
Lưu ý với test `rowLevelErrors`: dòng 2 (`P1`, "Tốt") hợp lệ; dòng 3 trùng mã **và** vẫn phân tích các cột khác bình thường; bộ đếm `errors.size() == before` bảo đảm dòng lỗi không thành `PersonRow`. Người bị bỏ vì lỗi vẫn được thêm vào `keys` (nếu mã hợp lệ) để không sinh thêm lỗi `UNKNOWN_REF` dây chuyền.

- [ ] **Step 5: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*ImportParserTest'`
Expected: PASS (7 tests). Nếu `referencesAreChecked` báo thêm/thiếu lỗi, đối chiếu từng dòng với bảng kỳ vọng — mỗi dòng sai chỉ một lỗi.

- [ ] **Step 6: Commit**

```bash
git add . && git commit -m "feat(E8): import parser with per-row validation"
```

---

### Task 4: ExcelImportService (chạy thử / nhập thật)

**Files:**
- Create (package `vn.giapha.imports`): `ImportReport.java`, `ExcelImportService.java`
- Modify: `backend/src/main/java/vn/giapha/audit/AuditSummary.java` (thêm mô tả `IMPORT`)
- Test: `backend/src/test/java/vn/giapha/imports/ExcelImportServiceTest.java`

**Interfaces:**
- Produces:
```java
public record ImportReport(boolean dryRun, int persons, int unions, int links, List<ImportModel.Error> errors, List<ImportModel.Error> warnings, Long clanId) {}
@Service ExcelImportService { public ImportReport run(AuthUser me, InputStream in, boolean dryRun); }
```
Hành vi: đọc → `ImportParser`; nếu có lỗi → trả báo cáo ngay (không đụng DB). Ngược lại chạy trong `TransactionTemplate`: tạo người (`created_by = me.id()`), hôn phối (`UnionService.create`), quan hệ (`ParentChildService.add`), dòng họ (tạo mới hoặc cập nhật theo **tên**), rồi `ClanMemberService.rebuild` / `rebuildClansOf` các người liên quan. Lỗi `ApiException` từ service được đổi thành `ImportError(sheet,row,"",code,message)`. Nếu `dryRun` hoặc có lỗi → rollback; nếu không → commit và ghi 1 dòng `audit_log` (`IMPORT`). Người `#id` phải tồn tại và chưa xoá, ngược lại lỗi `UNKNOWN_REF` (cột theo vai trò: `Mã cha/mẹ`, `Mã con`, `Mã người A/B`, `Mã thủy tổ`).
`AuditSummary.summary("IMPORT", "IMPORT", _, after, _)` → `"Nhập Excel: {persons} người, {unions} hôn phối, {links} quan hệ"` (đọc từ `after`: `persons`, `unions`, `links`).

- [ ] **Step 1: Viết test (failing)** `ExcelImportServiceTest.java`

```java
package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.AppUser;
import vn.giapha.auth.AuthUser;
import vn.giapha.auth.Role;
import vn.giapha.support.TestUsers;
import vn.giapha.support.XlsxBuilder;

@SpringBootTest
@Import({PostgresTestConfig.class, TestUsers.class})
class ExcelImportServiceTest {
    @Autowired ExcelImportService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestUsers users;

    static List<Object> p(Object... v) { return Arrays.asList(v); }
    static final List<String> PEOPLE = List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh", "Còn sống", "Ngày mất");

    XlsxBuilder family() {
        return new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(
                p("P1", "Lê Tổ Import", "Nam", "1880", "Không", "1950"), p("P2", "Phạm Vợ Import", "Nữ", "1885", "Không", "1960"),
                p("P3", "Lê Con Import", "Nam", "05/03/1910", "Không", "1980"), p("P4", "Lê Cháu Import", "Nữ", "1935", "Có", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B", "Thứ", "Tình trạng"), List.of(p("H1", "P1", "P2", 1, "Đang kết hôn")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Loại", "Mã hôn phối"), List.of(
                p("P1", "P3", "Ruột", "H1"), p("P2", "P3", "Ruột", "H1"), p("P3", "P4", "Ruột", "")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ", "Đếm thứ", "Gọi Út"), List.of(p("Họ Lê Import", "P1", "Theo hôn phối", "Có")));
    }
    ImportReport run(XlsxBuilder b, boolean dry) { return service.run(new AuthUser(users.create(Role.ADMIN).getId(), Role.ADMIN), new ByteArrayInputStream(b.bytes()), dry); }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    @Test
    void realImportCreatesEverythingAndBuildsClan() {
        int p0 = count("person"), u0 = count("union_rel"), l0 = count("parent_child");
        ImportReport r = run(family(), false);
        assertThat(r.errors()).isEmpty();
        assertThat(r.persons()).isEqualTo(4);
        assertThat(r.unions()).isEqualTo(1);
        assertThat(r.links()).isEqualTo(3);
        assertThat(r.clanId()).isNotNull();
        assertThat(count("person") - p0).isEqualTo(4);
        assertThat(count("union_rel") - u0).isEqualTo(1);
        assertThat(count("parent_child") - l0).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clan_member WHERE clan_id=?", Integer.class, r.clanId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT cm.generation FROM clan_member cm JOIN person p ON p.id=cm.person_id WHERE cm.clan_id=? AND p.full_name='Lê Cháu Import'",
            Integer.class, r.clanId())).isEqualTo(3);   // mọi truy vấn theo tên đều giới hạn trong dòng họ vừa nhập vì các test khác cũng nhập cùng tên
        String inClan = "SELECT birth_precision FROM person p JOIN clan_member cm ON cm.person_id = p.id AND cm.clan_id = ? WHERE p.full_name = ?";
        assertThat(jdbc.queryForObject(inClan, String.class, r.clanId(), "Lê Con Import")).isEqualTo("DAY");
        assertThat(jdbc.queryForObject(inClan, String.class, r.clanId(), "Lê Tổ Import")).isEqualTo("YEAR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='IMPORT' AND entity_id=?", Integer.class, r.clanId())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void dryRunReportsCountsButChangesNothing() {
        int p0 = count("person"), c0 = count("clan"), m0 = count("clan_member");
        ImportReport r = run(family(), true);
        assertThat(r.dryRun()).isTrue();
        assertThat(r.errors()).isEmpty();
        assertThat(r.persons()).isEqualTo(4);
        assertThat(count("person")).isEqualTo(p0);
        assertThat(count("clan")).isEqualTo(c0);
        assertThat(count("clan_member")).isEqualTo(m0);
    }

    @Test
    void parseErrorsSaveNothing() {
        int p0 = count("person");
        ImportReport r = run(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("P1", "Tốt", "Nam", "1950", "Có", ""), p("P2", "", "Nam", "1950", "Có", ""))), false);
        assertThat(r.errors()).hasSize(1);
        assertThat(r.errors().get(0).row()).isEqualTo(3);
        assertThat(count("person")).isEqualTo(p0);
    }

    @Test
    void cycleAndSecondBiologicalFatherAreReportedAtTheirRowsAndRollBackEverything() {
        int p0 = count("person"), l0 = count("parent_child");
        XlsxBuilder b = new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("P1", "A", "Nam", "1900", "Có", ""), p("P2", "B", "Nam", "1925", "Có", ""),
                p("P3", "C", "Nam", "1950", "Có", ""), p("P4", "D", "Nam", "1951", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(
                p("P1", "P2"), p("P2", "P3"), p("P3", "P1"),      // dòng 4: vòng lặp
                p("P1", "P4"), p("P2", "P4")));                   // dòng 6: con D có hai cha ruột
        ImportReport r = run(b, false);
        assertThat(r.errors()).extracting(e -> e.row() + ":" + e.code()).containsExactlyInAnyOrder("4:RELATION_CYCLE", "6:RELATION_INVALID");
        assertThat(r.errors()).allSatisfy(e -> assertThat(e.sheet()).isEqualTo("Quan hệ"));
        assertThat(count("person")).isEqualTo(p0);
        assertThat(count("parent_child")).isEqualTo(l0);
    }

    @Test
    void referencesExistingPersonById() {
        long existing = jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Người sẵn có','M') RETURNING id", Long.class);
        XlsxBuilder b = new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("N1", "Con của người sẵn có", "Nam", "2000", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(p("#" + existing, "N1")));
        ImportReport r = run(b, false);
        assertThat(r.errors()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_child WHERE parent_id=?", Integer.class, existing)).isEqualTo(1);
        // id không tồn tại
        ImportReport bad = run(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("N1", "X", "Nam", "2000", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(p("#999999999", "N1"))), false);
        assertThat(bad.errors()).extracting(ImportModel.Error::code).containsExactly("UNKNOWN_REF");
        assertThat(bad.errors().get(0).column()).isEqualTo("Mã cha/mẹ");
    }

    @Test
    void importingSameClanNameTwiceUpdatesInsteadOfDuplicating() {
        run(family(), false);
        int clans = count("clan");
        run(family(), false);
        assertThat(count("clan")).isEqualTo(clans);
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `./gradlew test --tests '*ExcelImportServiceTest'`
Expected: FAIL (thiếu lớp).

- [ ] **Step 3: Viết `ImportReport.java`**

```java
package vn.giapha.imports;

import java.util.List;

public record ImportReport(boolean dryRun, int persons, int unions, int links, List<ImportModel.Error> errors,
                           List<ImportModel.Error> warnings, Long clanId) {}
```

- [ ] **Step 4: Viết `ExcelImportService.java`**

```java
package vn.giapha.imports;

import java.io.InputStream;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.giapha.audit.AuditService;
import vn.giapha.auth.AuthUser;
import vn.giapha.clan.*;
import vn.giapha.common.ApiException;
import vn.giapha.imports.ImportModel.*;
import vn.giapha.person.*;
import vn.giapha.relation.*;

@Service
public class ExcelImportService {
    private final PersonRepository persons;
    private final UnionService unions;
    private final ParentChildService links;
    private final ClanRepository clans;
    private final ClanMemberService clanMembers;
    private final AuditService audit;
    private final PlatformTransactionManager tm;

    public ExcelImportService(PersonRepository persons, UnionService unions, ParentChildService links, ClanRepository clans,
                              ClanMemberService clanMembers, AuditService audit, PlatformTransactionManager tm) {
        this.persons = persons;
        this.unions = unions;
        this.links = links;
        this.clans = clans;
        this.clanMembers = clanMembers;
        this.audit = audit;
        this.tm = tm;
    }

    public ImportReport run(AuthUser me, InputStream in, boolean dryRun) {
        List<Error> errors = new ArrayList<>(), warnings = new ArrayList<>();
        Parsed parsed = ImportParser.parse(ExcelReader.read(in), errors, warnings);
        if (!errors.isEmpty()) {
            return new ImportReport(dryRun, parsed.persons().size(), parsed.unions().size(), parsed.links().size(), errors, warnings, null);
        }
        TransactionTemplate tx = new TransactionTemplate(tm);
        return tx.execute(status -> {
            ImportReport r = persist(me, parsed, dryRun, warnings);
            if (dryRun || !r.errors().isEmpty()) status.setRollbackOnly();
            return r;
        });
    }

    private ImportReport persist(AuthUser me, Parsed parsed, boolean dryRun, List<Error> warnings) {
        List<Error> errors = new ArrayList<>();
        Map<String, Long> ids = new HashMap<>();
        Set<Long> touched = new LinkedHashSet<>();

        for (PersonRow r : parsed.persons()) {
            Person p = new Person();
            p.setFullName(r.fullName());
            p.setNickname(r.nickname());
            p.setGender(r.gender());
            if (r.birth() != null) { p.setBirthDate(r.birth().date()); p.setBirthPrecision(r.birth().precision()); }
            p.setBirthLunar(r.birthLunar());
            p.setAlive(r.alive());
            if (r.death() != null) { p.setDeathDate(r.death().date()); p.setDeathPrecision(r.death().precision()); }
            p.setDeathAnniversaryLunar(r.deathLunar());
            p.setOriginPlace(r.origin());
            p.setHomePlace(r.home());
            p.setBurialPlace(r.burial());
            p.setBiography(r.biography());
            p.setCreatedBy(me.id());
            p = persons.save(p);
            ids.put(r.key(), p.getId());
            touched.add(p.getId());
        }
        persons.flush();

        Map<String, Long> unionIds = new HashMap<>();
        int unionCount = 0;
        for (UnionRow r : parsed.unions()) {
            Long a = resolve(r.a(), ids, errors, "Hôn phối", r.row(), "Mã người A");
            Long b = resolve(r.b(), ids, errors, "Hôn phối", r.row(), "Mã người B");
            if (a == null || b == null) continue;
            try {
                UnionRel u = unions.create(a, b, r.order(), r.status(), r.start(), r.end());
                unionIds.put(r.key(), u.getId());
                touched.add(a);
                touched.add(b);
                unionCount++;
            } catch (ApiException e) {
                errors.add(new Error("Hôn phối", r.row(), "", e.code(), e.getMessage()));
            }
        }

        int linkCount = 0;
        for (LinkRow r : parsed.links()) {
            Long parent = resolve(r.parent(), ids, errors, "Quan hệ", r.row(), "Mã cha/mẹ");
            Long child = resolve(r.child(), ids, errors, "Quan hệ", r.row(), "Mã con");
            if (parent == null || child == null) continue;
            try {
                links.add(parent, child, r.kind(), r.unionKey() == null ? null : unionIds.get(r.unionKey()));
                touched.add(parent);
                touched.add(child);
                linkCount++;
            } catch (ApiException e) {
                errors.add(new Error("Quan hệ", r.row(), "", e.code(), e.getMessage()));
            }
        }

        Long clanId = null;
        if (parsed.clan() != null && errors.isEmpty()) {
            ClanRow c = parsed.clan();
            Long founder = resolve(c.founderKey(), ids, errors, "Dòng họ", c.row(), "Mã thủy tổ");
            if (founder != null) {
                Clan clan = clans.findAll().stream().filter(x -> x.getName().equalsIgnoreCase(c.name())).findFirst().orElseGet(Clan::new);
                clan.setName(c.name());
                clan.setFounderPersonId(founder);
                if (c.mode() != null) clan.setSiblingCountMode(c.mode());
                if (c.useUt() != null) clan.setUseUtLabel(c.useUt());
                clan = clans.saveAndFlush(clan);
                clanId = clan.getId();
                clanMembers.rebuild(clanId);
            }
        }
        if (errors.isEmpty()) {
            clanMembers.rebuildClansOf(touched);
            if (!dryRun) {
                audit.record(me.id(), "IMPORT", clanId == null ? 0 : clanId, "IMPORT", null,
                    Map.of("persons", parsed.persons().size(), "unions", unionCount, "links", linkCount));
            }
        }
        return new ImportReport(dryRun, parsed.persons().size(), unionCount, linkCount, errors, warnings, clanId);
    }

    private Long resolve(String ref, Map<String, Long> ids, List<Error> errors, String sheet, int row, String column) {
        if (ref.startsWith("#")) {
            long id = Long.parseLong(ref.substring(1));
            if (persons.findByIdAndDeletedAtIsNull(id).isEmpty()) {
                errors.add(new Error(sheet, row, column, "UNKNOWN_REF", "Không có người đã có với mã " + ref));
                return null;
            }
            return id;
        }
        Long id = ids.get(ref);
        if (id == null) errors.add(new Error(sheet, row, column, "UNKNOWN_REF", "Không có người mã " + ref));
        return id;
    }
}
```
Lưu ý quan trọng: khi `ParentChildService.add` ném `ApiException`, proxy `@Transactional` đánh dấu giao dịch ngoài là rollback-only — đúng ý (có lỗi → rollback). Người đã bị loại ở bước parse **không** nằm trong `ids`, nên các tham chiếu tới họ được phát hiện từ bước parse (không tới được đây). `audit_log.entity_id` là NOT NULL nên dùng `0` khi không có dòng họ.

- [ ] **Step 5: Thêm mô tả `IMPORT` vào `AuditSummary.summary`**

Trong `switch (entityType)` của `AuditSummary.summary` thêm trước `default`:
```java
            case "IMPORT": {
                JsonNode a = after == null ? before : after;
                return "Nhập Excel: " + (a == null ? 0 : a.path("persons").asInt()) + " người, "
                    + (a == null ? 0 : a.path("unions").asInt()) + " hôn phối, " + (a == null ? 0 : a.path("links").asInt()) + " quan hệ";
            }
```
và thêm vào `AuditSummaryTest` (E7):
```java
    @Test
    void importSummary() throws Exception {
        assertThat(AuditSummary.summary("IMPORT", "IMPORT", null, j("{\"persons\":4,\"unions\":1,\"links\":3}"), NAMES::get))
            .isEqualTo("Nhập Excel: 4 người, 1 hôn phối, 3 quan hệ");
    }
```

- [ ] **Step 6: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*ExcelImportServiceTest' --tests '*AuditSummaryTest'`
Expected: PASS. Nếu `cycleAndSecondBiologicalFather...` báo thêm lỗi dây chuyền (vd lỗi ở dòng 5), kiểm tra: dòng 5 (`P1→P4`) hợp lệ; dòng 6 (`P2→P4`): cả P1 và P2 là nam → `RELATION_INVALID` "Đã có cha ruột". Dòng 4 `P3→P1`: P1 là tổ của P3 → `RELATION_CYCLE`.

- [ ] **Step 7: Commit**

```bash
git add . && git commit -m "feat(E8): all-or-nothing Excel import with dry run"
```

---

### Task 5: API nhập Excel

**Files:**
- Create: `backend/src/main/java/vn/giapha/imports/ImportController.java`
- Test: `backend/src/test/java/vn/giapha/imports/ImportApiTest.java`

**Interfaces:**
- Produces (ADMIN): `GET /api/v1/import/template` → `.xlsx` (`Content-Disposition: attachment; filename="mau-nhap-gia-pha.xlsx"`); `POST /api/v1/import/excel?dryRun=true|false` (multipart `file`; mặc định `dryRun=true`) → 200 `ImportReport`; nhập thật có lỗi → 422 `{code:"IMPORT_INVALID", message, details: <ImportReport>}`.

- [ ] **Step 1: Viết test (failing)** `ImportApiTest.java`

```java
package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.Role;
import vn.giapha.support.TestUsers;
import vn.giapha.support.XlsxBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class})
class ImportApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;

    static List<Object> p(Object... v) { return Arrays.asList(v); }
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "du-lieu.xlsx", XLSX, bytes); }
    byte[] good() {
        return new XlsxBuilder().sheet("Người", List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh"),
            List.of(p("A1", "Người API 1", "Nam", "1950"), p("A2", "Người API 2", "Nữ", "1952"))).bytes();
    }
    byte[] bad() {
        return new XlsxBuilder().sheet("Người", List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh"),
            List.of(p("A1", "Người API 1", "Nam", "1950"), p("A2", "", "Nữ", "1952"))).bytes();
    }

    @Test
    void permissionMatrix() throws Exception {
        mvc.perform(get("/api/v1/import/template")).andExpect(status().isUnauthorized());
        for (Role r : new Role[]{Role.VIEWER, Role.EDITOR}) {
            mvc.perform(get("/api/v1/import/template").header("Authorization", users.bearer(r))).andExpect(status().isForbidden());
            mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(good())).header("Authorization", users.bearer(r))).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(good()))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminDownloadsTemplate() throws Exception {
        var res = mvc.perform(get("/api/v1/import/template").header("Authorization", users.bearer(Role.ADMIN)))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mau-nhap-gia-pha.xlsx")))
            .andReturn().getResponse();
        assertThat(res.getContentAsByteArray().length).isGreaterThan(1000);
        assertThat(res.getContentType()).isEqualTo(XLSX);
    }

    @Test
    void dryRunIsDefaultAndDoesNotSave() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM person WHERE full_name LIKE 'Người API%'", Integer.class);
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(good())).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true)).andExpect(jsonPath("$.persons").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person WHERE full_name LIKE 'Người API%'", Integer.class)).isEqualTo(before);
    }

    @Test
    void realImportSaves() throws Exception {
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").param("dryRun", "false").file(file(good())).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(false)).andExpect(jsonPath("$.errors.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person WHERE full_name LIKE 'Người API%'", Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void dryRunWithErrorsIs200ButRealImportIs422WithReport() throws Exception {
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").param("dryRun", "true").file(file(bad())).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isOk()).andExpect(jsonPath("$.errors[0].row").value(3)).andExpect(jsonPath("$.errors[0].column").value("Họ và tên"));
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").param("dryRun", "false").file(file(bad())).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("IMPORT_INVALID"))
           .andExpect(jsonPath("$.details.errors[0].row").value(3));
    }

    @Test
    void garbageFileIs400() throws Exception {
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file("not excel".getBytes())).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**, rồi viết controller

Run: `./gradlew test --tests '*ImportApiTest'` → FAIL.

```java
package vn.giapha.imports;

import java.io.IOException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.giapha.auth.AuthUser;
import vn.giapha.common.ApiError;
import vn.giapha.common.ApiException;

@RestController
@RequestMapping("/api/v1/import")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final ExcelImportService service;

    public ImportController(ExcelImportService service) { this.service = service; }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-nhap-gia-pha.xlsx\"")
            .body(TemplateBuilder.build());
    }

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "true") boolean dryRun) throws IOException {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Chưa chọn tệp");
        ImportReport report = service.run(me, file.getInputStream(), dryRun);
        if (!dryRun && !report.errors().isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(new ApiError("IMPORT_INVALID", "Tệp có lỗi, chưa nhập gì cả", report));
        }
        return ResponseEntity.ok(report);
    }
}
```

- [ ] **Step 3: Chạy toàn bộ test BE**

Run: `./gradlew test`
Expected: PASS toàn bộ.

- [ ] **Step 4: Commit**

```bash
git add . && git commit -m "feat(E8): import template download and upload API"
```

---

### Task 6: FE — trang Nhập Excel

**Files:**
- Create: `frontend/src/api/importApi.ts`, `frontend/src/features/import/ImportPage.tsx`
- Modify: `frontend/src/App.tsx`, `frontend/src/components/AppLayout.tsx`, `frontend/src/auth/errorText.ts` (thêm `IMPORT_INVALID`)
- Test: `frontend/src/features/import/ImportPage.test.tsx`

**Interfaces:**
- Produces:
```ts
export interface ImportError { sheet: string; row: number; column: string; code: string; message: string }
export interface ImportReport { dryRun: boolean; persons: number; unions: number; links: number; errors: ImportError[]; warnings: ImportError[]; clanId: number | null }
export const importApi = {
  downloadTemplate(): Promise<void>,                                   // tải bằng fetch có Bearer rồi kích hoạt tải tệp
  upload(file: File, dryRun: boolean): Promise<ImportReport>,          // trả báo cáo cả khi 422 (errors.length > 0); các lỗi khác ném ApiFailure
}
```
Route `/admin/import` (ADMIN). Hành vi trang: chọn một tệp `.xlsx` (kéo-thả) → nút **Kiểm tra thử** → hiện tóm tắt "N người, M hôn phối, K quan hệ" và bảng lỗi/cảnh báo (cột Sheet, Dòng, Cột, Thông báo); nút **Nhập thật** chỉ bật khi lần kiểm tra thử **của đúng tệp đang chọn** sạch lỗi; chọn tệp khác → xoá kết quả cũ và tắt nút; nhập thật thành công → thông báo và liên kết "Xem cây" tới `/tree?mode=clan&clan=<clanId>`.

- [ ] **Step 1: Viết test (failing)** `ImportPage.test.tsx`

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { App as AntApp } from 'antd'
import ImportPage from './ImportPage'
import { importApi, type ImportReport } from '../../api/importApi'

vi.mock('../../api/importApi', async (orig) => ({ ...(await orig<typeof import('../../api/importApi')>()), importApi: { downloadTemplate: vi.fn(), upload: vi.fn() } }))

const clean = (over: Partial<ImportReport> = {}): ImportReport => ({ dryRun: true, persons: 4, unions: 1, links: 3, errors: [], warnings: [], clanId: null, ...over })
const xlsx = (name = 'a.xlsx') => new File(['x'], name, { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })

function setup() {
  const { container } = render(<MemoryRouter><AntApp><ImportPage /></AntApp></MemoryRouter>)
  return container.querySelector('input[type=file]') as HTMLInputElement
}

test('chưa chọn tệp thì không bấm được', () => {
  setup()
  expect(screen.getByRole('button', { name: 'Kiểm tra thử' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeDisabled()
})

test('tải mẫu gọi downloadTemplate', async () => {
  setup()
  await userEvent.click(screen.getByRole('button', { name: 'Tải mẫu Excel' }))
  expect(importApi.downloadTemplate).toHaveBeenCalled()
})

test('kiểm tra thử sạch lỗi hiện tóm tắt và bật Nhập thật', async () => {
  vi.mocked(importApi.upload).mockResolvedValue(clean())
  const input = setup()
  await userEvent.upload(input, xlsx())
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  expect(await screen.findByText(/4 người, 1 hôn phối, 3 quan hệ/)).toBeInTheDocument()
  expect(importApi.upload).toHaveBeenCalledWith(expect.any(File), true)
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeEnabled()
})

test('có lỗi thì hiện bảng lỗi theo dòng và không bật Nhập thật', async () => {
  vi.mocked(importApi.upload).mockResolvedValue(clean({ errors: [{ sheet: 'Người', row: 7, column: 'Ngày sinh', code: 'BAD_VALUE', message: 'Ngày không hợp lệ: 31/02/1950' }] }))
  const input = setup()
  await userEvent.upload(input, xlsx())
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  expect(await screen.findByText('Ngày không hợp lệ: 31/02/1950')).toBeInTheDocument()
  expect(screen.getByText('7')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeDisabled()
})

test('nhập thật thành công hiện liên kết xem cây', async () => {
  vi.mocked(importApi.upload).mockResolvedValueOnce(clean()).mockResolvedValueOnce(clean({ dryRun: false, clanId: 9 }))
  const input = setup()
  await userEvent.upload(input, xlsx())
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Nhập thật' }))
  await waitFor(() => expect(importApi.upload).toHaveBeenLastCalledWith(expect.any(File), false))
  expect(await screen.findByRole('link', { name: 'Xem cây' })).toHaveAttribute('href', '/tree?mode=clan&clan=9')
})

test('chọn tệp khác thì xoá kết quả cũ và tắt Nhập thật', async () => {
  vi.mocked(importApi.upload).mockResolvedValue(clean())
  const input = setup()
  await userEvent.upload(input, xlsx('a.xlsx'))
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  await screen.findByText(/4 người/)
  await userEvent.upload(input, xlsx('b.xlsx'))
  expect(screen.queryByText(/4 người/)).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeDisabled()
})
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run (ở `frontend`): `npx vitest run src/features/import`
Expected: FAIL.

- [ ] **Step 3: Viết triển khai**

`src/api/importApi.ts`:
```ts
import { rawFetch, toFailure } from './http'

export interface ImportError { sheet: string; row: number; column: string; code: string; message: string }
export interface ImportReport {
  dryRun: boolean; persons: number; unions: number; links: number; errors: ImportError[]; warnings: ImportError[]; clanId: number | null
}

export const importApi = {
  async downloadTemplate(): Promise<void> {
    const res = await rawFetch('/import/template')
    if (!res.ok) throw await toFailure(res)
    const url = URL.createObjectURL(await res.blob())
    const a = document.createElement('a')
    a.href = url
    a.download = 'mau-nhap-gia-pha.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  },
  async upload(file: File, dryRun: boolean): Promise<ImportReport> {
    const fd = new FormData()
    fd.append('file', file)
    const res = await rawFetch(`/import/excel?dryRun=${dryRun}`, { method: 'POST', body: fd })
    if (res.status === 422) {
      const body = (await res.json()) as { details: ImportReport }
      return body.details
    }
    if (!res.ok) throw await toFailure(res)
    return (await res.json()) as ImportReport
  },
}
```
`src/features/import/ImportPage.tsx`:
```tsx
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, App, Button, Space, Table, Typography, Upload } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { importApi, type ImportError, type ImportReport } from '../../api/importApi'
import { errorText } from '../../auth/errorText'

const columns = [
  { title: 'Sheet', dataIndex: 'sheet', width: 110 },
  { title: 'Dòng', dataIndex: 'row', width: 70 },
  { title: 'Cột', dataIndex: 'column', width: 140 },
  { title: 'Thông báo', dataIndex: 'message' },
]

export default function ImportPage() {
  const { message } = App.useApp()
  const [file, setFile] = useState<File | null>(null)
  const [dry, setDry] = useState<ImportReport | null>(null)
  const [done, setDone] = useState<ImportReport | null>(null)
  const [busy, setBusy] = useState(false)

  const run = async (dryRun: boolean) => {
    if (!file || busy) return
    setBusy(true)
    try {
      const r = await importApi.upload(file, dryRun)
      if (dryRun) { setDry(r); setDone(null) }
      else if (r.errors.length) setDry({ ...r, dryRun: true })
      else { setDone(r); message.success('Đã nhập xong') }
    } catch (e) {
      message.error(errorText((e as { code?: string }).code))
    } finally {
      setBusy(false)
    }
  }

  const canImport = !!file && !!dry && dry.errors.length === 0 && !done
  return (
    <div style={{ padding: 16, maxWidth: 960, margin: '0 auto' }}>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Button onClick={() => importApi.downloadTemplate().catch((e) => message.error(errorText((e as { code?: string }).code)))}>Tải mẫu Excel</Button>
        <Upload.Dragger accept=".xlsx" maxCount={1} showUploadList={!!file} beforeUpload={(f) => { setFile(f as File); setDry(null); setDone(null); return false }}
          onRemove={() => { setFile(null); setDry(null); setDone(null) }}>
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p>Kéo thả hoặc bấm để chọn tệp .xlsx</p>
        </Upload.Dragger>
        <Space>
          <Button disabled={!file} loading={busy} onClick={() => run(true)}>Kiểm tra thử</Button>
          <Button type="primary" disabled={!canImport} loading={busy} onClick={() => run(false)}>Nhập thật</Button>
        </Space>
        {dry && (
          <>
            <Alert showIcon type={dry.errors.length ? 'error' : 'success'}
              message={dry.errors.length ? `Có ${dry.errors.length} lỗi — chưa thể nhập` : `Sẵn sàng nhập: ${dry.persons} người, ${dry.unions} hôn phối, ${dry.links} quan hệ`} />
            {dry.errors.length > 0 && <Table<ImportError> size="small" rowKey={(e) => `${e.sheet}-${e.row}-${e.column}-${e.code}`} columns={columns} dataSource={dry.errors} pagination={{ pageSize: 20 }} scroll={{ x: true }} />}
            {dry.warnings.length > 0 && (
              <>
                <Typography.Text type="warning">Cảnh báo ({dry.warnings.length}) — vẫn nhập được:</Typography.Text>
                <Table<ImportError> size="small" rowKey={(e) => `${e.sheet}-${e.row}-${e.code}`} columns={columns} dataSource={dry.warnings} pagination={{ pageSize: 10 }} scroll={{ x: true }} />
              </>
            )}
          </>
        )}
        {done && (
          <Alert showIcon type="success" message={`Đã nhập ${done.persons} người, ${done.unions} hôn phối, ${done.links} quan hệ`}
            action={done.clanId ? <Link to={`/tree?mode=clan&clan=${done.clanId}`}>Xem cây</Link> : undefined} />
        )}
      </Space>
    </div>
  )
}
```
`src/auth/errorText.ts`: thêm vào bảng `TEXT` dòng `IMPORT_INVALID: 'Tệp có lỗi, chưa nhập gì cả',`.
`src/App.tsx`: thêm import và route (sau route `/admin/clans`):
```tsx
import ImportPage from './features/import/ImportPage'
// ...
        <Route path="/admin/import" element={<RequireAuth role="ADMIN"><ImportPage /></RequireAuth>} />
```
`src/components/AppLayout.tsx`: sau liên kết "Quản lý dòng họ" thêm:
```tsx
          {hasRole(user, 'ADMIN') && <Link to="/admin/import" style={{ color: '#fff' }}>Nhập Excel</Link>}
```

- [ ] **Step 4: Chạy test và build**

Run: `npx vitest run && npm run build`
Expected: PASS, build thành công.

- [ ] **Step 5: Kiểm tra thủ công (ghi vào mô tả commit)**

BE + FE chạy; đăng nhập ADMIN → "Nhập Excel": tải mẫu, mở bằng Excel, xoá dòng ví dụ rồi điền ~20 người + quan hệ (cố ý sai một ngày); Kiểm tra thử thấy lỗi đúng dòng; sửa; kiểm tra thử sạch; Nhập thật; "Xem cây" thấy dòng họ mới.

- [ ] **Step 6: Commit**

```bash
git add . && git commit -m "feat(E8): Excel import page"
```

---

## Self-review

- Spec: mẫu Excel, kiểm lỗi từng dòng, nhập thử (không lưu) rồi nhập thật, màn hình tải file + báo lỗi — T2–T6. Ràng buộc nghiệp vụ (vòng lặp, cha/mẹ ruột) dùng lại service E2 nên đồng nhất với nhập tay.
- Giới hạn có chủ ý: không đối chiếu trùng với dữ liệu đã có trong DB (chỉ cảnh báo trùng trong tệp); không có từng-dòng `audit_log` cho người nhập (chỉ một dòng `IMPORT`).
- Hiệu năng 2.000 người đo ở E9 (mỗi quan hệ đi qua service nên nhập ~4.000 dòng quan hệ mất vài giây đến vài chục giây).
