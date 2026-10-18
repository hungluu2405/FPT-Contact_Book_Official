package vn.edu.fpt.contactbook.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Thời khóa biểu một tuần (thứ Hai đến Chủ nhật); previous/next là ngày thứ Hai của tuần trước/sau.
 */
public record ScheduleWeek(LocalDate weekStart, LocalDate weekEnd, LocalDate previous, LocalDate next,
                           List<Day> days) {

    /** Một ngày trong tuần và các buổi của ngày đó theo giờ; today = ngày hôm nay. */
    public record Day(LocalDate date, boolean today, List<ScheduleItem> items) {
    }
}
