package vn.giapha.person;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.AuditService;
import vn.giapha.audit.Snapshots;
import vn.giapha.auth.AuthUser;
import vn.giapha.clan.ClanMemberService;
import vn.giapha.common.ApiException;

@Service
public class PersonService {
    private final PersonRepository persons;
    private final EditPolicy policy;
    private final AuditService audit;
    private final ClanMemberService clanMembers;
    private final JdbcTemplate jdbc;

    public PersonService(PersonRepository persons, EditPolicy policy, AuditService audit, ClanMemberService clanMembers, JdbcTemplate jdbc) {
        this.persons = persons;
        this.policy = policy;
        this.audit = audit;
        this.clanMembers = clanMembers;
        this.jdbc = jdbc;
    }

    @Transactional
    public Person create(AuthUser me, PersonInput in) {
        Person p = new Person();
        apply(p, in);
        p.setCreatedBy(me.id());
        p = persons.saveAndFlush(p);
        audit.record(me.id(), "PERSON", p.getId(), "CREATE", null, Snapshots.of(p));
        return p;
    }

    @Transactional
    public Person update(AuthUser me, long id, PersonInput in) {
        Person p = persons.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
        policy.assertCanEdit(me, p);
        if (in.version() == null) throw bad("Thiếu version");
        if (!in.version().equals(p.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Dữ liệu đã được người khác sửa, vui lòng tải lại");
        }
        var before = Snapshots.of(p);
        apply(p, in);
        p = persons.saveAndFlush(p);
        audit.record(me.id(), "PERSON", id, "UPDATE", before, Snapshots.of(p));
        return p;
    }

    @Transactional
    public void delete(AuthUser me, long id) { softDelete(me, id, "DELETE"); }

    @Transactional
    public void softDelete(AuthUser me, long id, String auditAction) {
        Person p = persons.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
        Integer founders = jdbc.queryForObject("SELECT count(*) FROM clan WHERE founder_person_id = ?", Integer.class, id);
        if (founders != null && founders > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", "Không thể xoá thủy tổ của một dòng họ");
        }
        var before = Snapshots.of(p);
        p.setDeletedAt(java.time.Instant.now());
        persons.saveAndFlush(p);
        jdbc.update("UPDATE app_user SET linked_person_id = NULL WHERE linked_person_id = ?", id);
        clanMembers.rebuildClansOf(List.of(id));
        audit.record(me.id(), "PERSON", id, auditAction, before, Snapshots.of(p));
    }

    @Transactional
    public void restore(AuthUser me, long id) {
        Person p = persons.findById(id).orElseThrow(this::notFound);
        if (p.getDeletedAt() == null) throw new ApiException(HttpStatus.CONFLICT, "NOT_REVERTIBLE", "Người này chưa bị xoá");
        var before = Snapshots.of(p);
        p.setDeletedAt(null);
        persons.saveAndFlush(p);
        List<Long> related = new java.util.ArrayList<>(jdbc.queryForList("""
            SELECT parent_id FROM parent_child WHERE child_id = ?
            UNION SELECT child_id FROM parent_child WHERE parent_id = ?
            UNION SELECT CASE WHEN partner_a_id = ? THEN partner_b_id ELSE partner_a_id END FROM union_rel WHERE partner_a_id = ? OR partner_b_id = ?
            """, Long.class, id, id, id, id, id));
        related.add(id);
        clanMembers.rebuildClansOf(related);
        audit.record(me.id(), "PERSON", id, "REVERT", before, Snapshots.of(p));
    }

    @Transactional
    public Person applyHistory(AuthUser me, long id, com.fasterxml.jackson.databind.JsonNode snap) {
        Person p = persons.findById(id).orElseThrow(this::notFound);
        if (p.getDeletedAt() != null) throw new ApiException(HttpStatus.CONFLICT, "NOT_REVERTIBLE", "Người này đã bị xoá");
        var before = Snapshots.of(p);
        p.setFullName(text(snap, "fullName") == null ? p.getFullName() : text(snap, "fullName"));
        p.setNickname(text(snap, "nickname"));
        if (text(snap, "gender") != null) p.setGender(Gender.valueOf(text(snap, "gender")));
        p.setBirthDate(date(snap, "birthDate"));
        if (text(snap, "birthPrecision") != null) p.setBirthPrecision(DatePrecision.valueOf(text(snap, "birthPrecision")));
        p.setBirthLunar(text(snap, "birthLunar"));
        p.setDeathDate(date(snap, "deathDate"));
        if (text(snap, "deathPrecision") != null) p.setDeathPrecision(DatePrecision.valueOf(text(snap, "deathPrecision")));
        p.setDeathAnniversaryLunar(text(snap, "deathAnniversaryLunar"));
        if (snap.has("alive")) p.setAlive(snap.get("alive").asBoolean());
        p.setOriginPlace(text(snap, "originPlace"));
        p.setHomePlace(text(snap, "homePlace"));
        p.setBurialPlace(text(snap, "burialPlace"));
        p.setBiography(text(snap, "biography"));
        p.setBirthOrderOverride(snap.hasNonNull("birthOrderOverride") ? snap.get("birthOrderOverride").asInt() : null);
        p = persons.saveAndFlush(p);
        audit.record(me.id(), "PERSON", id, "REVERT", before, Snapshots.of(p));
        return p;
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asText() : null; }
    private static java.time.LocalDate date(com.fasterxml.jackson.databind.JsonNode n, String f) { return n.hasNonNull(f) ? java.time.LocalDate.parse(n.get(f).asText()) : null; }

    private void apply(Person p, PersonInput in) {
        boolean alive = in.alive() == null || in.alive();
        if (alive && in.deathDate() != null) throw bad("Người còn sống không có ngày mất");
        if (in.birthDate() != null && in.deathDate() != null && in.deathDate().isBefore(in.birthDate())) throw bad("Ngày mất trước ngày sinh");
        p.setFullName(in.fullName().trim());
        p.setNickname(blank(in.nickname()));
        p.setGender(in.gender());
        p.setBirthDate(in.birthDate());
        p.setBirthPrecision(in.birthDate() == null ? DatePrecision.UNKNOWN : in.birthPrecision() == null ? DatePrecision.DAY : in.birthPrecision());
        p.setBirthLunar(blank(in.birthLunar()));
        p.setAlive(alive);
        p.setDeathDate(alive ? null : in.deathDate());
        p.setDeathPrecision(alive || in.deathDate() == null ? DatePrecision.UNKNOWN : in.deathPrecision() == null ? DatePrecision.DAY : in.deathPrecision());
        p.setDeathAnniversaryLunar(blank(in.deathAnniversaryLunar()));
        p.setOriginPlace(blank(in.originPlace()));
        p.setHomePlace(blank(in.homePlace()));
        p.setBurialPlace(blank(in.burialPlace()));
        p.setBiography(in.biography() == null || in.biography().isBlank() ? null : in.biography());
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    @Transactional
    public Person setBirthOrder(AuthUser me, long id, Integer order) {
        if (order != null && (order < 2 || order > 99)) throw bad("Thứ tự phải từ 2 đến 99");
        Person p = persons.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
        var before = Snapshots.of(p);
        p.setBirthOrderOverride(order);
        p = persons.saveAndFlush(p);
        audit.record(me.id(), "PERSON", id, "SET_BIRTH_ORDER", before, Snapshots.of(p));
        return p;
    }

    private ApiException bad(String m) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", m); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy người này"); }
}
