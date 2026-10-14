package vn.giapha.relation;

import java.time.LocalDate;
import java.util.*;

public final class SiblingOrderCalculator {
    public record Sibling(long id, LocalDate birthDate, Integer override, boolean counts) {}
    public record Result(int number, boolean needsConfirm) {}

    private SiblingOrderCalculator() {}

    public static Map<Long, Result> compute(List<Sibling> siblings) {
        Map<Long, Result> out = new LinkedHashMap<>();
        List<Sibling> counted = siblings.stream().filter(Sibling::counts).toList();

        Set<Integer> taken = new HashSet<>();
        for (Sibling s : counted) {
            if (s.override() != null) { taken.add(s.override()); out.put(s.id(), new Result(s.override(), false)); }
        }

        List<Sibling> auto = counted.stream().filter(s -> s.override() == null)
            .sorted(Comparator.comparing(Sibling::birthDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(Sibling::id))
            .toList();

        int next = 2;
        for (int i = 0; i < auto.size(); i++) {
            Sibling s = auto.get(i);
            while (taken.contains(next)) next++;
            boolean tie = s.birthDate() != null && (
                (i > 0 && s.birthDate().equals(auto.get(i - 1).birthDate())) ||
                (i < auto.size() - 1 && s.birthDate().equals(auto.get(i + 1).birthDate())));
            out.put(s.id(), new Result(next, s.birthDate() == null || tie));
            next++;
        }
        return out;
    }
}
