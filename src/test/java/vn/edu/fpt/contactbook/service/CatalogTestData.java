package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Dữ liệu dựng sẵn cho unit test các service danh mục.
 */
final class CatalogTestData {

    private CatalogTestData() {
    }

    static AppUserPrincipal principal(Long id, Set<String> roles, Map<String, Set<Long>> departments) {
        return new AppUserPrincipal(id, "09000000" + id, "Người " + id, null, true, false, roles, departments);
    }

    static Department department(Long id, String code) {
        Department department = new Department();
        department.setId(id);
        department.setCode(code);
        department.setName("Bộ môn " + code);
        return department;
    }

    static Semester semester(Long id, String code) {
        Semester semester = new Semester();
        semester.setId(id);
        semester.setCode(code);
        semester.setName("Học kỳ " + code);
        return semester;
    }

    static Teacher teacher(Long id, String code, Department department) {
        User user = new User();
        user.setFullName("Giảng viên " + code);
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setTeacherCode(code);
        teacher.setDepartment(department);
        teacher.setUser(user);
        return teacher;
    }

    static Student student(Long id, String code, StudentStatus status) {
        User user = new User();
        user.setFullName("Sinh viên " + code);
        Student student = new Student();
        student.setId(id);
        student.setStudentCode(code);
        student.setStatus(status);
        student.setUser(user);
        return student;
    }

    static ClassSection section(Long id, String code, Subject subject, Semester semester, Teacher teacher,
                                Integer maxStudents, SectionStatus status) {
        ClassSection section = new ClassSection();
        section.setId(id);
        section.setCode(code);
        section.setSubject(subject);
        section.setSemester(semester);
        section.setTeacher(teacher);
        section.setMaxStudents(maxStudents == null ? null : maxStudents.shortValue());
        section.setStatus(status);
        return section;
    }

    static Enrollment enrollment(Long id, ClassSection section, Student student, EnrollmentStatus status) {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setClassSection(section);
        enrollment.setStudent(student);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.of(2026, 9, 1, 8, 0));
        return enrollment;
    }

    static Subject subject(Long id, String code, Department department) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setCode(code);
        subject.setName("Môn " + code);
        subject.setCredits((short) 3);
        subject.setDepartment(department);
        return subject;
    }
}
