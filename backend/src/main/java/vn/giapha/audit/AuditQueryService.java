package vn.giapha.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {
    private static final String SELECT = """
        SELECT a.id, a.user_id, u.username, u.display_name, a.entity_type, a.entity_id, a.action,
               a.before_data::text, a.after_data::text, a.created_at
        FROM audit_log a LEFT JOIN app_user u ON u.id = a.user_id
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.om = om;
    }

    private record Raw(long id, Long userId, String username, String displayName, String entityType, long entityId, String action,
                       JsonNode before, JsonNode after, java.time.Instant createdAt) {}

    public List<AuditEntry> forPerson(long personId, Long beforeId, int limit) {
        String id = String.valueOf(personId);
        List<Raw> rows = jdbc.query(SELECT + """
            WHERE (?::bigint IS NULL OR a.id < ?::bigint)
              AND ((a.entity_type = 'PERSON' AND a.entity_id = ?)
                OR (a.entity_type = 'PARENT_CHILD' AND (COALESCE(a.after_data, a.before_data) ->> 'parentId' = ? OR COALESCE(a.after_data, a.before_data) ->> 'childId' = ?))
                OR (a.entity_type = 'UNION' AND (COALESCE(a.after_data, a.before_data) ->> 'partnerAId' = ? OR COALESCE(a.after_data, a.before_data) ->> 'partnerBId' = ?)))
            ORDER BY a.id DESC LIMIT ?
            """, this::raw, beforeId, beforeId, personId, id, id, id, id, clamp(limit));
        return enrich(rows);
    }

    public List<AuditEntry> all(Long beforeId, int limit) {
        List<Raw> rows = jdbc.query(SELECT + " WHERE (?::bigint IS NULL OR a.id < ?::bigint) ORDER BY a.id DESC LIMIT ?",
            this::raw, beforeId, beforeId, clamp(limit));
        return enrich(rows);
    }

    private static int clamp(int limit) { return Math.max(1, Math.min(100, limit)); }

    private Raw raw(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Raw(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6),
            rs.getString(7), parse(rs.getString(8)), parse(rs.getString(9)), rs.getTimestamp(10).toInstant());
    }

    private JsonNode parse(String s) {
        if (s == null) return null;
        try { return om.readTree(s); } catch (JsonProcessingException e) { return null; }
    }

    private List<AuditEntry> enrich(List<Raw> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Raw r : rows) {
            JsonNode n = r.after() != null ? r.after() : r.before();
            if (n == null) continue;
            for (String f : new String[]{"parentId", "childId", "partnerAId", "partnerBId"}) if (n.hasNonNull(f)) ids.add(n.get(f).asLong());
        }
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            jdbc.query("SELECT id, full_name FROM person WHERE id = ANY(?)", rs -> { names.put(rs.getLong(1), rs.getString(2)); },
                (Object) ids.toArray(Long[]::new));
        }
        List<AuditEntry> out = new ArrayList<>();
        for (Raw r : rows) {
            out.add(new AuditEntry(r.id(), r.userId(), r.username(), r.displayName(), r.entityType(), r.entityId(), r.action(),
                r.before(), r.after(), r.createdAt(),
                AuditSummary.summary(r.entityType(), r.action(), r.before(), r.after(), names::get),
                AuditSummary.changes(r.entityType(), r.before(), r.after()),
                AuditSummary.revertible(r.entityType(), r.action())));
        }
        return out;
    }
}
