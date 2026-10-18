package vn.edu.fpt.contactbook.dto;

/**
 * Ngành kèm khoa.
 */
public record MajorView(Long id, String code, String name, Long facultyId, String facultyCode, String facultyName) {
}
