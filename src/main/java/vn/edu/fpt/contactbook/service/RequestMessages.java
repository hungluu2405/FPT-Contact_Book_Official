package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của phân hệ đơn từ.
 */
final class RequestMessages {

    static final String TITLE_SIZE = "Tiêu đề tối đa 200 ký tự";
    static final String TITLE_REQUIRED = "Vui lòng nhập tiêu đề";
    static final String REASON_REQUIRED = "Vui lòng nhập lý do";
    static final String REASON_SIZE = "Lý do tối đa 1000 ký tự";
    static final String FROM_REQUIRED = "Vui lòng chọn từ ngày";
    static final String TO_REQUIRED = "Vui lòng chọn đến ngày";
    static final String TO_BEFORE_FROM = "Đến ngày phải bằng hoặc sau từ ngày";
    static final String FROM_TOO_OLD = "Từ ngày không được sớm hơn " + RequestRule.MAX_PAST_DAYS
            + " ngày trước hôm nay";
    static final String SPAN_TOO_LONG = "Mỗi đơn nghỉ tối đa " + RequestRule.MAX_SPAN_DAYS + " ngày";
    static final String SECTION_NOT_ENROLLED = "Học sinh không đang học lớp môn học đã chọn";
    static final String TYPE_REQUIRED = "Vui lòng chọn loại đơn";
    static final String TYPE_INVALID = "Loại đơn không hợp lệ";
    static final String COMMENT_REQUIRED = "Vui lòng nhập ghi chú khi từ chối đơn";
    static final String COMMENT_SIZE = "Ghi chú tối đa 500 ký tự";
    static final String DECISION_REQUIRED = "Vui lòng chọn duyệt hoặc từ chối";
    static final String NOT_PENDING = "Đơn không còn ở trạng thái chờ duyệt";
    static final String FILE_NOT_FOUND = "Không tìm thấy tệp đính kèm";

    private RequestMessages() {
    }
}
