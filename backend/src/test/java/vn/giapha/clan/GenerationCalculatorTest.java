package vn.giapha.clan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenerationCalculatorTest {
    @Test
    void rootIsGenerationOneChildrenPlusOneSpousesSameGenerationInLaw() {
        Map<Long, List<Long>> kids = Map.of(1L, List.of(3L), 3L, List.of(5L));
        Map<Long, List<Long>> spouses = Map.of(1L, List.of(2L), 3L, List.of(4L));
        var r = GenerationCalculator.compute(1L,
            id -> kids.getOrDefault(id, List.of()), id -> spouses.getOrDefault(id, List.of()));
        assertThat(r.get(1L)).isEqualTo(new GenerationCalculator.Gen(1, Membership.BLOOD));
        assertThat(r.get(2L)).isEqualTo(new GenerationCalculator.Gen(1, Membership.IN_LAW));
        assertThat(r.get(3L)).isEqualTo(new GenerationCalculator.Gen(2, Membership.BLOOD));
        assertThat(r.get(4L)).isEqualTo(new GenerationCalculator.Gen(2, Membership.IN_LAW));
        assertThat(r.get(5L)).isEqualTo(new GenerationCalculator.Gen(3, Membership.BLOOD));
    }

    @Test
    void terminatesOnCyclicBadData() {
        var r = GenerationCalculator.compute(1L, id -> id == 1L ? List.of(2L) : List.of(1L), id -> List.of());
        assertThat(r).hasSize(2);
    }
}
