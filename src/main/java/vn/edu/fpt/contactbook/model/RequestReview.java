package vn.edu.fpt.contactbook.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.ReviewDecision;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_reviews")
@Getter
@Setter
@NoArgsConstructor
public class RequestReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private UserRequest request;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 10)
    private ReviewDecision decision;

    @Size(max = 500)
    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private LocalDateTime reviewedAt;

    @PrePersist
    public void prePersist() {
        if (this.reviewedAt == null) {
            this.reviewedAt = AppTime.now();
        }
    }
}
