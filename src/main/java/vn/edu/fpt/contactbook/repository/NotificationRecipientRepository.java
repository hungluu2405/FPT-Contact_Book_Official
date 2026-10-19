package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.NotificationRecipient;
import vn.edu.fpt.contactbook.model.NotificationRecipientId;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, NotificationRecipientId> {

    /** Số thông báo chưa đọc của một người (chuông trên thanh trên). */
    @Query("SELECT COUNT(nr) FROM NotificationRecipient nr WHERE nr.id.userId = :userId AND nr.isRead = false")
    long countUnread(@Param("userId") Long userId);

    /** Hộp thư: thông báo một người nhận được, mới nhất trước. */
    @Query(value = """
            SELECT nr FROM NotificationRecipient nr JOIN FETCH nr.notification n JOIN FETCH n.sender
            WHERE nr.id.userId = :userId
            ORDER BY n.createdAt DESC, n.id DESC
            """,
            countQuery = "SELECT COUNT(nr) FROM NotificationRecipient nr WHERE nr.id.userId = :userId")
    Page<NotificationRecipient> findInbox(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE NotificationRecipient nr SET nr.isRead = true, nr.readAt = :now
            WHERE nr.id.userId = :userId AND nr.isRead = false
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Thống kê đã đọc của nhiều thông báo một lần: [notificationId, số người nhận, số đã đọc]. */
    @Query("""
            SELECT nr.id.notificationId, COUNT(nr), SUM(CASE WHEN nr.isRead = true THEN 1 ELSE 0 END)
            FROM NotificationRecipient nr WHERE nr.id.notificationId IN :notificationIds
            GROUP BY nr.id.notificationId
            """)
    List<Object[]> countReadStats(@Param("notificationIds") Collection<Long> notificationIds);

    /** Người nhận của một thông báo theo tên; read null là tất cả. */
    @Query(value = """
            SELECT nr FROM NotificationRecipient nr JOIN FETCH nr.user u
            WHERE nr.id.notificationId = :notificationId AND (:read IS NULL OR nr.isRead = :read)
            ORDER BY u.fullName, u.id
            """,
            countQuery = """
            SELECT COUNT(nr) FROM NotificationRecipient nr
            WHERE nr.id.notificationId = :notificationId AND (:read IS NULL OR nr.isRead = :read)
            """)
    Page<NotificationRecipient> findRecipients(@Param("notificationId") Long notificationId,
                                               @Param("read") Boolean read, Pageable pageable);
}
