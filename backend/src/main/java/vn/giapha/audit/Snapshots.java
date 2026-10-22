package vn.giapha.audit;

import java.time.LocalDate;
import vn.giapha.person.*;
import vn.giapha.relation.*;

public final class Snapshots {
    private Snapshots() {}

    public record PersonSnap(Long id, String fullName, String nickname, Gender gender, LocalDate birthDate, DatePrecision birthPrecision,
        String birthLunar, LocalDate deathDate, DatePrecision deathPrecision, String deathAnniversaryLunar, boolean alive,
        String originPlace, String homePlace, String burialPlace, String biography, String photoKey, Integer birthOrderOverride,
        boolean deleted) {}
    public record LinkSnap(Long id, Long parentId, Long childId, ParentKind kind, Long unionId, boolean countsInOrder) {}
    public record UnionSnap(Long id, Long partnerAId, Long partnerBId, int order, UnionStatus status, LocalDate startDate, LocalDate endDate) {}

    public static PersonSnap of(Person p) {
        return new PersonSnap(p.getId(), p.getFullName(), p.getNickname(), p.getGender(), p.getBirthDate(), p.getBirthPrecision(),
            p.getBirthLunar(), p.getDeathDate(), p.getDeathPrecision(), p.getDeathAnniversaryLunar(), p.isAlive(),
            p.getOriginPlace(), p.getHomePlace(), p.getBurialPlace(), p.getBiography(), p.getPhotoKey(),
            p.getBirthOrderOverride(), p.getDeletedAt() != null);
    }
    public static LinkSnap of(ParentChild l) {
        return new LinkSnap(l.getId(), l.getParentId(), l.getChildId(), l.getKind(), l.getUnionId(), l.isCountsInOrder());
    }
    public static UnionSnap of(UnionRel u) {
        return new UnionSnap(u.getId(), u.getPartnerAId(), u.getPartnerBId(), u.getUnionOrder(), u.getStatus(), u.getStartDate(), u.getEndDate());
    }
}
