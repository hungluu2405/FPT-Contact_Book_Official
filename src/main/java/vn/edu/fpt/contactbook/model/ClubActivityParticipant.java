package vn.edu.fpt.contactbook.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.ParticipantStatus;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalDateTime;

@Entity
@Table(name = "club_activity_participants")
@Getter
@Setter
@NoArgsConstructor
public class ClubActivityParticipant {

    @EmbeddedId
    private ClubActivityParticipantId id = new ClubActivityParticipantId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("activityId")
    @JoinColumn(name = "activity_id", nullable = false)
    private ClubActivity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParticipantStatus status = ParticipantStatus.REGISTERED;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @PrePersist
    public void prePersist() {
        if (this.registeredAt == null) {
            this.registeredAt = AppTime.now();
        }
    }
}
