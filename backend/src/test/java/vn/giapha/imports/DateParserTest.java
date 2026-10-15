package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.giapha.person.DatePrecision;

class DateParserTest {
    @Test void forms() {
        assertThat(DateParser.parse("1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 1, 1), DatePrecision.YEAR));
        assertThat(DateParser.parse("3/1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 1), DatePrecision.MONTH));
        assertThat(DateParser.parse("03/1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 1), DatePrecision.MONTH));
        assertThat(DateParser.parse("5/3/1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 5), DatePrecision.DAY));
        assertThat(DateParser.parse("05/03/1950")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 5), DatePrecision.DAY));
        assertThat(DateParser.parse("1950-03-05")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 3, 5), DatePrecision.DAY));
        assertThat(DateParser.parse(" 1950 ")).isEqualTo(new DateParser.Parsed(LocalDate.of(1950, 1, 1), DatePrecision.YEAR));
    }
    @Test void blankIsNull() {
        assertThat(DateParser.parse("")).isNull();
        assertThat(DateParser.parse("   ")).isNull();
        assertThat(DateParser.parse(null)).isNull();
    }
    @Test void invalidThrows() {
        for (String bad : new String[]{"31/02/1950", "13/1950", "abc", "1950/03/05", "0/0/0", "99", "3000", "12345"}) {
            assertThatThrownBy(() -> DateParser.parse(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
