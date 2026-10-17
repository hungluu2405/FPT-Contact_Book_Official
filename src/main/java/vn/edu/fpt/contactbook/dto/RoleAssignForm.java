package vn.edu.fpt.contactbook.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gán một cặp (vai trò, bộ môn). Nếu người dùng chưa có hồ sơ cho vai trò đó thì nhập hồ sơ cùng lúc.
 */
@Getter
@Setter
@NoArgsConstructor
public class RoleAssignForm {

    private String roleCode;
    private Long departmentId;

    @Valid
    private RoleProfileForm profile = new RoleProfileForm();
}
