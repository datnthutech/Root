package vn.giapha.audit;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.giapha.auth.AuthUser;
import vn.giapha.auth.Role;
import vn.giapha.common.ApiException;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditQueryService query;

    public AuditController(AuditQueryService query) { this.query = query; }

    @GetMapping
    @PreAuthorize("hasRole('EDITOR')")
    public List<AuditEntry> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long entityId,
                                 @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "30") int limit) {
        if (entityId == null) {
            if (me.role() != Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ quản trị viên xem toàn bộ nhật ký");
            return query.all(beforeId, limit);
        }
        return query.forPerson(entityId, beforeId, limit);
    }
}
