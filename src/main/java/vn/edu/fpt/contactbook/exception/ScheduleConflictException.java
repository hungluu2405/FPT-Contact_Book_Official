package vn.edu.fpt.contactbook.exception;

import lombok.Getter;
import vn.edu.fpt.contactbook.dto.ScheduleConflict;

import java.util.List;

/**
 * Lịch định tạo bị trùng; conflicts liệt kê từng buổi bị trùng để hiện thành bảng. Không có gì được lưu.
 */
@Getter
public class ScheduleConflictException extends BusinessException {

    private final List<ScheduleConflict> conflicts;

    public ScheduleConflictException(String message, List<ScheduleConflict> conflicts) {
        super(message);
        this.conflicts = List.copyOf(conflicts);
    }
}
