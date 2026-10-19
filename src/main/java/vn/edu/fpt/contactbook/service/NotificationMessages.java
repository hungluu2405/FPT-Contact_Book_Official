package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của phân hệ thông báo.
 */
final class NotificationMessages {

    static final String TITLE_REQUIRED = "Vui lòng nhập tiêu đề";
    static final String TITLE_SIZE = "Tiêu đề tối đa 200 ký tự";
    static final String CONTENT_REQUIRED = "Vui lòng nhập nội dung";
    static final String PRIORITY_REQUIRED = "Vui lòng chọn mức độ";
    static final String TARGET_REQUIRED = "Vui lòng chọn phạm vi gửi";
    static final String TARGET_ID_REQUIRED = "Vui lòng chọn nơi nhận";
    static final String TARGET_NOT_FOUND = "Không tìm thấy nơi nhận đã chọn";
    static final String PHONE_REQUIRED = "Vui lòng nhập số điện thoại người nhận";
    static final String PHONE_NOT_FOUND = "Không tìm thấy tài khoản đang hoạt động có số điện thoại này";
    static final String NO_RECIPIENTS = "Phạm vi đã chọn không có người nhận nào (ngoài chính bạn)";
    static final String TOO_MANY_FILES = "Tối đa " + AttachmentStorage.MAX_FILES + " tệp đính kèm";
    static final String FILE_EMPTY = "Tệp \"%s\" rỗng";
    static final String FILE_TOO_LARGE = "Tệp \"%s\" vượt quá 5 MB";
    static final String FILE_TYPE = "Tệp \"%s\" không hợp lệ: chỉ nhận PDF, JPG, PNG, DOCX, XLSX";
    static final String FILE_NOT_FOUND = "Không tìm thấy tệp đính kèm";

    private NotificationMessages() {
    }
}
