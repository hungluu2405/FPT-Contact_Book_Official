package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.SectionAttendanceRow;
import vn.edu.fpt.contactbook.dto.SessionView;
import vn.edu.fpt.contactbook.dto.TeacherSectionView;

/** Thống kê điểm danh một lớp: các buổi (để mở bảng điểm danh) và chuyên cần từng sinh viên, mỗi danh sách một trang. */
public record SectionAttendanceResponse(TeacherSectionView section, PageResponse<SessionView> sessions,
                                        PageResponse<SectionAttendanceRow> students, long totalSessions,
                                        int thresholdPercent) {
}
