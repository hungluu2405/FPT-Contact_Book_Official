package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của phân hệ danh mục (dùng chung cho kiểm tra trước và lỗi ràng buộc DB).
 */
final class CatalogMessages {

    static final String FACULTY_CODE_TAKEN = "Mã cấp học đã tồn tại";
    static final String DEPARTMENT_CODE_TAKEN = "Mã tổ chuyên môn đã tồn tại";
    static final String MAJOR_CODE_TAKEN = "Mã khối đã tồn tại";
    static final String CLASS_CODE_TAKEN = "Mã lớp đã tồn tại";
    static final String SUBJECT_CODE_TAKEN = "Mã môn học đã tồn tại";
    static final String SEMESTER_CODE_TAKEN = "Mã học kỳ đã tồn tại";
    static final String ROOM_CODE_TAKEN = "Mã phòng đã tồn tại";
    static final String COMPONENT_NAME_TAKEN = "Môn học đã có đầu điểm cùng tên";

    static final String FACULTY_REQUIRED = "Vui lòng chọn cấp học";
    static final String MAJOR_REQUIRED = "Vui lòng chọn khối";
    static final String DEPARTMENT_REQUIRED = "Vui lòng chọn tổ chuyên môn";
    static final String HOMEROOM_NOT_FOUND = "Không tìm thấy giáo viên có mã này";

    static final String CLASS_MAJOR_LOCKED = "Lớp đã có học sinh nên không đổi được khối";
    static final String SUBJECT_DEPARTMENT_LOCKED = "Môn học đã có lớp môn học nên không đổi được tổ chuyên môn";
    static final String SUBJECT_DEPARTMENT_OUT_OF_SCOPE = "Bạn chỉ được chọn tổ chuyên môn mình quản lý";

    static final String CREDITS_RANGE = "Số tiết/tuần phải từ 1 đến 20";
    static final String WEIGHT_RANGE = "Trọng số phải lớn hơn 0 và không quá 100";
    static final String SEMESTER_DATES = "Ngày kết thúc phải sau ngày bắt đầu";
    static final String ROOM_CAPACITY = "Sức chứa phải lớn hơn 0";
    static final String CURRENT_SEMESTER_CONFLICT =
            "Học kỳ hiện tại vừa được người khác thay đổi, vui lòng tải lại trang và thử lại";
    static final String COMPONENT_HAS_GRADES = "Đầu điểm đã có điểm nên không xóa được";

    static final String FACULTY_NOT_FOUND = "Không tìm thấy cấp học";
    static final String DEPARTMENT_NOT_FOUND = "Không tìm thấy tổ chuyên môn";
    static final String MAJOR_NOT_FOUND = "Không tìm thấy khối";
    static final String CLASS_NOT_FOUND = "Không tìm thấy lớp";
    static final String SUBJECT_NOT_FOUND = "Không tìm thấy môn học";
    static final String COMPONENT_NOT_FOUND = "Không tìm thấy đầu điểm";
    static final String SEMESTER_NOT_FOUND = "Không tìm thấy học kỳ";
    static final String ROOM_NOT_FOUND = "Không tìm thấy phòng học";

    private CatalogMessages() {
    }
}
