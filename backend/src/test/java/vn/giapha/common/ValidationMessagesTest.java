package vn.giapha.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidationMessagesTest {
    record Form(
        @Size(max = 120) String maxOnly,
        @Size(min = 8) String minOnly,
        @Size(min = 3, max = 50) String both,
        @NotBlank String blank,
        @NotNull Long required,
        @Pattern(regexp = "[a-z]+") String pattern) {}

    Locale saved;
    @BeforeEach void englishJvm() { saved = Locale.getDefault(); Locale.setDefault(Locale.US); }  // máy chủ thường chạy locale en
    @AfterEach void restore() { Locale.setDefault(saved); }

    String msg(Form f, String field) {
        Validator v = Validation.buildDefaultValidatorFactory().getValidator();
        return v.validate(f).stream().filter(c -> c.getPropertyPath().toString().equals(field))
            .findFirst().orElseThrow().getMessage();
    }

    @Test
    void messagesAreVietnameseEvenOnEnglishLocale() {
        Form f = new Form("x".repeat(121), "short", "ab", " ", null, "ABC");
        assertThat(msg(f, "maxOnly")).isEqualTo("Tối đa 120 ký tự");
        assertThat(msg(f, "minOnly")).isEqualTo("Tối thiểu 8 ký tự");
        assertThat(msg(f, "both")).isEqualTo("Phải từ 3 đến 50 ký tự");
        assertThat(msg(f, "blank")).isEqualTo("Không được để trống");
        assertThat(msg(f, "required")).isEqualTo("Bắt buộc phải có");
        assertThat(msg(f, "pattern")).isEqualTo("Không đúng định dạng cho phép");
    }
}
