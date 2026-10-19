package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.SectionStatus;

/**
 * Một lớp học phần trong trang lớp giảng dạy và tiêu đề trang thống kê điểm danh.
 */
public record TeacherSectionView(Long id, String code, String subjectCode, String subjectName, String semesterCode,
                                 SectionStatus status) {
}
