package vn.giapha.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;

/** Độ dài UTF-8 tối đa (bcrypt chỉ dùng 72 byte đầu, nên ký tự đa byte phải tính theo byte). */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
public @interface MaxUtf8Bytes {
    int value() default 72;
    String message() default "Mật khẩu quá dài (tối đa 72 byte, ký tự có dấu chiếm nhiều byte)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {
        private int max;
        @Override public void initialize(MaxUtf8Bytes a) { max = a.value(); }
        @Override public boolean isValid(String s, ConstraintValidatorContext c) {
            return s == null || s.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
