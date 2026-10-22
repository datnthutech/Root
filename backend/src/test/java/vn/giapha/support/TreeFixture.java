package vn.giapha.support;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import vn.giapha.clan.*;
import vn.giapha.person.*;
import vn.giapha.relation.*;

@TestComponent
public class TreeFixture {
    public record Ids(long clan, long g, long gw, long a, long aw, long aw2, long b, long bh, long c,
                      long a1, long a2, long a3, long b1) {}

    @Autowired PersonRepository persons;
    @Autowired UnionService unions;
    @Autowired ParentChildService links;
    @Autowired ClanRepository clans;
    @Autowired ClanMemberService members;
    @Autowired EntityManager em;

    private Person p(String name, Gender g, int year, boolean alive) {
        Person x = new Person();
        x.setFullName(name);
        x.setGender(g);
        x.setBirthDate(LocalDate.of(year, 1, 1));
        x.setBirthPrecision(DatePrecision.YEAR);
        x.setAlive(alive);
        if (!alive) { x.setDeathDate(LocalDate.of(year + 70, 1, 1)); x.setDeathPrecision(DatePrecision.YEAR); }
        return persons.save(x);
    }

    private void child(Person kid, UnionRel u, Person... parents) {
        for (Person parent : parents) links.add(parent.getId(), kid.getId(), ParentKind.BIOLOGICAL, u.getId());
    }

    public Ids create() {
        Person g = p("Nguyễn Văn Tổ", Gender.M, 1900, false), gw = p("Trần Thị Bà", Gender.F, 1905, false);
        Person a = p("Nguyễn Văn An", Gender.M, 1925, false), b = p("Nguyễn Thị Bình", Gender.F, 1928, false),
               c = p("Nguyễn Văn Cường", Gender.M, 1930, false);
        Person aw = p("Lê Thị Anh", Gender.F, 1928, false), aw2 = p("Phạm Thị Hoa", Gender.F, 1935, false),
               bh = p("Đặng Văn Hùng", Gender.M, 1926, false);
        Person a1 = p("Nguyễn Văn Một", Gender.M, 1950, true), a3 = p("Nguyễn Văn Ba", Gender.M, 1960, true),
               b1 = p("Đặng Văn Bảo", Gender.M, 1955, true);
        Person a2 = new Person();
        a2.setFullName("Nguyễn Thị Hai"); a2.setGender(Gender.F);
        a2.setBirthDate(LocalDate.of(1953, 4, 5)); a2.setBirthPrecision(DatePrecision.DAY); a2.setAlive(true);
        a2 = persons.save(a2);

        UnionRel ug = unions.create(g.getId(), gw.getId(), null, null, null, null);
        child(a, ug, g, gw); child(b, ug, g, gw); child(c, ug, g, gw);
        UnionRel ua1 = unions.create(a.getId(), aw.getId(), null, null, null, null);
        UnionRel ua2 = unions.create(a.getId(), aw2.getId(), null, null, null, null);
        child(a1, ua1, a, aw); child(a2, ua1, a, aw); child(a3, ua2, a, aw2);
        UnionRel ub = unions.create(b.getId(), bh.getId(), null, null, null, null);
        child(b1, ub, b, bh);

        Clan clan = new Clan();
        clan.setName("Họ Nguyễn (mẫu)");
        clan.setFounderPersonId(g.getId());
        clan = clans.save(clan);
        members.rebuild(clan.getId());
        em.flush();
        return new Ids(clan.getId(), g.getId(), gw.getId(), a.getId(), aw.getId(), aw2.getId(), b.getId(), bh.getId(),
            c.getId(), a1.getId(), a2.getId(), a3.getId(), b1.getId());
    }
}
