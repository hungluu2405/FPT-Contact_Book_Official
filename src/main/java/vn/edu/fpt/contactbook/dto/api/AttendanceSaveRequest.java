package vn.edu.fpt.contactbook.dto.api;

import jakarta.validation.constraints.Size;
import vn.edu.fpt.contactbook.dto.AttendanceForm;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Điểm danh một buổi: statuses là students.id → trạng thái, phải đủ mọi sinh viên của bảng điểm danh. reason bắt buộc
 * khi buổi đã được điểm danh trước đó (AttendanceService kiểm tra).
 */
public record AttendanceSaveRequest(Map<Long, AttendanceStatus> statuses,
                                    @Size(max = 255, message = "{attendance.reason.size}") String reason) {

    public AttendanceForm toForm() {
        AttendanceForm form = new AttendanceForm();
        form.setStatuses(statuses == null ? new LinkedHashMap<>() : new LinkedHashMap<>(statuses));
        form.setReason(reason);
        return form;
    }
}
