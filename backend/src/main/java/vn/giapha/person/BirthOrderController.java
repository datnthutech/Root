package vn.giapha.person;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.giapha.auth.AuthUser;

@RestController
@RequestMapping("/api/v1/persons")
public class BirthOrderController {
    public record Request(Integer order) {}
    public record Response(long personId, Integer birthOrderOverride) {}

    private final PersonService service;

    public BirthOrderController(PersonService service) { this.service = service; }

    @PutMapping("/{id}/birth-order")
    @PreAuthorize("hasRole('EDITOR')")
    public Response set(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @RequestBody Request r) {
        Person p = service.setBirthOrder(me, id, r.order());
        return new Response(p.getId(), p.getBirthOrderOverride());
    }
}
