package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<Enrollment> findByClassSectionId(Long classSectionId);

    @EntityGraph(attributePaths = {"classSection", "classSection.subject"})
    List<Enrollment> findByStudentId(Long studentId);

    /** Sinh viên của lớp học phần, sắp theo mã sinh viên; status null là mọi trạng thái. */
    @EntityGraph(attributePaths = {"student", "student.user", "student.schoolClass"})
    @Query(value = """
            SELECT e FROM Enrollment e
            WHERE e.classSection.id = :sectionId AND (:status IS NULL OR e.status = :status)
            ORDER BY e.student.studentCode
            """,
            countQuery = """
            SELECT COUNT(e) FROM Enrollment e
            WHERE e.classSection.id = :sectionId AND (:status IS NULL OR e.status = :status)
            """)
    Page<Enrollment> findPageBySection(@Param("sectionId") Long sectionId, @Param("status") EnrollmentStatus status,
                                       Pageable pageable);

    long countByClassSectionIdAndStatus(Long classSectionId, EnrollmentStatus status);

    /** Số dòng ghi danh theo trạng thái cho nhiều lớp một lần: mỗi phần tử là [classSectionId, count]. */
    @Query("""
            SELECT e.classSection.id, COUNT(e) FROM Enrollment e
            WHERE e.classSection.id IN :sectionIds AND e.status = :status
            GROUP BY e.classSection.id
            """)
    List<Object[]> countBySections(@Param("sectionIds") Collection<Long> sectionIds,
                                   @Param("status") EnrollmentStatus status);

    boolean existsByClassSectionId(Long classSectionId);

    /**
     * Các lớp học phần KHÁC có chung sinh viên đang ENROLLED với lớp sectionId: mỗi phần tử là
     * [classSectionId, số sinh viên chung]. Dùng để kiểm tra trùng lịch của sinh viên.
     */
    @Query("""
            SELECT other.classSection.id, COUNT(DISTINCT other.student.id)
            FROM Enrollment mine, Enrollment other
            WHERE mine.classSection.id = :sectionId
              AND mine.status = vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.ENROLLED
              AND other.student = mine.student
              AND other.status = vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.ENROLLED
              AND other.classSection.id <> :sectionId
            GROUP BY other.classSection.id
            """)
    List<Object[]> countSharedStudents(@Param("sectionId") Long sectionId);

    /** Các lớp học phần của học kỳ mà sinh viên đang ENROLLED (thời khóa biểu, lịch thi). */
    @Query("""
            SELECT e.classSection.id FROM Enrollment e
            WHERE e.student.id = :studentId AND e.classSection.semester.id = :semesterId
              AND e.status = vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.ENROLLED
            """)
    List<Long> findEnrolledSectionIds(@Param("studentId") Long studentId, @Param("semesterId") Long semesterId);

    Optional<Enrollment> findByClassSectionIdAndStudentId(Long classSectionId, Long studentId);

    /** Sinh viên của lớp theo một trạng thái, sắp theo mã sinh viên (bảng điểm danh của một buổi). */
    @EntityGraph(attributePaths = {"student", "student.user"})
    List<Enrollment> findByClassSectionIdAndStatusOrderByStudentStudentCodeAsc(Long classSectionId,
                                                                             EnrollmentStatus status);

    /** Các lớp học phần của học kỳ mà sinh viên đang học hoặc đã học xong (không tính DROPPED), theo mã môn. */
    @EntityGraph(attributePaths = {"classSection", "classSection.subject"})
    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.student.id = :studentId AND e.classSection.semester.id = :semesterId
              AND e.status <> vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.DROPPED
            ORDER BY e.classSection.subject.code, e.classSection.code
            """)
    List<Enrollment> findAttendedInSemester(@Param("studentId") Long studentId, @Param("semesterId") Long semesterId);

    List<Enrollment> findByClassSectionIdAndStudentIdIn(Long classSectionId, Collection<Long> studentIds);

    /** Lớp học phần sinh viên đang ở trạng thái :status (ô chọn lớp của đơn nghỉ), mới ghi danh trước. */
    @EntityGraph(attributePaths = {"classSection", "classSection.subject", "classSection.semester"})
    List<Enrollment> findByStudentIdAndStatusOrderByEnrolledAtDesc(Long studentId, EnrollmentStatus status);
}
