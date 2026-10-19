package vn.edu.fpt.contactbook.dto;

import java.math.BigDecimal;

/**
 * Ô điểm đã công bố đang được điều chỉnh: sinh viên, đầu điểm, điểm và ghi chú hiện tại.
 */
public record GradeAdjustView(Long gradeId, String sectionCode, String studentCode, String fullName,
                              String componentName, BigDecimal maxScore, BigDecimal score, String note) {
}
