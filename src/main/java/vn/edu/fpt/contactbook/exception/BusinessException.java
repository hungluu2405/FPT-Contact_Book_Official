package vn.edu.fpt.contactbook.exception;

import lombok.Getter;

/**
 * Lỗi nghiệp vụ, thông báo tiếng Việt hiển thị được cho người dùng.
 * field: tên ô trên form gây lỗi (null nếu là lỗi chung).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        this(message, null);
    }

    public BusinessException(String message, String field) {
        super(message);
        this.field = field;
    }
}
