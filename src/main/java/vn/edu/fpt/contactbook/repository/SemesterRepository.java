package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Semester;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {

    /** Học kỳ bắt đầu muộn hơn đứng trên. */
    @Query("""
            SELECT s FROM Semester s
            WHERE :keywordPattern IS NULL
               OR LOWER(s.code) LIKE :keywordPattern ESCAPE '\\'
               OR LOWER(s.name) LIKE :keywordPattern ESCAPE '\\'
            ORDER BY s.startDate DESC, s.id DESC
            """)
    Page<Semester> search(@Param("keywordPattern") String keywordPattern, Pageable pageable);

    /** Index ux_semesters_current bảo đảm có nhiều nhất một học kỳ hiện tại. */
    Optional<Semester> findByCurrentTrue();

    List<Semester> findAllByOrderByStartDateDesc();

    /** Học kỳ chưa kết thúc tại ngày day (end_date ≥ day), mới nhất trước. */
    List<Semester> findByEndDateGreaterThanEqualOrderByStartDateDesc(LocalDate day);

    /** Học kỳ đã kết thúc trước ngày day (end_date < day) gần nhất. */
    Optional<Semester> findFirstByEndDateLessThanOrderByEndDateDesc(LocalDate day);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
