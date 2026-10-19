package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form thêm và sửa câu lạc bộ (ADMIN). Logo gửi riêng dạng tệp; giảng viên phụ trách chọn từ danh sách
 * (users.id), để trống là chưa có. Mọi giá trị được kiểm tra lại ở service.
 */
@Getter
@Setter
@NoArgsConstructor
public class ClubForm {

    @NotBlank(message = "{catalog.code.required}")
    @Size(max = 20, message = "{catalog.code.size}")
    private String code;

    @NotBlank(message = "{club.name.required}")
    @Size(max = 150, message = "{catalog.name.size150}")
    private String name;

    @Size(max = 2000, message = "{club.description.size}")
    private String description;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate foundedDate;

    private Long advisorUserId;
}
