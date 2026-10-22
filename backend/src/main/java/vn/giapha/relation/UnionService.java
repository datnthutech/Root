package vn.giapha.relation;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.person.PersonRepository;

@Service
public class UnionService {
    private final UnionRepository unions;
    private final PersonRepository persons;

    public UnionService(UnionRepository unions, PersonRepository persons) {
        this.unions = unions;
        this.persons = persons;
    }

    @Transactional
    public UnionRel create(long a, long b, Integer order, UnionStatus status, LocalDate start, LocalDate end) {
        if (a == b) throw invalid("Không thể tự kết hôn với chính mình");
        persons.findByIdAndDeletedAtIsNull(a).orElseThrow(this::notFound);
        persons.findByIdAndDeletedAtIsNull(b).orElseThrow(this::notFound);
        UnionStatus st = status == null ? UnionStatus.MARRIED : status;
        if (st == UnionStatus.MARRIED) ensureNoActivePair(a, b, null);
        UnionRel u = new UnionRel();
        u.setPartnerAId(a);
        u.setPartnerBId(b);
        u.setStatus(st);
        u.setUnionOrder(order != null ? order : 1 + unions.findByPartnerAIdOrPartnerBId(a, a).size());
        u.setStartDate(start);
        u.setEndDate(end);
        return unions.save(u);
    }

    @Transactional
    public UnionRel update(long id, Integer order, UnionStatus status, LocalDate start, LocalDate end) {
        UnionRel u = unions.findById(id).orElseThrow(this::notFound);
        if (status == UnionStatus.MARRIED && u.getStatus() != UnionStatus.MARRIED) {
            ensureNoActivePair(u.getPartnerAId(), u.getPartnerBId(), id);
        }
        if (order != null) u.setUnionOrder(order);
        if (status != null) u.setStatus(status);
        if (start != null) u.setStartDate(start);
        if (end != null) u.setEndDate(end);
        return unions.saveAndFlush(u);
    }

    @Transactional
    public void delete(long id) {
        if (!unions.existsById(id)) throw notFound();
        unions.deleteById(id);
        unions.flush();
    }

    private void ensureNoActivePair(long a, long b, Long exceptId) {
        List<UnionRel> mine = unions.findByPartnerAIdOrPartnerBId(a, a);
        boolean dup = mine.stream().anyMatch(x -> x.getStatus() == UnionStatus.MARRIED
            && !x.getId().equals(exceptId)
            && ((x.getPartnerAId() == a && x.getPartnerBId() == b) || (x.getPartnerAId() == b && x.getPartnerBId() == a)));
        if (dup) throw invalid("Cặp này đã có hôn phối đang hiệu lực");
    }

    private ApiException invalid(String m) { return new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", m); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy"); }
}
