package vn.giapha.imports;

import java.util.Set;
import vn.giapha.clan.SiblingCountMode;
import vn.giapha.person.Gender;
import vn.giapha.relation.ParentKind;
import vn.giapha.relation.UnionStatus;

public final class Cells {
    private Cells() {}

    public static Gender gender(String s) {
        String k = Norm.key(s);
        if (k.isEmpty() || k.equals("khongro") || k.equals("u")) return Gender.U;
        if (k.equals("nam") || k.equals("m")) return Gender.M;
        if (k.equals("nu") || k.equals("f")) return Gender.F;
        throw new IllegalArgumentException("Giới tính phải là Nam, Nữ hoặc Không rõ: " + s);
    }

    public static Boolean bool(String s) {
        String k = Norm.key(s);
        if (k.isEmpty()) return null;
        if (Set.of("co", "1", "true", "x", "yes").contains(k)) return true;
        if (Set.of("khong", "0", "false", "no").contains(k)) return false;
        throw new IllegalArgumentException("Giá trị phải là Có hoặc Không: " + s);
    }

    public static ParentKind kind(String s) {
        String k = Norm.key(s);
        if (k.isEmpty() || k.equals("ruot")) return ParentKind.BIOLOGICAL;
        if (k.equals("connuoi")) return ParentKind.ADOPTED;
        if (k.equals("conrieng")) return ParentKind.STEP;
        throw new IllegalArgumentException("Loại phải là Ruột, Con nuôi hoặc Con riêng: " + s);
    }

    public static UnionStatus unionStatus(String s) {
        String k = Norm.key(s);
        if (k.isEmpty() || k.equals("dangkethon")) return UnionStatus.MARRIED;
        if (k.equals("dalyhon")) return UnionStatus.DIVORCED;
        if (k.equals("goa")) return UnionStatus.WIDOWED;
        throw new IllegalArgumentException("Tình trạng phải là Đang kết hôn, Đã ly hôn hoặc Góa: " + s);
    }

    public static SiblingCountMode countMode(String s) {
        String k = Norm.key(s);
        if (k.isEmpty() || k.equals("theohonphoi")) return SiblingCountMode.PER_UNION;
        if (k.equals("lientheocha")) return SiblingCountMode.PER_FATHER;
        throw new IllegalArgumentException("Cách đếm phải là Theo hôn phối hoặc Liền theo cha: " + s);
    }
}
