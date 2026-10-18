package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Form thêm và sửa đầu điểm của một môn. Điểm tối đa mặc định 10 (DEFAULT df_gc_max).
 */
@Getter
@Setter
@NoArgsConstructor
public class GradeComponentForm {

    @NotBlank(message = "{catalog.name.required}")
    @Size(max = 100, message = "{catalog.name.size100}")
    private String name;

    @NotNull(message = "{catalog.weight.required}")
    private BigDecimal weight;

    @NotNull(message = "{catalog.max-score.required}")
    private BigDecimal maxScore = BigDecimal.TEN;
}
