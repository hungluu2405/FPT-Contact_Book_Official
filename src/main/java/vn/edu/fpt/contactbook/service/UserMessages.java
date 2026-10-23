package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của phân hệ quản lý người dùng (dùng chung cho kiểm tra trước và lỗi ràng buộc DB).
 */
final class UserMessages {

    static final String PHONE_INVALID = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0";
    static final String PHONE_TAKEN = "Số điện thoại đã được sử dụng";
    static final String EMAIL_TAKEN = "Email đã được sử dụng";
    static final String STUDENT_CODE_TAKEN = "Mã học sinh đã tồn tại";
    static final String TEACHER_CODE_TAKEN = "Mã giáo viên đã tồn tại";
    static final String ROLE_ALREADY_ASSIGNED = "Người dùng đã có vai trò này với tổ chuyên môn này";
    static final String PROFILE_EXISTS = "Người dùng đã có hồ sơ này";
    static final String LINK_EXISTS = "Phụ huynh và học sinh này đã được liên kết";
    static final String USER_NOT_FOUND = "Không tìm thấy người dùng";

    private UserMessages() {
    }
}
