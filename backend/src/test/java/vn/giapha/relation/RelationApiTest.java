package vn.giapha.relation;

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
import jakarta.persistence.EntityManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.Role;
import vn.giapha.person.*;
import vn.giapha.support.TestUsers;
import vn.giapha.support.TreeFixture;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, TreeFixture.class})
@Transactional
class RelationApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonRepository persons;
    @Autowired ObjectMapper om;
    @Autowired EntityManager entityManager;

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    long newPerson(String name, Gender g) {
        Person p = new Person(); p.setFullName(name); p.setGender(g);
        return persons.saveAndFlush(p).getId();
    }
    int members(long clan) { return jdbc.queryForObject("SELECT count(*) FROM clan_member WHERE clan_id=?", Integer.class, clan); }

    @Test
    void permissionMatrixForEveryWriteEndpoint() throws Exception {
        var ids = fixture.create();
        String body = "{\"parentId\":" + ids.c() + ",\"childId\":" + newPerson("K", Gender.M) + "}";
        String[] urls = {"/api/v1/relations/parent-child", "/api/v1/relations/children"};
        for (String u : urls) {
            mvc.perform(post(u).contentType(JSON).content(body)).andExpect(status().isUnauthorized());
            mvc.perform(post(u).header("Authorization", users.bearer(Role.VIEWER)).contentType(JSON).content(body)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/relations/unions").header("Authorization", users.bearer(Role.VIEWER)).contentType(JSON)
            .content("{\"partnerAId\":" + ids.c() + ",\"partnerBId\":" + newPerson("W", Gender.F) + "}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/relations/parent-child/1").header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/relations/unions/1").header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/relations/unions/1").header("Authorization", users.bearer(Role.VIEWER)).contentType(JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void editorAddsChildAndClanMembershipGrows() throws Exception {
        var ids = fixture.create();
        int before = members(ids.clan());
        long kid = newPerson("Cháu mới", Gender.M);
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"parentId\":" + ids.a1() + ",\"childId\":" + kid + "}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.link.childId").value(kid)).andExpect(jsonPath("$.warnings.length()").value(0));
        assertThat(members(ids.clan())).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT generation FROM clan_member WHERE person_id=?", Integer.class, kid)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='PARENT_CHILD' AND action='CREATE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void addChildWithUnionLinksBothPartners() throws Exception {
        var ids = fixture.create();
        long kid = newPerson("Con chung", Gender.F);
        long unionId = jdbc.queryForObject("SELECT id FROM union_rel WHERE partner_a_id=? AND partner_b_id=?", Long.class, ids.a(), ids.aw());
        mvc.perform(post("/api/v1/relations/children").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"parentId\":" + ids.a() + ",\"childId\":" + kid + ",\"unionId\":" + unionId + "}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.links.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_child WHERE child_id=?", Integer.class, kid)).isEqualTo(2);
    }

    @Test
    void cycleIsRejectedAndMembershipUnchanged() throws Exception {
        var ids = fixture.create();
        int before = members(ids.clan());
        // G là tổ của A1; gắn A1 làm cha của G
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"parentId\":" + ids.a1() + ",\"childId\":" + ids.g() + "}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATION_CYCLE"));
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"parentId\":" + ids.a1() + ",\"childId\":" + ids.a1() + "}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATION_CYCLE"));
        assertThat(members(ids.clan())).isEqualTo(before);
    }

    @Test
    void secondBiologicalFatherIs409AndWarningsAreReturned() throws Exception {
        var ids = fixture.create();
        long dad2 = newPerson("Cha khác", Gender.M);
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"parentId\":" + dad2 + ",\"childId\":" + ids.a1() + "}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATION_INVALID"));
        long young = newPerson("Quá trẻ", Gender.M);
        jdbc.update("UPDATE person SET birth_date='1955-01-01', birth_precision='DAY' WHERE id=?", young);
        long kid = newPerson("Con", Gender.M);
        jdbc.update("UPDATE person SET birth_date='1960-01-01', birth_precision='DAY' WHERE id=?", kid);
        entityManager.flush(); entityManager.clear();
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"parentId\":" + young + ",\"childId\":" + kid + "}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.warnings[0].code").value("PARENT_TOO_YOUNG"));
    }

    @Test
    void removingLinkDropsBranchFromClanButKeepsPerson() throws Exception {
        var ids = fixture.create();
        long linkId = jdbc.queryForObject("SELECT id FROM parent_child WHERE parent_id=? AND child_id=?", Long.class, ids.g(), ids.c());
        long link2 = jdbc.queryForObject("SELECT id FROM parent_child WHERE parent_id=? AND child_id=?", Long.class, ids.gw(), ids.c());
        mvc.perform(delete("/api/v1/relations/parent-child/" + linkId).header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/relations/parent-child/" + link2).header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clan_member WHERE person_id=?", Integer.class, ids.c())).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM person WHERE id=?", Boolean.class, ids.c())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='PARENT_CHILD' AND action='DELETE'", Integer.class)).isEqualTo(2);
        mvc.perform(delete("/api/v1/relations/parent-child/" + linkId).header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isNotFound());
    }

    @Test
    void unionLifecycleAndMembership() throws Exception {
        var ids = fixture.create();
        long wife = newPerson("Vợ C", Gender.F);
        int before = members(ids.clan());
        String resp = mvc.perform(post("/api/v1/relations/unions").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"partnerAId\":" + ids.c() + ",\"partnerBId\":" + wife + "}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MARRIED")).andReturn().getResponse().getContentAsString();
        long uid = om.readTree(resp).get("id").asLong();
        assertThat(members(ids.clan())).isEqualTo(before + 1);
        mvc.perform(put("/api/v1/relations/unions/" + uid).header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"status\":\"DIVORCED\",\"endDate\":\"1990-01-01\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DIVORCED"));
        mvc.perform(delete("/api/v1/relations/unions/" + uid).header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isNoContent());
        assertThat(members(ids.clan())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='UNION'", Integer.class)).isEqualTo(3);
    }

    @Test
    void duplicateActiveUnionAndSelfUnionAre409() throws Exception {
        var ids = fixture.create();
        mvc.perform(post("/api/v1/relations/unions").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"partnerAId\":" + ids.a() + ",\"partnerBId\":" + ids.aw() + "}")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/relations/unions").header("Authorization", users.bearer(Role.EDITOR)).contentType(JSON)
            .content("{\"partnerAId\":" + ids.a() + ",\"partnerBId\":" + ids.a() + "}")).andExpect(status().isConflict());
    }
}
