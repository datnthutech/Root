package vn.giapha.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class BirthOrderApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired JdbcTemplate jdbc;

    String url(long id) { return "/api/v1/persons/" + id + "/birth-order"; }
    String body(String order) { return "{\"order\":" + order + "}"; }

    @Test
    void permissionMatrix() throws Exception {
        var ids = fixture.create();
        mvc.perform(put(url(ids.a())).contentType(MediaType.APPLICATION_JSON).content(body("3"))).andExpect(status().isUnauthorized());
        mvc.perform(put(url(ids.a())).header("Authorization", users.bearer(Role.VIEWER)).contentType(MediaType.APPLICATION_JSON).content(body("3")))
           .andExpect(status().isForbidden());
        mvc.perform(put(url(ids.a())).header("Authorization", users.bearer(Role.EDITOR)).contentType(MediaType.APPLICATION_JSON).content(body("3")))
           .andExpect(status().isOk());
        mvc.perform(put(url(ids.a())).header("Authorization", users.bearer(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body("2")))
           .andExpect(status().isOk());
    }

    @Test
    void overrideChangesLabelsInGraphAndCanBeCleared() throws Exception {
        var ids = fixture.create();
        String auth = users.bearer(Role.EDITOR);
        // C (út) đặt thành thứ Hai → A và B nhường chỗ: A=Ba, B=Tư
        mvc.perform(put(url(ids.c())).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body("2")))
           .andExpect(jsonPath("$.birthOrderOverride").value(2));
        mvc.perform(get("/api/v1/persons/" + ids.c()).header("Authorization", auth)).andExpect(jsonPath("$.orderLabel").value("anh Hai"));
        mvc.perform(get("/api/v1/persons/" + ids.a()).header("Authorization", auth)).andExpect(jsonPath("$.orderLabel").value("anh Ba"));
        mvc.perform(put(url(ids.c())).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body("null"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/persons/" + ids.a()).header("Authorization", auth)).andExpect(jsonPath("$.orderLabel").value("anh Hai"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='SET_BIRTH_ORDER' AND entity_id=?", Integer.class, ids.c())).isEqualTo(2);
    }

    @Test
    void invalidOrderIs400AndUnknownPersonIs404() throws Exception {
        var ids = fixture.create();
        String auth = users.bearer(Role.EDITOR);
        for (String bad : new String[]{"1", "0", "-3", "100"}) {
            mvc.perform(put(url(ids.a())).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(bad)))
               .andExpect(status().isBadRequest());
        }
        mvc.perform(put(url(99999999)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body("3"))).andExpect(status().isNotFound());
    }
}
