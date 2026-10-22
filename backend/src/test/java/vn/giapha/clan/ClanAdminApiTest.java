package vn.giapha.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
class ClanAdminApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired ClanMemberRepository members;
    @Autowired ClanRepository clans;

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void onlyAdminManagesClans() throws Exception {
        String body = "{\"name\":\"Họ Mới\"}";
        mvc.perform(post("/api/v1/admin/clans").contentType(JSON).content(body)).andExpect(status().isUnauthorized());
        for (Role r : new Role[]{Role.VIEWER, Role.EDITOR}) {
            mvc.perform(post("/api/v1/admin/clans").header("Authorization", users.bearer(r)).contentType(JSON).content(body)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/clans").header("Authorization", users.bearer(r))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/admin/clans").header("Authorization", users.bearer(Role.ADMIN)).contentType(JSON).content(body)).andExpect(status().isCreated());
    }

    @Test
    void createWithFounderBuildsMembership() throws Exception {
        var ids = fixture.create();
        mvc.perform(post("/api/v1/admin/clans").header("Authorization", users.bearer(Role.ADMIN)).contentType(JSON)
            .content("{\"name\":\"Họ B\",\"founderPersonId\":" + ids.a() + ",\"siblingCountMode\":\"PER_FATHER\",\"useUtLabel\":false}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.memberCount").value(6))   // A, 2 vợ, 3 con
           .andExpect(jsonPath("$.siblingCountMode").value("PER_FATHER"));
    }

    @Test
    void changingFounderRebuildsAndModeChangesOrdering() throws Exception {
        var ids = fixture.create();
        String admin = users.bearer(Role.ADMIN);
        mvc.perform(put("/api/v1/admin/clans/" + ids.clan()).header("Authorization", admin).contentType(JSON)
            .content("{\"name\":\"Họ Nguyễn\",\"founderPersonId\":" + ids.a() + "}")).andExpect(status().isOk());
        assertThat(members.findByClanId(ids.clan())).hasSize(6);
        // PER_FATHER: A1, A2, A3 cùng cha A → đếm liền → A3 là thứ Tư/Út
        mvc.perform(put("/api/v1/admin/clans/" + ids.clan()).header("Authorization", admin).contentType(JSON)
            .content("{\"name\":\"Họ Nguyễn\",\"founderPersonId\":" + ids.a() + ",\"siblingCountMode\":\"PER_FATHER\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/persons/" + ids.a3()).header("Authorization", admin)).andExpect(jsonPath("$.birthOrder").value(4));
    }

    @Test
    void validationAndNotFound() throws Exception {
        String admin = users.bearer(Role.ADMIN);
        mvc.perform(post("/api/v1/admin/clans").header("Authorization", admin).contentType(JSON).content("{\"name\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/clans").header("Authorization", admin).contentType(JSON)
            .content("{\"name\":\"X\",\"founderPersonId\":99999999}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/clans/99999999").header("Authorization", admin).contentType(JSON).content("{\"name\":\"X\"}")).andExpect(status().isNotFound());
    }

    @Test
    void clearingFounderDeletesMembership() throws Exception {
        var ids = fixture.create();
        String admin = users.bearer(Role.ADMIN);
        mvc.perform(put("/api/v1/admin/clans/" + ids.clan()).header("Authorization", admin).contentType(JSON)
            .content("{\"name\":\"Họ Nguyễn\",\"founderPersonId\":" + ids.a() + "}")).andExpect(status().isOk());
        assertThat(members.findByClanId(ids.clan())).isNotEmpty();
        mvc.perform(put("/api/v1/admin/clans/" + ids.clan()).header("Authorization", admin).contentType(JSON)
            .content("{\"name\":\"Họ Nguyễn\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.memberCount").value(0));
        assertThat(members.findByClanId(ids.clan())).isEmpty();
    }
}
