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
