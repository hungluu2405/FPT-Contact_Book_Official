package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Enrollment;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByClassSectionId(Long classSectionId);

    List<Enrollment> findByStudentId(Long studentId);

    Optional<Enrollment> findByClassSectionIdAndStudentId(Long classSectionId, Long studentId);
}
