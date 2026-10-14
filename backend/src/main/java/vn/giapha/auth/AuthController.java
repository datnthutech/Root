package vn.giapha.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    static final String COOKIE = "refresh_token";

    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[a-zA-Z0-9._-]+") String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 120) String displayName) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegisterResponse(long id, UserStatus status) {}
    public record TokenResponse(String accessToken, long expiresInSeconds, AuthService.UserView user) {}

    private final AuthService auth;
    private final boolean secure;
    private final long refreshDays;

    public AuthController(AuthService auth, @Value("${app.cookie-secure}") boolean secure,
                          @Value("${app.jwt.refresh-days}") long refreshDays) {
        this.auth = auth;
        this.secure = secure;
        this.refreshDays = refreshDays;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest r) {
        AppUser u = auth.register(r.username(), r.password(), r.displayName());
        return new RegisterResponse(u.getId(), u.getStatus());
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest r) {
        return withCookie(auth.login(r.username(), r.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = COOKIE, required = false) String raw) {
        return withCookie(auth.refresh(raw));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String raw) {
        auth.logout(raw);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    private ResponseEntity<TokenResponse> withCookie(AuthService.LoginResult r) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie(r.refreshToken(), Duration.ofDays(refreshDays)).toString())
            .body(new TokenResponse(r.accessToken(), r.expiresInSeconds(), r.user()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure).sameSite("Strict")
            .path("/api/v1/auth").maxAge(maxAge).build();
    }
}
