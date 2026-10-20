package vn.edu.fpt.contactbook.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi của REST API: mobile dựa vào code (không dựa vào câu chữ) để xử lý, message là câu tiếng Việt để hiển thị.
 * messageKey null: thông báo lấy từ chính exception (lỗi nghiệp vụ).
 */
@Getter
@RequiredArgsConstructor
public enum ApiErrorCode {

    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "auth.login.error"),
    ACCOUNT_LOCKED_TEMPORARILY(HttpStatus.TOO_MANY_REQUESTS, "auth.login.temporarily-locked"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "auth.login.disabled"),
    NO_ROLE(HttpStatus.FORBIDDEN, "auth.login.no-role"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "api.error.unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "api.error.forbidden"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "password.forced"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "api.error.validation"),
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST, null),
    NOT_FOUND(HttpStatus.NOT_FOUND, "api.error.not-found"),
    CONFLICT(HttpStatus.CONFLICT, "api.error.conflict"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "api.error.internal");

    private final HttpStatus status;
    private final String messageKey;
}
