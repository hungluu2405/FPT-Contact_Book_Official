package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Grade;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {

    List<Grade> findByEnrollmentId(Long enrollmentId);

    Optional<Grade> findByEnrollmentIdAndComponentId(Long enrollmentId, Long componentId);
}
