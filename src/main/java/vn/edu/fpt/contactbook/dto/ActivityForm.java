package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Form tạo và sửa hoạt động câu lạc bộ. Thời gian nhập bằng ô datetime-local; số lượng tối đa để trống là không
 * giới hạn. Mọi giá trị được kiểm tra lại ở service.
 */
@Getter
@Setter
@NoArgsConstructor
public class ActivityForm {

    @NotBlank(message = "{activity.title.required}")
    @Size(max = 200, message = "{activity.title.size}")
    private String title;

    @Size(max = 2000, message = "{club.description.size}")
    private String description;

    @Size(max = 200, message = "{activity.location.size}")
    private String location;

    @NotNull(message = "{activity.start.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME, fallbackPatterns = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;

    @NotNull(message = "{activity.end.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME, fallbackPatterns = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    private Integer maxParticipants;
}
