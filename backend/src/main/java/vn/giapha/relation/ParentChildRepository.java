package vn.giapha.relation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentChildRepository extends JpaRepository<ParentChild, Long> {
    List<ParentChild> findByChildId(Long childId);
    List<ParentChild> findByParentId(Long parentId);
}
