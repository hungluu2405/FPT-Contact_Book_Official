package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClassSession;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findByClassSectionIdOrderBySessionNoAsc(Long classSectionId);

    /** Mọi buổi học có ngày nằm trong [fromDate, toDate], tính cả hai đầu. */
    List<ClassSession> findBySessionDateBetweenOrderBySessionDateAscStartTimeAsc(LocalDate fromDate, LocalDate toDate);

    /** Buổi của một lớp học phần (mọi trạng thái) theo ngày giờ, cho trang chi tiết lớp. */
    @EntityGraph(attributePaths = {"room"})
    Page<ClassSession> findByClassSectionIdOrderBySessionDateAscStartTimeAsc(Long classSectionId, Pageable pageable);

    /** Buổi của chính lớp trong khoảng ngày, mọi trạng thái (uq_sess_cs_slot tính cả buổi đã hủy). */
    List<ClassSession> findByClassSectionIdAndSessionDateBetween(Long classSectionId, LocalDate fromDate,
                                                                 LocalDate toDate);

    @Query("SELECT MAX(s.sessionNo) FROM ClassSession s WHERE s.classSection.id = :sectionId")
    Short findMaxSessionNo(@Param("sectionId") Long sectionId);

    /**
     * Buổi chưa hủy của lớp KHÁC (lớp chưa hủy) trong khoảng ngày có thể xung đột: cùng phòng, cùng giảng viên,
     * hoặc thuộc một trong sectionIds (lớp có chung sinh viên). roomId, teacherId null là không xét tiêu chí đó;
     * sectionIds không được rỗng (truyền -1 khi không có lớp nào).
     */
    @EntityGraph(attributePaths = {"classSection", "classSection.subject", "room"})
    @Query("""
            SELECT s FROM ClassSession s JOIN s.classSection c LEFT JOIN s.room r
            WHERE s.status <> vn.edu.fpt.contactbook.model.enums.SessionStatus.CANCELLED
              AND c.status <> vn.edu.fpt.contactbook.model.enums.SectionStatus.CANCELLED
              AND s.sessionDate BETWEEN :fromDate AND :toDate
              AND c.id <> :sectionId
              AND ((:roomId IS NOT NULL AND r.id = :roomId)
                   OR (:teacherId IS NOT NULL AND c.teacher.id = :teacherId)
                   OR c.id IN :sectionIds)
            """)
    List<ClassSession> findPossibleConflicts(@Param("sectionId") Long sectionId,
                                             @Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate,
                                             @Param("roomId") Long roomId,
                                             @Param("teacherId") Long teacherId,
                                             @Param("sectionIds") Collection<Long> sectionIds);

    /** Thời khóa biểu: buổi (mọi trạng thái, để hiện buổi hủy) của các lớp trong khoảng ngày. */
    @EntityGraph(attributePaths = {"classSection", "classSection.subject", "classSection.teacher",
            "classSection.teacher.user", "room"})
    @Query("""
            SELECT s FROM ClassSession s
            WHERE s.classSection.id IN :sectionIds AND s.sessionDate BETWEEN :fromDate AND :toDate
            ORDER BY s.sessionDate, s.startTime
            """)
    List<ClassSession> findForSections(@Param("sectionIds") Collection<Long> sectionIds,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate);

    @EntityGraph(attributePaths = {"classSection", "classSection.subject", "classSection.teacher",
            "classSection.teacher.user", "room"})
    @Query(value = """
            SELECT s FROM ClassSession s
            WHERE s.classSection.id IN :sectionIds
            ORDER BY s.sessionDate, s.startTime
            """,
            countQuery = "SELECT COUNT(s) FROM ClassSession s WHERE s.classSection.id IN :sectionIds")
    Page<ClassSession> findPageForSections(@Param("sectionIds") Collection<Long> sectionIds, Pageable pageable);
}
