package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Form quên mật khẩu, bước 2: mã nhận qua email và mật khẩu mới (quy tắc trong PasswordPolicy). */
@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordForm {

    @NotBlank(message = "{forgot.phone.required}")
    private String phone;

    @NotBlank(message = "{forgot.code.required}")
    private String code;

    @NotBlank(message = "{password.new.required}")
    private String newPassword;

    @NotBlank(message = "{password.confirm.required}")
    private String confirmPassword;
}
