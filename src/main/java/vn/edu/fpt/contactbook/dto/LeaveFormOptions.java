package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Dữ liệu cho form đơn nghỉ: sinh viên liên quan và các lớp học phần sinh viên đang học (ô chọn tùy chọn).
 */
public record LeaveFormOptions(Long studentId, String studentCode, String studentName, List<OptionView> sections) {
}
