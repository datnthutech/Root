package vn.giapha.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.access-minutes}") long accessMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = accessMinutes;
    }

    public String issue(long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(userId)).claim("role", role.name())
            .issuedAt(Date.from(now)).expiration(Date.from(now.plus(accessMinutes, ChronoUnit.MINUTES)))
            .signWith(key).compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            var c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(Long.parseLong(c.getSubject()), Role.valueOf(c.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessSeconds() { return accessMinutes * 60; }
}
