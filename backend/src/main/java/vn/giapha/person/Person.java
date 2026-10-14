package vn.giapha.person;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "person")
@Getter @Setter
public class Person {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "full_name", nullable = false) private String fullName;
    private String nickname;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Gender gender = Gender.U;
    @Column(name = "birth_date") private LocalDate birthDate;
    @Enumerated(EnumType.STRING) @Column(name = "birth_precision", nullable = false) private DatePrecision birthPrecision = DatePrecision.UNKNOWN;
    @Column(name = "birth_lunar") private String birthLunar;
    @Column(name = "death_date") private LocalDate deathDate;
    @Enumerated(EnumType.STRING) @Column(name = "death_precision", nullable = false) private DatePrecision deathPrecision = DatePrecision.UNKNOWN;
    @Column(name = "death_anniversary_lunar") private String deathAnniversaryLunar;
    @Column(name = "is_alive", nullable = false) private boolean alive = true;
    @Column(name = "origin_place") private String originPlace;
    @Column(name = "home_place") private String homePlace;
    @Column(name = "burial_place") private String burialPlace;
    @Column(columnDefinition = "text") private String biography;
    @Column(name = "photo_key") private String photoKey;
    @Column(name = "birth_order_override") private Integer birthOrderOverride;
    @Version private Long version;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "deleted_at") private Instant deletedAt;

    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
