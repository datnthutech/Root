package vn.giapha.tree;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/persons")
@PreAuthorize("hasRole('VIEWER')")
public class PersonTreeController {
    private final FocusService focus;

    public PersonTreeController(FocusService focus) { this.focus = focus; }

    @GetMapping("/{id}/focus")
    public GraphDtos.Graph focus(@PathVariable long id, @RequestParam(defaultValue = "3") int up,
                                 @RequestParam(defaultValue = "3") int down) {
        return focus.focus(id, up, down);
    }
}
