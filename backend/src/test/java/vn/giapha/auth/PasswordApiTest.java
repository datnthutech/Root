package vn.giapha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.giapha.PostgresTestConfig;
import vn.giapha.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class})
class PasswordApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers tu;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    AppUser userWithPassword(Role role, String pw) {
        AppUser u = tu.create(role);
        u.setPasswordHash(encoder.encode(pw));
        return users.save(u);
    }

    int login(AppUser u, String pw) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + u.getUsername() + "\",\"password\":\"" + pw + "\"}")).andReturn().getResponse().getStatus();
    }

    Cookie loginCookie(AppUser u, String pw) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + u.getUsername() + "\",\"password\":\"" + pw + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");
    }

    String body(String current, String next) { return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"; }

    @Test
    void changePasswordSwitchesPasswordAndRevokesRefreshTokens() throws Exception {
        AppUser u = userWithPassword(Role.VIEWER, "password123");
        Cookie old = loginCookie(u, "password123");
        mvc.perform(put("/api/v1/me/password").header("Authorization", tu.bearerFor(u))
            .contentType(MediaType.APPLICATION_JSON).content(body("password123", "newpassword456"))).andExpect(status().isNoContent());
        assertThat(login(u, "newpassword456")).isEqualTo(200);
        assertThat(login(u, "password123")).isEqualTo(401);
        mvc.perform(post("/api/v1/auth/refresh").cookie(old)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCurrentPasswordIs400() throws Exception {
        AppUser u = userWithPassword(Role.VIEWER, "password123");
        mvc.perform(put("/api/v1/me/password").header("Authorization", tu.bearerFor(u))
            .contentType(MediaType.APPLICATION_JSON).content(body("sai-sai-sai", "newpassword456")))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(login(u, "password123")).isEqualTo(200);
    }

    @Test
    void shortNewPasswordIs400() throws Exception {
        AppUser u = userWithPassword(Role.VIEWER, "password123");
        mvc.perform(put("/api/v1/me/password").header("Authorization", tu.bearerFor(u))
            .contentType(MediaType.APPLICATION_JSON).content(body("password123", "short")))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void changePasswordRequiresLogin() throws Exception {
        mvc.perform(put("/api/v1/me/password").contentType(MediaType.APPLICATION_JSON).content(body("a", "newpassword456")))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void editorCannotResetOthersPassword() throws Exception {
        AppUser target = userWithPassword(Role.VIEWER, "password123");
        mvc.perform(put("/api/v1/admin/users/" + target.getId() + "/password").header("Authorization", tu.bearer(Role.EDITOR))
            .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"newpassword456\"}")).andExpect(status().isForbidden());
    }

    @Test
    void adminResetUnlocksAndRevokesAndNewPasswordWorks() throws Exception {
        AppUser target = userWithPassword(Role.VIEWER, "password123");
        Cookie old = loginCookie(target, "password123");
        target.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        target.setFailedAttempts(3);
        users.save(target);
        mvc.perform(put("/api/v1/admin/users/" + target.getId() + "/password").header("Authorization", tu.bearer(Role.ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"newpassword456\"}")).andExpect(status().isNoContent());
        AppUser after = users.findById(target.getId()).orElseThrow();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getFailedAttempts()).isZero();
        assertThat(login(target, "newpassword456")).isEqualTo(200);
        assertThat(login(target, "password123")).isEqualTo(401);
        mvc.perform(post("/api/v1/auth/refresh").cookie(old)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminResetValidatesAndHandlesUnknownUser() throws Exception {
        String admin = tu.bearer(Role.ADMIN);
        mvc.perform(put("/api/v1/admin/users/99999999/password").header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"newpassword456\"}")).andExpect(status().isNotFound());
        AppUser target = userWithPassword(Role.VIEWER, "password123");
        mvc.perform(put("/api/v1/admin/users/" + target.getId() + "/password").header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"short\"}")).andExpect(status().isBadRequest());
    }
}
