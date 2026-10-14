package vn.giapha.relation;

import java.util.*;
import vn.giapha.clan.SiblingCountMode;

public final class SiblingGrouper {
    private SiblingGrouper() {}

    public static Map<String, List<ChildRow>> group(List<ChildRow> rows, SiblingCountMode mode) {
        Map<String, List<ChildRow>> out = new LinkedHashMap<>();
        for (ChildRow r : rows) out.computeIfAbsent(key(r, mode), k -> new ArrayList<>()).add(r);
        return out;
    }

    private static String key(ChildRow r, SiblingCountMode mode) {
        if (mode == SiblingCountMode.PER_FATHER && r.fatherId() != null) return "f" + r.fatherId();
        if (r.unionId() != null) return "u" + r.unionId();
        return "p" + r.fatherId() + "-" + r.motherId();
    }
}
