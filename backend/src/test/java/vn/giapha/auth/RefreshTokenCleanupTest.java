package vn.giapha.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import vn.giapha.PostgresTestConfig;
import vn.giapha.support.TestUsers;

@SpringBootTest
@Import({PostgresTestConfig.class, TestUsers.class})
class RefreshTokenCleanupTest {
    @Autowired RefreshTokenCleanup cleanup;
    @Autowired RefreshTokenRepository tokens;
    @Autowired TestUsers tu;

    RefreshToken token(long userId, Instant expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setTokenHash(UUID.randomUUID().toString());
        t.setExpiresAt(expiresAt);
        return tokens.save(t);
    }

    @Test
    void deletesOnlyTokensExpiredMoreThanOneDayAgo() {
        long uid = tu.create(Role.VIEWER).getId();
        RefreshToken expired = token(uid, Instant.now().minus(Duration.ofDays(2)));
        RefreshToken justExpired = token(uid, Instant.now().minus(Duration.ofHours(1)));
        RefreshToken valid = token(uid, Instant.now().plus(Duration.ofDays(7)));

        cleanup.cleanup();

        assertThat(tokens.findById(expired.getId())).isEmpty();
        assertThat(tokens.findById(justExpired.getId())).isPresent();
        assertThat(tokens.findById(valid.getId())).isPresent();
    }
}
