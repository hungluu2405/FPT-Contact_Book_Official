package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Faculty;

import java.util.List;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    /** keywordPattern lấy từ LikePatterns (đã escape, chữ thường) hoặc null để lấy tất cả. */
    @Query("""
            SELECT f FROM Faculty f
            WHERE :keywordPattern IS NULL
               OR LOWER(f.code) LIKE :keywordPattern ESCAPE '\\'
               OR LOWER(f.name) LIKE :keywordPattern ESCAPE '\\'
            ORDER BY f.code
            """)
    Page<Faculty> search(@Param("keywordPattern") String keywordPattern, Pageable pageable);

    List<Faculty> findAllByOrderByCodeAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
