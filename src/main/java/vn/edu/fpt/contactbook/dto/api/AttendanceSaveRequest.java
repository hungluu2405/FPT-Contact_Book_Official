package vn.edu.fpt.contactbook.dto.api;

import jakarta.validation.constraints.Size;
import vn.edu.fpt.contactbook.dto.AttendanceForm;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Điểm danh một buổi: statuses là students.id → trạng thái, phải đủ mọi học sinh của bảng điểm danh. notes là
 * students.id → ghi chú (tùy chọn, tối đa 255 ký tự; chuỗi rỗng là xóa); học sinh không có trong notes thì giữ
 * nguyên ghi chú đã lưu. reason bắt buộc
 * khi đổi trạng thái của buổi đã điểm danh (AttendanceService kiểm tra).
 */
public record AttendanceSaveRequest(Map<Long, AttendanceStatus> statuses, Map<Long, String> notes,
                                    @Size(max = 255, message = "{attendance.reason.size}") String reason) {

    public AttendanceForm toForm() {
        AttendanceForm form = new AttendanceForm();
        form.setStatuses(statuses == null ? new LinkedHashMap<>() : new LinkedHashMap<>(statuses));
        form.setNotes(notes == null ? null : new LinkedHashMap<>(notes));
        form.setReason(reason);
        return form;
    }
}
