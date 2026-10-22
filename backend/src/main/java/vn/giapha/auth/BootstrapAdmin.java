package vn.giapha.auth;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public BootstrapAdmin(AppUserRepository users, PasswordEncoder encoder,
                          @Value("${app.bootstrap-admin.username}") String username,
                          @Value("${app.bootstrap-admin.password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank() || users.existsByRole(Role.ADMIN)) return;
        AppUser u = new AppUser();
        u.setUsername(username.toLowerCase(Locale.ROOT));
        u.setPasswordHash(encoder.encode(password));
        u.setDisplayName("Quản trị viên");
        u.setRole(Role.ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        users.save(u);
    }
}
