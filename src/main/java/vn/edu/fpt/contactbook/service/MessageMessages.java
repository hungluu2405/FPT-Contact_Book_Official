package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của phân hệ tin nhắn.
 */
final class MessageMessages {

    static final String CONTENT_REQUIRED = "Vui lòng nhập nội dung tin nhắn";
    static final String CONTENT_SIZE = "Tin nhắn tối đa " + MessageService.MAX_CONTENT + " ký tự";
    static final String CANNOT_SEND = "Bạn không còn được nhắn tin cho người này; vẫn xem được các tin cũ";

    private MessageMessages() {
    }
}
