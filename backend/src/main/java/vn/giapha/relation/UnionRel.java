package vn.giapha.relation;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "union_rel") @Getter @Setter
public class UnionRel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "partner_a_id", nullable = false) private Long partnerAId;
    @Column(name = "partner_b_id", nullable = false) private Long partnerBId;
    @Column(name = "union_order", nullable = false) private int unionOrder = 1;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UnionStatus status = UnionStatus.MARRIED;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
}
