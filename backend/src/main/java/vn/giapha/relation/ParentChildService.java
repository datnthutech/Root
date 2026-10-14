package vn.giapha.relation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.person.Gender;
import vn.giapha.person.Person;
import vn.giapha.person.PersonRepository;

@Service
public class ParentChildService {
    private final PersonRepository persons;
    private final ParentChildRepository links;
    private final UnionRepository unions;
    private final CycleChecker cycles;

    public ParentChildService(PersonRepository persons, ParentChildRepository links, UnionRepository unions, CycleChecker cycles) {
        this.persons = persons;
        this.links = links;
        this.unions = unions;
        this.cycles = cycles;
    }

    @Transactional
    public ParentChildResult add(long parentId, long childId, ParentKind kind, Long unionId) {
        ParentKind k = kind == null ? ParentKind.BIOLOGICAL : kind;
        if (parentId == childId) throw cycle("Một người không thể là cha/mẹ của chính mình");
        Person parent = persons.findByIdAndDeletedAtIsNull(parentId).orElseThrow(this::notFound);
        Person child = persons.findByIdAndDeletedAtIsNull(childId).orElseThrow(this::notFound);
        if (cycles.isDescendant(childId, parentId)) throw cycle("Tạo vòng lặp: cha/mẹ mới đang là hậu duệ của người con");

        List<ParentChild> existing = links.findByChildId(childId);
        if (existing.stream().anyMatch(l -> l.getParentId().equals(parentId))) {
            throw invalid("Quan hệ cha/mẹ - con này đã tồn tại");
        }
        if (k == ParentKind.BIOLOGICAL) checkBiologicalSlot(parent, existing);
        if (unionId != null) {
            UnionRel u = unions.findById(unionId).orElseThrow(() -> invalid("Không tìm thấy hôn phối"));
            if (!u.getPartnerAId().equals(parentId) && !u.getPartnerBId().equals(parentId)) {
                throw invalid("Hôn phối không thuộc về cha/mẹ này");
            }
        }

        ParentChild link = new ParentChild();
        link.setParentId(parentId);
        link.setChildId(childId);
        link.setKind(k);
        link.setUnionId(unionId);
        link.setCountsInOrder(k == ParentKind.BIOLOGICAL);
        return new ParentChildResult(links.save(link), warnings(parent, child));
    }

    @Transactional
    public void remove(long linkId) {
        if (!links.existsById(linkId)) throw notFound();
        links.deleteById(linkId);
    }

    private void checkBiologicalSlot(Person parent, List<ParentChild> existing) {
        List<Person> bioParents = existing.stream().filter(l -> l.getKind() == ParentKind.BIOLOGICAL)
            .map(l -> persons.findById(l.getParentId()).orElseThrow(this::notFound)).toList();
        if (parent.getGender() == Gender.U) {
            if (bioParents.size() >= 2) throw invalid("Đã đủ cha và mẹ ruột");
            return;
        }
        boolean taken = bioParents.stream().anyMatch(p -> p.getGender() == parent.getGender());
        if (taken) throw invalid(parent.getGender() == Gender.M ? "Đã có cha ruột" : "Đã có mẹ ruột");
    }

    private List<Warning> warnings(Person parent, Person child) {
        List<Warning> w = new ArrayList<>();
        LocalDate pb = parent.getBirthDate(), cb = child.getBirthDate();
        if (pb != null && cb != null) {
            if (cb.isBefore(pb)) w.add(new Warning("CHILD_BEFORE_PARENT", "Con sinh trước cha/mẹ"));
            else if (ChronoUnit.YEARS.between(pb, cb) < 13) w.add(new Warning("PARENT_TOO_YOUNG", "Cha/mẹ dưới 13 tuổi lúc sinh con"));
        }
        return w;
    }

    private ApiException cycle(String m) { return new ApiException(HttpStatus.CONFLICT, "RELATION_CYCLE", m); }
    private ApiException invalid(String m) { return new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", m); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy"); }
}
