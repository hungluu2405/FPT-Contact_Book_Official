package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Bộ lọc đã áp dụng của một báo cáo và các lựa chọn cho ô chọn: subjects là môn có lớp trong phạm vi ở học kỳ đã
 * chọn, sections là lớp trong phạm vi (của môn đã chọn nếu có). subjectId, sectionId null là "Tất cả".
 */
public record ReportSelection(SemesterView semester, Long subjectId, Long sectionId, List<OptionView> subjects,
                              List<OptionView> sections) {
}
