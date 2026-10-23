package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của điểm số (dùng chung cho kiểm tra trước và lỗi ràng buộc DB).
 */
final class GradeMessages {

    static final String SECTION_CANCELLED = "Lớp môn học đã hủy nên không nhập điểm được";
    static final String NO_COMPONENTS = "Môn học chưa có đầu điểm nên chưa nhập hoặc công bố điểm được";
    static final String WEIGHTS_INCOMPLETE =
            "Tổng trọng số các đầu điểm của môn đang là %s%%, phải đúng 100%% mới nhập hoặc công bố điểm được";
    static final String SCORE_INVALID = "Điểm phải là số, ví dụ 8.5";
    static final String SCORE_RANGE = "Điểm phải từ 0 đến %s";
    static final String SCORE_SCALE = "Điểm tối đa 2 chữ số thập phân";
    static final String SCORE_RANGE_DB = "Điểm phải từ 0 đến 10";
    static final String SCORE_REQUIRED = "Vui lòng nhập điểm mới";
    static final String SCORE_UNCHANGED = "Điểm mới trùng điểm hiện tại";
    static final String CELL_PUBLISHED = "Có ô điểm vừa được công bố nên không sửa được nữa, vui lòng tải lại trang";
    static final String STUDENTS_CHANGED = "Danh sách học sinh hoặc đầu điểm vừa thay đổi, vui lòng tải lại trang";
    static final String HAS_ERRORS = "Có ô điểm không hợp lệ, chưa lưu ô nào. Hãy sửa các ô được đánh dấu";
    static final String NOTHING_TO_PUBLISH = "Không có ô điểm nào đã nhập mà chưa công bố";
    static final String NOT_PUBLISHED = "Chỉ điều chỉnh được ô điểm đã công bố; ô chưa công bố do giáo viên sửa";
    static final String GRADE_NOT_FOUND = "Không tìm thấy ô điểm";
    static final String REASON_REQUIRED = "Vui lòng nhập lý do điều chỉnh điểm";
    static final String REASON_SIZE = "Lý do tối đa 255 ký tự";
    static final String CONCURRENT_CHANGE = "Bảng điểm vừa được người khác thay đổi, vui lòng tải lại trang và thử lại";

    private GradeMessages() {
    }
}
