package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.giapha.clan.SiblingCountMode;
import vn.giapha.person.Gender;

class SiblingGrouperTest {
    static ChildRow row(long id, Long f, Long m, Long u) {
        return new ChildRow(id, f, m, u, LocalDate.of(1950, 1, 1), null, true, Gender.M);
    }

    @Test
    void perUnionSplitsHalfSiblingsByUnion() {
        var rows = List.of(row(1, 10L, 20L, 100L), row(2, 10L, 20L, 100L), row(3, 10L, 21L, 101L));
        var g = SiblingGrouper.group(rows, SiblingCountMode.PER_UNION);
        assertThat(g).hasSize(2);
    }

    @Test
    void perFatherMergesChildrenOfSameFather() {
        var rows = List.of(row(1, 10L, 20L, 100L), row(2, 10L, 20L, 100L), row(3, 10L, 21L, 101L));
        var g = SiblingGrouper.group(rows, SiblingCountMode.PER_FATHER);
        assertThat(g).hasSize(1);
    }

    @Test
    void childrenWithoutUnionGroupByParentPair() {
        var rows = List.of(row(1, 10L, 20L, null), row(2, 10L, 20L, null), row(3, 10L, null, null));
        var g = SiblingGrouper.group(rows, SiblingCountMode.PER_UNION);
        assertThat(g).hasSize(2);
    }
}
