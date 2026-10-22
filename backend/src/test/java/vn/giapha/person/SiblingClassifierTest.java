package vn.giapha.person;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.giapha.person.SiblingClassifier.Kind;
import vn.giapha.person.SiblingClassifier.Parent;

class SiblingClassifierTest {
    static final Parent DAD = new Parent(1, Gender.M), MOM = new Parent(2, Gender.F), MOM2 = new Parent(3, Gender.F),
        DAD2 = new Parent(4, Gender.M), UNK = new Parent(5, Gender.U);

    @Test void sameBothParentsIsFull() { assertThat(SiblingClassifier.classify(Set.of(DAD, MOM), Set.of(DAD, MOM))).isEqualTo(Kind.FULL); }
    @Test void sameFatherDifferentMother() { assertThat(SiblingClassifier.classify(Set.of(DAD, MOM), Set.of(DAD, MOM2))).isEqualTo(Kind.HALF_SAME_FATHER); }
    @Test void sameMotherDifferentFather() { assertThat(SiblingClassifier.classify(Set.of(DAD, MOM), Set.of(DAD2, MOM))).isEqualTo(Kind.HALF_SAME_MOTHER); }
    @Test void onlyFatherKnownOnBothSidesIsFull() { assertThat(SiblingClassifier.classify(Set.of(DAD), Set.of(DAD))).isEqualTo(Kind.FULL); }
    @Test void sharesFatherButSiblingHasExtraMotherIsHalf() { assertThat(SiblingClassifier.classify(Set.of(DAD), Set.of(DAD, MOM2))).isEqualTo(Kind.HALF_SAME_FATHER); }
    @Test void unknownGenderSharedParentSubsetIsFull() { assertThat(SiblingClassifier.classify(Set.of(UNK), Set.of(UNK))).isEqualTo(Kind.FULL); }
}
