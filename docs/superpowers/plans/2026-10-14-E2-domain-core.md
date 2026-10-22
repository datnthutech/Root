# E2 Database và nghiệp vụ lõi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Có toàn bộ lược đồ DB (Flyway), entity/repository, và các quy tắc nghiệp vụ lõi có test: tên thứ tự miền Nam, tính thứ tự anh em, tính đời, kiểm vòng lặp, ràng buộc quan hệ cha mẹ – con và hôn phối.

**Architecture:** Logic thuần (không DB) đặt trong lớp `*Calculator`/`VietnameseOrder` để unit test nhanh; lớp `*Service` mỏng gọi repository + SQL đệ quy. Một migration `V2` tạo mọi bảng (kể cả `app_user`, `refresh_token`, `audit_log` để E3/E7 chỉ thêm code).

**Tech Stack:** Spring Data JPA, Flyway, PostgreSQL 16, JUnit 5, Testcontainers, Lombok.

**Spec:** `docs/superpowers/specs/2026-10-14-gia-pha-design.md` (mục 5) và tài liệu gốc mục "Mô hình dữ liệu", "Thứ tự anh em kiểu miền Nam".

## Global Constraints

- Mọi lệnh chạy ở `src/Root/backend`; test cần Docker đang chạy.
- Tên bảng/cột đúng như tài liệu gốc; `before`/`after` của audit lưu ở cột `before_data`/`after_data`.
- Mã lỗi: `RELATION_CYCLE`, `RELATION_INVALID`, `NOT_FOUND`, `VALIDATION_FAILED` (HTTP 409/409/404/400).
- Thứ tự anh em: con đầu là **thứ Hai** (số = vị trí + 2); `Tư` cho 4; `Út` tuỳ chọn theo dòng họ.
- `birth_order_override` = **số thứ** muốn gán trực tiếp (ví dụ 3 = "Ba"); người không có override lấp các số còn trống theo thứ tự ngày sinh bắt đầu từ 2.
- Cảnh báo ngày tháng không chặn lưu, trả trong `warnings`.

## Review Focus

- Hai người cùng ngày sinh (sinh đôi) hoặc không rõ ngày sinh: kết quả ổn định (không đổi thứ tự giữa các lần chạy) và đánh dấu `needsConfirm`.
- Gắn cha/mẹ cho chính mình, hoặc gắn hậu duệ làm cha/mẹ: bị chặn với `RELATION_CYCLE`, kể cả khi vòng lặp dài 5 đời.
- Hai người cùng cha khác mẹ: `PER_UNION` đếm tách, `PER_FATHER` đếm liền.
- Con nuôi (`counts_in_order=false`) không chiếm số thứ của con ruột.
- Cặp vợ chồng đã ly hôn rồi cưới lại: không bị chặn trùng (chỉ chặn khi đã có bản ghi `MARRIED` cho cùng cặp).

---

### Task 1: Lombok và migration lược đồ

**Files:**
- Modify: `build.gradle` (thêm Lombok)
- Create: `src/main/resources/db/migration/V2__core_schema.sql`
- Test: `src/test/java/vn/giapha/MigrationTest.java`

**Interfaces:**
- Produces: các bảng `person, union_rel, parent_child, clan, clan_member, app_user, refresh_token, audit_log`; hàm SQL `f_unaccent(text)`.

- [ ] **Step 1: Thêm Lombok vào `build.gradle`** (trong khối `dependencies { ... }`)

```groovy
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
```

- [ ] **Step 2: Viết test (failing)** `MigrationTest.java`

```java
package vn.giapha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestConfig.class)
class MigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void allTablesExist() {
        List<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='public'", String.class);
        assertThat(tables).contains("person", "union_rel", "parent_child", "clan", "clan_member",
            "app_user", "refresh_token", "audit_log");
    }

    @Test
    void unaccentHelperHandlesVietnamese() {
        String s = jdbc.queryForObject("SELECT lower(f_unaccent('Nguyễn Đức Thắng'))", String.class);
        assertThat(s).isEqualTo("nguyen duc thang");
    }

    @Test
    void cannotMarryTwiceWhileMarried() {
        jdbc.update("INSERT INTO person(id, full_name) VALUES (9001,'A'),(9002,'B')");
        jdbc.update("INSERT INTO union_rel(partner_a_id,partner_b_id,union_order,status) VALUES (9001,9002,1,'MARRIED')");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
            jdbc.update("INSERT INTO union_rel(partner_a_id,partner_b_id,union_order,status) VALUES (9002,9001,1,'MARRIED')"));
        jdbc.update("DELETE FROM union_rel WHERE partner_a_id=9001");
        jdbc.update("DELETE FROM person WHERE id IN (9001,9002)");
    }
}
```

- [ ] **Step 3: Chạy test, thấy FAIL**

Run: `./gradlew test --tests '*MigrationTest'`
Expected: FAIL (bảng chưa có).

- [ ] **Step 4: Viết `V2__core_schema.sql`**

```sql
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text AS
$$ SELECT public.unaccent('public.unaccent', $1) $$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

CREATE TABLE app_user (
  id               bigserial PRIMARY KEY,
  username         varchar(80)  NOT NULL UNIQUE,
  password_hash    varchar(100) NOT NULL,
  display_name     varchar(120),
  role             varchar(10)  NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('ADMIN','EDITOR','VIEWER')),
  linked_person_id bigint,
  status           varchar(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE','LOCKED')),
  failed_attempts  int          NOT NULL DEFAULT 0,
  locked_until     timestamptz,
  created_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE person (
  id                        bigserial PRIMARY KEY,
  full_name                 varchar(200) NOT NULL,
  nickname                  varchar(100),
  gender                    varchar(1)   NOT NULL DEFAULT 'U' CHECK (gender IN ('M','F','U')),
  birth_date                date,
  birth_precision           varchar(10)  NOT NULL DEFAULT 'UNKNOWN' CHECK (birth_precision IN ('DAY','MONTH','YEAR','UNKNOWN')),
  birth_lunar               varchar(40),
  death_date                date,
  death_precision           varchar(10)  NOT NULL DEFAULT 'UNKNOWN' CHECK (death_precision IN ('DAY','MONTH','YEAR','UNKNOWN')),
  death_anniversary_lunar   varchar(40),
  is_alive                  boolean      NOT NULL DEFAULT true,
  origin_place              varchar(200),
  home_place                varchar(200),
  burial_place              varchar(200),
  biography                 text,
  photo_key                 varchar(255),
  birth_order_override      int CHECK (birth_order_override IS NULL OR birth_order_override >= 2),
  version                   bigint       NOT NULL DEFAULT 0,
  created_by                bigint REFERENCES app_user(id),
  created_at                timestamptz  NOT NULL DEFAULT now(),
  updated_at                timestamptz  NOT NULL DEFAULT now(),
  deleted_at                timestamptz
);
CREATE INDEX idx_person_name_trgm ON person USING gin (lower(f_unaccent(full_name)) gin_trgm_ops);

ALTER TABLE app_user ADD CONSTRAINT fk_user_person FOREIGN KEY (linked_person_id) REFERENCES person(id);

CREATE TABLE union_rel (
  id           bigserial PRIMARY KEY,
  partner_a_id bigint NOT NULL REFERENCES person(id),
  partner_b_id bigint NOT NULL REFERENCES person(id),
  union_order  int    NOT NULL DEFAULT 1 CHECK (union_order >= 1),
  status       varchar(10) NOT NULL DEFAULT 'MARRIED' CHECK (status IN ('MARRIED','DIVORCED','WIDOWED')),
  start_date   date,
  end_date     date,
  CHECK (partner_a_id <> partner_b_id)
);
CREATE INDEX idx_union_partners ON union_rel(partner_a_id, partner_b_id);
CREATE INDEX idx_union_partner_b ON union_rel(partner_b_id);
CREATE UNIQUE INDEX uq_union_active_pair
  ON union_rel (LEAST(partner_a_id, partner_b_id), GREATEST(partner_a_id, partner_b_id))
  WHERE status = 'MARRIED';

CREATE TABLE parent_child (
  id              bigserial PRIMARY KEY,
  parent_id       bigint NOT NULL REFERENCES person(id),
  child_id        bigint NOT NULL REFERENCES person(id),
  kind            varchar(12) NOT NULL DEFAULT 'BIOLOGICAL' CHECK (kind IN ('BIOLOGICAL','ADOPTED','STEP')),
  union_id        bigint REFERENCES union_rel(id) ON DELETE SET NULL,
  counts_in_order boolean NOT NULL DEFAULT true,
  UNIQUE (parent_id, child_id),
  CHECK (parent_id <> child_id)
);
CREATE INDEX idx_pc_parent ON parent_child(parent_id);
CREATE INDEX idx_pc_child ON parent_child(child_id);

CREATE TABLE clan (
  id                 bigserial PRIMARY KEY,
  name               varchar(150) NOT NULL,
  founder_person_id  bigint REFERENCES person(id),
  description        text,
  sibling_count_mode varchar(12) NOT NULL DEFAULT 'PER_UNION' CHECK (sibling_count_mode IN ('PER_UNION','PER_FATHER')),
  use_ut_label       boolean NOT NULL DEFAULT true
);

CREATE TABLE clan_member (
  id         bigserial PRIMARY KEY,
  clan_id    bigint NOT NULL REFERENCES clan(id) ON DELETE CASCADE,
  person_id  bigint NOT NULL REFERENCES person(id),
  membership varchar(10) NOT NULL CHECK (membership IN ('BLOOD','IN_LAW')),
  generation int NOT NULL,
  UNIQUE (clan_id, person_id)
);
CREATE INDEX idx_clan_member_clan ON clan_member(clan_id);

CREATE TABLE refresh_token (
  id         bigserial PRIMARY KEY,
  user_id    bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash varchar(100) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked    boolean NOT NULL DEFAULT false
);

CREATE TABLE audit_log (
  id          bigserial PRIMARY KEY,
  user_id     bigint REFERENCES app_user(id),
  entity_type varchar(30) NOT NULL,
  entity_id   bigint NOT NULL,
  action      varchar(20) NOT NULL,
  before_data jsonb,
  after_data  jsonb,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id, created_at DESC);
```

