package vn.edu.fpt.contactbook.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClubActivity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClubActivityRepository extends JpaRepository<ClubActivity, Long> {

    /** Hoạt động sắp tới: chưa hủy và chưa kết thúc (gồm cả đang diễn ra), sớm nhất trước. */
    @Query("""
            SELECT a FROM ClubActivity a
            WHERE a.club.id = :clubId AND a.endTime > :now
              AND a.status <> vn.edu.fpt.contactbook.model.enums.ClubActivityStatus.CANCELLED
            ORDER BY a.startTime, a.id
            """)
    List<ClubActivity> findUpcoming(@Param("clubId") Long clubId, @Param("now") LocalDateTime now);

    /** Mọi hoạt động của CLB, mới nhất trước (trang quản lý hoạt động). */
    Page<ClubActivity> findByClubIdOrderByStartTimeDescIdDesc(Long clubId, Pageable pageable);

    /**
     * Trạng thái lưu và thời gian của mọi hoạt động thuộc các CLB (báo cáo CLB tính trạng thái hiển thị bằng
     * ClubRule.state): mỗi phần tử là [clubId, ClubActivityStatus, startTime, endTime].
     */
    @Query("SELECT a.club.id, a.status, a.startTime, a.endTime FROM ClubActivity a WHERE a.club.id IN :clubIds")
    List<Object[]> findTimesByClubs(@Param("clubIds") Collection<Long> clubIds);

    /** Khóa dòng hoạt động: kiểm tra sĩ số và đăng ký chạy lần lượt (hai người không cùng lấy chỗ cuối). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ClubActivity a WHERE a.id = :id")
    Optional<ClubActivity> findByIdForUpdate(@Param("id") Long id);
}
