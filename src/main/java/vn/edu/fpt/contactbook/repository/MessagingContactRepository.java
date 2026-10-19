package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.User;

/**
 * Truy vấn chỉ đọc cho quy tắc "ai nhắn cho ai" của tin nhắn. "Đang dạy" nghĩa là sinh viên ENROLLED trong lớp học
 * phần của giảng viên, lớp thuộc học kỳ hiện tại (is_current = 1) và không CANCELLED.
 */
@Repository
public interface MessagingContactRepository extends org.springframework.data.repository.Repository<User, Long> {

    /** Điều kiện "lớp học phần e.classSection đang dạy" dùng chung cho mọi truy vấn bên dưới. */
    String TEACHING_NOW = """
             e.status = vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.ENROLLED
             AND e.classSection.semester.current = true
             AND e.classSection.status <> vn.edu.fpt.contactbook.model.enums.SectionStatus.CANCELLED
            """;

    /** Người dùng u là giảng viên (TEACHER hoặc HEAD_OF_DEPARTMENT). */
    String HAS_TEACHER_ROLE = " EXISTS (SELECT 1 FROM UserRole ur WHERE ur.user = u"
            + " AND ur.role.code IN ('TEACHER', 'HEAD_OF_DEPARTMENT')) ";

    /**
     * Danh bạ "Nhắn tin mới": người đang hoạt động (khác chính mình) mà người dùng me được nhắn, theo cờ vai trò
     * của me. keywordPattern đã escape và bọc %...% (LikePatterns), null là không lọc.
     */
    String CONTACTS_FROM_WHERE = """
            FROM User u
            WHERE u.active = true AND u.id <> :me
              AND (:keywordPattern IS NULL
                   OR LOWER(u.fullName) LIKE :keywordPattern ESCAPE '\\'
                   OR u.phone LIKE :keywordPattern ESCAPE '\\')
              AND (
                   (:meTeacher = true AND """ + HAS_TEACHER_ROLE + """
                   )
                OR (:meTeacher = true
                    AND EXISTS (SELECT 1 FROM UserRole ur WHERE ur.user = u AND ur.role.code = 'STUDENT')
                    AND EXISTS (SELECT 1 FROM Enrollment e WHERE e.student.user = u
                                AND e.classSection.teacher.user.id = :me AND""" + TEACHING_NOW + """
                                ))
                OR (:meTeacher = true
                    AND EXISTS (SELECT 1 FROM UserRole ur WHERE ur.user = u AND ur.role.code = 'PARENT')
                    AND EXISTS (SELECT 1 FROM ParentStudent ps, Enrollment e WHERE ps.parent.user = u
                                AND e.student = ps.student
                                AND e.classSection.teacher.user.id = :me AND""" + TEACHING_NOW + """
                                ))
                OR (:meStudent = true AND """ + HAS_TEACHER_ROLE + """
                    AND EXISTS (SELECT 1 FROM Enrollment e WHERE e.student.user.id = :me
                                AND e.classSection.teacher.user = u AND""" + TEACHING_NOW + """
                                ))
                OR (:meParent = true AND """ + HAS_TEACHER_ROLE + """
                    AND EXISTS (SELECT 1 FROM ParentStudent ps, Enrollment e WHERE ps.parent.user.id = :me
                                AND e.student = ps.student
                                AND e.classSection.teacher.user = u AND""" + TEACHING_NOW + """
                                ))
              )
            """;

    /** Giảng viên (users.id) đang dạy sinh viên (users.id). */
    @Query("""
            SELECT COUNT(e) > 0 FROM Enrollment e
            WHERE e.classSection.teacher.user.id = :teacherUserId AND e.student.user.id = :studentUserId
              AND""" + TEACHING_NOW)
    boolean teachesStudentNow(@Param("teacherUserId") Long teacherUserId,
                              @Param("studentUserId") Long studentUserId);

    /** Giảng viên (users.id) đang dạy ít nhất một con đã liên kết của phụ huynh (users.id). */
    @Query("""
            SELECT COUNT(e) > 0 FROM ParentStudent ps, Enrollment e
            WHERE ps.parent.user.id = :parentUserId AND e.student = ps.student
              AND e.classSection.teacher.user.id = :teacherUserId
              AND""" + TEACHING_NOW)
    boolean teachesChildNow(@Param("teacherUserId") Long teacherUserId, @Param("parentUserId") Long parentUserId);

    @Query(value = "SELECT u " + CONTACTS_FROM_WHERE + " ORDER BY u.fullName, u.id",
            countQuery = "SELECT COUNT(u) " + CONTACTS_FROM_WHERE)
    Page<User> findContacts(@Param("me") Long me, @Param("meTeacher") boolean meTeacher,
                            @Param("meStudent") boolean meStudent, @Param("meParent") boolean meParent,
                            @Param("keywordPattern") String keywordPattern, Pageable pageable);
}
