package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Form quên mật khẩu, bước 1: nhập số điện thoại để nhận mã qua email. */
@Getter
@Setter
@NoArgsConstructor
public class ForgotPasswordForm {

    @NotBlank(message = "{forgot.phone.required}")
    private String phone;
}
