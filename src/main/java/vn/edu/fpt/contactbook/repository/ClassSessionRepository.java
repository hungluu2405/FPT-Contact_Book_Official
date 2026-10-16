package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClassSession;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findByClassSectionIdOrderBySessionNoAsc(Long classSectionId);

    List<ClassSession> findByClassSectionIdAndSessionDateBetween(Long classSectionId, LocalDate fromDate, LocalDate toDate);
}