- [ ] **Step 5: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*MigrationTest'`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add . && git commit -m "feat(E2): core schema migration"
```

---

### Task 2: Entity và repository

**Files:**
- Create (package `vn.giapha.person`): `Gender.java`, `DatePrecision.java`, `Person.java`, `PersonRepository.java`
- Create (package `vn.giapha.relation`): `UnionStatus.java`, `ParentKind.java`, `UnionRel.java`, `ParentChild.java`, `UnionRepository.java`, `ParentChildRepository.java`
- Create (package `vn.giapha.clan`): `Membership.java`, `SiblingCountMode.java`, `Clan.java`, `ClanMember.java`, `ClanRepository.java`, `ClanMemberRepository.java`
- Test: `src/test/java/vn/giapha/person/PersonRepositoryTest.java`

**Interfaces:**
- Produces (dùng ở mọi plan sau):
  - `Person`: `Long id; String fullName, nickname; Gender gender; LocalDate birthDate; DatePrecision birthPrecision; String birthLunar; LocalDate deathDate; DatePrecision deathPrecision; String deathAnniversaryLunar; boolean alive; String originPlace, homePlace, burialPlace, biography, photoKey; Integer birthOrderOverride; Long version; Long createdBy; Instant createdAt, updatedAt, deletedAt`.
  - `UnionRel`: `Long id, partnerAId, partnerBId; int unionOrder; UnionStatus status; LocalDate startDate, endDate`.
  - `ParentChild`: `Long id, parentId, childId; ParentKind kind; Long unionId; boolean countsInOrder`.
  - `Clan`: `Long id; String name; Long founderPersonId; String description; SiblingCountMode siblingCountMode; boolean useUtLabel`.
  - `ClanMember`: `Long id, clanId, personId; Membership membership; int generation`.
  - Repo: `PersonRepository.findByIdAndDeletedAtIsNull(Long)`, `UnionRepository.findByPartnerAIdOrPartnerBId(Long,Long)`, `ParentChildRepository.findByChildId(Long)`, `findByParentId(Long)`, `ClanMemberRepository.findByClanId(Long)`, `deleteByClanId(Long)`.

- [ ] **Step 1: Viết test (failing)** `PersonRepositoryTest.java`

```java
package vn.giapha.person;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class PersonRepositoryTest {
    @Autowired PersonRepository repo;

    @Test
    void savesAndIncrementsVersion() {
        Person p = new Person();
        p.setFullName("Nguyễn Văn A");
        p.setGender(Gender.M);
        p.setBirthDate(LocalDate.of(1950, 1, 2));
        p.setBirthPrecision(DatePrecision.DAY);
        p = repo.saveAndFlush(p);
        assertThat(p.getId()).isNotNull();
        assertThat(p.getVersion()).isEqualTo(0L);
        p.setNickname("Ba");
        p = repo.saveAndFlush(p);
        assertThat(p.getVersion()).isEqualTo(1L);
    }

    @Test
    void softDeletedPersonIsNotFoundByActiveQuery() {
        Person p = new Person();
        p.setFullName("B");
        p = repo.saveAndFlush(p);
        p.setDeletedAt(java.time.Instant.now());
        repo.saveAndFlush(p);
        assertThat(repo.findByIdAndDeletedAtIsNull(p.getId())).isEmpty();
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL** (không biên dịch được)

Run: `./gradlew test --tests '*PersonRepositoryTest'`
Expected: FAIL — thiếu lớp.

- [ ] **Step 3: Viết enum**

`person/Gender.java`: `package vn.giapha.person; public enum Gender { M, F, U }`
`person/DatePrecision.java`: `public enum DatePrecision { DAY, MONTH, YEAR, UNKNOWN }`
`relation/UnionStatus.java`: `public enum UnionStatus { MARRIED, DIVORCED, WIDOWED }`
`relation/ParentKind.java`: `public enum ParentKind { BIOLOGICAL, ADOPTED, STEP }`
`clan/Membership.java`: `public enum Membership { BLOOD, IN_LAW }`
`clan/SiblingCountMode.java`: `public enum SiblingCountMode { PER_UNION, PER_FATHER }`
(mỗi file có `package` đúng thư mục của nó.)

- [ ] **Step 4: Viết entity `Person`**

```java
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
```

`PersonRepository.java`:
```java
package vn.giapha.person;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByIdAndDeletedAtIsNull(Long id);
}
```

- [ ] **Step 5: Viết entity quan hệ và dòng họ**

`relation/UnionRel.java`:
```java
package vn.giapha.relation;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "union_rel") @Getter @Setter
public class UnionRel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "partner_a_id", nullable = false) private Long partnerAId;
    @Column(name = "partner_b_id", nullable = false) private Long partnerBId;
    @Column(name = "union_order", nullable = false) private int unionOrder = 1;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UnionStatus status = UnionStatus.MARRIED;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
}
```
`relation/ParentChild.java`:
```java
package vn.giapha.relation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "parent_child") @Getter @Setter
public class ParentChild {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "parent_id", nullable = false) private Long parentId;
    @Column(name = "child_id", nullable = false) private Long childId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ParentKind kind = ParentKind.BIOLOGICAL;
    @Column(name = "union_id") private Long unionId;
    @Column(name = "counts_in_order", nullable = false) private boolean countsInOrder = true;
}
```
`relation/UnionRepository.java`:
```java
package vn.giapha.relation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnionRepository extends JpaRepository<UnionRel, Long> {
    List<UnionRel> findByPartnerAIdOrPartnerBId(Long a, Long b);
}
```
`relation/ParentChildRepository.java`:
```java
package vn.giapha.relation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentChildRepository extends JpaRepository<ParentChild, Long> {
    List<ParentChild> findByChildId(Long childId);
    List<ParentChild> findByParentId(Long parentId);
}
```
`clan/Clan.java`:
```java
package vn.giapha.clan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "clan") @Getter @Setter
public class Clan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(name = "founder_person_id") private Long founderPersonId;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(name = "sibling_count_mode", nullable = false) private SiblingCountMode siblingCountMode = SiblingCountMode.PER_UNION;
    @Column(name = "use_ut_label", nullable = false) private boolean useUtLabel = true;
}
```
`clan/ClanMember.java`:
```java
package vn.giapha.clan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "clan_member") @Getter @Setter
public class ClanMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "clan_id", nullable = false) private Long clanId;
    @Column(name = "person_id", nullable = false) private Long personId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Membership membership;
    @Column(nullable = false) private int generation;
}
```
`clan/ClanRepository.java`: `public interface ClanRepository extends JpaRepository<Clan, Long> {}`
`clan/ClanMemberRepository.java`:
```java
package vn.giapha.clan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ClanMemberRepository extends JpaRepository<ClanMember, Long> {
    List<ClanMember> findByClanId(Long clanId);
    @Transactional void deleteByClanId(Long clanId);
}
```

- [ ] **Step 6: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*PersonRepositoryTest'`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add . && git commit -m "feat(E2): JPA entities and repositories"
```

---

### Task 3: Tên thứ tự miền Nam

**Files:**
- Create: `src/main/java/vn/giapha/relation/VietnameseOrder.java`
- Test: `src/test/java/vn/giapha/relation/VietnameseOrderTest.java`

**Interfaces:**
- Produces: `static String name(int number)`; `static String label(int number, Gender gender, boolean lastWithUt)`.

- [ ] **Step 1: Viết test (failing)**

```java
package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.giapha.person.Gender;

class VietnameseOrderTest {
    @Test
    void names() {
        assertThat(VietnameseOrder.name(2)).isEqualTo("Hai");
        assertThat(VietnameseOrder.name(3)).isEqualTo("Ba");
        assertThat(VietnameseOrder.name(4)).isEqualTo("Tư");
        assertThat(VietnameseOrder.name(5)).isEqualTo("Năm");
        assertThat(VietnameseOrder.name(6)).isEqualTo("Sáu");
        assertThat(VietnameseOrder.name(7)).isEqualTo("Bảy");
        assertThat(VietnameseOrder.name(8)).isEqualTo("Tám");
        assertThat(VietnameseOrder.name(9)).isEqualTo("Chín");
        assertThat(VietnameseOrder.name(10)).isEqualTo("Mười");
        assertThat(VietnameseOrder.name(11)).isEqualTo("Mười Một");
        assertThat(VietnameseOrder.name(12)).isEqualTo("Mười Hai");
        assertThat(VietnameseOrder.name(14)).isEqualTo("Mười Bốn");
        assertThat(VietnameseOrder.name(15)).isEqualTo("Mười Lăm");
        assertThat(VietnameseOrder.name(20)).isEqualTo("20");
    }

    @Test
    void labels() {
        assertThat(VietnameseOrder.label(2, Gender.M, false)).isEqualTo("anh Hai");
        assertThat(VietnameseOrder.label(3, Gender.F, false)).isEqualTo("chị Ba");
        assertThat(VietnameseOrder.label(3, Gender.U, false)).isEqualTo("thứ Ba");
        assertThat(VietnameseOrder.label(7, Gender.M, true)).isEqualTo("anh Út (thứ Bảy)");
        assertThat(VietnameseOrder.label(7, Gender.F, true)).isEqualTo("chị Út (thứ Bảy)");
        assertThat(VietnameseOrder.label(7, Gender.U, true)).isEqualTo("Út (thứ Bảy)");
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `./gradlew test --tests '*VietnameseOrderTest'`
Expected: FAIL (thiếu lớp).

- [ ] **Step 3: Viết triển khai**

```java
package vn.giapha.relation;

import vn.giapha.person.Gender;

public final class VietnameseOrder {
    private static final String[] BASE = {null, null, "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười"};
    private static final String[] UNIT = {null, "Một", "Hai", "Ba", "Bốn", "Lăm", "Sáu", "Bảy", "Tám", "Chín"};

    private VietnameseOrder() {}

    public static String name(int n) {
        if (n >= 2 && n <= 10) return BASE[n];
        if (n >= 11 && n <= 19) return "Mười " + UNIT[n - 10];
        return String.valueOf(n);
    }

    public static String label(int n, Gender gender, boolean lastWithUt) {
        String title = switch (gender) { case M -> "anh"; case F -> "chị"; case U -> null; };
        if (lastWithUt) {
            String ut = "Út (thứ " + name(n) + ")";
            return title == null ? ut : title + " " + ut;
        }
        return (title == null ? "thứ" : title) + " " + name(n);
    }
}
```

- [ ] **Step 4: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*VietnameseOrderTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m "feat(E2): southern Vietnamese sibling-order naming"
```

---

### Task 4: Tính thứ tự anh em (thuần)

**Files:**
- Create: `src/main/java/vn/giapha/relation/SiblingOrderCalculator.java`
- Test: `src/test/java/vn/giapha/relation/SiblingOrderCalculatorTest.java`

**Interfaces:**
- Produces:
```java
public final class SiblingOrderCalculator {
    public record Sibling(long id, LocalDate birthDate, Integer override, boolean counts) {}
    public record Result(int number, boolean needsConfirm) {}
    public static Map<Long, Result> compute(List<Sibling> siblings)   // người counts=false không có trong kết quả
}
```

- [ ] **Step 1: Viết test (failing)**

```java
package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.giapha.relation.SiblingOrderCalculator.Sibling;

class SiblingOrderCalculatorTest {
    static LocalDate d(int y) { return LocalDate.of(y, 1, 1); }

    @Test
    void firstChildIsTwoByBirthDate() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(3, d(1955), null, true), new Sibling(1, d(1950), null, true), new Sibling(2, d(1952), null, true)));
        assertThat(r.get(1L).number()).isEqualTo(2);
        assertThat(r.get(2L).number()).isEqualTo(3);
        assertThat(r.get(3L).number()).isEqualTo(4);
        assertThat(r.get(1L).needsConfirm()).isFalse();
    }

    @Test
    void unknownBirthDateGoesLastAndNeedsConfirm() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(1, null, null, true), new Sibling(2, d(1950), null, true)));
        assertThat(r.get(2L).number()).isEqualTo(2);
        assertThat(r.get(1L).number()).isEqualTo(3);
        assertThat(r.get(1L).needsConfirm()).isTrue();
    }

    @Test
    void twinsKeepStableOrderByIdAndNeedConfirm() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(9, d(1950), null, true), new Sibling(4, d(1950), null, true)));
        assertThat(r.get(4L).number()).isEqualTo(2);
        assertThat(r.get(9L).number()).isEqualTo(3);
        assertThat(r.get(4L).needsConfirm()).isTrue();
        assertThat(r.get(9L).needsConfirm()).isTrue();
    }

    @Test
    void overrideTakesExactNumberAndOthersFillGaps() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(1, d(1950), null, true), new Sibling(2, d(1952), null, true), new Sibling(3, d(1960), 2, true)));
        assertThat(r.get(3L).number()).isEqualTo(2);
        assertThat(r.get(3L).needsConfirm()).isFalse();
        assertThat(r.get(1L).number()).isEqualTo(3);
        assertThat(r.get(2L).number()).isEqualTo(4);
    }

    @Test
    void childNotCountedIsAbsentAndDoesNotTakeANumber() {
        var r = SiblingOrderCalculator.compute(List.of(
            new Sibling(1, d(1950), null, true), new Sibling(2, d(1951), null, false), new Sibling(3, d(1952), null, true)));
        assertThat(r).doesNotContainKey(2L);
        assertThat(r.get(3L).number()).isEqualTo(3);
    }

    @Test
    void emptyListGivesEmptyMap() {
        assertThat(SiblingOrderCalculator.compute(List.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `./gradlew test --tests '*SiblingOrderCalculatorTest'`
Expected: FAIL.

- [ ] **Step 3: Viết triển khai**

```java
package vn.giapha.relation;

import java.time.LocalDate;
import java.util.*;

public final class SiblingOrderCalculator {
    public record Sibling(long id, LocalDate birthDate, Integer override, boolean counts) {}
    public record Result(int number, boolean needsConfirm) {}

    private SiblingOrderCalculator() {}

    public static Map<Long, Result> compute(List<Sibling> siblings) {
        Map<Long, Result> out = new LinkedHashMap<>();
        List<Sibling> counted = siblings.stream().filter(Sibling::counts).toList();

        Set<Integer> taken = new HashSet<>();
        for (Sibling s : counted) {
            if (s.override() != null) { taken.add(s.override()); out.put(s.id(), new Result(s.override(), false)); }
        }

        List<Sibling> auto = counted.stream().filter(s -> s.override() == null)
            .sorted(Comparator.comparing(Sibling::birthDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(Sibling::id))
            .toList();

        int next = 2;
        for (int i = 0; i < auto.size(); i++) {
            Sibling s = auto.get(i);
            while (taken.contains(next)) next++;
            boolean tie = s.birthDate() != null && (
                (i > 0 && s.birthDate().equals(auto.get(i - 1).birthDate())) ||
                (i < auto.size() - 1 && s.birthDate().equals(auto.get(i + 1).birthDate())));
            out.put(s.id(), new Result(next, s.birthDate() == null || tie));
            next++;
        }
        return out;
    }
}
```

- [ ] **Step 4: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*SiblingOrderCalculatorTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m "feat(E2): sibling order calculator"
```

---

### Task 5: Nhóm anh em và dịch vụ thứ tự theo dòng họ

**Files:**
- Create: `src/main/java/vn/giapha/relation/SiblingGrouper.java`, `src/main/java/vn/giapha/relation/SiblingOrderService.java`
- Test: `src/test/java/vn/giapha/relation/SiblingGrouperTest.java`, `src/test/java/vn/giapha/relation/SiblingOrderServiceTest.java`

**Interfaces:**
- Consumes: `SiblingOrderCalculator`, `VietnameseOrder`, `SiblingCountMode`, `Gender`.
- Produces:
```java
public record ChildRow(long childId, Long fatherId, Long motherId, Long unionId, LocalDate birthDate,
                       Integer override, boolean counts, Gender gender) {}
public final class SiblingGrouper { public static Map<String, List<ChildRow>> group(List<ChildRow> rows, SiblingCountMode mode); }
public record OrderInfo(Integer number, String label, boolean needsConfirm) {}
@Service public class SiblingOrderService { public Map<Long, OrderInfo> computeForClan(long clanId); }
```
`ChildRow` là record công khai trong file riêng `ChildRow.java`.

- [ ] **Step 1: Viết test grouper (failing)** `SiblingGrouperTest.java`

```java
package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.giapha.clan.SiblingCountMode;
import vn.giapha.person.Gender;

class SiblingGrouperTest {
    static ChildRow row(long id, Long f, Long m, Long u) {
        return new ChildRow(id, f, m, u, LocalDate.of(1950, 1, 1), null, true, Gender.M);
    }

    @Test
    void perUnionSplitsHalfSiblingsByUnion() {
        var rows = List.of(row(1, 10L, 20L, 100L), row(2, 10L, 20L, 100L), row(3, 10L, 21L, 101L));
        var g = SiblingGrouper.group(rows, SiblingCountMode.PER_UNION);
        assertThat(g).hasSize(2);
    }

    @Test
    void perFatherMergesChildrenOfSameFather() {
        var rows = List.of(row(1, 10L, 20L, 100L), row(2, 10L, 20L, 100L), row(3, 10L, 21L, 101L));
        var g = SiblingGrouper.group(rows, SiblingCountMode.PER_FATHER);
        assertThat(g).hasSize(1);
    }

    @Test
    void childrenWithoutUnionGroupByParentPair() {
        var rows = List.of(row(1, 10L, 20L, null), row(2, 10L, 20L, null), row(3, 10L, null, null));
        var g = SiblingGrouper.group(rows, SiblingCountMode.PER_UNION);
        assertThat(g).hasSize(2);
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**, rồi viết triển khai

Run: `./gradlew test --tests '*SiblingGrouperTest'` → FAIL.

`ChildRow.java`:
```java
package vn.giapha.relation;

import java.time.LocalDate;
import vn.giapha.person.Gender;

public record ChildRow(long childId, Long fatherId, Long motherId, Long unionId, LocalDate birthDate,
                       Integer override, boolean counts, Gender gender) {}
```
`SiblingGrouper.java`:
```java
package vn.giapha.relation;

import java.util.*;
import vn.giapha.clan.SiblingCountMode;

public final class SiblingGrouper {
    private SiblingGrouper() {}

    public static Map<String, List<ChildRow>> group(List<ChildRow> rows, SiblingCountMode mode) {
        Map<String, List<ChildRow>> out = new LinkedHashMap<>();
        for (ChildRow r : rows) out.computeIfAbsent(key(r, mode), k -> new ArrayList<>()).add(r);
        return out;
    }

    private static String key(ChildRow r, SiblingCountMode mode) {
        if (mode == SiblingCountMode.PER_FATHER && r.fatherId() != null) return "f" + r.fatherId();
        if (r.unionId() != null) return "u" + r.unionId();
        return "p" + r.fatherId() + "-" + r.motherId();
    }
}
```
Run lại: PASS.

- [ ] **Step 3: Viết test service (failing)** `SiblingOrderServiceTest.java`

```java
package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.clan.*;
import vn.giapha.person.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class SiblingOrderServiceTest {
    @Autowired PersonRepository persons;
    @Autowired UnionRepository unions;
    @Autowired ParentChildRepository links;
    @Autowired ClanRepository clans;
    @Autowired ClanMemberRepository members;
    @Autowired SiblingOrderService service;

    Person p(String n, Gender g, int year) {
        Person x = new Person(); x.setFullName(n); x.setGender(g);
        x.setBirthDate(LocalDate.of(year, 1, 1)); x.setBirthPrecision(DatePrecision.YEAR);
        return persons.save(x);
    }
    void link(Person parent, Person child, UnionRel u, boolean counts) {
        ParentChild pc = new ParentChild(); pc.setParentId(parent.getId()); pc.setChildId(child.getId());
        pc.setUnionId(u == null ? null : u.getId()); pc.setCountsInOrder(counts); links.save(pc);
    }

    @Test
    void computesLabelsWithUtForLastChildAndSkipsAdopted() {
        Person dad = p("Cha", Gender.M, 1920), mom = p("Mẹ", Gender.F, 1925);
        UnionRel u = new UnionRel(); u.setPartnerAId(dad.getId()); u.setPartnerBId(mom.getId()); u = unions.save(u);
        Person a = p("A", Gender.M, 1950), b = p("B", Gender.F, 1952), c = p("C", Gender.F, 1955), adopted = p("D", Gender.M, 1953);
        for (Person k : new Person[]{a, b, c}) { link(dad, k, u, true); link(mom, k, u, true); }
        link(dad, adopted, u, false);

        Clan clan = new Clan(); clan.setName("Họ Test"); clan.setFounderPersonId(dad.getId()); clan = clans.save(clan);
        for (Person x : new Person[]{dad, mom, a, b, c, adopted}) {
            ClanMember m = new ClanMember(); m.setClanId(clan.getId()); m.setPersonId(x.getId());
            m.setMembership(Membership.BLOOD); m.setGeneration(1); members.save(m);
        }

        var r = service.computeForClan(clan.getId());
        assertThat(r.get(a.getId()).label()).isEqualTo("anh Hai");
        assertThat(r.get(b.getId()).label()).isEqualTo("chị Ba");
        assertThat(r.get(c.getId()).label()).isEqualTo("chị Út (thứ Tư)");
        assertThat(r).doesNotContainKey(adopted.getId());
        assertThat(r).doesNotContainKey(dad.getId());
    }
}
```

- [ ] **Step 4: Chạy test, thấy FAIL**, rồi viết `SiblingOrderService`

`OrderInfo.java`:
```java
package vn.giapha.relation;

public record OrderInfo(Integer number, String label, boolean needsConfirm) {}
```
`SiblingOrderService.java`:
```java
package vn.giapha.relation;

import java.sql.Date;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.giapha.clan.Clan;
import vn.giapha.clan.ClanRepository;
import vn.giapha.common.ApiException;
import org.springframework.http.HttpStatus;
import vn.giapha.person.Gender;

@Service
public class SiblingOrderService {
    private final JdbcTemplate jdbc;
    private final ClanRepository clans;

    public SiblingOrderService(JdbcTemplate jdbc, ClanRepository clans) {
        this.jdbc = jdbc;
        this.clans = clans;
    }

    private record Link(long childId, long parentId, String parentGender, Long unionId, boolean counts) {}

    /** Trả về thứ tự cho mọi thành viên dòng họ có cha hoặc mẹ trong DB (người không có cha mẹ thì không có mục). */
    public Map<Long, OrderInfo> computeForClan(long clanId) {
        Clan clan = clans.findById(clanId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy dòng họ"));

        List<Link> links = jdbc.query("""
            SELECT pc.child_id, pc.parent_id, par.gender AS pg, pc.union_id, pc.counts_in_order
            FROM parent_child pc
            JOIN clan_member cm ON cm.person_id = pc.child_id AND cm.clan_id = ?
            JOIN person par ON par.id = pc.parent_id AND par.deleted_at IS NULL
            WHERE pc.kind = 'BIOLOGICAL' OR pc.counts_in_order
            """, (rs, i) -> new Link(rs.getLong(1), rs.getLong(2), rs.getString(3),
                (Long) rs.getObject(4), rs.getBoolean(5)), clanId);

        Map<Long, List<Link>> byChild = links.stream().collect(Collectors.groupingBy(Link::childId));
        List<Long> childIds = new ArrayList<>(byChild.keySet());
        if (childIds.isEmpty()) return Map.of();

        Map<Long, Object[]> childInfo = new HashMap<>();
        jdbc.query("SELECT id, gender, birth_date, birth_order_override FROM person WHERE id = ANY(?)",
            rs -> { childInfo.put(rs.getLong(1), new Object[]{Gender.valueOf(rs.getString(2)),
                rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate(), (Integer) rs.getObject(4)}); },
            (Object) childIds.toArray(Long[]::new));

        List<ChildRow> rows = new ArrayList<>();
        for (var e : byChild.entrySet()) {
            Long father = null, mother = null, union = null;
            boolean counts = true;
            for (Link l : e.getValue()) {
                if ("M".equals(l.parentGender())) father = l.parentId();
                else if ("F".equals(l.parentGender())) mother = l.parentId();
                else if (father == null) father = l.parentId();
                if (l.unionId() != null) union = l.unionId();
                counts = counts && l.counts();
            }
            Object[] info = childInfo.get(e.getKey());
            if (info == null) continue;
            rows.add(new ChildRow(e.getKey(), father, mother, union, (java.time.LocalDate) info[1],
                (Integer) info[2], counts, (Gender) info[0]));
        }

        Map<Long, OrderInfo> out = new HashMap<>();
        for (List<ChildRow> group : SiblingGrouper.group(rows, clan.getSiblingCountMode()).values()) {
            Map<Long, ChildRow> byId = group.stream().collect(Collectors.toMap(ChildRow::childId, r -> r));
            var res = SiblingOrderCalculator.compute(group.stream()
                .map(r -> new SiblingOrderCalculator.Sibling(r.childId(), r.birthDate(), r.override(), r.counts())).toList());
            int max = res.values().stream().mapToInt(SiblingOrderCalculator.Result::number).max().orElse(0);
            boolean useUt = clan.isUseUtLabel() && res.size() >= 2;
            res.forEach((id, r) -> out.put(id, new OrderInfo(r.number(),
                VietnameseOrder.label(r.number(), byId.get(id).gender(), useUt && r.number() == max),
                r.needsConfirm())));
        }
        return out;
    }
}
```
(Xoá import `java.sql.Date` không dùng nếu trình biên dịch cảnh báo.)

- [ ] **Step 5: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*SiblingGrouperTest' --tests '*SiblingOrderServiceTest'`
Expected: PASS. Nếu truy vấn `= ANY(?)` với mảng lỗi, đổi sang `NamedParameterJdbcTemplate` với `IN (:ids)`; giữ nguyên kết quả mong đợi.

- [ ] **Step 6: Commit**

```bash
git add . && git commit -m "feat(E2): sibling grouping and per-clan order service"
```

---

### Task 6: Tính đời và dựng danh sách thành viên dòng họ

**Files:**
- Create: `src/main/java/vn/giapha/clan/GenerationCalculator.java`, `src/main/java/vn/giapha/clan/ClanMemberService.java`
- Test: `src/test/java/vn/giapha/clan/GenerationCalculatorTest.java`, `src/test/java/vn/giapha/clan/ClanMemberServiceTest.java`

**Interfaces:**
- Produces:
```java
public final class GenerationCalculator {
    public record Gen(int generation, Membership membership) {}
    public static Map<Long, Gen> compute(long rootId, Function<Long, List<Long>> childrenOf, Function<Long, List<Long>> spousesOf)
}
@Service public class ClanMemberService { @Transactional public int rebuild(long clanId) }  // trả số thành viên
```

- [ ] **Step 1: Viết test thuần (failing)** `GenerationCalculatorTest.java`

```java
package vn.giapha.clan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenerationCalculatorTest {
    @Test
    void rootIsGenerationOneChildrenPlusOneSpousesSameGenerationInLaw() {
        Map<Long, List<Long>> kids = Map.of(1L, List.of(3L), 3L, List.of(5L));
        Map<Long, List<Long>> spouses = Map.of(1L, List.of(2L), 3L, List.of(4L));
        var r = GenerationCalculator.compute(1L,
            id -> kids.getOrDefault(id, List.of()), id -> spouses.getOrDefault(id, List.of()));
        assertThat(r.get(1L)).isEqualTo(new GenerationCalculator.Gen(1, Membership.BLOOD));
        assertThat(r.get(2L)).isEqualTo(new GenerationCalculator.Gen(1, Membership.IN_LAW));
        assertThat(r.get(3L)).isEqualTo(new GenerationCalculator.Gen(2, Membership.BLOOD));
        assertThat(r.get(4L)).isEqualTo(new GenerationCalculator.Gen(2, Membership.IN_LAW));
        assertThat(r.get(5L)).isEqualTo(new GenerationCalculator.Gen(3, Membership.BLOOD));
    }

    @Test
    void terminatesOnCyclicBadData() {
        var r = GenerationCalculator.compute(1L, id -> id == 1L ? List.of(2L) : List.of(1L), id -> List.of());
        assertThat(r).hasSize(2);
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**, rồi viết `GenerationCalculator`

```java
package vn.giapha.clan;

import java.util.*;
import java.util.function.Function;

public final class GenerationCalculator {
    public record Gen(int generation, Membership membership) {}

    private GenerationCalculator() {}

    public static Map<Long, Gen> compute(long rootId, Function<Long, List<Long>> childrenOf,
                                         Function<Long, List<Long>> spousesOf) {
        Map<Long, Gen> out = new LinkedHashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        out.put(rootId, new Gen(1, Membership.BLOOD));
        queue.add(rootId);
        while (!queue.isEmpty()) {
            long p = queue.poll();
            Gen g = out.get(p);
            for (long s : spousesOf.apply(p)) out.putIfAbsent(s, new Gen(g.generation(), Membership.IN_LAW));
            for (long c : childrenOf.apply(p)) {
                if (!out.containsKey(c)) { out.put(c, new Gen(g.generation() + 1, Membership.BLOOD)); queue.add(c); }
            }
        }
        return out;
    }
}
```
Run: `./gradlew test --tests '*GenerationCalculatorTest'` → PASS.

- [ ] **Step 3: Viết test service (failing)** `ClanMemberServiceTest.java`

```java
package vn.giapha.clan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.person.*;
import vn.giapha.relation.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class ClanMemberServiceTest {
    @Autowired PersonRepository persons;
    @Autowired UnionRepository unions;
    @Autowired ParentChildRepository links;
    @Autowired ClanRepository clans;
    @Autowired ClanMemberRepository members;
    @Autowired ClanMemberService service;

    Person p(String n) { Person x = new Person(); x.setFullName(n); return persons.save(x); }

    @Test
    void rebuildStoresGenerationAndMembership() {
        Person root = p("Tổ"), wife = p("Vợ tổ"), kid = p("Con"), softDeleted = p("Đã xoá");
        UnionRel u = new UnionRel(); u.setPartnerAId(root.getId()); u.setPartnerBId(wife.getId()); unions.save(u);
        for (Person parent : new Person[]{root, wife}) {
            ParentChild pc = new ParentChild(); pc.setParentId(parent.getId()); pc.setChildId(kid.getId()); links.save(pc);
        }
        ParentChild gone = new ParentChild(); gone.setParentId(root.getId()); gone.setChildId(softDeleted.getId()); links.save(gone);
        softDeleted.setDeletedAt(java.time.Instant.now()); persons.save(softDeleted);

        Clan c = new Clan(); c.setName("Họ"); c.setFounderPersonId(root.getId()); c = clans.save(c);
        int n = service.rebuild(c.getId());
        assertThat(n).isEqualTo(3);
        var byPerson = members.findByClanId(c.getId()).stream()
            .collect(java.util.stream.Collectors.toMap(ClanMember::getPersonId, m -> m));
        assertThat(byPerson.get(root.getId()).getGeneration()).isEqualTo(1);
        assertThat(byPerson.get(wife.getId()).getMembership()).isEqualTo(Membership.IN_LAW);
        assertThat(byPerson.get(kid.getId()).getGeneration()).isEqualTo(2);
        assertThat(byPerson).doesNotContainKey(softDeleted.getId());
    }
}
```

- [ ] **Step 4: Chạy test, thấy FAIL**, rồi viết `ClanMemberService`

```java
package vn.giapha.clan;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;

@Service
public class ClanMemberService {
    private final JdbcTemplate jdbc;
    private final ClanRepository clans;

    public ClanMemberService(JdbcTemplate jdbc, ClanRepository clans) {
        this.jdbc = jdbc;
        this.clans = clans;
    }

    @Transactional
    public int rebuild(long clanId) {
        Clan clan = clans.findById(clanId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy dòng họ"));
        if (clan.getFounderPersonId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", "Dòng họ chưa có thủy tổ");
        }
        Map<Long, List<Long>> kids = new HashMap<>();
        jdbc.query("""
            SELECT pc.parent_id, pc.child_id FROM parent_child pc
            JOIN person c ON c.id = pc.child_id AND c.deleted_at IS NULL
            JOIN person p ON p.id = pc.parent_id AND p.deleted_at IS NULL
            """, rs -> { kids.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)); });
        Map<Long, List<Long>> spouses = new HashMap<>();
        jdbc.query("""
            SELECT u.partner_a_id, u.partner_b_id FROM union_rel u
            JOIN person a ON a.id = u.partner_a_id AND a.deleted_at IS NULL
            JOIN person b ON b.id = u.partner_b_id AND b.deleted_at IS NULL
            """, rs -> {
                spouses.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
                spouses.computeIfAbsent(rs.getLong(2), k -> new ArrayList<>()).add(rs.getLong(1));
            });

        var gens = GenerationCalculator.compute(clan.getFounderPersonId(),
            id -> kids.getOrDefault(id, List.of()), id -> spouses.getOrDefault(id, List.of()));

        jdbc.update("DELETE FROM clan_member WHERE clan_id = ?", clanId);
        List<Map.Entry<Long, GenerationCalculator.Gen>> entries = new ArrayList<>(gens.entrySet());
        jdbc.batchUpdate("INSERT INTO clan_member(clan_id, person_id, membership, generation) VALUES (?,?,?,?)",
            entries, 500, (ps, e) -> {
                ps.setLong(1, clanId);
                ps.setLong(2, e.getKey());
                ps.setString(3, e.getValue().membership().name());
                ps.setInt(4, e.getValue().generation());
            });
        return entries.size();
    }
}
```
Lưu ý: người đã xoá mềm là con của gốc bị loại vì join `deleted_at IS NULL`.

- [ ] **Step 5: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*GenerationCalculatorTest' --tests '*ClanMemberServiceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add . && git commit -m "feat(E2): generation calculator and clan member rebuild"
```

---

### Task 7: Quan hệ cha mẹ – con (vòng lặp, ràng buộc, cảnh báo)

**Files:**
- Create: `src/main/java/vn/giapha/relation/Warning.java`, `RelationResult.java`, `CycleChecker.java`, `ParentChildService.java`
- Test: `src/test/java/vn/giapha/relation/ParentChildServiceTest.java`

**Interfaces:**
- Consumes: repositories (Task 2).
- Produces:
```java
public record Warning(String code, String message) {}
public record ParentChildResult(ParentChild link, List<Warning> warnings) {}
@Service public class CycleChecker { public boolean isDescendant(long ancestorId, long candidateId) }
@Service public class ParentChildService {
    @Transactional public ParentChildResult add(long parentId, long childId, ParentKind kind, Long unionId);
    @Transactional public void remove(long linkId);
}
```
`kind == null` coi như `BIOLOGICAL`. `add` đặt `countsInOrder = (kind == BIOLOGICAL)`.

- [ ] **Step 1: Viết test (failing)** `ParentChildServiceTest.java`

```java
package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.common.ApiException;
import vn.giapha.person.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class ParentChildServiceTest {
    @Autowired PersonRepository persons;
    @Autowired ParentChildService service;
    @Autowired ParentChildRepository links;

    Person p(String n, Gender g, Integer birthYear) {
        Person x = new Person(); x.setFullName(n); x.setGender(g);
        if (birthYear != null) { x.setBirthDate(LocalDate.of(birthYear, 6, 1)); x.setBirthPrecision(DatePrecision.DAY); }
        return persons.save(x);
    }

    @Test
    void addsLinkAndMarksBiologicalAsCounted() {
        Person dad = p("Cha", Gender.M, 1920), kid = p("Con", Gender.M, 1950);
        var r = service.add(dad.getId(), kid.getId(), null, null);
        assertThat(r.link().getKind()).isEqualTo(ParentKind.BIOLOGICAL);
        assertThat(r.link().isCountsInOrder()).isTrue();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void adoptedChildIsNotCounted() {
        Person dad = p("Cha", Gender.M, 1920), kid = p("Con", Gender.M, 1950);
        assertThat(service.add(dad.getId(), kid.getId(), ParentKind.ADOPTED, null).link().isCountsInOrder()).isFalse();
    }

    @Test
    void selfParentIsRejectedAsCycle() {
        Person a = p("A", Gender.M, null);
        assertThatThrownBy(() -> service.add(a.getId(), a.getId(), null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_CYCLE"));
    }

    @Test
    void descendantCannotBecomeAncestorEvenFiveGenerationsDeep() {
        Person[] g = new Person[6];
        for (int i = 0; i < 6; i++) g[i] = p("G" + i, Gender.M, null);
        for (int i = 0; i < 5; i++) service.add(g[i].getId(), g[i + 1].getId(), null, null);
        assertThatThrownBy(() -> service.add(g[5].getId(), g[0].getId(), null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_CYCLE"));
    }

    @Test
    void secondBiologicalFatherIsRejectedButStepFatherAllowed() {
        Person dad1 = p("Cha1", Gender.M, null), dad2 = p("Cha2", Gender.M, null), kid = p("Con", Gender.M, null);
        service.add(dad1.getId(), kid.getId(), null, null);
        assertThatThrownBy(() -> service.add(dad2.getId(), kid.getId(), null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
        assertThat(service.add(dad2.getId(), kid.getId(), ParentKind.STEP, null).link().getId()).isNotNull();
    }

    @Test
    void duplicateLinkIsRejected() {
        Person dad = p("Cha", Gender.M, null), kid = p("Con", Gender.M, null);
        service.add(dad.getId(), kid.getId(), null, null);
        assertThatThrownBy(() -> service.add(dad.getId(), kid.getId(), ParentKind.ADOPTED, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
    }

    @Test
    void unknownPersonIsNotFound() {
        Person dad = p("Cha", Gender.M, null);
        assertThatThrownBy(() -> service.add(dad.getId(), 999999L, null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void warnsWhenChildBornBeforeParent() {
        Person dad = p("Cha", Gender.M, 1960), kid = p("Con", Gender.M, 1950);
        var r = service.add(dad.getId(), kid.getId(), null, null);
        assertThat(r.warnings()).extracting(Warning::code).containsExactly("CHILD_BEFORE_PARENT");
    }

    @Test
    void warnsWhenParentUnder13AtBirth() {
        Person dad = p("Cha", Gender.M, 1950), kid = p("Con", Gender.M, 1960);
        var r = service.add(dad.getId(), kid.getId(), null, null);
        assertThat(r.warnings()).extracting(Warning::code).containsExactly("PARENT_TOO_YOUNG");
    }

    @Test
    void removeDeletesLink() {
        Person dad = p("Cha", Gender.M, null), kid = p("Con", Gender.M, null);
        long id = service.add(dad.getId(), kid.getId(), null, null).link().getId();
        service.remove(id);
        assertThat(links.findById(id)).isEmpty();
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `./gradlew test --tests '*ParentChildServiceTest'`
Expected: FAIL (thiếu lớp).

- [ ] **Step 3: Viết triển khai**

`Warning.java`: `package vn.giapha.relation; public record Warning(String code, String message) {}`
`ParentChildResult.java`:
```java
package vn.giapha.relation;

import java.util.List;

public record ParentChildResult(ParentChild link, List<Warning> warnings) {}
```
`CycleChecker.java`:
```java
package vn.giapha.relation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CycleChecker {
    private final JdbcTemplate jdbc;

    public CycleChecker(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** true nếu candidateId là hậu duệ (con, cháu, ...) của ancestorId. */
    public boolean isDescendant(long ancestorId, long candidateId) {
        Boolean r = jdbc.queryForObject("""
            WITH RECURSIVE d(id) AS (
              SELECT child_id FROM parent_child WHERE parent_id = ?
              UNION
              SELECT pc.child_id FROM parent_child pc JOIN d ON pc.parent_id = d.id
            ) SELECT EXISTS (SELECT 1 FROM d WHERE id = ?)
            """, Boolean.class, ancestorId, candidateId);
        return Boolean.TRUE.equals(r);
    }
}
```
`ParentChildService.java`:
```java
package vn.giapha.relation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.person.Gender;
import vn.giapha.person.Person;
import vn.giapha.person.PersonRepository;

@Service
public class ParentChildService {
    private final PersonRepository persons;
    private final ParentChildRepository links;
    private final UnionRepository unions;
    private final CycleChecker cycles;

    public ParentChildService(PersonRepository persons, ParentChildRepository links, UnionRepository unions, CycleChecker cycles) {
        this.persons = persons;
        this.links = links;
        this.unions = unions;
        this.cycles = cycles;
    }

    @Transactional
    public ParentChildResult add(long parentId, long childId, ParentKind kind, Long unionId) {
        ParentKind k = kind == null ? ParentKind.BIOLOGICAL : kind;
        if (parentId == childId) throw cycle("Một người không thể là cha/mẹ của chính mình");
        Person parent = persons.findByIdAndDeletedAtIsNull(parentId).orElseThrow(this::notFound);
        Person child = persons.findByIdAndDeletedAtIsNull(childId).orElseThrow(this::notFound);
        if (cycles.isDescendant(childId, parentId)) throw cycle("Tạo vòng lặp: cha/mẹ mới đang là hậu duệ của người con");

        List<ParentChild> existing = links.findByChildId(childId);
        if (existing.stream().anyMatch(l -> l.getParentId().equals(parentId))) {
            throw invalid("Quan hệ cha/mẹ - con này đã tồn tại");
        }
        if (k == ParentKind.BIOLOGICAL) checkBiologicalSlot(parent, existing);
        if (unionId != null) {
            UnionRel u = unions.findById(unionId).orElseThrow(() -> invalid("Không tìm thấy hôn phối"));
            if (!u.getPartnerAId().equals(parentId) && !u.getPartnerBId().equals(parentId)) {
                throw invalid("Hôn phối không thuộc về cha/mẹ này");
            }
        }

        ParentChild link = new ParentChild();
        link.setParentId(parentId);
        link.setChildId(childId);
        link.setKind(k);
        link.setUnionId(unionId);
        link.setCountsInOrder(k == ParentKind.BIOLOGICAL);
        return new ParentChildResult(links.save(link), warnings(parent, child));
    }

    @Transactional
    public void remove(long linkId) {
        if (!links.existsById(linkId)) throw notFound();
        links.deleteById(linkId);
    }

    private void checkBiologicalSlot(Person parent, List<ParentChild> existing) {
        List<Person> bioParents = existing.stream().filter(l -> l.getKind() == ParentKind.BIOLOGICAL)
            .map(l -> persons.findById(l.getParentId()).orElseThrow(this::notFound)).toList();
        if (parent.getGender() == Gender.U) {
            if (bioParents.size() >= 2) throw invalid("Đã đủ cha và mẹ ruột");
            return;
        }
        boolean taken = bioParents.stream().anyMatch(p -> p.getGender() == parent.getGender());
        if (taken) throw invalid(parent.getGender() == Gender.M ? "Đã có cha ruột" : "Đã có mẹ ruột");
    }

    private List<Warning> warnings(Person parent, Person child) {
        List<Warning> w = new ArrayList<>();
        LocalDate pb = parent.getBirthDate(), cb = child.getBirthDate();
        if (pb != null && cb != null) {
            if (cb.isBefore(pb)) w.add(new Warning("CHILD_BEFORE_PARENT", "Con sinh trước cha/mẹ"));
            else if (ChronoUnit.YEARS.between(pb, cb) < 13) w.add(new Warning("PARENT_TOO_YOUNG", "Cha/mẹ dưới 13 tuổi lúc sinh con"));
        }
        return w;
    }

    private ApiException cycle(String m) { return new ApiException(HttpStatus.CONFLICT, "RELATION_CYCLE", m); }
    private ApiException invalid(String m) { return new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", m); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy"); }
}
```

- [ ] **Step 4: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*ParentChildServiceTest'`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m "feat(E2): parent-child relation rules with cycle check and warnings"
```

---

### Task 8: Hôn phối

**Files:**
- Create: `src/main/java/vn/giapha/relation/UnionService.java`
- Test: `src/test/java/vn/giapha/relation/UnionServiceTest.java`

**Interfaces:**
- Produces:
```java
@Service public class UnionService {
    @Transactional public UnionRel create(long partnerAId, long partnerBId, Integer order, UnionStatus status, LocalDate start, LocalDate end);
    @Transactional public UnionRel update(long unionId, Integer order, UnionStatus status, LocalDate start, LocalDate end);
    @Transactional public void delete(long unionId);
}
```
`order == null` → `1 + số hôn phối hiện có của partnerA`. `status == null` → `MARRIED`.

- [ ] **Step 1: Viết test (failing)** `UnionServiceTest.java`

```java
package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.common.ApiException;
import vn.giapha.person.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class UnionServiceTest {
    @Autowired PersonRepository persons;
    @Autowired UnionService service;
    @Autowired UnionRepository unions;
    @Autowired ParentChildService pcs;
    @Autowired ParentChildRepository links;

    Person p(String n) { Person x = new Person(); x.setFullName(n); return persons.save(x); }

    @Test
    void createDefaultsOrderAndStatus() {
        Person a = p("A"), b = p("B"), c = p("C");
        UnionRel u1 = service.create(a.getId(), b.getId(), null, null, null, null);
        UnionRel u2 = service.create(a.getId(), c.getId(), null, null, null, null);
        assertThat(u1.getUnionOrder()).isEqualTo(1);
        assertThat(u2.getUnionOrder()).isEqualTo(2);
        assertThat(u1.getStatus()).isEqualTo(UnionStatus.MARRIED);
    }

    @Test
    void cannotMarrySelf() {
        Person a = p("A");
        assertThatThrownBy(() -> service.create(a.getId(), a.getId(), null, null, null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
    }

    @Test
    void cannotCreateSecondActiveUnionForSamePairInEitherOrder() {
        Person a = p("A"), b = p("B");
        service.create(a.getId(), b.getId(), null, null, null, null);
        assertThatThrownBy(() -> service.create(b.getId(), a.getId(), null, null, null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
    }

    @Test
    void canRemarryAfterDivorce() {
        Person a = p("A"), b = p("B");
        UnionRel u = service.create(a.getId(), b.getId(), null, null, null, null);
        service.update(u.getId(), null, UnionStatus.DIVORCED, null, null);
        assertThat(service.create(a.getId(), b.getId(), null, null, null, null).getId()).isNotEqualTo(u.getId());
    }

    @Test
    void cannotReactivateIntoDuplicateMarriage() {
        Person a = p("A"), b = p("B");
        UnionRel old = service.create(a.getId(), b.getId(), null, null, null, null);
        service.update(old.getId(), null, UnionStatus.DIVORCED, null, null);
        service.create(a.getId(), b.getId(), null, null, null, null);
        assertThatThrownBy(() -> service.update(old.getId(), null, UnionStatus.MARRIED, null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
    }

    @Test
    void deleteKeepsChildLinkButClearsUnionId() {
        Person a = p("A"), b = p("B"), kid = p("K");
        UnionRel u = service.create(a.getId(), b.getId(), null, null, null, null);
        long linkId = pcs.add(a.getId(), kid.getId(), null, u.getId()).link().getId();
        service.delete(u.getId());
        assertThat(links.findById(linkId)).isPresent();
        assertThat(links.findById(linkId).get().getUnionId()).isNull();
    }
}
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `./gradlew test --tests '*UnionServiceTest'`
Expected: FAIL.

- [ ] **Step 3: Viết `UnionService.java`**

```java
package vn.giapha.relation;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.person.PersonRepository;

@Service
public class UnionService {
    private final UnionRepository unions;
    private final PersonRepository persons;

    public UnionService(UnionRepository unions, PersonRepository persons) {
        this.unions = unions;
        this.persons = persons;
    }

    @Transactional
    public UnionRel create(long a, long b, Integer order, UnionStatus status, LocalDate start, LocalDate end) {
        if (a == b) throw invalid("Không thể tự kết hôn với chính mình");
        persons.findByIdAndDeletedAtIsNull(a).orElseThrow(this::notFound);
        persons.findByIdAndDeletedAtIsNull(b).orElseThrow(this::notFound);
        UnionStatus st = status == null ? UnionStatus.MARRIED : status;
        if (st == UnionStatus.MARRIED) ensureNoActivePair(a, b, null);
        UnionRel u = new UnionRel();
        u.setPartnerAId(a);
        u.setPartnerBId(b);
        u.setStatus(st);
        u.setUnionOrder(order != null ? order : 1 + unions.findByPartnerAIdOrPartnerBId(a, a).size());
        u.setStartDate(start);
        u.setEndDate(end);
        return unions.save(u);
    }

    @Transactional
    public UnionRel update(long id, Integer order, UnionStatus status, LocalDate start, LocalDate end) {
        UnionRel u = unions.findById(id).orElseThrow(this::notFound);
        if (status == UnionStatus.MARRIED && u.getStatus() != UnionStatus.MARRIED) {
            ensureNoActivePair(u.getPartnerAId(), u.getPartnerBId(), id);
        }
        if (order != null) u.setUnionOrder(order);
        if (status != null) u.setStatus(status);
        if (start != null) u.setStartDate(start);
        if (end != null) u.setEndDate(end);
        return unions.saveAndFlush(u);
    }

    @Transactional
    public void delete(long id) {
        if (!unions.existsById(id)) throw notFound();
        unions.deleteById(id);
        unions.flush();
    }

    private void ensureNoActivePair(long a, long b, Long exceptId) {
        List<UnionRel> mine = unions.findByPartnerAIdOrPartnerBId(a, a);
        boolean dup = mine.stream().anyMatch(x -> x.getStatus() == UnionStatus.MARRIED
            && !x.getId().equals(exceptId)
            && ((x.getPartnerAId() == a && x.getPartnerBId() == b) || (x.getPartnerAId() == b && x.getPartnerBId() == a)));
        if (dup) throw invalid("Cặp này đã có hôn phối đang hiệu lực");
    }

    private ApiException invalid(String m) { return new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", m); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy"); }
}
```
Ghi chú: `x.getPartnerAId() == a` so sánh `Long` với `long` → tự mở hộp (unbox) nên đúng; nếu cả hai vế đều là `Long` phải dùng `.equals`.

- [ ] **Step 4: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*UnionServiceTest'`
Expected: PASS. Nếu `deleteKeepsChildLinkButClearsUnionId` fail vì cache JPA, thêm `entityManager.clear()` vào test trước khi đọc lại (không đổi service).

- [ ] **Step 5: Chạy toàn bộ test của plan**

Run: `./gradlew test`
Expected: tất cả PASS.

- [ ] **Step 6: Commit**

```bash
git add . && git commit -m "feat(E2): union service"
```

---

## Self-review

- Spec: bảng DB (T1–2), thứ tự miền Nam (T3–5), đời (T6), ràng buộc 1,2,3,4 (T7–8), ràng buộc 5 (version) dùng `@Version` (T2, được kiểm ở E6).
- Chưa có REST controller cho quan hệ — thuộc E6.
