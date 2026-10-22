package vn.giapha.tree;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import vn.giapha.common.ApiException;

public enum Side {
    BOTH, PATERNAL, MATERNAL;

    public static Side parse(String s) {
        if (s == null || s.isBlank()) return BOTH;
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "side phải là both, paternal hoặc maternal");
        }
    }
}
