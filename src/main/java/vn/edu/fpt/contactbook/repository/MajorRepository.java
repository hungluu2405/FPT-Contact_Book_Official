package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Major;

import java.util.List;

@Repository
public interface MajorRepository extends JpaRepository<Major, Long> {

    /** keywordPattern lấy từ LikePatterns (đã escape, chữ thường) hoặc null; facultyId null là mọi khoa. */
    String SEARCH_FROM_WHERE = """
            FROM Major m
            WHERE (:keywordPattern IS NULL
                   OR LOWER(m.code) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(m.name) LIKE :keywordPattern ESCAPE '\\')
              AND (:facultyId IS NULL OR m.faculty.id = :facultyId)
            """;

    List<Major> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = {"faculty"})
    @Query(value = "SELECT m " + SEARCH_FROM_WHERE + " ORDER BY m.code",
            countQuery = "SELECT COUNT(m) " + SEARCH_FROM_WHERE)
    Page<Major> search(@Param("keywordPattern") String keywordPattern, @Param("facultyId") Long facultyId,
                       Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
