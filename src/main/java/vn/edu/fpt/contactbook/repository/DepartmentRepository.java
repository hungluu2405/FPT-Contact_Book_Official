package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Department;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** keywordPattern lấy từ LikePatterns (đã escape, chữ thường) hoặc null; facultyId null là mọi khoa. */
    String SEARCH_FROM_WHERE = """
            FROM Department d
            WHERE (:keywordPattern IS NULL
                   OR LOWER(d.code) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(d.name) LIKE :keywordPattern ESCAPE '\\')
              AND (:facultyId IS NULL OR d.faculty.id = :facultyId)
            """;

    List<Department> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = {"faculty"})
    @Query(value = "SELECT d " + SEARCH_FROM_WHERE + " ORDER BY d.code",
            countQuery = "SELECT COUNT(d) " + SEARCH_FROM_WHERE)
    Page<Department> search(@Param("keywordPattern") String keywordPattern, @Param("facultyId") Long facultyId,
                            Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
