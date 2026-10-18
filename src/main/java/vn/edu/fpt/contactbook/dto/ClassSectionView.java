package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.SectionStatus;

/**
 * Lớp học phần kèm môn, học kỳ, giảng viên; enrolledCount là số sinh viên đang ENROLLED,
 * maxStudents null là không giới hạn; hasEnrollments = đã có dòng ghi danh (khóa đổi môn, học kỳ).
 */
public record ClassSectionView(Long id, String code, Long subjectId, String subjectCode, String subjectName,
                               Long departmentId, String departmentCode, Long semesterId, String semesterCode,
                               String semesterName, Long teacherId, String teacherCode, String teacherName,
                               Short maxStudents, SectionStatus status, long enrolledCount, boolean hasEnrollments) {

    public boolean open() {
        return status == SectionStatus.OPEN;
    }

    public boolean full() {
        return maxStudents != null && enrolledCount >= maxStudents;
    }
}
