package vn.giapha.tree;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.clan.*;
import vn.giapha.common.ApiException;
import vn.giapha.person.Gender;
import vn.giapha.person.PersonRepository;
import vn.giapha.relation.OrderInfo;
import vn.giapha.relation.SiblingOrderService;

@Service
@Transactional(readOnly = true)
public class FocusService {
    private final JdbcTemplate jdbc;
    private final PersonRepository persons;
    private final GraphRepository repo;
    private final GraphService graphs;
    private final ClanRepository clans;
    private final ClanMemberRepository members;
    private final SiblingOrderService orders;

    public FocusService(JdbcTemplate jdbc, PersonRepository persons, GraphRepository repo, GraphService graphs,
                        ClanRepository clans, ClanMemberRepository members, SiblingOrderService orders) {
        this.jdbc = jdbc;
        this.persons = persons;
        this.repo = repo;
        this.graphs = graphs;
        this.clans = clans;
        this.members = members;
        this.orders = orders;
    }

    private List<long[]> parentsOf(long childId) { // {parentId, gender(M=0,F=1,U=2)}
        return jdbc.query("""
            SELECT pc.parent_id, p.gender FROM parent_child pc JOIN person p ON p.id = pc.parent_id AND p.deleted_at IS NULL
            WHERE pc.child_id = ? ORDER BY pc.id""",
            (rs, i) -> new long[]{rs.getLong(1), "M".equals(rs.getString(2)) ? 0 : "F".equals(rs.getString(2)) ? 1 : 2}, childId);
    }

    private List<Long> childrenOf(long parentId) {
        return jdbc.queryForList("""
            SELECT pc.child_id FROM parent_child pc JOIN person p ON p.id = pc.child_id AND p.deleted_at IS NULL
            WHERE pc.parent_id = ? ORDER BY pc.id""", Long.class, parentId);
    }

    private List<Long> partnersOf(long personId) {
        return jdbc.queryForList("""
            SELECT CASE WHEN partner_a_id = ? THEN partner_b_id ELSE partner_a_id END
            FROM union_rel WHERE partner_a_id = ? OR partner_b_id = ?""", Long.class, personId, personId, personId);
    }

    public GraphDtos.Graph focus(long personId, int up, int down) {
        persons.findByIdAndDeletedAtIsNull(personId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy người này"));
        int u = Math.max(0, Math.min(10, up)), d = Math.max(0, Math.min(10, down));

        Set<Long> keep = new LinkedHashSet<>();
        keep.add(personId);

        long top = personId;
        Set<Long> parentsOfSelf = new LinkedHashSet<>();
        for (long[] p : parentsOf(personId)) parentsOfSelf.add(p[0]);
        for (int i = 0; i < u; i++) {
            List<long[]> ps = parentsOf(top);
            if (ps.isEmpty()) break;
            long[] chosen = ps.stream().filter(x -> x[1] == 0).findFirst().orElse(ps.get(0));
            top = chosen[0];
            keep.add(top);
        }
        // hậu duệ
        Deque<long[]> q = new ArrayDeque<>();
        q.add(new long[]{personId, 0});
        while (!q.isEmpty()) {
            long[] cur = q.poll();
            if (cur[1] >= d) continue;
            for (long c : childrenOf(cur[0])) if (keep.add(c)) q.add(new long[]{c, cur[1] + 1});
        }
        // anh chị em
        for (long parent : parentsOfSelf) for (long c : childrenOf(parent)) keep.add(c);
        // vợ/chồng của mọi người đang có (một lượt, không lan tiếp)
        for (long p : new ArrayList<>(keep)) keep.addAll(partnersOf(p));

        List<ClanMember> mine = jdbc.queryForList("SELECT DISTINCT clan_id FROM clan_member WHERE person_id = ? ORDER BY clan_id", Long.class, personId)
            .stream().findFirst().map(cid -> members.findByClanId(cid)).orElse(List.of());
        long clanId = mine.isEmpty() ? 0 : mine.get(0).getClanId();
        Map<Long, ClanMember> byPerson = mine.stream().collect(Collectors.toMap(ClanMember::getPersonId, m -> m));
        Map<Long, OrderInfo> orderInfo = clanId == 0 ? Map.of() : orders.computeForClan(clanId);
        SiblingCountMode mode = clanId == 0 ? SiblingCountMode.PER_UNION
            : clans.findById(clanId).map(Clan::getSiblingCountMode).orElse(SiblingCountMode.PER_UNION);
        return graphs.assemble(clanId, top, mode, keep, byPerson, orderInfo);
    }
}
