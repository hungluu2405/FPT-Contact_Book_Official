package vn.edu.fpt.contactbook.dto.api;

import jakarta.validation.constraints.NotBlank;

/** Cùng quy tắc với form web (PasswordPolicy trong AccountService). */
public record ChangePasswordRequest(
        @NotBlank(message = "{password.current.required}") String currentPassword,
        @NotBlank(message = "{password.new.required}") String newPassword,
        @NotBlank(message = "{password.confirm.required}") String confirmPassword) {
}
