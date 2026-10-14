package vn.giapha.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
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
class ParentChildServiceTest {
    @Autowired PersonRepository persons;
    @Autowired ParentChildService service;
    @Autowired ParentChildRepository links;

    Person p(String n, Gender g, Integer birthYear) {
        Person x = new Person(); x.setFullName(n); x.setGender(g);
        if (birthYear != null) { x.setBirthDate(LocalDate.of(birthYear, 6, 1)); x.setBirthPrecision(DatePrecision.DAY); }
        return persons.save(x);
    }

    @Test
    void addsLinkAndMarksBiologicalAsCounted() {
        Person dad = p("Cha", Gender.M, 1920), kid = p("Con", Gender.M, 1950);
        var r = service.add(dad.getId(), kid.getId(), null, null);
        assertThat(r.link().getKind()).isEqualTo(ParentKind.BIOLOGICAL);
        assertThat(r.link().isCountsInOrder()).isTrue();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void adoptedChildIsNotCounted() {
        Person dad = p("Cha", Gender.M, 1920), kid = p("Con", Gender.M, 1950);
        assertThat(service.add(dad.getId(), kid.getId(), ParentKind.ADOPTED, null).link().isCountsInOrder()).isFalse();
    }

    @Test
    void selfParentIsRejectedAsCycle() {
        Person a = p("A", Gender.M, null);
        assertThatThrownBy(() -> service.add(a.getId(), a.getId(), null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_CYCLE"));
    }

    @Test
    void descendantCannotBecomeAncestorEvenFiveGenerationsDeep() {
        Person[] g = new Person[6];
        for (int i = 0; i < 6; i++) g[i] = p("G" + i, Gender.M, null);
        for (int i = 0; i < 5; i++) service.add(g[i].getId(), g[i + 1].getId(), null, null);
        assertThatThrownBy(() -> service.add(g[5].getId(), g[0].getId(), null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_CYCLE"));
    }

    @Test
    void secondBiologicalFatherIsRejectedButStepFatherAllowed() {
        Person dad1 = p("Cha1", Gender.M, null), dad2 = p("Cha2", Gender.M, null), kid = p("Con", Gender.M, null);
        service.add(dad1.getId(), kid.getId(), null, null);
        assertThatThrownBy(() -> service.add(dad2.getId(), kid.getId(), null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
        assertThat(service.add(dad2.getId(), kid.getId(), ParentKind.STEP, null).link().getId()).isNotNull();
    }

    @Test
    void duplicateLinkIsRejected() {
        Person dad = p("Cha", Gender.M, null), kid = p("Con", Gender.M, null);
        service.add(dad.getId(), kid.getId(), null, null);
        assertThatThrownBy(() -> service.add(dad.getId(), kid.getId(), ParentKind.ADOPTED, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RELATION_INVALID"));
    }

    @Test
    void unknownPersonIsNotFound() {
        Person dad = p("Cha", Gender.M, null);
        assertThatThrownBy(() -> service.add(dad.getId(), 999999L, null, null))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void warnsWhenChildBornBeforeParent() {
        Person dad = p("Cha", Gender.M, 1960), kid = p("Con", Gender.M, 1950);
        var r = service.add(dad.getId(), kid.getId(), null, null);
        assertThat(r.warnings()).extracting(Warning::code).containsExactly("CHILD_BEFORE_PARENT");
    }

    @Test
    void warnsWhenParentUnder13AtBirth() {
        Person dad = p("Cha", Gender.M, 1950), kid = p("Con", Gender.M, 1960);
        var r = service.add(dad.getId(), kid.getId(), null, null);
        assertThat(r.warnings()).extracting(Warning::code).containsExactly("PARENT_TOO_YOUNG");
    }

    @Test
    void removeDeletesLink() {
        Person dad = p("Cha", Gender.M, null), kid = p("Con", Gender.M, null);
        long id = service.add(dad.getId(), kid.getId(), null, null).link().getId();
        service.remove(id);
        assertThat(links.findById(id)).isEmpty();
    }
}
