package vn.giapha.tree;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.clan.*;
import vn.giapha.common.ApiException;
import vn.giapha.person.Gender;
import vn.giapha.relation.OrderInfo;
import vn.giapha.relation.SiblingOrderService;

@Service
@Transactional(readOnly = true)
public class GraphService {
    private final ClanRepository clans;
    private final ClanMemberRepository members;
    private final GraphRepository repo;
    private final SiblingOrderService orders;

    public GraphService(ClanRepository clans, ClanMemberRepository members, GraphRepository repo, SiblingOrderService orders) {
        this.clans = clans;
        this.members = members;
        this.repo = repo;
        this.orders = orders;
    }

    static String thumb(long id, String photoKey) {
        return photoKey == null ? null : "/persons/" + id + "/photo?size=96&v=" + photoKey;
    }

    public List<GraphDtos.ClanSummary> listClans() {
        return clans.findAll().stream()
            .map(c -> new GraphDtos.ClanSummary(c.getId(), c.getName(), c.getFounderPersonId(), members.findByClanId(c.getId()).size()))
            .toList();
    }

    public GraphDtos.Graph clanGraph(long clanId, Side side) {
        Clan clan = clans.findById(clanId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy dòng họ"));
        List<ClanMember> all = members.findByClanId(clanId);
        if (clan.getFounderPersonId() == null || all.isEmpty()) {
            return new GraphDtos.Graph(clanId, clan.getFounderPersonId(), clan.getSiblingCountMode(), List.of(), List.of(), List.of());
        }
        Map<Long, ClanMember> byPerson = all.stream().collect(Collectors.toMap(ClanMember::getPersonId, m -> m));
        Set<Long> memberIds = byPerson.keySet();

        Set<Long> keep = new LinkedHashSet<>(memberIds);
        if (side != Side.BOTH) {
            Map<Long, List<Long>> kids = new HashMap<>();
            repo.linksAmong(memberIds).forEach(l -> kids.computeIfAbsent(l.parentId(), k -> new ArrayList<>()).add(l.childId()));
            Map<Long, Gender> gender = repo.persons(memberIds).stream()
                .collect(Collectors.toMap(GraphRepository.PersonRow::id, GraphRepository.PersonRow::gender));
            keep = new LinkedHashSet<>(SideFilter.apply(side, clan.getFounderPersonId(),
                id -> kids.getOrDefault(id, List.of()), id -> gender.getOrDefault(id, Gender.U)));
            // giữ vợ/chồng của người được giữ
            for (GraphDtos.Union u : repo.unionsAmong(memberIds)) {
                if (keep.contains(u.partnerAId()) && byPerson.get(u.partnerBId()).getMembership() == Membership.IN_LAW) keep.add(u.partnerBId());
                if (keep.contains(u.partnerBId()) && byPerson.get(u.partnerAId()).getMembership() == Membership.IN_LAW) keep.add(u.partnerAId());
            }
        }
        return assemble(clanId, clan.getFounderPersonId(), clan.getSiblingCountMode(), keep, byPerson, orders.computeForClan(clanId));
    }

    GraphDtos.Graph assemble(long clanId, Long rootId, SiblingCountMode mode, Set<Long> keep,
                             Map<Long, ClanMember> byPerson, Map<Long, OrderInfo> orderInfo) {
        List<GraphDtos.Person> persons = repo.persons(keep).stream().map(r -> {
            ClanMember m = byPerson.get(r.id());
            OrderInfo o = orderInfo.get(r.id());
            return new GraphDtos.Person(r.id(), r.name(), r.gender(), r.birthYear(), r.deathYear(), r.alive(),
                thumb(r.id(), r.photoKey()), o == null ? null : o.number(), o == null ? null : o.label(),
                m == null ? 1 : m.getGeneration(), m == null ? Membership.BLOOD : m.getMembership());
        }).toList();
        Set<Long> present = persons.stream().map(GraphDtos.Person::id).collect(Collectors.toSet());
        return new GraphDtos.Graph(clanId, rootId, mode, persons, repo.unionsAmong(present), repo.linksAmong(present));
    }
}
