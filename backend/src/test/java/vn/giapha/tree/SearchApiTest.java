package vn.giapha.tree;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
class SearchApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;

    String auth() { return users.bearer(Role.VIEWER); }

    @Test
    void findsWithoutDiacriticsAndCase() throws Exception {
        var ids = fixture.create();
        mvc.perform(get("/api/v1/persons/search").param("q", "nguyen van an").header("Authorization", auth()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.id==" + ids.a() + ")].name").value("Nguyễn Văn An"));
    }

    @Test
    void findsDWithStroke() throws Exception {
        var ids = fixture.create();
        mvc.perform(get("/api/v1/persons/search").param("q", "dang van hung").header("Authorization", auth()))
           .andExpect(jsonPath("$[?(@.id==" + ids.bh() + ")]").exists());
    }

    @Test
    void percentSignDoesNotMatchEverything() throws Exception {
        fixture.create();
        mvc.perform(get("/api/v1/persons/search").param("q", "%").header("Authorization", auth()))
           .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blankQueryIs400() throws Exception {
        mvc.perform(get("/api/v1/persons/search").param("q", "  ").header("Authorization", auth())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/persons/search").header("Authorization", auth())).andExpect(status().isBadRequest());
    }

    @Test
    void limitIsClampedAndAnonymousIs401() throws Exception {
        fixture.create();
        mvc.perform(get("/api/v1/persons/search").param("q", "n").param("limit", "2").header("Authorization", auth()))
           .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/persons/search").param("q", "n")).andExpect(status().isUnauthorized());
    }
}
