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
