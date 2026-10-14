package vn.giapha.person;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.giapha.auth.AuthUser;

@RestController
@RequestMapping("/api/v1/persons")
@PreAuthorize("hasRole('VIEWER')")
public class PersonController {
    private final PersonProfileService profiles;

    public PersonController(PersonProfileService profiles) { this.profiles = profiles; }

    @GetMapping("/{id}")
    public PersonProfile get(@PathVariable long id, @AuthenticationPrincipal AuthUser me) {
        return profiles.profile(id, me.role());
    }

    @GetMapping("/{id}/biography")
    public BiographyResponse biography(@PathVariable long id) { return profiles.biography(id); }
}
