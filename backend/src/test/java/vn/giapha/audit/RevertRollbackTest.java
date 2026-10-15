package vn.giapha.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.Role;
import vn.giapha.support.TestUsers;
import vn.giapha.support.TreeFixture;

/** Không dùng @Transactional ở lớp test (commit thật): chứng minh revert bị từ chối không đổi trạng thái, không ghi dòng REVERT, và lỗi giữa chừng được rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, TreeFixture.class})
class RevertRollbackTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;
    @Autowired TransactionTemplate tx;

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    static final String TABLES = "audit_log, clan_member, clan, parent_child, union_rel, refresh_token, person, app_user";

    @BeforeEach @AfterEach
    void clean() { jdbc.execute("TRUNCATE " + TABLES + " RESTART IDENTITY CASCADE"); }

    long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    @Test
    void rejectedRevertLeavesStateUnchangedAndWritesNoRevertRow() throws Exception {
        var ids = tx.execute(s -> fixture.create());
        String a = users.bearer(Role.ADMIN);

        // 1. gỡ liên kết A1 -> rồi xoá mềm người con; revert việc gỡ phải thất bại (NOT_REVERTIBLE)
        long linkId = count("SELECT id FROM parent_child WHERE parent_id=? AND child_id=?", ids.a(), ids.a1());
        mvc.perform(delete("/api/v1/relations/parent-child/" + linkId).header("Authorization", a)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/persons/" + ids.a1()).header("Authorization", a)).andExpect(status().isNoContent());
        long delAudit = count("SELECT id FROM audit_log WHERE entity_type='PARENT_CHILD' AND action='DELETE' AND entity_id=?", linkId);
        long links = count("SELECT count(*) FROM parent_child"), members = count("SELECT count(*) FROM clan_member"),
             audits = count("SELECT count(*) FROM audit_log");
        mvc.perform(post("/api/v1/audit/" + delAudit + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REVERTIBLE"));
        assertThat(count("SELECT count(*) FROM parent_child")).isEqualTo(links);
        assertThat(count("SELECT count(*) FROM clan_member")).isEqualTo(members);
        assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(audits);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='REVERT'")).isZero();
    }

    @Test
    void rejectedCycleRevertLeavesStateUnchanged() throws Exception {
        var ids = tx.execute(s -> fixture.create());
        String a = users.bearer(Role.ADMIN);
        long linkId = count("SELECT id FROM parent_child WHERE parent_id=? AND child_id=?", ids.g(), ids.a());
        mvc.perform(delete("/api/v1/relations/parent-child/" + linkId).header("Authorization", a)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", a).contentType(JSON)
            .content("{\"parentId\":" + ids.a() + ",\"childId\":" + ids.g() + "}")).andExpect(status().isCreated());
        long delAudit = count("SELECT id FROM audit_log WHERE entity_type='PARENT_CHILD' AND action='DELETE' AND entity_id=?", linkId);
        long links = count("SELECT count(*) FROM parent_child"), members = count("SELECT count(*) FROM clan_member"),
             audits = count("SELECT count(*) FROM audit_log");
        mvc.perform(post("/api/v1/audit/" + delAudit + "/revert").header("Authorization", a))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATION_CYCLE"));
        assertThat(count("SELECT count(*) FROM parent_child")).isEqualTo(links);
        assertThat(count("SELECT count(*) FROM clan_member")).isEqualTo(members);
        assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(audits);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='REVERT'")).isZero();
    }

    @Test
    void failureAfterPartialWritesRollsBackUnionAndRevertRow() throws Exception {
        var ids = tx.execute(s -> fixture.create());
        String a = users.bearer(Role.ADMIN);
        long wife = jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Vợ','F') RETURNING id", Long.class);
        String resp = mvc.perform(post("/api/v1/relations/unions").header("Authorization", a).contentType(JSON)
            .content("{\"partnerAId\":" + ids.c() + ",\"partnerBId\":" + wife + "}")).andReturn().getResponse().getContentAsString();
        long uid = om.readTree(resp).get("id").asLong();
        mvc.perform(delete("/api/v1/relations/unions/" + uid).header("Authorization", a)).andExpect(status().isNoContent());
        long delAudit = count("SELECT id FROM audit_log WHERE entity_type='UNION' AND action='DELETE' AND entity_id=?", uid);
        // làm bước dựng lại clan_member (sau khi union đã tạo và dòng REVERT đã ghi) thất bại
        jdbc.update("UPDATE clan SET founder_person_id = NULL WHERE id=?", ids.clan());
        long audits = count("SELECT count(*) FROM audit_log");
        mvc.perform(post("/api/v1/audit/" + delAudit + "/revert").header("Authorization", a)).andExpect(status().isConflict());
        assertThat(count("SELECT count(*) FROM union_rel WHERE partner_a_id=? AND partner_b_id=?", ids.c(), wife)).isZero();
        assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(audits);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='REVERT'")).isZero();
    }
}
