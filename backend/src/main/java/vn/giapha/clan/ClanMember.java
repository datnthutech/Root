package vn.giapha.clan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "clan_member") @Getter @Setter
public class ClanMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "clan_id", nullable = false) private Long clanId;
    @Column(name = "person_id", nullable = false) private Long personId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Membership membership;
    @Column(nullable = false) private int generation;
}
