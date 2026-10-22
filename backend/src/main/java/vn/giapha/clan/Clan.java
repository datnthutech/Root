package vn.giapha.clan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "clan") @Getter @Setter
public class Clan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(name = "founder_person_id") private Long founderPersonId;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(name = "sibling_count_mode", nullable = false) private SiblingCountMode siblingCountMode = SiblingCountMode.PER_UNION;
    @Column(name = "use_ut_label", nullable = false) private boolean useUtLabel = true;
}
