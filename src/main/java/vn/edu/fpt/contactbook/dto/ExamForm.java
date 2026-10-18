package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.fpt.contactbook.model.enums.ExamType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form thêm và sửa lịch thi; phòng để trống là chưa xếp phòng.
 */
@Getter
@Setter
@NoArgsConstructor
public class ExamForm {

    @NotNull(message = "{exam.type.required}")
    private ExamType examType;

    @NotNull(message = "{exam.date.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate examDate;

    @NotNull(message = "{schedule.start-time.required}")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "{schedule.end-time.required}")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private Long roomId;

    @Size(max = 255, message = "{schedule.note.size}")
    private String note;
}
