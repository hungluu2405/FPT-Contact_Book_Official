package vn.edu.fpt.contactbook.dto;

/**
 * Một sinh viên đang học trong bảng thống kê điểm danh của lớp học phần.
 */
public record SectionAttendanceRow(Long studentId, String studentCode, String fullName, AttendanceSummary summary) {
}
