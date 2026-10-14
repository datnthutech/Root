package vn.giapha.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "app_user") @Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String username;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "display_name") private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role = Role.VIEWER;
    @Column(name = "linked_person_id") private Long linkedPersonId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserStatus status = UserStatus.PENDING;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
