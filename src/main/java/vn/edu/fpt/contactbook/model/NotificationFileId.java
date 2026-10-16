package vn.edu.fpt.contactbook.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NotificationFileId implements Serializable {

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "file_id")
    private Long fileId;
}
