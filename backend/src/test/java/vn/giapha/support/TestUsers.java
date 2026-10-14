package vn.giapha.support;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import vn.giapha.auth.*;

@TestComponent
public class TestUsers {
    @Autowired AppUserRepository users;
    @Autowired JwtService jwt;

    public AppUser create(Role role) {
        AppUser u = new AppUser();
        u.setUsername("t" + UUID.randomUUID().toString().substring(0, 12));
        u.setPasswordHash("x");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return users.save(u);
    }

    public String bearer(Role role) {
        AppUser u = create(role);
        return "Bearer " + jwt.issue(u.getId(), role);
    }

    public String bearerFor(AppUser u) { return "Bearer " + jwt.issue(u.getId(), u.getRole()); }
}
