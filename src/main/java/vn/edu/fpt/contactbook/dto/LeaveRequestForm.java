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
 * Form đơn xin nghỉ. Tiêu đề để trống thì service tự điền "Đơn xin nghỉ học từ dd/MM/yyyy đến dd/MM/yyyy";
 * sectionId để trống là không chọn lớp học phần. Mọi giá trị được kiểm tra lại ở service.
 */
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequestForm {

    @Size(max = 200, message = "{request.title.size}")
    private String title;

    private Long sectionId;

    @NotNull(message = "{request.from.required}")
    @DateTimeFormat(pattern = DateFormats.DATE, fallbackPatterns = {DateFormats.DATE_SHORT, DateFormats.ISO_DATE})
    private LocalDate fromDate;

    @NotNull(message = "{request.to.required}")
    @DateTimeFormat(pattern = DateFormats.DATE, fallbackPatterns = {DateFormats.DATE_SHORT, DateFormats.ISO_DATE})
    private LocalDate toDate;

    @NotBlank(message = "{request.reason.required}")
    @Size(max = 1000, message = "{request.reason.size}")
    private String reason;
}
