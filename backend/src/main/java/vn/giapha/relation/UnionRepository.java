package vn.giapha.relation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnionRepository extends JpaRepository<UnionRel, Long> {
    List<UnionRel> findByPartnerAIdOrPartnerBId(Long a, Long b);
}
