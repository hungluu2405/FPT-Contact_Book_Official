package vn.edu.fpt.contactbook.dto;

/**
 * Kết quả lưu điểm danh: firstTime = lần điểm danh đầu của buổi; changed = số sinh viên được tạo hoặc đổi trạng thái
 * (0 khi điều chỉnh mà không có gì thay đổi).
 */
public record AttendanceSaveResult(boolean firstTime, int changed) {
}
