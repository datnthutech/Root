package vn.giapha.person;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.relation.OrderInfo;
import vn.giapha.relation.SiblingOrderService;
import vn.giapha.relation.UnionStatus;
import vn.giapha.person.SiblingClassifier.Kind;
import vn.giapha.person.SiblingClassifier.Parent;

@Service
@Transactional(readOnly = true)
public class FamilyService {
    public record PersonRef(long id, String name, Gender gender, Integer birthYear, Integer deathYear, boolean alive,
                            String orderLabel, Long linkId) {}
    public record SpouseFamily(long unionId, int unionOrder, UnionStatus status, LocalDate startDate, LocalDate endDate,
                               PersonRef partner, List<PersonRef> children) {}
    public record FamilyResponse(long personId, List<PersonRef> parents, List<SpouseFamily> spouses, List<PersonRef> otherChildren,
                                 List<PersonRef> fullSiblings, List<PersonRef> halfSiblingsSameFather,
                                 List<PersonRef> halfSiblingsSameMother) {}

    private final JdbcTemplate jdbc;
    private final PersonRepository persons;
    private final SiblingOrderService orders;

    public FamilyService(JdbcTemplate jdbc, PersonRepository persons, SiblingOrderService orders) {
        this.jdbc = jdbc;
        this.persons = persons;
        this.orders = orders;
    }

    private record Row(long id, String name, Gender gender, Integer by, Integer dy, boolean alive) {}

    private Map<Long, Row> load(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Row> m = new HashMap<>();
        jdbc.query("""
            SELECT id, full_name, gender,
              CASE WHEN birth_precision <> 'UNKNOWN' THEN EXTRACT(YEAR FROM birth_date)::int END,
              CASE WHEN death_precision <> 'UNKNOWN' THEN EXTRACT(YEAR FROM death_date)::int END, is_alive
            FROM person WHERE deleted_at IS NULL AND id = ANY(?)""",
            rs -> { m.put(rs.getLong(1), new Row(rs.getLong(1), rs.getString(2), Gender.valueOf(rs.getString(3)),
                (Integer) rs.getObject(4), (Integer) rs.getObject(5), rs.getBoolean(6))); }, (Object) ids.toArray(Long[]::new));
        return m;
    }

