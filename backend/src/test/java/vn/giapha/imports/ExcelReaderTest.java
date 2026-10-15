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
