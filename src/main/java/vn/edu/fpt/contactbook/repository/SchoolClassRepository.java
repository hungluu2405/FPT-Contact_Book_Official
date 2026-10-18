package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.SchoolClass;

import java.util.List;

@Repository
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    /** keywordPattern lấy từ LikePatterns (đã escape, chữ thường) hoặc null; majorId null là mọi ngành. */
    String SEARCH_FROM_WHERE = """
            FROM SchoolClass c
            WHERE (:keywordPattern IS NULL
                   OR LOWER(c.code) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(c.name) LIKE :keywordPattern ESCAPE '\\')
              AND (:majorId IS NULL OR c.major.id = :majorId)
            """;

    @EntityGraph(attributePaths = {"major"})
    List<SchoolClass> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = {"major", "homeroomTeacher", "homeroomTeacher.user"})
    @Query(value = "SELECT c " + SEARCH_FROM_WHERE + " ORDER BY c.code",
            countQuery = "SELECT COUNT(c) " + SEARCH_FROM_WHERE)
    Page<SchoolClass> search(@Param("keywordPattern") String keywordPattern, @Param("majorId") Long majorId,
                             Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
