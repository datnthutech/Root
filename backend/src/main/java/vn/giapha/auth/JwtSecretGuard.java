package vn.giapha.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Từ chối khởi động production (cookie-secure=true) khi còn dùng JWT secret mặc định trong application.yml. */
@Component
public class JwtSecretGuard {
    static final String DEFAULT_SECRET = "change-me-change-me-change-me-change-me-32b";

    public JwtSecretGuard(@Value("${app.jwt.secret}") String secret, @Value("${app.cookie-secure}") boolean secure) {
        check(secret, secure);
    }

    static void check(String secret, boolean secure) {
        if (secure && DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException("JWT_SECRET đang dùng giá trị mặc định; đặt JWT_SECRET riêng khi COOKIE_SECURE=true");
        }
    }
}
