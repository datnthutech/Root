package vn.giapha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.giapha.PostgresTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class RefreshReuseTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

    String name() { return "r" + UUID.randomUUID().toString().substring(0, 10); }

    /** Tạo tài khoản ACTIVE và đăng nhập, trả về user id và cookie refresh. */
    long activeUserWithLogin(String u) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + u + "\",\"password\":\"password123\",\"displayName\":\"Tên\"}"))
           .andExpect(status().isCreated());
        AppUser x = users.findByUsername(u).orElseThrow();
        x.setStatus(UserStatus.ACTIVE);
        users.save(x);
        return x.getId();
    }

    Cookie login(String u) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + u + "\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("refresh_token");
    }

    int refreshStatus(Cookie c) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(c)).andReturn().getResponse().getStatus();
    }

    Cookie refreshOk(Cookie c) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/refresh").cookie(c)).andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("refresh_token");
    }

    /** Lùi thời điểm xoay vòng của các token đã bị xoay của user để vượt quá cửa sổ ân hạn. */
    void ageRotatedTokens(long userId) {
        jdbc.update("update refresh_token set rotated_at = now() - interval '60 seconds' "
            + "where user_id = ? and rotated_at is not null", userId);
    }

    long activeTokenCount(long userId) {
        return jdbc.queryForObject("select count(*) from refresh_token where user_id = ? and revoked = false",
            Long.class, userId);
    }

    @Test
    void reuseWithinGraceIsRejectedButNewTokenStillWorks() throws Exception {
        String u = name();
        activeUserWithLogin(u);
        Cookie c1 = login(u);
        Cookie c2 = refreshOk(c1);

        assertThat(refreshStatus(c1)).isEqualTo(401);   // dùng lại ngay: trong cửa sổ ân hạn
        assertThat(refreshStatus(c2)).isEqualTo(200);   // token mới vẫn dùng được
    }

    @Test
    void reuseAfterGraceRevokesAllSessionsIncludingNewest() throws Exception {
        String u = name();
        long userId = activeUserWithLogin(u);
        Cookie c1 = login(u);
        Cookie c2 = refreshOk(c1);

        ageRotatedTokens(userId);
        assertThat(refreshStatus(c1)).isEqualTo(401);   // dùng lại sau ân hạn: bị coi là đánh cắp
        assertThat(refreshStatus(c2)).isEqualTo(401);   // token mới nhất cũng đã bị thu hồi
        assertThat(activeTokenCount(userId)).isZero();  // việc thu hồi được lưu, không bị rollback
    }

    @Test
    void logoutOnlyAffectsThatSession() throws Exception {
        String u = name();
        activeUserWithLogin(u);
        Cookie sessionA = login(u);
        Cookie sessionB = login(u);

        mvc.perform(post("/api/v1/auth/logout").cookie(sessionA)).andExpect(status().isNoContent());
        assertThat(refreshStatus(sessionA)).isEqualTo(401);   // thu hồi do logout: chỉ từ chối
        assertThat(refreshStatus(sessionB)).isEqualTo(200);   // phiên khác không bị ảnh hưởng
    }

    @Test
    void concurrentRefreshWithSameTokenSucceedsOnlyOnce() throws Exception {
        String u = name();
        long userId = activeUserWithLogin(u);
        Cookie c1 = login(u);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return refreshStatus(c1);
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) statuses.add(f.get());

            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        } finally {
            pool.shutdownNow();
        }
        // lệnh 401 nằm trong cửa sổ ân hạn nên không được thu hồi phiên mới: còn đúng một token hoạt động
        assertThat(activeTokenCount(userId)).isEqualTo(1);
    }
}
