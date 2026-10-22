package vn.giapha.relation;

import vn.giapha.person.Gender;

public final class VietnameseOrder {
    private static final String[] BASE = {null, null, "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười"};
    private static final String[] UNIT = {null, "Một", "Hai", "Ba", "Bốn", "Lăm", "Sáu", "Bảy", "Tám", "Chín"};

    private VietnameseOrder() {}

    public static String name(int n) {
        if (n >= 2 && n <= 10) return BASE[n];
        if (n >= 11 && n <= 19) return "Mười " + UNIT[n - 10];
        return String.valueOf(n);
    }

    public static String label(int n, Gender gender, boolean lastWithUt) {
        String title = switch (gender) { case M -> "anh"; case F -> "chị"; case U -> null; };
        if (lastWithUt) {
            String ut = "Út (thứ " + name(n) + ")";
            return title == null ? ut : title + " " + ut;
        }
        return (title == null ? "thứ" : title) + " " + name(n);
    }
}
