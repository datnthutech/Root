package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;

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

    XlsxBuilder family() {
        return new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(
                p("P1", "Lê Tổ Import", "Nam", "1880", "Không", "1950"), p("P2", "Phạm Vợ Import", "Nữ", "1885", "Không", "1960"),
                p("P3", "Lê Con Import", "Nam", "05/03/1910", "Không", "1980"), p("P4", "Lê Cháu Import", "Nữ", "1935", "Có", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B", "Thứ", "Tình trạng"), List.of(p("H1", "P1", "P2", 1, "Đang kết hôn")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Loại", "Mã hôn phối"), List.of(
                p("P1", "P3", "Ruột", "H1"), p("P2", "P3", "Ruột", "H1"), p("P3", "P4", "Ruột", "")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ", "Đếm thứ", "Gọi Út"), List.of(p("Họ Lê Import", "P1", "Theo hôn phối", "Có")));
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
        int p0 = count("person"), c0 = count("clan"), m0 = count("clan_member");
        ImportReport r = run(family(), true);
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
    void importingSameClanNameTwiceUpdatesInsteadOfDuplicating() {
        run(family(), false);
        int clans = count("clan");
        run(family(), false);
        assertThat(count("clan")).isEqualTo(clans);
    }
}
