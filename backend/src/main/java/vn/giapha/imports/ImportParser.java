package vn.giapha.imports;

import java.util.*;
import java.util.function.Supplier;
import vn.giapha.imports.ImportModel.*;
import vn.giapha.imports.ImportModel.Error;

public final class ImportParser {
    private static final String S_PEOPLE = "Người", S_LINKS = "Quan hệ", S_UNIONS = "Hôn phối", S_CLAN = "Dòng họ";

    private ImportParser() {}

    public static Parsed parse(Map<String, ExcelReader.Sheet> sheets, List<Error> errors, List<Error> warnings) {
        List<PersonRow> persons = new ArrayList<>();
        List<UnionRow> unions = new ArrayList<>();
        List<LinkRow> links = new ArrayList<>();
        ClanRow clan = null;

        ExcelReader.Sheet people = sheets.get("nguoi");
        if (people == null) {
            errors.add(new Error(S_PEOPLE, 0, "", "MISSING_SHEET", "Thiếu sheet \"Người\""));
            return new Parsed(persons, unions, links, null);
        }
        if (missing(people, S_PEOPLE, errors, Map.of("ma", "Mã", "hovaten", "Họ và tên"))) return new Parsed(persons, unions, links, null);

        Set<String> keys = new LinkedHashSet<>();
        Map<String, Integer> seenIdentity = new HashMap<>();
        for (ExcelReader.Row r : people.rows()) {
            int before = errors.size();
            String key = cell(r, "ma");
            if (key.isEmpty()) errors.add(err(S_PEOPLE, r, "Mã", "REQUIRED", "Thiếu mã"));
            else if (key.startsWith("#")) errors.add(err(S_PEOPLE, r, "Mã", "BAD_VALUE", "Mã không được bắt đầu bằng #"));
            else if (!keys.add(key)) errors.add(err(S_PEOPLE, r, "Mã", "DUPLICATE_KEY", "Mã bị trùng: " + key));
            String name = cell(r, "hovaten");
            if (name.isEmpty()) errors.add(err(S_PEOPLE, r, "Họ và tên", "REQUIRED", "Thiếu họ và tên"));
            length(errors, S_PEOPLE, r, "Họ và tên", name, 200);
            length(errors, S_PEOPLE, r, "Tên thường gọi", cell(r, "tenthuonggoi"), 100);
            length(errors, S_PEOPLE, r, "Ngày sinh âm lịch", cell(r, "ngaysinhamlich"), 40);
            length(errors, S_PEOPLE, r, "Ngày giỗ âm lịch", cell(r, "ngaygioamlich"), 40);
            length(errors, S_PEOPLE, r, "Nguyên quán", cell(r, "nguyenquan"), 200);
            length(errors, S_PEOPLE, r, "Quê quán", cell(r, "quequan"), 200);
            length(errors, S_PEOPLE, r, "Nơi an táng", cell(r, "noiantang"), 200);
            length(errors, S_PEOPLE, r, "Sơ yếu lý lịch", cell(r, "soyeulylich"), 20000);

            var gender = guard(errors, S_PEOPLE, r, "Giới tính", () -> Cells.gender(cell(r, "gioitinh")));
            var birth = guard(errors, S_PEOPLE, r, "Ngày sinh", () -> DateParser.parse(cell(r, "ngaysinh")));
            var death = guard(errors, S_PEOPLE, r, "Ngày mất", () -> DateParser.parse(cell(r, "ngaymat")));
            Boolean aliveCell = guard(errors, S_PEOPLE, r, "Còn sống", () -> Cells.bool(cell(r, "consong")));
            boolean alive = aliveCell != null ? aliveCell : death == null;
            if (alive && death != null) errors.add(err(S_PEOPLE, r, "Còn sống", "INCONSISTENT", "Còn sống nhưng có ngày mất"));
            if (birth != null && death != null && death.date().isBefore(birth.date())) {
                errors.add(err(S_PEOPLE, r, "Ngày mất", "INCONSISTENT", "Ngày mất trước ngày sinh"));
            }
            if (errors.size() == before && gender != null) {
                persons.add(new PersonRow(r.number(), key, name, blank(cell(r, "tenthuonggoi")), gender, birth, blank(cell(r, "ngaysinhamlich")),
                    alive, alive ? null : death, blank(cell(r, "ngaygioamlich")), blank(cell(r, "nguyenquan")), blank(cell(r, "quequan")),
                    blank(cell(r, "noiantang")), blank(cell(r, "soyeulylich"))));
                if (birth != null && birth.precision() == vn.giapha.person.DatePrecision.DAY) {
                    String ident = Norm.key(name) + "|" + birth.date();
                    Integer first = seenIdentity.putIfAbsent(ident, r.number());
                    if (first != null) warnings.add(err(S_PEOPLE, r, "Họ và tên", "POSSIBLE_DUPLICATE", "Có thể trùng với dòng " + first));
                }
            }
        }

        Set<String> unionKeys = new LinkedHashSet<>();
        ExcelReader.Sheet us = sheets.get("honphoi");
        if (us != null && !missing(us, S_UNIONS, errors, Map.of("mahonphoi", "Mã hôn phối", "manguoia", "Mã người A", "manguoib", "Mã người B"))) {
            for (ExcelReader.Row r : us.rows()) {
                int before = errors.size();
                String key = cell(r, "mahonphoi"), a = cell(r, "manguoia"), b = cell(r, "manguoib");
                if (key.isEmpty()) errors.add(err(S_UNIONS, r, "Mã hôn phối", "REQUIRED", "Thiếu mã hôn phối"));
                else if (!unionKeys.add(key)) errors.add(err(S_UNIONS, r, "Mã hôn phối", "DUPLICATE_KEY", "Mã hôn phối bị trùng: " + key));
                checkRef(errors, S_UNIONS, r, "Mã người A", a, keys);
                checkRef(errors, S_UNIONS, r, "Mã người B", b, keys);
                if (!a.isEmpty() && a.equals(b)) errors.add(err(S_UNIONS, r, "Mã người B", "BAD_VALUE", "Hai người trong hôn phối phải khác nhau"));
                Integer order = null;
                if (!cell(r, "thu").isEmpty()) {
                    try { order = Integer.parseInt(cell(r, "thu")); if (order < 1) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { errors.add(err(S_UNIONS, r, "Thứ", "BAD_VALUE", "Thứ phải là số nguyên ≥ 1")); }
                }
                var status = guard(errors, S_UNIONS, r, "Tình trạng", () -> Cells.unionStatus(cell(r, "tinhtrang")));
                var start = guard(errors, S_UNIONS, r, "Ngày bắt đầu", () -> DateParser.parse(cell(r, "ngaybatdau")));
                var end = guard(errors, S_UNIONS, r, "Ngày kết thúc", () -> DateParser.parse(cell(r, "ngayketthuc")));
                if (errors.size() == before) unions.add(new UnionRow(r.number(), key, a, b, order, status, start == null ? null : start.date(), end == null ? null : end.date()));
            }
        }

        ExcelReader.Sheet ls = sheets.get("quanhe");
        if (ls != null && !missing(ls, S_LINKS, errors, Map.of("machame", "Mã cha/mẹ", "macon", "Mã con"))) {
            for (ExcelReader.Row r : ls.rows()) {
                int before = errors.size();
                String parent = cell(r, "machame"), child = cell(r, "macon"), uKey = cell(r, "mahonphoi");
                checkRef(errors, S_LINKS, r, "Mã cha/mẹ", parent, keys);
                checkRef(errors, S_LINKS, r, "Mã con", child, keys);
                if (!parent.isEmpty() && parent.equals(child)) errors.add(err(S_LINKS, r, "Mã con", "BAD_VALUE", "Cha/mẹ và con phải khác nhau"));
                if (!uKey.isEmpty() && !unionKeys.contains(uKey)) errors.add(err(S_LINKS, r, "Mã hôn phối", "UNKNOWN_REF", "Không có hôn phối " + uKey + " trong sheet Hôn phối"));
                var kind = guard(errors, S_LINKS, r, "Loại", () -> Cells.kind(cell(r, "loai")));
                if (errors.size() == before) links.add(new LinkRow(r.number(), parent, child, kind, uKey.isEmpty() ? null : uKey));
            }
        }

        ExcelReader.Sheet cs = sheets.get("dongho");
        if (cs != null && !missing(cs, S_CLAN, errors, Map.of("tendongho", "Tên dòng họ", "mathuyto", "Mã thủy tổ"))) {
            for (int i = 0; i < cs.rows().size(); i++) {
                ExcelReader.Row r = cs.rows().get(i);
                if (i > 0) { errors.add(err(S_CLAN, r, "Tên dòng họ", "BAD_VALUE", "Chỉ nhập một dòng họ mỗi lần")); continue; }
                int before = errors.size();
                String name = cell(r, "tendongho"), founder = cell(r, "mathuyto");
                if (name.isEmpty()) errors.add(err(S_CLAN, r, "Tên dòng họ", "REQUIRED", "Thiếu tên dòng họ"));
                length(errors, S_CLAN, r, "Tên dòng họ", name, 150);
                if (founder.isEmpty()) errors.add(err(S_CLAN, r, "Mã thủy tổ", "REQUIRED", "Thiếu mã thủy tổ"));
                else checkRef(errors, S_CLAN, r, "Mã thủy tổ", founder, keys);
                var mode = guard(errors, S_CLAN, r, "Đếm thứ", () -> Cells.countMode(cell(r, "demthu")));
                Boolean useUt = guard(errors, S_CLAN, r, "Gọi Út", () -> Cells.bool(cell(r, "goiut")));
                if (errors.size() == before) clan = new ClanRow(r.number(), name, founder, mode, useUt);
            }
        }
        return new Parsed(persons, unions, links, clan);
    }

    private static boolean missing(ExcelReader.Sheet s, String sheetName, List<Error> errors, Map<String, String> required) {
        boolean bad = false;
        for (var e : required.entrySet()) {
            if (!s.headers().contains(e.getKey())) {
                errors.add(new Error(sheetName, 1, e.getValue(), "MISSING_SHEET", "Sheet \"" + sheetName + "\" thiếu cột \"" + e.getValue() + "\""));
                bad = true;
            }
        }
        return bad;
    }

    private static void checkRef(List<Error> errors, String sheet, ExcelReader.Row r, String col, String ref, Set<String> keys) {
        if (ref.isEmpty()) { errors.add(err(sheet, r, col, "REQUIRED", "Thiếu " + col)); return; }
        if (ref.startsWith("#")) {
            try { Long.parseLong(ref.substring(1)); } catch (NumberFormatException e) { errors.add(err(sheet, r, col, "BAD_VALUE", "Mã người đã có phải dạng #123")); }
            return;
        }
        if (!keys.contains(ref)) errors.add(err(sheet, r, col, "UNKNOWN_REF", "Không có người mã " + ref + " trong sheet Người"));
    }

    private static <T> T guard(List<Error> errors, String sheet, ExcelReader.Row r, String col, Supplier<T> f) {
        try { return f.get(); } catch (IllegalArgumentException e) { errors.add(err(sheet, r, col, "BAD_VALUE", e.getMessage())); return null; }
    }

    private static void length(List<Error> errors, String sheet, ExcelReader.Row r, String col, String v, int max) {
        if (v.length() > max) errors.add(err(sheet, r, col, "TOO_LONG", col + " dài quá " + max + " ký tự"));
    }

    private static String cell(ExcelReader.Row r, String key) { return r.cells().getOrDefault(key, "").trim(); }
    private static String blank(String s) { return s.isEmpty() ? null : s; }
    private static Error err(String sheet, ExcelReader.Row r, String col, String code, String msg) { return new Error(sheet, r.number(), col, code, msg); }
}
