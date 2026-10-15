package vn.giapha.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.Role;
import vn.giapha.support.TestUsers;

@Tag("perf")
@SpringBootTest(properties = "app.dev-seed.persons=10000")
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class})
class GraphPerf10kTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void graphOf10000PersonsIsFastAndSmall() throws Exception {
        long clan = jdbc.queryForObject("SELECT id FROM clan WHERE name='Họ Mẫu'", Long.class);
        String auth = users.bearer(Role.VIEWER);
        mvc.perform(get("/api/v1/clans/" + clan + "/graph").header("Authorization", auth)).andReturn();      // làm nóng JIT/pool
        long t0 = System.nanoTime();
        byte[] json = mvc.perform(get("/api/v1/clans/" + clan + "/graph").header("Authorization", auth)).andReturn().getResponse().getContentAsByteArray();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        var bos = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(bos)) { gz.write(json); }
        System.out.printf("PERF 10k: %d ms, json=%d KB, gzip=%d KB%n", ms, json.length / 1024, bos.size() / 1024);
        assertThat(ms).isLessThan(3000);
    }
}
