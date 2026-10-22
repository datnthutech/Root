package vn.giapha.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;

@Service
public class AuthService {
    static final int MAX_FAILED = 5;
    static final Duration LOCK_FOR = Duration.ofMinutes(15);

    public record UserView(long id, String username, String displayName, Role role, Long linkedPersonId) {
        static UserView of(AppUser u) { return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getLinkedPersonId()); }
    }
    public record LoginResult(String accessToken, long expiresInSeconds, String refreshToken, UserView user) {}

    private final AppUserRepository users;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final long refreshDays;
    /** Băm giả để username không tồn tại cũng tốn thời gian BCrypt như tài khoản thật. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository tokens, PasswordEncoder encoder, JwtService jwt,
                       @Value("${app.jwt.refresh-days}") long refreshDays) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshDays = refreshDays;
        this.dummyHash = encoder.encode("dummy-" + java.util.UUID.randomUUID());
    }

    @Transactional
    public AppUser register(String username, String password, String displayName) {
        String name = username.toLowerCase(Locale.ROOT);
        if (users.existsByUsername(name)) throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Tên đăng nhập đã tồn tại");
        AppUser u = new AppUser();
        u.setUsername(name);
        u.setPasswordHash(encoder.encode(password));
        u.setDisplayName(displayName);
        u.setRole(Role.VIEWER);
        u.setStatus(UserStatus.PENDING);
        return users.save(u);
    }

    /** noRollbackFor: bộ đếm sai mật khẩu phải được lưu dù request trả lỗi. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String username, String password) {
        AppUser u = users.findByUsername(username.toLowerCase(Locale.ROOT)).orElse(null);
        // luôn chạy BCrypt (kể cả username không tồn tại) để không lộ username qua thời gian phản hồi
        boolean ok = encoder.matches(password, u == null ? dummyHash : u.getPasswordHash());
        if (u == null) throw badCredentials();
        Instant now = Instant.now();
        boolean tempLocked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(now);
        if (tempLocked || u.getStatus() == UserStatus.LOCKED) {
            // đang khoá: chỉ ai biết đúng mật khẩu mới thấy 423, sai mật khẩu → 401 và không tăng bộ đếm
            if (!ok) throw badCredentials();
            throw new ApiException(HttpStatus.valueOf(423), "ACCOUNT_LOCKED",
                tempLocked ? "Tài khoản tạm khoá, thử lại sau" : "Tài khoản đã bị khoá");
        }
        if (!ok) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            if (u.getFailedAttempts() >= MAX_FAILED) {
                u.setLockedUntil(now.plus(LOCK_FOR));
                u.setFailedAttempts(0);
            }
            users.save(u);
            throw badCredentials();
        }
        if (u.getStatus() == UserStatus.PENDING) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_PENDING", "Tài khoản đang chờ duyệt");
        }
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        return issue(u);
    }

    @Transactional
    public LoginResult refresh(String rawRefresh) {
        RefreshToken t = find(rawRefresh);
        if (t == null || t.isRevoked() || t.getExpiresAt().isBefore(Instant.now())) throw unauth();
        AppUser u = users.findById(t.getUserId()).orElseThrow(this::unauth);
        if (u.getStatus() != UserStatus.ACTIVE) throw unauth();
        t.setRevoked(true);
        tokens.save(t);
        return issue(u);
    }

    @Transactional
    public void logout(String rawRefresh) {
        RefreshToken t = find(rawRefresh);
        if (t != null) { t.setRevoked(true); tokens.save(t); }
    }

    /** Đổi mật khẩu của chính mình; thu hồi mọi refresh token để các phiên khác phải đăng nhập lại. */
    @Transactional
    public void changePassword(long userId, String current, String next) {
        AppUser u = users.findById(userId).orElseThrow(this::unauth);
        if (!encoder.matches(current, u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Mật khẩu hiện tại không đúng");
        }
        u.setPasswordHash(encoder.encode(next));
        users.save(u);
        tokens.revokeAllForUser(userId);
    }

    private RefreshToken find(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return tokens.findByTokenHash(TokenHasher.hash(raw)).orElse(null);
    }

    private LoginResult issue(AppUser u) {
        String raw = TokenHasher.newToken();
        RefreshToken t = new RefreshToken();
        t.setUserId(u.getId());
        t.setTokenHash(TokenHasher.hash(raw));
        t.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDays)));
        tokens.save(t);
        return new LoginResult(jwt.issue(u.getId(), u.getRole()), jwt.accessSeconds(), raw, UserView.of(u));
    }

    private ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu");
    }

    private ApiException unauth() { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Phiên đăng nhập hết hạn"); }
}
