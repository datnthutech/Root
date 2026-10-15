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
