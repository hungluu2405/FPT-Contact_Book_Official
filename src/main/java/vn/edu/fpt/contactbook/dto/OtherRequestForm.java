package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.RequestType;

/**
 * Form đơn khác của sinh viên: xin bảng điểm, xác nhận sinh viên, khác (không nhận LEAVE, kiểm tra ở service).
 */
@Getter
@Setter
@NoArgsConstructor
public class OtherRequestForm {

    @NotNull(message = "{request.type.required}")
    private RequestType requestType;

    @NotBlank(message = "{request.title.required}")
    @Size(max = 200, message = "{request.title.size}")
    private String title;

    @NotBlank(message = "{request.reason.required}")
    @Size(max = 1000, message = "{request.reason.size}")
    private String reason;
}
