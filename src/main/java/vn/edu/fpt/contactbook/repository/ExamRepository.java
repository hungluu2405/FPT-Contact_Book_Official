package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Exam;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    @EntityGraph(attributePaths = {"room"})
    Page<Exam> findByClassSectionIdOrderByExamDateAscStartTimeAsc(Long classSectionId, Pageable pageable);

    /**
     * Lịch thi (của lớp chưa hủy) trong khoảng ngày có thể xung đột: cùng phòng, hoặc thuộc một trong sectionIds
     * (chính lớp hoặc lớp có chung sinh viên). excludeExamId là lịch thi đang sửa (null khi thêm mới); sectionIds không được
     * rỗng (truyền -1 khi không có lớp nào).
     */
    @EntityGraph(attributePaths = {"classSection", "classSection.subject", "room"})
    @Query("""
            SELECT x FROM Exam x JOIN x.classSection c LEFT JOIN x.room r
            WHERE x.examDate BETWEEN :fromDate AND :toDate
              AND c.status <> vn.edu.fpt.contactbook.model.enums.SectionStatus.CANCELLED
              AND (:excludeExamId IS NULL OR x.id <> :excludeExamId)
              AND ((:roomId IS NOT NULL AND r.id = :roomId) OR c.id IN :sectionIds)
            """)
    List<Exam> findPossibleConflicts(@Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate,
                                     @Param("excludeExamId") Long excludeExamId,
                                     @Param("roomId") Long roomId,
                                     @Param("sectionIds") Collection<Long> sectionIds);

    /** Lịch thi của các lớp (giảng viên, sinh viên, phụ huynh xem). */
    @EntityGraph(attributePaths = {"classSection", "classSection.subject", "room"})
    @Query(value = """
            SELECT x FROM Exam x WHERE x.classSection.id IN :sectionIds ORDER BY x.examDate, x.startTime
            """,
            countQuery = "SELECT COUNT(x) FROM Exam x WHERE x.classSection.id IN :sectionIds")
    Page<Exam> findPageForSections(@Param("sectionIds") Collection<Long> sectionIds, Pageable pageable);

    /** Lịch thi từ ngày fromDate trở đi của các lớp, sớm nhất trước (trang chủ); pageable giới hạn số dòng. */
    @EntityGraph(attributePaths = {"classSection", "classSection.subject", "room"})
    @Query("""
            SELECT x FROM Exam x WHERE x.classSection.id IN :sectionIds AND x.examDate >= :fromDate
            ORDER BY x.examDate, x.startTime
            """)
    List<Exam> findUpcomingForSections(@Param("sectionIds") Collection<Long> sectionIds,
                                       @Param("fromDate") LocalDate fromDate, Pageable pageable);
}
