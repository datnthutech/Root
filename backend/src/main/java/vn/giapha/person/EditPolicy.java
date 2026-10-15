package vn.giapha.person;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.giapha.auth.AuthUser;
import vn.giapha.auth.Role;
import vn.giapha.common.ApiException;

@Component
public class EditPolicy {
    static final Duration WINDOW = Duration.ofHours(24);
    private final Clock clock;

    public EditPolicy(Clock clock) { this.clock = clock; }

    public boolean canEdit(AuthUser me, Person p) {
        if (me.role() == Role.ADMIN) return true;
        if (me.role() != Role.EDITOR) return false;
        return isOwner(me, p) && inWindow(p);
    }

    public void assertCanEdit(AuthUser me, Person p) {
        if (me.role() == Role.ADMIN) return;
        if (me.role() != Role.EDITOR || !isOwner(me, p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ được sửa người do mình tạo");
        }
        if (!inWindow(p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EDIT_WINDOW_EXPIRED", "Đã quá 24 giờ, chỉ quản trị viên được sửa");
        }
    }

    private boolean isOwner(AuthUser me, Person p) { return p.getCreatedBy() != null && p.getCreatedBy() == me.id(); }

    private boolean inWindow(Person p) {
        Instant created = p.getCreatedAt();
        return created != null && !Instant.now(clock).isAfter(created.plus(WINDOW));
    }
}
