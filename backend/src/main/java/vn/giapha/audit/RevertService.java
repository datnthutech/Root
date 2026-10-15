package vn.giapha.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.auth.AuthUser;
import vn.giapha.clan.ClanMemberService;
import vn.giapha.common.ApiException;
import vn.giapha.person.PersonService;
import vn.giapha.relation.*;

@Service
public class RevertService {
    private record Row(long id, String type, long entityId, String action, JsonNode before, JsonNode after) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;
    private final PersonService persons;
    private final ParentChildService pcs;
    private final ParentChildRepository links;
    private final UnionService unions;
    private final UnionRepository unionRepo;
    private final ClanMemberService clans;
    private final AuditService audit;

    public RevertService(JdbcTemplate jdbc, ObjectMapper om, PersonService persons, ParentChildService pcs, ParentChildRepository links,
                         UnionService unions, UnionRepository unionRepo, ClanMemberService clans, AuditService audit) {
        this.jdbc = jdbc;
        this.om = om;
        this.persons = persons;
        this.pcs = pcs;
        this.links = links;
        this.unions = unions;
        this.unionRepo = unionRepo;
        this.clans = clans;
        this.audit = audit;
    }

    @Transactional
    public void revert(AuthUser me, long auditId) {
        Row r = load(auditId);
        if (!AuditSummary.revertible(r.type(), r.action())) throw notRevertible("Mục này không khôi phục được");
        try {
            switch (r.type()) {
                case "PERSON" -> revertPerson(me, r);
                case "PARENT_CHILD" -> revertLink(me, r);
                case "UNION" -> revertUnion(me, r);
                default -> throw notRevertible("Mục này không khôi phục được");
            }
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            // snapshot không đọc được / thiếu trường bắt buộc
            throw notRevertible("Dữ liệu lịch sử không hợp lệ");
        } catch (ApiException e) {
            // một đối tượng liên quan đã bị xoá: không còn khôi phục được (không phải lỗi "không tìm thấy mục nhật ký")
            if ("NOT_FOUND".equals(e.code())) throw notRevertible("Dữ liệu liên quan không còn tồn tại");
            throw e;
        }
    }

    private void revertPerson(AuthUser me, Row r) {
        switch (r.action()) {
            case "UPDATE", "SET_BIRTH_ORDER" -> persons.applyHistory(me, r.entityId(), requireObject(r.before()));
            case "CREATE" -> persons.softDelete(me, r.entityId(), "REVERT");
            case "DELETE" -> persons.restore(me, r.entityId());
            default -> throw notRevertible("Mục này không khôi phục được");
        }
    }

    private void revertLink(AuthUser me, Row r) {
        if ("CREATE".equals(r.action())) {
            ParentChild l = links.findById(r.entityId()).orElseThrow(() -> notRevertible("Quan hệ này đã bị gỡ"));
            var before = Snapshots.of(l);
            pcs.remove(l.getId());
            audit.record(me.id(), "PARENT_CHILD", l.getId(), "REVERT", before, null);
            clans.rebuildClansOf(List.of(l.getParentId(), l.getChildId()));
        } else {
            LinkSnapView s = om.convertValue(requireObject(r.before()), LinkSnapView.class);
            if (s.parentId() == null || s.childId() == null) throw notRevertible("Dữ liệu lịch sử không hợp lệ");
            Long unionId = s.unionId() != null && unionRepo.existsById(s.unionId()) ? s.unionId() : null;
            var res = pcs.add(s.parentId(), s.childId(), s.kind(), unionId);
            audit.record(me.id(), "PARENT_CHILD", res.link().getId(), "REVERT", null, Snapshots.of(res.link()));
            clans.rebuildClansOf(List.of(s.parentId(), s.childId()));
        }
    }

    private void revertUnion(AuthUser me, Row r) {
        switch (r.action()) {
            case "CREATE" -> {
                UnionRel u = unionRepo.findById(r.entityId()).orElseThrow(() -> notRevertible("Hôn phối này đã bị gỡ"));
                var before = Snapshots.of(u);
                unions.delete(u.getId());
                audit.record(me.id(), "UNION", u.getId(), "REVERT", before, null);
                clans.rebuildClansOf(List.of(u.getPartnerAId(), u.getPartnerBId()));
            }
            case "UPDATE" -> {
                UnionRel u = unionRepo.findById(r.entityId()).orElseThrow(() -> notRevertible("Hôn phối này đã bị gỡ"));
                var before = Snapshots.of(u);
                UnionSnapView s = om.convertValue(requireObject(r.before()), UnionSnapView.class);
                unions.update(u.getId(), s.order(), s.status(), null, null);
                UnionRel fresh = unionRepo.findById(u.getId()).orElseThrow();
                fresh.setStartDate(s.startDate());
                fresh.setEndDate(s.endDate());
                unionRepo.saveAndFlush(fresh);
                audit.record(me.id(), "UNION", u.getId(), "REVERT", before, Snapshots.of(fresh));
            }
            case "DELETE" -> {
                UnionSnapView s = om.convertValue(requireObject(r.before()), UnionSnapView.class);
                if (s.partnerAId() == null || s.partnerBId() == null || s.status() == null) throw notRevertible("Dữ liệu lịch sử không hợp lệ");
                Integer dup = jdbc.queryForObject("""
                    SELECT count(*) FROM union_rel WHERE status = ? AND start_date IS NOT DISTINCT FROM ?::date
                      AND ((partner_a_id = ? AND partner_b_id = ?) OR (partner_a_id = ? AND partner_b_id = ?))
                    """, Integer.class, s.status().name(), s.startDate(), s.partnerAId(), s.partnerBId(), s.partnerBId(), s.partnerAId());
                if (dup != null && dup > 0) throw notRevertible("Hôn phối này đã được khôi phục");
                // Lưu ý: hôn phối tạo lại có id mới và không gắn lại parent_child.union_id cũ (đã bị bỏ khi xoá).
                UnionRel created = unions.create(s.partnerAId(), s.partnerBId(), s.order(), s.status(), s.startDate(), s.endDate());
                audit.record(me.id(), "UNION", created.getId(), "REVERT", null, Snapshots.of(created));
                clans.rebuildClansOf(List.of(s.partnerAId(), s.partnerBId()));
            }
            default -> throw notRevertible("Mục này không khôi phục được");
        }
    }

    private Row load(long id) {
        List<Row> rows = jdbc.query("SELECT id, entity_type, entity_id, action, before_data::text, after_data::text FROM audit_log WHERE id = ?",
            (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), parse(rs.getString(5)), parse(rs.getString(6))), id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy mục nhật ký");
        return rows.get(0);
    }

    private JsonNode parse(String s) {
        if (s == null) return null;
        try { return om.readTree(s); } catch (JsonProcessingException e) { return null; }
    }

    private JsonNode requireObject(JsonNode n) {
        if (n == null || !n.isObject()) throw notRevertible("Mục này không có dữ liệu để khôi phục");
        return n;
    }

    private ApiException notRevertible(String m) { return new ApiException(HttpStatus.CONFLICT, "NOT_REVERTIBLE", m); }

    private record LinkSnapView(Long parentId, Long childId, ParentKind kind, Long unionId) {}
    private record UnionSnapView(Long partnerAId, Long partnerBId, int order, UnionStatus status, java.time.LocalDate startDate, java.time.LocalDate endDate) {}
}
