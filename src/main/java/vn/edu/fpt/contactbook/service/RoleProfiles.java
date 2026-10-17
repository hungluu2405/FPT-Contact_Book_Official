package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.RoleProfileForm;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Major;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;

import java.util.Locale;
import java.util.Map;

/**
 * Kiểm tra và ghi hồ sơ theo vai trò (students, teachers, parents), dùng chung khi thêm, sửa người dùng
 * và khi gán vai trò cho người chưa có hồ sơ. Lỗi được gom vào errors theo tên ô "profile.*".
 */
@Component
@RequiredArgsConstructor
public class RoleProfiles {

    static final int MIN_ENROLLMENT_YEAR = 1990;
    static final int MAX_ENROLLMENT_YEAR = 2100;

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final MajorRepository majorRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final DepartmentRepository departmentRepository;

    static boolean needsStudent(String roleCode) {
        return RoleCodes.STUDENT.equals(roleCode);
    }

    static boolean needsTeacher(String roleCode) {
        return RoleCodes.TEACHER.equals(roleCode) || RoleCodes.HEAD_OF_DEPARTMENT.equals(roleCode);
    }

    static boolean needsParent(String roleCode) {
        return RoleCodes.PARENT.equals(roleCode);
    }

    /** Mã SV/GV: bỏ khoảng trắng hai đầu, viết hoa; rỗng thành null. */
    static void normalize(RoleProfileForm form) {
        form.setStudentCode(upper(form.getStudentCode()));
        form.setTeacherCode(upper(form.getTeacherCode()));
    }

    /** existingStudentId: id hồ sơ đang sửa (null khi tạo mới) để bỏ qua chính nó khi kiểm tra trùng mã. */
    void checkStudent(RoleProfileForm form, Long existingStudentId, Map<String, String> errors) {
        String code = form.getStudentCode();
        if (code == null) {
            errors.putIfAbsent("profile.studentCode", "Vui lòng nhập mã sinh viên");
        } else if (existingStudentId == null
                ? studentRepository.existsByStudentCode(code)
                : studentRepository.existsByStudentCodeAndIdNot(code, existingStudentId)) {
            errors.putIfAbsent("profile.studentCode", UserMessages.STUDENT_CODE_TAKEN);
        }

        Major major = form.getMajorId() == null ? null : majorRepository.findById(form.getMajorId()).orElse(null);
        if (major == null) {
            errors.putIfAbsent("profile.majorId", "Vui lòng chọn ngành");
        }
        if (form.getClassId() != null) {
            SchoolClass schoolClass = schoolClassRepository.findById(form.getClassId()).orElse(null);
            if (schoolClass == null) {
                errors.putIfAbsent("profile.classId", "Lớp không tồn tại");
            } else if (major != null && !schoolClass.getMajor().getId().equals(major.getId())) {
                errors.putIfAbsent("profile.classId", "Lớp không thuộc ngành đã chọn");
            }
        }

        Short year = form.getEnrollmentYear();
        if (year == null) {
            errors.putIfAbsent("profile.enrollmentYear", "Vui lòng nhập năm nhập học");
        } else if (year < MIN_ENROLLMENT_YEAR || year > MAX_ENROLLMENT_YEAR) {
            errors.putIfAbsent("profile.enrollmentYear", "Năm nhập học không hợp lệ");
        }
    }

    void checkTeacher(RoleProfileForm form, Long existingTeacherId, Map<String, String> errors) {
        String code = form.getTeacherCode();
        if (code == null) {
            errors.putIfAbsent("profile.teacherCode", "Vui lòng nhập mã giảng viên");
        } else if (existingTeacherId == null
                ? teacherRepository.existsByTeacherCode(code)
                : teacherRepository.existsByTeacherCodeAndIdNot(code, existingTeacherId)) {
            errors.putIfAbsent("profile.teacherCode", UserMessages.TEACHER_CODE_TAKEN);
        }
        if (form.getTeacherDepartmentId() == null
                || !departmentRepository.existsById(form.getTeacherDepartmentId())) {
            errors.putIfAbsent("profile.teacherDepartmentId", "Vui lòng chọn bộ môn của giảng viên");
        }
    }

    /** Ghi các trường đã kiểm tra bằng checkStudent vào hồ sơ. */
    Student applyStudent(Student student, RoleProfileForm form) {
        student.setStudentCode(form.getStudentCode());
        student.setMajor(majorRepository.getReferenceById(form.getMajorId()));
        student.setSchoolClass(form.getClassId() == null ? null
                : schoolClassRepository.getReferenceById(form.getClassId()));
        student.setEnrollmentYear(form.getEnrollmentYear());
        student.setStatus(form.getStudentStatus() == null ? StudentStatus.STUDYING : form.getStudentStatus());
        return student;
    }

    Teacher applyTeacher(Teacher teacher, RoleProfileForm form) {
        teacher.setTeacherCode(form.getTeacherCode());
        teacher.setDepartment(departmentRepository.getReferenceById(form.getTeacherDepartmentId()));
        teacher.setAcademicTitle(form.getAcademicTitle());
        return teacher;
    }

    Parent applyParent(Parent parent, RoleProfileForm form) {
        parent.setOccupation(form.getOccupation());
        return parent;
    }

    Department department(Long departmentId) {
        return departmentId == null ? null : departmentRepository.findById(departmentId).orElse(null);
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
