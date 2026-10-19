package vn.edu.fpt.contactbook.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;

import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {

    /** keywordPattern so mã hoặc tên (đã escape và bọc %...%, null là không lọc); status null là tất cả. */
    String SEARCH_WHERE = """
            WHERE (:keywordPattern IS NULL OR LOWER(c.code) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(c.name) LIKE :keywordPattern ESCAPE '\\')
              AND (:status IS NULL OR c.status = :status)
            """;

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @EntityGraph(attributePaths = "advisor")
    @Query(value = "SELECT c FROM Club c " + SEARCH_WHERE + " ORDER BY c.name, c.id",
            countQuery = "SELECT COUNT(c) FROM Club c " + SEARCH_WHERE)
    Page<Club> search(@Param("keywordPattern") String keywordPattern, @Param("status") ClubStatus status,
                      Pageable pageable);

    @EntityGraph(attributePaths = "advisor")
    @Query("SELECT c FROM Club c WHERE c.id = :id")
    Optional<Club> findWithAdvisor(@Param("id") Long id);

    /** Khóa dòng CLB: mọi thao tác đổi thành viên của một CLB chạy lần lượt (giữ đúng một chủ nhiệm). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Club c WHERE c.id = :id")
    Optional<Club> findByIdForUpdate(@Param("id") Long id);
}
