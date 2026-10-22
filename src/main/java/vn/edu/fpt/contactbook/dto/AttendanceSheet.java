package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Bảng điểm danh của một buổi: sinh viên đang học của lớp kèm trạng thái đã lưu (null nếu chưa có).
 * marked = buổi đã có điểm danh, lần lưu tiếp theo là điều chỉnh và cần lý do.
 * approvedLeave = sinh viên có đơn nghỉ đã duyệt phủ ngày của buổi (không chọn lớp hoặc đúng lớp này).
 * editableUntil = hạn điểm danh, sửa của giáo viên (AttendanceEditWindow); locked = người đang xem là giáo viên và đã
 * quá hạn (chỉ xem); reasonRequired = lần lưu tiếp theo phải có lý do (buổi đã điểm danh, hoặc ADMIN điểm danh thay).
 */
public record AttendanceSheet(Long sectionId, String sectionCode, String subjectCode, String subjectName,
                              Long sessionId, Short sessionNo, LocalDate date, LocalTime startTime,
                              LocalTime endTime, String roomCode, boolean marked, LocalDateTime editableUntil,
                              boolean locked, boolean reasonRequired, List<Row> rows) {

    public record Row(Long studentId, String studentCode, String fullName, AttendanceStatus status, String note,
                      boolean approvedLeave) {
    }
}
