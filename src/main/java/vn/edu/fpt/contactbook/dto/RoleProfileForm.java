package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;

/**
 * Hồ sơ theo vai trò (sinh viên, giảng viên, phụ huynh). Ô nào bắt buộc tùy vai trò được chọn,
 * nên kiểm tra bắt buộc nằm ở service.
 */
@Getter
@Setter
@NoArgsConstructor
public class RoleProfileForm {

    @Size(max = 20, message = "{user.student-code.size}")
    private String studentCode;
    private Long majorId;
    private Long classId;
    private Short enrollmentYear;
    private StudentStatus studentStatus;

    @Size(max = 20, message = "{user.teacher-code.size}")
    private String teacherCode;
    private Long teacherDepartmentId;
    @Size(max = 50, message = "{user.academic-title.size}")
    private String academicTitle;

    @Size(max = 100, message = "{user.occupation.size}")
    private String occupation;
}
