package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Thông báo đã gửi của một người, mới nhất trước. */
    Page<Notification> findBySenderIdOrderByCreatedAtDescIdDesc(Long senderId, Pageable pageable);
}
