package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Form điểm danh một buổi: statuses là students.id → trạng thái của mọi sinh viên đang học; notes là students.id →
 * ghi chú của từng học sinh (tùy chọn, tối đa 255 ký tự, ví dụ "đi muộn 10 phút"). reason bắt buộc khi đổi trạng
 * thái của một buổi đã điểm danh, và khi ADMIN điểm danh thay sau hạn; lý do chỉ ghi vào nhật ký.
 */
@Getter
@Setter
@NoArgsConstructor
public class AttendanceForm {

    private Map<Long, AttendanceStatus> statuses = new LinkedHashMap<>();

    private Map<Long, String> notes = new LinkedHashMap<>();

    @Size(max = 255, message = "{attendance.reason.size}")
    private String reason;
}
