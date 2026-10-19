package vn.edu.fpt.contactbook.dto;

/**
 * Điểm danh của một sinh viên trong một lớp học phần (một môn) ở trang xem của sinh viên và phụ huynh.
 */
public record StudentAttendanceRow(Long sectionId, String sectionCode, String subjectCode, String subjectName,
                                   AttendanceSummary summary) {
}
