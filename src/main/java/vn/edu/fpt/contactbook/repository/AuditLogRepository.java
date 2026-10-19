package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.AuditLog;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Bộ lọc nhật ký; tham số null là không lọc. keywordPattern lấy từ LikePatterns (họ tên hoặc số điện thoại của
     * người thực hiện), normalizedPhone là từ khóa đã chuẩn hóa bằng PhoneUtils (null nếu không phải số điện thoại).
     * Thời gian: from ≤ created_at &lt; toExclusive.
     */
    String SEARCH_WHERE = """
            WHERE (:keywordPattern IS NULL
                   OR LOWER(u.fullName) LIKE :keywordPattern ESCAPE '\\'
                   OR u.phone LIKE :keywordPattern ESCAPE '\\'
                   OR u.phone = :normalizedPhone)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:toExclusive IS NULL OR a.createdAt < :toExclusive)
            """;

    /** Mới nhất trước (id IDENTITY tăng theo thời điểm ghi). */
    @Query(value = "SELECT a FROM AuditLog a LEFT JOIN FETCH a.user u " + SEARCH_WHERE + " ORDER BY a.id DESC",
            countQuery = "SELECT COUNT(a) FROM AuditLog a LEFT JOIN a.user u " + SEARCH_WHERE)
    Page<AuditLog> search(@Param("keywordPattern") String keywordPattern,
                          @Param("normalizedPhone") String normalizedPhone,
                          @Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("from") LocalDateTime from,
                          @Param("toExclusive") LocalDateTime toExclusive,
                          Pageable pageable);

    @Query("SELECT a FROM AuditLog a LEFT JOIN FETCH a.user WHERE a.id = :id")
    Optional<AuditLog> findWithUser(@Param("id") Long id);
}
