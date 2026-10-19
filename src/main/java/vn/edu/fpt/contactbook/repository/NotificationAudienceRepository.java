package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.User;

import java.util.Collection;
import java.util.List;

/**
 * Truy vấn chỉ đọc để xác định người nhận thông báo (chỉ lấy users.id, chỉ tài khoản is_active = 1) và kiểm tra
 * người nhận cá nhân có nằm trong phạm vi của người gửi không. Gom về một chỗ vì đều phục vụ phân hệ thông báo.
 */
@Repository
public interface NotificationAudienceRepository extends org.springframework.data.repository.Repository<User, Long> {

    String ENROLLED = "vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.ENROLLED";

    @Query("SELECT u.id FROM User u WHERE u.active = true")
    List<Long> allActive();

    @Query("SELECT u.id FROM User u WHERE u.id IN :userIds AND u.active = true")
    List<Long> activeAmong(@Param("userIds") Collection<Long> userIds);

    @Query("SELECT DISTINCT ur.user.id FROM UserRole ur WHERE ur.role.id = :roleId AND ur.user.active = true")
    List<Long> withRole(@Param("roleId") Long roleId);

    /** Giảng viên của bộ môn (teachers.department_id). */
    @Query("SELECT t.user.id FROM Teacher t WHERE t.department.id = :departmentId AND t.user.active = true")
    List<Long> teachersOfDepartment(@Param("departmentId") Long departmentId);

    /** Sinh viên của lớp hành chính. */
    @Query("SELECT s.user.id FROM Student s WHERE s.schoolClass.id = :classId AND s.user.active = true")
    List<Long> studentsOfClass(@Param("classId") Long classId);

    /** Phụ huynh đã liên kết của sinh viên lớp hành chính. */
    @Query("""
            SELECT ps.parent.user.id FROM ParentStudent ps
            WHERE ps.student.schoolClass.id = :classId AND ps.parent.user.active = true
            """)
    List<Long> parentsOfClass(@Param("classId") Long classId);

    /** Sinh viên đang ENROLLED của lớp học phần. */
    @Query("SELECT e.student.user.id FROM Enrollment e WHERE e.classSection.id = :sectionId AND e.status = "
            + ENROLLED + " AND e.student.user.active = true")
    List<Long> studentsOfSection(@Param("sectionId") Long sectionId);

    /** Phụ huynh đã liên kết của sinh viên đang ENROLLED trong lớp học phần. */
    @Query("""
            SELECT ps.parent.user.id FROM ParentStudent ps, Enrollment e
            WHERE e.student = ps.student AND e.classSection.id = :sectionId
              AND e.status = vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.ENROLLED
              AND ps.parent.user.active = true
            """)
    List<Long> parentsOfSection(@Param("sectionId") Long sectionId);

    /** Tài khoản của các sinh viên (students.id) cùng phụ huynh đã liên kết của họ. */
    @Query("SELECT s.user.id FROM Student s WHERE s.id IN :studentIds AND s.user.active = true")
    List<Long> studentUsers(@Param("studentIds") Collection<Long> studentIds);

    @Query("""
            SELECT ps.parent.user.id FROM ParentStudent ps
            WHERE ps.student.id IN :studentIds AND ps.parent.user.active = true
            """)
    List<Long> parentUsers(@Param("studentIds") Collection<Long> studentIds);

    /** Người dùng là sinh viên ENROLLED ở lớp học phần do giảng viên này dạy. */
    @Query("SELECT COUNT(e) > 0 FROM Enrollment e WHERE e.student.user.id = :userId AND e.status = " + ENROLLED
            + " AND e.classSection.teacher.id = :teacherId")
    boolean isStudentOfTeacher(@Param("userId") Long userId, @Param("teacherId") Long teacherId);

    /** Người dùng là phụ huynh của sinh viên ENROLLED ở lớp học phần do giảng viên này dạy. */
    @Query("""
            SELECT COUNT(ps) > 0 FROM ParentStudent ps, Enrollment e
            WHERE ps.parent.user.id = :userId AND e.student = ps.student
              AND e.status = vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.ENROLLED
              AND e.classSection.teacher.id = :teacherId
            """)
    boolean isParentOfTeacherStudent(@Param("userId") Long userId, @Param("teacherId") Long teacherId);

    /** Người dùng là sinh viên ENROLLED ở lớp học phần có môn thuộc một trong các bộ môn. */
    @Query("SELECT COUNT(e) > 0 FROM Enrollment e WHERE e.student.user.id = :userId AND e.status = " + ENROLLED
            + " AND e.classSection.subject.department.id IN :departmentIds")
    boolean isStudentOfDepartments(@Param("userId") Long userId,
                                   @Param("departmentIds") Collection<Long> departmentIds);

    /** Người dùng là phụ huynh của sinh viên ENROLLED ở lớp học phần có môn thuộc một trong các bộ môn. */
    @Query("""
            SELECT COUNT(ps) > 0 FROM ParentStudent ps, Enrollment e
            WHERE ps.parent.user.id = :userId AND e.student = ps.student
              AND e.status = vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.ENROLLED
              AND e.classSection.subject.department.id IN :departmentIds
            """)
    boolean isParentOfDepartmentStudent(@Param("userId") Long userId,
                                        @Param("departmentIds") Collection<Long> departmentIds);

    /** Người dùng là giảng viên thuộc một trong các bộ môn. */
    @Query("SELECT COUNT(t) > 0 FROM Teacher t WHERE t.user.id = :userId AND t.department.id IN :departmentIds")
    boolean isTeacherOfDepartments(@Param("userId") Long userId,
                                   @Param("departmentIds") Collection<Long> departmentIds);
}
