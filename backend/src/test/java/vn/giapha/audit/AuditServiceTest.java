package vn.giapha.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.person.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class AuditServiceTest {
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonRepository persons;

    @Test
    void recordsBeforeAndAfterAsJsonWithDates() {
        Person p = new Person();
        p.setFullName("Nguyễn A"); p.setBirthDate(LocalDate.of(1950, 1, 2)); p.setBirthPrecision(DatePrecision.DAY);
        p = persons.save(p);
        audit.record(null, "PERSON", p.getId(), "CREATE", null, Snapshots.of(p));
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT action, before_data::text AS b, after_data ->> 'fullName' AS name, after_data ->> 'birthDate' AS bd FROM audit_log WHERE entity_id = ?", p.getId());
        assertThat(row.get("action")).isEqualTo("CREATE");
        assertThat(row.get("b")).isNull();
        assertThat(row.get("name")).isEqualTo("Nguyễn A");
        assertThat(row.get("bd")).isEqualTo("1950-01-02");
    }
}
