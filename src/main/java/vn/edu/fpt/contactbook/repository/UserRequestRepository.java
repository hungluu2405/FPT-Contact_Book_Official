package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.UserRequest;
import vn.edu.fpt.contactbook.model.enums.RequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Đơn từ. Người duyệt không lưu trong bảng mà tính theo đơn (RequestReviewerResolver); hai điều kiện TEACHER_QUEUE và
 * ADMIN_QUEUE dưới đây là cùng quy tắc đó viết bằng JPQL để lọc và phân trang ở database.
 */
@Repository
public interface UserRequestRepository extends JpaRepository<UserRequest, Long> {

    String LEAVE = "vn.edu.fpt.contactbook.model.enums.RequestType.LEAVE";
    String PENDING = "vn.edu.fpt.contactbook.model.enums.RequestStatus.PENDING";

    /** Đơn nghỉ do giảng viên :teacherId duyệt: giảng viên lớp học phần, hoặc GVCN khi đơn không chọn lớp. */
    String TEACHER_QUEUE = """
            FROM UserRequest r LEFT JOIN r.classSection cs LEFT JOIN r.student s LEFT JOIN s.schoolClass sc
            WHERE r.requestType = """ + LEAVE + """

              AND ((cs IS NOT NULL AND cs.teacher.id = :teacherId)
                   OR (cs IS NULL AND sc.homeroomTeacher.id = :teacherId))
              AND (:status IS NULL OR r.status = :status)
            """;

    /** Đơn do ADMIN xử lý: đơn khác, hoặc đơn nghỉ không chọn lớp mà sinh viên không có GVCN. */
    String ADMIN_QUEUE = """
            FROM UserRequest r LEFT JOIN r.classSection cs LEFT JOIN r.student s LEFT JOIN s.schoolClass sc
            WHERE (r.requestType <> """ + LEAVE + """

                   OR (cs IS NULL AND (sc IS NULL OR sc.homeroomTeacher IS NULL)))
              AND (:status IS NULL OR r.status = :status)
            """;

    @EntityGraph(attributePaths = {"requester", "student", "student.user", "classSection"})
    @Query(value = "SELECT r FROM UserRequest r WHERE r.student.id = :studentId ORDER BY r.createdAt DESC, r.id DESC",
            countQuery = "SELECT COUNT(r) FROM UserRequest r WHERE r.student.id = :studentId")
    Page<UserRequest> findPageByStudent(@Param("studentId") Long studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"requester", "student", "student.user", "classSection"})
    @Query(value = "SELECT r " + TEACHER_QUEUE + " ORDER BY r.createdAt DESC, r.id DESC",
            countQuery = "SELECT COUNT(r) " + TEACHER_QUEUE)
    Page<UserRequest> findTeacherQueue(@Param("teacherId") Long teacherId, @Param("status") RequestStatus status,
                                       Pageable pageable);

    @EntityGraph(attributePaths = {"requester", "student", "student.user", "classSection"})
    @Query(value = "SELECT r " + ADMIN_QUEUE + " ORDER BY r.createdAt DESC, r.id DESC",
            countQuery = "SELECT COUNT(r) " + ADMIN_QUEUE)
    Page<UserRequest> findAdminQueue(@Param("status") RequestStatus status, Pageable pageable);

    @Query("SELECT COUNT(r) " + TEACHER_QUEUE)
    long countTeacherQueue(@Param("teacherId") Long teacherId, @Param("status") RequestStatus status);

    @Query("SELECT COUNT(r) " + ADMIN_QUEUE)
    long countAdminQueue(@Param("status") RequestStatus status);

    /** Chi tiết kèm những gì cần để xác định người duyệt và hiển thị (không N+1). */
    @EntityGraph(attributePaths = {"requester", "student", "student.user", "student.schoolClass",
            "student.schoolClass.homeroomTeacher", "student.schoolClass.homeroomTeacher.user", "classSection",
            "classSection.subject", "classSection.teacher", "classSection.teacher.user"})
    @Query("SELECT r FROM UserRequest r WHERE r.id = :id")
    Optional<UserRequest> findDetailById(@Param("id") Long id);

    /**
     * Chuyển đơn từ PENDING sang trạng thái mới chỉ khi đơn vẫn PENDING ngay lúc ghi (một câu UPDATE, SQL Server giữ
     * khóa dòng tới hết transaction), nên duyệt và hủy đồng thời không cùng thành công. Trả số dòng đã đổi (0 hoặc 1).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserRequest r SET r.status = :status, r.updatedAt = :now WHERE r.id = :id AND r.status = "
            + PENDING)
    int transitionFromPending(@Param("id") Long id, @Param("status") RequestStatus status,
                              @Param("now") LocalDateTime now);

    /** Sinh viên (trong :studentIds) có đơn nghỉ đã duyệt phủ ngày :date, không chọn lớp hoặc đúng lớp :sectionId. */
    @Query("""
            SELECT DISTINCT r.student.id FROM UserRequest r
            WHERE r.requestType = """ + LEAVE + """

              AND r.status = vn.edu.fpt.contactbook.model.enums.RequestStatus.APPROVED
              AND r.student.id IN :studentIds
              AND r.fromDate <= :date AND r.toDate >= :date
              AND (r.classSection IS NULL OR r.classSection.id = :sectionId)
            """)
    List<Long> findStudentsWithApprovedLeave(@Param("studentIds") Collection<Long> studentIds,
                                             @Param("sectionId") Long sectionId, @Param("date") LocalDate date);
}
