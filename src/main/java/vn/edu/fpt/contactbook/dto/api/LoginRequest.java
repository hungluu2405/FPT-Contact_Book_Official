package vn.edu.fpt.contactbook.dto.api;

import jakarta.validation.constraints.NotBlank;

/** Số điện thoại nhận mọi dạng PhoneUtils chuẩn hóa được (+84, khoảng trắng, dấu chấm...). */
public record LoginRequest(
        @NotBlank(message = "{api.login.phone.required}") String phone,
        @NotBlank(message = "{api.login.password.required}") String password) {
}
