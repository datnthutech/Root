package vn.giapha.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
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
class FocusApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired ObjectMapper om;

    Set<Long> personIds(String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", users.bearer(Role.VIEWER)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode t = om.readTree(body);
        Set<Long> s = new HashSet<>();
        t.get("persons").forEach(p -> s.add(p.get("id").asLong()));
        return s;
    }

    @Test
    void focusOnA1ShowsAncestorsSiblingsAndSpousesButNotUncles() throws Exception {
        var ids = fixture.create();
        Set<Long> s = personIds("/api/v1/persons/" + ids.a1() + "/focus?up=3&down=1");
        assertThat(s).contains(ids.a1(), ids.a(), ids.aw(), ids.g(), ids.gw(), ids.a2());
        assertThat(s).doesNotContain(ids.b(), ids.c(), ids.b1());      // chú bác và anh em họ không có mặt
    }

    @Test
    void upLimitCutsAncestors() throws Exception {
        var ids = fixture.create();
        Set<Long> s = personIds("/api/v1/persons/" + ids.a1() + "/focus?up=1&down=0");
        assertThat(s).contains(ids.a1(), ids.a(), ids.aw());
        assertThat(s).doesNotContain(ids.g());
    }

    @Test
    void rootPersonIsTopmostAncestor() throws Exception {
        var ids = fixture.create();
        mvc.perform(get("/api/v1/persons/" + ids.a1() + "/focus?up=3").header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.rootPersonId").value(ids.g()));
    }

    @Test
    void descendantsRespectDownLimit() throws Exception {
        var ids = fixture.create();
        Set<Long> s0 = personIds("/api/v1/persons/" + ids.g() + "/focus?up=0&down=1");
        assertThat(s0).contains(ids.g(), ids.gw(), ids.a(), ids.b(), ids.c());
        assertThat(s0).doesNotContain(ids.a1());
        Set<Long> s1 = personIds("/api/v1/persons/" + ids.g() + "/focus?up=0&down=2");
        assertThat(s1).contains(ids.a1(), ids.a2(), ids.a3(), ids.b1());
    }

    @Test
    void unknownPersonIs404AndAnonymousIs401() throws Exception {
        mvc.perform(get("/api/v1/persons/99999999/focus").header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/persons/1/focus")).andExpect(status().isUnauthorized());
    }

    @Test
    void personWithoutParentsIsOwnRoot() throws Exception {
        var ids = fixture.create();
        mvc.perform(get("/api/v1/persons/" + ids.g() + "/focus").header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.rootPersonId").value(ids.g()));
    }
}
