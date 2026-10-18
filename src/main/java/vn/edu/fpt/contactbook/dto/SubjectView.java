package vn.edu.fpt.contactbook.dto;

/**
 * Môn học kèm bộ môn.
 */
public record SubjectView(Long id, String code, String name, Short credits, String description, Long departmentId,
                          String departmentCode, String departmentName) {
}
