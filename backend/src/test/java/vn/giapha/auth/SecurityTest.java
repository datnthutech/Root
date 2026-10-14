package vn.giapha.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.giapha.PostgresTestConfig;
import vn.giapha.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class})
class SecurityTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;

    @Test
    void anonymousGets401Json() throws Exception {
        mvc.perform(get("/api/v1/me"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void garbageTokenGets401() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer abc"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReturnsMe() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", users.bearer(Role.EDITOR)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.role").value("EDITOR"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
