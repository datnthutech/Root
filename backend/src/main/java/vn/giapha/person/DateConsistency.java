package vn.giapha.person;

import java.time.LocalDate;

/** Kiểm tra ngày mất trước ngày sinh ở độ chính xác thô hơn của hai ngày (khớp frontend lib/dateField.ts). */
public final class DateConsistency {
    private DateConsistency() {}

    private static int len(DatePrecision p) {
        return switch (p) { case YEAR -> 4; case MONTH -> 7; case DAY -> 10; case UNKNOWN -> 0; };
    }

    public static boolean deathBeforeBirth(LocalDate birth, DatePrecision bp, LocalDate death, DatePrecision dp) {
        if (birth == null || death == null) return false;
        DatePrecision b = bp == null ? DatePrecision.DAY : bp, d = dp == null ? DatePrecision.DAY : dp;
        int n = Math.min(len(b), len(d));
        if (n == 0) return false;
        return death.toString().substring(0, n).compareTo(birth.toString().substring(0, n)) < 0;
    }
}
