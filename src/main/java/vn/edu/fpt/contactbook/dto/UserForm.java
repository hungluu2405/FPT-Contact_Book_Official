package vn.edu.fpt.contactbook.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.fpt.contactbook.dto.validation.ValidPhone;
import vn.edu.fpt.contactbook.model.enums.Gender;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Form thêm và sửa người dùng. roles và headDepartmentId chỉ dùng khi thêm;
 * khi sửa, vai trò được quản lý ở màn hình riêng.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserForm {

    @NotBlank(message = "{user.phone.required}")
    @ValidPhone
    private String phone;

    @NotBlank(message = "{user.full-name.required}")
    @Size(max = 150, message = "{user.full-name.size}")
    private String fullName;

    private Gender gender;

    @Past(message = "{user.date-of-birth.past}")
    @DateTimeFormat(pattern = DateFormats.DATE, fallbackPatterns = {DateFormats.DATE_SHORT, DateFormats.ISO_DATE})
    private LocalDate dateOfBirth;

    @Email(message = "{profile.email.invalid}")
    @Size(max = 150, message = "{profile.email.size}")
    private String email;

    @Size(max = 255, message = "{profile.address.size}")
    private String address;

    private Set<String> roles = new LinkedHashSet<>();

    private Long headDepartmentId;

    @Valid
    private RoleProfileForm profile = new RoleProfileForm();
}
