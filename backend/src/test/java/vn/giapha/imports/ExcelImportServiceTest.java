package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.AppUser;
import vn.giapha.auth.AuthUser;
import vn.giapha.auth.Role;
import vn.giapha.support.TestUsers;
import vn.giapha.support.XlsxBuilder;

@SpringBootTest
@Import({PostgresTestConfig.class, TestUsers.class})
class ExcelImportServiceTest {
    @Autowired ExcelImportService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestUsers users;

    static List<Object> p(Object... v) { return Arrays.asList(v); }
    static final List<String> PEOPLE = List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh", "Còn sống", "Ngày mất");

    XlsxBuilder family() { return family("Họ Lê Import " + java.util.UUID.randomUUID()); }
    XlsxBuilder family(String clanName) {
        return new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(
                p("P1", "Lê Tổ Import", "Nam", "1880", "Không", "1950"), p("P2", "Phạm Vợ Import", "Nữ", "1885", "Không", "1960"),
                p("P3", "Lê Con Import", "Nam", "05/03/1910", "Không", "1980"), p("P4", "Lê Cháu Import", "Nữ", "1935", "Có", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B", "Thứ", "Tình trạng"), List.of(p("H1", "P1", "P2", 1, "Đang kết hôn")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Loại", "Mã hôn phối"), List.of(
                p("P1", "P3", "Ruột", "H1"), p("P2", "P3", "Ruột", "H1"), p("P3", "P4", "Ruột", "")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ", "Đếm thứ", "Gọi Út"), List.of(p(clanName, "P1", "Theo hôn phối", "Có")));
    }
    ImportReport run(XlsxBuilder b, boolean dry) { return service.run(new AuthUser(users.create(Role.ADMIN).getId(), Role.ADMIN), new ByteArrayInputStream(b.bytes()), dry); }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    @Test
    void realImportCreatesEverythingAndBuildsClan() {
        int p0 = count("person"), u0 = count("union_rel"), l0 = count("parent_child");
        ImportReport r = run(family(), false);
        assertThat(r.errors()).isEmpty();
        assertThat(r.persons()).isEqualTo(4);
        assertThat(r.unions()).isEqualTo(1);
        assertThat(r.links()).isEqualTo(3);
        assertThat(r.clanId()).isNotNull();
        assertThat(count("person") - p0).isEqualTo(4);
        assertThat(count("union_rel") - u0).isEqualTo(1);
        assertThat(count("parent_child") - l0).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clan_member WHERE clan_id=?", Integer.class, r.clanId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT cm.generation FROM clan_member cm JOIN person p ON p.id=cm.person_id WHERE cm.clan_id=? AND p.full_name='Lê Cháu Import'",
            Integer.class, r.clanId())).isEqualTo(3);   // mọi truy vấn theo tên đều giới hạn trong dòng họ vừa nhập vì các test khác cũng nhập cùng tên
        String inClan = "SELECT birth_precision FROM person p JOIN clan_member cm ON cm.person_id = p.id AND cm.clan_id = ? WHERE p.full_name = ?";
        assertThat(jdbc.queryForObject(inClan, String.class, r.clanId(), "Lê Con Import")).isEqualTo("DAY");
        assertThat(jdbc.queryForObject(inClan, String.class, r.clanId(), "Lê Tổ Import")).isEqualTo("YEAR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='IMPORT' AND entity_id=?", Integer.class, r.clanId())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void dryRunReportsCountsButChangesNothing() {
        int p0 = count("person"), c0 = count("clan"), m0 = count("clan_member"), u0 = count("union_rel"), l0 = count("parent_child"), a0 = count("audit_log");
        ImportReport r = run(family(), true);
        assertThat(count("union_rel")).isEqualTo(u0);
        assertThat(count("parent_child")).isEqualTo(l0);
        assertThat(count("audit_log")).isEqualTo(a0);
        assertThat(r.dryRun()).isTrue();
        assertThat(r.errors()).isEmpty();
        assertThat(r.persons()).isEqualTo(4);
        assertThat(count("person")).isEqualTo(p0);
        assertThat(count("clan")).isEqualTo(c0);
        assertThat(count("clan_member")).isEqualTo(m0);
    }

    @Test
    void parseErrorsSaveNothing() {
        int p0 = count("person");
        ImportReport r = run(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("P1", "Tốt", "Nam", "1950", "Có", ""), p("P2", "", "Nam", "1950", "Có", ""))), false);
        assertThat(r.errors()).hasSize(1);
        assertThat(r.errors().get(0).row()).isEqualTo(3);
        assertThat(count("person")).isEqualTo(p0);
    }

    @Test
    void cycleAndSecondBiologicalFatherAreReportedAtTheirRowsAndRollBackEverything() {
        int p0 = count("person"), l0 = count("parent_child");
        XlsxBuilder b = new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("P1", "A", "Nam", "1900", "Có", ""), p("P2", "B", "Nam", "1925", "Có", ""),
                p("P3", "C", "Nam", "1950", "Có", ""), p("P4", "D", "Nam", "1951", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(
                p("P1", "P2"), p("P2", "P3"), p("P3", "P1"),      // dòng 4: vòng lặp
                p("P1", "P4"), p("P2", "P4")));                   // dòng 6: con D có hai cha ruột
        ImportReport r = run(b, false);
        assertThat(r.errors()).extracting(e -> e.row() + ":" + e.code()).containsExactlyInAnyOrder("4:RELATION_CYCLE", "6:RELATION_INVALID");
        assertThat(r.errors()).allSatisfy(e -> assertThat(e.sheet()).isEqualTo("Quan hệ"));
        assertThat(count("person")).isEqualTo(p0);
        assertThat(count("parent_child")).isEqualTo(l0);
    }

    @Test
    void referencesExistingPersonById() {
        long existing = jdbc.queryForObject("INSERT INTO person(full_name, gender) VALUES ('Người sẵn có','M') RETURNING id", Long.class);
        XlsxBuilder b = new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("N1", "Con của người sẵn có", "Nam", "2000", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(p("#" + existing, "N1")));
        ImportReport r = run(b, false);
        assertThat(r.errors()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_child WHERE parent_id=?", Integer.class, existing)).isEqualTo(1);
        // id không tồn tại
        ImportReport bad = run(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("N1", "X", "Nam", "2000", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(p("#999999999", "N1"))), false);
        assertThat(bad.errors()).extracting(ImportModel.Error::code).containsExactly("UNKNOWN_REF");
        assertThat(bad.errors().get(0).column()).isEqualTo("Mã cha/mẹ");
    }

    @Test
    void importingSameClanNameTwiceDoesNotDuplicate() {
        run(family("Họ Trùng Tên"), false);
        int clans = count("clan");
        ImportReport again = run(family("Họ Trùng Tên"), false);
        assertThat(again.errors()).extracting(ImportModel.Error::code).containsExactly("CLAN_EXISTS");
        assertThat(count("clan")).isEqualTo(clans);
    }

    @Test
    void nonAdminIsForbidden() {
        AuthUser editor = new AuthUser(users.create(Role.EDITOR).getId(), Role.EDITOR);
        assertThatThrownBy(() -> service.run(editor, new ByteArrayInputStream(family().bytes()), true))
            .isInstanceOfSatisfying(vn.giapha.common.ApiException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void softDeletedExistingPersonIsUnknownRef() {
        long gone = jdbc.queryForObject("INSERT INTO person(full_name, gender, deleted_at) VALUES ('Đã xoá','M', now()) RETURNING id", Long.class);
        ImportReport r = run(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("N1", "X", "Nam", "2000", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(p("#" + gone, "N1"))), false);
        assertThat(r.errors()).extracting(ImportModel.Error::code).containsExactly("UNKNOWN_REF");
    }

    @Test
    void unionServiceErrorRollsBackEverything() {
        int p0 = count("person"), u0 = count("union_rel");
        XlsxBuilder b = new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("A", "Hôn A", "Nam", "1900", "Có", ""), p("B", "Hôn B", "Nữ", "1900", "Có", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B", "Tình trạng"), List.of(
                p("H1", "A", "B", "Đang kết hôn"), p("H2", "B", "A", "Đang kết hôn")));
        ImportReport r = run(b, false);
        assertThat(r.errors()).isNotEmpty();
        assertThat(r.errors().get(0).sheet()).isEqualTo("Hôn phối");
        assertThat(count("person")).isEqualTo(p0);
        assertThat(count("union_rel")).isEqualTo(u0);
    }

    @Test
    void existingClanWithDifferentFounderIsRejected() {
        run(family("Họ Bị Chiếm"), false);
        int p0 = count("person");
        XlsxBuilder b = new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("Z1", "Kẻ chiếm", "Nam", "1900", "Có", "")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ"), List.of(p("Họ Bị Chiếm", "Z1")));
        ImportReport r = run(b, false);
        assertThat(r.errors()).extracting(e -> e.sheet() + ":" + e.code()).containsExactly("Dòng họ:CLAN_EXISTS");
        assertThat(count("person")).isEqualTo(p0);
    }

    @Test
    void reimportWithSameFounderIsAllowed() {
        ImportReport first = run(family("Họ Tái Nhập"), false);
        long founder = jdbc.queryForObject("SELECT founder_person_id FROM clan WHERE id=?", Long.class, first.clanId());
        int clans = count("clan");
        ImportReport r = run(new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("N1", "Con thêm", "Nam", "1915", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(p("#" + founder, "N1")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ"), List.of(p("Họ Tái Nhập", "#" + founder))), false);
        assertThat(r.errors()).isEmpty();
        assertThat(r.clanId()).isEqualTo(first.clanId());
        assertThat(count("clan")).isEqualTo(clans);
    }

    @Test
    void relationWarningsAreMappedToQuanHeRows() {
        // cha sinh sau con -> cảnh báo từ ParentChildService
        XlsxBuilder b = new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("P1", "Cha trẻ", "Nam", "1990", "Có", ""), p("P2", "Con già", "Nam", "1950", "Có", "")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of(p("P1", "P2")));
        ImportReport r = run(b, true);
        assertThat(r.errors()).isEmpty();
        assertThat(r.warnings()).anySatisfy(w -> { assertThat(w.sheet()).isEqualTo("Quan hệ"); assertThat(w.row()).isEqualTo(2); });
    }

    @Test
    @org.junit.jupiter.api.Tag("perf")
    void largeImportCompletesInReasonableTime() {
        int n = 5000;
        List<List<Object>> people = new java.util.ArrayList<>(), links = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            people.add(p("K" + i, "Perf Người " + i, i % 2 == 0 ? "Nam" : "Nữ", String.valueOf(1000 + i / 10), "Không", ""));
        }
        // mỗi người chỉ một cha (cây 5 nhánh)
        for (int i = 1; i < n; i++) links.add(p("K" + (i - 1) / 5, "K" + i));
        XlsxBuilder b = new XlsxBuilder().sheet("Người", PEOPLE, people).sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), links);
        long t0 = System.nanoTime();
        ImportReport r = run(b, false);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PERF import " + n + " people/" + (n - 1) + " links: " + ms + " ms; errors=" + r.errors().size());
        assertThat(r.persons()).isEqualTo(n);
        assertThat(ms).isLessThan(60_000);
    }
}
