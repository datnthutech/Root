package vn.giapha.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.giapha.common.ApiException;

@RestController
@RequestMapping("/api/v1")
public class MeController {
    private final AppUserRepository users;
    private final AuthService auth;

    public MeController(AppUserRepository users, AuthService auth) { this.users = users; this.auth = auth; }

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) @vn.giapha.common.MaxUtf8Bytes String newPassword) {}

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

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser principal, @Valid @RequestBody ChangePasswordRequest r) {
        auth.changePassword(principal.id(), r.currentPassword(), r.newPassword());
    }
}
