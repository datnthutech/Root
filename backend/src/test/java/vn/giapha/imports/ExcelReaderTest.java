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

    private static List<List<Object>> nRows(int n) {
        var rows = new java.util.ArrayList<List<Object>>();
        for (int i = 0; i < n; i++) rows.add(Arrays.asList("P" + i, "N"));
        return rows;
    }

    @Test
    void totalRowsAcrossSheetsIsCapped() {
        byte[] x = new XlsxBuilder()
            .sheet("Người", List.of("Mã", "Họ và tên"), nRows(ExcelReader.MAX_ROWS / 2 + 1))
            .sheet("Quan hệ", List.of("Mã", "Họ và tên"), nRows(ExcelReader.MAX_ROWS / 2 + 1)).bytes();
        assertThatThrownBy(() -> ExcelReader.read(new ByteArrayInputStream(x)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void tooManySheetsIs400() {
        var b = new XlsxBuilder();
        for (int i = 0; i <= ExcelReader.MAX_SHEETS; i++) b.sheet("S" + i, List.of("Mã"), List.of(Arrays.asList("x")));
        byte[] x = b.bytes();
        assertThatThrownBy(() -> ExcelReader.read(new ByteArrayInputStream(x)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void dateCellReadsAsIsoDate() {
        byte[] x = new XlsxBuilder().sheet("Người", List.of("Mã", "Ngày sinh"), List.of(Arrays.asList("P1", LocalDate.of(1999, 12, 31)))).bytes();
        assertThat(ExcelReader.read(new ByteArrayInputStream(x)).get("nguoi").rows().get(0).cells().get("ngaysinh")).isEqualTo("1999-12-31");
    }

    @Test
    void erroredOrUnsupportedFormulaDoesNotRejectFile() {
        byte[] x = new XlsxBuilder().sheet("Người", List.of("Mã", "A", "B", "C"),
            List.of(Arrays.asList("P1", "=1/0", "=[1]Sheet1!A1", "=NOSUCHFUNC(1)"))).bytes();
        var cells = ExcelReader.read(new ByteArrayInputStream(x)).get("nguoi").rows().get(0).cells();
        assertThat(cells.get("ma")).isEqualTo("P1");
        assertThat(cells.get("a")).isEmpty();
        assertThat(cells).containsKeys("b", "c");
    }

    @Test
    void duplicateHeaderLastColumnWins() {
        byte[] x = new XlsxBuilder().sheet("Người", List.of("Mã", "Tên", "Tên"), List.of(Arrays.asList("P1", "first", "second"))).bytes();
        assertThat(ExcelReader.read(new ByteArrayInputStream(x)).get("nguoi").rows().get(0).cells().get("ten")).isEqualTo("second");
    }
}
