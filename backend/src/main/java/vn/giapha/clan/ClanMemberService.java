package vn.giapha.clan;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;

@Service
public class ClanMemberService {
    private final JdbcTemplate jdbc;
    private final ClanRepository clans;

    public ClanMemberService(JdbcTemplate jdbc, ClanRepository clans) {
        this.jdbc = jdbc;
        this.clans = clans;
    }

    @Transactional
    public void rebuildClansOf(java.util.Collection<Long> personIds) { rebuildClansOf(personIds, null); }

    @Transactional
    public void rebuildClansOf(java.util.Collection<Long> personIds, Long excludeClanId) {
        if (personIds.isEmpty()) return;
        List<Long> clanIds = jdbc.queryForList(
            "SELECT DISTINCT clan_id FROM clan_member WHERE person_id = ANY(?) ORDER BY clan_id", Long.class,
            (Object) personIds.toArray(Long[]::new));
        for (long clanId : clanIds) {
            if (excludeClanId != null && clanId == excludeClanId) continue;
            boolean hasFounder = clans.findById(clanId).map(c -> c.getFounderPersonId() != null).orElse(false);
            if (hasFounder) rebuild(clanId);
        }
    }

    @Transactional
    public int rebuild(long clanId) {
        Clan clan = clans.findById(clanId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy dòng họ"));
        if (clan.getFounderPersonId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "RELATION_INVALID", "Dòng họ chưa có thủy tổ");
        }
        Map<Long, List<Long>> kids = new HashMap<>();
        jdbc.query("""
            SELECT pc.parent_id, pc.child_id FROM parent_child pc
            JOIN person c ON c.id = pc.child_id AND c.deleted_at IS NULL
            JOIN person p ON p.id = pc.parent_id AND p.deleted_at IS NULL
            """, rs -> { kids.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)); });
        Map<Long, List<Long>> spouses = new HashMap<>();
        jdbc.query("""
            SELECT u.partner_a_id, u.partner_b_id FROM union_rel u
            JOIN person a ON a.id = u.partner_a_id AND a.deleted_at IS NULL
            JOIN person b ON b.id = u.partner_b_id AND b.deleted_at IS NULL
            """, rs -> {
                spouses.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
                spouses.computeIfAbsent(rs.getLong(2), k -> new ArrayList<>()).add(rs.getLong(1));
            });

        var gens = GenerationCalculator.compute(clan.getFounderPersonId(),
            id -> kids.getOrDefault(id, List.of()), id -> spouses.getOrDefault(id, List.of()));

        jdbc.update("DELETE FROM clan_member WHERE clan_id = ?", clanId);
        List<Map.Entry<Long, GenerationCalculator.Gen>> entries = new ArrayList<>(gens.entrySet());
        jdbc.batchUpdate("INSERT INTO clan_member(clan_id, person_id, membership, generation) VALUES (?,?,?,?)",
            entries, 500, (ps, e) -> {
                ps.setLong(1, clanId);
                ps.setLong(2, e.getKey());
                ps.setString(3, e.getValue().membership().name());
                ps.setInt(4, e.getValue().generation());
            });
        return entries.size();
    }
}
