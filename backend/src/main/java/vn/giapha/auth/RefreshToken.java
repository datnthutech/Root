package vn.giapha.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "refresh_token") @Getter @Setter
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean revoked;
    /** Thời điểm bị xoay vòng khi refresh; null nếu bị thu hồi bởi logout, đổi mật khẩu hoặc admin. */
    @Column(name = "rotated_at") private Instant rotatedAt;
}
