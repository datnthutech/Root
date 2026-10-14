package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.common.ApiException;
import vn.giapha.person.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class UnionServiceTest {
    @Autowired PersonRepository persons;
    @Autowired UnionService service;
    @Autowired UnionRepository unions;
    @Autowired ParentChildService pcs;
    @Autowired ParentChildRepository links;
    @Autowired EntityManager entityManager;

    Person p(String n) { Person x = new Person(); x.setFullName(n); return persons.save(x); }

    @Test
    void createDefaultsOrderAndStatus() {
        Person a = p("A"), b = p("B"), c = p("C");
        UnionRel u1 = service.create(a.getId(), b.getId(), null, null, null, null);
        UnionRel u2 = service.create(a.getId(), c.getId(), null, null, null, null);
        assertThat(u1.getUnionOrder()).isEqualTo(1);
        assertThat(u2.getUnionOrder()).isEqualTo(2);
        assertThat(u1.getStatus()).isEqualTo(UnionStatus.MARRIED);
    }

    @Test
    void cannotMarrySelf() {
        Person a = p("A");
        assertThatThrownBy(() -> service.create(a.getId(), a.getId(), null, null, null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
    }

    @Test
    void cannotCreateSecondActiveUnionForSamePairInEitherOrder() {
        Person a = p("A"), b = p("B");
        service.create(a.getId(), b.getId(), null, null, null, null);
        assertThatThrownBy(() -> service.create(b.getId(), a.getId(), null, null, null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
    }

    @Test
    void canRemarryAfterDivorce() {
        Person a = p("A"), b = p("B");
        UnionRel u = service.create(a.getId(), b.getId(), null, null, null, null);
        service.update(u.getId(), null, UnionStatus.DIVORCED, null, null);
        assertThat(service.create(a.getId(), b.getId(), null, null, null, null).getId()).isNotEqualTo(u.getId());
    }

    @Test
    void cannotReactivateIntoDuplicateMarriage() {
        Person a = p("A"), b = p("B");
        UnionRel old = service.create(a.getId(), b.getId(), null, null, null, null);
        service.update(old.getId(), null, UnionStatus.DIVORCED, null, null);
        service.create(a.getId(), b.getId(), null, null, null, null);
        assertThatThrownBy(() -> service.update(old.getId(), null, UnionStatus.MARRIED, null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
    }

    @Test
    void deleteKeepsChildLinkButClearsUnionId() {
        Person a = p("A"), b = p("B"), kid = p("K");
        UnionRel u = service.create(a.getId(), b.getId(), null, null, null, null);
        long linkId = pcs.add(a.getId(), kid.getId(), null, u.getId()).link().getId();
        service.delete(u.getId());
        entityManager.clear();
        assertThat(links.findById(linkId)).isPresent();
        assertThat(links.findById(linkId).get().getUnionId()).isNull();
    }
}
