package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.giapha.imports.ImportModel.Error;
import vn.giapha.person.DatePrecision;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.support.XlsxBuilder;

class ImportParserTest {
    static final List<String> PEOPLE = List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh", "Còn sống", "Ngày mất");
    List<Error> errors = new ArrayList<>(), warnings = new ArrayList<>();

    ImportModel.Parsed parse(XlsxBuilder b) {
        return ImportParser.parse(ExcelReader.read(new ByteArrayInputStream(b.bytes())), errors, warnings);
    }
    static List<Object> p(Object... v) { return Arrays.asList(v); }

    @Test
    void parsesHappyPath() {
        var parsed = parse(new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("P1", "Lê Tổ", "Nam", "1880", "Không", "1950"), p("P2", "Phạm Vợ", "Nữ", "05/03/1885", "", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B", "Thứ"), List.of(p("H1", "P1", "P2", 1)))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Loại", "Mã hôn phối"), List.of(p("P1", "#77", "Con nuôi", "H1")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ"), List.of(p("Họ Lê", "P1"))));
        assertThat(errors).isEmpty();
        assertThat(parsed.persons()).hasSize(2);
        var p1 = parsed.persons().get(0);
        assertThat(p1.gender()).isEqualTo(Gender.M);
        assertThat(p1.birth().precision()).isEqualTo(DatePrecision.YEAR);
        assertThat(p1.alive()).isFalse();
        var p2 = parsed.persons().get(1);
        assertThat(p2.birth().precision()).isEqualTo(DatePrecision.DAY);
        assertThat(p2.alive()).isTrue();                       // trống + không có ngày mất
        assertThat(parsed.unions().get(0).order()).isEqualTo(1);
        assertThat(parsed.links().get(0).kind()).isEqualTo(ParentKind.ADOPTED);
        assertThat(parsed.links().get(0).child()).isEqualTo("#77");
        assertThat(parsed.clan().name()).isEqualTo("Họ Lê");
    }

    @Test
    void missingPeopleSheetAndMissingColumns() {
        parse(new XlsxBuilder().sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con"), List.of()));
        assertThat(errors).extracting(Error::code).contains("MISSING_SHEET");
        errors.clear();
        parse(new XlsxBuilder().sheet("Người", List.of("Mã"), List.of(p("P1"))));
        assertThat(errors).anySatisfy(e -> { assertThat(e.code()).isEqualTo("MISSING_SHEET"); assertThat(e.column()).isEqualTo("Họ và tên"); });
    }

    @Test
    void rowLevelErrorsCarryExcelRowAndColumn() {
        parse(new XlsxBuilder().sheet("Người", PEOPLE, List.of(
            p("P1", "Tốt", "Nam", "1950", "Có", ""),                  // dòng 2: hợp lệ
            p("P1", "Trùng mã", "Nam", "1950", "Có", ""),             // dòng 3: trùng mã
            p("P3", "", "Nam", "1950", "Có", ""),                     // dòng 4: thiếu tên
            p("P4", "Sai ngày", "Nam", "31/02/1950", "Có", ""),       // dòng 5
            p("P5", "Sai giới tính", "Robot", "1950", "Có", ""),      // dòng 6
            p("P6", "Còn sống mà có mất", "Nam", "1950", "Có", "1990"), // dòng 7
            p("P7", "Mất trước sinh", "Nam", "1950", "Không", "1940"),  // dòng 8
            p("#9", "Mã bắt đầu bằng #", "Nam", "1950", "Có", ""))));  // dòng 9
        assertThat(errors).extracting(e -> e.row() + ":" + e.column() + ":" + e.code()).containsExactlyInAnyOrder(
            "3:Mã:DUPLICATE_KEY", "4:Họ và tên:REQUIRED", "5:Ngày sinh:BAD_VALUE", "6:Giới tính:BAD_VALUE",
            "7:Còn sống:INCONSISTENT", "8:Ngày mất:INCONSISTENT", "9:Mã:BAD_VALUE");
        assertThat(errors).allSatisfy(e -> assertThat(e.sheet()).isEqualTo("Người"));
    }

    @Test
    void referencesAreChecked() {
        parse(new XlsxBuilder()
            .sheet("Người", PEOPLE, List.of(p("P1", "A", "Nam", "1950", "Có", ""), p("P2", "B", "Nữ", "1952", "Có", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B"), List.of(p("H1", "P1", "P1"), p("H2", "P1", "P9")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Mã hôn phối"), List.of(p("P1", "P1", ""), p("P1", "P2", "H9"), p("PX", "P2", ""))));
        assertThat(errors).extracting(e -> e.sheet() + ":" + e.row() + ":" + e.column() + ":" + e.code()).containsExactlyInAnyOrder(
            "Hôn phối:2:Mã người B:BAD_VALUE", "Hôn phối:3:Mã người B:UNKNOWN_REF",
            "Quan hệ:2:Mã con:BAD_VALUE", "Quan hệ:3:Mã hôn phối:UNKNOWN_REF", "Quan hệ:4:Mã cha/mẹ:UNKNOWN_REF");
    }

    @Test
    void secondClanRowAndUnknownFounderAreErrors() {
        parse(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("P1", "A", "Nam", "1950", "Có", "")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ"), List.of(p("Họ A", "P1"), p("Họ B", "P1"), p("Họ C", "P7"))));
        assertThat(errors).extracting(e -> e.row() + ":" + e.code()).containsExactlyInAnyOrder("3:BAD_VALUE", "4:BAD_VALUE");
    }

    @Test
    void possibleDuplicateInFileIsOnlyAWarning() {
        parse(new XlsxBuilder().sheet("Người", PEOPLE, List.of(
            p("P1", "Nguyễn Văn A", "Nam", "05/03/1950", "Có", ""), p("P2", "nguyen van a", "Nam", "05/03/1950", "Có", ""))));
        assertThat(errors).isEmpty();
        assertThat(warnings).extracting(Error::code).containsExactly("POSSIBLE_DUPLICATE");
        assertThat(warnings.get(0).row()).isEqualTo(3);
    }

    @Test
    void tooLongValuesAreRejected() {
        parse(new XlsxBuilder().sheet("Người", PEOPLE, List.of(p("P1", "x".repeat(201), "Nam", "1950", "Có", ""))));
        assertThat(errors).extracting(Error::code).containsExactly("TOO_LONG");
    }
}
