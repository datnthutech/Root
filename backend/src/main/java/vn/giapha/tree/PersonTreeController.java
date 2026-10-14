package vn.giapha.tree;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/persons")
@PreAuthorize("hasRole('VIEWER')")
public class PersonTreeController {
    private final FocusService focus;
    private final SearchService search;

    public PersonTreeController(FocusService focus, SearchService search) {
        this.focus = focus;
        this.search = search;
    }

    @GetMapping("/{id}/focus")
    public GraphDtos.Graph focus(@PathVariable long id, @RequestParam(defaultValue = "3") int up,
                                 @RequestParam(defaultValue = "3") int down) {
        return focus.focus(id, up, down);
    }

    @GetMapping("/search")
    public java.util.List<SearchService.Hit> search(@RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "20") int limit) {
        return search.search(q, limit);
    }
}
