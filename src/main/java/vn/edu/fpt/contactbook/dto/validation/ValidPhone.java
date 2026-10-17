package vn.edu.fpt.contactbook.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Số điện thoại chuẩn hóa được về 10 chữ số bắt đầu bằng 0 (nhận +84, 84, khoảng trắng, dấu chấm, gạch ngang).
 * Rỗng thì coi là hợp lệ, để @NotBlank báo lỗi bắt buộc riêng.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPhone.Validator.class)
public @interface ValidPhone {

    String message() default "{user.phone.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPhone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || PhoneUtils.normalize(value).isPresent();
        }
    }
}
