package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClubActivityParticipant;
import vn.edu.fpt.contactbook.model.ClubActivityParticipantId;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ClubActivityParticipantRepository extends JpaRepository<ClubActivityParticipant, ClubActivityParticipantId> {

    /** Đăng ký đang giữ chỗ: REGISTERED hoặc ATTENDED (CANCELLED không tính). */
    String TAKEN = " p.status IN (vn.edu.fpt.contactbook.model.enums.ParticipantStatus.REGISTERED,"
            + " vn.edu.fpt.contactbook.model.enums.ParticipantStatus.ATTENDED) ";

    @Query("SELECT COUNT(p) FROM ClubActivityParticipant p WHERE p.id.activityId = :activityId AND" + TAKEN)
    long countTaken(@Param("activityId") Long activityId);

    /** Số chỗ đã giữ của nhiều hoạt động một lần: [activityId, số đăng ký]. */
    @Query("SELECT p.id.activityId, COUNT(p) FROM ClubActivityParticipant p WHERE p.id.activityId IN :activityIds"
            + " AND" + TAKEN + "GROUP BY p.id.activityId")
    List<Object[]> countTakenByActivities(@Param("activityIds") Collection<Long> activityIds);

    /** Đăng ký của một người ở nhiều hoạt động (mọi trạng thái). */
    List<ClubActivityParticipant> findByIdUserIdAndIdActivityIdIn(Long userId, Collection<Long> activityIds);

    /** Danh sách tham dự: người đang giữ chỗ, kèm tài khoản, theo họ tên. */
    @Query("SELECT p FROM ClubActivityParticipant p JOIN FETCH p.user u WHERE p.id.activityId = :activityId AND"
            + TAKEN + "ORDER BY u.fullName, u.id")
    List<ClubActivityParticipant> findTaken(@Param("activityId") Long activityId);

    /** Rời CLB: hủy các đăng ký REGISTERED ở hoạt động chưa bắt đầu của CLB đó; trả số dòng bị hủy. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ClubActivityParticipant p
            SET p.status = vn.edu.fpt.contactbook.model.enums.ParticipantStatus.CANCELLED
            WHERE p.id.userId = :userId
              AND p.status = vn.edu.fpt.contactbook.model.enums.ParticipantStatus.REGISTERED
              AND p.id.activityId IN (SELECT a.id FROM ClubActivity a
                                      WHERE a.club.id = :clubId AND a.startTime > :now)
            """)
    int cancelFutureRegistrations(@Param("clubId") Long clubId, @Param("userId") Long userId,
                                  @Param("now") LocalDateTime now);
}
