package vn.giapha.auth;

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
import vn.giapha.PostgresTestConfig;
import vn.giapha.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class})
class AdminUserApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers tu;
    @Autowired AppUserRepository users;

    @Test
    void onlyAdminCanListUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", tu.bearer(Role.VIEWER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", tu.bearer(Role.EDITOR))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", tu.bearer(Role.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void onlyAdminCanUpdateOrCreate() throws Exception {
        AppUser target = tu.create(Role.VIEWER);
        String body = "{\"role\":\"EDITOR\"}";
        for (Role r : new Role[]{Role.VIEWER, Role.EDITOR}) {
            mvc.perform(put("/api/v1/admin/users/" + target.getId()).header("Authorization", tu.bearer(r))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/users").header("Authorization", tu.bearer(r))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminApprovesPendingUserAndAssignsRole() throws Exception {
        AppUser pending = tu.create(Role.VIEWER);
        pending.setStatus(UserStatus.PENDING);
        users.save(pending);
        mvc.perform(put("/api/v1/admin/users/" + pending.getId()).header("Authorization", tu.bearer(Role.ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\",\"role\":\"EDITOR\"}"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.role").value("EDITOR"));
        assertThat(users.findById(pending.getId()).orElseThrow().getRole()).isEqualTo(Role.EDITOR);
    }

    @Test
    void unlockingClearsTemporaryLock() throws Exception {
        AppUser u = tu.create(Role.VIEWER);
        u.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        u.setFailedAttempts(3);
        users.save(u);
        mvc.perform(put("/api/v1/admin/users/" + u.getId()).header("Authorization", tu.bearer(Role.ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
        AppUser after = users.findById(u.getId()).orElseThrow();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getFailedAttempts()).isZero();
    }

    @Test
    void adminCannotLockOrDemoteSelf() throws Exception {
        AppUser admin = tu.create(Role.ADMIN);
        String bearer = tu.bearerFor(admin);
        mvc.perform(put("/api/v1/admin/users/" + admin.getId()).header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/users/" + admin.getId()).header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void adminCreatesPreApprovedAccount() throws Exception {
        String name = "pre" + System.nanoTime();
        mvc.perform(post("/api/v1/admin/users").header("Authorization", tu.bearer(Role.ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + name + "\",\"password\":\"password123\",\"displayName\":\"X\",\"role\":\"EDITOR\"}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.role").value("EDITOR"));
    }

    @Test
    void updatingUnknownUserIs404() throws Exception {
        mvc.perform(put("/api/v1/admin/users/99999999").header("Authorization", tu.bearer(Role.ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}")).andExpect(status().isNotFound());
    }
}
