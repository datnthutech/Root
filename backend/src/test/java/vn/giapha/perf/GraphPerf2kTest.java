package vn.giapha.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@SpringBootTest(properties = "app.dev-seed.persons=2000")
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class})
class GraphPerf2kTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void graphOf2000PersonsIsFastAndSmall() throws Exception {
        long clan = jdbc.queryForObject("SELECT id FROM clan WHERE name='Họ Mẫu'", Long.class);
        String auth = users.bearer(Role.VIEWER);
        String url = "/api/v1/clans/" + clan + "/graph";
        // làm nóng JIT/pool, đồng thời kiểm tra phản hồi hợp lệ
        var warm = mvc.perform(get(url).header("Authorization", auth)).andReturn().getResponse();
        assertThat(warm.getStatus()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(warm.getContentAsByteArray()).get("persons").size()).isGreaterThanOrEqualTo(1900);
        long[] samples = new long[3];
        byte[] json = null;
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            var res = mvc.perform(get(url).header("Authorization", auth)).andReturn().getResponse();
            samples[i] = (System.nanoTime() - t0) / 1_000_000;
            assertThat(res.getStatus()).isEqualTo(200);
            json = res.getContentAsByteArray();
        }
        java.util.Arrays.sort(samples);
        long ms = samples[1];   // trung vị của 3 lần đo
        var bos = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(bos)) { gz.write(json); }
        System.out.printf("PERF 2k: %d ms, json=%d KB, gzip=%d KB%n", ms, json.length / 1024, bos.size() / 1024);
        assertThat(ms).isLessThan(800);
        assertThat(bos.size()).isLessThan(120 * 1024);
    }
}
