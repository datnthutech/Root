package vn.giapha.person;

import java.util.Set;

public final class SiblingClassifier {
    public enum Kind { FULL, HALF_SAME_FATHER, HALF_SAME_MOTHER }
    public record Parent(long id, Gender gender) {}

    private SiblingClassifier() {}

    public static Kind classify(Set<Parent> mine, Set<Parent> theirs) {
        if (mine.equals(theirs)) return Kind.FULL;
        boolean sharedFather = mine.stream().anyMatch(p -> p.gender() == Gender.M && theirs.contains(p));
        boolean sharedMother = mine.stream().anyMatch(p -> p.gender() == Gender.F && theirs.contains(p));
        if (sharedFather && sharedMother) return Kind.FULL;
        if (sharedFather) return Kind.HALF_SAME_FATHER;
        if (sharedMother) return Kind.HALF_SAME_MOTHER;
        return mine.containsAll(theirs) || theirs.containsAll(mine) ? Kind.FULL : Kind.HALF_SAME_FATHER;
    }
}
