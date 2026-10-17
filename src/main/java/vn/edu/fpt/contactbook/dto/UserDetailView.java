package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.Gender;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UserDetailView(Long id, String phone, String fullName, String email, Gender gender,
                             LocalDate dateOfBirth, String address, boolean active, boolean mustChangePassword,
                             LocalDateTime lastLoginAt, LocalDateTime createdAt, List<UserRoleView> roles,
                             StudentProfile student, TeacherProfile teacher, ParentProfile parent,
                             List<ParentLinkView> parents, List<ParentLinkView> children) {

    public record StudentProfile(Long id, String studentCode, String majorName, String classCode,
                                 Short enrollmentYear, StudentStatus status) {
    }

    public record TeacherProfile(Long id, String teacherCode, String departmentName, String academicTitle) {
    }

    public record ParentProfile(Long id, String occupation) {
    }
}
