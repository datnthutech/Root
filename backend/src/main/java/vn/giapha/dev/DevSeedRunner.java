package vn.giapha.dev;

import java.sql.Date;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import vn.giapha.clan.ClanMemberService;

/** Chỉ dùng cho dev/đo hiệu năng: bật bằng DEV_SEED_PERSONS=N. */
@Component
public class DevSeedRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final ClanMemberService members;
    private final int total;

    public DevSeedRunner(JdbcTemplate jdbc, ClanMemberService members, @Value("${app.dev-seed.persons}") int total) {
        this.jdbc = jdbc;
        this.members = members;
        this.total = total;
    }

    private long person(String name, String gender, int year, boolean alive) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                INSERT INTO person(full_name, gender, birth_date, birth_precision, is_alive, death_date, death_precision)
                VALUES (?,?,?,'YEAR',?,?,?)""", new String[]{"id"});
            ps.setString(1, name);
            ps.setString(2, gender);
            ps.setDate(3, Date.valueOf(year + "-01-01"));
            ps.setBoolean(4, alive);
            ps.setDate(5, alive ? null : Date.valueOf((year + 70) + "-01-01"));
            ps.setString(6, alive ? "UNKNOWN" : "YEAR");
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    private long union(long a, long b) {
        return jdbc.queryForObject("INSERT INTO union_rel(partner_a_id, partner_b_id, union_order) VALUES (?,?,1) RETURNING id",
            Long.class, a, b);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (total <= 0) return;
        Integer clans = jdbc.queryForObject("SELECT count(*) FROM clan", Integer.class);
        if (clans != null && clans > 0) return;

        Random rnd = new Random(42);
        int[] count = {0};
        record Lead(long id, String gender, int year) {}
        Deque<Lead> queue = new ArrayDeque<>();
        long root = person("Tổ Mẫu", "M", 1800, false);
        count[0]++;
        queue.add(new Lead(root, "M", 1800));
        List<long[]> links = new ArrayList<>();   // {parent, child, union}
        while (!queue.isEmpty() && count[0] < total) {
            Lead p = queue.poll();
            long sp = person("Vợ/chồng " + p.id(), p.gender().equals("M") ? "F" : "M", p.year() + rnd.nextInt(6), p.year() > 1950);
            count[0]++;
            long u = union(p.id(), sp);
            int kids = 1 + rnd.nextInt(4);
            for (int i = 0; i < kids && count[0] < total; i++) {
                int y = p.year() + 22 + i * 2;
                String g = rnd.nextBoolean() ? "M" : "F";
                long c = person("Người " + (count[0] + 1), g, y, y > 1950);
                count[0]++;
                links.add(new long[]{p.id(), c, u});
                links.add(new long[]{sp, c, u});
                queue.add(new Lead(c, g, y));
            }
        }
        jdbc.batchUpdate("INSERT INTO parent_child(parent_id, child_id, union_id) VALUES (?,?,?)", links, 1000, (ps, l) -> {
            ps.setLong(1, l[0]);
            ps.setLong(2, l[1]);
            ps.setLong(3, l[2]);
        });
        long clanId = jdbc.queryForObject("INSERT INTO clan(name, founder_person_id) VALUES ('Họ Mẫu', ?) RETURNING id", Long.class, root);
        members.rebuild(clanId);
    }
}
