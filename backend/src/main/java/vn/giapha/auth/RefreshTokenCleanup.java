package vn.giapha.auth;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Dọn refresh token đã hết hạn (chừa 1 ngày) để bảng không phình vô hạn. */
@Component
public class RefreshTokenCleanup {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository tokens;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void cleanup() {
        int n = tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (n > 0) log.info("Đã xoá {} refresh token hết hạn", n);
    }
}
