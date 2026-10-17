package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form đổi mật khẩu. Quy tắc độ dài, chữ, số... nằm trong PasswordPolicy để service dùng lại.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordForm {

    @NotBlank(message = "{password.current.required}")
    private String currentPassword;

    @NotBlank(message = "{password.new.required}")
    private String newPassword;

    @NotBlank(message = "{password.confirm.required}")
    private String confirmPassword;
}
