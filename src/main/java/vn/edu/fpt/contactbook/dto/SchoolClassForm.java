package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form thêm và sửa lớp hành chính. Giảng viên chủ nhiệm nhập bằng mã giảng viên, để trống là chưa có.
 */
@Getter
@Setter
@NoArgsConstructor
public class SchoolClassForm {

    @NotNull(message = "{catalog.major.required}")
    private Long majorId;

    @NotBlank(message = "{catalog.code.required}")
    @Size(max = 20, message = "{catalog.code.size}")
    private String code;

    @NotBlank(message = "{catalog.name.required}")
    @Size(max = 150, message = "{catalog.name.size150}")
    private String name;

    @NotNull(message = "{catalog.intake-year.required}")
    private Integer intakeYear;

    @Size(max = 20, message = "{user.teacher-code.size}")
    private String homeroomTeacherCode;
}
