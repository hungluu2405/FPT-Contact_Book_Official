package vn.edu.fpt.contactbook.dto.api;

import jakarta.validation.constraints.NotBlank;

/** Đặt mật khẩu mới bằng mã nhận qua email; quy tắc mật khẩu như đổi mật khẩu (PasswordPolicy). */
public record PasswordResetConfirmRequest(
        @NotBlank(message = "{api.login.phone.required}") String phone,
        @NotBlank(message = "{forgot.code.required}") String code,
        @NotBlank(message = "{password.new.required}") String newPassword,
        @NotBlank(message = "{password.confirm.required}") String confirmPassword) {
}
