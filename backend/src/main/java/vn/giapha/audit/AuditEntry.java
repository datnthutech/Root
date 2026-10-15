package vn.giapha.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record AuditEntry(long id, Long userId, String username, String displayName, String entityType, long entityId, String action,
                         JsonNode before, JsonNode after, Instant createdAt, String summary, List<AuditSummary.Change> changes,
                         boolean revertible) {}
