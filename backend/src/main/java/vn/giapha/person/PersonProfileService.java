package vn.giapha.person;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.auth.Role;
import vn.giapha.common.ApiException;
import vn.giapha.relation.OrderInfo;
import vn.giapha.relation.SiblingOrderService;

@Service
@Transactional(readOnly = true)
public class PersonProfileService {
    private final PersonRepository persons;
    private final JdbcTemplate jdbc;
    private final SiblingOrderService orders;

    public PersonProfileService(PersonRepository persons, JdbcTemplate jdbc, SiblingOrderService orders) {
        this.persons = persons;
        this.jdbc = jdbc;
        this.orders = orders;
    }

    static Integer year(LocalDate d, DatePrecision p) { return d == null || p == DatePrecision.UNKNOWN ? null : d.getYear(); }

    public PersonProfile profile(long id, Role viewerRole) {
        Person p = persons.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
        boolean hideFull = viewerRole == Role.VIEWER && p.isAlive();

        var clanRow = jdbc.queryForList(
            "SELECT clan_id, generation FROM clan_member WHERE person_id = ? ORDER BY clan_id LIMIT 1", id);
        Integer generation = clanRow.isEmpty() ? null : ((Number) clanRow.get(0).get("generation")).intValue();
        OrderInfo order = null;
        if (!clanRow.isEmpty()) {
            Map<Long, OrderInfo> m = orders.computeForClan(((Number) clanRow.get(0).get("clan_id")).longValue());
            order = m.get(id);
        }
        return new PersonProfile(p.getId(), p.getFullName(), p.getNickname(), p.getGender(),
            hideFull ? null : p.getBirthDate(), p.getBirthPrecision(), year(p.getBirthDate(), p.getBirthPrecision()),
            hideFull ? null : p.getBirthLunar(),
            p.getDeathDate(), p.getDeathPrecision(), year(p.getDeathDate(), p.getDeathPrecision()), p.getDeathAnniversaryLunar(),
            p.isAlive(), p.getOriginPlace(), p.getHomePlace(), p.getBurialPlace(), generation,
            order == null ? null : order.number(), order == null ? null : order.label(), order != null && order.needsConfirm(),
            p.getPhotoKey() == null ? null : "/persons/" + id + "/photo?size=256&v=" + p.getPhotoKey(),
            p.getVersion(), p.getCreatedBy(), p.getCreatedAt());
    }

    public BiographyResponse biography(long id) {
        Person p = persons.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
        return new BiographyResponse(id, p.getBiography());
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy người này"); }
}
