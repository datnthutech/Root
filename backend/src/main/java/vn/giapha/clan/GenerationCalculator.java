package vn.giapha.clan;

import java.util.*;
import java.util.function.Function;

public final class GenerationCalculator {
    public record Gen(int generation, Membership membership) {}

    private GenerationCalculator() {}

    public static Map<Long, Gen> compute(long rootId, Function<Long, List<Long>> childrenOf,
                                         Function<Long, List<Long>> spousesOf) {
        Map<Long, Gen> out = new LinkedHashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        out.put(rootId, new Gen(1, Membership.BLOOD));
        queue.add(rootId);
        while (!queue.isEmpty()) {
            long p = queue.poll();
            Gen g = out.get(p);
            for (long s : spousesOf.apply(p)) out.putIfAbsent(s, new Gen(g.generation(), Membership.IN_LAW));
            for (long c : childrenOf.apply(p)) {
                if (!out.containsKey(c)) { out.put(c, new Gen(g.generation() + 1, Membership.BLOOD)); queue.add(c); }
            }
        }
        return out;
    }
}
