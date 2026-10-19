package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.GradeComponent;

import java.util.Collection;
import java.util.List;

@Repository
public interface GradeComponentRepository extends JpaRepository<GradeComponent, Long> {

    List<GradeComponent> findBySubjectIdOrderByIdAsc(Long subjectId);

    /** Đầu điểm của nhiều môn một lần (trang điểm của sinh viên theo học kỳ). */
    List<GradeComponent> findBySubjectIdInOrderByIdAsc(Collection<Long> subjectIds);

    boolean existsBySubjectIdAndNameIgnoreCase(Long subjectId, String name);

    boolean existsBySubjectIdAndNameIgnoreCaseAndIdNot(Long subjectId, String name, Long id);
}
