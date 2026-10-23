package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của buổi học và lịch thi (dùng chung cho kiểm tra trước và lỗi ràng buộc DB).
 */
final class ScheduleMessages {

    static final String ROOM_SLOT_TAKEN = "Phòng đã có buổi học khác bắt đầu cùng ngày giờ, vui lòng chọn phòng hoặc giờ khác";
    static final String SECTION_SLOT_TAKEN =
            "Lớp đã có buổi học (kể cả buổi đã hủy) bắt đầu đúng ngày giờ này, vui lòng chọn giờ bắt đầu khác";
    static final String END_AFTER_START = "Giờ kết thúc phải sau giờ bắt đầu";
    static final String OUTSIDE_SEMESTER = "Ngày phải nằm trong học kỳ (%s đến %s)";
    static final String CONFLICTS = "Lịch bị trùng, chưa lưu buổi nào";
    static final String EXAM_CONFLICTS = "Lịch thi bị trùng, chưa lưu";
    static final String SECTION_CANCELLED = "Lớp môn học đã hủy nên không thêm được buổi học hay lịch thi";
    static final String SESSION_NOT_FOUND = "Không tìm thấy buổi học";
    static final String EXAM_NOT_FOUND = "Không tìm thấy lịch thi";
    static final String SESSION_CANCELLED = "Buổi học đã hủy, không sửa được";
    static final String SESSION_ALREADY_CANCELLED = "Buổi học đã được hủy trước đó";
    static final String HAS_ATTENDANCE_TIME = "Buổi học đã có điểm danh nên chỉ đổi được phòng và ghi chú";
    static final String HAS_ATTENDANCE_CANCEL = "Buổi học đã có điểm danh nên không hủy được";
    static final String ROOM_NOT_FOUND = "Không tìm thấy phòng học";
    static final String WEEKDAYS_REQUIRED = "Vui lòng chọn ít nhất một thứ trong tuần";
    static final String COUNT_RANGE = "Số buổi phải từ 1 đến %d";
    static final String NOT_ENOUGH_DAYS =
            "Chỉ xếp được tối đa %d buổi từ ngày bắt đầu đến ngày kết thúc học kỳ (%s), chưa lưu buổi nào";
    static final String CONCURRENT_CHANGE = "Lịch vừa được người khác thay đổi, vui lòng thử lại";

    private ScheduleMessages() {
    }
}
