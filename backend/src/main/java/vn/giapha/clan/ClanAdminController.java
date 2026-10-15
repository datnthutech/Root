package vn.giapha.clan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/clans")
@PreAuthorize("hasRole('ADMIN')")
public class ClanAdminController {
    public record ClanRequest(@NotBlank @Size(max = 150) String name, Long founderPersonId, String description,
                              SiblingCountMode siblingCountMode, Boolean useUtLabel) {}

    private final ClanAdminService service;

    public ClanAdminController(ClanAdminService service) { this.service = service; }

    @GetMapping
    public List<ClanAdminService.ClanView> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClanAdminService.ClanView create(@Valid @RequestBody ClanRequest r) {
        return service.create(r.name(), r.founderPersonId(), r.description(), r.siblingCountMode(), r.useUtLabel());
    }

    @PutMapping("/{id}")
    public ClanAdminService.ClanView update(@PathVariable long id, @Valid @RequestBody ClanRequest r) {
        return service.update(id, r.name(), r.founderPersonId(), r.description(), r.siblingCountMode(), r.useUtLabel());
    }
}
