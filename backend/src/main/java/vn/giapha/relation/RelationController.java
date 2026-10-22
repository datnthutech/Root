package vn.giapha.relation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.giapha.auth.AuthUser;

@RestController
@RequestMapping("/api/v1/relations")
@PreAuthorize("hasRole('EDITOR')")
public class RelationController {
    public record LinkRequest(@NotNull Long parentId, @NotNull Long childId, ParentKind kind, Long unionId) {}
    public record LinkDto(Long id, Long parentId, Long childId, ParentKind kind, Long unionId, boolean countsInOrder) {
        static LinkDto of(ParentChild l) { return new LinkDto(l.getId(), l.getParentId(), l.getChildId(), l.getKind(), l.getUnionId(), l.isCountsInOrder()); }
    }
    public record LinkResponse(LinkDto link, List<Warning> warnings) {}
    public record LinksResponse(List<LinkDto> links, List<Warning> warnings) {}
    public record UnionCreateRequest(@NotNull Long partnerAId, @NotNull Long partnerBId, Integer order, UnionStatus status, LocalDate startDate, LocalDate endDate) {}
    public record UnionUpdateRequest(Integer order, UnionStatus status, LocalDate startDate, LocalDate endDate) {}
    public record UnionResponse(Long id, Long partnerAId, Long partnerBId, int order, UnionStatus status, LocalDate startDate, LocalDate endDate) {
        static UnionResponse of(UnionRel u) { return new UnionResponse(u.getId(), u.getPartnerAId(), u.getPartnerBId(), u.getUnionOrder(), u.getStatus(), u.getStartDate(), u.getEndDate()); }
    }

    private final RelationService service;

    public RelationController(RelationService service) { this.service = service; }

    @PostMapping("/parent-child")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse addLink(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody LinkRequest r) {
        var res = service.addParentChild(me, r.parentId(), r.childId(), r.kind(), r.unionId());
        return new LinkResponse(LinkDto.of(res.link()), res.warnings());
    }

    @PostMapping("/children")
    @ResponseStatus(HttpStatus.CREATED)
    public LinksResponse addChild(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody LinkRequest r) {
        var res = service.addChild(me, r.parentId(), r.childId(), r.kind(), r.unionId());
        return new LinksResponse(res.stream().map(x -> LinkDto.of(x.link())).toList(),
            res.stream().flatMap(x -> x.warnings().stream()).distinct().collect(Collectors.toList()));
    }

    @DeleteMapping("/parent-child/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLink(@AuthenticationPrincipal AuthUser me, @PathVariable long id) { service.removeParentChild(me, id); }

    @PostMapping("/unions")
    @ResponseStatus(HttpStatus.CREATED)
    public UnionResponse createUnion(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UnionCreateRequest r) {
        return UnionResponse.of(service.createUnion(me, r.partnerAId(), r.partnerBId(), r.order(), r.status(), r.startDate(), r.endDate()));
    }

    @PutMapping("/unions/{id}")
    public UnionResponse updateUnion(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @RequestBody UnionUpdateRequest r) {
        return UnionResponse.of(service.updateUnion(me, id, r.order(), r.status(), r.startDate(), r.endDate()));
    }

    @DeleteMapping("/unions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnion(@AuthenticationPrincipal AuthUser me, @PathVariable long id) { service.deleteUnion(me, id); }
}
