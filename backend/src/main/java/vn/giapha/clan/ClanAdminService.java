package vn.giapha.clan;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.person.PersonRepository;

@Service
public class ClanAdminService {
    public record ClanView(long id, String name, Long founderPersonId, String description,
                           SiblingCountMode siblingCountMode, boolean useUtLabel, int memberCount) {}

    private final ClanRepository clans;
    private final ClanMemberRepository members;
    private final ClanMemberService memberService;
    private final PersonRepository persons;

    public ClanAdminService(ClanRepository clans, ClanMemberRepository members, ClanMemberService memberService, PersonRepository persons) {
        this.clans = clans;
        this.members = members;
        this.memberService = memberService;
        this.persons = persons;
    }

    public List<ClanView> list() { return clans.findAll().stream().map(this::view).toList(); }

    @Transactional
    public ClanView create(String name, Long founderId, String description, SiblingCountMode mode, Boolean useUt) {
        Clan c = new Clan();
        apply(c, name, founderId, description, mode, useUt);
        c = clans.saveAndFlush(c);
        if (c.getFounderPersonId() != null) memberService.rebuild(c.getId());
        return view(c);
    }

    @Transactional
    public ClanView update(long id, String name, Long founderId, String description, SiblingCountMode mode, Boolean useUt) {
        Clan c = clans.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy dòng họ"));
        apply(c, name, founderId, description, mode, useUt);
        c = clans.saveAndFlush(c);
        if (c.getFounderPersonId() != null) memberService.rebuild(c.getId());
        else members.deleteByClanId(c.getId());
        return view(c);
    }

    private void apply(Clan c, String name, Long founderId, String description, SiblingCountMode mode, Boolean useUt) {
        if (founderId != null && persons.findByIdAndDeletedAtIsNull(founderId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thủy tổ không tồn tại");
        }
        c.setName(name.trim());
        c.setFounderPersonId(founderId);
        c.setDescription(description);
        if (mode != null) c.setSiblingCountMode(mode);
        if (useUt != null) c.setUseUtLabel(useUt);
    }

    private ClanView view(Clan c) {
        return new ClanView(c.getId(), c.getName(), c.getFounderPersonId(), c.getDescription(), c.getSiblingCountMode(),
            c.isUseUtLabel(), members.findByClanId(c.getId()).size());
    }
}
