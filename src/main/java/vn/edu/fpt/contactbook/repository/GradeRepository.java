package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Grade;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {

    boolean existsByComponentId(Long componentId);

    /** Các đầu điểm của môn đã có ít nhất một dòng điểm (không xóa được). */
    @Query("SELECT DISTINCT g.component.id FROM Grade g WHERE g.component.subject.id = :subjectId")
    Set<Long> findComponentIdsWithGrades(@Param("subjectId") Long subjectId);

    /** Mọi ô điểm của lớp học phần (mọi trạng thái công bố), cho bảng điểm của giảng viên và người quản lý. */
    @Query("SELECT g FROM Grade g WHERE g.enrollment.classSection.id = :sectionId")
    List<Grade> findBySectionId(@Param("sectionId") Long sectionId);

    /** Mọi ô điểm (mọi trạng thái công bố) của nhiều lớp học phần một lần, cho báo cáo điểm. */
    @Query("SELECT g FROM Grade g WHERE g.enrollment.classSection.id IN :sectionIds")
    List<Grade> findBySectionIds(@Param("sectionIds") Collection<Long> sectionIds);

    /** CHỈ ô đã công bố của các dòng ghi danh: nguồn duy nhất cho trang điểm của sinh viên và phụ huynh. */
    @Query("SELECT g FROM Grade g WHERE g.enrollment.id IN :enrollmentIds AND g.isPublished = true")
    List<Grade> findPublishedByEnrollmentIds(@Param("enrollmentIds") Collection<Long> enrollmentIds);

    /** Ô đã có điểm nhưng chưa công bố của sinh viên chưa rút khỏi lớp. */
    String PUBLISHABLE_WHERE = """
            WHERE g.enrollment.classSection.id = :sectionId AND g.isPublished = false AND g.score IS NOT NULL
              AND g.enrollment.status <> vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.DROPPED
            """;

    /** Các ô sẽ được công bố (PUBLISHABLE_WHERE), kèm dòng ghi danh để biết sinh viên cần báo. */
    @EntityGraph(attributePaths = {"enrollment"})
    @Query("SELECT g FROM Grade g " + PUBLISHABLE_WHERE)
    List<Grade> findPublishable(@Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(g) FROM Grade g " + PUBLISHABLE_WHERE)
    long countPublishable(@Param("sectionId") Long sectionId);

    @EntityGraph(attributePaths = {"enrollment", "enrollment.student", "enrollment.student.user", "component"})
    Optional<Grade> findWithDetailsById(Long id);
}
