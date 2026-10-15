package vn.giapha.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.Role;
import vn.giapha.support.TestUsers;
import vn.giapha.support.TreeFixture;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, TreeFixture.class})
@Transactional
class RevertApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;
    @Autowired jakarta.persistence.EntityManager em;

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    long lastAudit(String type, String action, long entityId) {
        return jdbc.queryForObject("SELECT id FROM audit_log WHERE entity_type=? AND action=? AND entity_id=? ORDER BY id DESC LIMIT 1", Long.class, type, action, entityId);
    }
    String admin() { return users.bearer(Role.ADMIN); }
    int members(long clan) { return jdbc.queryForObject("SELECT count(*) FROM clan_member WHERE clan_id=?", Integer.class, clan); }

    @Test
    void permissionMatrix() throws Exception {
        mvc.perform(post("/api/v1/audit/1/revert")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/audit/1/revert").header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/audit/1/revert").header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/audit/99999999/revert").header("Authorization", admin())).andExpect(status().isNotFound());
    }

    @Test
    void revertUpdateRestoresOldFieldsEvenAfterLaterEdits() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long v0 = jdbc.queryForObject("SELECT version FROM person WHERE id=?", Long.class, ids.c());
        mvc.perform(put("/api/v1/persons/" + ids.c()).header("Authorization", a).contentType(JSON)
            .content("{\"fullName\":\"Sửa Lần 1\",\"nickname\":\"Chín\",\"gender\":\"M\",\"alive\":true,\"version\":" + v0 + "}")).andExpect(status().isOk());
        long auditFirst = lastAudit("PERSON", "UPDATE", ids.c());
        long v1 = jdbc.queryForObject("SELECT version FROM person WHERE id=?", Long.class, ids.c());
        mvc.perform(put("/api/v1/persons/" + ids.c()).header("Authorization", a).contentType(JSON)
            .content("{\"fullName\":\"Sửa Lần 2\",\"gender\":\"M\",\"alive\":true,\"version\":" + v1 + "}")).andExpect(status().isOk());

        mvc.perform(post("/api/v1/audit/" + auditFirst + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT full_name FROM person WHERE id=?", String.class, ids.c())).isEqualTo("Nguyễn Văn Cường");
        assertThat(jdbc.queryForObject("SELECT nickname FROM person WHERE id=?", String.class, ids.c())).isNull();
        assertThat(jdbc.queryForObject("SELECT before_data->>'fullName' FROM audit_log WHERE entity_type='PERSON' AND action='REVERT' AND entity_id=?", String.class, ids.c())).isEqualTo("Sửa Lần 2");
        assertThat(jdbc.queryForObject("SELECT after_data->>'fullName' FROM audit_log WHERE entity_type='PERSON' AND action='REVERT' AND entity_id=?", String.class, ids.c())).isEqualTo("Nguyễn Văn Cường");
        // @Version vẫn hợp lệ: PUT với version mới thành công
        long v2 = jdbc.queryForObject("SELECT version FROM person WHERE id=?", Long.class, ids.c());
        mvc.perform(put("/api/v1/persons/" + ids.c()).header("Authorization", a).contentType(JSON)
            .content("{\"fullName\":\"Sau Revert\",\"gender\":\"M\",\"alive\":true,\"version\":" + v2 + "}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='PERSON' AND action='REVERT' AND entity_id=?", Integer.class, ids.c())).isEqualTo(1);
    }

    @Test
    void revertBirthOrderClearsOverride() throws Exception {
        var ids = fixture.create();
        String a = admin();
        mvc.perform(put("/api/v1/persons/" + ids.c() + "/birth-order").header("Authorization", a).contentType(JSON).content("{\"order\":2}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/audit/" + lastAudit("PERSON", "SET_BIRTH_ORDER", ids.c()) + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT birth_order_override FROM person WHERE id=?", Integer.class, ids.c())).isNull();
    }

    @Test
    void revertCreateSoftDeletesAndRevertDeleteRestoresIntoClan() throws Exception {
        var ids = fixture.create();
        String a = admin();
        int before = members(ids.clan());
        mvc.perform(delete("/api/v1/persons/" + ids.a()).header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(members(ids.clan())).isLessThan(before);
        mvc.perform(post("/api/v1/audit/" + lastAudit("PERSON", "DELETE", ids.a()) + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM person WHERE id=?", Boolean.class, ids.a())).isTrue();
        assertThat(members(ids.clan())).isEqualTo(before);   // A và cả cành của A trở lại

        // tạo người rồi khôi phục mục CREATE → bị xoá mềm
        String r = mvc.perform(post("/api/v1/persons").header("Authorization", a).contentType(JSON).content("{\"fullName\":\"Tạm\",\"gender\":\"M\"}"))
            .andReturn().getResponse().getContentAsString();
        long newId = om.readTree(r).get("id").asLong();
        mvc.perform(post("/api/v1/audit/" + lastAudit("PERSON", "CREATE", newId) + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM person WHERE id=?", Boolean.class, newId)).isTrue();
    }

    @Test
    void revertLinkCreateAndDelete() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long kid = jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Cháu','M') RETURNING id", Long.class);
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", a).contentType(JSON)
            .content("{\"parentId\":" + ids.a1() + ",\"childId\":" + kid + "}")).andExpect(status().isCreated());
        int withKid = members(ids.clan());
        long createAudit = lastAudit("PARENT_CHILD", "CREATE", jdbc.queryForObject("SELECT id FROM parent_child WHERE child_id=?", Long.class, kid));
        mvc.perform(post("/api/v1/audit/" + createAudit + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_child WHERE child_id=?", Integer.class, kid)).isZero();
        assertThat(members(ids.clan())).isEqualTo(withKid - 1);
        // lần revert thứ hai của cùng mục: quan hệ đã không còn
        mvc.perform(post("/api/v1/audit/" + createAudit + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));

        // gỡ rồi khôi phục việc gỡ
        long linkId = jdbc.queryForObject("SELECT id FROM parent_child WHERE parent_id=? AND child_id=?", Long.class, ids.a(), ids.a1());
        mvc.perform(delete("/api/v1/relations/parent-child/" + linkId).header("Authorization", a)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/audit/" + lastAudit("PARENT_CHILD", "DELETE", linkId) + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_child WHERE parent_id=? AND child_id=?", Integer.class, ids.a(), ids.a1())).isEqualTo(1);
    }

    @Test
    void revertingRemovalThatWouldNowCreateCycleIsRejected() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long linkId = jdbc.queryForObject("SELECT id FROM parent_child WHERE parent_id=? AND child_id=?", Long.class, ids.g(), ids.a());
        mvc.perform(delete("/api/v1/relations/parent-child/" + linkId).header("Authorization", a)).andExpect(status().isNoContent());
        // sau khi gỡ G→A, gắn A1 (cháu của A) làm cha của G? → G là hậu duệ của... tạo quan hệ A → G
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", a).contentType(JSON)
            .content("{\"parentId\":" + ids.a() + ",\"childId\":" + ids.g() + "}")).andExpect(status().isCreated());
        int audits = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='PARENT_CHILD' AND action='REVERT'", Integer.class);
        mvc.perform(post("/api/v1/audit/" + lastAudit("PARENT_CHILD", "DELETE", linkId) + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATION_CYCLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='PARENT_CHILD' AND action='REVERT'", Integer.class)).isEqualTo(audits);
    }

    @Test
    void revertUnionLifecycle() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long wife = jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Vợ','F') RETURNING id", Long.class);
        String resp = mvc.perform(post("/api/v1/relations/unions").header("Authorization", a).contentType(JSON)
            .content("{\"partnerAId\":" + ids.c() + ",\"partnerBId\":" + wife + ",\"startDate\":\"1960-01-01\"}")).andReturn().getResponse().getContentAsString();
        long uid = om.readTree(resp).get("id").asLong();
        mvc.perform(put("/api/v1/relations/unions/" + uid).header("Authorization", a).contentType(JSON).content("{\"status\":\"DIVORCED\",\"endDate\":\"1970-01-01\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/audit/" + lastAudit("UNION", "UPDATE", uid) + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM union_rel WHERE id=?", String.class, uid)).isEqualTo("MARRIED");
        assertThat(jdbc.queryForObject("SELECT end_date FROM union_rel WHERE id=?", java.sql.Date.class, uid)).isNull();

        mvc.perform(delete("/api/v1/relations/unions/" + uid).header("Authorization", a)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/audit/" + lastAudit("UNION", "DELETE", uid) + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM union_rel WHERE partner_a_id=? AND partner_b_id=?", Integer.class, ids.c(), wife)).isEqualTo(1);
    }

    @Test
    void photoAndRevertEntriesAreNotRevertible() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long v = jdbc.queryForObject("SELECT version FROM person WHERE id=?", Long.class, ids.c());
        mvc.perform(put("/api/v1/persons/" + ids.c()).header("Authorization", a).contentType(JSON)
            .content("{\"fullName\":\"Đổi\",\"gender\":\"M\",\"alive\":true,\"version\":" + v + "}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/audit/" + lastAudit("PERSON", "UPDATE", ids.c()) + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/audit/" + lastAudit("PERSON", "REVERT", ids.c()) + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
        jdbc.update("INSERT INTO audit_log(entity_type, entity_id, action) VALUES ('PERSON', ?, 'SET_PHOTO')", ids.c());
        mvc.perform(post("/api/v1/audit/" + lastAudit("PERSON", "SET_PHOTO", ids.c()) + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
    }

    @Test
    void profileRevertKeepsPhotoKeyAndRejectsSoftDeletedPerson() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long v = jdbc.queryForObject("SELECT version FROM person WHERE id=?", Long.class, ids.a1());
        mvc.perform(put("/api/v1/persons/" + ids.a1()).header("Authorization", a).contentType(JSON)
            .content("{\"fullName\":\"Đổi Tên\",\"gender\":\"M\",\"alive\":true,\"version\":" + v + "}")).andExpect(status().isOk());
        long upd = lastAudit("PERSON", "UPDATE", ids.a1());
        em.flush();
        jdbc.update("UPDATE person SET photo_key='photos/x.jpg' WHERE id=?", ids.a1());
        em.clear();
        mvc.perform(post("/api/v1/audit/" + upd + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT photo_key FROM person WHERE id=?", String.class, ids.a1())).isEqualTo("photos/x.jpg");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM person WHERE id=?", Boolean.class, ids.a1())).isTrue();

        mvc.perform(delete("/api/v1/persons/" + ids.a1()).header("Authorization", a)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/audit/" + upd + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
    }

    @Test
    void revertUnionCreateRemovesUnion() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long wife = jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Vợ','F') RETURNING id", Long.class);
        String resp = mvc.perform(post("/api/v1/relations/unions").header("Authorization", a).contentType(JSON)
            .content("{\"partnerAId\":" + ids.c() + ",\"partnerBId\":" + wife + "}")).andReturn().getResponse().getContentAsString();
        long uid = om.readTree(resp).get("id").asLong();
        long createAudit = lastAudit("UNION", "CREATE", uid);
        mvc.perform(post("/api/v1/audit/" + createAudit + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM union_rel WHERE id=?", Integer.class, uid)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='UNION' AND action='REVERT' AND entity_id=?", Integer.class, uid)).isEqualTo(1);
        mvc.perform(post("/api/v1/audit/" + createAudit + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
    }

    @Test
    void doubleRevertOfUnionDeleteDoesNotDuplicate() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long wife = jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Vợ','F') RETURNING id", Long.class);
        for (String status : new String[] {"MARRIED", "DIVORCED"}) {
            long w = status.equals("MARRIED") ? wife : jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Vợ2','F') RETURNING id", Long.class);
            String resp = mvc.perform(post("/api/v1/relations/unions").header("Authorization", a).contentType(JSON)
                .content("{\"partnerAId\":" + ids.c() + ",\"partnerBId\":" + w + ",\"status\":\"" + status + "\",\"startDate\":\"1960-01-01\"}"))
                .andReturn().getResponse().getContentAsString();
            long uid = om.readTree(resp).get("id").asLong();
            mvc.perform(delete("/api/v1/relations/unions/" + uid).header("Authorization", a)).andExpect(status().isNoContent());
            long del = lastAudit("UNION", "DELETE", uid);
            mvc.perform(post("/api/v1/audit/" + del + "/revert").header("Authorization", a)).andExpect(status().isNoContent());
            mvc.perform(post("/api/v1/audit/" + del + "/revert").header("Authorization", a))
               .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM union_rel WHERE partner_a_id=? AND partner_b_id=?", Integer.class, ids.c(), w)).isEqualTo(1);
        }
    }

    @Test
    void missingSnapshotIsNotRevertibleNever500() throws Exception {
        var ids = fixture.create();
        String a = admin();
        long wife = jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Vợ','F') RETURNING id", Long.class);
        String resp = mvc.perform(post("/api/v1/relations/unions").header("Authorization", a).contentType(JSON)
            .content("{\"partnerAId\":" + ids.c() + ",\"partnerBId\":" + wife + "}")).andReturn().getResponse().getContentAsString();
        long uid = om.readTree(resp).get("id").asLong();
        long[][] ents = {{0, ids.c()}, {1, linkIdOf(ids)}, {2, uid}, {3, uid}};
        String[][] types = {{"PERSON", "UPDATE"}, {"PARENT_CHILD", "DELETE"}, {"UNION", "DELETE"}, {"UNION", "UPDATE"}};
        for (int i = 0; i < types.length; i++) {
            long id = jdbc.queryForObject("INSERT INTO audit_log(entity_type, entity_id, action) VALUES (?, ?, ?) RETURNING id", Long.class, types[i][0], ents[i][1], types[i][1]);
            mvc.perform(post("/api/v1/audit/" + id + "/revert").header("Authorization", a))
               .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
        }
        // snapshot hôn phối thiếu "order" (có đủ partners/status)
        for (String act : new String[] {"UPDATE", "DELETE"}) {
            long noOrder = jdbc.queryForObject("INSERT INTO audit_log(entity_type, entity_id, action, before_data) VALUES ('UNION', ?, ?, ?::jsonb) RETURNING id", Long.class,
                uid, act, "{\"partnerAId\":" + ids.a() + ",\"partnerBId\":" + wife + ",\"status\":\"MARRIED\"}");
            mvc.perform(post("/api/v1/audit/" + noOrder + "/revert").header("Authorization", a))
               .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
        }
        long bad = jdbc.queryForObject("INSERT INTO audit_log(entity_type, entity_id, action, before_data) VALUES ('PERSON', ?, 'UPDATE', '{\"gender\":\"ZZ\",\"birthDate\":\"xx\"}'::jsonb) RETURNING id", Long.class, ids.c());
        mvc.perform(post("/api/v1/audit/" + bad + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
    }

    long linkIdOf(vn.giapha.support.TreeFixture.Ids ids) {
        return jdbc.queryForObject("SELECT id FROM parent_child WHERE parent_id=? AND child_id=?", Long.class, ids.a(), ids.a1());
    }
}
