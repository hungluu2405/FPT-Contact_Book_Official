package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.NotificationRecipient;
import vn.edu.fpt.contactbook.model.NotificationRecipientId;

import java.util.List;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, NotificationRecipientId> {

    List<NotificationRecipient> findByIdUserId(Long userId);

    List<NotificationRecipient> findByIdNotificationId(Long notificationId);

    long countByIdUserIdAndIsReadFalse(Long userId);
}
