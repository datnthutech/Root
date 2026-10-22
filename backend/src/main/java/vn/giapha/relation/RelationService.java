package vn.giapha.relation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.AuditService;
import vn.giapha.audit.Snapshots;
import vn.giapha.auth.AuthUser;
import vn.giapha.clan.ClanMemberService;
import vn.giapha.common.ApiException;

@Service
public class RelationService {
    private final ParentChildService pcs;
    private final UnionService unions;
    private final ParentChildRepository links;
    private final UnionRepository unionRepo;
    private final ClanMemberService clans;
    private final AuditService audit;

    public RelationService(ParentChildService pcs, UnionService unions, ParentChildRepository links, UnionRepository unionRepo,
                           ClanMemberService clans, AuditService audit) {
        this.pcs = pcs;
        this.unions = unions;
        this.links = links;
        this.unionRepo = unionRepo;
        this.clans = clans;
        this.audit = audit;
    }

    @Transactional
    public ParentChildResult addParentChild(AuthUser me, long parentId, long childId, ParentKind kind, Long unionId) {
        ParentChildResult r = pcs.add(parentId, childId, kind, unionId);
        audit.record(me.id(), "PARENT_CHILD", r.link().getId(), "CREATE", null, Snapshots.of(r.link()));
        clans.rebuildClansOf(List.of(parentId, childId));
        return r;
    }

    @Transactional
    public List<ParentChildResult> addChild(AuthUser me, long parentId, long childId, ParentKind kind, Long unionId) {
        List<ParentChildResult> out = new ArrayList<>();
        out.add(pcs.add(parentId, childId, kind, unionId));
        if (unionId != null) {
            UnionRel u = unionRepo.findById(unionId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", "Không tìm thấy hôn phối"));
            long other = u.getPartnerAId() == parentId ? u.getPartnerBId() : u.getPartnerAId();
            out.add(pcs.add(other, childId, kind, unionId));
        }
        out.forEach(r -> audit.record(me.id(), "PARENT_CHILD", r.link().getId(), "CREATE", null, Snapshots.of(r.link())));
        List<Long> involved = new ArrayList<>(List.of(parentId, childId));
        out.forEach(r -> involved.add(r.link().getParentId()));
        clans.rebuildClansOf(involved);
        return out;
    }

    @Transactional
    public void removeParentChild(AuthUser me, long linkId) {
        ParentChild l = links.findById(linkId).orElseThrow(this::notFound);
        var before = Snapshots.of(l);
        pcs.remove(linkId);
        audit.record(me.id(), "PARENT_CHILD", linkId, "DELETE", before, null);
        clans.rebuildClansOf(List.of(l.getParentId(), l.getChildId()));
    }

    @Transactional
    public UnionRel createUnion(AuthUser me, long a, long b, Integer order, UnionStatus status, LocalDate start, LocalDate end) {
        UnionRel u = unions.create(a, b, order, status, start, end);
        audit.record(me.id(), "UNION", u.getId(), "CREATE", null, Snapshots.of(u));
        clans.rebuildClansOf(List.of(a, b));
        return u;
    }

    @Transactional
    public UnionRel updateUnion(AuthUser me, long id, Integer order, UnionStatus status, LocalDate start, LocalDate end) {
        UnionRel existing = unionRepo.findById(id).orElseThrow(this::notFound);
        var before = Snapshots.of(existing);
        UnionRel u = unions.update(id, order, status, start, end);
        audit.record(me.id(), "UNION", id, "UPDATE", before, Snapshots.of(u));
        return u;
    }

    @Transactional
    public void deleteUnion(AuthUser me, long id) {
        UnionRel u = unionRepo.findById(id).orElseThrow(this::notFound);
        var before = Snapshots.of(u);
        unions.delete(id);
        audit.record(me.id(), "UNION", id, "DELETE", before, null);
        clans.rebuildClansOf(List.of(u.getPartnerAId(), u.getPartnerBId()));
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy"); }
}
