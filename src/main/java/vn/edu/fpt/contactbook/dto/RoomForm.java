package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form thêm và sửa phòng học. Sức chứa để trống là chưa xác định.
 */
@Getter
@Setter
@NoArgsConstructor
public class RoomForm {

    @NotBlank(message = "{catalog.code.required}")
    @Size(max = 20, message = "{catalog.code.size}")
    private String code;

    @NotBlank(message = "{catalog.name.required}")
    @Size(max = 100, message = "{catalog.name.size100}")
    private String name;

    @Size(max = 100, message = "{catalog.building.size}")
    private String building;

    private Integer capacity;
}
