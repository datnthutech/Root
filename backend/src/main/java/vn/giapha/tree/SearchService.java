package vn.giapha.tree;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.giapha.common.ApiException;
import vn.giapha.person.Gender;

@Service
public class SearchService {
    public record Hit(long id, String name, Gender gender, Integer birthYear, Integer deathYear, boolean alive, Integer generation) {}

    private final JdbcTemplate jdbc;

    public SearchService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    static String escapeLike(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public List<Hit> search(String q, int limit) {
        if (q == null || q.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thiếu từ khoá tìm kiếm");
        int lim = Math.max(1, Math.min(50, limit));
        String pattern = "%" + escapeLike(q.trim()) + "%";
        return jdbc.query("""
            SELECT p.id, p.full_name, p.gender,
                   CASE WHEN p.birth_precision <> 'UNKNOWN' THEN EXTRACT(YEAR FROM p.birth_date)::int END,
                   CASE WHEN p.death_precision <> 'UNKNOWN' THEN EXTRACT(YEAR FROM p.death_date)::int END,
                   p.is_alive,
                   (SELECT MIN(cm.generation) FROM clan_member cm WHERE cm.person_id = p.id)
            FROM person p
            WHERE p.deleted_at IS NULL
              AND lower(f_unaccent(p.full_name)) LIKE lower(f_unaccent(?)) ESCAPE '\\'
            ORDER BY position(lower(f_unaccent(?)) IN lower(f_unaccent(p.full_name))), p.full_name
            LIMIT ?
            """, (rs, i) -> new Hit(rs.getLong(1), rs.getString(2), Gender.valueOf(rs.getString(3)),
                (Integer) rs.getObject(4), (Integer) rs.getObject(5), rs.getBoolean(6), (Integer) rs.getObject(7)),
            pattern, q.trim(), lim);
    }
}
