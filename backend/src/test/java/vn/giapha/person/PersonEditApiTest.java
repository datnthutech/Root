package vn.giapha.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import vn.giapha.auth.*;
import vn.giapha.support.TestUsers;
import vn.giapha.support.TreeFixture;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, TreeFixture.class})
@Transactional
class PersonEditApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonRepository persons;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

    static final String BODY = "{\"fullName\":\"Trần Mới\",\"gender\":\"M\",\"birthDate\":\"1990-05-06\",\"birthPrecision\":\"DAY\",\"alive\":true}";

    long createAs(String bearer) throws Exception {
        String r = mvc.perform(post("/api/v1/persons").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readTree(r).get("id").asLong();
    }

    @Test
    void viewerAndAnonymousCannotCreate() throws Exception {
        mvc.perform(post("/api/v1/persons").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/persons").header("Authorization", users.bearer(Role.VIEWER)).contentType(MediaType.APPLICATION_JSON).content(BODY))
           .andExpect(status().isForbidden());
    }

    @Test
    void editorCreatesPersonWithCreatorAndAuditRow() throws Exception {
        AppUser editor = users.create(Role.EDITOR);
        long id = createAs(users.bearerFor(editor));
        Person p = persons.findById(id).orElseThrow();
        assertThat(p.getCreatedBy()).isEqualTo(editor.getId());
        assertThat(p.getBirthPrecision()).isEqualTo(DatePrecision.DAY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='PERSON' AND entity_id=? AND action='CREATE' AND user_id=?",
            Integer.class, id, editor.getId())).isEqualTo(1);
    }

    @Test
    void validation() throws Exception {
        String auth = users.bearer(Role.EDITOR);
        for (String bad : new String[]{
            "{\"gender\":\"M\"}",                                                                            // thiếu tên
            "{\"fullName\":\"A\",\"gender\":\"M\",\"alive\":true,\"deathDate\":\"2000-01-01\"}",              // còn sống mà có ngày mất
            "{\"fullName\":\"A\",\"gender\":\"M\",\"alive\":false,\"birthDate\":\"2000-01-01\",\"deathDate\":\"1999-01-01\"}"}) {
            mvc.perform(post("/api/v1/persons").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(bad))
               .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void ownerEditsWithinWindowAndVersionIncrements() throws Exception {
        AppUser editor = users.create(Role.EDITOR);
        String auth = users.bearerFor(editor);
        long id = createAs(auth);
        long v = persons.findById(id).orElseThrow().getVersion();
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Trần Sửa\",\"gender\":\"M\",\"alive\":true,\"version\":" + v + "}"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Trần Sửa"));
        assertThat(persons.findById(id).orElseThrow().getVersion()).isEqualTo(v + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='PERSON' AND entity_id=? AND action='UPDATE'", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void staleVersionGives409AndKeepsFirstWriter() throws Exception {
        AppUser editor = users.create(Role.EDITOR);
        String auth = users.bearerFor(editor);
        long id = createAs(auth);
        long v = persons.findById(id).orElseThrow().getVersion();
        String body = "{\"fullName\":\"%s\",\"gender\":\"M\",\"alive\":true,\"version\":" + v + "}";
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body.formatted("Người Một")))
           .andExpect(status().isOk());
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body.formatted("Người Hai")))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(persons.findById(id).orElseThrow().getFullName()).isEqualTo("Người Một");
    }

    @Test
    void missingVersionIs400() throws Exception {
        AppUser editor = users.create(Role.EDITOR);
        String auth = users.bearerFor(editor);
        long id = createAs(auth);
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"X\",\"gender\":\"M\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void editorCannotEditSomeoneElsesPersonButAdminCan() throws Exception {
        long id = createAs(users.bearer(Role.EDITOR));
        long v = persons.findById(id).orElseThrow().getVersion();
        String body = "{\"fullName\":\"Đổi\",\"gender\":\"M\",\"alive\":true,\"version\":" + v + "}";
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", users.bearer(Role.EDITOR)).contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", users.bearer(Role.VIEWER)).contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", users.bearer(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk());
    }

    @Test
    void ownerAfter24hGetsEditWindowExpired() throws Exception {
        AppUser editor = users.create(Role.EDITOR);
        String auth = users.bearerFor(editor);
        long id = createAs(auth);
        jdbc.update("UPDATE person SET created_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minus(25, ChronoUnit.HOURS)), id);
        entityManager.flush();
        entityManager.clear();
        long v = jdbc.queryForObject("SELECT version FROM person WHERE id = ?", Long.class, id);
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Muộn\",\"gender\":\"M\",\"alive\":true,\"version\":" + v + "}"))
           .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EDIT_WINDOW_EXPIRED"));
    }

    @Test
    void onlyAdminDeletesAndDeleteIsSoft() throws Exception {
        var ids = fixture.create();
        mvc.perform(delete("/api/v1/persons/" + ids.c()).header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/persons/" + ids.c()).header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/persons/" + ids.c())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/persons/" + ids.c()).header("Authorization", users.bearer(Role.ADMIN))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM person WHERE id = ?", Boolean.class, ids.c())).isTrue();
        // đã rời khỏi dòng họ
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clan_member WHERE person_id = ?", Integer.class, ids.c())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id=? AND action='DELETE'", Integer.class, ids.c())).isEqualTo(1);
    }

    @Test
    void cannotDeleteClanFounder() throws Exception {
        var ids = fixture.create();
        mvc.perform(delete("/api/v1/persons/" + ids.g()).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATION_INVALID"));
    }

    @Test
    void deletingLinkedPersonUnlinksAccountButKeepsAccount() throws Exception {
        var ids = fixture.create();
        AppUser u = users.create(Role.VIEWER);
        u.setLinkedPersonId(ids.c());
        jdbc.update("UPDATE app_user SET linked_person_id = ? WHERE id = ?", ids.c(), u.getId());
        mvc.perform(delete("/api/v1/persons/" + ids.c()).header("Authorization", users.bearer(Role.ADMIN))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT linked_person_id FROM app_user WHERE id = ?", Long.class, u.getId())).isNull();
    }

    @Test
    void updateUnknownPersonIs404() throws Exception {
        mvc.perform(put("/api/v1/persons/99999999").header("Authorization", users.bearer(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"X\",\"gender\":\"M\",\"version\":0}")).andExpect(status().isNotFound());
    }

    @Test
    void coarseDatePrecisionIsComparedAtTheCoarserLevel() throws Exception {
        String auth = users.bearer(Role.EDITOR);
        String ok = "{\"fullName\":\"A\",\"gender\":\"M\",\"alive\":false,\"birthDate\":\"1950-03-15\",\"birthPrecision\":\"DAY\","
            + "\"deathDate\":\"1950-01-01\",\"deathPrecision\":\"YEAR\"}";
        mvc.perform(post("/api/v1/persons").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(ok))
           .andExpect(status().isCreated());
        String bad = ok.replace("\"deathDate\":\"1950-01-01\"", "\"deathDate\":\"1949-01-01\"");
        mvc.perform(post("/api/v1/persons").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(bad))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
