package vn.giapha.person;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.giapha.auth.AuthUser;

@RestController
@RequestMapping("/api/v1/persons")
public class PersonEditController {
    private final PersonService service;
    private final PersonProfileService profiles;

    public PersonEditController(PersonService service, PersonProfileService profiles) {
        this.service = service;
        this.profiles = profiles;
    }

    @PostMapping
    @PreAuthorize("hasRole('EDITOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonProfile create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PersonInput in) {
        Person p = service.create(me, in);
        return profiles.profile(p.getId(), me.role());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public PersonProfile update(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @Valid @RequestBody PersonInput in) {
        service.update(me, id, in);
        return profiles.profile(id, me.role());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        service.delete(me, id);
    }
}
