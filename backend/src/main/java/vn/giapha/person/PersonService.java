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
    public void delete(AuthUser me, long id) {
        Person p = persons.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
        Integer founders = jdbc.queryForObject("SELECT count(*) FROM clan WHERE founder_person_id = ?", Integer.class, id);
        if (founders != null && founders > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", "Không thể xoá thủy tổ của một dòng họ");
        }
        List<Long> affected = List.of(id);
        var before = Snapshots.of(p);
        p.setDeletedAt(java.time.Instant.now());
        persons.saveAndFlush(p);
        jdbc.update("UPDATE app_user SET linked_person_id = NULL WHERE linked_person_id = ?", id);
        clanMembers.rebuildClansOf(affected);   // chạy khi người còn trong clan_member để biết dòng họ nào cần dựng lại
        audit.record(me.id(), "PERSON", id, "DELETE", before, Snapshots.of(p));
    }

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
    private ApiException bad(String m) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", m); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy người này"); }
}
