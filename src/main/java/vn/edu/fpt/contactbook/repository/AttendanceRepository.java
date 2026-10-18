package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Attendance;

import java.util.Set;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    boolean existsBySessionId(Long sessionId);

    /** Các buổi của lớp học phần đã có ít nhất một dòng điểm danh. */
    @Query("SELECT DISTINCT a.session.id FROM Attendance a WHERE a.session.classSection.id = :sectionId")
    Set<Long> findSessionIdsWithAttendance(@Param("sectionId") Long sectionId);
}
