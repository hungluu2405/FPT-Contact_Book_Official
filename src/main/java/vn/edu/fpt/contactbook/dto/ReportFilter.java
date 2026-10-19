package vn.edu.fpt.contactbook.dto;

/**
 * Bộ lọc của báo cáo điểm danh và báo cáo điểm; null là không lọc. semesterId null là học kỳ hiện tại; có sectionId
 * thì học kỳ và môn lấy theo lớp học phần đó.
 */
public record ReportFilter(Long semesterId, Long subjectId, Long sectionId) {
}
