package vn.edu.fpt.contactbook.dto;

/**
 * Lớp hành chính kèm ngành và giảng viên chủ nhiệm (null nếu chưa có).
 */
public record SchoolClassView(Long id, String code, String name, Short intakeYear, Long majorId, String majorCode,
                              String majorName, String homeroomTeacherCode, String homeroomTeacherName) {
}
