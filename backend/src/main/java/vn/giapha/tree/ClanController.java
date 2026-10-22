package vn.giapha.tree;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clans")
@PreAuthorize("hasRole('VIEWER')")
public class ClanController {
    private final GraphService graphs;

    public ClanController(GraphService graphs) { this.graphs = graphs; }

    @GetMapping
    public List<GraphDtos.ClanSummary> list() { return graphs.listClans(); }

    @GetMapping("/{id}/graph")
    public ResponseEntity<GraphDtos.Graph> graph(@PathVariable long id, @RequestParam(required = false) String side) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
            .body(graphs.clanGraph(id, Side.parse(side)));
    }
}
