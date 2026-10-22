package vn.giapha.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.giapha.PostgresTestConfig;

@SpringBootTest(properties = "app.dev-seed.persons=300")
@Import(PostgresTestConfig.class)
class DevSeedRunnerTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void seedsExactlyRequestedPersonsIntoOneClan() {
        Integer members = jdbc.queryForObject(
            "SELECT count(*) FROM clan_member cm JOIN clan c ON c.id = cm.clan_id WHERE c.name = 'Họ Mẫu'", Integer.class);
        assertThat(members).isEqualTo(300);
        Integer dangling = jdbc.queryForObject("""
            SELECT count(*) FROM parent_child pc
            LEFT JOIN person p ON p.id = pc.parent_id LEFT JOIN person c ON c.id = pc.child_id
            WHERE p.id IS NULL OR c.id IS NULL""", Integer.class);
        assertThat(dangling).isZero();
    }
}
