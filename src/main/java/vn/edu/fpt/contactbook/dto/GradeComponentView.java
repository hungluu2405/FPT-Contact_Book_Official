package vn.edu.fpt.contactbook.dto;

import java.math.BigDecimal;

/**
 * Một đầu điểm; hasGrades = đã có điểm dùng đầu điểm này (không xóa được).
 */
public record GradeComponentView(Long id, String name, BigDecimal weight, BigDecimal maxScore, boolean hasGrades) {
}
