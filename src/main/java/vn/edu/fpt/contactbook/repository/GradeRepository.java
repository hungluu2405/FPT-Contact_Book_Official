package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Grade;

import java.util.Set;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {

    boolean existsByComponentId(Long componentId);

    /** Các đầu điểm của môn đã có ít nhất một dòng điểm (không xóa được). */
    @Query("SELECT DISTINCT g.component.id FROM Grade g WHERE g.component.subject.id = :subjectId")
    Set<Long> findComponentIdsWithGrades(@Param("subjectId") Long subjectId);
}
