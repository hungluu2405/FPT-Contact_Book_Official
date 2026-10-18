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

    Optional<Enrollment> findByClassSectionIdAndStudentId(Long classSectionId, Long studentId);

    List<Enrollment> findByClassSectionIdAndStudentIdIn(Long classSectionId, Collection<Long> studentIds);
}
