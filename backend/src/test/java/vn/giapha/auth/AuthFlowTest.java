package vn.giapha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.giapha.PostgresTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired ObjectMapper om;

    String name() { return "u" + UUID.randomUUID().toString().substring(0, 10); }

    void register(String u, String p) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\",\"displayName\":\"Tên\"}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    MvcResult login(String u, String p) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}")).andReturn();
    }

    void activate(String u) {
        AppUser x = users.findByUsername(u).orElseThrow();
        x.setStatus(UserStatus.ACTIVE);
        users.save(x);
    }

    @Test
    void pendingAccountCannotLoginButWrongPasswordDoesNotRevealPending() throws Exception {
        String u = name();
        register(u, "password123");
        assertThat(login(u, "password123").getResponse().getStatus()).isEqualTo(403);
        MvcResult wrong = login(u, "nope-nope-nope");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).contains("BAD_CREDENTIALS");
    }

    @Test
    void registerRejectsDuplicateAndWeakInput() throws Exception {
        String u = name();
        register(u, "password123");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + u.toUpperCase() + "\",\"password\":\"password123\"}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"ab\",\"password\":\"short\"}"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
           // thông báo tiếng Việt từ ValidationMessages.properties, frontend hiển thị nguyên văn
           .andExpect(jsonPath("$.details.username").value("Phải từ 3 đến 50 ký tự"))
           .andExpect(jsonPath("$.details.password").value("Tối thiểu 8 ký tự"));
    }

    @Test
    void fullFlowLoginMeRefreshRotateLogout() throws Exception {
        String u = name();
        register(u, "password123");
        activate(u);

        MvcResult r1 = login(u, "password123");
        assertThat(r1.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = om.readTree(r1.getResponse().getContentAsString());
        String access = body.get("accessToken").asText();
        Cookie c1 = r1.getResponse().getCookie("refresh_token");
        assertThat(c1).isNotNull();
        assertThat(c1.isHttpOnly()).isTrue();
        assertThat(c1.getPath()).isEqualTo("/api/v1/auth");

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
           .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(u));

        MvcResult r2 = mvc.perform(post("/api/v1/auth/refresh").cookie(c1)).andExpect(status().isOk()).andReturn();
        Cookie c2 = r2.getResponse().getCookie("refresh_token");
        assertThat(c2.getValue()).isNotEqualTo(c1.getValue());

        mvc.perform(post("/api/v1/auth/refresh").cookie(c1)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout").cookie(c2)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(c2)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void fiveWrongPasswordsLockEvenCorrectPassword() throws Exception {
        String u = name();
        register(u, "password123");
        activate(u);
        for (int i = 0; i < 5; i++) assertThat(login(u, "wrong-wrong-1").getResponse().getStatus()).isEqualTo(401);
        MvcResult locked = login(u, "password123");
        assertThat(locked.getResponse().getStatus()).isEqualTo(423);
        assertThat(locked.getResponse().getContentAsString()).contains("ACCOUNT_LOCKED");
    }

    @Test
    void adminLockedAccountCannotLogin() throws Exception {
        String u = name();
        register(u, "password123");
        AppUser x = users.findByUsername(u).orElseThrow();
        x.setStatus(UserStatus.LOCKED);
        users.save(x);
        assertThat(login(u, "password123").getResponse().getStatus()).isEqualTo(423);
    }

    @Test
    void multibytePasswordOver72BytesIsRejected() throws Exception {
        String pw = "ệ".repeat(30);   // 30 ký tự nhưng 90 byte UTF-8
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + name() + "\",\"password\":\"" + pw + "\"}"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
           .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void lockedAccountWithWrongPasswordIs401AndDoesNotCountAttempts() throws Exception {
        String u = name();
        register(u, "password123");
        activate(u);
        AppUser x = users.findByUsername(u).orElseThrow();
        x.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        x.setFailedAttempts(2);
        users.save(x);
        MvcResult wrong = login(u, "wrong-wrong-1");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).contains("BAD_CREDENTIALS");
        assertThat(users.findByUsername(u).orElseThrow().getFailedAttempts()).isEqualTo(2);
    }

    @Test
    void unknownUsernameIs401BadCredentials() throws Exception {
        MvcResult r = login(name(), "password123");
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(r.getResponse().getContentAsString()).contains("BAD_CREDENTIALS");
    }
}
