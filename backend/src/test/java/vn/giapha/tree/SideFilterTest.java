package vn.giapha.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.giapha.common.ApiException;
import vn.giapha.person.Gender;

class SideFilterTest {
    // R(M) -> D(F), S(M);  D -> DK(M);  S -> SK(M)
    static final Map<Long, List<Long>> KIDS = Map.of(1L, List.of(2L, 3L), 2L, List.of(4L), 3L, List.of(5L));
    static final Map<Long, Gender> G = Map.of(1L, Gender.M, 2L, Gender.F, 3L, Gender.M, 4L, Gender.M, 5L, Gender.M);

    static java.util.Set<Long> run(Side s) {
        return SideFilter.apply(s, 1L, id -> KIDS.getOrDefault(id, List.of()), G::get);
    }

    @Test void bothKeepsEveryone() { assertThat(run(Side.BOTH)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L); }

    @Test void paternalKeepsDaughterAsLeafButNotHerChildren() {
        assertThat(run(Side.PATERNAL)).containsExactlyInAnyOrder(1L, 2L, 3L, 5L);
    }

    @Test void maternalKeepsChildrenOfWomenAndTheirAncestorsOnly() {
        assertThat(run(Side.MATERNAL)).containsExactlyInAnyOrder(1L, 2L, 4L);
    }

    @Test void parse() {
        assertThat(Side.parse(null)).isEqualTo(Side.BOTH);
        assertThat(Side.parse("paternal")).isEqualTo(Side.PATERNAL);
        assertThat(Side.parse("MATERNAL")).isEqualTo(Side.MATERNAL);
        assertThatThrownBy(() -> Side.parse("abc")).isInstanceOf(ApiException.class);
    }

    @Test void cyclicBadDataTerminates() {
        var kids = Map.of(1L, List.of(2L), 2L, List.of(1L));
        assertThat(SideFilter.apply(Side.BOTH, 1L, id -> kids.getOrDefault(id, List.of()), id -> Gender.M)).hasSize(2);
    }
}