    public FamilyResponse family(long personId) {
        persons.findByIdAndDeletedAtIsNull(personId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy người này"));

        List<Long> clan = jdbc.queryForList("SELECT clan_id FROM clan_member WHERE person_id = ? ORDER BY clan_id LIMIT 1", Long.class, personId);
        Map<Long, OrderInfo> orderInfo = clan.isEmpty() ? Map.of() : orders.computeForClan(clan.get(0));

        Map<Long, Long> parentLinkId = new LinkedHashMap<>();
        jdbc.query("SELECT parent_id, id FROM parent_child WHERE child_id = ? ORDER BY id",
            rs -> { parentLinkId.put(rs.getLong(1), rs.getLong(2)); }, personId);
        List<Long> parentIds = new ArrayList<>(parentLinkId.keySet());

        record ChildLink(long childId, Long unionId, long linkId) {}
        List<ChildLink> childLinks = jdbc.query("SELECT child_id, union_id, id FROM parent_child WHERE parent_id = ? ORDER BY id",
            (rs, i) -> new ChildLink(rs.getLong(1), (Long) rs.getObject(2), rs.getLong(3)), personId);
        Map<Long, Long> childLinkId = new HashMap<>();
        childLinks.forEach(c -> childLinkId.put(c.childId(), c.linkId()));

        record UnionRow(long id, long partnerId, int order, UnionStatus status, LocalDate start, LocalDate end) {}
        List<UnionRow> unionRows = jdbc.query("""
            SELECT id, CASE WHEN partner_a_id = ? THEN partner_b_id ELSE partner_a_id END, union_order, status, start_date, end_date
            FROM union_rel WHERE partner_a_id = ? OR partner_b_id = ? ORDER BY union_order, id""",
            (rs, i) -> new UnionRow(rs.getLong(1), rs.getLong(2), rs.getInt(3), UnionStatus.valueOf(rs.getString(4)),
                rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate(), rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate()),
            personId, personId, personId);

        record SibLink(long childId, long parentId) {}
        List<SibLink> sibLinks = parentIds.isEmpty() ? List.of() : jdbc.query(
            "SELECT child_id, parent_id FROM parent_child WHERE parent_id = ANY(?) AND child_id <> ?",
            (rs, i) -> new SibLink(rs.getLong(1), rs.getLong(2)), (Object) parentIds.toArray(Long[]::new), personId);
        Set<Long> sibIds = sibLinks.stream().map(SibLink::childId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<Long>> parentsOfSib = new HashMap<>();
        if (!sibIds.isEmpty()) {
            jdbc.query("SELECT child_id, parent_id FROM parent_child WHERE child_id = ANY(?)",
                rs -> { parentsOfSib.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)); },
                (Object) sibIds.toArray(Long[]::new));
        }

        Set<Long> need = new LinkedHashSet<>(parentIds);
        unionRows.forEach(u -> need.add(u.partnerId()));
        childLinks.forEach(c -> need.add(c.childId()));
        need.addAll(sibIds);
        parentsOfSib.values().forEach(need::addAll);
        Map<Long, Row> rows = load(need);

        java.util.function.BiFunction<Long, Map<Long, Long>, PersonRef> ref = (id, links) -> {
            Row r = rows.get(id);
            if (r == null) return null;
            OrderInfo o = orderInfo.get(id);
            return new PersonRef(r.id(), r.name(), r.gender(), r.by(), r.dy(), r.alive(), o == null ? null : o.label(), links.get(id));
        };
        java.util.function.BiFunction<Collection<Long>, Map<Long, Long>, List<PersonRef>> refs =
            (ids, links) -> ids.stream().map(id -> ref.apply(id, links)).filter(Objects::nonNull).toList();
        Map<Long, Long> none = Map.of();

        Set<Parent> mine = parentIds.stream().filter(rows::containsKey).map(id -> new Parent(id, rows.get(id).gender())).collect(Collectors.toSet());
        List<Long> full = new ArrayList<>(), halfF = new ArrayList<>(), halfM = new ArrayList<>();
        for (long sid : sibIds) {
            if (!rows.containsKey(sid)) continue;
            Set<Parent> theirs = parentsOfSib.getOrDefault(sid, List.of()).stream().filter(rows::containsKey)
                .map(id -> new Parent(id, rows.get(id).gender())).collect(Collectors.toSet());
            Kind k = SiblingClassifier.classify(mine, theirs);
            (k == Kind.FULL ? full : k == Kind.HALF_SAME_FATHER ? halfF : halfM).add(sid);
        }

        Set<Long> unionIds = new HashSet<>();
        List<SpouseFamily> spouses = new ArrayList<>();
        for (UnionRow u : unionRows) {
            PersonRef partner = ref.apply(u.partnerId(), none);
            if (partner == null) continue;
            List<Long> kids = childLinks.stream().filter(c -> Objects.equals(c.unionId(), u.id())).map(ChildLink::childId).toList();
            unionIds.add(u.id());
            spouses.add(new SpouseFamily(u.id(), u.order(), u.status(), u.start(), u.end(), partner, refs.apply(kids, childLinkId)));
        }
        List<Long> other = childLinks.stream().filter(c -> c.unionId() == null || !unionIds.contains(c.unionId()))
            .map(ChildLink::childId).toList();
        return new FamilyResponse(personId, refs.apply(parentIds, parentLinkId), spouses, refs.apply(other, childLinkId),
            refs.apply(full, none), refs.apply(halfF, none), refs.apply(halfM, none));
    }
}
