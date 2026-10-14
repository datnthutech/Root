package vn.giapha.auth;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.person.PersonRepository;

@Service
public class AdminUserService {
    public record AdminUserView(long id, String username, String displayName, Role role, UserStatus status,
                                Long linkedPersonId, Instant createdAt) {
        static AdminUserView of(AppUser u) {
            return new AdminUserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getStatus(),
                u.getLinkedPersonId(), u.getCreatedAt());
        }
    }

    private final AppUserRepository users;
    private final PersonRepository persons;
    private final PasswordEncoder encoder;

    public AdminUserService(AppUserRepository users, PersonRepository persons, PasswordEncoder encoder) {
        this.users = users;
        this.persons = persons;
        this.encoder = encoder;
    }

    public List<AdminUserView> list(UserStatus status) {
        return (status == null ? users.findAll() : users.findByStatus(status)).stream().map(AdminUserView::of).toList();
    }

    @Transactional
    public AdminUserView create(String username, String password, String displayName, Role role, Long linkedPersonId) {
        String name = username.toLowerCase(Locale.ROOT);
        if (users.existsByUsername(name)) throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Tên đăng nhập đã tồn tại");
        checkPerson(linkedPersonId);
        AppUser u = new AppUser();
        u.setUsername(name);
        u.setPasswordHash(encoder.encode(password));
        u.setDisplayName(displayName);
        u.setRole(role == null ? Role.VIEWER : role);
        u.setLinkedPersonId(linkedPersonId);
        u.setStatus(UserStatus.ACTIVE);
        return AdminUserView.of(users.save(u));
    }

    @Transactional
    public AdminUserView update(long actorId, long id, String displayName, Role role, UserStatus status, Long linkedPersonId) {
        AppUser u = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài khoản"));
        if (actorId == id && ((role != null && role != Role.ADMIN) || (status != null && status != UserStatus.ACTIVE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Không thể tự hạ quyền hoặc tự khoá chính mình");
        }
        if (displayName != null) u.setDisplayName(displayName);
        if (role != null) u.setRole(role);
        if (linkedPersonId != null) { checkPerson(linkedPersonId); u.setLinkedPersonId(linkedPersonId); }
        if (status != null) {
            u.setStatus(status);
            if (status == UserStatus.ACTIVE) { u.setLockedUntil(null); u.setFailedAttempts(0); }
        }
        return AdminUserView.of(users.save(u));
    }

    private void checkPerson(Long personId) {
        if (personId != null && persons.findByIdAndDeletedAtIsNull(personId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Người được gắn không tồn tại");
        }
    }
}
