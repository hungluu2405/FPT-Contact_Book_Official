package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;

import java.time.LocalDateTime;

/**
 * Một sinh viên trong danh sách lớp học phần; classCode là lớp hành chính (null nếu chưa xếp lớp).
 */
public record EnrollmentRow(Long enrollmentId, Long studentId, String studentCode, String fullName, String classCode,
                            EnrollmentStatus status, LocalDateTime enrolledAt) {

    public boolean enrolled() {
        return status == EnrollmentStatus.ENROLLED;
    }
}
