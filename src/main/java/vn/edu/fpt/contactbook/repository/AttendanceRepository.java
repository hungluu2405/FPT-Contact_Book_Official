package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Attendance;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
}
