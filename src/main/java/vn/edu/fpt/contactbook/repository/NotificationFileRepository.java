package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.NotificationFile;
import vn.edu.fpt.contactbook.model.NotificationFileId;

import java.util.List;

@Repository
public interface NotificationFileRepository extends JpaRepository<NotificationFile, NotificationFileId> {

    List<NotificationFile> findByIdNotificationId(Long notificationId);
}
