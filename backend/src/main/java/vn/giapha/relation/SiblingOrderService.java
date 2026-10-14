package vn.giapha.relation;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.giapha.clan.Clan;
import vn.giapha.clan.ClanRepository;
import vn.giapha.common.ApiException;
import vn.giapha.person.Gender;

@Service
public class SiblingOrderService {
    private final JdbcTemplate jdbc;
    private final ClanRepository clans;

    public SiblingOrderService(JdbcTemplate jdbc, ClanRepository clans) {
        this.jdbc = jdbc;
        this.clans = clans;
    }

    private record Link(long childId, long parentId, String parentGender, Long unionId, boolean counts) {}

    /** Trả về thứ tự cho mọi thành viên dòng họ có cha hoặc mẹ trong DB (người không có cha mẹ thì không có mục). */
    public Map<Long, OrderInfo> computeForClan(long clanId) {
        Clan clan = clans.findById(clanId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy dòng họ"));

        List<Link> links = jdbc.query("""
            SELECT pc.child_id, pc.parent_id, par.gender AS pg, pc.union_id, pc.counts_in_order
            FROM parent_child pc
            JOIN clan_member cm ON cm.person_id = pc.child_id AND cm.clan_id = ?
            JOIN person par ON par.id = pc.parent_id AND par.deleted_at IS NULL
            WHERE pc.kind = 'BIOLOGICAL' OR pc.counts_in_order
            """, (rs, i) -> new Link(rs.getLong(1), rs.getLong(2), rs.getString(3),
                (Long) rs.getObject(4), rs.getBoolean(5)), clanId);

        Map<Long, List<Link>> byChild = links.stream().collect(Collectors.groupingBy(Link::childId));
        List<Long> childIds = new ArrayList<>(byChild.keySet());
        if (childIds.isEmpty()) return Map.of();

        Map<Long, Object[]> childInfo = new HashMap<>();
        jdbc.query("SELECT id, gender, birth_date, birth_order_override FROM person WHERE id = ANY(?)",
            rs -> { childInfo.put(rs.getLong(1), new Object[]{Gender.valueOf(rs.getString(2)),
                rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate(), (Integer) rs.getObject(4)}); },
            (Object) childIds.toArray(Long[]::new));

        List<ChildRow> rows = new ArrayList<>();
        for (var e : byChild.entrySet()) {
            Long father = null, mother = null, union = null;
            boolean counts = true;
            for (Link l : e.getValue()) {
                if ("M".equals(l.parentGender())) father = l.parentId();
                else if ("F".equals(l.parentGender())) mother = l.parentId();
                else if (father == null) father = l.parentId();
                if (l.unionId() != null) union = l.unionId();
                counts = counts && l.counts();
            }
            Object[] info = childInfo.get(e.getKey());
            if (info == null) continue;
            rows.add(new ChildRow(e.getKey(), father, mother, union, (java.time.LocalDate) info[1],
                (Integer) info[2], counts, (Gender) info[0]));
        }

        Map<Long, OrderInfo> out = new HashMap<>();
        for (List<ChildRow> group : SiblingGrouper.group(rows, clan.getSiblingCountMode()).values()) {
            Map<Long, ChildRow> byId = group.stream().collect(Collectors.toMap(ChildRow::childId, r -> r));
            var res = SiblingOrderCalculator.compute(group.stream()
                .map(r -> new SiblingOrderCalculator.Sibling(r.childId(), r.birthDate(), r.override(), r.counts())).toList());
            int max = res.values().stream().mapToInt(SiblingOrderCalculator.Result::number).max().orElse(0);
            boolean useUt = clan.isUseUtLabel() && res.size() >= 2;
            res.forEach((id, r) -> out.put(id, new OrderInfo(r.number(),
                VietnameseOrder.label(r.number(), byId.get(id).gender(), useUt && r.number() == max),
                r.needsConfirm())));
        }
        return out;
    }
}
