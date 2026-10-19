package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Bảng điểm danh của một buổi: sinh viên đang học của lớp kèm trạng thái đã lưu (null nếu chưa có).
 * marked = buổi đã có điểm danh, lần lưu tiếp theo là điều chỉnh và cần lý do.
 */
public record AttendanceSheet(Long sectionId, String sectionCode, String subjectCode, String subjectName,
                              Long sessionId, Short sessionNo, LocalDate date, LocalTime startTime,
                              LocalTime endTime, String roomCode, boolean marked, List<Row> rows) {

    public record Row(Long studentId, String studentCode, String fullName, AttendanceStatus status, String note) {
    }
}
