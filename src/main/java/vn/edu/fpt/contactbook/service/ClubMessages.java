package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của phân hệ câu lạc bộ.
 */
final class ClubMessages {

    static final String NOT_FOUND = "Không tìm thấy câu lạc bộ";
    static final String CODE_TAKEN = "Mã câu lạc bộ đã tồn tại";
    static final String NAME_REQUIRED = "Vui lòng nhập tên câu lạc bộ";
    static final String NAME_SIZE = "Tên tối đa 150 ký tự";
    static final String DESCRIPTION_SIZE = "Mô tả tối đa " + ClubRule.MAX_DESCRIPTION + " ký tự";
    static final String FOUNDED_IN_FUTURE = "Ngày thành lập không được sau hôm nay";
    static final String ADVISOR_INVALID = "Giáo viên phụ trách phải là giáo viên đang hoạt động";
    static final String ALREADY_ACTIVE = "Câu lạc bộ đang hoạt động";
    static final String ALREADY_INACTIVE = "Câu lạc bộ đã ngừng hoạt động";
    static final String CLUB_INACTIVE = "Câu lạc bộ đã ngừng hoạt động";

    static final String STUDENT_CODE_REQUIRED = "Vui lòng nhập mã học sinh";
    static final String STUDENT_NOT_FOUND = "Không tìm thấy học sinh có mã này";
    static final String PRESIDENT_NOT_STUDENT = "Chủ nhiệm phải là học sinh có tài khoản đang hoạt động";
    static final String ALREADY_PRESIDENT = "Học sinh này đã là chủ nhiệm câu lạc bộ";

    static final String ONLY_STUDENTS_JOIN = "Chỉ học sinh mới tham gia được câu lạc bộ";
    static final String ALREADY_PENDING = "Bạn đã gửi yêu cầu tham gia, vui lòng chờ chủ nhiệm duyệt";
    static final String ALREADY_MEMBER = "Bạn đã là thành viên của câu lạc bộ này";
    static final String MEMBER_NOT_PENDING = "Yêu cầu không còn ở trạng thái chờ duyệt";
    static final String MEMBER_NOT_ACTIVE = "Người này không còn là thành viên đang hoạt động";
    static final String ROLE_INVALID = "Chỉ đổi được giữa thành viên và phó chủ nhiệm";
    static final String PRESIDENT_ROLE_LOCKED = "Muốn đổi chủ nhiệm, quản trị viên dùng chức năng chỉ định chủ nhiệm";
    static final String NOT_MEMBER = "Bạn không phải thành viên đang hoạt động của câu lạc bộ này";
    static final String PRESIDENT_CANNOT_LEAVE = "Chủ nhiệm không thể rời câu lạc bộ khi chưa chuyển giao";

    static final String ACTIVITY_NOT_FOUND = "Không tìm thấy hoạt động";
    static final String TITLE_REQUIRED = "Vui lòng nhập tiêu đề";
    static final String TITLE_SIZE = "Tiêu đề tối đa 200 ký tự";
    static final String LOCATION_SIZE = "Địa điểm tối đa 200 ký tự";
    static final String START_REQUIRED = "Vui lòng chọn thời gian bắt đầu";
    static final String END_REQUIRED = "Vui lòng chọn thời gian kết thúc";
    static final String END_AFTER_START = "Thời gian kết thúc phải sau thời gian bắt đầu";
    static final String START_IN_PAST = "Thời gian bắt đầu phải sau thời điểm hiện tại";
    static final String MAX_RANGE = "Số lượng tối đa phải từ 1 đến " + Short.MAX_VALUE;
    static final String MAX_BELOW_TAKEN = "Số lượng tối đa không được nhỏ hơn số người đã đăng ký (%d)";
    static final String ACTIVITY_CANCELLED = "Hoạt động đã bị hủy";
    static final String ACTIVITY_STARTED = "Hoạt động đã bắt đầu";
    static final String ACTIVITY_NOT_STARTED = "Chỉ ghi nhận tham dự từ giờ bắt đầu hoạt động";
    static final String ACTIVITY_FULL = "Hoạt động đã đủ số lượng";
    static final String ALREADY_REGISTERED = "Bạn đã đăng ký hoạt động này";
    static final String NOT_REGISTERED = "Bạn chưa đăng ký hoạt động này";
    static final String PARTICIPANT_UNKNOWN = "Danh sách tham dự có người chưa đăng ký hoạt động";

    private ClubMessages() {
    }
}
