package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.clan.*;
import vn.giapha.person.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class SiblingOrderServiceTest {
    @Autowired PersonRepository persons;
    @Autowired UnionRepository unions;
    @Autowired ParentChildRepository links;
    @Autowired ClanRepository clans;
    @Autowired ClanMemberRepository members;
    @Autowired SiblingOrderService service;

    Person p(String n, Gender g, int year) {
        Person x = new Person(); x.setFullName(n); x.setGender(g);
        x.setBirthDate(LocalDate.of(year, 1, 1)); x.setBirthPrecision(DatePrecision.YEAR);
        return persons.save(x);
    }
    void link(Person parent, Person child, UnionRel u, boolean counts) {
        ParentChild pc = new ParentChild(); pc.setParentId(parent.getId()); pc.setChildId(child.getId());
        pc.setUnionId(u == null ? null : u.getId()); pc.setCountsInOrder(counts); links.save(pc);
    }

    @Test
    void computesLabelsWithUtForLastChildAndSkipsAdopted() {
        Person dad = p("Cha", Gender.M, 1920), mom = p("Mẹ", Gender.F, 1925);
        UnionRel u = new UnionRel(); u.setPartnerAId(dad.getId()); u.setPartnerBId(mom.getId()); u = unions.save(u);
        Person a = p("A", Gender.M, 1950), b = p("B", Gender.F, 1952), c = p("C", Gender.F, 1955), adopted = p("D", Gender.M, 1953);
        for (Person k : new Person[]{a, b, c}) { link(dad, k, u, true); link(mom, k, u, true); }
        link(dad, adopted, u, false);

        Clan clan = new Clan(); clan.setName("Họ Test"); clan.setFounderPersonId(dad.getId()); clan = clans.save(clan);
        for (Person x : new Person[]{dad, mom, a, b, c, adopted}) {
            ClanMember m = new ClanMember(); m.setClanId(clan.getId()); m.setPersonId(x.getId());
            m.setMembership(Membership.BLOOD); m.setGeneration(1); members.save(m);
        }

        var r = service.computeForClan(clan.getId());
        assertThat(r.get(a.getId()).label()).isEqualTo("anh Hai");
        assertThat(r.get(b.getId()).label()).isEqualTo("chị Ba");
        assertThat(r.get(c.getId()).label()).isEqualTo("chị Út (thứ Tư)");
        assertThat(r).doesNotContainKey(adopted.getId());
        assertThat(r).doesNotContainKey(dad.getId());
    }
}
