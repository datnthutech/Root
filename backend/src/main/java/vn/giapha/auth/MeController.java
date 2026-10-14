package vn.giapha.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.common.ApiException;

@RestController
@RequestMapping("/api/v1")
public class MeController {
    private final AppUserRepository users;

    public MeController(AppUserRepository users) { this.users = users; }

    public record MeResponse(long id, String username, String displayName, Role role, Long linkedPersonId) {
        static MeResponse of(AppUser u) {
            return new MeResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getLinkedPersonId());
        }
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser principal) {
        AppUser u = users.findById(principal.id())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Tài khoản không còn tồn tại"));
        return MeResponse.of(u);
    }
}
