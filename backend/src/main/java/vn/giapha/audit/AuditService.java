package vn.giapha.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public AuditService(JdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.om = om;
    }

    public void record(Long userId, String entityType, long entityId, String action, Object before, Object after) {
        jdbc.update("INSERT INTO audit_log(user_id, entity_type, entity_id, action, before_data, after_data) VALUES (?,?,?,?,?::jsonb,?::jsonb)",
            userId, entityType, entityId, action, json(before), json(after));
    }

    private String json(Object o) {
        if (o == null) return null;
        try { return om.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
