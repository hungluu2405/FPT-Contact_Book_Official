package vn.edu.fpt.contactbook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Hạn điểm danh của giáo viên (app.attendance.edit-window-hours, mặc định 24 giờ):
 * <ul>
 *   <li>Buổi đã điểm danh: sửa được tới lần điểm danh đầu (attendance.marked_at sớm nhất) cộng số giờ.</li>
 *   <li>Buổi chưa điểm danh: điểm danh lần đầu được tới giờ kết thúc buổi học cộng số giờ.</li>
 * </ul>
 * Quá hạn thì giáo viên chỉ xem; ADMIN (trang quản lý lớp học phần) vẫn sửa được, bắt buộc có lý do.
 * Giờ tính theo Asia/Ho_Chi_Minh; "bây giờ" truyền vào để test được.
 */
@Component
public class AttendanceEditWindow {

    private final Duration window;

    public AttendanceEditWindow(@Value("${app.attendance.edit-window-hours}") int hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("app.attendance.edit-window-hours must be positive");
        }
        this.window = Duration.ofHours(hours);
    }

    public long hours() {
        return window.toHours();
    }

    /** firstMarkedAt null: buổi chưa điểm danh, hạn tính từ giờ kết thúc buổi học. */
    public LocalDateTime deadline(LocalDateTime firstMarkedAt, LocalDate sessionDate, LocalTime sessionEnd) {
        LocalDateTime start = firstMarkedAt != null ? firstMarkedAt : LocalDateTime.of(sessionDate, sessionEnd);
        return start.plus(window);
    }

    /** Còn trong hạn khi chưa qua mốc (đúng mốc vẫn được). */
    public boolean isOpen(LocalDateTime deadline, LocalDateTime now) {
        return !now.isAfter(deadline);
    }
}
