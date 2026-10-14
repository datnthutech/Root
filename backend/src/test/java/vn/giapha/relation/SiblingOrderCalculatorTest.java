package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.giapha.relation.SiblingOrderCalculator.Sibling;

class SiblingOrderCalculatorTest {
    static LocalDate d(int y) { return LocalDate.of(y, 1, 1); }

    @Test
    void firstChildIsTwoByBirthDate() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(3, d(1955), null, true), new Sibling(1, d(1950), null, true), new Sibling(2, d(1952), null, true)));
        assertThat(r.get(1L).number()).isEqualTo(2);
        assertThat(r.get(2L).number()).isEqualTo(3);
        assertThat(r.get(3L).number()).isEqualTo(4);
        assertThat(r.get(1L).needsConfirm()).isFalse();
    }

    @Test
    void unknownBirthDateGoesLastAndNeedsConfirm() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(1, null, null, true), new Sibling(2, d(1950), null, true)));
        assertThat(r.get(2L).number()).isEqualTo(2);
        assertThat(r.get(1L).number()).isEqualTo(3);
        assertThat(r.get(1L).needsConfirm()).isTrue();
    }

    @Test
    void twinsKeepStableOrderByIdAndNeedConfirm() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(9, d(1950), null, true), new Sibling(4, d(1950), null, true)));
        assertThat(r.get(4L).number()).isEqualTo(2);
        assertThat(r.get(9L).number()).isEqualTo(3);
        assertThat(r.get(4L).needsConfirm()).isTrue();
        assertThat(r.get(9L).needsConfirm()).isTrue();
    }

    @Test
    void overrideTakesExactNumberAndOthersFillGaps() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(1, d(1950), null, true), new Sibling(2, d(1952), null, true), new Sibling(3, d(1960), 2, true)));
        assertThat(r.get(3L).number()).isEqualTo(2);
        assertThat(r.get(3L).needsConfirm()).isFalse();
        assertThat(r.get(1L).number()).isEqualTo(3);
        assertThat(r.get(2L).number()).isEqualTo(4);
    }

    @Test
    void childNotCountedIsAbsentAndDoesNotTakeANumber() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(1, d(1950), null, true), new Sibling(2, d(1951), null, false), new Sibling(3, d(1952), null, true)));
        assertThat(r).doesNotContainKey(2L);
        assertThat(r.get(3L).number()).isEqualTo(3);
    }

    @Test
    void emptyListGivesEmptyMap() {
        assertThat(SiblingOrderCalculator.compute(List.of())).isEmpty();
    }
}
