package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.giapha.person.Gender;

class VietnameseOrderTest {
    @Test
    void names() {
        assertThat(VietnameseOrder.name(2)).isEqualTo("Hai");
        assertThat(VietnameseOrder.name(3)).isEqualTo("Ba");
        assertThat(VietnameseOrder.name(4)).isEqualTo("Tư");
        assertThat(VietnameseOrder.name(5)).isEqualTo("Năm");
        assertThat(VietnameseOrder.name(6)).isEqualTo("Sáu");
        assertThat(VietnameseOrder.name(7)).isEqualTo("Bảy");
        assertThat(VietnameseOrder.name(8)).isEqualTo("Tám");
        assertThat(VietnameseOrder.name(9)).isEqualTo("Chín");
        assertThat(VietnameseOrder.name(10)).isEqualTo("Mười");
        assertThat(VietnameseOrder.name(11)).isEqualTo("Mười Một");
        assertThat(VietnameseOrder.name(12)).isEqualTo("Mười Hai");
        assertThat(VietnameseOrder.name(14)).isEqualTo("Mười Bốn");
        assertThat(VietnameseOrder.name(15)).isEqualTo("Mười Lăm");
        assertThat(VietnameseOrder.name(20)).isEqualTo("20");
    }

    @Test
    void labels() {
        assertThat(VietnameseOrder.label(2, Gender.M, false)).isEqualTo("anh Hai");
        assertThat(VietnameseOrder.label(3, Gender.F, false)).isEqualTo("chị Ba");
        assertThat(VietnameseOrder.label(3, Gender.U, false)).isEqualTo("thứ Ba");
        assertThat(VietnameseOrder.label(7, Gender.M, true)).isEqualTo("anh Út (thứ Bảy)");
        assertThat(VietnameseOrder.label(7, Gender.F, true)).isEqualTo("chị Út (thứ Bảy)");
        assertThat(VietnameseOrder.label(7, Gender.U, true)).isEqualTo("Út (thứ Bảy)");
    }
}
