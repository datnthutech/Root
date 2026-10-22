package vn.giapha.person;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.relation.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class FamilyServiceTest {
    @Autowired PersonRepository persons;
    @Autowired UnionService unions;
    @Autowired ParentChildService links;
    @Autowired FamilyService family;

    Person p(String n, Gender g) { Person x = new Person(); x.setFullName(n); x.setGender(g); return persons.save(x); }

    @Test
    void childrenOfUnionWithSoftDeletedPartnerFallToOtherChildren() {
        Person dad = p("Cha", Gender.M), mom = p("Mẹ", Gender.F), kid = p("Con", Gender.M);
        UnionRel u = unions.create(dad.getId(), mom.getId(), null, null, null, null);
        links.add(dad.getId(), kid.getId(), null, u.getId());
        mom.setDeletedAt(java.time.Instant.now()); persons.saveAndFlush(mom);

        var f = family.family(dad.getId());
        assertThat(f.spouses()).isEmpty();
        assertThat(f.otherChildren()).extracting(FamilyService.PersonRef::id).containsExactly(kid.getId());
    }
}
