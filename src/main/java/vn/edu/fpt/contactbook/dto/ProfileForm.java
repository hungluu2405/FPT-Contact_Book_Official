package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Các trường người dùng tự sửa trong hồ sơ. Số điện thoại do quản trị viên quản lý nên không có ở đây.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProfileForm {

    @Email(message = "{profile.email.invalid}")
    @Size(max = 150, message = "{profile.email.size}")
    private String email;

    @Size(max = 255, message = "{profile.address.size}")
    private String address;
}
