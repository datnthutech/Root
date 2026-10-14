package vn.giapha.relation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CycleChecker {
    private final JdbcTemplate jdbc;

    public CycleChecker(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** true nếu candidateId là hậu duệ (con, cháu, ...) của ancestorId. */
    public boolean isDescendant(long ancestorId, long candidateId) {
        Boolean r = jdbc.queryForObject("""
            WITH RECURSIVE d(id) AS (
              SELECT child_id FROM parent_child WHERE parent_id = ?
              UNION
              SELECT pc.child_id FROM parent_child pc JOIN d ON pc.parent_id = d.id
            ) SELECT EXISTS (SELECT 1 FROM d WHERE id = ?)
            """, Boolean.class, ancestorId, candidateId);
        return Boolean.TRUE.equals(r);
    }
}
