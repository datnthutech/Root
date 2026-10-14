package vn.giapha.clan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ClanMemberRepository extends JpaRepository<ClanMember, Long> {
    List<ClanMember> findByClanId(Long clanId);
    @Transactional void deleteByClanId(Long clanId);
}
