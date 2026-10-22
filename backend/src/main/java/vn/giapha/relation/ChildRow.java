package vn.giapha.relation;

import java.time.LocalDate;
import vn.giapha.person.Gender;

public record ChildRow(long childId, Long fatherId, Long motherId, Long unionId, LocalDate birthDate,
                       Integer override, boolean counts, Gender gender) {}
