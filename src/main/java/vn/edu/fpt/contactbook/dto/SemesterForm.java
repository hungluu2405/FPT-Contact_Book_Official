package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form thêm và sửa học kỳ. Cờ học kỳ hiện tại đổi bằng thao tác riêng, không qua form này.
 */
@Getter
@Setter
@NoArgsConstructor
public class SemesterForm {

    @NotBlank(message = "{catalog.code.required}")
    @Size(max = 20, message = "{catalog.code.size}")
    private String code;

    @NotBlank(message = "{catalog.name.required}")
    @Size(max = 100, message = "{catalog.name.size100}")
    private String name;

    @NotNull(message = "{catalog.start-date.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "{catalog.end-date.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
