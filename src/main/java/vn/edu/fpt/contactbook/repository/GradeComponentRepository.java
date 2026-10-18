package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.GradeComponent;

import java.util.List;

@Repository
public interface GradeComponentRepository extends JpaRepository<GradeComponent, Long> {

    List<GradeComponent> findBySubjectIdOrderByIdAsc(Long subjectId);

    boolean existsBySubjectIdAndNameIgnoreCase(Long subjectId, String name);

    boolean existsBySubjectIdAndNameIgnoreCaseAndIdNot(Long subjectId, String name, Long id);
}
