package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.NotificationFile;
import vn.edu.fpt.contactbook.model.NotificationFileId;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationFileRepository extends JpaRepository<NotificationFile, NotificationFileId> {

    @EntityGraph(attributePaths = {"file"})
    List<NotificationFile> findByIdNotificationIdOrderByIdFileIdAsc(Long notificationId);

    @EntityGraph(attributePaths = {"file"})
    Optional<NotificationFile> findByIdNotificationIdAndIdFileId(Long notificationId, Long fileId);
}
