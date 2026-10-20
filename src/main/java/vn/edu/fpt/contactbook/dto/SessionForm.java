package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form thêm và sửa một buổi học; phòng để trống là chưa xếp phòng.
 */
@Getter
@Setter
@NoArgsConstructor
public class SessionForm {

    @NotNull(message = "{schedule.date.required}")
    @DateTimeFormat(pattern = DateFormats.DATE, fallbackPatterns = {DateFormats.DATE_SHORT, DateFormats.ISO_DATE})
    private LocalDate sessionDate;

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
