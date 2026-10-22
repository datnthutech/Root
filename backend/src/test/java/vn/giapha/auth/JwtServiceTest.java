package vn.giapha.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    static final String SECRET = "unit-test-secret-unit-test-secret-32b!!";

    @Test
    void roundTrip() {
        JwtService jwt = new JwtService(SECRET, 15);
        var parsed = jwt.parse(jwt.issue(42, Role.EDITOR));
        assertThat(parsed).contains(new AuthUser(42, Role.EDITOR));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, -1);
        assertThat(jwt.parse(jwt.issue(1, Role.ADMIN))).isEmpty();
    }

    @Test
    void tamperedOrForeignTokenIsRejected() {
        JwtService a = new JwtService(SECRET, 15);
        JwtService b = new JwtService("another-secret-another-secret-32b!!!!", 15);
        assertThat(a.parse(b.issue(1, Role.ADMIN))).isEmpty();
        assertThat(a.parse("garbage")).isEmpty();
        assertThat(a.parse("")).isEmpty();
    }
}
