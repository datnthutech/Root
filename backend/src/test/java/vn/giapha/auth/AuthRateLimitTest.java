package vn.giapha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import vn.giapha.support.TestUsers;

@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class})
class AuthRateLimitTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;

    MvcResult login(String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"u" + UUID.randomUUID().toString().substring(0, 10) + "\",\"password\":\"sai-sai-sai\"}")
            .with(r -> { r.setRemoteAddr(ip); return r; })).andReturn();
    }

    @Test
    void eleventhLoginFromSameIpIsBlockedWith429() throws Exception {
        String ip = "10.1.0.1";
        for (int i = 0; i < 10; i++) assertThat(login(ip).getResponse().getStatus()).isNotEqualTo(429);
        MvcResult blocked = login(ip);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(blocked.getResponse().getHeader("Retry-After"))).isBetween(1, 60);
    }

    @Test
    void differentIpsAreCountedSeparately() throws Exception {
        for (int i = 0; i < 11; i++) login("10.2.0.1");
        assertThat(login("10.2.0.1").getResponse().getStatus()).isEqualTo(429);
        assertThat(login("10.2.0.2").getResponse().getStatus()).isNotEqualTo(429);
    }

    @Test
    void registerIsLimitedToFivePerHour() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"r" + UUID.randomUUID().toString().substring(0, 10) + "\",\"password\":\"password123\"}")
                .with(r -> { r.setRemoteAddr("10.3.0.1"); return r; })).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"abcdef\",\"password\":\"password123\"}")
            .with(r -> { r.setRemoteAddr("10.3.0.1"); return r; })).andExpect(status().isTooManyRequests());
    }

    @Test
    void otherEndpointsAreNotAffected() throws Exception {
        for (int i = 0; i < 11; i++) login("10.4.0.1");
        String bearer = testUsers.bearer(Role.VIEWER);
        for (int i = 0; i < 15; i++) {
            mvc.perform(get("/api/v1/me").header("Authorization", bearer)
                .with(r -> { r.setRemoteAddr("10.4.0.1"); return r; })).andExpect(status().isOk());
        }
    }
}
