package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    boolean existsBySessionId(Long sessionId);

    /** Các buổi của lớp học phần đã có ít nhất một dòng điểm danh. */
    @Query("SELECT DISTINCT a.session.id FROM Attendance a WHERE a.session.classSection.id = :sectionId")
    Set<Long> findSessionIdsWithAttendance(@Param("sectionId") Long sectionId);

    @EntityGraph(attributePaths = {"student"})
    List<Attendance> findBySessionId(Long sessionId);

    /** Điểm danh của một sinh viên ở các buổi đã cho (lịch sử theo môn). */
    List<Attendance> findByStudentIdAndSessionIdIn(Long studentId, Collection<Long> sessionIds);

    /**
     * Số dòng điểm danh theo sinh viên và trạng thái trong một lớp học phần, chỉ buổi chưa hủy:
     * mỗi phần tử là [studentId, AttendanceStatus, count].
     */
    @Query("""
            SELECT a.student.id, a.status, COUNT(a) FROM Attendance a
            WHERE a.session.classSection.id = :sectionId AND a.student.id IN :studentIds
              AND a.session.status <> vn.edu.fpt.contactbook.model.enums.SessionStatus.CANCELLED
            GROUP BY a.student.id, a.status
            """)
    List<Object[]> countByStudentAndStatus(@Param("sectionId") Long sectionId,
                                           @Param("studentIds") Collection<Long> studentIds);

    /**
     * Số dòng điểm danh của một sinh viên theo lớp học phần và trạng thái, chỉ buổi chưa hủy:
     * mỗi phần tử là [classSectionId, AttendanceStatus, count].
     */
    @Query("""
            SELECT a.session.classSection.id, a.status, COUNT(a) FROM Attendance a
            WHERE a.student.id = :studentId AND a.session.classSection.id IN :sectionIds
              AND a.session.status <> vn.edu.fpt.contactbook.model.enums.SessionStatus.CANCELLED
            GROUP BY a.session.classSection.id, a.status
            """)
    List<Object[]> countBySectionAndStatus(@Param("studentId") Long studentId,
                                           @Param("sectionIds") Collection<Long> sectionIds);

    /**
     * Dòng vắng không phép của một sinh viên ở các buổi có ngày trong [from, to], chỉ lớp :sectionId nếu khác null
     * (duyệt đơn nghỉ).
     */
    @Query("""
            SELECT a FROM Attendance a JOIN FETCH a.session s JOIN FETCH s.classSection cs
            WHERE a.student.id = :studentId
              AND a.status = vn.edu.fpt.contactbook.model.enums.AttendanceStatus.ABSENT_UNEXCUSED
              AND s.sessionDate >= :from AND s.sessionDate <= :to
              AND (:sectionId IS NULL OR cs.id = :sectionId)
            ORDER BY s.sessionDate, s.startTime
            """)
    List<Attendance> findUnexcusedInRange(@Param("studentId") Long studentId, @Param("from") LocalDate from,
                                          @Param("to") LocalDate to, @Param("sectionId") Long sectionId);
}
