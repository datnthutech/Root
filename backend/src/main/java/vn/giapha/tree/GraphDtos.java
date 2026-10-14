package vn.giapha.tree;

import java.util.List;
import vn.giapha.clan.Membership;
import vn.giapha.clan.SiblingCountMode;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.relation.UnionStatus;

public final class GraphDtos {
    private GraphDtos() {}

    public record Person(long id, String name, Gender gender, Integer birthYear, Integer deathYear, boolean alive,
                         String thumb, Integer birthOrder, String orderLabel, int generation, Membership membership) {}
    public record Union(long id, long partnerAId, long partnerBId, int order, UnionStatus status) {}
    public record Link(long parentId, long childId, Long unionId, ParentKind kind) {}
    public record Graph(long clanId, Long rootPersonId, SiblingCountMode siblingCountMode,
                        List<Person> persons, List<Union> unions, List<Link> links) {}
    public record ClanSummary(long id, String name, Long founderPersonId, int memberCount) {}
}
