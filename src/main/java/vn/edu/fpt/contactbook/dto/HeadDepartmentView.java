package vn.edu.fpt.contactbook.dto;

/** Một tổ chuyên môn người xem làm tổ trưởng, kèm số giáo viên của tổ. */
public record HeadDepartmentView(Long id, String code, String name, long teacherCount) {
}
