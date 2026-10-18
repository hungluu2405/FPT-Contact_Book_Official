package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Subject;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    /**
     * keywordPattern lấy từ LikePatterns hoặc null; departmentId null là mọi bộ môn.
     * scoped = true giới hạn vào môn thuộc scopeDepartmentIds (phạm vi của HEAD_OF_DEPARTMENT).
     */
    String SEARCH_FROM_WHERE = """
            FROM Subject s
            WHERE (:keywordPattern IS NULL
                   OR LOWER(s.code) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(s.name) LIKE :keywordPattern ESCAPE '\\')
              AND (:departmentId IS NULL OR s.department.id = :departmentId)
              AND (:scoped = FALSE OR s.department.id IN :scopeDepartmentIds)
            """;

    @EntityGraph(attributePaths = {"department"})
    @Query(value = "SELECT s " + SEARCH_FROM_WHERE + " ORDER BY s.code",
            countQuery = "SELECT COUNT(s) " + SEARCH_FROM_WHERE)
    Page<Subject> search(@Param("keywordPattern") String keywordPattern,
                         @Param("departmentId") Long departmentId,
                         @Param("scoped") boolean scoped,
                         @Param("scopeDepartmentIds") Collection<Long> scopeDepartmentIds,
                         Pageable pageable);

    @EntityGraph(attributePaths = {"department"})
    List<Subject> findAllByOrderByCodeAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
