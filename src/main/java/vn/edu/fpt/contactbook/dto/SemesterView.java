package vn.edu.fpt.contactbook.dto;

import java.time.LocalDate;

/**
 * Học kỳ; current = đang là học kỳ hiện tại.
 */
public record SemesterView(Long id, String code, String name, LocalDate startDate, LocalDate endDate, boolean current) {
}
