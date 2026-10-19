package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của điểm danh (dùng chung cho kiểm tra trước và lỗi ràng buộc DB).
 */
final class AttendanceMessages {

    static final String SECTION_CANCELLED = "Lớp học phần đã hủy nên không điểm danh được";
    static final String SESSION_CANCELLED = "Buổi học đã hủy nên không điểm danh được";
    static final String FUTURE_SESSION = "Buổi học chưa diễn ra, chỉ điểm danh được từ ngày %s";
    static final String NO_STUDENTS = "Lớp chưa có sinh viên đang học để điểm danh";
    static final String STATUS_MISSING = "Vui lòng chọn trạng thái điểm danh cho mọi sinh viên";
    static final String STUDENTS_CHANGED = "Danh sách sinh viên của lớp vừa thay đổi, vui lòng tải lại trang";
    static final String REASON_REQUIRED = "Vui lòng nhập lý do điều chỉnh điểm danh";
    static final String REASON_SIZE = "Lý do tối đa 255 ký tự";
    static final String NOT_MARKED_YET = "Buổi học chưa được giảng viên điểm danh nên chưa điều chỉnh được";
    static final String CONCURRENT_CHANGE = "Điểm danh vừa được người khác lưu, vui lòng tải lại trang và thử lại";

    private AttendanceMessages() {
    }
}
