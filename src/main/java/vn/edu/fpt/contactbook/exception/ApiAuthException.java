package vn.edu.fpt.contactbook.exception;

import lombok.Getter;

/**
 * Lỗi đăng nhập/làm mới token của REST API không có sẵn trong Spring Security. ApiExceptionHandler đổi reason
 * thành mã lỗi: NO_ROLE → 403 NO_ROLE, INVALID_REFRESH_TOKEN → 401 UNAUTHORIZED.
 */
@Getter
public class ApiAuthException extends RuntimeException {

    public enum Reason { NO_ROLE, INVALID_REFRESH_TOKEN }

    private final Reason reason;

    public ApiAuthException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
