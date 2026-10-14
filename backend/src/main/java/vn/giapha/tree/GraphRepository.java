package vn.giapha.tree;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.relation.UnionStatus;

@Repository
public class GraphRepository {
    public record PersonRow(long id, String name, Gender gender, Integer birthYear, Integer deathYear,
                            boolean alive, String photoKey) {}

    private final JdbcTemplate jdbc;

    public GraphRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static Long[] arr(Collection<Long> ids) { return ids.toArray(Long[]::new); }

    public List<PersonRow> persons(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT id, full_name, gender,
                   CASE WHEN birth_precision <> 'UNKNOWN' THEN EXTRACT(YEAR FROM birth_date)::int END AS by,
                   CASE WHEN death_precision <> 'UNKNOWN' THEN EXTRACT(YEAR FROM death_date)::int END AS dy,
                   is_alive, photo_key
            FROM person WHERE deleted_at IS NULL AND id = ANY(?) ORDER BY id
            """, (rs, i) -> new PersonRow(rs.getLong(1), rs.getString(2), Gender.valueOf(rs.getString(3)),
                (Integer) rs.getObject(4), (Integer) rs.getObject(5), rs.getBoolean(6), rs.getString(7)), (Object) arr(ids));
    }

    public List<GraphDtos.Union> unionsAmong(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT id, partner_a_id, partner_b_id, union_order, status FROM union_rel
            WHERE partner_a_id = ANY(?) AND partner_b_id = ANY(?) ORDER BY id
            """, (rs, i) -> new GraphDtos.Union(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4),
                UnionStatus.valueOf(rs.getString(5))), (Object) arr(ids), (Object) arr(ids));
    }

    public List<GraphDtos.Link> linksAmong(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT parent_id, child_id, union_id, kind FROM parent_child
            WHERE parent_id = ANY(?) AND child_id = ANY(?) ORDER BY id
            """, (rs, i) -> new GraphDtos.Link(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3),
                ParentKind.valueOf(rs.getString(4))), (Object) arr(ids), (Object) arr(ids));
    }
}
