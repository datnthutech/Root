package vn.giapha.imports;

import java.io.InputStream;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.giapha.audit.AuditService;
import vn.giapha.auth.AuthUser;
import vn.giapha.clan.*;
import vn.giapha.common.ApiException;
import vn.giapha.imports.ImportModel.*;
import vn.giapha.person.*;
import vn.giapha.relation.*;

@Service
public class ExcelImportService {
    private final PersonRepository persons;
    private final UnionService unions;
    private final ParentChildService links;
    private final ClanRepository clans;
    private final ClanMemberService clanMembers;
    private final AuditService audit;
    private final PlatformTransactionManager tm;

    public ExcelImportService(PersonRepository persons, UnionService unions, ParentChildService links, ClanRepository clans,
                              ClanMemberService clanMembers, AuditService audit, PlatformTransactionManager tm) {
        this.persons = persons;
        this.unions = unions;
        this.links = links;
        this.clans = clans;
        this.clanMembers = clanMembers;
        this.audit = audit;
        this.tm = tm;
    }

    public ImportReport run(AuthUser me, InputStream in, boolean dryRun) {
        List<ImportModel.Error> errors = new ArrayList<>(), warnings = new ArrayList<>();
        Parsed parsed = ImportParser.parse(ExcelReader.read(in), errors, warnings);
        if (!errors.isEmpty()) {
            return new ImportReport(dryRun, parsed.persons().size(), parsed.unions().size(), parsed.links().size(), errors, warnings, null);
        }
        TransactionTemplate tx = new TransactionTemplate(tm);
        return tx.execute(status -> {
            ImportReport r = persist(me, parsed, dryRun, warnings);
            if (dryRun || !r.errors().isEmpty()) status.setRollbackOnly();
            return r;
        });
    }

    private ImportReport persist(AuthUser me, Parsed parsed, boolean dryRun, List<ImportModel.Error> warnings) {
        List<ImportModel.Error> errors = new ArrayList<>();
        Map<String, Long> ids = new HashMap<>();
        Set<Long> touched = new LinkedHashSet<>();

        for (PersonRow r : parsed.persons()) {
            Person p = new Person();
            p.setFullName(r.fullName());
            p.setNickname(r.nickname());
            p.setGender(r.gender());
            if (r.birth() != null) { p.setBirthDate(r.birth().date()); p.setBirthPrecision(r.birth().precision()); }
            p.setBirthLunar(r.birthLunar());
            p.setAlive(r.alive());
            if (r.death() != null) { p.setDeathDate(r.death().date()); p.setDeathPrecision(r.death().precision()); }
            p.setDeathAnniversaryLunar(r.deathLunar());
            p.setOriginPlace(r.origin());
            p.setHomePlace(r.home());
            p.setBurialPlace(r.burial());
            p.setBiography(r.biography());
            p.setCreatedBy(me.id());
            p = persons.save(p);
            ids.put(r.key(), p.getId());
            touched.add(p.getId());
        }
        persons.flush();

        Map<String, Long> unionIds = new HashMap<>();
        int unionCount = 0;
        for (UnionRow r : parsed.unions()) {
            Long a = resolve(r.a(), ids, errors, "Hôn phối", r.row(), "Mã người A");
            Long b = resolve(r.b(), ids, errors, "Hôn phối", r.row(), "Mã người B");
            if (a == null || b == null) continue;
            try {
                UnionRel u = unions.create(a, b, r.order(), r.status(), r.start(), r.end());
                unionIds.put(r.key(), u.getId());
                touched.add(a);
                touched.add(b);
                unionCount++;
            } catch (ApiException e) {
                errors.add(new ImportModel.Error("Hôn phối", r.row(), "", e.code(), e.getMessage()));
            }
        }

        int linkCount = 0;
        for (LinkRow r : parsed.links()) {
            Long parent = resolve(r.parent(), ids, errors, "Quan hệ", r.row(), "Mã cha/mẹ");
            Long child = resolve(r.child(), ids, errors, "Quan hệ", r.row(), "Mã con");
            if (parent == null || child == null) continue;
            try {
                links.add(parent, child, r.kind(), r.unionKey() == null ? null : unionIds.get(r.unionKey()));
                touched.add(parent);
                touched.add(child);
                linkCount++;
            } catch (ApiException e) {
                errors.add(new ImportModel.Error("Quan hệ", r.row(), "", e.code(), e.getMessage()));
            }
        }

        Long clanId = null;
        if (parsed.clan() != null && errors.isEmpty()) {
            ClanRow c = parsed.clan();
            Long founder = resolve(c.founderKey(), ids, errors, "Dòng họ", c.row(), "Mã thủy tổ");
            if (founder != null) {
                Clan clan = clans.findAll().stream().filter(x -> x.getName().equalsIgnoreCase(c.name())).findFirst().orElseGet(Clan::new);
                clan.setName(c.name());
                clan.setFounderPersonId(founder);
                if (c.mode() != null) clan.setSiblingCountMode(c.mode());
                if (c.useUt() != null) clan.setUseUtLabel(c.useUt());
                clan = clans.saveAndFlush(clan);
                clanId = clan.getId();
                clanMembers.rebuild(clanId);
            }
        }
        if (errors.isEmpty()) {
            clanMembers.rebuildClansOf(touched);
            if (!dryRun) {
                audit.record(me.id(), "IMPORT", clanId == null ? 0 : clanId, "IMPORT", null,
                    Map.of("persons", parsed.persons().size(), "unions", unionCount, "links", linkCount));
            }
        }
        return new ImportReport(dryRun, parsed.persons().size(), unionCount, linkCount, errors, warnings, clanId);
    }

    private Long resolve(String ref, Map<String, Long> ids, List<ImportModel.Error> errors, String sheet, int row, String column) {
        if (ref.startsWith("#")) {
            long id = Long.parseLong(ref.substring(1));
            if (persons.findByIdAndDeletedAtIsNull(id).isEmpty()) {
                errors.add(new ImportModel.Error(sheet, row, column, "UNKNOWN_REF", "Không có người đã có với mã " + ref));
                return null;
            }
            return id;
        }
        Long id = ids.get(ref);
        if (id == null) errors.add(new ImportModel.Error(sheet, row, column, "UNKNOWN_REF", "Không có người mã " + ref));
        return id;
    }
}
