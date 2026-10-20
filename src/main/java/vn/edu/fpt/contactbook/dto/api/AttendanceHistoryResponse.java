package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.AttendanceHistoryItem;
import vn.edu.fpt.contactbook.dto.StudentAttendanceRow;

/** Điểm danh từng buổi của một lớp học phần, kèm tổng hợp; thresholdPercent như AttendanceOverview. */
public record AttendanceHistoryResponse(StudentAttendanceRow summary, int thresholdPercent,
                                        PageResponse<AttendanceHistoryItem> sessions) {
}
