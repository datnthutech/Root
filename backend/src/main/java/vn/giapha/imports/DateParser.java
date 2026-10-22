package vn.giapha.imports;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.giapha.person.DatePrecision;

public final class DateParser {
    public record Parsed(LocalDate date, DatePrecision precision) {}

    private static final Pattern YEAR = Pattern.compile("^(\\d{4})$");
    private static final Pattern MONTH = Pattern.compile("^(\\d{1,2})/(\\d{4})$");
    private static final Pattern DAY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$");
    private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");

    private DateParser() {}

    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            Matcher m;
            if ((m = YEAR.matcher(s)).matches()) return new Parsed(LocalDate.of(year(m.group(1)), 1, 1), DatePrecision.YEAR);
            if ((m = MONTH.matcher(s)).matches()) return new Parsed(LocalDate.of(year(m.group(2)), Integer.parseInt(m.group(1)), 1), DatePrecision.MONTH);
            if ((m = DAY.matcher(s)).matches()) return new Parsed(LocalDate.of(year(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))), DatePrecision.DAY);
            if ((m = ISO.matcher(s)).matches()) return new Parsed(LocalDate.of(year(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))), DatePrecision.DAY);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Ngày không hợp lệ: " + s);
        }
        throw new IllegalArgumentException("Ngày không đúng định dạng (yyyy, MM/yyyy hoặc dd/MM/yyyy): " + s);
    }

    private static int year(String y) {
        int v = Integer.parseInt(y);
        if (v < 1000 || v > 2100) throw new DateTimeException("year");
        return v;
    }
}
