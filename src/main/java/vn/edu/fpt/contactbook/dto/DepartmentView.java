package vn.edu.fpt.contactbook.dto;

/**
 * Bộ môn kèm khoa.
 */
public record DepartmentView(Long id, String code, String name, String description, Long facultyId, String facultyCode,
                             String facultyName) {
}
