package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form thêm và sửa môn học.
 */
@Getter
@Setter
@NoArgsConstructor
public class SubjectForm {

    @NotNull(message = "{catalog.department.required}")
    private Long departmentId;

    @NotBlank(message = "{catalog.code.required}")
    @Size(max = 20, message = "{catalog.code.size}")
    private String code;

    @NotBlank(message = "{catalog.name.required}")
    @Size(max = 200, message = "{catalog.name.size200}")
    private String name;

    @NotNull(message = "{catalog.credits.required}")
    private Integer credits;

    @Size(max = 500, message = "{catalog.description.size}")
    private String description;
}
