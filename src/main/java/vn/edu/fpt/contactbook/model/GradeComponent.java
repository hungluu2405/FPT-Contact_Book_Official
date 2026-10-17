package vn.edu.fpt.contactbook.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "grade_components",
        uniqueConstraints = @UniqueConstraint(name = "uq_grade_components", columnNames = {"subject_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
public class GradeComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @DecimalMax("100")
    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @NotNull
    @Column(name = "max_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal maxScore = BigDecimal.TEN;
}
