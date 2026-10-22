package vn.giapha.relation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "parent_child") @Getter @Setter
public class ParentChild {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "parent_id", nullable = false) private Long parentId;
    @Column(name = "child_id", nullable = false) private Long childId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ParentKind kind = ParentKind.BIOLOGICAL;
    @Column(name = "union_id") private Long unionId;
    @Column(name = "counts_in_order", nullable = false) private boolean countsInOrder = true;
}
