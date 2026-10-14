package vn.giapha.clan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.person.*;
import vn.giapha.relation.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class ClanMemberServiceTest {
    @Autowired PersonRepository persons;
    @Autowired UnionRepository unions;
    @Autowired ParentChildRepository links;
    @Autowired ClanRepository clans;
    @Autowired ClanMemberRepository members;
    @Autowired ClanMemberService service;

    Person p(String n) { Person x = new Person(); x.setFullName(n); return persons.save(x); }

    @Test
    void rebuildStoresGenerationAndMembership() {
        Person root = p("Tổ"), wife = p("Vợ tổ"), kid = p("Con"), softDeleted = p("Đã xoá");
        UnionRel u = new UnionRel(); u.setPartnerAId(root.getId()); u.setPartnerBId(wife.getId()); unions.save(u);
        for (Person parent : new Person[]{root, wife}) {
            ParentChild pc = new ParentChild(); pc.setParentId(parent.getId()); pc.setChildId(kid.getId()); links.save(pc);
        }
        ParentChild gone = new ParentChild(); gone.setParentId(root.getId()); gone.setChildId(softDeleted.getId()); links.save(gone);
        softDeleted.setDeletedAt(java.time.Instant.now()); persons.saveAndFlush(softDeleted);

        Clan c = new Clan(); c.setName("Họ"); c.setFounderPersonId(root.getId()); c = clans.save(c);
        int n = service.rebuild(c.getId());
        assertThat(n).isEqualTo(3);
        var byPerson = members.findByClanId(c.getId()).stream()
            .collect(java.util.stream.Collectors.toMap(ClanMember::getPersonId, m -> m));
        assertThat(byPerson.get(root.getId()).getGeneration()).isEqualTo(1);
        assertThat(byPerson.get(wife.getId()).getMembership()).isEqualTo(Membership.IN_LAW);
        assertThat(byPerson.get(kid.getId()).getGeneration()).isEqualTo(2);
        assertThat(byPerson).doesNotContainKey(softDeleted.getId());
    }
}
