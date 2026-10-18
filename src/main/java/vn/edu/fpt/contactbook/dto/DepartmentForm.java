package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form thêm và sửa bộ môn (thuộc một khoa).
 */
@Getter
@Setter
@NoArgsConstructor
public class DepartmentForm {

    @NotNull(message = "{catalog.faculty.required}")
    private Long facultyId;

    @NotBlank(message = "{catalog.code.required}")
    @Size(max = 20, message = "{catalog.code.size}")
    private String code;

    @NotBlank(message = "{catalog.name.required}")
    @Size(max = 150, message = "{catalog.name.size150}")
    private String name;

    @Size(max = 500, message = "{catalog.description.size}")
    private String description;
}
