package vn.giapha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestConfig.class)
class MigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void allTablesExist() {
        List<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='public'", String.class);
        assertThat(tables).contains("person", "union_rel", "parent_child", "clan", "clan_member",
            "app_user", "refresh_token", "audit_log");
    }

    @Test
    void unaccentHelperHandlesVietnamese() {
        String s = jdbc.queryForObject("SELECT lower(f_unaccent('Nguyễn Đức Thắng'))", String.class);
        assertThat(s).isEqualTo("nguyen duc thang");
    }

    @Test
    void cannotMarryTwiceWhileMarried() {
        jdbc.update("INSERT INTO person(id, full_name) VALUES (9001,'A'),(9002,'B')");
        jdbc.update("INSERT INTO union_rel(partner_a_id,partner_b_id,union_order,status) VALUES (9001,9002,1,'MARRIED')");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
            jdbc.update("INSERT INTO union_rel(partner_a_id,partner_b_id,union_order,status) VALUES (9002,9001,1,'MARRIED')"));
        jdbc.update("DELETE FROM union_rel WHERE partner_a_id=9001");
        jdbc.update("DELETE FROM person WHERE id IN (9001,9002)");
    }
}
