package vn.edu.fpt.contactbook.dto.api;

import jakarta.validation.constraints.NotBlank;

/** Yêu cầu gửi mã quên mật khẩu; số điện thoại nhận mọi dạng PhoneUtils chuẩn hóa được. */
public record PasswordResetCodeRequest(
        @NotBlank(message = "{api.login.phone.required}") String phone) {
}
