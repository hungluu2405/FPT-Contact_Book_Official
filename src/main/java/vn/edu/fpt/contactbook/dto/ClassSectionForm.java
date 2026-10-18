package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;

/**
 * Form thêm và sửa lớp học phần. Mã để trống thì service tự đề xuất theo mẫu MÃMÔN-MÃKỲ-STT;
 * sĩ số tối đa để trống là không giới hạn.
 */
@Getter
@Setter
@NoArgsConstructor
public class ClassSectionForm {

    @NotNull(message = "{section.subject.required}")
    private Long subjectId;

    @NotNull(message = "{section.semester.required}")
    private Long semesterId;

    @NotNull(message = "{section.teacher.required}")
    private Long teacherId;

    @Size(max = 50, message = "{section.code.size}")
    private String code;

    private Integer maxStudents;

    @NotNull(message = "{section.status.required}")
    private SectionStatus status = SectionStatus.OPEN;
}
