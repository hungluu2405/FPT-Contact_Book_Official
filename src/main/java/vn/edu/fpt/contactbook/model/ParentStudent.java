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
import vn.edu.fpt.contactbook.model.enums.ParentRelationship;

import java.time.LocalDateTime;

@Entity
@Table(name = "parent_student")
@Getter
@Setter
@NoArgsConstructor
public class ParentStudent {

    @EmbeddedId
    private ParentStudentId id = new ParentStudentId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("parentId")
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 30)
    private ParentRelationship relationship;

    @NotNull
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
