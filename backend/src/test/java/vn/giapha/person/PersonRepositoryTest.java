package vn.giapha.person;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class PersonRepositoryTest {
    @Autowired PersonRepository repo;

    @Test
    void savesAndIncrementsVersion() {
        Person p = new Person();
        p.setFullName("Nguyễn Văn A");
        p.setGender(Gender.M);
        p.setBirthDate(LocalDate.of(1950, 1, 2));
        p.setBirthPrecision(DatePrecision.DAY);
        p = repo.saveAndFlush(p);
        assertThat(p.getId()).isNotNull();
        assertThat(p.getVersion()).isEqualTo(0L);
        p.setNickname("Ba");
        p = repo.saveAndFlush(p);
        assertThat(p.getVersion()).isEqualTo(1L);
    }

    @Test
    void softDeletedPersonIsNotFoundByActiveQuery() {
        Person p = new Person();
        p.setFullName("B");
        p = repo.saveAndFlush(p);
        p.setDeletedAt(java.time.Instant.now());
        repo.saveAndFlush(p);
        assertThat(repo.findByIdAndDeletedAtIsNull(p.getId())).isEmpty();
    }
}
