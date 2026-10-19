package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Form điểm danh một buổi: statuses là students.id → trạng thái của mọi sinh viên đang học; reason bắt buộc khi
 * điều chỉnh một buổi đã điểm danh (lưu vào attendance.note của các dòng bị đổi).
 */
@Getter
@Setter
@NoArgsConstructor
public class AttendanceForm {

    private Map<Long, AttendanceStatus> statuses = new LinkedHashMap<>();

    @Size(max = 255, message = "{attendance.reason.size}")
    private String reason;
}
