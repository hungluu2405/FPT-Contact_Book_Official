package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Điều chỉnh một ô điểm đã công bố (trưởng bộ môn, ADMIN): điểm mới và lý do đều bắt buộc.
 */
@Getter
@Setter
@NoArgsConstructor
public class GradeAdjustForm {

    @NotBlank(message = "{grade.adjust.score.required}")
    private String score;

    @NotBlank(message = "{grade.adjust.reason.required}")
    @Size(max = 255, message = "{grade.adjust.reason.size}")
    private String reason;
}
