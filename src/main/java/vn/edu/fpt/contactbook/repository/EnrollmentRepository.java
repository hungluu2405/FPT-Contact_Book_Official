package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Enrollment;

import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<Enrollment> findByClassSectionId(Long classSectionId);

    @EntityGraph(attributePaths = {"classSection", "classSection.subject"})
    List<Enrollment> findByStudentId(Long studentId);
}
