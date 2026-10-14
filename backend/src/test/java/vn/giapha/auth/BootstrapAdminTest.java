package vn.giapha.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import vn.giapha.PostgresTestConfig;

@SpringBootTest(properties = {"app.bootstrap-admin.username=Root", "app.bootstrap-admin.password=password123"})
@Import(PostgresTestConfig.class)
class BootstrapAdminTest {
    @Autowired AppUserRepository users;
    @Autowired BootstrapAdmin bootstrap;

    @Test
    void createsActiveAdminWhenNoneExists() throws Exception {
        // Deterministic: other test classes may have committed ADMIN users into the shared DB.
        users.deleteAll(users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN || "root".equals(u.getUsername())).toList());
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();

        bootstrap.run(null);

        AppUser u = users.findByUsername("root").orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.ADMIN);
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
