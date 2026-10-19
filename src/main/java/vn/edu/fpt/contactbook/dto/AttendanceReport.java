package vn.edu.fpt.contactbook.dto;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

/**
 * Báo cáo điểm danh (SRS FR-REPORT-01). section khác null khi lọc theo một lớp học phần (chi tiết từng sinh viên);
 * rows là các lớp trong bộ lọc (một trang), totals là tổng của MỌI lớp trong bộ lọc (tổng hợp theo học kỳ).
 * Chỉ tính sinh viên ENROLLED, giống bảng thống kê điểm danh của lớp.
 */
public record AttendanceReport(ReportSelection selection, int thresholdPercent, SectionDetail section,
                               Page<SectionRow> rows, Totals totals) {

    /**
     * Một lớp: từng sinh viên (tính bằng AttendanceRule như trang thống kê điểm danh), số sinh viên vượt ngưỡng và
     * tổng số buổi theo trạng thái của cả lớp (classSummary).
     */
    public record SectionDetail(TeacherSectionView section, String teacherName, long totalSessions,
                                List<SectionAttendanceRow> students, long warnings, AttendanceSummary classSummary) {
    }

    /** Một dòng của bảng tổng hợp: attendanceRate là chuyên cần của cả lớp, null nếu chưa điểm danh ai. */
    public record SectionRow(TeacherSectionView section, String teacherName, long students, long totalSessions,
                             BigDecimal attendanceRate, long warnings) {
    }

    /** Tổng các lớp trong bộ lọc; attendanceRate tính trên tổng số buổi theo trạng thái của mọi sinh viên. */
    public record Totals(long sections, long students, long warnings, BigDecimal attendanceRate) {
    }
}
