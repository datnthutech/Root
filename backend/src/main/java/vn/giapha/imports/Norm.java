package vn.giapha.imports;

import java.text.Normalizer;
import java.util.Locale;

public final class Norm {
    private Norm() {}

    public static String key(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('đ', 'd');
        return t.replaceAll("[^a-z0-9]", "");
    }
}
