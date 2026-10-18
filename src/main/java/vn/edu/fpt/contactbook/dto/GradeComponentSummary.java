package vn.edu.fpt.contactbook.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Các đầu điểm của một môn và tổng trọng số; complete = tổng đúng 100%.
 */
public record GradeComponentSummary(List<GradeComponentView> components, BigDecimal totalWeight, boolean complete) {
}
