package vn.edu.fpt.contactbook.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_files")
@Getter
@Setter
@NoArgsConstructor
public class NotificationFile {

    @EmbeddedId
    private NotificationFileId id = new NotificationFileId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("notificationId")
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("fileId")
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;
}
