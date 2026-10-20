package vn.giapha.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    public record CreateRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[a-zA-Z0-9._-]+") String username,
        @NotBlank @Size(min = 8) @vn.giapha.common.MaxUtf8Bytes String password,
        @Size(max = 120) String displayName, Role role, Long linkedPersonId) {}
    public record UpdateRequest(@Size(max = 120) String displayName, Role role, UserStatus status, Long linkedPersonId) {}

    public record ResetPasswordRequest(
        @NotBlank @Size(min = 8) @vn.giapha.common.MaxUtf8Bytes String newPassword) {}

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public List<AdminUserService.AdminUserView> list(@RequestParam(required = false) UserStatus status) {
        return service.list(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserService.AdminUserView create(@Valid @RequestBody CreateRequest r) {
        return service.create(r.username(), r.password(), r.displayName(), r.role(), r.linkedPersonId());
    }

    @PutMapping("/{id}")
    public AdminUserService.AdminUserView update(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                                 @Valid @RequestBody UpdateRequest r) {
        return service.update(me.id(), id, r.displayName(), r.role(), r.status(), r.linkedPersonId());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest r) {
        service.resetPassword(id, r.newPassword());
    }
}
