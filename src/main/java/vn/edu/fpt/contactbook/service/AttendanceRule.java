package vn.edu.fpt.contactbook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.AttendanceSummary;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Công thức chuyên cần (SRS FR-ATT-02, 04):
 * tỷ lệ vắng = (ABSENT_EXCUSED + ABSENT_UNEXCUSED) / số buổi chưa hủy của lớp.
 * Cảnh báo khi tỷ lệ vắng VƯỢT ngưỡng (lớn hơn hẳn); lớp chưa có buổi nào thì không cảnh báo.
 * tỷ lệ chuyên cần = PRESENT / số buổi sinh viên đã được điểm danh (đi muộn được điểm danh là PRESENT).
 * So sánh với ngưỡng bằng số nguyên để không lệch do làm tròn.
 */
@Component
public class AttendanceRule {

    private final int thresholdPercent;

    public AttendanceRule(@Value("${app.attendance.absent-threshold-percent}") int thresholdPercent) {
        if (thresholdPercent < 0 || thresholdPercent > 100) {
            throw new IllegalArgumentException("app.attendance.absent-threshold-percent must be between 0 and 100");
        }
        this.thresholdPercent = thresholdPercent;
    }

    public int thresholdPercent() {
        return thresholdPercent;
    }

    /** counts: số buổi theo trạng thái (thiếu khóa là 0); totalSessions: số buổi chưa hủy của lớp. */
    public AttendanceSummary summarize(Map<AttendanceStatus, Long> counts, long totalSessions) {
        long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
        long excused = counts.getOrDefault(AttendanceStatus.ABSENT_EXCUSED, 0L);
        long unexcused = counts.getOrDefault(AttendanceStatus.ABSENT_UNEXCUSED, 0L);
        long absent = excused + unexcused;
        long marked = present + absent;
        boolean warning = totalSessions > 0 && absent * 100 > (long) thresholdPercent * totalSessions;
        return new AttendanceSummary(present, excused, unexcused, totalSessions,
                marked == 0 ? null : percent(present, marked),
                totalSessions == 0 ? BigDecimal.ZERO.setScale(1) : percent(absent, totalSessions), warning);
    }

    private static BigDecimal percent(long part, long whole) {
        return BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }
}
