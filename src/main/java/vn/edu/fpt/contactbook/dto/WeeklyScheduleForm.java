package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Form "Tạo lịch hàng tuần": các thứ trong tuần, cùng khung giờ và phòng, từ ngày bắt đầu, đủ count buổi.
 */
@Getter
@Setter
@NoArgsConstructor
public class WeeklyScheduleForm {

    private Set<DayOfWeek> days = new LinkedHashSet<>();

    @NotNull(message = "{schedule.start-time.required}")
    @DateTimeFormat(pattern = DateFormats.TIME, fallbackPatterns = DateFormats.TIME_SHORT)
    private LocalTime startTime;

    @NotNull(message = "{schedule.end-time.required}")
    @DateTimeFormat(pattern = DateFormats.TIME, fallbackPatterns = DateFormats.TIME_SHORT)
    private LocalTime endTime;

    private Long roomId;

    @NotNull(message = "{schedule.from-date.required}")
    @DateTimeFormat(pattern = DateFormats.DATE, fallbackPatterns = {DateFormats.DATE_SHORT, DateFormats.ISO_DATE})
    private LocalDate fromDate;

    @NotNull(message = "{schedule.count.required}")
    private Integer count;
}
