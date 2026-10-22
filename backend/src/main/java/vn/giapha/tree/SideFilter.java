package vn.giapha.tree;

import java.util.*;
import java.util.function.Function;
import vn.giapha.person.Gender;

public final class SideFilter {
    private SideFilter() {}

    public static Set<Long> apply(Side side, long rootId, Function<Long, List<Long>> childrenOf, Function<Long, Gender> genderOf) {
        Map<Long, Boolean> viaFemale = new LinkedHashMap<>();
        Map<Long, Long> parentOf = new HashMap<>();
        Map<Long, List<Long>> kids = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        viaFemale.put(rootId, false);
        queue.add(rootId);
        while (!queue.isEmpty()) {
            long p = queue.poll();
            for (long c : childrenOf.apply(p)) {
                if (viaFemale.containsKey(c)) continue;
                viaFemale.put(c, viaFemale.get(p) || genderOf.apply(p) == Gender.F);
                parentOf.put(c, p);
                kids.computeIfAbsent(p, k -> new ArrayList<>()).add(c);
                queue.add(c);
            }
        }
        switch (side) {
            case BOTH:
                return new LinkedHashSet<>(viaFemale.keySet());
            case PATERNAL: {
                Set<Long> out = new LinkedHashSet<>();
                Deque<Long> stack = new ArrayDeque<>();
                stack.push(rootId);
                while (!stack.isEmpty()) {
                    long p = stack.pop();
                    out.add(p);
                    if (p == rootId || genderOf.apply(p) != Gender.F) {
                        for (long c : kids.getOrDefault(p, List.of())) stack.push(c);
                    }
                }
                return out;
            }
            default: {
                Set<Long> out = new LinkedHashSet<>();
                List<Long> order = new ArrayList<>(viaFemale.keySet());
                Collections.reverse(order); // con trước cha mẹ
                for (long p : order) {
                    if (viaFemale.get(p) || out.contains(p)) {
                        out.add(p);
                        Long parent = parentOf.get(p);
                        if (parent != null) out.add(parent);
                    }
                }
                return out;
            }
        }
    }
}
