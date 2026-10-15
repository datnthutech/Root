package vn.giapha.imports;

import java.time.LocalDate;
import java.util.List;
import vn.giapha.clan.SiblingCountMode;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.relation.UnionStatus;

public final class ImportModel {
    private ImportModel() {}

    public record Error(String sheet, int row, String column, String code, String message) {}
    public record PersonRow(int row, String key, String fullName, String nickname, Gender gender, DateParser.Parsed birth, String birthLunar,
                            boolean alive, DateParser.Parsed death, String deathLunar, String origin, String home, String burial, String biography) {}
    public record UnionRow(int row, String key, String a, String b, Integer order, UnionStatus status, LocalDate start, LocalDate end) {}
    public record LinkRow(int row, String parent, String child, ParentKind kind, String unionKey) {}
    public record ClanRow(int row, String name, String founderKey, SiblingCountMode mode, Boolean useUt) {}
    public record Parsed(List<PersonRow> persons, List<UnionRow> unions, List<LinkRow> links, ClanRow clan) {}
}
